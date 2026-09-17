package com.yeyofone.core.calling

import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallEndReason
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.TransferState
import com.yeyofone.core.model.VoipError
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.CallHistoryRepository
import com.yeyofone.core.voip.MediaManager
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.NativeTransferEvent
import com.yeyofone.core.voip.SipCallGateway
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallCoordinator(
    private val accounts: AccountRepository,
    private val gateway: SipCallGateway,
    scope: CoroutineScope,
    private val history: CallHistoryRepository? = null,
) : CallManager, MediaManager {
    private val mutableSessions = MutableStateFlow<List<CallSession>>(emptyList())
    override val sessions: StateFlow<List<CallSession>> = mutableSessions.asStateFlow()
    private val mediaStates = mutableMapOf<CallId, MutableStateFlow<MediaState>>()
    private val locallyEndedCalls = ConcurrentHashMap.newKeySet<CallId>()
    private val commandMutex = Mutex()

    init {
        scope.launch {
            gateway.callEvents.collect { event -> onCallEvent(event) }
        }
        scope.launch {
            gateway.mediaEvents.collect { event ->
                val id = CallId(event.callId)
                mediaState(id).value = mediaState(id).value.copy(muted = event.muted, held = event.held)
            }
        }
        scope.launch {
            gateway.transferEvents.collect { event -> onTransferEvent(event) }
        }
    }

    override suspend fun call(accountId: SipAccountId, destination: String): CallId {
        val account = accounts.observeAccount(accountId).first()
            ?: error("Account does not exist")
        val uri = destination.toSipUri(account.server.domain)
        val nativeId = gateway.makeCall(accountId, uri)
        val id = CallId(nativeId)
        val session = CallSession(
            id = id,
            accountId = accountId,
            remoteUri = uri,
            direction = CallDirection.OUTGOING,
            state = CallState.Preparing,
            createdAt = Instant.now(),
        )
        mutableSessions.update { it + session }
        history?.upsert(session.toHistoryEntry())
        return id
    }

    override suspend fun answer(callId: CallId) {
        commandMutex.withLock {
            val session = sessions.value.firstOrNull { it.id == callId } ?: return
            if (session.state != CallState.Incoming && session.state != CallState.Ringing) return
            mutableSessions.update { current ->
                current.map { if (it.id == callId) it.copy(state = CallState.Answering) else it }
            }
            try {
                gateway.answer(callId.value)
            } catch (error: Exception) {
                mutableSessions.update { current ->
                    current.map {
                        // A concurrent native event (e.g. the caller cancelling) may have already
                        // moved this session on while gateway.answer() was in flight - don't
                        // resurrect a call that's already correctly Disconnected/Failed.
                        if (it.id == callId && it.state == CallState.Answering) {
                            it.copy(state = CallState.Failed(VoipError.Native(error.message ?: "Answer failed")))
                        } else it
                    }
                }
            }
        }
    }

    override suspend fun reject(callId: CallId) {
        commandMutex.withLock {
            val session = sessions.value.firstOrNull { it.id == callId } ?: return
            if (session.state == CallState.Disconnecting || session.state.isTerminal()) return
            if (session.state == CallState.Incoming || session.state == CallState.Ringing) {
                mutableSessions.update { current ->
                    current.map { if (it.id == callId) it.copy(state = CallState.Disconnecting) else it }
                }
            }
            try {
                gateway.hangup(callId.value)
            } catch (error: Exception) {
                mutableSessions.update { current ->
                    current.map {
                        // As with answer(), a concurrent native event may have already resolved
                        // this call while gateway.hangup() was in flight.
                        if (it.id == callId && !it.state.isTerminal()) {
                            it.copy(state = CallState.Failed(VoipError.Native(error.message ?: "Reject failed")))
                        } else it
                    }
                }
            }
        }
    }

    override suspend fun end(callId: CallId) {
        val session = sessions.value.firstOrNull { it.id == callId } ?: return
        if (session.state == CallState.Disconnecting || session.state.isTerminal()) return
        locallyEndedCalls += callId
        mutableSessions.update { current ->
            current.map { if (it.id == callId) it.copy(state = CallState.Disconnecting) else it }
        }
        try {
            gateway.hangup(callId.value)
        } catch (error: Exception) {
            locallyEndedCalls -= callId
            mutableSessions.update { current ->
                current.map {
                    if (it.id == callId && it.state == CallState.Disconnecting) {
                        it.copy(state = CallState.Failed(VoipError.Native(error.message ?: "Hangup failed")))
                    } else it
                }
            }
        }
    }

    override suspend fun sendDtmf(callId: CallId, digit: Char) {
        require(digit in DTMF_DIGITS) { "Unsupported DTMF digit" }
        val session = sessions.value.firstOrNull { it.id == callId }
            ?: error("Call does not exist")
        check(session.state == CallState.Connected) { "DTMF requires a connected call" }
        check(!mediaState(callId).value.held) { "DTMF is unavailable while the call is held" }
        gateway.sendDtmf(callId.value, digit)
    }

    override suspend fun transfer(callId: CallId, destination: String) {
        require(destination.isNotBlank()) { "Transfer destination must not be blank" }
        val session = sessions.value.firstOrNull { it.id == callId }
            ?: error("Call does not exist")
        check(session.state == CallState.Connected) { "Transfer requires a connected call" }
        check(!mediaState(callId).value.held) { "Transfer is unavailable while the call is held" }
        val account = accounts.observeAccount(session.accountId).first()
            ?: error("Account does not exist")
        val uri = destination.toSipUri(account.server.domain)
        mutableSessions.update { current ->
            current.map {
                if (it.id == callId) it.copy(state = CallState.Transferring, transfer = TransferState.Pending(uri)) else it
            }
        }
        try {
            gateway.transfer(callId.value, uri)
        } catch (error: Exception) {
            mutableSessions.update { current ->
                current.map {
                    if (it.id == callId) {
                        it.copy(
                            state = CallState.Connected,
                            transfer = TransferState.Failed(uri, null, error.message?.take(120)),
                        )
                    } else it
                }
            }
        }
    }

    override suspend fun attendedTransfer(callId: CallId, destinationCallId: CallId) {
        require(callId != destinationCallId) { "Attended transfer requires two different calls" }
        commandMutex.withLock {
            val source = sessions.value.firstOrNull { it.id == callId }
                ?: error("Source call does not exist")
            val destination = sessions.value.firstOrNull { it.id == destinationCallId }
                ?: error("Destination call does not exist")
            check(source.accountId == destination.accountId) { "Calls must use the same SIP account" }
            check(source.state == CallState.Connected) { "Source call must be connected" }
            check(destination.state == CallState.Connected) { "Destination call must be connected" }
            check(mediaState(callId).value.held) { "Source call must be held" }
            check(source.transfer !is TransferState.Pending) { "Transfer is already pending" }
            val target = destination.remoteUri
            mutableSessions.update { current ->
                current.map {
                    if (it.id == callId) it.copy(state = CallState.Transferring, transfer = TransferState.Pending(target)) else it
                }
            }
            try {
                gateway.attendedTransfer(callId.value, destinationCallId.value)
            } catch (error: Exception) {
                mutableSessions.update { current ->
                    current.map {
                        // Only resurrect the session if it's still where we left it - a concurrent
                        // native disconnect event may have already moved it on while the transfer
                        // attempt was in flight.
                        if (it.id == callId && it.state == CallState.Transferring) {
                            it.copy(
                                state = CallState.Connected,
                                transfer = TransferState.Failed(target, null, error.message?.take(120)),
                            )
                        } else it
                    }
                }
            }
        }
    }

    override fun observe(callId: CallId): StateFlow<MediaState> = mediaState(callId).asStateFlow()

    override suspend fun setMuted(callId: CallId, muted: Boolean) {
        gateway.setMuted(callId.value, muted)
    }

    override suspend fun setHeld(callId: CallId, held: Boolean) {
        gateway.setHeld(callId.value, held)
    }

    private fun mediaState(callId: CallId): MutableStateFlow<MediaState> =
        mediaStates.getOrPut(callId) { MutableStateFlow(MediaState()) }

    private suspend fun onCallEvent(event: NativeCallEvent) {
        val id = CallId(event.callId)
        val nativeState = event.toCallState()
        val state = if (event.invState == PJSIP_INV_STATE_DISCONNECTED && locallyEndedCalls.remove(id)) {
            CallState.Disconnected(CallEndReason.LOCAL_HANGUP)
        } else {
            nativeState
        }
        val now = Instant.now()
        mutableSessions.update { sessions ->
            if (sessions.none { it.id == id }) {
                sessions + CallSession(
                    id = id,
                    accountId = event.accountId,
                    remoteUri = event.remoteUri,
                    direction = event.direction,
                    state = state,
                    createdAt = now,
                    connectedAt = now.takeIf { state == CallState.Connected },
                    endedAt = now.takeIf { state is CallState.Disconnected || state is CallState.Failed },
                )
            } else {
                sessions.map { session ->
                    if (session.id != id) return@map session
                    if ((session.state == CallState.Answering ||
                            session.state == CallState.Connecting ||
                            session.state == CallState.Connected) &&
                        (state == CallState.Incoming || state == CallState.Ringing)
                    ) return@map session
                    session.copy(
                        state = state,
                        connectedAt = session.connectedAt ?: now.takeIf { state == CallState.Connected },
                        endedAt = session.endedAt ?: now.takeIf { state is CallState.Disconnected || state is CallState.Failed },
                    )
                }
            }
        }
        mutableSessions.value.firstOrNull { it.id == id }?.let { history?.upsert(it.toHistoryEntry()) }
    }

    private fun onTransferEvent(event: NativeTransferEvent) {
        val id = CallId(event.callId)
        mutableSessions.update { sessions ->
            sessions.map { session ->
                if (session.id != id) return@map session
                val destination = when (val transfer = session.transfer) {
                    is TransferState.Pending -> transfer.destination
                    is TransferState.Succeeded -> transfer.destination
                    is TransferState.Failed -> transfer.destination
                    TransferState.Idle -> return@map session
                }
                when {
                    !event.final -> session
                    event.statusCode in 200..299 -> session.copy(
                        transfer = TransferState.Succeeded(destination),
                    )
                    else -> session.copy(
                        state = CallState.Connected,
                        transfer = TransferState.Failed(destination, event.statusCode, event.safeReason),
                    )
                }
            }
        }
    }
}

private fun CallState.isTerminal(): Boolean = this is CallState.Disconnected || this is CallState.Failed

private fun String.toSipUri(domain: String): String = when {
    startsWith("sip:", ignoreCase = true) -> this
    contains('@') -> "sip:$this"
    else -> "sip:$this@$domain"
}

private fun CallSession.toHistoryEntry() = CallHistoryEntry(
    id = CallHistoryId(id.value),
    accountId = accountId,
    remoteUri = remoteUri,
    direction = direction,
    startedAt = createdAt,
    connectedAt = connectedAt,
    endedAt = endedAt,
    endReason = when (val current = state) {
        is CallState.Disconnected -> current.reason
        is CallState.Failed -> CallEndReason.UNKNOWN
        else -> null
    },
)

private fun NativeCallEvent.toCallState(): CallState = when (invState) {
    PJSIP_INV_STATE_CALLING -> CallState.Calling
    PJSIP_INV_STATE_INCOMING -> CallState.Incoming
    PJSIP_INV_STATE_EARLY -> CallState.Ringing
    PJSIP_INV_STATE_CONNECTING -> CallState.Connecting
    PJSIP_INV_STATE_CONFIRMED -> CallState.Connected
    PJSIP_INV_STATE_DISCONNECTED -> disconnectedState(lastStatusCode)
    else -> CallState.Failed(VoipError.Native("Unknown call state"))
}

private fun disconnectedState(statusCode: Int): CallState = when (statusCode) {
    486, 600 -> CallState.Disconnected(CallEndReason.BUSY)
    603 -> CallState.Disconnected(CallEndReason.DECLINED)
    487 -> CallState.Disconnected(CallEndReason.CANCELLED)
    408 -> CallState.Disconnected(CallEndReason.TIMEOUT)
    in 200..299 -> CallState.Disconnected(CallEndReason.REMOTE_HANGUP)
    0 -> CallState.Disconnected(CallEndReason.NETWORK_FAILURE)
    else -> CallState.Failed(VoipError.SipResponse(statusCode, "Call failed"))
}

// Mirrors org.pjsip.pjsua2.pjsip_inv_state, kept dependency-free here since this module has no
// PJSIP binding on its classpath.
private const val PJSIP_INV_STATE_NULL = 0
private const val PJSIP_INV_STATE_CALLING = PJSIP_INV_STATE_NULL + 1
private const val PJSIP_INV_STATE_INCOMING = PJSIP_INV_STATE_CALLING + 1
private const val PJSIP_INV_STATE_EARLY = PJSIP_INV_STATE_INCOMING + 1
private const val PJSIP_INV_STATE_CONNECTING = PJSIP_INV_STATE_EARLY + 1
private const val PJSIP_INV_STATE_CONFIRMED = PJSIP_INV_STATE_CONNECTING + 1
private const val PJSIP_INV_STATE_DISCONNECTED = PJSIP_INV_STATE_CONFIRMED + 1
private const val DTMF_DIGITS = "0123456789*#"
