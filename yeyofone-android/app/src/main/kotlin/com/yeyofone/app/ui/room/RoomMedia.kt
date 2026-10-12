package com.yeyofone.app.ui.room

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yeyofone.app.R
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local captures for the Team room, like the desktop app's roomMedia.ts. They are shown only on
 * this device; nothing is sent, and each one stops as soon as it is turned off or the room closes.
 */
internal object RoomLevels {
    /** Root-mean-square level (0–1) of the first [count] 16-bit PCM samples. */
    fun micLevel(samples: ShortArray, count: Int = samples.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val value = samples[i] / 32768.0
            sum += value * value
        }
        return sqrt(sum / count).toFloat()
    }

    /**
     * Two thresholds so the speaking badge doesn't flicker around a single value: it turns on
     * above 0.04 and only turns off again below 0.02.
     */
    fun nextTalking(level: Float, wasTalking: Boolean): Boolean =
        if (wasTalking) level > 0.02f else level > 0.04f
}

/** Lights your "speaking" badge from the microphone level. */
internal class RoomMicMeter(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var record: AudioRecord? = null

    /** Starts listening, or returns the string resource saying why it couldn't. */
    @SuppressLint("MissingPermission") // The room asks for RECORD_AUDIO before calling this.
    fun start(onTalking: (Boolean) -> Unit): Int? {
        val rate = 16_000
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (size <= 0) return R.string.room_error_mic_missing
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: return R.string.room_error_mic_failed
        runCatching { record.startRecording() }.onFailure {
            record.release()
            return R.string.room_error_mic_failed
        }
        this.record = record
        job = scope.launch(Dispatchers.IO) {
            val samples = ShortArray(size / 2)
            var talking = false
            while (isActive) {
                val read = record.read(samples, 0, samples.size)
                if (read <= 0) continue
                val next = RoomLevels.nextTalking(RoomLevels.micLevel(samples, read), talking)
                if (next != talking) {
                    talking = next
                    withContext(Dispatchers.Main) { onTalking(next) }
                }
            }
        }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
    }
}

/**
 * The live front-camera picture, mirrored like a mirror. CameraX allows one preview at a time,
 * so the room shows your camera in a single place (the stage, or your own tile).
 */
@Composable
internal fun RoomCameraPreview(modifier: Modifier = Modifier, onError: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        future.addListener({
            val cameras = runCatching { future.get() }.getOrNull() ?: return@addListener onError(R.string.room_error_camera_failed)
            provider = cameras
            fun has(selector: CameraSelector) = runCatching { cameras.hasCamera(selector) }.getOrDefault(false)
            val selector = when {
                has(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                has(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> return@addListener onError(R.string.room_error_camera_missing)
            }
            runCatching { cameras.bindToLifecycle(lifecycleOwner, selector, preview) }
                .onFailure { onError(R.string.room_error_camera_failed) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbind(preview) }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
