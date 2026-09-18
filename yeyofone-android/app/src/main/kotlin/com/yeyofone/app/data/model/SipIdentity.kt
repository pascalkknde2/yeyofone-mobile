package com.yeyofone.app.data.model

data class SipIdentity(val displayName: String, val extension: String)

fun String.toSipIdentity(): SipIdentity {
    val raw = trim()
    val address = raw.substringAfter('<', raw).substringBefore('>').trim()
    val extension = SIP_USER.find(address)?.groupValues?.get(1)
        ?: address.removePrefix("tel:").substringBefore('@').substringBefore(';').trim().trim('"', '\'', ' ')
    val suppliedName = if ('<' in raw) raw.substringBefore('<').sanitizeDisplayName() else ""
    return SipIdentity(
        displayName = suppliedName.ifBlank { extension }.ifBlank { raw.sanitizeDisplayName() },
        extension = extension.ifBlank { raw.sanitizeDisplayName() },
    )
}

private fun String.sanitizeDisplayName(): String =
    trim().trim('"', '\'', ' ').replace(Regex("\\s+"), " ")

private val SIP_USER = Regex("(?i)^sips?:([^@;>]+)")
