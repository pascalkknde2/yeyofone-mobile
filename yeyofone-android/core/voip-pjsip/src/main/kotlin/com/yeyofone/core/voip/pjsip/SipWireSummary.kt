package com.yeyofone.core.voip.pjsip

/** Summarize PJSIP packet logs without logging SIP bodies, credentials, or peer identities. */
internal fun sipWireSummary(message: String): String? {
    val direction = Regex("\\b(RX|TX) \\d+ bytes ").find(message)?.groupValues?.get(1) ?: return null
    val headers = message.replace("\r\n", "\n").substringBefore("\n\n")
    val cseq = Regex("(?im)^CSeq:\\s*(\\d+)\\s+([A-Z]+)\\s*$").find(headers) ?: return null
    val method = cseq.groupValues[2]
    if (method !in setOf("INVITE", "ACK", "BYE", "CANCEL", "UPDATE", "REGISTER", "OPTIONS")) return null
    val response = Regex("(?m)^SIP/2\\.0 (\\d{3})\\b").find(headers)?.groupValues?.get(1)
    return "$direction method=$method cseq=${cseq.groupValues[1]} code=${response ?: "request"}"
}

/**
 * Full packet trace for live interop debugging, off unless someone explicitly enables it
 * (`adb shell setprop log.tag.YeyoFoneSipTrace DEBUG`) on a verbose-diagnostics build. Digest
 * credentials and challenges are redacted; the rest is verbatim so a trace can be read against the
 * PBX's own logs. Returns null for log lines that are not packet dumps.
 */
internal fun sipWireTrace(message: String): String? {
    if (Regex("\\b(RX|TX) \\d+ bytes ").find(message) == null) return null
    return message.replace("\r\n", "\n").lineSequence().joinToString("\n") { line ->
        val name = line.substringBefore(':', "").trim()
        if (name.lowercase() in REDACTED_HEADERS) "$name: <redacted>" else line
    }
}

private val REDACTED_HEADERS =
    setOf("authorization", "proxy-authorization", "www-authenticate", "proxy-authenticate")
