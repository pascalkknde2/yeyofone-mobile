package com.yeyofone.app.ui.incomingcall

import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingCallScreenTest {
    @Test
    fun `caller name is extracted from SIP URI`() {
        assertEquals("sarah.jenkins", callerName("sip:sarah.jenkins@pbx.example.com"))
        assertEquals("1001", callerName("sips:1001@pbx.example.com;transport=tls"))
    }
}
