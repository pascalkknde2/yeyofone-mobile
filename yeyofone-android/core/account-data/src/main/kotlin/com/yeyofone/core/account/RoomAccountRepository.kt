package com.yeyofone.core.account

import android.content.Context
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.SipServerConfiguration
import com.yeyofone.core.model.TransportProtocol
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomAccountRepository internal constructor(
    private val dao: AccountDao,
    private val secrets: SecretStore,
) : AccountRepository {
    private val writes = Mutex()

    override fun observeAccounts(): Flow<List<SipAccount>> =
        dao.observeAll().map { accounts -> accounts.map(AccountEntity::toModel) }

    override fun observeAccount(id: SipAccountId): Flow<SipAccount?> =
        dao.observe(id.value).map { it?.toModel() }

    override suspend fun save(draft: AccountDraft): Result<SipAccountId> = runCatching {
        writes.withLock {
            AccountValidator.validate(draft)
            val id = draft.id ?: SipAccountId(UUID.randomUUID().toString())
            if (dao.findDuplicate(
                    draft.authenticationUsername.trim(),
                    draft.domain.normalizedHost(),
                    draft.transport.name,
                    id.value,
                ) != null
            ) throw AccountValidationException.Duplicate()
            if (draft.password != null) secrets.put(id, draft.password)
            check(draft.id != null || secrets.contains(id)) { "A secret is required for a new account" }
            dao.upsert(draft.toEntity(id))
            id
        }
    }.also { draft.clearSecret() }

    override suspend fun setEnabled(id: SipAccountId, enabled: Boolean) {
        check(dao.setEnabled(id.value, enabled) == 1) { "Account does not exist" }
    }

    override suspend fun delete(id: SipAccountId) = writes.withLock {
        secrets.delete(id)
        dao.delete(id.value)
        Unit
    }

    companion object {
        fun create(context: Context): RoomAccountRepository {
            val applicationContext = context.applicationContext
            return RoomAccountRepository(
                AccountDatabase.open(applicationContext).accounts(),
                AndroidKeystoreSecretStore(applicationContext),
            )
        }

        fun secretProvider(context: Context): AccountSecretProvider =
            AndroidKeystoreSecretStore(context.applicationContext)
    }
}

private fun AccountDraft.toEntity(id: SipAccountId) = AccountEntity(
    id = id.value,
    displayName = displayName.trim(),
    username = username.trim(),
    authenticationUsername = authenticationUsername.trim(),
    domain = domain.normalizedHost(),
    registrarUri = registrarUri.trim(),
    outboundProxyUri = outboundProxyUri.cleanOptional(),
    port = port,
    transport = transport.name,
    securityMode = securityMode.name,
    stunServer = nat.stunServer.cleanOptional(),
    turnServer = nat.turnServer.cleanOptional(),
    turnUsername = nat.turnUsername.cleanOptional(),
    iceEnabled = nat.iceEnabled,
    srtpEnabled = nat.srtpEnabled,
    registrationExpirySeconds = registrationExpirySeconds,
    voicemailNumber = voicemailNumber.cleanOptional(),
    callerId = callerId.cleanOptional(),
    enabled = enabled,
)

private fun AccountEntity.toModel() = SipAccount(
    id = SipAccountId(id),
    displayName = displayName,
    username = username,
    authenticationUsername = authenticationUsername,
    server = SipServerConfiguration(
        domain = domain,
        registrarUri = registrarUri,
        outboundProxyUri = outboundProxyUri,
        port = port,
        transport = TransportProtocol.valueOf(transport),
        securityMode = SecurityMode.valueOf(securityMode),
    ),
    nat = NatConfiguration(stunServer, turnServer, turnUsername, iceEnabled, srtpEnabled),
    registrationExpirySeconds = registrationExpirySeconds,
    voicemailNumber = voicemailNumber,
    callerId = callerId,
    enabled = enabled,
)

private fun String.normalizedHost(): String = trim().removeSuffix(".").lowercase()
private fun String?.cleanOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)
