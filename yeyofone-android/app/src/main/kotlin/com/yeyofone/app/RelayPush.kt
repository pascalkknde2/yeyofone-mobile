package com.yeyofone.app

import org.json.JSONObject

/**
 * A validated push-relay v2 message (schema "1"). Pushes are hints only: a wake never creates a
 * call by itself (the SIP INVITE does), and nothing in a push authenticates a call.
 */
internal sealed interface RelayPush {
    val callId: String
    val eventId: String

    /** Epoch seconds after which the call's ringing deadline has passed. */
    val expiresAt: Double

    data class Wake(override val callId: String, override val eventId: String, override val expiresAt: Double) :
        RelayPush

    /** The relay call reached [state] (cancelled, answered or expired) and must not ring again. */
    data class Cancel(
        override val callId: String,
        override val eventId: String,
        val state: String,
        override val expiresAt: Double,
    ) : RelayPush

    companion object {
        private val identifier = Regex("^[A-Za-z0-9_.@+-]{1,128}$")
        private val terminalStates = setOf("cancelled", "answered", "expired")

        /** Returns null for anything that is not a well-formed schema-1 relay message. */
        fun parse(data: Map<String, String>): RelayPush? {
            if (data["schema"] != "1") return null
            val callId = data["call_id"]?.takeIf(identifier::matches) ?: return null
            val eventId = data["event_id"]?.takeIf(identifier::matches) ?: return null
            val expiresAt = data["expires_at"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
            val state = data["state"]
            return when (data["type"]) {
                "incoming_call_wake" -> if (state == "ringing") Wake(callId, eventId, expiresAt) else null
                "incoming_call_cancel" -> if (state in terminalStates) Cancel(callId, eventId, state!!, expiresAt) else null
                else -> null
            }
        }
    }
}

/**
 * Remembers recently handled relay events and terminal relay calls, so FCM duplicates are ignored
 * and a cancelled call can never be revived by a late or replayed wake, even when the cancel
 * arrives before the wake. Entries are kept for the call lifetime plus [RETENTION_SECONDS].
 */
internal class PushLedger(
    private val seenEvents: MutableMap<String, Double> = linkedMapOf(),
    private val terminalCalls: MutableMap<String, Double> = linkedMapOf(),
) {
    enum class Decision { WAKE, CANCEL, DUPLICATE, EXPIRED, CALL_ALREADY_TERMINAL }

    fun decide(push: RelayPush, now: Double): Decision {
        prune(now)
        if (push.eventId in seenEvents) return Decision.DUPLICATE
        val keepUntil = maxOf(push.expiresAt, now) + RETENTION_SECONDS
        remember(seenEvents, push.eventId, keepUntil)
        return when (push) {
            is RelayPush.Cancel -> {
                // Honoured even after expiry: a late cancel still prevents revival.
                remember(terminalCalls, push.callId, keepUntil)
                Decision.CANCEL
            }
            is RelayPush.Wake -> when {
                push.callId in terminalCalls -> Decision.CALL_ALREADY_TERMINAL
                push.expiresAt < now - CLOCK_SKEW_SECONDS -> Decision.EXPIRED
                push.expiresAt > now + MAX_FUTURE_SECONDS -> Decision.EXPIRED
                else -> Decision.WAKE
            }
        }
    }

    fun isTerminal(callId: String, now: Double): Boolean {
        prune(now)
        return callId in terminalCalls
    }

    fun toJson(): String = JSONObject()
        .put("events", JSONObject(seenEvents as Map<*, *>))
        .put("calls", JSONObject(terminalCalls as Map<*, *>))
        .toString()

    private fun remember(map: MutableMap<String, Double>, key: String, keepUntil: Double) {
        map.remove(key)
        map[key] = keepUntil
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
    }

    private fun prune(now: Double) {
        seenEvents.values.removeAll { it < now }
        terminalCalls.values.removeAll { it < now }
    }

    companion object {
        const val RETENTION_SECONDS = 600.0
        const val CLOCK_SKEW_SECONDS = 5.0

        /** The relay caps ringing deadlines at MAX_CALL_SECONDS (120 s); allow generous skew. */
        const val MAX_FUTURE_SECONDS = 600.0
        const val MAX_ENTRIES = 256

        fun fromJson(json: String?): PushLedger = runCatching {
            val root = JSONObject(json ?: return PushLedger())
            PushLedger(root.optJSONObject("events").toDoubleMap(), root.optJSONObject("calls").toDoubleMap())
        }.getOrElse { PushLedger() }

        private fun JSONObject?.toDoubleMap(): MutableMap<String, Double> {
            val result = linkedMapOf<String, Double>()
            if (this == null) return result
            keys().forEach { key -> optDouble(key).takeIf { !it.isNaN() }?.let { result[key] = it } }
            return result
        }
    }
}
