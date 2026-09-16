package com.yeyofone.core.voip.pjsip

import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjsip_transport_type_e
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.NativeMediaEvent
import com.yeyofone.core.voip.NativeRegistrationEvent
import com.yeyofone.core.voip.NativeTransferEvent
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.AuthCredInfoVector
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.CallSendDtmfParam
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnCallTransferStatusParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.StringVector
import org.pjsip.pjsua2.pjmedia_srtp_use
import org.pjsip.pjsua2.pjsua_call_flag
import org.pjsip.pjsua2.pjsua_call_media_status
import org.pjsip.pjsua2.pjsua_dtmf_method
import org.pjsip.pjsua2.pjsip_inv_state
import java.util.UUID

/** Owns all generated PJSUA2 objects. Calls are serialized by [PjsipEngine]. */
internal class Pjsua2EndpointBackend : EndpointBackend {
    private var endpoint: Endpoint? = null
    private val accounts = mutableMapOf<SipAccountId, NativeAccount>()
    private val calls = mutableMapOf<String, NativeCall>()
    private var callEventCallback: ((NativeCallEvent) -> Unit)? = null
    private var mediaEventCallback: ((NativeMediaEvent) -> Unit)? = null
    private var transferEventCallback: ((NativeTransferEvent) -> Unit)? = null

    override fun setCallEventListener(callback: (NativeCallEvent) -> Unit) {
        callEventCallback = callback
    }

    override fun setMediaEventListener(callback: (NativeMediaEvent) -> Unit) {
        mediaEventCallback = callback
    }

    override fun setTransferEventListener(callback: (NativeTransferEvent) -> Unit) {
        transferEventCallback = callback
    }

    override fun create() {
        check(endpoint == null) { "PJSIP endpoint already exists" }
        NativeLibrary.load()
        endpoint = Endpoint().also { it.libCreate() }
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
        calls.values.forEach { runCatching { it.hangup(CallOpParam(true)) }; it.delete() }
        calls.clear()
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
            } else {
                pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED
            }
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

    override fun makeCall(
        accountId: SipAccountId,
        destination: String,
        callback: (NativeCallEvent) -> Unit,
    ): String {
        val account = checkNotNull(accounts[accountId]) { "Native account does not exist" }
        val id = UUID.randomUUID().toString()
        val call = NativeCall(id, accountId, destination, CallDirection.OUTGOING, account, -1, callback)
        calls[id] = call
        val prm = CallOpParam(true)
        try {
            call.makeCall(destination, prm)
        } catch (e: Exception) {
            calls.remove(id)
            call.delete()
            callback(
                NativeCallEvent(
                    id,
                    accountId,
                    destination,
                    CallDirection.OUTGOING,
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED,
                    0,
                    e.message?.take(120),
                ),
            )
        } finally {
            prm.delete()
        }
        return id
    }

    override fun hangupCall(callId: String) {
        val call = calls[callId] ?: return
        val prm = CallOpParam(true)
        try {
            call.hangup(prm)
        } catch (_: Exception) {
            // Best-effort: the call may already be disconnecting.
        } finally {
            prm.delete()
        }
    }

    override fun answerCall(callId: String) {
        val call = calls[callId] ?: return
        val prm = CallOpParam(true).apply { statusCode = 200 }
        try {
            call.answer(prm)
        } catch (_: Exception) {
            // Best-effort: the call may already have been cancelled by the caller.
        } finally {
            prm.delete()
        }
    }

    override fun sendDtmf(callId: String, digit: Char) {
        val call = checkNotNull(calls[callId]) { "Native call does not exist" }
        val prm = CallSendDtmfParam().apply {
            method = pjsua_dtmf_method.PJSUA_DTMF_METHOD_RFC2833
            digits = digit.toString()
        }
        try {
            call.sendDtmf(prm)
        } finally {
            prm.delete()
        }
    }

    override fun transferCall(callId: String, destination: String) {
        val call = checkNotNull(calls[callId]) { "Native call does not exist" }
        val prm = CallOpParam(true)
        try {
            call.xfer(destination, prm)
        } finally {
            prm.delete()
        }
    }

    override fun setMuted(callId: String, muted: Boolean) {
        val call = calls[callId] ?: return
        val ep = endpoint ?: return
        val audio = runCatching { call.getAudioMedia(-1) }.getOrNull() ?: return
        if (muted) {
            ep.audDevManager().captureDevMedia.stopTransmit(audio)
        } else {
            ep.audDevManager().captureDevMedia.startTransmit(audio)
        }
        call.muted = muted
        call.reportMediaState()
    }

    override fun setHeld(callId: String, held: Boolean) {
        val call = calls[callId] ?: return
        val prm = CallOpParam(true)
        try {
            if (held) {
                call.setHold(prm)
            } else {
                prm.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
                call.reinvite(prm)
            }
        } finally {
            prm.delete()
        }
    }

    private inner class NativeAccount(
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

        override fun onIncomingCall(prm: OnIncomingCallParam) {
            val listener = callEventCallback
            if (listener == null) {
                val call = org.pjsip.pjsua2.Call(this, prm.callId)
                val op = CallOpParam(true).apply { statusCode = 486 }
                try {
                    call.hangup(op)
                } catch (_: Exception) {
                    // Best-effort rejection when no one is listening for incoming calls.
                } finally {
                    op.delete()
                    call.delete()
                }
                return
            }
            val callId = UUID.randomUUID().toString()
            val call = NativeCall(callId, id, "", CallDirection.INCOMING, this, prm.callId, listener)
            calls[callId] = call
            runCatching { call.getInfo().remoteUri }.getOrNull()?.let { call.remoteUri = it }
            val ringing = CallOpParam(true).apply { statusCode = 180 }
            try {
                call.answer(ringing)
            } catch (_: Exception) {
                // Best-effort: proceed even if the provisional response could not be sent.
            } finally {
                ringing.delete()
            }
            call.reportState()
        }
    }

    private inner class NativeCall(
        private val id: String,
        private val accountId: SipAccountId,
        var remoteUri: String,
        private val direction: CallDirection,
        account: NativeAccount,
        nativeCallId: Int,
        private val callback: (NativeCallEvent) -> Unit,
    ) : org.pjsip.pjsua2.Call(account, nativeCallId) {
        var muted: Boolean = false

        fun reportState() {
            val info = runCatching { getInfo() }.getOrNull()
            val invState = info?.state ?: pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED
            callback(
                NativeCallEvent(
                    id,
                    accountId,
                    remoteUri,
                    direction,
                    invState,
                    info?.lastStatusCode ?: 0,
                    info?.lastReason?.replace(Regex("[\\r\\n]"), " ")?.take(120),
                ),
            )
            if (invState == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                calls.remove(id)
                delete()
            }
        }

        override fun onCallState(prm: OnCallStateParam) = reportState()

        override fun onCallTransferStatus(prm: OnCallTransferStatusParam) {
            transferEventCallback?.invoke(
                NativeTransferEvent(
                    callId = id,
                    statusCode = prm.statusCode,
                    safeReason = prm.reason?.replace(Regex("[\\r\\n]"), " ")?.take(120),
                    final = prm.finalNotify,
                ),
            )
        }

        override fun onCallMediaState(prm: OnCallMediaStateParam) {
            val info = runCatching { getInfo() }.getOrNull() ?: return
            val hasActiveAudio = info.media.any { it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE }
            if (hasActiveAudio) {
                val ep = endpoint ?: return
                runCatching {
                    val audio = getAudioMedia(-1)
                    audio.startTransmit(ep.audDevManager().playbackDevMedia)
                    if (!muted) ep.audDevManager().captureDevMedia.startTransmit(audio)
                }
            }
            reportMediaState(info.media.any { it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD })
        }

        fun reportMediaState(held: Boolean? = null) {
            val localHold = held ?: runCatching {
                getInfo().media.any { it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD }
            }.getOrDefault(false)
            mediaEventCallback?.invoke(NativeMediaEvent(id, muted, localHold))
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
