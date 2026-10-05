package com.yeyofone.app

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Wakes the process for an incoming call when it has been killed (not force-stopped) and has no
 * live SIP registration left to receive the INVITE over. See PushRelayClient for how the token
 * reaches the PBX side.
 */
class YeyoFoneFirebaseMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed")
        (application as YeyoFoneApplication).onFcmTokenAvailable(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // A push only means "an INVITE is coming or waiting" - it carries no call details.
        // Starting the foreground service (idempotent if already running) is what re-establishes
        // SIP registration via YeyoFoneApplication.onCreate() and readies the app to notify/ring
        // once the real INVITE arrives.
        Log.i(TAG, "Push wake received, starting IncomingCallService")
        IncomingCallService.start(this)
    }

    private companion object {
        const val TAG = "YeyoFonePush"
    }
}
