package com.yeyofone.app.ui.incomingcall

import com.yeyofone.app.data.model.SipIdentity
import com.yeyofone.app.data.model.toSipIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingCallScreenTest {
    @Test
    fun `caller identity is extracted from plain SIP URI`() {
        assertEquals(
            SipIdentity("sarah.jenkins", "sarah.jenkins"),
            "sip:sarah.jenkins@pbx.example.com".toSipIdentity(),
        )
    }

    @Test
    fun `quoted SIP display name is sanitized and extension retained`() {
        assertEquals(
            SipIdentity("Pascal Kankonde", "1001"),
            "\"Pascal Kankonde\" <sip:1001@pbx.example.com>".toSipIdentity(),
        )
    }

    @Test
    fun `secure SIP URI parameters are excluded from extension`() {
        assertEquals(
            SipIdentity("1002", "1002"),
            "sips:1002@pbx.example.com;transport=tls".toSipIdentity(),
        )
    }
}
