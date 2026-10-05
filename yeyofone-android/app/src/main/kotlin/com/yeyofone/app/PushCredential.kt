package com.yeyofone.app

import org.json.JSONException
import org.json.JSONObject

/**
 * A push-relay device credential issued by an operator for one tenant/account/device (relay
 * `issue-credential --role device`). The bearer [token] is scoped and expiring on the relay side;
 * it is never logged or shown, and is stored only through the Keystore-backed credential store.
 */
internal class PushCredential private constructor(
    val id: String,
    val token: String,
    val tenant: String,
    val account: String,
    val device: String,
) {
    override fun toString(): String = "PushCredential(id=$id, tenant=$tenant, account=$account, device=$device)"

    companion object {
        // Relay identifier rules (relay/validation.py): 1-128 of A-Z a-z 0-9 _ . @ + -
        private val identifier = Regex("^[A-Za-z0-9_.@+-]{1,128}$")
        private const val MIN_TOKEN_LENGTH = 16

        /** Parses the relay CLI's credential JSON and checks it belongs to [sipUsername]. */
        fun parse(json: String, sipUsername: String): PushCredential {
            val obj = try {
                JSONObject(json.trim())
            } catch (_: JSONException) {
                throw InvalidPushCredentialException("is not valid credential JSON")
            }
            if (obj.optString("role") != "device") throw InvalidPushCredentialException("is not a device credential")
            val token = obj.optString("token")
            if (token.length < MIN_TOKEN_LENGTH || token.any(Char::isWhitespace)) {
                throw InvalidPushCredentialException("has no usable token")
            }
            val credential = PushCredential(
                id = obj.requireIdentifier("id"),
                token = token,
                tenant = obj.requireIdentifier("tenant"),
                account = obj.requireIdentifier("account"),
                device = obj.requireIdentifier("device"),
            )
            if (credential.account != sipUsername) {
                throw InvalidPushCredentialException("was issued for a different account")
            }
            return credential
        }

        private fun JSONObject.requireIdentifier(field: String): String {
            val value = optString(field)
            if (!identifier.matches(value)) throw InvalidPushCredentialException("has an invalid $field")
            return value
        }
    }
}

internal class InvalidPushCredentialException(reason: String) : IllegalArgumentException("Push credential $reason")
