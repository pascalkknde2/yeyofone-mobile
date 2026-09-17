package com.yeyofone.app.data.model

import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.SipAccountId
import java.time.Duration
import java.time.Instant

enum class CallType { INCOMING, OUTGOING, MISSED }

data class CallLog(
    val id: CallHistoryId,
    val accountId: SipAccountId,
    val remoteUri: String,
    val contactName: String,
    val contactImageUrl: String? = null,
    val callType: CallType,
    val timestamp: Instant,
    val duration: Duration? = null,
    val hasVoicemail: Boolean = false,
) {
    /** Destination used by the dialer; history may store a full SIP URI. */
    val dialDestination: String
        get() {
            val target = remoteUri.substringAfter('<', remoteUri).substringBefore('>').trim()
            val withoutScheme = when {
                target.startsWith("sips:", ignoreCase = true) -> target.substring(5)
                target.startsWith("sip:", ignoreCase = true) -> target.substring(4)
                target.startsWith("tel:", ignoreCase = true) -> target.substring(4)
                else -> target
            }
            return withoutScheme.substringBefore('@').trim().ifEmpty { remoteUri.trim() }
        }
}

internal fun CallHistoryEntry.toCallLog() = CallLog(
    id = id,
    accountId = accountId,
    remoteUri = remoteUri,
    contactName = remoteUri.displayName(),
    callType = when {
        missed -> CallType.MISSED
        direction == CallDirection.INCOMING -> CallType.INCOMING
        else -> CallType.OUTGOING
    },
    timestamp = startedAt,
    duration = duration.takeUnless { missed },
)

private fun String.displayName(): String {
    val quotedName = QUOTED_NAME.find(this)?.groupValues?.get(1)?.trim()
    if (!quotedName.isNullOrEmpty()) return quotedName
    return removePrefix("sip:")
        .substringBefore('@')
        .substringBefore('<')
        .trim()
        .ifEmpty { this }
}

private val QUOTED_NAME = Regex("\"([^\"]+)\"")
