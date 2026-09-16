package com.yeyofone.core.calling

import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.SipServerConfiguration
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.model.TransferState
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.CallHistoryRepository
import com.yeyofone.core.voip.NativeMediaEvent
import com.yeyofone.core.voip.NativeTransferEvent
import com.yeyofone.core.voip.SipCallGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
        assertEquals(CallState.Answering, coordinator.sessions.value.single().state)
    }

    @Test
    fun `double answer executes only once for one call id`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()
        gateway.emit("incoming-1", id, PJSIP_INV_STATE_INCOMING, 0, direction = CallDirection.INCOMING)
        runCurrent()

        coordinator.answer(CallId("incoming-1"))
        coordinator.answer(CallId("incoming-1"))

        assertEquals(listOf("incoming-1"), gateway.answered)
        assertEquals(1, coordinator.sessions.value.count { it.id == CallId("incoming-1") })
    }

    @Test
    fun `duplicate incoming events keep one session and do not regress answering`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()
        gateway.emit("incoming-1", id, PJSIP_INV_STATE_INCOMING, 0, direction = CallDirection.INCOMING)
        runCurrent()
        coordinator.answer(CallId("incoming-1"))
        gateway.emit("incoming-1", id, PJSIP_INV_STATE_INCOMING, 0, direction = CallDirection.INCOMING)
        runCurrent()

        assertEquals(1, coordinator.sessions.value.count { it.id == CallId("incoming-1") })
        assertEquals(CallState.Answering, coordinator.sessions.value.single().state)
    }

    @Test
    fun `caller cancelling before answer disconnects the call and stale answer is ignored`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()
        gateway.emit("incoming-1", id, PJSIP_INV_STATE_INCOMING, 0, direction = CallDirection.INCOMING)
        runCurrent()

        gateway.emit("incoming-1", id, PJSIP_INV_STATE_DISCONNECTED, 487, direction = CallDirection.INCOMING)
        runCurrent()
        assertIs<CallState.Disconnected>(coordinator.sessions.value.single().state)

        // A stale Answer arriving after the cancel must not resurrect the call.
        coordinator.answer(CallId("incoming-1"))
        assertTrue(gateway.answered.isEmpty())
        assertIs<CallState.Disconnected>(coordinator.sessions.value.single().state)
    }

    @Test
    fun `caller cancelling while answer is in flight is not overwritten by the answer failure`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        runCurrent()
        gateway.emit("incoming-1", id, PJSIP_INV_STATE_INCOMING, 0, direction = CallDirection.INCOMING)
        runCurrent()

        // Simulate the native cancel event arriving concurrently, before gateway.answer() throws.
        gateway.failAnswer = true
        gateway.cancelDuringAnswer = {
            gateway.emit("incoming-1", id, PJSIP_INV_STATE_DISCONNECTED, 487, direction = CallDirection.INCOMING)
            yield()
        }
        coordinator.answer(CallId("incoming-1"))
        runCurrent()

        val session = coordinator.sessions.value.single()
        assertIs<CallState.Disconnected>(session.state)
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
    fun `blind transfer resolves destination and reports success`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val callId = coordinator.call(id, "1001")
        runCurrent()
        gateway.emit(callId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        runCurrent()

        coordinator.transfer(callId, "1002")
        assertEquals(callId.value to "sip:1002@pbx.example.com", gateway.lastTransfer)
        assertIs<TransferState.Pending>(coordinator.sessions.value.single().transfer)

        gateway.emitTransfer(callId.value, 200, final = true)
        runCurrent()
        assertIs<TransferState.Succeeded>(coordinator.sessions.value.single().transfer)
    }

    @Test
    fun `failed blind transfer restores connected call and prevents duplicate request`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val callId = coordinator.call(id, "1001")
        runCurrent()
        gateway.emit(callId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        runCurrent()

        coordinator.transfer(callId, "1002")
        assertFailsWith<IllegalStateException> { coordinator.transfer(callId, "1003") }
        gateway.emitTransfer(callId.value, 404, "Not Found", final = true)
        runCurrent()

        val session = coordinator.sessions.value.single()
        assertEquals(CallState.Connected, session.state)
        assertEquals(404, assertIs<TransferState.Failed>(session.transfer).statusCode)
    }

    @Test
    fun `attended transfer requires held source and connected consultation call`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val sourceId = coordinator.call(id, "1001")
        val destinationId = coordinator.call(id, "1002")
        runCurrent()
        gateway.emit(sourceId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        gateway.emit(destinationId.value, id, invState = PJSIP_INV_STATE_CONFIRMED, lastStatusCode = 200)
        runCurrent()

        assertFailsWith<IllegalStateException> { coordinator.attendedTransfer(sourceId, destinationId) }
        gateway.emitMedia(sourceId.value, muted = false, held = true)
        runCurrent()
        coordinator.attendedTransfer(sourceId, destinationId)

        assertEquals(sourceId.value to destinationId.value, gateway.lastAttendedTransfer)
        assertEquals(CallState.Transferring, coordinator.sessions.value.first { it.id == sourceId }.state)
    }

    @Test
    fun `attended transfer rejects the same call as source and destination`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway()
        val coordinator = CallCoordinator(FakeAccounts(mapOf(id to account(id))), gateway, backgroundScope)
        val callId = coordinator.call(id, "1001")

        assertFailsWith<IllegalArgumentException> { coordinator.attendedTransfer(callId, callId) }
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
        private val mutableTransferEvents = MutableSharedFlow<NativeTransferEvent>(extraBufferCapacity = 8)
        override val transferEvents: Flow<NativeTransferEvent> = mutableTransferEvents
        var lastDestination: String? = null
        val hungUp = mutableListOf<String>()
        val answered = mutableListOf<String>()
        var lastMute: Pair<String, Boolean>? = null
        var lastHold: Pair<String, Boolean>? = null
        var lastDtmf: Pair<String, Char>? = null
        var lastTransfer: Pair<String, String>? = null
        var lastAttendedTransfer: Pair<String, String>? = null
        var cancelDuringAnswer: (suspend () -> Unit)? = null
        var failAnswer = false
        private var counter = 0

        override suspend fun makeCall(accountId: SipAccountId, destination: String): String {
            lastDestination = destination
            return "native-call-${counter++}"
        }

        override suspend fun answer(callId: String) {
            cancelDuringAnswer?.invoke()
            answered += callId
            if (failAnswer) error("native answer failed")
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

        override suspend fun transfer(callId: String, destination: String) {
            lastTransfer = callId to destination
        }

        override suspend fun attendedTransfer(callId: String, destinationCallId: String) {
            lastAttendedTransfer = callId to destinationCallId
        }

        suspend fun emitTransfer(callId: String, statusCode: Int, reason: String? = null, final: Boolean) {
            mutableTransferEvents.emit(NativeTransferEvent(callId, statusCode, reason, final))
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
