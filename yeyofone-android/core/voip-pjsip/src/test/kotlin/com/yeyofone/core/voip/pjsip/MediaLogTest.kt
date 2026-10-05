package com.yeyofone.core.voip.pjsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaLogTest {
    private val addresses = MediaAddresses("10.0.0.5:4000", "203.0.113.9:5004", "203.0.113.9:5004")

    @Test
    fun `release media log has no network identifiers`() {
        val line = mediaTransportLogLine("call-1", "PCMA/8000", srtp = true, addresses = null)
        assertEquals("call=call-1 codec=PCMA/8000 srtp=true", line)
        assertFalse(Regex("""\d+\.\d+\.\d+\.\d+""").containsMatchIn(line))
    }

    @Test
    fun `verbose diagnostics add RTP addresses`() {
        val line = mediaTransportLogLine("call-1", "PCMA/8000", srtp = false, addresses = addresses)
        assertTrue("remoteRtp=203.0.113.9:5004" in line)
        assertTrue("localRtp=10.0.0.5:4000" in line)
    }

    @Test
    fun `PJSIP logs default to errors only`() {
        assertEquals(1, PjsipEngineConfiguration().logLevel)
        assertFalse(PjsipEngineConfiguration().verboseDiagnostics)
    }
}
