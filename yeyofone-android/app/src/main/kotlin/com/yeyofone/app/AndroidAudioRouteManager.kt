package com.yeyofone.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.voip.AudioRouteManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Application-lifetime audio routing. Device callbacks publish the actual OS route. */
class AndroidAudioRouteManager(context: Context) : AudioRouteManager {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val mutableRoutes = MutableStateFlow<List<AudioRoute>>(emptyList())
    private val mutableSelected = MutableStateFlow<AudioRoute?>(null)
    private val scoState = MutableStateFlow(AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
    private val selectionMutex = Mutex()
    private var previousMode: Int? = null
    private var preferredRoute: AudioRoute? = null

    override val availableRoutes: StateFlow<List<AudioRoute>> = mutableRoutes.asStateFlow()
    override val selectedRoute: StateFlow<AudioRoute?> = mutableSelected.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshSafely()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshSafely()
    }

    init {
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.addOnCommunicationDeviceChangedListener(ContextCompat.getMainExecutor(context)) {
                refreshSafely()
            }
        } else {
            // SCO is asynchronous; a requested connection is not an active headset route.
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return
                    scoState.value = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    refreshSafely()
                }
            }
            val sticky = ContextCompat.registerReceiver(
                context.applicationContext, receiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED), ContextCompat.RECEIVER_EXPORTED,
            )
            sticky?.let { receiver.onReceive(context, it) }
        }
        refreshSafely()
    }

    override suspend fun prepareForCall() = withContext(Dispatchers.Main.immediate) {
        selectionMutex.withLock {
            if (previousMode != null) return@withLock
            previousMode = audio.mode
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            refreshSafely()
            // Preserve an existing headset route; otherwise prefer a connected headset.
            val headset = preferredRoute?.takeIf { it in mutableRoutes.value }
                ?: mutableSelected.value?.takeIf { it is AudioRoute.Bluetooth || it is AudioRoute.WiredHeadset }
                ?: mutableRoutes.value.firstOrNull { it is AudioRoute.WiredHeadset }
                ?: mutableRoutes.value.firstOrNull { it is AudioRoute.Bluetooth }
            if (headset != null) {
                try {
                    selectDevice(headset)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // A headset disappearing must not prevent answering on the phone.
                    Log.w(TAG, "Headset unavailable at call start; using system route", error)
                    clearSelection()
                    refreshSafely()
                }
            }
        }
    }

    override suspend fun select(route: AudioRoute) = withContext(Dispatchers.Main.immediate) {
        selectionMutex.withLock {
            refreshSafely()
            check(route in mutableRoutes.value) { "Audio route is unavailable" }
            if (previousMode == null) {
                // Settings choose the next call's device without leaving the phone in call mode.
                preferredRoute = route
                mutableSelected.value = route
                return@withLock
            }
            try {
                selectDevice(route)
            } catch (error: Exception) {
                clearSelection()
                refreshSafely()
                throw error
            }
        }
    }

    private suspend fun selectDevice(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audio.availableCommunicationDevices.firstOrNull { it.toRoute() == route }
                ?: error("Audio route is unavailable")
            check(audio.setCommunicationDevice(device)) { "Unable to select audio route" }
            refreshSafely()
            check(withTimeoutOrNull(10_000) { mutableSelected.first { it == route }; true } == true) {
                "Audio route did not connect"
            }
        } else {
            selectLegacy(route)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun selectLegacy(route: AudioRoute) {
        if (route is AudioRoute.Bluetooth) {
            check(mutableRoutes.value.contains(route)) { "Bluetooth headset is unavailable" }
            audio.isSpeakerphoneOn = false
            audio.startBluetoothSco()
            check(withTimeoutOrNull(10_000) { scoState.first { it == AudioManager.SCO_AUDIO_STATE_CONNECTED }; true } == true) {
                "Bluetooth audio did not connect"
            }
            audio.isBluetoothScoOn = true
        } else {
            audio.stopBluetoothSco()
            audio.isBluetoothScoOn = false
            audio.isSpeakerphoneOn = route == AudioRoute.Speaker
        }
        refreshSafely()
    }

    override suspend fun releaseAfterCall() = withContext(Dispatchers.Main.immediate) {
        selectionMutex.withLock {
            val mode = previousMode ?: return@withLock
            try {
                clearSelection()
            } catch (error: Exception) {
                Log.w(TAG, "Could not clear communication device", error)
            } finally {
                runCatching { audio.mode = mode }.onFailure { Log.w(TAG, "Could not restore audio mode", it) }
                previousMode = null
                preferredRoute = null
                refreshSafely()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun clearSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.clearCommunicationDevice()
        } else {
            audio.stopBluetoothSco()
            audio.isBluetoothScoOn = false
            audio.isSpeakerphoneOn = false
        }
    }

    private fun refreshSafely() {
        try {
            refresh()
        } catch (error: SecurityException) {
            Log.w(TAG, "Audio devices unavailable until permission is granted", error)
            mutableRoutes.value = listOf(AudioRoute.Earpiece, AudioRoute.Speaker)
            mutableSelected.value = null
        }
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.availableCommunicationDevices
            else audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        mutableRoutes.value = devices.mapNotNull(AudioDeviceInfo::toRoute).distinct()
        if (preferredRoute !in mutableRoutes.value) preferredRoute = null
        mutableSelected.value = if (previousMode == null && preferredRoute != null) {
            preferredRoute
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.communicationDevice?.toRoute()
        } else when {
            audio.isBluetoothScoOn && scoState.value == AudioManager.SCO_AUDIO_STATE_CONNECTED ->
                mutableRoutes.value.firstOrNull { it is AudioRoute.Bluetooth }
            audio.isSpeakerphoneOn -> AudioRoute.Speaker
            else -> mutableRoutes.value.firstOrNull { it is AudioRoute.WiredHeadset }
                ?: mutableRoutes.value.firstOrNull { it == AudioRoute.Earpiece }
        }
    }

    private companion object { const val TAG = "YeyoFoneAudioRoute" }
}

private fun AudioDeviceInfo.toRoute(): AudioRoute? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.Earpiece
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET ->
        AudioRoute.WiredHeadset(productName?.toString())
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET ->
        AudioRoute.Bluetooth(id.toString(), productName?.toString().orEmpty().ifBlank { "Bluetooth" })
    else -> null
}
