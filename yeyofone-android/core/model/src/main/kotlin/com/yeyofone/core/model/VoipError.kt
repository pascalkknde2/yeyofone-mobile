package com.yeyofone.core.model

sealed interface VoipError {
    val safeMessage: String

    data class Authentication(override val safeMessage: String) : VoipError
    data class Dns(override val safeMessage: String) : VoipError
    data class Network(override val safeMessage: String) : VoipError
    data class Transport(override val safeMessage: String) : VoipError
    data class Tls(override val safeMessage: String) : VoipError
    data class SipResponse(val code: Int, override val safeMessage: String) : VoipError
    data class Media(override val safeMessage: String) : VoipError
    data class Native(override val safeMessage: String) : VoipError
    data class InvalidConfiguration(override val safeMessage: String) : VoipError
}
