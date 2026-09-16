package com.yeyofone.core.account

import java.net.IDN
import java.net.URI

object AccountValidator {
    private val userPattern = Regex("^[^\\s<>@;:,]+$")
    private val phonePattern = Regex("^[+*#0-9A-Da-d]{1,32}$")

    fun validate(draft: AccountDraft) {
        requireText("displayName", draft.displayName, 1, 80)
        validateUser("username", draft.username)
        validateUser("authenticationUsername", draft.authenticationUsername)
        validateHost("domain", draft.domain)
        validateSipUri("registrarUri", draft.registrarUri)
        draft.outboundProxyUri?.takeIf(String::isNotBlank)?.let {
            validateSipUri("outboundProxyUri", it)
        }
        if (draft.port !in 1..65535) invalid("port", "must be between 1 and 65535")
        if (draft.registrationExpirySeconds !in 60..86_400) {
            invalid("registrationExpirySeconds", "must be between 60 and 86400")
        }
        draft.nat.stunServer?.takeIf(String::isNotBlank)?.let { validateHostPort("stunServer", it) }
        draft.nat.turnServer?.takeIf(String::isNotBlank)?.let { validateHostPort("turnServer", it) }
        if (!draft.nat.turnServer.isNullOrBlank() && draft.nat.turnUsername.isNullOrBlank()) {
            invalid("turnUsername", "is required when a TURN server is configured")
        }
        if (draft.nat.iceEnabled && draft.nat.stunServer.isNullOrBlank()) {
            // Without a resolved STUN server, ICE can only offer host candidates - unreachable
            // from outside the device's own NAT - which silently breaks any call the device
            // itself originates while leaving inbound calls looking fine.
            invalid("stunServer", "is required when ICE is enabled")
        }
        draft.voicemailNumber?.takeIf(String::isNotBlank)?.let {
            if (!phonePattern.matches(it)) invalid("voicemailNumber", "contains unsupported characters")
        }
        draft.callerId?.takeIf(String::isNotBlank)?.let { requireText("callerId", it, 1, 80) }
        if (draft.id == null && draft.password.isNullOrEmpty()) {
            invalid("password", "is required for a new account")
        }
    }

    private fun validateUser(field: String, value: String) {
        requireText(field, value, 1, 128)
        if (!userPattern.matches(value)) invalid(field, "contains invalid SIP username characters")
    }

    private fun validateSipUri(field: String, value: String) {
        val uri = runCatching { URI(value.trim()) }.getOrElse { invalid(field, "is not a valid URI") }
        if (uri.scheme?.lowercase() !in setOf("sip", "sips")) invalid(field, "must use sip: or sips:")
        val authority = uri.rawSchemeSpecificPart.substringBefore(';').substringAfterLast('@')
        val host = authority.substringBeforeLast(':', authority).removePrefix("[").removeSuffix("]")
        validateHost(field, host)
    }

    private fun validateHostPort(field: String, value: String) {
        val trimmed = value.trim()
        val host = if (trimmed.startsWith('[')) trimmed.substringAfter('[').substringBefore(']')
        else trimmed.substringBeforeLast(':', trimmed)
        validateHost(field, host)
        val portText = if (trimmed.startsWith('[')) trimmed.substringAfter("]:", "")
        else if (trimmed.count { it == ':' } == 1) trimmed.substringAfterLast(':') else ""
        if (portText.isNotEmpty() && (portText.toIntOrNull() !in 1..65535)) invalid(field, "has an invalid port")
    }

    private fun validateHost(field: String, value: String) {
        val host = value.trim().removeSuffix(".")
        if (host.isEmpty() || host.length > 253 || host.any(Char::isWhitespace)) invalid(field, "is not a valid host")
        val ascii = runCatching { IDN.toASCII(host) }.getOrElse { invalid(field, "is not a valid host") }
        if (ascii.split('.').any { label ->
                label.isEmpty() || label.length > 63 || label.startsWith('-') || label.endsWith('-') ||
                    label.any { !it.isLetterOrDigit() && it != '-' }
            }
        ) invalid(field, "is not a valid host")
    }

    private fun requireText(field: String, value: String, min: Int, max: Int) {
        if (value.trim().length !in min..max) invalid(field, "must contain $min to $max characters")
    }

    private fun invalid(field: String, reason: String): Nothing =
        throw AccountValidationException.InvalidField(field, reason)
}

private fun CharArray?.isNullOrEmpty(): Boolean = this == null || isEmpty()
