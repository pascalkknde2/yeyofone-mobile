package com.yeyofone.core.voip.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayCallIdTest {
    private fun invite(vararg headers: String): String =
        (listOf("INVITE sip:1005@pbx.example.com SIP/2.0", "Via: SIP/2.0/TLS pbx.example.com") + headers.toList())
            .joinToString("\r\n") + "\r\n\r\n" + "v=0\r\nX-Yeyo-Call-ID: body-not-header\r\n"

    @Test
    fun `reads the adapter header case-insensitively`() {
        assertEquals("6c40cf1a-5487", relayCallIdFromInvite(invite("X-Yeyo-Call-ID: 6c40cf1a-5487")))
        assertEquals("abc", relayCallIdFromInvite(invite("x-yeyo-call-id:abc")))
    }

    @Test
    fun `ignores the body, repeats and malformed values`() {
        assertNull(relayCallIdFromInvite(invite()))
        assertNull(relayCallIdFromInvite(invite("X-Yeyo-Call-ID: a", "X-Yeyo-Call-ID: b")))
        assertNull(relayCallIdFromInvite(invite("X-Yeyo-Call-ID: has space")))
        assertNull(relayCallIdFromInvite(invite("X-Yeyo-Call-ID: " + "x".repeat(129))))
        assertNull(relayCallIdFromInvite(invite("X-Yeyo-Call-ID-Other: abc")))
    }

    @Test
    fun `accepts LF-only messages`() {
        assertEquals("abc", relayCallIdFromInvite("INVITE sip:a SIP/2.0\nX-Yeyo-Call-ID: abc\n\nbody"))
    }
}
