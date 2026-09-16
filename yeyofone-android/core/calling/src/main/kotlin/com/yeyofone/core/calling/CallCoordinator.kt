package com.yeyofone.core.calling

import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallEndReason
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.VoipError
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.MediaManager
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.SipCallGateway
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CallCoordinator(
    private val accounts: AccountRepository,
    private val gateway: SipCallGateway,
    scope: CoroutineScope,
) : CallManager, MediaManager {
    private val mutableSessions = MutableStateFlow<List<CallSession>>(emptyList())
    override val sessions: StateFlow<List<CallSession>> = mutableSessions.asStateFlow()
    private val mediaStates = mutableMapOf<CallId, MutableStateFlow<MediaState>>()

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
    }

    override suspend fun call(accountId: SipAccountId, destination: String): CallId {
        val account = accounts.observeAccount(accountId).first()
            ?: error("Account does not exist")
        val uri = when {
            destination.startsWith("sip:", ignoreCase = true) -> destination
            destination.contains('@') -> "sip:$destination"
            else -> "sip:$destination@${account.server.domain}"
        }
        val nativeId = gateway.makeCall(accountId, uri)
        val id = CallId(nativeId)
        mutableSessions.update {
            it + CallSession(
                id = id,
                accountId = accountId,
                remoteUri = uri,
                direction = CallDirection.OUTGOING,
                state = CallState.Preparing,
                createdAt = Instant.now(),
            )
        }
        return id
    }

    override suspend fun answer(callId: CallId) {
        gateway.answer(callId.value)
    }

    override suspend fun reject(callId: CallId) {
        gateway.hangup(callId.value)
    }

    override suspend fun end(callId: CallId) {
        gateway.hangup(callId.value)
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

    private fun onCallEvent(event: NativeCallEvent) {
        val id = CallId(event.callId)
        val state = event.toCallState()
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
                    session.copy(
                        state = state,
                        connectedAt = session.connectedAt ?: now.takeIf { state == CallState.Connected },
                        endedAt = session.endedAt ?: now.takeIf { state is CallState.Disconnected || state is CallState.Failed },
                    )
                }
            }
        }
    }
}

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
