package com.yeyofone.core.registration

import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.account.AccountSecretProvider
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.SipServerConfiguration
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.model.VoipError
import com.yeyofone.core.voip.NativeRegistrationEvent
import com.yeyofone.core.voip.SipRegistrationGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationCoordinatorTest {
    @Test
    fun `successful registration preserves safe details and expiry`() = runTest {
        val id = SipAccountId("one")
        val gateway = FakeGateway { NativeRegistrationEvent(id, 200, "OK", 300) }
        val manager = manager(mapOf(id to account(id)), gateway) { awaitCancellation() }

        manager.register(id)
        runCurrent()

        assertIs<RegistrationState.Registered>(manager.observe(id).value)
        assertEquals(200, manager.observeDetails(id).value.sipResponseCode)
        assertEquals("sip:pbx.example.com", manager.observeDetails(id).value.registrar)
    }

    @Test
    fun `authentication failure does not retry`() = runTest {
        val id = SipAccountId("auth")
        val gateway = FakeGateway { NativeRegistrationEvent(id, 403, "Forbidden", 0) }
        val delays = mutableListOf<Long>()
        val manager = manager(mapOf(id to account(id)), gateway) { delays += it }

        manager.register(id)
        runCurrent()

        assertIs<RegistrationState.Failed>(manager.observe(id).value)
        assertEquals(1, gateway.registrationRequests[id])
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `server failures back off independently per account`() = runTest {
        val one = SipAccountId("one")
        val two = SipAccountId("two")
        val gateway = FakeGateway { NativeRegistrationEvent(it, 503, "Unavailable", 0) }
        val delays = mutableListOf<Long>()
        val manager = manager(mapOf(one to account(one), two to account(two)), gateway) {
            delays += it
            awaitCancellation()
        }

        manager.register(one)
        manager.register(two)
        runCurrent()

        assertEquals(1, gateway.registrationRequests[one])
        assertEquals(1, gateway.registrationRequests[two])
        assertEquals(2, delays.size)
        assertTrue(delays.all { it in 1_000..1_500 })
    }

    @Test
    fun `unregister cancels retry and disposes only requested account`() = runTest {
        val one = SipAccountId("one")
        val two = SipAccountId("two")
        val gateway = FakeGateway { NativeRegistrationEvent(it, 503, "Unavailable", 0) }
        val manager = manager(mapOf(one to account(one), two to account(two)), gateway) { awaitCancellation() }
        manager.register(one)
        manager.register(two)
        runCurrent()

        manager.unregister(one)

        assertEquals(RegistrationState.Unregistered, manager.observe(one).value)
        assertEquals(listOf(one), gateway.removed)
        assertIs<RegistrationState.Failed>(manager.observe(two).value)
    }

    @Test
    fun `SIP and native failures map to typed safe errors`() {
        val id = SipAccountId("mapping")
        assertIs<VoipError.Authentication>(NativeRegistrationEvent(id, 401, "raw", 0).toVoipError())
        assertIs<VoipError.SipResponse>(NativeRegistrationEvent(id, 488, "raw", 0).toVoipError())
        assertIs<VoipError.SipResponse>(NativeRegistrationEvent(id, 503, "raw", 0).toVoipError())
        assertIs<VoipError.Dns>(IllegalStateException("DNS resolve failed for redacted host").toVoipError())
        assertIs<VoipError.Transport>(IllegalStateException("transport unavailable").toVoipError())
        assertIs<VoipError.Tls>(IllegalStateException("TLS certificate failure").toVoipError())
    }

    private fun kotlinx.coroutines.test.TestScope.manager(
        accounts: Map<SipAccountId, SipAccount>,
        gateway: FakeGateway,
        delay: suspend (Long) -> Unit,
    ) = RegistrationCoordinator(
        FakeAccounts(accounts),
        AccountSecretProvider { _, consumer -> consumer("test-only".toCharArray()); true },
        gateway,
        NetworkStatus { MutableStateFlow(true) },
        backgroundScope,
        Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC),
        Random(1),
        delay,
    )

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

    private class FakeGateway(private val response: (SipAccountId) -> NativeRegistrationEvent) : SipRegistrationGateway {
        private val mutableEvents = MutableSharedFlow<NativeRegistrationEvent>(extraBufferCapacity = 8)
        override val events: Flow<NativeRegistrationEvent> = mutableEvents
        val registrationRequests = mutableMapOf<SipAccountId, Int>()
        val removed = mutableListOf<SipAccountId>()
        override suspend fun createOrUpdate(account: SipAccount, password: CharArray) = Unit
        override suspend fun setRegistration(accountId: SipAccountId, renew: Boolean) {
            if (renew) {
                registrationRequests[accountId] = (registrationRequests[accountId] ?: 0) + 1
                mutableEvents.emit(response(accountId))
            }
        }
        override suspend fun remove(accountId: SipAccountId) { removed += accountId }
    }
}
