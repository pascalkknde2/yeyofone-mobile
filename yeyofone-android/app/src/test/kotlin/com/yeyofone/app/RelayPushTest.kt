package com.yeyofone.app

import com.yeyofone.app.PushLedger.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayPushTest {
    private val now = 1_800_000_000.0

    private fun data(
        type: String = "incoming_call_wake",
        state: String = "ringing",
        callId: String = "call-1",
        eventId: String = "event-1",
        expiresAt: String = (now + 60).toString(),
        schema: String = "1",
    ) = mapOf(
        "schema" to schema,
        "type" to type,
        "call_id" to callId,
        "event_id" to eventId,
        "state" to state,
        "expires_at" to expiresAt,
    )

    private fun wake(eventId: String = "event-1", callId: String = "call-1", expiresAt: Double = now + 60) =
        RelayPush.parse(data(eventId = eventId, callId = callId, expiresAt = expiresAt.toString()))!!

    private fun cancel(eventId: String = "event-2", callId: String = "call-1", expiresAt: Double = now + 60) =
        RelayPush.parse(data("incoming_call_cancel", "cancelled", callId, eventId, expiresAt.toString()))!!

    @Test
    fun `relay contract messages parse`() {
        assertEquals(RelayPush.Wake("call-1", "event-1", now + 60), RelayPush.parse(data()))
        assertIs<RelayPush.Cancel>(RelayPush.parse(data("incoming_call_cancel", "answered")))
        assertIs<RelayPush.Cancel>(RelayPush.parse(data("incoming_call_cancel", "expired")))
    }

    @Test
    fun `purpose, schema, identity and expiry are validated`() {
        assertNull(RelayPush.parse(data(schema = "2")))
        assertNull(RelayPush.parse(data(type = "marketing")))
        assertNull(RelayPush.parse(data(state = "cancelled")))
        assertNull(RelayPush.parse(data("incoming_call_cancel", "ringing")))
        assertNull(RelayPush.parse(data(callId = "bad id")))
        assertNull(RelayPush.parse(data(eventId = "")))
        assertNull(RelayPush.parse(data(expiresAt = "soon")))
        assertNull(RelayPush.parse(data(expiresAt = "NaN")))
        assertNull(RelayPush.parse(emptyMap()))
    }

    @Test
    fun `fresh wake wakes once and duplicates are ignored`() {
        val ledger = PushLedger()
        assertEquals(Decision.WAKE, ledger.decide(wake(), now))
        assertEquals(Decision.DUPLICATE, ledger.decide(wake(), now + 1))
    }

    @Test
    fun `expired or implausible wakes are rejected with small clock-skew grace`() {
        val ledger = PushLedger()
        assertEquals(Decision.WAKE, ledger.decide(wake("e1", expiresAt = now - 4), now))
        assertEquals(Decision.EXPIRED, ledger.decide(wake("e2", expiresAt = now - 6), now))
        assertEquals(Decision.EXPIRED, ledger.decide(wake("e3", expiresAt = now + 3_600), now))
    }

    @Test
    fun `cancel before or after the wake prevents revival`() {
        val ledger = PushLedger()
        assertEquals(Decision.CANCEL, ledger.decide(cancel(), now))
        assertEquals(Decision.CALL_ALREADY_TERMINAL, ledger.decide(wake(), now + 1))
        assertTrue(ledger.isTerminal("call-1", now + 1))
        assertFalse(ledger.isTerminal("call-2", now + 1))
        assertEquals(Decision.WAKE, ledger.decide(wake("e9", callId = "call-2"), now + 1))
    }

    @Test
    fun `expired cancel is still honoured`() {
        val ledger = PushLedger()
        assertEquals(Decision.CANCEL, ledger.decide(cancel(expiresAt = now - 300), now))
        assertTrue(ledger.isTerminal("call-1", now))
    }

    @Test
    fun `ledger survives persistence and forgets after retention`() {
        val ledger = PushLedger()
        ledger.decide(cancel(), now)
        val restored = PushLedger.fromJson(ledger.toJson())
        assertEquals(Decision.DUPLICATE, restored.decide(cancel(), now + 10))
        assertEquals(Decision.CALL_ALREADY_TERMINAL, restored.decide(wake("e5"), now + 10))
        val later = now + 60 + PushLedger.RETENTION_SECONDS + 1
        assertFalse(restored.isTerminal("call-1", later))
        assertEquals(PushLedger().toJson(), PushLedger.fromJson("not json").toJson())
    }

    @Test
    fun `ledger is bounded`() {
        val ledger = PushLedger()
        repeat(PushLedger.MAX_ENTRIES + 50) { i -> ledger.decide(wake("e$i", "c$i"), now) }
        // The oldest event was evicted, so it is no longer reported as a duplicate.
        assertEquals(Decision.WAKE, ledger.decide(wake("e0", "c0"), now))
    }
}
