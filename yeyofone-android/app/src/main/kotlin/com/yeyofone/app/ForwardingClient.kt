package com.yeyofone.app

import android.content.Context
import com.yeyofone.core.model.ForwardingState
import com.yeyofone.core.model.SipAccount
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Client for yeyofone-push-relay's call-forwarding endpoints (GET/PUT
 * /v1/accounts/<account>/forwarding). Reuses [PushRelayClient]'s HTTP plumbing and the same
 * per-device credential; inert under the same conditions (no relay URL, no credential).
 *
 * Not internal, unlike [PushRelayClient]: [Result] is passed through [YeyoFoneViewModel]'s public
 * `setForwarding` callback and the Compose settings screen, so it has to be at least as visible
 * as those.
 */
object ForwardingClient {
    sealed interface Result {
        data class Success(val state: ForwardingState) : Result
        data object NotConfigured : Result
        data object Rejected : Result
        data object NotFound : Result
        data object Invalid : Result
        data class Failed(val retryAfterSeconds: Long?) : Result
    }

    fun get(context: Context, account: SipAccount): Result {
        val (endpoint, credential) = target(context, account) ?: return Result.NotConfigured
        return resultFor(PushRelayClient.request(endpoint, "GET", credential, null))
    }

    fun set(context: Context, account: SipAccount, state: ForwardingState): Result {
        val (endpoint, credential) = target(context, account) ?: return Result.NotConfigured
        // destination must stay a JSON null (not an absent key) when disabled: the relay requires
        // both "enabled" and "destination" to be present on every PUT.
        val body = JSONObject().put("enabled", state.enabled).put("destination", state.destination ?: JSONObject.NULL)
        return resultFor(PushRelayClient.request(endpoint, "PUT", credential, body.toString()))
    }

    private fun target(context: Context, account: SipAccount): Pair<URL, String>? {
        val credential = PushRelayClient.credential(context, account) ?: return null
        val endpoint = forwardingEndpoint(BuildConfig.PUSH_RELAY_URL, credential.account) ?: return null
        return endpoint to credential.token
    }

    private fun resultFor(response: PushRelayClient.Response): Result {
        val code = response.code ?: return Result.Failed(response.retryAfterSeconds)
        return when (code) {
            in 200..299 -> parseBody(response.body) ?: Result.Failed(null)
            400 -> Result.Invalid
            401, 403 -> Result.Rejected
            404 -> Result.NotFound
            else -> Result.Failed(response.retryAfterSeconds)
        }
    }

    private fun parseBody(body: String?): Result.Success? {
        val json = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val destination = if (json.isNull("destination")) null else json.optString("destination").takeIf { it.isNotBlank() }
        return Result.Success(ForwardingState(enabled = json.optBoolean("enabled", false), destination = destination))
    }
}

/** Builds `{base}/v1/accounts/{account}/forwarding`. HTTPS-only, same rule as [deviceEndpoint]. */
internal fun forwardingEndpoint(baseUrl: String, account: String): URL? {
    val base = baseUrl.trim().trimEnd('/')
    if (!base.startsWith("https://", ignoreCase = true)) return null
    return runCatching { URL("$base/v1/accounts/${URLEncoder.encode(account, "UTF-8")}/forwarding") }.getOrNull()
}
