package com.yeyofone.app.ai

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class NotesSummarizerTest {
    @Test
    fun `an empty transcript gives empty notes`() {
        val notes = NotesSummarizer.summarize(emptyList())
        assertTrue(notes.isEmpty)
        assertEquals("", notes.summary)
    }

    @Test
    fun `a short call keeps every sentence in the summary`() {
        val notes = NotesSummarizer.summarize(listOf("the invoice is overdue", "i will send the invoice again"))
        assertTrue(notes.summary.contains("invoice is overdue"))
        assertTrue(notes.summary.contains("send the invoice again"))
    }

    @Test
    fun `commitments and requests become follow-ups`() {
        val notes = NotesSummarizer.summarize(
            listOf(
                "the delivery slipped to next week",
                "i will email you the new schedule tomorrow",
                "can you confirm the address please",
                "that is all for today",
            ),
        )
        assertEquals(2, notes.actionItems.size)
        assertTrue(notes.actionItems.first().startsWith("I will email you"))
    }

    @Test
    fun `a long call is cut down to three sentences`() {
        val segments = (1..8).map { "sentence number $it is about the quarterly budget review" }
        val notes = NotesSummarizer.summarize(segments)
        assertEquals(3, notes.summary.split(". ").size)
    }

    @Test
    fun `notes survive a json round trip`() {
        val notes = CallNotes("Summary.", listOf("Do it."), "Full text.")
        assertEquals(notes, CallNotes.fromJson(notes.toJson()))
    }
}
