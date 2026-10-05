package com.yeyofone.app

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yeyofone.core.account.PushCredentialStore
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Low-level client for yeyofone-push-relay v2 (a separate project - this repo stays client-only
 * per MAIN-IDEA.md). Each request authenticates with the account's operator-issued, per-device
 * credential; no shared relay secret ships in the app. Inert whenever BuildConfig.PUSH_RELAY_URL
 * is blank (the default) or an account has no credential.
 *
 * All network calls block; [PushRegistrar] schedules them off the main thread with retries.
 */
internal object PushRelayClient {
    private const val TAG = "YeyoFonePush"
    private const val PREFS_NAME = "yeyofone_push"
    private const val KEY_TOKEN = "fcm_token"
    private const val PENDING_REVOKE_PREFIX = "pending_revoke."
    private const val TIMEOUT_MS = 10_000

    enum class Status {
        /** The relay accepted this device's token. */
        REGISTERED,

        /** No relay URL, or no credential imported for the account. */
        NOT_CONFIGURED,

        /** 401: the credential expired or was revoked; the operator must issue a new one. */
        CREDENTIAL_REJECTED,

        /** 403: the credential is not allowed to register this device. */
        SCOPE_REJECTED,

        /** Network failure, rate limit or relay error; retried with backoff. */
        FAILED,
    }

    /** A relay response: [code] is null when no response arrived. */
    data class Response(val code: Int?, val retryAfterSeconds: Long? = null)

    fun saveToken(context: Context, token: String) {
        prefs(context).edit { putString(KEY_TOKEN, token) }
    }

    fun cachedToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun lastStatus(context: Context, accountId: SipAccountId): Status? =
        prefs(context).getString(statusKey(accountId), null)?.let { runCatching { Status.valueOf(it) }.getOrNull() }

    /** Validates and stores an imported credential, rejecting one issued for another account. */
    fun importCredential(context: Context, account: SipAccount, json: String): PushCredential {
        val credential = PushCredential.parse(json, account.username)
        check(!isRevocationPending(context, account.id)) { "The previous push credential is still being revoked" }
        val chars = json.trim().toCharArray()
        try {
            PushCredentialStore.create(context).save(account.id, chars)
        } finally {
            chars.fill('\u0000')
        }
        prefs(context).edit { remove(statusKey(account.id)) }
        return credential
    }

    fun credential(context: Context, account: SipAccount): PushCredential? =
        credential(context, account.id, account.username)

    private fun credential(context: Context, accountId: SipAccountId, username: String): PushCredential? {
        val stored = PushCredentialStore.create(context).read(accountId) ?: return null
        return try {
            runCatching { PushCredential.parse(String(stored), username) }
                .onFailure { Log.w(TAG, "account=${accountId.value} stored push credential is unusable") }
                .getOrNull()
        } finally {
            stored.fill('\u0000')
        }
    }

    /**
     * PUTs the FCM token. A credential the relay already rejected (expired, revoked or out of
     * scope), or one awaiting revocation, is never replayed.
     */
    fun register(context: Context, account: SipAccount, fcmToken: String): Pair<Status, Response?> {
        lastStatus(context, account.id)?.takeIf(::isTerminalRejection)?.let { return it to null }
        if (isRevocationPending(context, account.id)) return Status.NOT_CONFIGURED to null
        val credential = credential(context, account)
        val endpoint = credential?.let { deviceEndpoint(BuildConfig.PUSH_RELAY_URL, it.device) }
            ?: return Status.NOT_CONFIGURED to null
        val response = request(endpoint, "PUT", credential.token, JSONObject().put("token", fcmToken).toString())
        val status = response.code?.let(::statusFor) ?: Status.FAILED
        Log.i(TAG, "account=${account.id.value} relay registration status=$status code=${response.code}")
        prefs(context).edit { putString(statusKey(account.id), status.name) }
        return status to response
    }

    /**
     * Marks the account's credential for revocation; from then on it is never used to register.
     * [username] is kept so the stored credential can still be validated after the account is
     * deleted or renamed. Returns false when there is no credential to revoke.
     */
    fun markForRevocation(context: Context, accountId: SipAccountId, username: String): Boolean {
        if (!PushCredentialStore.create(context).contains(accountId)) return false
        prefs(context).edit {
            putString(PENDING_REVOKE_PREFIX + accountId.value, username)
            remove(statusKey(accountId))
        }
        return true
    }

    fun pendingRevocations(context: Context): Set<SipAccountId> =
        prefs(context).all.keys.filter { it.startsWith(PENDING_REVOKE_PREFIX) }
            .map { SipAccountId(it.removePrefix(PENDING_REVOKE_PREFIX)) }.toSet()

    fun isRevocationPending(context: Context, accountId: SipAccountId): Boolean =
        prefs(context).contains(PENDING_REVOKE_PREFIX + accountId.value)

    /**
     * DELETEs the relay registration, which also revokes the device's credentials. The local
     * credential is erased once the relay confirms or no longer accepts it; on a network/relay
     * failure it is kept, still marked pending, so the revocation can be retried.
     */
    fun revokePending(context: Context, accountId: SipAccountId): RevokeOutcome {
        val username = prefs(context).getString(PENDING_REVOKE_PREFIX + accountId.value, null)
            ?: return RevokeOutcome.AlreadyInvalid
        val credential = credential(context, accountId, username)
        val endpoint = credential?.let { deviceEndpoint(BuildConfig.PUSH_RELAY_URL, it.device) }
        // Without a relay URL or a readable credential there is nothing the relay could accept.
        val outcome = if (endpoint == null) {
            RevokeOutcome.AlreadyInvalid
        } else {
            revokeOutcomeFor(request(endpoint, "DELETE", credential.token, null))
        }
        if (outcome !is RevokeOutcome.Retry) {
            PushCredentialStore.create(context).delete(accountId)
            prefs(context).edit { remove(PENDING_REVOKE_PREFIX + accountId.value) }
        }
        Log.i(TAG, "account=${accountId.value} push credential revocation outcome=$outcome")
        return outcome
    }

    private fun request(endpoint: URL, method: String, bearer: String, body: String?): Response {
        val connection = runCatching { endpoint.openConnection() as HttpURLConnection }
            .getOrElse { return Response(null) }
        return try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            Response(connection.responseCode, parseRetryAfter(connection.getHeaderField("Retry-After")))
        } catch (e: Exception) {
            Log.w(TAG, "relay $method failed: ${e.javaClass.simpleName}")
            Response(null)
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun statusKey(accountId: SipAccountId) = "relay_status.${accountId.value}"
}

sealed interface RevokeOutcome {
    /** The relay removed the registration and revoked the credentials. */
    data object Confirmed : RevokeOutcome

    /** The relay no longer accepts the credential (expired/revoked/unknown): nothing to remove. */
    data object AlreadyInvalid : RevokeOutcome

    /** Network failure, rate limit or relay error; keep the credential and retry. */
    data class Retry(val retryAfterSeconds: Long?) : RevokeOutcome
}

/** Maps a relay DELETE response to whether the local credential can be erased. */
internal fun revokeOutcomeFor(response: PushRelayClient.Response): RevokeOutcome = when (response.code) {
    204, 404 -> RevokeOutcome.Confirmed
    401, 403 -> RevokeOutcome.AlreadyInvalid
    else -> RevokeOutcome.Retry(response.retryAfterSeconds)
}

/** 401/403 mean the relay refused this credential; only a newly imported one can succeed. */
internal fun isTerminalRejection(status: PushRelayClient.Status): Boolean =
    status == PushRelayClient.Status.CREDENTIAL_REJECTED || status == PushRelayClient.Status.SCOPE_REJECTED

/** Maps a relay v2 device-registration response code to a client status. */
internal fun statusFor(httpCode: Int): PushRelayClient.Status = when (httpCode) {
    in 200..299 -> PushRelayClient.Status.REGISTERED
    401 -> PushRelayClient.Status.CREDENTIAL_REJECTED
    403 -> PushRelayClient.Status.SCOPE_REJECTED
    else -> PushRelayClient.Status.FAILED
}

/** Delta-seconds form only (the relay sends integers); HTTP dates are ignored. */
internal fun parseRetryAfter(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

/**
 * Builds `{base}/v1/devices/{device}`. Returns null unless the relay URL is HTTPS: the bearer
 * credential must never travel in clear text.
 */
internal fun deviceEndpoint(baseUrl: String, device: String): URL? {
    val base = baseUrl.trim().trimEnd('/')
    if (!base.startsWith("https://", ignoreCase = true)) return null
    return runCatching { URL("$base/v1/devices/${URLEncoder.encode(device, "UTF-8")}") }.getOrNull()
}
