package com.yeyofone.app.data.model

import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallSession
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class CallSummary(
    val callerName: String,
    val remoteUri: String,
    val direction: CallDirection,
    val durationSeconds: Long,
    val timestamp: String,
    val wasAnswered: Boolean,
) {
    val formattedDuration: String get() = formatCallDuration(durationSeconds)
}

fun CallSession.toCallSummary(): CallSummary {
    val finishedAt = endedAt ?: createdAt
    val duration = connectedAt?.let { max(0, finishedAt.epochSecond - it.epochSecond) } ?: 0
    return CallSummary(
        callerName = remoteUri.toSipIdentity().displayName,
        remoteUri = remoteUri,
        direction = direction,
        durationSeconds = duration,
        timestamp = finishedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
        wasAnswered = connectedAt != null,
    )
}

internal fun formatCallDuration(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
