package com.yeyofone.core.voip.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SipWireSummaryTest {
    @Test
    fun `reports direction without exposing signaling identities or authorization`() {
        val packet = "TX 300 bytes Request msg BYE/cseq=42 to UDP private:5060:\n" +
            "BYE sip:private SIP/2.0\r\nCSeq: 42 BYE\r\nAuthorization: secret\r\n\r\nprivate body"
        assertEquals("TX method=BYE cseq=42 code=request", sipWireSummary(packet))
    }

    @Test
    fun `reports received response and ignores unrelated logs and body headers`() {
        assertEquals("RX method=INVITE cseq=7 code=200", sipWireSummary("RX 123 bytes response:\nSIP/2.0 200 OK\nCSeq: 7 INVITE\n\n"))
        assertNull(sipWireSummary("RX 123 bytes response:\nSIP/2.0 200 OK\n\nCSeq: 7 INVITE"))
        assertNull(sipWireSummary("some other log"))
    }
}

class SipWireTraceTest {
    @Test
    fun `keeps the packet verbatim but redacts digest credentials`() {
        val packet = "TX 300 bytes Request msg REGISTER/cseq=1 to UDP host:5060:\n" +
            "REGISTER sip:host SIP/2.0\r\nCSeq: 1 REGISTER\r\nAuthorization: Digest response=\"deadbeef\"\r\n"
        val trace = assertNotNull(sipWireTrace(packet))
        assertTrue("REGISTER sip:host SIP/2.0" in trace)
        assertTrue("CSeq: 1 REGISTER" in trace)
        assertTrue("Authorization: <redacted>" in trace)
        assertFalse("deadbeef" in trace)
    }

    @Test
    fun `ignores log lines that are not packet dumps`() {
        assertNull(sipWireTrace("pjsua_core.c  .Account registration success"))
    }
}
