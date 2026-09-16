package com.yeyofone.core.model

data class SipAccount(
    val id: SipAccountId,
    val displayName: String,
    val username: String,
    val authenticationUsername: String = username,
    val server: SipServerConfiguration,
    val nat: NatConfiguration = NatConfiguration(),
    val registrationExpirySeconds: Int = 300,
    val voicemailNumber: String? = null,
    val callerId: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(registrationExpirySeconds in 60..86_400) {
            "Registration expiry must be between 60 and 86400 seconds"
        }
    }
}

/** A transient write-only value. It must never be persisted or exposed in presentation state. */
data class SipCredentials(
    val username: String,
    val password: CharArray,
) {
    fun clear() = password.fill('\u0000')
}

data class SipServerConfiguration(
    val domain: String,
    val registrarUri: String,
    val outboundProxyUri: String? = null,
    val port: Int,
    val transport: TransportProtocol,
    val securityMode: SecurityMode,
) {
    init { require(port in 1..65535) { "SIP port must be between 1 and 65535" } }
}

data class NatConfiguration(
    val stunServer: String? = null,
    val turnServer: String? = null,
    val turnUsername: String? = null,
    val iceEnabled: Boolean = false,
    val srtpEnabled: Boolean = false,
)

enum class TransportProtocol { UDP, TCP, TLS }
enum class SecurityMode { REQUIRE_SECURE, PREFER_SECURE, ALLOW_INSECURE }
