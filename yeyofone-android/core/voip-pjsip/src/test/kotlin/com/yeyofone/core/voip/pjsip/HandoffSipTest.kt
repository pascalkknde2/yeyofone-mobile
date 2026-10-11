package com.yeyofone.core.voip.pjsip

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class HandoffSipTest {
    @Test
    fun responseCarriesTheLocalTagInFrom() {
        val ok = "SIP/2.0 200 OK\r\n" +
            "From: <sip:1005@sysinfos.co.uk>;tag=EEFAB386-1E15\r\n" +
            "To: <sip:1002@sysinfos.co.uk>;tag=XFHHFQy1yggtp\r\n" +
            "Call-ID: 5B2143FB-D55B-46AF\r\n\r\n"
        val dialog = dialogFromReceived(ok)!!
        assertEquals("5B2143FB-D55B-46AF", dialog.callId)
        assertEquals("EEFAB386-1E15", dialog.localTag)
        assertEquals("XFHHFQy1yggtp", dialog.remoteTag)
        assertEquals("1002", dialog.remoteUser)
    }

    @Test
    fun requestCarriesTheLocalTagInTo() {
        val ack = "ACK sip:1005@10.0.0.2:5060 SIP/2.0\n" +
            "f: \"Pascal\" <sip:1001@sysinfos.co.uk>;tag=remote1\n" +
            "t: <sip:1005@sysinfos.co.uk>;tag=local1\n" +
            "i: abc@host\n\n"
        val dialog = dialogFromReceived(ack)!!
        assertEquals("abc@host", dialog.callId)
        assertEquals("local1", dialog.localTag)
        assertEquals("remote1", dialog.remoteTag)
        assertEquals("1001", dialog.remoteUser)
    }

    @Test
    fun earlyResponsesWithoutBothTagsAreIgnored() {
        val trying = "SIP/2.0 100 Trying\r\nFrom: <sip:1005@x>;tag=a\r\nTo: <sip:1002@x>\r\nCall-ID: c\r\n\r\n"
        assertNull(dialogFromReceived(trying))
        val injected = "SIP/2.0 200 OK\r\nFrom: <sip:1005@x>;tag=a\r\nTo: <sip:1002@x>;tag=b\r\nCall-ID: c;evil=1\r\n\r\n"
        assertNull(dialogFromReceived(injected))
    }

    @Test
    fun detectsCallCompletedElsewhere() {
        val bye = "BYE sip:1005@1.2.3.4 SIP/2.0\r\nReason: SIP;cause=200;text=\"Call completed elsewhere\"\r\n\r\n"
        assertTrue(completedElsewhere(bye))
        assertFalse(completedElsewhere("BYE sip:1005@1.2.3.4 SIP/2.0\r\nReason: Q.850;cause=16\r\n\r\n"))
        assertFalse(completedElsewhere("SIP/2.0 200 OK\r\nReason: SIP;cause=200\r\n\r\n"))
        assertTrue(completedElsewhere("CANCEL sip:1005@1.2.3.4 SIP/2.0\r\nReason: SIP;cause=200;text=\"Call completed elsewhere\"\r\n\r\n"))
    }
}
