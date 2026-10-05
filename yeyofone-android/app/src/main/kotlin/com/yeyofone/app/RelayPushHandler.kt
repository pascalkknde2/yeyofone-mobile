package com.yeyofone.app

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.voip.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Applies relay pushes against the app's actual SIP state. Wakes only (re)start the calling
 * service; SIP signaling stays authoritative and a push never creates or answers a call. A cancel
 * rejects only an unanswered incoming SIP call carrying the same relay call ID, and only if SIP has
 * not ended it by itself within [CANCEL_GRACE_MS]. An INVITE for a relay call already known to be
 * terminal is rejected, so a cancelled call cannot ring again.
 */
internal class RelayPushHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callManager: CallManager,
    private val now: () -> Double = { System.currentTimeMillis() / 1000.0 },
) {
    private val lock = Any()

    /** Called from FirebaseMessagingService.onMessageReceived; returns quickly. */
    fun onMessage(data: Map<String, String>) {
        val push = RelayPush.parse(data)
        if (push == null) {
            Log.w(TAG, "Ignoring push that is not a valid relay message")
            return
        }
        val decision = synchronized(lock) {
            val ledger = loadLedger()
            ledger.decide(push, now()).also { saveLedger(ledger) }
        }
        Log.i(TAG, "relay push type=${push.javaClass.simpleName} call=${push.callId} decision=$decision")
        when (decision) {
            PushLedger.Decision.WAKE -> IncomingCallService.start(context)
            PushLedger.Decision.CANCEL -> scope.launch { reconcileCancel(push.callId) }
            else -> Unit
        }
    }

    /** Watches new incoming SIP calls; rejects any whose relay call is already terminal. */
    fun start() {
        scope.launch {
            val checked = mutableSetOf<String>()
            callManager.sessions.collect { current ->
                current.forEach { session ->
                    val relayCallId = session.relayCallId ?: return@forEach
                    if (!session.isUnansweredIncoming() || !checked.add(session.id.value)) return@forEach
                    val terminal = synchronized(lock) { loadLedger().isTerminal(relayCallId, now()) }
                    if (terminal) {
                        Log.i(TAG, "rejecting INVITE for terminal relay call=$relayCallId")
                        callManager.reject(session.id)
                    }
                }
                checked.retainAll(current.map { it.id.value }.toSet())
            }
        }
    }

    private suspend fun reconcileCancel(relayCallId: String) {
        // Give the PBX's own SIP CANCEL/BYE a chance to arrive first; SIP state is authoritative.
        delay(CANCEL_GRACE_MS)
        callManager.sessions.value
            .filter { it.relayCallId == relayCallId && it.isUnansweredIncoming() }
            .forEach { session ->
                Log.i(TAG, "relay cancelled call=$relayCallId still ringing in SIP; rejecting it")
                callManager.reject(session.id)
            }
    }

    private fun CallSession.isUnansweredIncoming() =
        direction == CallDirection.INCOMING && (state == CallState.Incoming || state == CallState.Ringing)

    private fun loadLedger() = PushLedger.fromJson(prefs().getString(KEY_LEDGER, null))

    private fun saveLedger(ledger: PushLedger) = prefs().edit { putString(KEY_LEDGER, ledger.toJson()) }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val TAG = "YeyoFonePush"
        const val PREFS_NAME = "yeyofone_push_ledger"
        const val KEY_LEDGER = "ledger"
        const val CANCEL_GRACE_MS = 3_000L
    }
}
