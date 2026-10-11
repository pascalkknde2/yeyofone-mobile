package com.yeyofone.core.calling

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class HandoffJsonTest {
    @Test
    fun roundTripsFlatMessages() {
        val text = HandoffJson.encode(
            mapOf("v" to 1L, "type" to "call", "name" to "Pixel \"7\"\n", "connectedAt" to 1760000000000L, "remoteName" to null),
        )
        val fields = HandoffJson.decode(text)!!
        assertEquals(1L, fields["v"])
        assertEquals("call", fields["type"])
        assertEquals("Pixel \"7\"\n", fields["name"])
        assertEquals(1760000000000L, fields["connectedAt"])
        assertNull(fields["remoteName"])
    }

    @Test
    fun readsMessagesFromTheOtherApps() {
        // iOS (JSONEncoder) and desktop (serde_json) output.
        val fields = HandoffJson.decode("""{"v":1,"type":"query","device":"ios-1F2"}""")!!
        assertEquals("query", fields["type"])
        assertEquals("ios-1F2", fields["device"])
        assertEquals("a/b", HandoffJson.decode("""{ "callId" : "a\/b" }""")!!["callId"])
    }

    @Test
    fun rejectsAnythingButAFlatObject() {
        assertNull(HandoffJson.decode("""{"v":{"nested":1}}"""))
        assertNull(HandoffJson.decode("""["call"]"""))
        assertNull(HandoffJson.decode("""{"v":1} trailing"""))
        assertNull(HandoffJson.decode(""))
    }
}
