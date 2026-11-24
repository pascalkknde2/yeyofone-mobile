package com.yeyofone.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What the assistant produced for one call: a short summary, follow-ups and the full transcript. */
data class CallNotes(
    val summary: String,
    val actionItems: List<String>,
    val transcript: String,
) {
    val isEmpty: Boolean get() = transcript.isBlank()

    fun toJson(): String = JSONObject()
        .put("summary", summary)
        .put("actionItems", JSONArray(actionItems))
        .put("transcript", transcript)
        .toString()

    companion object {
        fun fromJson(json: String): CallNotes? = runCatching {
            val obj = JSONObject(json)
            val items = obj.optJSONArray("actionItems")
            CallNotes(
                summary = obj.optString("summary"),
                actionItems = (0 until (items?.length() ?: 0)).map { items!!.getString(it) },
                transcript = obj.optString("transcript"),
            )
        }.getOrNull()

        /** The notes file saved next to a recording. */
        fun fileFor(recording: File): File = File(recording.parentFile, recording.name + ".notes.json")
    }
}

/** Where the assistant is for a call that has ended. */
sealed interface NotesState {
    /** Downloading the offline speech model (first use only); [percent] is 0-100 or null when unknown. */
    data class Preparing(val percent: Int?) : NotesState
    data object Transcribing : NotesState
    data class Ready(val notes: CallNotes) : NotesState
    data class Failed(val reason: String) : NotesState
}

/**
 * Builds notes from a transcript without any server: it keeps the sentences that carry the most
 * frequent content words as the summary, and picks out sentences that sound like a commitment or
 * request as follow-ups. Plain heuristics, so the notes are a starting point, not a minute-taker.
 */
object NotesSummarizer {
    private val stopWords = setOf(
        "the", "and", "that", "this", "with", "have", "for", "you", "your", "are", "was", "were", "but", "not",
        "what", "when", "where", "which", "who", "how", "can", "could", "would", "should", "will", "just", "like",
        "yeah", "okay", "well", "got", "get", "going", "gonna", "from", "they", "them", "then", "there", "here",
        "about", "because", "been", "had", "has", "his", "her", "its", "our", "out", "all", "any", "some", "very",
        "really", "also", "too", "yes", "know", "think", "mean", "right", "sure", "thanks", "thank", "hello", "okay",
    )
    private val followUp = Regex(
        "\\b(i'?ll|i will|we'?ll|we will|let me|i'?m going to|i need to|we need to|you need to|can you|could you|" +
            "would you|please|remind me|don'?t forget|follow up|get back to|call (you )?back|send (you|me|it|them)|" +
            "email (you|me|it)|by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|tonight|noon|" +
            "the end of))\\b",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_SUMMARY_SENTENCES = 3
    private const val MAX_ACTION_ITEMS = 5

    fun summarize(segments: List<String>): CallNotes {
        val sentences = segments.flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
            .map { it.trim().replaceFirstChar(Char::uppercase) }
            .filter { it.split(Regex("\\s+")).size >= 3 }
        if (sentences.isEmpty()) return CallNotes("", emptyList(), segments.joinToString(" ").trim())

        val frequency = HashMap<String, Int>()
        sentences.forEach { s -> contentWords(s).forEach { frequency.merge(it, 1, Int::plus) } }
        val scored = sentences.mapIndexed { index, sentence ->
            val words = contentWords(sentence)
            val score = if (words.isEmpty()) 0.0 else words.sumOf { frequency.getValue(it) }.toDouble() / Math.pow(words.size.toDouble(), 0.6)
            Triple(index, sentence, score)
        }
        val keep = if (sentences.size <= MAX_SUMMARY_SENTENCES) sentences.size else MAX_SUMMARY_SENTENCES
        val summary = scored.sortedByDescending { it.third }.take(keep).sortedBy { it.first }
            .joinToString(" ") { ensureEnd(it.second) }
        val actions = sentences.filter { followUp.containsMatchIn(it) }.take(MAX_ACTION_ITEMS).map(::ensureEnd)
        return CallNotes(summary, actions, sentences.joinToString(" ") { ensureEnd(it) })
    }

    private fun contentWords(sentence: String): List<String> =
        Regex("[a-z']{3,}").findAll(sentence.lowercase()).map { it.value }.filter { it !in stopWords }.toList()

    private fun ensureEnd(sentence: String) = if (sentence.last() in ".!?") sentence else "$sentence."
}
