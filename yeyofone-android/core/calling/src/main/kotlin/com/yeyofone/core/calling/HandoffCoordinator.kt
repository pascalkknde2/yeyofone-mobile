package com.yeyofone.core.calling

import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.TransferState
import com.yeyofone.core.voip.HandoffMessage
import com.yeyofone.core.voip.RegistrationManager
import com.yeyofone.core.voip.SipHandoffGateway
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A call on another device of the same account that can be continued on this one. */
data class HandoffOffer(
    val accountId: SipAccountId,
    /** The offering device's SIP Call-ID; also identifies the offer. */
    val callId: String,
    val localTag: String,
    val remoteTag: String,
    val remote: String,
    val remoteName: String?,
    val deviceName: String,
    val connectedAt: Instant?,
    val seenAt: Instant,
)

/**
 * Call handoff between devices of the same account, shared with the iOS and desktop apps.
 * Devices exchange small JSON messages through SIP MESSAGEs to their own address, which the PBX
 * delivers to every registered device: a device on a call announces it ("call", repeated every
 * 15 s, then "ended"), and a device that registers asks for current calls ("query"). Announced
 * calls become [offers]; [takeOver] moves one here with an INVITE carrying Replaces.
 */
class HandoffCoordinator(
    private val accounts: AccountRepository,
    private val registration: RegistrationManager,
    private val calls: CallCoordinator,
    private val gateway: SipHandoffGateway,
    private val scope: CoroutineScope,
    private val deviceName: String,
    private val clock: () -> Instant = Instant::now,
) {
    private val device = "android-${UUID.randomUUID()}"
    private val mutableOffers = MutableStateFlow<List<HandoffOffer>>(emptyList())
    val offers: StateFlow<List<HandoffOffer>> = mutableOffers.asStateFlow()
    private val mutex = Mutex()
    private var announced: Announced? = null
    private var queryPending = false
    private val dismissed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private data class Announced(val callId: CallId, val accountId: SipAccountId, val sipCallId: String, val sentAt: Instant)

    fun start() {
        scope.launch { gateway.handoffMessages.collect { receive(it) } }
        // Ask for calls to take over whenever an account (re)registers.
        scope.launch {
            accounts.observeAccounts().collectLatest { list ->
                coroutineScope {
                    list.forEach { account ->
                        launch {
                            registration.observe(account.id)
                                .map { it is RegistrationState.Registered }
                                .distinctUntilChanged()
                                .collect { registered ->
                                    if (registered) send(account.id, HandoffJson.encode(mapOf("v" to 1L, "type" to "query", "device" to device)))
                                }
                        }
                    }
                }
            }
        }
        scope.launch {
            while (true) {
                runCatching { tick() }
                delay(1_000)
            }
        }
    }

    suspend fun takeOver(offer: HandoffOffer): CallId {
        mutableOffers.update { current -> current.filterNot { it.callId == offer.callId } }
        return calls.takeOver(offer)
    }

    fun dismiss(offer: HandoffOffer) {
        dismissed += offer.callId
        mutableOffers.update { current -> current.filterNot { it.callId == offer.callId } }
    }

    private suspend fun receive(message: HandoffMessage) {
        val fields = HandoffJson.decode(message.body) ?: return
        if (fields["v"] != 1L) return
        val sender = fields["device"] as? String ?: return
        if (sender == device || sender.length > 64) return
        when (fields["type"]) {
            "query" -> mutex.withLock { queryPending = true }
            "ended" -> {
                val callId = fields["callId"] as? String ?: return
                mutableOffers.update { current -> current.filterNot { it.accountId == message.accountId && it.callId == callId } }
            }
            "call" -> {
                val offer = offerFrom(fields, message.accountId) ?: return
                if (offer.callId in dismissed) return
                mutableOffers.update { current ->
                    current.filterNot { it.callId == offer.callId } + offer
                }
            }
        }
    }

    private fun offerFrom(fields: Map<String, Any?>, accountId: SipAccountId): HandoffOffer? {
        val callId = fields["callId"] as? String ?: return null
        val localTag = fields["localTag"] as? String ?: return null
        val remoteTag = fields["remoteTag"] as? String ?: return null
        val remote = fields["remote"] as? String ?: return null
        if (!isToken(callId, 128, callId = true) || !isToken(localTag, 64) || !isToken(remoteTag, 64) || !isParty(remote)) {
            return null
        }
        return HandoffOffer(
            accountId = accountId,
            callId = callId,
            localTag = localTag,
            remoteTag = remoteTag,
            remote = remote,
            remoteName = label(fields["remoteName"] as? String, 64),
            deviceName = label(fields["name"] as? String, 32) ?: "another device",
            connectedAt = (fields["connectedAt"] as? Long)?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            seenAt = clock(),
        )
    }

    /** Drops stale offers and keeps this device's connected call announced. */
    private suspend fun tick() = mutex.withLock {
        val now = clock()
        mutableOffers.update { current -> current.filter { now.toEpochMilli() - it.seenAt.toEpochMilli() < OFFER_TTL_MS } }
        val connected = calls.sessions.value.filter { it.state == CallState.Connected }
        val candidate = connected.singleOrNull()?.takeIf { it.transfer !is TransferState.Pending }
        val current = announced
        if (current != null && current.callId != candidate?.id) {
            send(current.accountId, HandoffJson.encode(mapOf("v" to 1L, "type" to "ended", "device" to device, "callId" to current.sipCallId)))
            announced = null
        }
        if (candidate == null) {
            queryPending = false
            return@withLock
        }
        val due = announced == null || queryPending ||
            now.toEpochMilli() - announced!!.sentAt.toEpochMilli() >= ANNOUNCE_EVERY_MS
        if (!due) return@withLock
        val dialog = calls.dialog(candidate.id) ?: return@withLock
        val remote = dialog.remoteUser.takeIf { isParty(it) }
            ?: Regex("sips?:([^@;>\\s]+)@", RegexOption.IGNORE_CASE).find(candidate.remoteUri)?.groupValues?.get(1)?.takeIf { isParty(it) }
            ?: return@withLock
        val sent = send(
            candidate.accountId,
            HandoffJson.encode(
                mapOf(
                    "v" to 1L,
                    "type" to "call",
                    "device" to device,
                    "name" to deviceName,
                    "callId" to dialog.callId,
                    "localTag" to dialog.localTag,
                    "remoteTag" to dialog.remoteTag,
                    "remote" to remote,
                    "connectedAt" to (candidate.connectedAt?.toEpochMilli() ?: 0L),
                ),
            ),
        )
        if (sent) {
            queryPending = false
            announced = Announced(candidate.id, candidate.accountId, dialog.callId, now)
        }
    }

    private suspend fun send(accountId: SipAccountId, body: String): Boolean =
        runCatching { gateway.sendHandoff(accountId, body) }.isSuccess

    private companion object {
        const val OFFER_TTL_MS = 45_000L
        const val ANNOUNCE_EVERY_MS = 15_000L

        fun isToken(value: String, max: Int, callId: Boolean = false): Boolean =
            value.isNotEmpty() && value.length <= max && value.all {
                it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-.!%*_+`'~" || (callId && it in "@:/")
            }

        fun isParty(value: String): Boolean =
            value.isNotEmpty() && value.length <= 64 && value.all {
                it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+*#._-"
            }

        fun label(value: String?, max: Int): String? =
            value?.filterNot { it.isISOControl() }?.take(max)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Encodes and decodes the flat JSON objects of handoff messages (strings, integers, booleans, null). */
internal object HandoffJson {
    fun encode(fields: Map<String, Any?>): String = fields.entries.joinToString(",", "{", "}") { (key, value) ->
        "${quote(key)}:" + when (value) {
            null -> "null"
            is String -> quote(value)
            is Number, is Boolean -> value.toString()
            else -> quote(value.toString())
        }
    }

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    /** A flat object, or null when [text] isn't one. Nested values are rejected. */
    fun decode(text: String): Map<String, Any?>? = runCatching {
        var i = 0
        fun skip() { while (i < text.length && text[i].isWhitespace()) i++ }
        fun expect(c: Char) { skip(); require(i < text.length && text[i] == c); i++ }
        fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(i < text.length)
                when (val c = text[i++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        when (val e = text[i++]) {
                            'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                            'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                            'u' -> { out.append(text.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> out.append(e)
                        }
                    }
                    else -> out.append(c)
                }
            }
        }
        fun value(): Any? {
            skip()
            return when {
                text.startsWith("null", i) -> { i += 4; null }
                text.startsWith("true", i) -> { i += 4; true }
                text.startsWith("false", i) -> { i += 5; false }
                text[i] == '"' -> string()
                text[i] == '-' || text[i].isDigit() -> {
                    val start = i
                    i++
                    while (i < text.length && (text[i].isDigit() || text[i] in ".eE+-")) i++
                    val number = text.substring(start, i)
                    number.toLongOrNull() ?: number.toDouble()
                }
                else -> error("Unsupported value")
            }
        }
        val fields = linkedMapOf<String, Any?>()
        expect('{')
        skip()
        if (text[i] == '}') {
            i++
        } else {
            while (true) {
                val key = string()
                expect(':')
                fields[key] = value()
                skip()
                if (text[i] == ',') { i++; continue }
                expect('}')
                break
            }
        }
        skip()
        require(i == text.length)
        fields
    }.getOrNull()
}
