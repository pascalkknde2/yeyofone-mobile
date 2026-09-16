package com.yeyofone.core.model

import java.time.Duration
import java.time.Instant

data class CallHistoryEntry(
    val id: CallHistoryId,
    val accountId: SipAccountId,
    val remoteUri: String,
    val direction: CallDirection,
    val startedAt: Instant,
    val connectedAt: Instant? = null,
    val endedAt: Instant? = null,
    val endReason: CallEndReason? = null,
) {
    val missed: Boolean
        get() = direction == CallDirection.INCOMING && connectedAt == null && endedAt != null

    val duration: Duration
        get() = Duration.between(connectedAt ?: startedAt, endedAt ?: Instant.now()).coerceAtLeast(Duration.ZERO)
}
