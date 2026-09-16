package com.yeyofone.app

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Wakes the process for an incoming call when it has been killed (not force-stopped) and has no
 * live SIP registration left to receive the INVITE over. Inert until a real Firebase project is
 * wired up (see app/build.gradle.kts): FCM will not route messages to this app, and this service
 * will never be instantiated, without a google-services.json and the matching PBX-side push
 * trigger. See HANDOFF.md for what the PBX side still needs to provide.
 */
class YeyoFoneFirebaseMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // TODO: send this token to the PBX/server side once it exists, so it can target this
        // device's registration when a push-worthy INVITE arrives. Persisted locally for now so
        // whatever sends it can read the current token without waiting for another rotation.
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        Log.i(TAG, "FCM token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // A push only means "an INVITE is coming or waiting" - it carries no call details.
        // Starting the foreground service (idempotent if already running) is what re-establishes
        // SIP registration via YeyoFoneApplication.onCreate() and readies the app to notify/ring
        // once the real INVITE arrives.
        Log.i(TAG, "Push wake received, starting IncomingCallService")
        IncomingCallService.start(this)
    }

    companion object {
        private const val TAG = "YeyoFonePush"
        private const val PREFS_NAME = "yeyofone_push"
        private const val KEY_TOKEN = "fcm_token"
    }
}
