package com.yeyofone.app

import android.content.Context
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Registers this device's FCM token with yeyofone-push-relay (a separate project - this repo
 * stays client-only per MAIN-IDEA.md) for a given SIP extension. Inert whenever
 * BuildConfig.PUSH_RELAY_URL is blank (the default).
 *
 * Called from two places, since a token can arrive before an account exists (or vice versa):
 * YeyoFoneFirebaseMessagingService.onNewToken() when the token changes, and
 * YeyoFoneApplication's account observer when an account becomes enabled.
 */
internal object PushRelayClient {
    private const val TAG = "YeyoFonePush"
    private const val PREFS_NAME = "yeyofone_push"
    private const val KEY_TOKEN = "fcm_token"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun cachedToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun register(context: Context, extension: String, token: String) {
        if (BuildConfig.PUSH_RELAY_URL.isBlank()) return
        runCatching {
            val url = URL("${BuildConfig.PUSH_RELAY_URL}/register")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.PUSH_RELAY_SECRET}")
                OutputStreamWriter(outputStream).use {
                    it.write(JSONObject().put("extension", extension).put("token", token).toString())
                }
                val code = responseCode
                if (code !in 200..299) Log.w(TAG, "push-relay register for $extension failed: HTTP $code")
                disconnect()
            }
        }.onFailure { Log.w(TAG, "push-relay register for $extension failed", it) }
    }
}
