package com.yeyofone.core.registration

import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.account.AccountSecretProvider
import com.yeyofone.core.model.RegistrationDetails
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.VoipError
import com.yeyofone.core.voip.NativeRegistrationEvent
import com.yeyofone.core.voip.RegistrationManager
import com.yeyofone.core.voip.SipRegistrationGateway
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class RegistrationCoordinator(
    private val accounts: AccountRepository,
    private val secrets: AccountSecretProvider,
    private val gateway: SipRegistrationGateway,
    private val network: NetworkStatus,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val random: Random = Random.Default,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) : RegistrationManager {
    private val states = ConcurrentHashMap<SipAccountId, MutableStateFlow<RegistrationState>>()
    private val details = ConcurrentHashMap<SipAccountId, MutableStateFlow<RegistrationDetails>>()
    private val jobs = ConcurrentHashMap<SipAccountId, Job>()

    override fun observe(accountId: SipAccountId): StateFlow<RegistrationState> =
        states.getOrPut(accountId) { MutableStateFlow(RegistrationState.Unregistered) }

    override fun observeDetails(accountId: SipAccountId): StateFlow<RegistrationDetails> =
        details.getOrPut(accountId) { MutableStateFlow(RegistrationDetails()) }

    override suspend fun register(accountId: SipAccountId) {
        jobs.remove(accountId)?.cancel()
        jobs[accountId] = scope.launch { registrationLoop(accountId) }
    }

    override suspend fun unregister(accountId: SipAccountId) {
        jobs.remove(accountId)?.cancel()
        state(accountId).value = RegistrationState.Unregistering
        runCatching { gateway.setRegistration(accountId, false) }
        gateway.remove(accountId)
        state(accountId).value = RegistrationState.Unregistered
    }

    private suspend fun registrationLoop(id: SipAccountId) {
        val account = accounts.observeAccount(id).first()
        if (account == null || !account.enabled) {
            state(id).value = RegistrationState.Disabled
            return
        }
        var attempt = 0
        while (true) {
            if (!network.online().first()) {
                state(id).value = RegistrationState.Failed(VoipError.Network("Device is offline"))
                network.online().filter { it }.first()
            }
            state(id).value = if (attempt == 0) RegistrationState.Registering else RegistrationState.Refreshing
            val started = clock.instant()
            val result = runCatching { registerOnce(id, account) }
            val event = result.getOrNull()
            if (event != null && event.sipCode in 200..299 && event.expirationSeconds > 0) {
                attempt = 0
                val now = clock.instant()
                val expires = now.plusSeconds(event.expirationSeconds)
                state(id).value = RegistrationState.Registered(expires)
                detail(id).value = RegistrationDetails(
                    event.sipCode, event.safeReason, java.time.Duration.between(started, now).toMillis().milliseconds,
                    now, null, expires, account.server.transport, account.server.registrarUri,
                )
                delayMillis(((event.expirationSeconds - 5).coerceAtLeast(1)) * 1_000)
                continue
            }
            val now = clock.instant()
            val error = result.exceptionOrNull()?.toVoipError() ?: event.toVoipError()
            val recoverable = error !is VoipError.Authentication && error !is VoipError.Tls
            val wait = if (recoverable) retryDelay(attempt++) else null
            val retryAt = wait?.let(now::plusMillis)
            state(id).value = RegistrationState.Failed(error, retryAt)
            detail(id).update { it.copy(
                sipResponseCode = event?.sipCode, reason = event?.safeReason, lastFailure = now,
                transport = account.server.transport, registrar = account.server.registrarUri,
            ) }
            if (wait == null) return
            delayMillis(wait)
        }
    }

    private suspend fun registerOnce(id: SipAccountId, account: com.yeyofone.core.model.SipAccount): NativeRegistrationEvent =
        coroutineScope {
            val event = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(15_000) { gateway.events.filter { it.accountId == id }.first() }
            }
            val found = secrets.consume(id) { password ->
                gateway.createOrUpdate(account, password)
                gateway.setRegistration(id, true)
            }
            if (!found) throw IllegalStateException("Account credential is unavailable")
            event.await()
        }

    private fun retryDelay(attempt: Int): Long {
        val base = min(60_000L, 1_000L shl attempt.coerceAtMost(6))
        return base + random.nextLong(0, base / 2 + 1)
    }

    private fun state(id: SipAccountId) = states.getOrPut(id) { MutableStateFlow(RegistrationState.Unregistered) }
    private fun detail(id: SipAccountId) = details.getOrPut(id) { MutableStateFlow(RegistrationDetails()) }
}

internal fun NativeRegistrationEvent?.toVoipError(): VoipError {
    val code = this?.sipCode
    return when {
        code == 401 || code == 403 || code == 407 -> VoipError.Authentication("Registration authentication failed")
        code != null && code >= 500 -> VoipError.SipResponse(code, "Registration server error")
        code != null -> VoipError.SipResponse(code, "Registration rejected")
        else -> VoipError.Network("Registration timed out")
    }
}

internal fun Throwable.toVoipError(): VoipError {
    if (this is kotlinx.coroutines.TimeoutCancellationException) return VoipError.Network("Registration timed out")
    if (this is CancellationException) throw this
    val safe = message.orEmpty().lowercase()
    return when {
        "credential" in safe -> VoipError.Authentication("Account credential is unavailable")
        "tls" in safe || "certificate" in safe -> VoipError.Tls("Secure transport failed")
        "dns" in safe || "resolve" in safe -> VoipError.Dns("Registrar could not be resolved")
        "transport" in safe -> VoipError.Transport("SIP transport failed")
        else -> VoipError.Network("Registration failed")
    }
}
