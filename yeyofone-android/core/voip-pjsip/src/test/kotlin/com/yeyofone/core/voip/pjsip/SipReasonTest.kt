package com.yeyofone.core.voip.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals

class SipReasonTest {
    @Test
    fun `logs numeric causes without peer supplied text`() {
        assertEquals(
            "SIP:408,Q.850:102",
            sipReasonCauses("BYE sip:private SIP/2.0\r\nReason: SIP;cause=408;text=\"private caller\", Q.850;cause=102\r\n\r\n"),
        )
    }

    @Test
    fun `ignores body and malformed or unknown causes`() {
        assertEquals("", sipReasonCauses("BYE sip:a SIP/2.0\nReason: other;cause=408\nReason: SIP;cause=4089\n\nReason: SIP;cause=408"))
        assertEquals("", sipReasonCauses("BYE sip:a SIP/2.0\n\n"))
    }
}
