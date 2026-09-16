package com.yeyofone.core.voip.pjsip

import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.NativeCallEvent
import com.yeyofone.core.voip.NativeMediaEvent
import com.yeyofone.core.voip.NativeRegistrationEvent

/** Internal seam around generated PJSUA2 bindings; native objects never cross this boundary. */
internal interface EndpointBackend {
    fun create()
    fun initialize(configuration: PjsipEngineConfiguration)
    fun createTransports(transports: Set<SipTransport>)
    fun start()
    fun destroy()
    fun createOrUpdateAccount(account: SipAccount, password: CharArray, callback: (NativeRegistrationEvent) -> Unit): Unit =
        error("Registration is not supported by this backend")
    fun setRegistration(accountId: SipAccountId, renew: Boolean): Unit =
        error("Registration is not supported by this backend")
    fun removeAccount(accountId: SipAccountId) = Unit
    fun makeCall(accountId: SipAccountId, destination: String, callback: (NativeCallEvent) -> Unit): String =
        error("Calling is not supported by this backend")
    fun answerCall(callId: String): Unit =
        error("Calling is not supported by this backend")
    fun hangupCall(callId: String): Unit =
        error("Calling is not supported by this backend")
    fun sendDtmf(callId: String, digit: Char): Unit =
        error("DTMF is not supported by this backend")
    fun setMuted(callId: String, muted: Boolean): Unit =
        error("Mute is not supported by this backend")
    fun setHeld(callId: String, held: Boolean): Unit =
        error("Hold is not supported by this backend")
    fun setCallEventListener(callback: (NativeCallEvent) -> Unit) = Unit
    fun setMediaEventListener(callback: (NativeMediaEvent) -> Unit) = Unit
}

enum class SipTransport { UDP, TCP, TLS }

data class PjsipEngineConfiguration(
    val userAgent: String = "YeyoFone Android",
    val logLevel: Int = 3,
    val transports: Set<SipTransport> = setOf(SipTransport.UDP),
) {
    init {
        require(userAgent.isNotBlank()) { "User agent must not be blank" }
        require(logLevel in 0..6) { "PJSIP log level must be between 0 and 6" }
        require(transports.isNotEmpty()) { "At least one SIP transport is required" }
    }
}
