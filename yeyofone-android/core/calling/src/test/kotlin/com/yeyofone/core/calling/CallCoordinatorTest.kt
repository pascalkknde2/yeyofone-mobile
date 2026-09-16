package com.yeyofone.core.calling

import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.SipServerConfiguration
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.CallHistoryRepository
import com.yeyofone.core.voip.NativeMediaEvent
import com.yeyofone.core.voip.SipCallGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CallCoordinatorTest {
    @Test
    fun `call reaches Connected when native reports CONFIRMED`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()

        val callId = coordinator.call(id, "1001")
        assertEquals("sip:1001@pbx.example.com", gateway.lastDestination)
        assertEquals(CallState.Preparing, coordinator.sessions.value.single().state)

        gateway.emit(callId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        runCurrent()

        val session = coordinator.sessions.value.single()
        assertEquals(CallState.Connected, session.state)
        assertEquals(CallDirection.OUTGOING, session.direction)
    }

    @Test
    fun `disconnected event with 486 maps to busy`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()

        val callId = coordinator.call(id, "1001")
        gateway.emit(callId.value, id, invState = PJSIP_INV_STATE_DISCONNECTED, lastStatusCode = 486)
        runCurrent()

        val session = coordinator.sessions.value.single()
        assertIs<CallState.Disconnected>(session.state)
    }

    @Test
    fun `end delegates to gateway hangup`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)

        val callId = coordinator.call(id, "sip:1001@elsewhere.example.com")
        coordinator.end(callId)

        assertTrue(gateway.hungUp.contains(callId.value))
    }

    @Test
    fun `incoming event with no prior call creates a new incoming session`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()

        gateway.emit(
            "native-incoming-1", id,
            invState = PJSIP_INV_STATE_INCOMING, lastStatusCode = 0,
            direction = CallDirection.INCOMING, remoteUri = "sip:2002@pbx.example.com",
        )
        runCurrent()

        val session = coordinator.sessions.value.single()
        assertEquals(CallDirection.INCOMING, session.direction)
        assertEquals(CallState.Incoming, session.state)
        assertEquals("sip:2002@pbx.example.com", session.remoteUri)
    }

    @Test
    fun `answer delegates to gateway answer`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()
        gateway.emit("native-incoming-1", id, invState = PJSIP_INV_STATE_INCOMING, lastStatusCode = 0, direction = CallDirection.INCOMING)
        runCurrent()

        coordinator.answer(coordinator.sessions.value.single().id)

        assertTrue(gateway.answered.contains("native-incoming-1"))
    }

    @Test
    fun `reject delegates to gateway hangup`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()
        gateway.emit("native-incoming-1", id, invState = PJSIP_INV_STATE_INCOMING, lastStatusCode = 0, direction = CallDirection.INCOMING)
        runCurrent()

        coordinator.reject(coordinator.sessions.value.single().id)

        assertTrue(gateway.hungUp.contains("native-incoming-1"))
    }

    @Test
    fun `mute and hold delegate and update from native media events`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val callId = coordinator.call(id, "1001")
        runCurrent()

        coordinator.setMuted(callId, true)
        coordinator.setHeld(callId, true)
        assertEquals(callId.value to true, gateway.lastMute)
        assertEquals(callId.value to true, gateway.lastHold)

        gateway.emitMedia(callId.value, muted = true, held = true)
        runCurrent()
        assertTrue(coordinator.observe(callId).value.muted)
        assertTrue(coordinator.observe(callId).value.held)
    }

    @Test
    fun `DTMF delegates valid digit for connected call`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val callId = coordinator.call(id, "1001")
        runCurrent()
        gateway.emit(callId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        runCurrent()

        coordinator.sendDtmf(callId, '#')

        assertEquals(callId.value to '#', gateway.lastDtmf)
    }

    @Test
    fun `DTMF rejects unsupported digit disconnected call and held call`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val callId = coordinator.call(id, "1001")
        runCurrent()

        assertFailsWith<IllegalArgumentException> { coordinator.sendDtmf(callId, 'A') }
        assertFailsWith<IllegalStateException> { coordinator.sendDtmf(callId, '1') }

        gateway.emit(callId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        gateway.emitMedia(callId.value, muted = false, held = true)
        runCurrent()
        assertFailsWith<IllegalStateException> { coordinator.sendDtmf(callId, '1') }
        assertEquals(null, gateway.lastDtmf)
    }

    @Test
    fun `terminal unanswered incoming call is recorded as missed`() = runTest {
        val accountId = SipAccountId("one")
        val gateway = FakeGateway()
        val history = FakeHistory()
        CallCoordinator(FakeAccounts(mapOf(accountId to account(accountId))), gateway, backgroundScope, history)
        runCurrent()

        gateway.emit(
            "missed-1", accountId, PJSIP_INV_STATE_INCOMING, 0,
            direction = CallDirection.INCOMING,
        )
        gateway.emit(
            "missed-1", accountId, PJSIP_INV_STATE_DISCONNECTED, 487,
            direction = CallDirection.INCOMING,
        )
        runCurrent()

        assertTrue(history.entries.single().missed)
    }

    private fun account(id: SipAccountId) = SipAccount(
        id, "Account ${id.value}", id.value, id.value,
        SipServerConfiguration("pbx.example.com", "sip:pbx.example.com", null, 5060, TransportProtocol.UDP, SecurityMode.ALLOW_INSECURE),
        NatConfiguration(),
    )

    private class FakeAccounts(private val accounts: Map<SipAccountId, SipAccount>) : AccountRepository {
        override fun observeAccounts(): Flow<List<SipAccount>> = MutableStateFlow(accounts.values.toList())
        override fun observeAccount(id: SipAccountId): Flow<SipAccount?> = MutableStateFlow(accounts[id])
        override suspend fun save(draft: AccountDraft) = error("not used")
        override suspend fun setEnabled(id: SipAccountId, enabled: Boolean) = Unit
        override suspend fun delete(id: SipAccountId) = Unit
    }

    private class FakeGateway : SipCallGateway {
        private val mutableCallEvents = MutableSharedFlow<NativeCallEvent>(extraBufferCapacity = 8)
        override val callEvents: Flow<NativeCallEvent> = mutableCallEvents
        private val mutableMediaEvents = MutableSharedFlow<NativeMediaEvent>(extraBufferCapacity = 8)
        override val mediaEvents: Flow<NativeMediaEvent> = mutableMediaEvents
        var lastDestination: String? = null
        val hungUp = mutableListOf<String>()
        val answered = mutableListOf<String>()
        var lastMute: Pair<String, Boolean>? = null
        var lastHold: Pair<String, Boolean>? = null
        var lastDtmf: Pair<String, Char>? = null
        private var counter = 0

        override suspend fun makeCall(accountId: SipAccountId, destination: String): String {
            lastDestination = destination
            return "native-call-${counter++}"
        }

        override suspend fun answer(callId: String) {
            answered += callId
        }

        override suspend fun hangup(callId: String) {
            hungUp += callId
        }

        override suspend fun setMuted(callId: String, muted: Boolean) {
            lastMute = callId to muted
        }

        override suspend fun setHeld(callId: String, held: Boolean) {
            lastHold = callId to held
        }

        override suspend fun sendDtmf(callId: String, digit: Char) {
            lastDtmf = callId to digit
        }

        suspend fun emitMedia(callId: String, muted: Boolean, held: Boolean) {
            mutableMediaEvents.emit(NativeMediaEvent(callId, muted, held))
        }

        suspend fun emit(
            callId: String,
            accountId: SipAccountId,
            invState: Int,
            lastStatusCode: Int,
            direction: CallDirection = CallDirection.OUTGOING,
            remoteUri: String = "sip:1001@pbx.example.com",
        ) {
            mutableCallEvents.emit(
                NativeCallEvent(callId, accountId, remoteUri, direction, invState, lastStatusCode, null),
            )
        }
    }

    private class FakeHistory : CallHistoryRepository {
        val entries = mutableListOf<CallHistoryEntry>()
        private val state = MutableStateFlow<List<CallHistoryEntry>>(emptyList())

        override fun observeHistory(): Flow<List<CallHistoryEntry>> = state
        override suspend fun upsert(entry: CallHistoryEntry) {
            entries.removeAll { it.id == entry.id }
            entries += entry
            state.value = entries.toList()
        }
        override suspend fun delete(id: CallHistoryId) {
            entries.removeAll { it.id == id }
            state.value = entries.toList()
        }
        override suspend fun clear() {
            entries.clear()
            state.value = emptyList()
        }
    }

    private companion object {
        const val PJSIP_INV_STATE_INCOMING = 2
        const val PJSIP_INV_STATE_CONFIRMED = 5
        const val PJSIP_INV_STATE_DISCONNECTED = 6
    }
}
