package com.yeyofone.core.voip.pjsip

import android.util.Log
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
import org.pjsip.pjsua2.pjsua_stun_use
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/** Owns all generated PJSUA2 objects. Calls are serialized by [PjsipEngine]. */
internal class Pjsua2EndpointBackend : EndpointBackend {
    private var endpoint: Endpoint? = null
    private val accounts = mutableMapOf<SipAccountId, NativeAccount>()
    private val calls = mutableMapOf<String, NativeCall>()
    private val locallyHeldCallIds = ConcurrentHashMap.newKeySet<String>()
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
        locallyHeldCallIds.clear()
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
            val stunServer = account.nat.stunServer?.takeIf { it.isNotBlank() }
            if (stunServer != null) {
                val stunServers = StringVector().apply { add(stunServer) }
                try {
                    requireEndpoint().natUpdateStunServers(stunServers, false)
                } catch (_: Exception) {
                    // Best-effort: fall through with STUN disabled below if resolution fails.
                } finally {
                    stunServers.delete()
                }
            }
            config.natConfig.apply {
                iceEnabled = account.nat.iceEnabled
                // Without a resolved STUN server, ICE can only offer host candidates, which are
                // unreachable from outside the device's own NAT. Disabling STUN explicitly here
                // (rather than leaving PJSUA_STUN_USE_DEFAULT) avoids silently depending on
                // whatever another account most recently registered with natUpdateStunServers.
                sipStunUse = if (stunServer != null) pjsua_stun_use.PJSUA_STUN_USE_DEFAULT else pjsua_stun_use.PJSUA_STUN_USE_DISABLED
                mediaStunUse = if (stunServer != null) pjsua_stun_use.PJSUA_STUN_USE_DEFAULT else pjsua_stun_use.PJSUA_STUN_USE_DISABLED
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

    override fun attendedTransferCall(callId: String, destinationCallId: String) {
        require(callId != destinationCallId) { "Attended transfer requires two different calls" }
        val call = checkNotNull(calls[callId]) { "Native source call does not exist" }
        val destinationCall = checkNotNull(calls[destinationCallId]) { "Native destination call does not exist" }
        val prm = CallOpParam(true)
        try {
            call.xferReplaces(destinationCall, prm)
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
            val devices = ep.audDevManager()
            if (!devices.sndIsActive()) {
                devices.setSndDevMode(0)
                Log.i(MEDIA_LOG_TAG, "call=$callId reopened sound device")
            }
            val playback = devices.playbackDevMedia
            val capture = devices.captureDevMedia
            // A conference port can be reused after a SIP hold. Restore neutral
            // per-port gains so a previous held/muted route cannot silence the new leg.
            audio.adjustTxLevel(1f)
            audio.adjustRxLevel(1f)
            capture.adjustTxLevel(1f)
            playback.adjustRxLevel(1f)
            audio.startTransmit(playback)
            capture.startTransmit(audio)
            logBridge(callId, audio, capture, devices.sndIsActive())
            runCatching {
                val stat = call.getStreamStat(0)
                try {
                    Log.i(
                        MEDIA_LOG_TAG,
                        "call=$callId reattached port=${audio.portId} rtpTx=${stat.rtcp.txStat.pkt} rtpRx=${stat.rtcp.rxStat.pkt}",
                    )
                } finally {
                    stat.delete()
                }
            }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$callId stream stats unavailable", it) }
            call.logAudioTransport()
        }
        call.muted = muted
        call.reportMediaState()
    }

    override fun setHeld(callId: String, held: Boolean) {
        val call = calls[callId] ?: return
        if (held) locallyHeldCallIds.add(callId)
        if (held) {
            // Detach while getAudioMedia() still refers to the active conference port.
            // After the hold re-INVITE PJSIP may replace it with a new inactive port,
            // making it too late to disconnect the original sound-device routes.
            endpoint?.let { ep ->
                runCatching {
                    val audio = call.getAudioMedia(-1)
                    audio.stopTransmit(ep.audDevManager().playbackDevMedia)
                    ep.audDevManager().captureDevMedia.stopTransmit(audio)
                    Log.i(MEDIA_LOG_TAG, "call=$callId pre-hold detached port=${audio.portId}")
                }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$callId pre-hold detach skipped", it) }
            }
        }
        val prm = CallOpParam(true)
        try {
            if (held) {
                call.setHold(prm)
            } else {
                prm.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
                call.reinvite(prm)
                locallyHeldCallIds.remove(callId)
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
                locallyHeldCallIds.remove(id)
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
            val hasActiveAudio = info.media.any {
                it.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                    it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
            }
            val locallyHeld = info.media.any { it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD } ||
                locallyHeldCallIds.contains(id)
            val ep = endpoint ?: return
            Log.i(
                MEDIA_LOG_TAG,
                "call=$id media=${info.media.joinToString { "${it.index}:${it.type}:${it.dir}:${it.status}" }} activePorts=${ep.mediaActivePorts()} localHeld=$locallyHeld",
            )
            logAudioTransport()
            // A held call can emit a delayed ACTIVE media callback while its
            // re-INVITE is settling. Never reconnect that leg to the sound
            // device, or it can steal the consultation call's conference port.
            if (hasActiveAudio && !locallyHeld) {
                runCatching {
                    val audio = getAudioMedia(-1)
                    audio.adjustTxLevel(1f)
                    audio.adjustRxLevel(1f)
                    audio.startTransmit(ep.audDevManager().playbackDevMedia)
                    if (!muted) ep.audDevManager().captureDevMedia.startTransmit(audio)
                    Log.i(MEDIA_LOG_TAG, "call=$id attached port=${audio.portId}")
                }.onFailure { Log.e(MEDIA_LOG_TAG, "call=$id attach failed", it) }
            } else {
                // PJSIP's conference connections survive a SIP hold unless explicitly detached.
                // Leaving the held call connected can consume the sound device while a consultation
                // call is active, producing silence on the new leg.
                runCatching {
                    val audio = getAudioMedia(-1)
                    audio.stopTransmit(ep.audDevManager().playbackDevMedia)
                    ep.audDevManager().captureDevMedia.stopTransmit(audio)
                    Log.i(MEDIA_LOG_TAG, "call=$id detached port=${audio.portId}")
                }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$id detach skipped", it) }
            }
            reportMediaState(locallyHeld)
        }

        fun reportMediaState(held: Boolean? = null) {
            val localHold = held ?: runCatching {
                getInfo().media.any { it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD }
            }.getOrDefault(false)
            mediaEventCallback?.invoke(NativeMediaEvent(id, muted, localHold))
        }

        fun logAudioTransport() {
            runCatching {
                val stream = getStreamInfo(0)
                val transport = getMedTransportInfo(0)
                try {
                    Log.i(
                        MEDIA_LOG_TAG,
                        "call=$id localRtp=${transport.localRtpName} remoteRtp=${stream.remoteRtpAddress} " +
                            "sourceRtp=${transport.srcRtpName} codec=${stream.codecName}/${stream.codecClockRate}",
                    )
                } finally {
                    transport.delete()
                    stream.delete()
                }
            }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$id transport info unavailable", it) }
        }
    }

    private fun requireEndpoint(): Endpoint = checkNotNull(endpoint) { "PJSIP endpoint is not created" }

    private fun logBridge(callId: String, audio: org.pjsip.pjsua2.AudioMedia, capture: org.pjsip.pjsua2.AudioMedia, active: Boolean) {
        runCatching {
            val callPort = audio.portInfo
            val capturePort = capture.portInfo
            try {
                Log.i(
                    MEDIA_LOG_TAG,
                    "call=$callId sndActive=$active callPort=${callPort.portId}->${callPort.listeners.joinToString()} " +
                        "capturePort=${capturePort.portId}->${capturePort.listeners.joinToString()}",
                )
            } finally {
                callPort.delete()
                capturePort.delete()
            }
        }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$callId bridge info unavailable", it) }
    }

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

private const val MEDIA_LOG_TAG = "YeyoFoneMedia"
