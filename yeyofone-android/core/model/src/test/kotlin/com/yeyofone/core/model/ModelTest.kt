package com.yeyofone.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelTest {
    @Test
    fun `identifiers reject blank values`() {
        assertFailsWith<IllegalArgumentException> { SipAccountId(" ") }
        assertFailsWith<IllegalArgumentException> { CallId("") }
    }

    @Test
    fun `server configuration rejects invalid port`() {
        assertFailsWith<IllegalArgumentException> {
            SipServerConfiguration("example.com", "sip:example.com", port = 0,
                transport = TransportProtocol.TLS, securityMode = SecurityMode.REQUIRE_SECURE)
        }
    }

    @Test
    fun `credentials can be cleared in place`() {
        val credentials = SipCredentials("alice", "secret".toCharArray())
        credentials.clear()
        assertEquals(List(6) { '\u0000' }, credentials.password.toList())
    }
}
