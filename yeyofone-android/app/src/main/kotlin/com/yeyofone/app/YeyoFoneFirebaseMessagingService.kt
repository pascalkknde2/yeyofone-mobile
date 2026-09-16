package com.yeyofone.app

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wakes the process for an incoming call when it has been killed (not force-stopped) and has no
 * live SIP registration left to receive the INVITE over. See PushRelayClient for how the token
 * reaches the PBX side.
 */
class YeyoFoneFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        PushRelayClient.saveToken(this, token)
        Log.i(TAG, "FCM token refreshed")

        val app = application as YeyoFoneApplication
        scope.launch {
            val extensions = app.accountRepository.observeAccounts().first()
                .filter { it.enabled }
                .map { it.username }
            extensions.forEach { extension -> PushRelayClient.register(this@YeyoFoneFirebaseMessagingService, extension, token) }
        }
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
