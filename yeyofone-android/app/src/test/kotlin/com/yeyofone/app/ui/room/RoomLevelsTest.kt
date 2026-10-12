package com.yeyofone.app.ui.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomLevelsTest {
    @Test
    fun `silence has no level`() {
        assertEquals(0f, RoomLevels.micLevel(ShortArray(256)))
        assertEquals(0f, RoomLevels.micLevel(ShortArray(0)))
    }

    @Test
    fun `level is the root mean square of the samples read`() {
        val samples = shortArrayOf(16384, -16384, 16384, -16384, 0, 0)
        assertEquals(0.5f, RoomLevels.micLevel(samples, count = 4), 0.0001f)
    }

    @Test
    fun `speaking badge turns on above 0_04 and off below 0_02`() {
        assertFalse(RoomLevels.nextTalking(0.03f, wasTalking = false))
        assertTrue(RoomLevels.nextTalking(0.05f, wasTalking = false))
        assertTrue(RoomLevels.nextTalking(0.03f, wasTalking = true))
        assertFalse(RoomLevels.nextTalking(0.01f, wasTalking = true))
    }
}
