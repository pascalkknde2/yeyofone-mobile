package com.yeyofone.app.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Turns a call recording into [CallNotes] entirely on the phone with the Vosk offline recogniser.
 * The small English model (about 40 MB) is downloaded once, the first time the assistant is used;
 * no audio or text ever leaves the device.
 */
class CallNotesProcessor(private val context: Context) {
    suspend fun process(recording: File, onState: (NotesState) -> Unit) {
        val state = try {
            awaitFinalized(recording)
            val modelDir = ensureModel { percent -> onState(NotesState.Preparing(percent)) }
            onState(NotesState.Transcribing)
            val segments = transcribe(recording, modelDir)
            val notes = NotesSummarizer.summarize(segments)
            CallNotes.fileFor(recording).writeText(notes.toJson())
            NotesState.Ready(notes)
        } catch (error: Exception) {
            Log.w(TAG, "Call notes could not be made", error)
            NotesState.Failed(error.message?.take(120) ?: error.javaClass.simpleName)
        }
        onState(state)
    }

    /** The engine closes the WAV when the call disconnects; wait until its size stops changing. */
    private suspend fun awaitFinalized(file: File) {
        var last = -1L
        repeat(20) {
            val size = file.length()
            if (size > 44 && size == last) return
            last = size
            delay(500)
        }
        check(file.length() > 44) { "The recording is empty" }
    }

    private suspend fun ensureModel(onProgress: (Int?) -> Unit): File = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "vosk")
        val model = File(root, MODEL_NAME)
        if (File(model, "am/final.mdl").exists()) return@withContext model
        onProgress(0)
        root.mkdirs()
        val zip = File(context.cacheDir, "$MODEL_NAME.zip")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            check(connection.responseCode == 200) { "Speech model download failed (${connection.responseCode})" }
            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                zip.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val target = File(root, "$MODEL_NAME.partial").also { it.deleteRecursively() }
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                val out = File(root, "$MODEL_NAME.partial/" + entry.name.removePrefix("$MODEL_NAME/"))
                // Guard against entries that try to escape the model folder.
                check(out.canonicalPath.startsWith(target.canonicalPath)) { "Unexpected path in model archive" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { stream.copyTo(it) }
                }
            }
        }
        zip.delete()
        check(target.renameTo(model)) { "Speech model could not be installed" }
        model
    }

    private suspend fun transcribe(wav: File, modelDir: File): List<String> = withContext(Dispatchers.IO) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val segments = mutableListOf<String>()
        Model(modelDir.absolutePath).use { model ->
            FileInputStream(wav).use { input ->
                val (sampleRate, dataStart) = readWavHeader(wav)
                input.skip(dataStart)
                Recognizer(model, sampleRate.toFloat()).use { recognizer ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (recognizer.acceptWaveForm(buffer, n)) segments += textOf(recognizer.result)
                    }
                    segments += textOf(recognizer.finalResult)
                }
            }
        }
        segments.filter { it.isNotBlank() }
    }

    private fun textOf(json: String): String = JSONObject(json).optString("text")

    /** Sample rate and the byte offset where the PCM data starts, from a RIFF/WAVE header. */
    private fun readWavHeader(file: File): Pair<Int, Long> {
        FileInputStream(file).use { input ->
            val header = ByteArray(4096)
            val read = input.read(header)
            check(read > 44 && String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") { "Not a WAV recording" }
            var sampleRate = 16_000
            var position = 12
            while (position + 8 <= read) {
                val id = String(header, position, 4)
                val size = le32(header, position + 4)
                if (id == "fmt ") sampleRate = le32(header, position + 12)
                if (id == "data") return sampleRate to (position + 8).toLong()
                position += 8 + size + (size and 1)
            }
            error("WAV recording has no audio data")
        }
    }

    private fun le32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val TAG = "YeyoFoneNotes"
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    }
}
