package com.yeyofone.core.voip.pjsip

import com.yeyofone.core.voip.CallDialog

/** Content type of call-handoff messages, shared with the iOS and desktop apps. */
internal const val HANDOFF_CONTENT_TYPE = "application/vnd.yeyofone.handoff+json"
internal const val HANDOFF_MAX_BYTES = 1024

/**
 * True for a received BYE or CANCEL with "Reason: SIP;cause=200", which FreeSWITCH sends with
 * "Call completed elsewhere" when another device of the account takes the call over or answers it.
 */
internal fun completedElsewhere(message: String): Boolean {
    val start = message.trimStart()
    if (!start.startsWith("BYE ") && !start.startsWith("CANCEL ")) return false
    return "SIP:200" in sipReasonCauses(message).split(',')
}

/**
 * A call's dialog identifiers from a message it received, or null unless both tags are present.
 * A received request (such as the ACK of an answered call) carries the local tag in To; a
 * received response (such as the 200 OK of a placed call) carries it in From.
 */
internal fun dialogFromReceived(message: String): CallDialog? {
    val lines = message.replace("\r\n", "\n").substringBefore("\n\n").lines()
    val request = !lines.firstOrNull().orEmpty().startsWith("SIP/2.0")
    fun header(vararg names: String) = lines.drop(1).firstOrNull { line ->
        names.any { line.substringBefore(':').trim().equals(it, ignoreCase = true) }
    }?.substringAfter(':')?.trim()
    val callId = header("Call-ID", "i") ?: return null
    val from = header("From", "f") ?: return null
    val to = header("To", "t") ?: return null
    val fromTag = TAG.find(from)?.groupValues?.get(1) ?: return null
    val toTag = TAG.find(to)?.groupValues?.get(1) ?: return null
    val remote = if (request) from else to
    val dialog = CallDialog(
        callId = callId,
        localTag = if (request) toTag else fromTag,
        remoteTag = if (request) fromTag else toTag,
        remoteUser = USER.find(remote)?.groupValues?.get(1)?.takeIf { isHandoffParty(it) }.orEmpty(),
    )
    return dialog.takeIf {
        isSipToken(it.callId, 128, callId = true) && isSipToken(it.localTag, 64) && isSipToken(it.remoteTag, 64)
    }
}

/** SIP token characters that are safe inside a Replaces header value. */
internal fun isSipToken(value: String, max: Int, callId: Boolean = false): Boolean =
    value.isNotEmpty() && value.length <= max && value.all {
        it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-.!%*_+`'~" || (callId && it in "@:/")
    }

internal fun isHandoffParty(value: String): Boolean =
    value.isNotEmpty() && value.length <= 64 && value.all {
        it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+*#._-"
    }

private val TAG = Regex(";\\s*tag=([^;>\\s,]+)", RegexOption.IGNORE_CASE)
private val USER = Regex("sips?:([^@;>\\s]+)@", RegexOption.IGNORE_CASE)
