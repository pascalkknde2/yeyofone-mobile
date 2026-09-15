package com.yeyofone.core.model

import java.time.Instant
import kotlin.time.Duration

sealed interface RegistrationState {
    data object Disabled : RegistrationState
    data object Registering : RegistrationState
    data class Registered(val expiresAt: Instant) : RegistrationState
    data object Refreshing : RegistrationState
    data class Failed(val error: VoipError, val retryAt: Instant? = null) : RegistrationState
    data object Unregistering : RegistrationState
    data object Unregistered : RegistrationState
}

data class RegistrationDetails(
    val sipResponseCode: Int? = null,
    val reason: String? = null,
    val latency: Duration? = null,
    val lastSuccessfulRegistration: Instant? = null,
    val lastFailure: Instant? = null,
    val expiresAt: Instant? = null,
    val transport: TransportProtocol? = null,
    val registrar: String? = null,
)
