package com.yeyofone.core.account

import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.TransportProtocol
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<SipAccount>>
    fun observeAccount(id: SipAccountId): Flow<SipAccount?>
    suspend fun save(draft: AccountDraft): Result<SipAccountId>
    suspend fun setEnabled(id: SipAccountId, enabled: Boolean)
    suspend fun delete(id: SipAccountId)
}

/** Adds the standard SIP scheme when the user enters only a registrar host name. */
fun normalizeRegistrarUri(value: String): String {
    val trimmed = value.trim()
    val hasExplicitScheme = SIP_SCHEME.containsMatchIn(trimmed) || URI_SCHEME.containsMatchIn(trimmed)
    return if (hasExplicitScheme || trimmed.isEmpty()) trimmed else "sip:$trimmed"
}

private val SIP_SCHEME = Regex("^sips?:", RegexOption.IGNORE_CASE)
private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/** Write-only command. Secrets must not be copied into screen state, saved state, or logs. */
class AccountDraft(
    val id: SipAccountId? = null,
    val displayName: String,
    val username: String,
    val authenticationUsername: String,
    val password: CharArray?,
    val domain: String,
    val registrarUri: String,
    val outboundProxyUri: String? = null,
    val port: Int = 5060,
    val transport: TransportProtocol = TransportProtocol.UDP,
    val securityMode: SecurityMode = SecurityMode.ALLOW_INSECURE,
    val nat: NatConfiguration = NatConfiguration(),
    val registrationExpirySeconds: Int = 300,
    val voicemailNumber: String? = null,
    val callerId: String? = null,
    val enabled: Boolean = true,
) {
    fun clearSecret() = password?.fill('\u0000')
}

sealed class AccountValidationException(message: String) : IllegalArgumentException(message) {
    class InvalidField(val field: String, reason: String) :
        AccountValidationException("$field: $reason")

    class Duplicate : AccountValidationException("An account with this identity and server already exists")
}
