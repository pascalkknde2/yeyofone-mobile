package com.yeyofone.core.model

import java.time.Instant

data class CallSession(
    val id: CallId,
    val accountId: SipAccountId,
    val remoteUri: String,
    val direction: CallDirection,
    val state: CallState,
    val createdAt: Instant,
    val connectedAt: Instant? = null,
    val endedAt: Instant? = null,
)

enum class CallDirection { INCOMING, OUTGOING }

sealed interface CallState {
    data object Preparing : CallState
    data object Calling : CallState
    data object EarlyMedia : CallState
    data object Ringing : CallState
    data object Incoming : CallState
    data object Connecting : CallState
    data object Connected : CallState
    data object Held : CallState
    data object Transferring : CallState
    data object Disconnecting : CallState
    data class Disconnected(val reason: CallEndReason) : CallState
    data class Failed(val error: VoipError) : CallState
}

enum class CallEndReason {
    LOCAL_HANGUP, REMOTE_HANGUP, BUSY, DECLINED, CANCELLED, TIMEOUT, NETWORK_FAILURE,
    MEDIA_FAILURE, UNKNOWN,
}
