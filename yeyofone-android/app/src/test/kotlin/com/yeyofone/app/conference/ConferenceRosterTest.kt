package com.yeyofone.app.conference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConferenceRosterTest {
    @Test
    fun `people who left travel with the roster`() {
        val roster = ConferenceRoster(
            host = "1001",
            members = listOf(RosterMember("1001", "Host", false), RosterMember("1002", "Bea", false)),
            left = listOf(RosterMember("1005", "Cal", false)),
        )
        val (parsed, ended) = ConferenceRoster.parse(roster.toJson())!!
        assertEquals(false, ended)
        assertEquals(listOf("1005"), parsed.left.map { it.ext })
        assertEquals(listOf("1001", "1002"), parsed.members.map { it.ext })
    }

    @Test
    fun `a roster from an older build has nobody who left`() {
        val (parsed, _) = ConferenceRoster.parse("""{"v":1,"host":"1001","members":[{"ext":"1002","name":"Bea"}]}""")!!
        assertTrue(parsed.left.isEmpty())
    }

    @Test
    fun `someone back on the call is not also listed as left`() {
        val body = """{"v":1,"host":"1001","members":[{"ext":"1005","name":"Cal"}],"left":[{"ext":"1005","name":"Cal"}]}"""
        assertTrue(ConferenceRoster.parse(body)!!.first.left.isEmpty())
    }
}
