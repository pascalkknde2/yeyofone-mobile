package com.yeyofone.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignalingPolicyTest {
    @Test
    fun `matching or absent transport parameters are allowed`() {
        assertNull(server(TransportProtocol.TLS, "sip:pbx.example.com").signalingPolicyViolation())
        assertNull(server(TransportProtocol.TLS, "sips:pbx.example.com;transport=TLS").signalingPolicyViolation())
        assertNull(server(TransportProtocol.UDP, "sip:pbx.example.com;transport=udp").signalingPolicyViolation())
        assertNull(server(TransportProtocol.TCP, "sip:pbx.example.com", proxy = " ").signalingPolicyViolation())
    }

    @Test
    fun `secure mode without TLS is refused`() {
        val violation = server(TransportProtocol.UDP, "sip:pbx.example.com", SecurityMode.REQUIRE_SECURE)
            .signalingPolicyViolation()!!
        assertEquals("transport", violation.field)
        assertTrue(violation.bypassesTls)
    }

    @Test
    fun `URI parameters cannot bypass TLS`() {
        val registrar = server(TransportProtocol.TLS, "sip:pbx.example.com;lr;Transport=UDP").signalingPolicyViolation()!!
        assertEquals("registrarUri", registrar.field)
        assertTrue(registrar.bypassesTls)
        val proxy = server(TransportProtocol.TLS, "sip:pbx.example.com", proxy = "sip:edge.example.com;transport=tcp")
            .signalingPolicyViolation()!!
        assertEquals("outboundProxyUri", proxy.field)
        assertTrue(proxy.bypassesTls)
    }

    @Test
    fun `non TLS conflicts are configuration errors`() {
        val sips = server(TransportProtocol.UDP, "sips:pbx.example.com").signalingPolicyViolation()!!
        assertFalse(sips.bypassesTls)
        val mismatch = server(TransportProtocol.UDP, "sip:pbx.example.com;transport=tcp").signalingPolicyViolation()!!
        assertFalse(mismatch.bypassesTls)
    }

    @Test
    fun `user part parameters are not mistaken for URI parameters`() {
        assertNull(server(TransportProtocol.TLS, "sip:100;transport=udp@pbx.example.com").signalingPolicyViolation())
    }

    private fun server(
        transport: TransportProtocol,
        registrar: String,
        mode: SecurityMode = if (transport == TransportProtocol.TLS) SecurityMode.REQUIRE_SECURE else SecurityMode.ALLOW_INSECURE,
        proxy: String? = null,
    ) = SipServerConfiguration("pbx.example.com", registrar, proxy, 5061, transport, mode)
}
