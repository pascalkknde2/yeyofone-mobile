package com.yeyofone.app

import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.SipAccountId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationAcceptTest {
    private val id = CallId("6c40cf1a-5487-4da1-ae61-8ba03436ec29")

    private fun session(
        state: CallState,
        direction: CallDirection = CallDirection.INCOMING,
        callId: CallId = id,
    ) = CallSession(callId, SipAccountId("acct"), "sip:2002@pbx.example.com", direction, state, Instant.EPOCH)

    @Test
    fun `only the non-exported alias with the accept action is honoured`() {
        val alias = NotificationAccept.ALIAS_CLASS
        val action = NotificationAccept.ACTION
        assertEquals(id, acceptRequestCallId(alias, action, id.value))
        // Another app can only reach the exported MainActivity, never the alias.
        assertNull(acceptRequestCallId("com.yeyofone.app.MainActivity", action, id.value))
        assertNull(acceptRequestCallId(alias, "android.intent.action.MAIN", id.value))
        assertNull(acceptRequestCallId(null, null, id.value))
    }

    @Test
    fun `malformed call ids are rejected`() {
        val alias = NotificationAccept.ALIAS_CLASS
        val action = NotificationAccept.ACTION
        assertNull(acceptRequestCallId(alias, action, null))
        assertNull(acceptRequestCallId(alias, action, ""))
        assertNull(acceptRequestCallId(alias, action, "bad id"))
        assertNull(acceptRequestCallId(alias, action, "x".repeat(129)))
    }

    @Test
    fun `unanswered incoming call is answered only with microphone access`() {
        listOf(CallState.Incoming, CallState.Ringing).forEach { state ->
            assertEquals(AcceptAction.ANSWER, acceptAction(id, listOf(session(state)), microphoneGranted = true))
            assertEquals(
                AcceptAction.REQUEST_MICROPHONE,
                acceptAction(id, listOf(session(state)), microphoneGranted = false),
            )
        }
    }

    @Test
    fun `stale or foreign requests are discarded`() {
        listOf(
            session(CallState.Connected),
            session(CallState.Answering),
            session(CallState.Disconnected(com.yeyofone.core.model.CallEndReason.CANCELLED)),
            session(CallState.Ringing, direction = CallDirection.OUTGOING),
        ).forEach { assertEquals(AcceptAction.DISCARD, acceptAction(id, listOf(it), microphoneGranted = true)) }
    }

    @Test
    fun `unknown call waits instead of answering another one`() {
        val other = session(CallState.Ringing, callId = CallId("other-call"))
        assertEquals(AcceptAction.WAIT, acceptAction(id, listOf(other), microphoneGranted = true))
        assertEquals(AcceptAction.WAIT, acceptAction(id, emptyList(), microphoneGranted = true))
    }
}
