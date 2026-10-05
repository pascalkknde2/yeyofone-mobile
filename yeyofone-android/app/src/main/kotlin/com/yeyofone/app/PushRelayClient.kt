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
 * Registers this device's FCM token with yeyofone-push-relay v2 (a separate project - this repo
 * stays client-only per MAIN-IDEA.md) using a per-device, operator-issued credential imported for
 * each SIP account. No shared relay secret ships in the app. Inert whenever
 * BuildConfig.PUSH_RELAY_URL is blank (the default) or an account has no credential.
 *
 * Called when the FCM token changes (YeyoFoneFirebaseMessagingService.onNewToken), when an
 * account becomes enabled (YeyoFoneApplication), and right after a credential is imported.
 */
internal object PushRelayClient {
    private const val TAG = "YeyoFonePush"
    private const val PREFS_NAME = "yeyofone_push"
    private const val KEY_TOKEN = "fcm_token"
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

        /** Network or relay failure; retried on the next token change or account update. */
        FAILED,
    }

    fun saveToken(context: Context, token: String) {
        prefs(context).edit { putString(KEY_TOKEN, token) }
    }

    fun cachedToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun lastStatus(context: Context, accountId: SipAccountId): Status? =
        prefs(context).getString(statusKey(accountId), null)?.let { runCatching { Status.valueOf(it) }.getOrNull() }

    /** Validates and stores an imported credential, rejecting one issued for another account. */
    fun importCredential(context: Context, account: SipAccount, json: String): PushCredential {
        val credential = PushCredential.parse(json, account.username)
        val chars = json.trim().toCharArray()
        try {
            PushCredentialStore.create(context).save(account.id, chars)
        } finally {
            chars.fill('\u0000')
        }
        prefs(context).edit { remove(statusKey(account.id)) }
        return credential
    }

    fun credential(context: Context, account: SipAccount): PushCredential? {
        val stored = PushCredentialStore.create(context).read(account.id) ?: return null
        return try {
            runCatching { PushCredential.parse(String(stored), account.username) }
                .onFailure { Log.w(TAG, "account=${account.id.value} stored push credential is unusable") }
                .getOrNull()
        } finally {
            stored.fill('\u0000')
        }
    }

    /**
     * Blocking; call off the main thread. A credential the relay already rejected (expired,
     * revoked or out of scope) is not replayed; it stays rejected until a new one is imported.
     */
    fun register(context: Context, account: SipAccount, fcmToken: String): Status {
        lastStatus(context, account.id)?.takeIf(::isTerminalRejection)?.let { return it }
        val credential = credential(context, account)
        val endpoint = credential?.let { deviceEndpoint(BuildConfig.PUSH_RELAY_URL, it.device) }
        if (endpoint == null) return Status.NOT_CONFIGURED
        val status = runCatching {
            request(endpoint, "PUT", credential.token, JSONObject().put("token", fcmToken).toString())
        }.fold(::statusFor) {
            Log.w(TAG, "account=${account.id.value} relay registration failed", it)
            Status.FAILED
        }
        Log.i(TAG, "account=${account.id.value} relay registration status=$status")
        prefs(context).edit { putString(statusKey(account.id), status.name) }
        return status
    }

    /**
     * Deletes the relay registration (which also revokes the device's credentials) and then the
     * local credential. The local copy is removed even if the relay is unreachable; returns
     * whether the relay confirmed removal. Blocking; call off the main thread.
     */
    fun removeCredential(context: Context, account: SipAccount): Boolean {
        val credential = credential(context, account)
        val endpoint = credential?.let { deviceEndpoint(BuildConfig.PUSH_RELAY_URL, it.device) }
        val confirmed = endpoint != null && runCatching {
            request(endpoint, "DELETE", credential.token, null)
        }.getOrNull().let { it == 204 || it == 404 }
        PushCredentialStore.create(context).delete(account.id)
        prefs(context).edit { remove(statusKey(account.id)) }
        Log.i(TAG, "account=${account.id.value} push credential removed relayConfirmed=$confirmed")
        return confirmed
    }

    private fun request(endpoint: URL, method: String, bearer: String, body: String?): Int {
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
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
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun statusKey(accountId: SipAccountId) = "relay_status.${accountId.value}"
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

/**
 * Builds `{base}/v1/devices/{device}`. Returns null unless the relay URL is HTTPS: the bearer
 * credential must never travel in clear text.
 */
internal fun deviceEndpoint(baseUrl: String, device: String): URL? {
    val base = baseUrl.trim().trimEnd('/')
    if (!base.startsWith("https://", ignoreCase = true)) return null
    return runCatching { URL("$base/v1/devices/${URLEncoder.encode(device, "UTF-8")}") }.getOrNull()
}
