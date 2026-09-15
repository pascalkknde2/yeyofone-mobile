package com.yeyofone.core.voip.pjsip

import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjsip_transport_type_e
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.voip.NativeRegistrationEvent
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.AuthCredInfoVector
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.StringVector
import org.pjsip.pjsua2.pjmedia_srtp_use

/** Owns all generated PJSUA2 objects. Calls are serialized by [PjsipEngine]. */
internal class Pjsua2EndpointBackend : EndpointBackend {
    private var endpoint: Endpoint? = null
    private val accounts = mutableMapOf<SipAccountId, NativeAccount>()

    override fun create() {
        check(endpoint == null) { "PJSIP endpoint already exists" }
        NativeLibrary.load()
        endpoint = Endpoint().also {
            it.libCreate()
            it.libRegisterThread("yeyofone-pjsip")
        }
    }

    override fun initialize(configuration: PjsipEngineConfiguration) {
        val config = EpConfig()
        try {
            config.uaConfig.userAgent = configuration.userAgent
            config.logConfig.apply {
                level = configuration.logLevel.toLong()
                consoleLevel = configuration.logLevel.toLong()
                msgLogging = 0
            }
            requireEndpoint().libInit(config)
        } finally {
            config.delete()
        }
    }

    override fun createTransports(transports: Set<SipTransport>) {
        transports.forEach { transport ->
            val config = TransportConfig()
            try {
                requireEndpoint().transportCreate(transport.toPjsipTransportType(), config)
            } finally {
                config.delete()
            }
        }
    }

    override fun start() = requireEndpoint().libStart()

    override fun destroy() {
        val current = endpoint ?: return
        endpoint = null
        accounts.values.forEach { it.shutdown(); it.delete() }
        accounts.clear()
        try {
            current.libDestroy()
        } finally {
            current.delete()
        }
    }

    override fun createOrUpdateAccount(
        account: SipAccount,
        password: CharArray,
        callback: (NativeRegistrationEvent) -> Unit,
    ) {
        require(account.server.transport != TransportProtocol.TLS) { "TLS is unavailable in this native build" }
        removeAccount(account.id)
        val config = AccountConfig()
        val credential = AuthCredInfo("digest", "*", account.authenticationUsername, 0, String(password))
        val credentials = AuthCredInfoVector().apply { add(credential) }
        val proxies = StringVector()
        try {
            config.idUri = "sip:${account.username}@${account.server.domain}"
            config.regConfig.apply {
                registrarUri = account.server.registrarUri.withTransport(account.server.transport)
                registerOnAdd = false
                timeoutSec = account.registrationExpirySeconds.toLong()
                retryIntervalSec = 0
                firstRetryIntervalSec = 0
                randomRetryIntervalSec = 0
                delayBeforeRefreshSec = 5
            }
            config.sipConfig.authCreds = credentials
            account.server.outboundProxyUri?.let {
                proxies.add(it.withTransport(account.server.transport))
                config.sipConfig.proxies = proxies
            }
            config.natConfig.apply {
                iceEnabled = account.nat.iceEnabled
                turnEnabled = !account.nat.turnServer.isNullOrBlank()
                account.nat.turnServer?.let { turnServer = it }
                account.nat.turnUsername?.let { turnUserName = it }
            }
            config.mediaConfig.srtpUse = if (account.nat.srtpEnabled) {
                pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY
            } else pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL
            NativeAccount(account.id, callback).also { native ->
                native.create(config)
                accounts[account.id] = native
            }
        } finally {
            proxies.delete()
            credentials.delete()
            credential.delete()
            config.delete()
        }
    }

    override fun setRegistration(accountId: SipAccountId, renew: Boolean) {
        checkNotNull(accounts[accountId]) { "Native account does not exist" }.setRegistration(renew)
    }

    override fun removeAccount(accountId: SipAccountId) {
        accounts.remove(accountId)?.let { it.shutdown(); it.delete() }
    }

    private class NativeAccount(
        private val id: SipAccountId,
        private val callback: (NativeRegistrationEvent) -> Unit,
    ) : org.pjsip.pjsua2.Account() {
        override fun onRegState(prm: OnRegStateParam) {
            callback(
                NativeRegistrationEvent(
                    id,
                    prm.code.takeIf { it > 0 },
                    prm.reason?.replace(Regex("[\\r\\n]"), " ")?.take(120),
                    prm.expiration,
                ),
            )
        }
    }

    private fun requireEndpoint(): Endpoint = checkNotNull(endpoint) { "PJSIP endpoint is not created" }

    private object NativeLibrary {
        init {
            System.loadLibrary("pjsua2")
        }

        fun load() = Unit
    }
}

internal fun SipTransport.toPjsipTransportType(): Int = when (this) {
    SipTransport.UDP -> pjsip_transport_type_e.PJSIP_TRANSPORT_UDP
    SipTransport.TCP -> pjsip_transport_type_e.PJSIP_TRANSPORT_TCP
    SipTransport.TLS -> pjsip_transport_type_e.PJSIP_TRANSPORT_TLS
}

private fun String.withTransport(transport: TransportProtocol): String {
    if (contains(";transport=", ignoreCase = true)) return this
    return "$this;transport=${transport.name.lowercase()}"
}
