package com.yeyofone.core.voip

import com.yeyofone.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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

data class NativeCallEvent(
    val callId: String,
    val accountId: SipAccountId,
    val remoteUri: String,
    val direction: CallDirection,
    val invState: Int,
    val lastStatusCode: Int,
    val lastReason: String?,
    /** Relay call ID from the PBX's `X-Yeyo-Call-ID` INVITE header; correlation only, never trust. */
    val relayCallId: String? = null,
    /**
     * The PBX ended the call with "Reason: SIP;cause=200" ("Call completed elsewhere"): another
     * device of the account took it over or answered it.
     */
    val endedElsewhere: Boolean = false,
)

/** A call's SIP dialog as the PBX sees it, which another device needs to take the call over. */
data class CallDialog(
    val callId: String,
    val localTag: String,
    val remoteTag: String,
    /** The other party's user part, such as an extension; empty when unknown. */
    val remoteUser: String,
)

/** A call-handoff message (JSON) from a device of [accountId], delivered through the PBX. */
data class HandoffMessage(val accountId: SipAccountId, val body: String)

/**
 * Call handoff between devices of the same account: messages are SIP MESSAGEs to the account's
 * own address, and a takeover is a call whose INVITE carries Replaces for another device's dialog.
 */
interface SipHandoffGateway {
    val handoffMessages: Flow<HandoffMessage>
    suspend fun sendHandoff(accountId: SipAccountId, body: String)
    suspend fun callDialog(callId: String): CallDialog?
    /** Places a call that replaces [replaces], a dialog announced by another device; returns the call ID. */
    suspend fun makeReplacingCall(accountId: SipAccountId, destination: String, replaces: CallDialog): String
}

data class NativeMediaEvent(
    val callId: String,
    val muted: Boolean,
    val held: Boolean,
)

data class NativeTransferEvent(
    val callId: String,
    val statusCode: Int,
    val safeReason: String?,
    val final: Boolean,
)

/** Platform-neutral gateway; implementations keep all PJSUA2 objects internal. */
interface SipCallGateway {
    val callEvents: Flow<NativeCallEvent>
    val mediaEvents: Flow<NativeMediaEvent> get() = emptyFlow()
    val transferEvents: Flow<NativeTransferEvent> get() = emptyFlow()
    suspend fun makeCall(accountId: SipAccountId, destination: String): String
    suspend fun answer(callId: String)
    suspend fun hangup(callId: String)
    suspend fun sendDtmf(callId: String, digit: Char): Unit = error("DTMF is not supported")
    suspend fun transfer(callId: String, destination: String): Unit = error("Transfer is not supported")
    suspend fun attendedTransfer(callId: String, destinationCallId: String): Unit =
        error("Attended transfer is not supported")
    suspend fun setMuted(callId: String, muted: Boolean): Unit = error("Mute is not supported")
    suspend fun setHeld(callId: String, held: Boolean): Unit = error("Hold is not supported")
}

interface CallManager {
    val sessions: StateFlow<List<CallSession>>
    suspend fun call(accountId: SipAccountId, destination: String): CallId
    suspend fun answer(callId: CallId)
    suspend fun reject(callId: CallId)
    suspend fun end(callId: CallId)
    suspend fun sendDtmf(callId: CallId, digit: Char)
    suspend fun transfer(callId: CallId, destination: String)
    suspend fun attendedTransfer(callId: CallId, destinationCallId: CallId)
}

interface CallHistoryRepository {
    fun observeHistory(): Flow<List<CallHistoryEntry>>
    suspend fun upsert(entry: CallHistoryEntry)
    suspend fun delete(id: CallHistoryId)
    suspend fun clear()
}

interface MediaManager {
    fun observe(callId: CallId): StateFlow<MediaState>
    suspend fun setMuted(callId: CallId, muted: Boolean)
    suspend fun setHeld(callId: CallId, held: Boolean)
}

interface AudioRouteManager {
    val availableRoutes: StateFlow<List<AudioRoute>>
    val selectedRoute: StateFlow<AudioRoute?>
    /** Puts the platform audio stack into the mode required for two-way call audio. */
    suspend fun prepareForCall() = Unit
    suspend fun select(route: AudioRoute)
}

interface SipDiagnostics {
    fun observeCallQuality(callId: CallId): Flow<CallQualityMetrics>
}
