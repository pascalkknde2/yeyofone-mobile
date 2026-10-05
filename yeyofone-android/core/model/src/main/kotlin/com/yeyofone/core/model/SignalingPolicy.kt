package com.yeyofone.core.model

/** A server configuration that would send signaling over a different transport than chosen. */
data class SignalingPolicyViolation(val field: String, val reason: String, val bypassesTls: Boolean)

/** Thrown by native layers that refuse to register a configuration violating signaling policy. */
class SignalingPolicyException(val violation: SignalingPolicyViolation) :
    IllegalStateException("Signaling policy violation: ${violation.field} ${violation.reason}")

/**
 * Finds configurations that would silently downgrade or redirect SIP signaling: secure mode
 * without TLS, a `sips:` URI on a non-TLS account, or a URI `transport` parameter that differs
 * from the account transport (PJSIP honours the URI parameter over the account setting).
 */
fun SipServerConfiguration.signalingPolicyViolation(): SignalingPolicyViolation? {
    val tls = transport == TransportProtocol.TLS
    if (securityMode == SecurityMode.REQUIRE_SECURE && !tls) {
        return SignalingPolicyViolation("transport", "must be TLS when secure signaling is required", true)
    }
    return sipUriTransportViolation("registrarUri", registrarUri, transport)
        ?: outboundProxyUri?.takeIf(String::isNotBlank)?.let {
            sipUriTransportViolation("outboundProxyUri", it, transport)
        }
}

/** Checks one SIP URI against the account transport; see [signalingPolicyViolation]. */
fun sipUriTransportViolation(field: String, uri: String, transport: TransportProtocol): SignalingPolicyViolation? {
    val tls = transport == TransportProtocol.TLS
    val trimmed = uri.trim()
    if (trimmed.startsWith("sips:", ignoreCase = true) && !tls) {
        return SignalingPolicyViolation(field, "uses sips:, which requires TLS transport", false)
    }
    val parameter = trimmed.substringBefore('?').substringAfterLast('@').split(';').drop(1)
        .map { it.trim() }
        .firstOrNull { it.startsWith("transport=", ignoreCase = true) }
        ?.substringAfter('=')?.lowercase()
        ?: return null
    if (parameter == transport.name.lowercase()) return null
    return SignalingPolicyViolation(field, "sets transport=$parameter, which conflicts with ${transport.name}", tls)
}

/**
 * SRTP is supported only with SDES keying over TLS signaling. SDES carries the media keys in the
 * SDP, so over UDP/TCP anyone on the path could read them. The encryption is hop-by-hop between
 * the device and the PBX (which can decrypt the media); it is not end-to-end.
 */
fun srtpPolicyViolation(transport: TransportProtocol, nat: NatConfiguration): SignalingPolicyViolation? =
    if (nat.srtpEnabled && transport != TransportProtocol.TLS) {
        SignalingPolicyViolation("srtpEnabled", "requires TLS transport, because SRTP keys are sent in the SIP message", false)
    } else {
        null
    }
