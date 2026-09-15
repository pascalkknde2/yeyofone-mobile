package com.yeyofone.core.voip

import com.yeyofone.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface EngineState {
    data object Uninitialized : EngineState
    data object Initializing : EngineState
    data object Running : EngineState
    data object Stopping : EngineState
    data object Stopped : EngineState
    data class Failed(val error: VoipError) : EngineState
}

interface SipEngine {
    val state: StateFlow<EngineState>
    suspend fun start()
    suspend fun stop()
}

interface SipAccountManager {
    val accounts: StateFlow<List<SipAccount>>
    suspend fun enable(accountId: SipAccountId)
    suspend fun disable(accountId: SipAccountId)
}

interface RegistrationManager {
    fun observe(accountId: SipAccountId): StateFlow<RegistrationState>
    fun observeDetails(accountId: SipAccountId): StateFlow<RegistrationDetails>
    suspend fun register(accountId: SipAccountId)
    suspend fun unregister(accountId: SipAccountId)
}

data class NativeRegistrationEvent(
    val accountId: SipAccountId,
    val sipCode: Int?,
    val safeReason: String?,
    val expirationSeconds: Long,
)

/** Platform-neutral gateway; implementations keep all PJSUA2 objects internal. */
interface SipRegistrationGateway {
    val events: Flow<NativeRegistrationEvent>
    suspend fun createOrUpdate(account: SipAccount, password: CharArray)
    suspend fun setRegistration(accountId: SipAccountId, renew: Boolean)
    suspend fun remove(accountId: SipAccountId)
}

interface CallManager {
    val sessions: StateFlow<List<CallSession>>
    suspend fun call(accountId: SipAccountId, destination: String): CallId
    suspend fun answer(callId: CallId)
    suspend fun reject(callId: CallId)
    suspend fun end(callId: CallId)
}

interface MediaManager {
    fun observe(callId: CallId): StateFlow<MediaState>
    suspend fun setMuted(callId: CallId, muted: Boolean)
    suspend fun setHeld(callId: CallId, held: Boolean)
}

interface AudioRouteManager {
    val availableRoutes: StateFlow<List<AudioRoute>>
    val selectedRoute: StateFlow<AudioRoute?>
    suspend fun select(route: AudioRoute)
}

interface SipDiagnostics {
    fun observeCallQuality(callId: CallId): Flow<CallQualityMetrics>
}
