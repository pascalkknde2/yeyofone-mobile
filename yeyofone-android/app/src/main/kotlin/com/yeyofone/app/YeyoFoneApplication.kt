package com.yeyofone.app

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.yeyofone.core.account.RoomAccountRepository
import com.yeyofone.core.account.RoomCallHistoryRepository
import com.yeyofone.core.calling.CallCoordinator
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.registration.AndroidNetworkStatus
import com.yeyofone.core.registration.RegistrationCoordinator
import com.yeyofone.core.voip.pjsip.PjsipEngine
import com.yeyofone.core.voip.pjsip.PjsipEngineConfiguration
import com.yeyofone.core.voip.pjsip.SipTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class YeyoFoneApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val accountRepository by lazy { RoomAccountRepository.create(this) }
    val callHistory by lazy { RoomCallHistoryRepository.create(this) }
    private val engine by lazy {
        PjsipEngine.create(PjsipEngineConfiguration(transports = setOf(SipTransport.UDP, SipTransport.TCP, SipTransport.TLS)))
    }
    val registration by lazy {
        RegistrationCoordinator(
            accountRepository,
            RoomAccountRepository.secretProvider(this),
            engine,
            AndroidNetworkStatus(this),
            applicationScope,
        )
    }
    val callManager by lazy { CallCoordinator(accountRepository, engine, applicationScope, callHistory) }
    val audioRouteManager by lazy { AndroidAudioRouteManager(this) }
    internal val pushRegistrar by lazy { PushRegistrar(this, applicationScope) }
    internal val relayPushHandler by lazy { RelayPushHandler(this, applicationScope, callManager) }

    override fun onCreate() {
        super.onCreate()
        // Starting here (not just from MainActivity.onCreate()) ensures the SIP registration and
        // incoming-call notification path come up whenever the process is created for any reason
        // - including a push-triggered cold start - not only when the user opens the UI.
        IncomingCallService.start(this)
        pushRegistrar.resumePendingRevocations()
        relayPushHandler.start()
        // onNewToken is only guaranteed when Firebase creates or rotates a token. Fetch the
        // existing token too, so accounts added after the initial FCM registration can enroll.
        runCatching { FirebaseMessaging.getInstance().token }
            .onSuccess { task ->
                task.addOnSuccessListener(::onFcmTokenAvailable)
                    .addOnFailureListener { error ->
                        Log.w(TAG, "Unable to fetch the FCM token", error)
                    }
            }
            .onFailure { error -> Log.w(TAG, "Firebase Messaging is unavailable", error) }
        applicationScope.launch {
            engine.start()
            var previous = emptyMap<SipAccountId, SipAccount>()
            accountRepository.observeAccounts().collectLatest { accounts ->
                val current = accounts.associateBy { it.id }
                previous.values.filter { it.id !in current }.forEach { removed ->
                    registration.unregister(removed.id)
                    // The relay registration must not outlive the account.
                    pushRegistrar.revoke(removed.id, removed.username)
                }
                accounts.forEach { account ->
                    val before = previous[account.id]
                    val changed = before != account
                    if (account.enabled && changed) registration.register(account.id)
                    if (!account.enabled && changed) registration.unregister(account.id)
                    when {
                        !changed -> Unit
                        // The credential was issued for the old extension; it cannot be reused.
                        before != null && before.username != account.username ->
                            pushRegistrar.revoke(account.id, before.username)
                        // Signed out/disabled: stop wakes for this account and revoke its device.
                        !account.enabled -> pushRegistrar.revoke(account.id, account.username)
                        // Covers a token that arrived before this account existed or was enabled;
                        // onNewToken fires only on rotation. Also re-validates on every start.
                        else -> pushRegistrar.register(account)
                    }
                }
                previous = current
            }
        }
    }

    internal fun onFcmTokenAvailable(token: String) {
        if (token.isBlank()) return
        PushRelayClient.saveToken(this, token)
        applicationScope.launch {
            pushRegistrar.onTokenRefreshed(accountRepository.observeAccounts().first())
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        engine.close()
        super.onTerminate()
    }

    private companion object {
        const val TAG = "YeyoFonePush"
    }
}
