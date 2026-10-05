package com.yeyofone.core.account

import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.TransportProtocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountValidatorTest {
    @Test
    fun `registrar domain receives default sip scheme`() {
        assertEquals("sip:pbx.example.com", normalizeRegistrarUri("pbx.example.com"))
        assertEquals("sip:pbx.example.com:5070", normalizeRegistrarUri(" pbx.example.com:5070 "))
        assertEquals("sip:pbx.example.com", normalizeRegistrarUri("sip:pbx.example.com"))
        assertEquals("sips:pbx.example.com", normalizeRegistrarUri("sips:pbx.example.com"))
    }

    @Test
    fun `explicit non SIP scheme remains available to validator`() {
        assertEquals("https://pbx.example.com", normalizeRegistrarUri("https://pbx.example.com"))
    }

    @Test
    fun `valid complete account is accepted`() {
        AccountValidator.validate(validDraft())
    }

    @Test
    fun `new account requires a password`() {
        val failure = assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(password = null))
        }
        assertEquals("password", failure.field)
    }

    @Test
    fun `invalid URI host and port cannot be saved`() {
        assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(registrar = "https://pbx.example.com"))
        }
        assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(domain = "bad host"))
        }
        assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(port = 70_000))
        }
    }

    @Test
    fun `TLS accounts cannot be saved with URIs that bypass TLS`() {
        AccountValidator.validate(validDraft(transport = TransportProtocol.TLS, registrar = "sips:pbx.example.com"))
        val registrar = assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(
                validDraft(transport = TransportProtocol.TLS, registrar = "sip:pbx.example.com;transport=udp"),
            )
        }
        assertEquals("registrarUri", registrar.field)
        val proxy = assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(
                validDraft(transport = TransportProtocol.TLS, proxy = "sip:edge.example.com;transport=tcp"),
            )
        }
        assertEquals("outboundProxyUri", proxy.field)
        val secureMode = assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(securityMode = SecurityMode.REQUIRE_SECURE))
        }
        assertEquals("transport", secureMode.field)
    }

    @Test
    fun `SRTP requires TLS signaling`() {
        AccountValidator.validate(
            validDraft(transport = TransportProtocol.TLS, nat = NatConfiguration(srtpEnabled = true)),
        )
        val failure = assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(nat = NatConfiguration(srtpEnabled = true)))
        }
        assertEquals("srtpEnabled", failure.field)
    }

    @Test
    fun `TURN server requires a username`() {
        assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(nat = NatConfiguration(turnServer = "turn.example.com")))
        }
    }

    @Test
    fun `ICE requires a STUN server`() {
        val failure = assertFailsWith<AccountValidationException.InvalidField> {
            AccountValidator.validate(validDraft(nat = NatConfiguration(iceEnabled = true)))
        }
        assertEquals("stunServer", failure.field)
        AccountValidator.validate(validDraft(nat = NatConfiguration(iceEnabled = true, stunServer = "stun.example.com")))
    }

    @Test
    fun `repository rejects duplicates and clears command secret`() = runTest {
        val dao = FakeDao()
        val secrets = FakeSecrets()
        val repository = RoomAccountRepository(dao, secrets)
        val first = validDraft()

        assertTrue(repository.save(first).isSuccess)
        assertTrue(first.password!!.all { it == '\u0000' })
        val duplicate = validDraft(password = "different".toCharArray())
        assertIs<AccountValidationException.Duplicate>(repository.save(duplicate).exceptionOrNull())
        assertTrue(duplicate.password!!.all { it == '\u0000' })
    }

    @Test
    fun `failed new account save leaves no orphaned secret`() = runTest {
        val dao = FakeDao().apply { failUpserts = true }
        val secrets = FakeSecrets()
        val repository = RoomAccountRepository(dao, secrets)

        assertTrue(repository.save(validDraft()).isFailure)
        assertEquals(0, secrets.size)
    }

    @Test
    fun `failed password change keeps the previous password`() = runTest {
        val dao = FakeDao()
        val secrets = FakeSecrets()
        val repository = RoomAccountRepository(dao, secrets)
        val id = repository.save(validDraft(password = "original-secret".toCharArray())).getOrThrow()

        dao.failUpserts = true
        val update = validDraft(password = "new-secret".toCharArray()).withId(id)
        assertTrue(repository.save(update).isFailure)
        assertEquals("original-secret", secrets.value(id))
        assertTrue(update.password!!.all { it == '\u0000' })
    }

    @Test
    fun `failed edit without a new password leaves the secret untouched`() = runTest {
        val dao = FakeDao()
        val secrets = FakeSecrets()
        val repository = RoomAccountRepository(dao, secrets)
        val id = repository.save(validDraft(password = "original-secret".toCharArray())).getOrThrow()

        dao.failUpserts = true
        assertTrue(repository.save(validDraft(password = null).withId(id)).isFailure)
        assertEquals("original-secret", secrets.value(id))
    }

    @Test
    fun `repository recreation exposes public data but never a secret`() = runTest {
        val dao = FakeDao()
        val secrets = FakeSecrets()
        val firstProcess = RoomAccountRepository(dao, secrets)
        val id = firstProcess.save(validDraft()).getOrThrow()

        val recreatedProcess = RoomAccountRepository(dao, secrets)
        val account = recreatedProcess.observeAccount(id).first()

        assertEquals("alice", account?.username)
        assertTrue(secrets.contains(id))
        assertFalse(AccountEntity::class.java.declaredFields.any { it.name.contains("password", ignoreCase = true) })
    }

    @Test
    fun `delete removes public data and protected secret`() = runTest {
        val dao = FakeDao()
        val secrets = FakeSecrets()
        val repository = RoomAccountRepository(dao, secrets)
        val id = repository.save(validDraft()).getOrThrow()

        repository.delete(id)

        assertFalse(secrets.contains(id))
        assertEquals(null, repository.observeAccount(id).first())
    }

    private fun validDraft(
        password: CharArray? = "correct horse".toCharArray(),
        domain: String = "pbx.example.com",
        registrar: String = "sip:pbx.example.com",
        port: Int = 5060,
        nat: NatConfiguration = NatConfiguration(),
        transport: TransportProtocol = TransportProtocol.UDP,
        securityMode: SecurityMode =
            if (transport == TransportProtocol.TLS) SecurityMode.REQUIRE_SECURE else SecurityMode.ALLOW_INSECURE,
        proxy: String? = null,
    ) = AccountDraft(
        displayName = "Alice", username = "alice", authenticationUsername = "alice-auth",
        password = password, domain = domain, registrarUri = registrar, outboundProxyUri = proxy, port = port,
        transport = transport, securityMode = securityMode, nat = nat,
    )

    private fun AccountDraft.withId(id: SipAccountId) = AccountDraft(
        id = id, displayName = displayName, username = username,
        authenticationUsername = authenticationUsername, password = password, domain = domain,
        registrarUri = registrarUri, outboundProxyUri = outboundProxyUri, port = port,
        transport = transport, securityMode = securityMode, nat = nat,
    )

    private class FakeSecrets : SecretStore {
        private val values = mutableMapOf<SipAccountId, String>()
        override fun put(accountId: SipAccountId, secret: CharArray) { values[accountId] = String(secret) }
        override fun contains(accountId: SipAccountId) = accountId in values
        override fun delete(accountId: SipAccountId) { values -= accountId }
        override fun read(accountId: SipAccountId): CharArray? = values[accountId]?.toCharArray()
        fun value(accountId: SipAccountId) = values[accountId]
        val size get() = values.size
    }

    private class FakeDao : AccountDao {
        var failUpserts = false
        private val state = MutableStateFlow<List<AccountEntity>>(emptyList())
        override fun observeAll(): Flow<List<AccountEntity>> = state
        override fun observe(id: String): Flow<AccountEntity?> =
            state.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun findDuplicate(
            username: String,
            domain: String,
            transport: String,
            excludedId: String,
        ) = state.value.firstOrNull {
            it.id != excludedId && it.authenticationUsername.equals(username, true) &&
                it.domain.equals(domain, true) && it.transport == transport
        }

        override suspend fun upsert(account: AccountEntity) {
            if (failUpserts) throw IllegalStateException("disk full")
            state.value = state.value.filterNot { it.id == account.id } + account
        }

        override suspend fun setEnabled(id: String, enabled: Boolean): Int {
            if (state.value.none { it.id == id }) return 0
            state.value = state.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            return 1
        }

        override suspend fun delete(id: String): Int {
            val before = state.value.size
            state.value = state.value.filterNot { it.id == id }
            return before - state.value.size
        }
    }
}
