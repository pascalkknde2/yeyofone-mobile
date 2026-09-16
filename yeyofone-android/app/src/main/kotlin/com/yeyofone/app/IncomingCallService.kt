package com.yeyofone.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IncomingCallService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var ringtone: Ringtone? = null
    private val notifiedMissedCalls = mutableSetOf<CallId>()

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())

        val app = application as YeyoFoneApplication
        serviceScope.launch {
            // Re-evaluate on every foreground/background transition too, not just call-state
            // changes - otherwise backgrounding mid-call (with no further session update) leaves
            // the in-call notification suppressed with no way to hang up from outside the app.
            app.callManager.sessions.combine(AppVisibility.isForegroundFlow) { sessions, _ -> sessions }
                .collectLatest(::updateCallNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        stopRinging()
        notifications.cancel(CALL_NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun createChannels() {
        notifications.createNotificationChannels(
            listOf(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    getString(R.string.background_service_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CALL_CHANNEL_ID,
                    getString(R.string.incoming_call_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(true)
                },
                NotificationChannel(
                    MISSED_CHANNEL_ID,
                    getString(R.string.missed_calls_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    private fun serviceNotification(): Notification = Notification.Builder(this, SERVICE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_phone)
        .setContentTitle(getString(R.string.ready_for_calls))
        .setContentText(getString(R.string.sip_service_running))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()

    private fun updateCallNotification(sessions: List<CallSession>) {
        sessions.filter { it.isMissedCall() && notifiedMissedCalls.add(it.id) }.forEach(::notifyMissedCall)
        val active = sessions.lastOrNull { !it.state.isTerminal() }
        if (active == null) {
            stopRinging()
            notifications.cancel(CALL_NOTIFICATION_ID)
            return
        }

        val incoming = active.direction == CallDirection.INCOMING &&
            (active.state == CallState.Incoming || active.state == CallState.Ringing)
        if (incoming) startRinging() else stopRinging()
        if (AppVisibility.isForeground) {
            notifications.cancel(CALL_NOTIFICATION_ID)
            return
        }
        val builder = Notification.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(
                getString(if (incoming) R.string.incoming_call_title else R.string.call_in_progress),
            )
            .setContentText(active.remoteUri)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(!incoming)

        if (incoming) {
            builder
                .setFullScreenIntent(openAppIntent(), true)
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.decline),
                        actionIntent(ACTION_DECLINE, active.id),
                    ).build(),
                )
                .addAction(
                    Notification.Action.Builder(
                        null,
                        getString(R.string.accept),
                        actionIntent(ACTION_ACCEPT, active.id),
                    ).build(),
                )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.hang_up),
                    actionIntent(ACTION_HANG_UP, active.id),
                ).build(),
            )
        }

        notifications.notify(CALL_NOTIFICATION_ID, builder.build())
    }

    private fun notifyMissedCall(session: CallSession) {
        val id = MISSED_NOTIFICATION_BASE + (session.id.value.hashCode() and Int.MAX_VALUE) % 100_000
        notifications.notify(
            id,
            Notification.Builder(this, MISSED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(getString(R.string.missed_call_notification))
                .setContentText(session.remoteUri)
                .setContentIntent(openAppIntent())
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun startRinging() {
        if (ringtone?.isPlaying == true) return
        runCatching {
            ringtone = RingtoneManager.getRingtone(
                this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            )?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String, callId: CallId): PendingIntent = PendingIntent.getBroadcast(
        this,
        action.hashCode(),
        Intent(this, CallActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("yeyofone://call/${callId.value}/$action")
            putExtra(EXTRA_CALL_ID, callId.value)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_ACCEPT = "com.yeyofone.app.action.ACCEPT"
        const val ACTION_DECLINE = "com.yeyofone.app.action.DECLINE"
        const val ACTION_HANG_UP = "com.yeyofone.app.action.HANG_UP"
        const val EXTRA_CALL_ID = "call_id"

        private const val TAG = "IncomingCallService"
        private const val SERVICE_CHANNEL_ID = "yeyofone_service"
        private const val CALL_CHANNEL_ID = "incoming_calls_v2"
        private const val MISSED_CHANNEL_ID = "missed_calls"
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val CALL_NOTIFICATION_ID = 1002
        private const val MISSED_NOTIFICATION_BASE = 2000

        fun start(context: Context) {
            // Android 12+ can refuse a foreground-service start from a non-exempt background
            // context (ForegroundServiceStartNotAllowedException). Callers such as
            // Application.onCreate() can run in exactly that context, so this must not crash them;
            // a call that arrives while the service isn't running will still show as a missed call.
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, IncomingCallService::class.java))
            }.onFailure { Log.w(TAG, "Could not start IncomingCallService", it) }
        }
    }
}

/** Process-level visibility shared by the activity and the notification service. */
internal object AppVisibility {
    private val mutableIsForeground = MutableStateFlow(false)
    val isForegroundFlow: StateFlow<Boolean> = mutableIsForeground.asStateFlow()
    var isForeground: Boolean
        get() = mutableIsForeground.value
        set(value) {
            mutableIsForeground.value = value
        }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(IncomingCallService.EXTRA_CALL_ID)?.let(::CallId) ?: return
        val pendingResult = goAsync()
        val manager = (context.applicationContext as YeyoFoneApplication).callManager
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    IncomingCallService.ACTION_ACCEPT -> {
                        manager.answer(callId)
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(IncomingCallService.EXTRA_CALL_ID, callId.value)
                            },
                        )
                    }
                    IncomingCallService.ACTION_DECLINE -> manager.reject(callId)
                    IncomingCallService.ACTION_HANG_UP -> manager.end(callId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            IncomingCallService.start(context)
        }
    }
}

private fun CallState.isTerminal(): Boolean = this is CallState.Disconnected || this is CallState.Failed

private fun CallSession.isMissedCall(): Boolean =
    direction == CallDirection.INCOMING && connectedAt == null && state.isTerminal()
