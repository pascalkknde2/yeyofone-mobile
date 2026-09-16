package com.yeyofone.core.account

import com.yeyofone.core.model.NatConfiguration
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
    ) = AccountDraft(
        displayName = "Alice", username = "alice", authenticationUsername = "alice-auth",
        password = password, domain = domain, registrarUri = registrar, port = port,
        transport = TransportProtocol.UDP, nat = nat,
    )

    private class FakeSecrets : SecretStore {
        private val ids = mutableSetOf<SipAccountId>()
        override fun put(accountId: SipAccountId, secret: CharArray) { ids += accountId }
        override fun contains(accountId: SipAccountId) = accountId in ids
        override fun delete(accountId: SipAccountId) { ids -= accountId }
        override fun read(accountId: SipAccountId): CharArray? = null
    }

    private class FakeDao : AccountDao {
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
