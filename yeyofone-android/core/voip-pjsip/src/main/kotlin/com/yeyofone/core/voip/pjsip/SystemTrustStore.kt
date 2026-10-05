package com.yeyofone.core.voip.pjsip

import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Exports the platform's system CA certificates as a PEM bundle for OpenSSL, which does not read
 * the Android trust store itself. User-installed CAs are excluded, matching the platform default
 * for apps targeting API 24+.
 */
internal object SystemTrustStore {
    private const val SYSTEM_ALIAS_PREFIX = "system:"

    fun pemBundle(): String {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
        return buildString {
            for (alias in store.aliases()) {
                if (!alias.startsWith(SYSTEM_ALIAS_PREFIX)) continue
                val certificate = store.getCertificate(alias) as? X509Certificate ?: continue
                append("-----BEGIN CERTIFICATE-----\n")
                append(encoder.encodeToString(certificate.encoded))
                append("\n-----END CERTIFICATE-----\n")
            }
        }
    }
}
