package com.yeyofone.app.conference

import org.json.JSONArray
import org.json.JSONObject

/** One person on a conference as the organiser shares them with everyone else on it. */
data class RosterMember(val ext: String, val name: String, val calling: Boolean)

/**
 * Who is on a conference, sent by the organiser to each person on it so everyone sees the same
 * conference screen. [host] is the organiser's extension and is also one of [members].
 */
data class ConferenceRoster(val host: String, val members: List<RosterMember>) {
    fun toJson(ended: Boolean = false): String = JSONObject()
        .put("v", 1)
        .put("host", host)
        .put("ended", ended)
        .put(
            "members",
            JSONArray().also { array ->
                members.forEach {
                    array.put(JSONObject().put("ext", it.ext).put("name", it.name).put("calling", it.calling))
                }
            },
        )
        .toString()

    companion object {
        private const val MAX_MEMBERS = 8
        private val EXTENSION = Regex("[A-Za-z0-9._+-]{1,32}")

        /** The roster and whether the conference has ended, or null if [body] isn't a valid roster. */
        fun parse(body: String): Pair<ConferenceRoster, Boolean>? = runCatching {
            val json = JSONObject(body)
            if (json.optInt("v") != 1) return null
            val host = json.getString("host").takeIf { EXTENSION.matches(it) } ?: return null
            val ended = json.optBoolean("ended", false)
            val array = json.optJSONArray("members") ?: JSONArray()
            val members = (0 until minOf(array.length(), MAX_MEMBERS)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val ext = item.optString("ext").takeIf { EXTENSION.matches(it) } ?: return@mapNotNull null
                val name = item.optString("name").filter { !it.isISOControl() }.trim().take(40).ifBlank { ext }
                RosterMember(ext, name, item.optBoolean("calling", false))
            }
            ConferenceRoster(host, members) to ended
        }.getOrNull()
    }
}
