package com.yeyofone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * SEC-08 regression guard: no Android log call in the app or core modules may interpolate
 * credentials, push tokens, SIP identities/URIs, caller details or raw SIP messages.
 */
class LogRedactionTest {
    private val forbidden = Regex(
        """\$\{?[\w.]*?(token|password|secret|credential|username|uri|callerid|displayname|wholemsg|sdp|authorization)""",
        RegexOption.IGNORE_CASE,
    )
    private val logCall = Regex("""\bLog\.[vdiwe]\(""")

    @Test
    fun `log statements do not interpolate sensitive values`() {
        val sources = listOf(File("src/main/kotlin"), File("../core"))
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" && "/src/main/" in it.invariantPath() } }
        assertTrue(sources.size > 20, "source scan found too few files: ${sources.size}")

        val violations = sources.flatMap { file ->
            val text = file.readText()
            logCall.findAll(text).mapNotNull { match ->
                val call = callText(text, match.range.last)
                forbidden.find(call)?.let { "${file.invariantPath()}: ${it.value} in ${call.take(160)}" }
            }.toList()
        }
        if (violations.isNotEmpty()) fail("Sensitive values in logs:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `the guard catches a sensitive interpolation`() {
        val sample = "Log.i(TAG, \"registered \${account.username} with \${credential.token}\")"
        assertTrue(forbidden.containsMatchIn(callText(sample, sample.indexOf('('))))
    }

    /** Text of the call starting at [open] (its opening parenthesis); parens inside strings ignored. */
    private fun callText(text: String, open: Int): String {
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                !inString && c == '(' -> depth++
                !inString && c == ')' -> if (--depth == 0) return text.substring(open, i + 1)
            }
            i++
        }
        return text.substring(open)
    }

    private fun File.invariantPath() = path.replace('\\', '/')
}
