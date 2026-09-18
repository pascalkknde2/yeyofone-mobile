package com.yeyofone.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SipIdentityTest {
    @Test
    fun `quoted display name and extension are parsed`() {
        assertEquals(
            SipIdentity("Pascal Kankonde", "1001"),
            "\"Pascal Kankonde\" <sip:1001@pbx.example.com>".toSipIdentity(),
        )
    }

    @Test
    fun `unquoted display name is preserved`() {
        assertEquals(
            SipIdentity("Pascal Kankonde", "1001"),
            "Pascal Kankonde <sips:1001@pbx.example.com;transport=tls>".toSipIdentity(),
        )
    }

    @Test
    fun `plain SIP URI uses extension as display name`() {
        assertEquals(SipIdentity("1001", "1001"), "sip:1001@pbx.example.com".toSipIdentity())
    }
}
