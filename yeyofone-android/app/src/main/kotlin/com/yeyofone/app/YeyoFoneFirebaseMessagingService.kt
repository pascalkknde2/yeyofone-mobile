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
        // A valid, fresh, non-duplicate wake (re)starts the foreground service, which restores
        // SIP registration so the real INVITE can arrive; the push itself never creates a call.
        // Cancels are reconciled against SIP state. See RelayPushHandler.
        (application as YeyoFoneApplication).relayPushHandler.onMessage(message.data)
    }

    private companion object {
        const val TAG = "YeyoFonePush"
    }
}
