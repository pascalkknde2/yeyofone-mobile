package com.yeyofone.app

import android.app.Application
import com.yeyofone.core.account.RoomAccountRepository
import com.yeyofone.core.account.RoomCallHistoryRepository
import com.yeyofone.core.calling.CallCoordinator
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
import kotlinx.coroutines.launch

class YeyoFoneApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val accountRepository by lazy { RoomAccountRepository.create(this) }
    val callHistory by lazy { RoomCallHistoryRepository.create(this) }
    private val engine by lazy {
        PjsipEngine.create(PjsipEngineConfiguration(transports = setOf(SipTransport.UDP, SipTransport.TCP)))
    }
    private val registration by lazy {
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

    override fun onCreate() {
        super.onCreate()
        // Starting here (not just from MainActivity.onCreate()) ensures the SIP registration and
        // incoming-call notification path come up whenever the process is created for any reason
        // - including a push-triggered cold start - not only when the user opens the UI.
        IncomingCallService.start(this)
        applicationScope.launch {
            engine.start()
            var previous = emptyMap<SipAccountId, Any>()
            accountRepository.observeAccounts().collectLatest { accounts ->
                val current = accounts.associate { it.id to it }
                previous.keys.minus(current.keys).forEach { registration.unregister(it) }
                accounts.forEach { account ->
                    val changed = previous[account.id] != account
                    if (account.enabled && changed) {
                        registration.register(account.id)
                        // Covers the case where the FCM token arrived before this account existed
                        // or was enabled - onNewToken only fires again on a token rotation, not on
                        // every app start, so it wouldn't otherwise register this extension.
                        PushRelayClient.cachedToken(this@YeyoFoneApplication)?.let { token ->
                            launch(Dispatchers.IO) { PushRelayClient.register(this@YeyoFoneApplication, account.username, token) }
                        }
                    }
                    if (!account.enabled && previous[account.id] != account) registration.unregister(account.id)
                }
                previous = current
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        engine.close()
        super.onTerminate()
    }
}
