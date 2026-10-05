package com.yeyofone.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState

/**
 * The incoming-call notification's Accept action opens [MainActivity] directly (no broadcast
 * trampoline, which Android 12+ blocks). It goes through the non-exported `AcceptCallActivity`
 * alias: MainActivity is exported as the launcher, so an accept request is honoured only when it
 * arrives through the alias, which other apps cannot start. That prevents another app from
 * answering a ringing call with a crafted intent.
 */
internal object NotificationAccept {
    const val ALIAS_CLASS = "com.yeyofone.app.AcceptCallActivity"
    const val ACTION = "com.yeyofone.app.action.ACCEPT_CALL"
    private const val EXTRA_CALL_ID = "call_id"

    fun intent(context: Context, callId: CallId): Intent = Intent(ACTION)
        .setClassName(context, ALIAS_CLASS)
        .setData("yeyofone://accept/${Uri.encode(callId.value)}".toUri())
        .putExtra(EXTRA_CALL_ID, callId.value)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The call to accept, if [intent] is a genuine notification Accept request. */
    fun callIdFrom(intent: Intent?): CallId? =
        acceptRequestCallId(intent?.component?.className, intent?.action, intent?.getStringExtra(EXTRA_CALL_ID))
}

private val callIdPattern = Regex("^[A-Za-z0-9_.:-]{1,128}$")

internal fun acceptRequestCallId(componentClass: String?, action: String?, callId: String?): CallId? {
    if (componentClass != NotificationAccept.ALIAS_CLASS || action != NotificationAccept.ACTION) return null
    return callId?.takeIf(callIdPattern::matches)?.let(::CallId)
}

internal enum class AcceptAction {
    /** Unanswered incoming call and microphone granted. */
    ANSWER,

    /** Unanswered incoming call; ask for the microphone first and answer only if granted. */
    REQUEST_MICROPHONE,

    /** The call is not known yet (state still loading); keep the request briefly. */
    WAIT,

    /** Stale request: the call ended, was answered or is not an incoming call. */
    DISCARD,
}

internal fun acceptAction(callId: CallId, sessions: List<CallSession>, microphoneGranted: Boolean): AcceptAction {
    val session = sessions.firstOrNull { it.id == callId } ?: return AcceptAction.WAIT
    val unanswered = session.state == CallState.Incoming || session.state == CallState.Ringing
    if (session.direction != CallDirection.INCOMING || !unanswered) return AcceptAction.DISCARD
    return if (microphoneGranted) AcceptAction.ANSWER else AcceptAction.REQUEST_MICROPHONE
}
