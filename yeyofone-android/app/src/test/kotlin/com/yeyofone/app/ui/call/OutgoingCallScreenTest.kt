package com.yeyofone.app.ui.call

import kotlin.test.Test
import kotlin.test.assertEquals

class OutgoingCallScreenTest {
    @Test
    fun `duration is zero padded`() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:46", formatDuration(106))
        assertEquals("61:01", formatDuration(3_661))
    }
}
