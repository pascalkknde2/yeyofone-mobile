package com.yeyofone.core.voip.pjsip

import android.util.Log
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pj_ssl_sock_proto
import org.pjsip.pjsua2.pjsip_transport_type_e
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.SignalingPolicyException
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.model.signalingPolicyViolation
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
import org.pjsip.pjsua2.OnCallTsxStateParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnCallTransferStatusParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.OnTransportStateParam
import org.pjsip.pjsua2.pjsip_transport_state
import org.pjsip.pjsua2.StringVector
import org.pjsip.pjsua2.IntVector
import org.pjsip.pjsua2.pjmedia_srtp_keying_method
import org.pjsip.pjsua2.pjmedia_srtp_use
import org.pjsip.pjsua2.pjmedia_tp_proto
import org.pjsip.pjsua2.pjsua_call_flag
import org.pjsip.pjsua2.pjsua_call_media_status
import org.pjsip.pjsua2.pjsua_dtmf_method
import org.pjsip.pjsua2.pjsua_stun_use
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_event_id_e
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/** Owns all generated PJSUA2 objects. Calls are serialized by [PjsipEngine]. */
internal class Pjsua2EndpointBackend : EndpointBackend {
    private companion object {
        // Preserve decoded sample levels: boosting loud remote speech clips PCM audio.
        // Listening volume belongs to Android's voice-call volume control.
        const val CALL_RECEIVE_GAIN = 1f
    }
    private var endpoint: Endpoint? = null
    private var verboseDiagnostics = false
    // Endpoint owns the native writer; keep its Java director alive until libDestroy().
    private var sipLogWriter: org.pjsip.pjsua2.LogWriter? = null
    private val transportIds = mutableMapOf<SipTransport, Int>()
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
        endpoint = TransportLoggingEndpoint().also { it.libCreate() }
    }

    override fun initialize(configuration: PjsipEngineConfiguration) {
        verboseDiagnostics = configuration.verboseDiagnostics
        val config = EpConfig()
        try {
            config.uaConfig.userAgent = configuration.userAgent
            config.logConfig.apply {
                if (verboseDiagnostics) {
                    level = maxOf(configuration.logLevel, 4).toLong()
                    // PJSUA gates the custom writer with consoleLevel as well as level.
                    // The writer emits only the sanitized summary, never the raw packet.
                    consoleLevel = level
                    msgLogging = 1
                    sipLogWriter = object : org.pjsip.pjsua2.LogWriter() {
                        override fun write(entry: org.pjsip.pjsua2.LogEntry) {
                            sipWireSummary(entry.msg)?.let { Log.i(SIP_LOG_TAG, it) }
                            // Opt-in only (adb shell setprop log.tag.YeyoFoneSipTrace DEBUG): the
                            // full packet, minus digest credentials, for PBX interop debugging.
                            if (Log.isLoggable(SIP_TRACE_LOG_TAG, Log.DEBUG)) {
                                sipWireTrace(entry.msg)?.let { Log.d(SIP_TRACE_LOG_TAG, it) }
                            }
                        }
                    }
                    writer = sipLogWriter
                } else {
                    level = configuration.logLevel.toLong()
                    consoleLevel = configuration.logLevel.toLong()
                    msgLogging = 0
                }
            }
            requireEndpoint().libInit(config)
            configureVoiceCodecs()
        } finally {
            config.delete()
        }
    }

    /** Prefer wideband codecs while retaining narrowband fallbacks for older SIP peers. */
    private fun configureVoiceCodecs() {
        val ep = requireEndpoint()
        val codecs = ep.codecEnum2()
        try {
            codecs.forEach { codec ->
                val priority = when {
                    codec.codecId.startsWith("opus/", ignoreCase = true) -> 255
                    codec.codecId.startsWith("G722/", ignoreCase = true) -> 220
                    codec.codecId.startsWith("PCMU/", ignoreCase = true) -> 100
                    codec.codecId.startsWith("PCMA/", ignoreCase = true) -> 90
                    else -> null
                }
                priority?.let {
                    ep.codecSetPriority(codec.codecId, it.toShort())
                    Log.i(MEDIA_LOG_TAG, "codec=${codec.codecId} priority=$it")
                }
            }
        } finally {
            codecs.delete()
        }
    }

    override fun createTransports(transports: Set<SipTransport>) {
        transports.forEach { transport ->
            val config = TransportConfig()
            try {
                if (transport == SipTransport.TLS) {
                    config.tlsConfig.apply {
                        proto = (pj_ssl_sock_proto.PJ_SSL_SOCK_PROTO_TLS1_2 or
                            pj_ssl_sock_proto.PJ_SSL_SOCK_PROTO_TLS1_3).toLong()
                        caBuf = SystemTrustStore.pemBundle()
                        // Rejects untrusted chains and certificates not naming the SIP host.
                        verifyServer = true
                        verifyClient = false
                        requireClientCert = false
                    }
                }
                transportIds[transport] = requireEndpoint().transportCreate(transport.toPjsipTransportType(), config)
            } catch (e: Exception) {
                // A native build without TLS must not take UDP/TCP accounts down with it; TLS
                // accounts fail explicitly in createOrUpdateAccount instead of downgrading.
                if (transport != SipTransport.TLS) throw e
                Log.e(TLS_LOG_TAG, "TLS transport unavailable", e)
            } finally {
                config.delete()
            }
        }
    }

    override fun start() = requireEndpoint().libStart()

    override fun destroy() {
        val current = endpoint ?: return
        endpoint = null
        transportIds.clear()
        calls.values.forEach { runCatching { it.hangup(voiceCallOpParam()) }; it.delete() }
        calls.clear()
        locallyHeldCallIds.clear()
        accounts.values.forEach { it.shutdown(); it.delete() }
        accounts.clear()
        try {
            current.libDestroy()
        } finally {
            current.delete()
            sipLogWriter = null
        }
    }

    override fun createOrUpdateAccount(
        account: SipAccount,
        password: CharArray,
        callback: (NativeRegistrationEvent) -> Unit,
    ) {
        // Stored accounts predate validation changes; refuse anything that would bypass TLS.
        account.server.signalingPolicyViolation()?.let { throw SignalingPolicyException(it) }
        val tlsTransportId = if (account.server.transport == TransportProtocol.TLS) {
            checkNotNull(transportIds[SipTransport.TLS]) { "TLS transport is unavailable" }
        } else {
            null
        }
        removeAccount(account.id)
        val config = AccountConfig()
        val credential = AuthCredInfo("digest", "*", account.authenticationUsername, 0, String(password))
        val credentials = AuthCredInfoVector().apply { add(credential) }
        val proxies = StringVector()
        val srtpKeyings = IntVector().apply { add(pjmedia_srtp_keying_method.PJMEDIA_SRTP_KEYING_SDES) }
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
            // Pin TLS accounts to the verified TLS transport so signaling cannot fall back.
            tlsTransportId?.let { config.sipConfig.transportId = it }
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
                // Without STUN/ICE, advertise the address learned from REGISTER's Via
                // instead of a private device address that an external PBX cannot reach.
                // STUN/ICE keep control of their own media address selection.
                sdpNatRewriteUse = if (stunServer == null && !account.nat.iceEnabled) 1 else 0
                // RFC 5626 "SIP outbound" is PJSIP's default (and documented as a no-op
                // over UDP transports), but it still tags every Contact header with ";ob".
                // Live-verified against sysinfos.co.uk/FreeSWITCH: with ";ob" present,
                // FreeSWITCH answers every inbound call (clean INVITE/200/ACK) but never
                // transmits a single inbound RTP packet in either direction, confirmed via
                // its own RTCP (Receiver Reports only, never a Sender Report) - most likely
                // mod_sofia applying RFC 5626 flow-based NAT/routing logic to a UDP
                // registration that never asked for it. Disabling it (plain Contact, no
                // ";ob") immediately produced clean bidirectional RTP on two consecutive
                // inbound calls. This was never a problem on outgoing calls, which don't
                // carry the Contact from REGISTER.
                sipOutboundUse = 0
                turnEnabled = !account.nat.turnServer.isNullOrBlank()
                account.nat.turnServer?.let { turnServer = it }
                account.nat.turnUsername?.let { turnUserName = it }
            }
            config.mediaConfig.apply {
                // Mandatory, never optional: a "require SRTP" account must not fall back to RTP.
                srtpUse = if (account.nat.srtpEnabled) {
                    pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY
                } else {
                    pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED
                }
                // SDES only (the supported mode), and only when the first signaling hop is TLS,
                // because SDES keys travel in the SDP.
                srtpSecureSignaling = SRTP_SECURE_SIGNALING_TLS_HOP
                srtpOpt.keyings = srtpKeyings
            }
            NativeAccount(account.id, callback).also { native ->
                native.create(config)
                accounts[account.id] = native
            }
        } finally {
            proxies.delete()
            srtpKeyings.delete()
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

    /** PJSIP 2.17 defaults include RTT; this app implements voice calls only. */
    private fun voiceCallOpParam(): CallOpParam = CallOpParam(true).apply {
        opt.audioCount = 1
        opt.videoCount = 0
        opt.textCount = 0
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
        val prm = voiceCallOpParam()
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
        Log.i("YeyoFoneCall", "call=$callId localHangupRequested=true")
        if (verboseDiagnostics) call.logFinalMediaStats()
        val prm = voiceCallOpParam()
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
        val prm = voiceCallOpParam().apply { statusCode = 200 }
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
        val prm = voiceCallOpParam()
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
        val prm = voiceCallOpParam()
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
        } else if (locallyHeldCallIds.contains(callId)) {
            // Held calls are deliberately detached from the sound device (see setHeld/
            // onCallMediaState) so they can't steal a consultation call's conference port.
            // Unmuting a held call must not undo that detachment.
            call.muted = muted
            call.reportMediaState()
            return
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
            audio.adjustRxLevel(CALL_RECEIVE_GAIN)
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
                        "call=$callId reattached port=${audio.portId} rtpTx=${stat.rtcp.txStat.pkt} rtpRx=${stat.rtcp.rxStat.pkt}" +
                            " rxLost=${stat.rtcp.rxStat.loss} rxDiscard=${stat.rtcp.rxStat.discard}" +
                            " jitterBufferLost=${stat.jbuf.lost} jitterBufferEmpty=${stat.jbuf.empty}",
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
        val prm = voiceCallOpParam()
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
            // Status code and reason only: no URIs, usernames or credentials (SEC-08).
            Log.i(REGISTRATION_LOG_TAG, "account=${id.value} code=${prm.code} reason=${prm.reason?.take(60)}")
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
                val op = voiceCallOpParam().apply { statusCode = 486 }
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
            call.relayCallId = runCatching { relayCallIdFromInvite(prm.rdata.wholeMsg) }.getOrNull()
            calls[callId] = call
            runCatching { call.getInfo().remoteUri }.getOrNull()?.let { call.remoteUri = it }
            val ringing = voiceCallOpParam().apply { statusCode = 180 }
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
        var relayCallId: String? = null

        fun reportState() {
            val info = runCatching { getInfo() }.getOrNull()
            val invState = info?.state ?: pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED
            // Numeric signaling details only: never log peer URIs or SIP message bodies.
            Log.i("YeyoFoneCall", "call=$id direction=$direction state=$invState code=${info?.lastStatusCode ?: 0}")
            callback(
                NativeCallEvent(
                    id,
                    accountId,
                    remoteUri,
                    direction,
                    invState,
                    info?.lastStatusCode ?: 0,
                    info?.lastReason?.replace(Regex("[\\r\\n]"), " ")?.take(120),
                    relayCallId,
                ),
            )
            if (invState == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                calls.remove(id)
                locallyHeldCallIds.remove(id)
                delete()
            }
        }

        override fun onCallState(prm: OnCallStateParam) = reportState()

        override fun onCallTsxState(prm: OnCallTsxStateParam) {
            if (!verboseDiagnostics) return
            val event = prm.e.body.tsxState
            val transaction = event.tsx
            Log.i(
                "YeyoFoneCall",
                "call=$id method=${transaction.method} role=${transaction.role} " +
                    "state=${transaction.state} code=${transaction.statusCode} event=${event.type}",
            )
            if (transaction.method == "BYE" && event.type == pjsip_event_id_e.PJSIP_EVENT_RX_MSG) {
                val causes = sipReasonCauses(event.src.rdata.wholeMsg)
                Log.i("YeyoFoneCall", "call=$id remoteHangup=true causes=${causes.ifEmpty { "unspecified" }}")
                // Still inside the dialog, so the media session is alive and its counters readable.
                logFinalMediaStats()
            }
        }

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
            val isOnHold = locallyHeld || info.media.any {
                it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD
            }
            val ep = endpoint ?: return
            if (Log.isLoggable(MEDIA_LOG_TAG, Log.DEBUG)) {
                // Gated: building this string and logAudioTransport()'s extra native round-trips
                // run on every media transition on the PJSIP signaling thread, not just once, so
                // this must stay off unless someone has explicitly enabled debug logging for the
                // tag (adb shell setprop log.tag.YeyoFoneMedia DEBUG).
                Log.d(
                    MEDIA_LOG_TAG,
                    "call=$id media=${info.media.joinToString { "${it.index}:${it.type}:${it.dir}:${it.status}" }} activePorts=${ep.mediaActivePorts()} localHeld=$locallyHeld",
                )
                logAudioTransport()
            }
            // A held call can emit a delayed ACTIVE media callback while its
            // re-INVITE is settling. Never reconnect that leg to the sound
            // device, or it can steal the consultation call's conference port.
            if (hasActiveAudio && !locallyHeld) {
                runCatching {
                    val audio = getAudioMedia(-1)
                    audio.adjustTxLevel(1f)
                    audio.adjustRxLevel(CALL_RECEIVE_GAIN)
                    audio.startTransmit(ep.audDevManager().playbackDevMedia)
                    if (!muted) ep.audDevManager().captureDevMedia.startTransmit(audio)
                    Log.i(MEDIA_LOG_TAG, "call=$id attached port=${audio.portId}")
                }.onFailure { Log.e(MEDIA_LOG_TAG, "call=$id attach failed", it) }
            } else if (isOnHold) {
                // PJSIP's conference connections survive a SIP hold unless explicitly detached.
                // Leaving the held call connected can consume the sound device while a consultation
                // call is active, producing silence on the new leg.
                runCatching {
                    val audio = getAudioMedia(-1)
                    audio.stopTransmit(ep.audDevManager().playbackDevMedia)
                    ep.audDevManager().captureDevMedia.stopTransmit(audio)
                    Log.i(MEDIA_LOG_TAG, "call=$id detached port=${audio.portId}")
                }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$id detach skipped", it) }
            } else {
                // NONE/ERROR are ambiguous, possibly-transient statuses (e.g. mid codec/ICE
                // renegotiation) rather than a deliberate hold - leave existing routing untouched
                // rather than risk a permanent detach with no later callback to reattach it.
                Log.w(MEDIA_LOG_TAG, "call=$id media status is neither active nor on hold; routing left unchanged")
            }
            reportMediaState(locallyHeld)
        }

        fun reportMediaState(held: Boolean? = null) {
            val localHold = held ?: runCatching {
                getInfo().media.any { it.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD }
            }.getOrDefault(false)
            mediaEventCallback?.invoke(NativeMediaEvent(id, muted, localHold))
        }

        /**
         * One line of hard RTP evidence per call, emitted as the call tears down: whether packets
         * actually left and arrived, and which source they arrived from. Without it a silent call
         * cannot be told apart from a call whose media never reached the network.
         */
        fun logFinalMediaStats() {
            runCatching {
                val stat = getStreamStat(0)
                val transport = getMedTransportInfo(0)
                try {
                    Log.i(
                        MEDIA_LOG_TAG,
                        "call=$id direction=$direction rtpTx=${stat.rtcp.txStat.pkt} rtpRx=${stat.rtcp.rxStat.pkt} " +
                            "rxLoss=${stat.rtcp.rxStat.loss} rxDiscard=${stat.rtcp.rxStat.discard} " +
                            "jbufEmpty=${stat.jbuf.avgBurst} sourceRtp=${transport.srcRtpName}",
                    )
                } finally {
                    transport.delete()
                    stat.delete()
                }
            }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$id final media stats unavailable", it) }
        }

        fun logAudioTransport() {
            runCatching {
                val stream = getStreamInfo(0)
                val transport = getMedTransportInfo(0)
                try {
                    Log.i(
                        MEDIA_LOG_TAG,
                        mediaTransportLogLine(
                            id,
                            "${stream.codecName}/${stream.codecClockRate}",
                            stream.proto and pjmedia_tp_proto.PJMEDIA_TP_PROFILE_SRTP != 0,
                            if (verboseDiagnostics) {
                                MediaAddresses(transport.localRtpName, stream.remoteRtpAddress, transport.srcRtpName)
                            } else {
                                null
                            },
                        ),
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
                        "capturePort=${capturePort.portId}->${capturePort.listeners.joinToString()} " +
                        "callRxLevel=${audio.rxLevel} callTxLevel=${audio.txLevel} " +
                        "captureRxLevel=${capture.rxLevel} captureTxLevel=${capture.txLevel}",
                )
            } finally {
                callPort.delete()
                capturePort.delete()
            }
        }.onFailure { Log.w(MEDIA_LOG_TAG, "call=$callId bridge info unavailable", it) }
    }

    /** Surfaces TLS handshake and certificate-verification outcomes without peer identities. */
    private class TransportLoggingEndpoint : Endpoint() {
        override fun onTransportState(prm: OnTransportStateParam) {
            if (!prm.type.startsWith("TLS", ignoreCase = true)) return
            val tls = prm.tlsInfo
            val state = when (prm.state) {
                pjsip_transport_state.PJSIP_TP_STATE_CONNECTED -> "connected"
                pjsip_transport_state.PJSIP_TP_STATE_DISCONNECTED -> "disconnected"
                else -> return
            }
            Log.i(
                TLS_LOG_TAG,
                "state=$state established=${tls.established} cipher=${tls.cipherName} " +
                    "verifyStatus=0x${tls.verifyStatus.toString(16)} lastError=${prm.lastError}",
            )
        }
    }

    private object NativeLibrary {
        init {
            System.loadLibrary("pjsua2")
        }

        fun load() = Unit
    }
}

/**
 * Reads the PBX adapter's `X-Yeyo-Call-ID` header from a raw INVITE. Only the header section is
 * searched; a missing, repeated or malformed header yields null. The value only correlates relay
 * pushes with this call and is never treated as authentication.
 */
internal fun relayCallIdFromInvite(message: String): String? {
    val headerSection = message.replace("\r\n", "\n").substringBefore("\n\n")
    val values = headerSection.lineSequence().drop(1).mapNotNull { line ->
        val name = line.substringBefore(':', "").trim()
        if (name.equals(RELAY_CALL_ID_HEADER, ignoreCase = true)) line.substringAfter(':').trim() else null
    }.toList()
    return values.singleOrNull()?.takeIf { RELAY_ID_PATTERN.matches(it) }
}

private const val RELAY_CALL_ID_HEADER = "X-Yeyo-Call-ID"
private val RELAY_ID_PATTERN = Regex("^[A-Za-z0-9_.@+-]{1,128}$")

/** Only standardized numeric causes are safe to log; reason text can contain peer identities. */
internal fun sipReasonCauses(message: String): String =
    message.replace("\r\n", "\n").substringBefore("\n\n")
        .lineSequence().drop(1)
        .filter { it.substringBefore(':').trim().equals("Reason", ignoreCase = true) }
        .flatMap { it.substringAfter(':').split(',').asSequence() }
        .mapNotNull { reason ->
            Regex("^\\s*(SIP|Q\\.850)\\s*;\\s*cause\\s*=\\s*(\\d{1,3})(?=\\s*(?:;|$))", RegexOption.IGNORE_CASE)
                .find(reason)?.let { "${it.groupValues[1].uppercase()}:${it.groupValues[2].toInt()}" }
        }.joinToString(",")

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

/** RTP endpoints of a call; network identifiers, logged only with verbose diagnostics. */
internal data class MediaAddresses(val localRtp: String, val remoteRtp: String, val sourceRtp: String)

/** Media summary for logs (SEC-08): addresses are included only when explicitly provided. */
internal fun mediaTransportLogLine(callId: String, codec: String, srtp: Boolean, addresses: MediaAddresses?): String =
    buildString {
        append("call=").append(callId).append(" codec=").append(codec).append(" srtp=").append(srtp)
        if (addresses != null) {
            append(" localRtp=").append(addresses.localRtp)
            append(" remoteRtp=").append(addresses.remoteRtp)
            append(" sourceRtp=").append(addresses.sourceRtp)
        }
    }
private const val TLS_LOG_TAG = "YeyoFoneTls"
// pjsua_acc_config.srtp_secure_signaling: 1 = require TLS on the first hop (2 would demand sips:).
private const val SRTP_SECURE_SIGNALING_TLS_HOP = 1
private const val REGISTRATION_LOG_TAG = "YeyoFoneRegistration"
private const val SIP_LOG_TAG = "YeyoFoneSip"
private const val SIP_TRACE_LOG_TAG = "YeyoFoneSipTrace"
