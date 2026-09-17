package com.yeyofone.app

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.voip.AudioRouteManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAudioRouteManager(context: Context) : AudioRouteManager {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val mutableRoutes = MutableStateFlow<List<AudioRoute>>(emptyList())
    private val mutableSelected = MutableStateFlow<AudioRoute?>(null)

    override val availableRoutes: StateFlow<List<AudioRoute>> = mutableRoutes.asStateFlow()
    override val selectedRoute: StateFlow<AudioRoute?> = mutableSelected.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    init {
        audio.registerAudioDeviceCallback(callback, null)
        refresh()
    }

    override suspend fun prepareForCall() {
        // PJSIP uses the Android communication stream. Without this mode some devices route
        // the call through a low-volume media stream instead of the voice-call path.
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override suspend fun select(route: AudioRoute) {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audio.availableCommunicationDevices.firstOrNull { it.toRoute() == route }
                ?: error("Audio route is unavailable")
            check(audio.setCommunicationDevice(device)) { "Unable to select audio route" }
        } else {
            selectLegacy(route)
        }
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun selectLegacy(route: AudioRoute) {
        when (route) {
            AudioRoute.Speaker -> audio.isSpeakerphoneOn = true
            AudioRoute.Earpiece, is AudioRoute.WiredHeadset -> audio.isSpeakerphoneOn = false
            is AudioRoute.Bluetooth -> {
                audio.isSpeakerphoneOn = false
                audio.startBluetoothSco()
                audio.isBluetoothScoOn = true
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.availableCommunicationDevices
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        mutableRoutes.value = devices.mapNotNull(AudioDeviceInfo::toRoute).distinct()
        mutableSelected.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.communicationDevice?.toRoute()
        } else if (audio.isSpeakerphoneOn) {
            AudioRoute.Speaker
        } else {
            mutableRoutes.value.firstOrNull { it != AudioRoute.Speaker }
        }
    }
}

private fun AudioDeviceInfo.toRoute(): AudioRoute? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.Earpiece
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    -> AudioRoute.WiredHeadset(productName?.toString())
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    -> AudioRoute.Bluetooth(id.toString(), productName?.toString().orEmpty().ifBlank { "Bluetooth" })
    else -> null
}
