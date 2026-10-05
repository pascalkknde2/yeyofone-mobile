package com.yeyofone.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushRegistrarTest {
    /** Always returns the lower jitter bound (0.8) or the upper one (just under 1.2). */
    private class FixedRandom(private val upper: Boolean) : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble(from: Double, until: Double) = if (upper) until - 1e-9 else from
    }

    @Test
    fun `backoff doubles from 30 seconds with bounded jitter`() {
        val low = FixedRandom(upper = false)
        val high = FixedRandom(upper = true)
        assertEquals(24_000, PushRegistrar.retryDelayMillis(0, null, low))
        assertEquals(48_000, PushRegistrar.retryDelayMillis(1, null, low))
        assertEquals(96_000, PushRegistrar.retryDelayMillis(2, null, low))
        assertTrue(PushRegistrar.retryDelayMillis(0, null, high) in 35_999..36_000)
    }

    @Test
    fun `backoff is capped at 30 minutes plus jitter`() {
        val high = FixedRandom(upper = true)
        assertTrue(PushRegistrar.retryDelayMillis(20, null, high) <= 36 * 60_000)
        assertTrue(PushRegistrar.retryDelayMillis(PushRegistrar.MAX_ATTEMPTS, null, high) <= 36 * 60_000)
    }

    @Test
    fun `longer relay Retry-After wins and is capped at one hour`() {
        val low = FixedRandom(upper = false)
        assertEquals(24_000, PushRegistrar.retryDelayMillis(0, 2, low)) // 503 -> 2 s, backoff larger
        assertEquals(60_000, PushRegistrar.retryDelayMillis(0, 60, low)) // 429 -> 60 s
        assertEquals(3_600_000, PushRegistrar.retryDelayMillis(0, 86_400, low))
    }

    @Test
    fun `Retry-After accepts delta seconds only`() {
        assertEquals(60, parseRetryAfter("60"))
        assertEquals(2, parseRetryAfter(" 2 "))
        assertNull(parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(parseRetryAfter("-5"))
        assertNull(parseRetryAfter(null))
    }

    @Test
    fun `revocation keeps the credential only when the relay could not answer`() {
        assertIs<RevokeOutcome.Confirmed>(revokeOutcomeFor(PushRelayClient.Response(204)))
        assertIs<RevokeOutcome.Confirmed>(revokeOutcomeFor(PushRelayClient.Response(404)))
        assertIs<RevokeOutcome.AlreadyInvalid>(revokeOutcomeFor(PushRelayClient.Response(401)))
        assertIs<RevokeOutcome.AlreadyInvalid>(revokeOutcomeFor(PushRelayClient.Response(403)))
        assertEquals(RevokeOutcome.Retry(60), revokeOutcomeFor(PushRelayClient.Response(429, 60)))
        assertEquals(RevokeOutcome.Retry(2), revokeOutcomeFor(PushRelayClient.Response(503, 2)))
        assertEquals(RevokeOutcome.Retry(null), revokeOutcomeFor(PushRelayClient.Response(null)))
    }
}
