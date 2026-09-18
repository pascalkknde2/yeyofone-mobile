package com.yeyofone.app.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CallSummaryTest {
    @Test
    fun `duration is formatted with zero padding`() {
        assertEquals("00:00", formatCallDuration(0))
        assertEquals("04:23", formatCallDuration(263))
        assertEquals("61:01", formatCallDuration(3_661))
    }
}
