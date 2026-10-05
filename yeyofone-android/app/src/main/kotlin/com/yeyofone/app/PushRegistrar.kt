package com.yeyofone.app

import android.content.Context
import android.util.Log
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the push-relay lifecycle of each account: registration on FCM token rotation, account
 * enable or credential import; unregister/revoke when an account is disabled, removed or renamed;
 * and bounded retries with backoff. One job runs per account at a time, so a later request (for
 * example a revoke after a register) replaces the earlier one.
 *
 * Retries live in this process. Anything still unfinished when the process dies is picked up on
 * the next start: every enabled account re-registers and pending revocations are retried.
 */
internal class PushRegistrar(
    private val context: Context,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val jobs = ConcurrentHashMap<SipAccountId, Job>()

    /** Registers with the cached FCM token, if there is one yet. Never preempts a revocation. */
    fun register(account: SipAccount) {
        val token = PushRelayClient.cachedToken(context) ?: return
        if (PushRelayClient.isRevocationPending(context, account.id)) return
        launchFor(account.id) {
            var attempt = 0
            while (true) {
                val (status, response) = PushRelayClient.register(context, account, token)
                if (status != PushRelayClient.Status.FAILED || attempt >= MAX_ATTEMPTS) break
                delay(retryDelayMillis(attempt++, response?.retryAfterSeconds, random))
            }
        }
    }

    /** Called after an FCM token rotation was saved; every enabled account re-registers. */
    fun onTokenRefreshed(accounts: List<SipAccount>) {
        accounts.filter { it.enabled }.forEach(::register)
    }

    /**
     * Unregisters the account's device and revokes its credential, for account disable, removal or
     * a username change ([username] is the one the credential was issued for). Persisted first, so
     * the revocation survives process death and the credential is never used again.
     */
    fun revoke(accountId: SipAccountId, username: String) {
        if (!PushRelayClient.markForRevocation(context, accountId, username)) return
        launchRevocation(accountId)
    }

    /** Retries revocations left unfinished by an earlier process. */
    fun resumePendingRevocations() {
        PushRelayClient.pendingRevocations(context).forEach(::launchRevocation)
    }

    private fun launchRevocation(accountId: SipAccountId) = launchFor(accountId) {
        var attempt = 0
        while (true) {
            val outcome = PushRelayClient.revokePending(context, accountId)
            if (outcome !is RevokeOutcome.Retry || attempt >= MAX_ATTEMPTS) {
                if (outcome is RevokeOutcome.Retry) {
                    Log.w(TAG, "account=${accountId.value} revocation still pending; retried on next start")
                }
                break
            }
            delay(retryDelayMillis(attempt++, outcome.retryAfterSeconds, random))
        }
    }

    private fun launchFor(accountId: SipAccountId, block: suspend () -> Unit) {
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) { block() }
        jobs.put(accountId, job)?.cancel()
        job.invokeOnCompletion { jobs.remove(accountId, job) }
        job.start()
    }

    companion object {
        private const val TAG = "YeyoFonePush"
        const val MAX_ATTEMPTS = 6
        private const val BASE_DELAY_MS = 30_000L
        private const val MAX_DELAY_MS = 30 * 60_000L
        private const val MAX_RETRY_AFTER_MS = 60 * 60_000L

        /**
         * Exponential backoff from 30 s, capped at 30 min, with +/-20 % jitter; a relay
         * Retry-After (429: 60 s, 503: 2 s) wins when it is longer, capped at one hour.
         */
        fun retryDelayMillis(attempt: Int, retryAfterSeconds: Long?, random: Random): Long {
            val exponential = (BASE_DELAY_MS shl attempt.coerceIn(0, 10)).coerceAtMost(MAX_DELAY_MS)
            val jittered = (exponential * random.nextDouble(0.8, 1.2)).toLong()
            val retryAfter = retryAfterSeconds?.let { (it * 1000).coerceAtMost(MAX_RETRY_AFTER_MS) } ?: 0
            return maxOf(jittered, retryAfter)
        }
    }
}
