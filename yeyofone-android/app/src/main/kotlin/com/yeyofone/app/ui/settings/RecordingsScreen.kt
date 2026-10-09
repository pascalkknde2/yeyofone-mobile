package com.yeyofone.app.ui.settings

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RecordingFile(
    val file: File,
    val durationMs: Long,
    val modifiedAt: Long,
) {
    val title: String get() = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
    val durationText: String get() = "%02d:%02d".format(durationMs / 60_000, durationMs / 1_000 % 60)
}

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var activePath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    val recordings by produceState<List<RecordingFile>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadRecordings(File(context.filesDir, RECORDINGS_DIRECTORY)) }
    }
    val usedBytes = recordings.sumOf { it.file.length() }
    val totalBytes = runCatching { android.os.StatFs(context.filesDir.path).totalBytes }.getOrDefault(0L)
    val filtered = recordings.filter { it.title.contains(query.trim(), ignoreCase = true) }

    fun stopPlayback() {
        player.value?.runCatching { stop() }
        player.value?.release()
        player.value = null
        activePath = null
        isPlaying = false
        isReady = false
    }

    fun playRecording(recording: RecordingFile) {
        if (activePath == recording.file.absolutePath) {
            if (isPlaying) {
                player.value?.pause()
                isPlaying = false
            } else if (isReady) {
                player.value?.start()
                isPlaying = true
            }
            return
        }
        stopPlayback()
        runCatching {
            MediaPlayer().also { mediaPlayer ->
                player.value = mediaPlayer
                activePath = recording.file.absolutePath
                mediaPlayer.setDataSource(recording.file.absolutePath)
                mediaPlayer.setOnPreparedListener { isReady = true; it.start(); isPlaying = true }
                mediaPlayer.setOnCompletionListener { stopPlayback() }
                mediaPlayer.prepareAsync()
            }
        }.onFailure {
            stopPlayback()
            Toast.makeText(context, R.string.recording_playback_failed, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.value?.release()
            player.value = null
        }
    }

    Scaffold(containerColor = BackgroundGray) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            Row(
                Modifier.fillMaxWidth().background(CardWhite).statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary)
                }
                Text(stringResource(R.string.recordings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.recording_storage_title).uppercase(), Modifier.padding(start = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InactiveGray, letterSpacing = 1.sp)
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite).padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.recording_storage_used), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}", fontSize = 13.sp, color = TextSecondary)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(CircleShape).background(Color(0xFFE2E8F0))) {
                                val progress = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(AccentBlue))
                            }
                            Text(
                                pluralStringResource(R.plurals.recording_count, recordings.size, recordings.size),
                                Modifier.padding(top = 8.dp),
                                fontSize = 12.sp,
                                color = InactiveGray,
                            )
                        }
                    }
                }
                item {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        placeholder = { Text(stringResource(R.string.recording_search_hint), color = InactiveGray) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = InactiveGray) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                item {
                    Text(stringResource(R.string.recording_recent_title).uppercase(), Modifier.padding(start = 4.dp, bottom = (-12).dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InactiveGray, letterSpacing = 1.sp)
                }
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite).padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Mic, null, tint = InactiveGray, modifier = Modifier.size(32.dp))
                            Text(
                                stringResource(if (query.isBlank()) R.string.recording_empty_title else R.string.recording_search_empty_title),
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            Text(
                                stringResource(if (query.isBlank()) R.string.recording_empty_subtitle else R.string.recording_search_empty_subtitle),
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                } else {
                    item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite)) {
                        filtered.forEachIndexed { index, recording ->
                            if (index > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 80.dp), color = BorderLight.copy(alpha = .55f))
                            RecordingRow(
                                recording = recording,
                                playing = activePath == recording.file.absolutePath && isPlaying,
                                onPlay = { playRecording(recording) },
                            )
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(recording: RecordingFile, playing: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFDF2F8)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = Color(0xFFDB2777), modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recording.title, maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AccessTime, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Text(recording.durationText, fontSize = 12.sp, color = TextSecondary)
                Text("·", color = InactiveGray)
                Text(DateUtils.getRelativeTimeSpanString(recording.modifiedAt).toString(), fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                Text("·", color = InactiveGray)
                Text(formatBytes(recording.file.length()), fontSize = 12.sp, color = TextSecondary)
            }
        }
        IconButton(
            onClick = onPlay,
            modifier = Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape).background(PrimaryLight),
        ) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.recording_pause else R.string.recording_play), tint = AccentBlue)
        }
    }
}

private fun loadRecordings(directory: File): List<RecordingFile> = directory.listFiles()
    .orEmpty()
    .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
    .map { file ->
        val retriever = MediaMetadataRetriever()
        val duration = runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        runCatching { retriever.release() }
        RecordingFile(file, duration, file.lastModified())
    }
    .sortedByDescending { it.modifiedAt }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}

private const val RECORDINGS_DIRECTORY = "recordings"
private val SUPPORTED_EXTENSIONS = setOf("wav", "mp3", "m4a", "3gp", "ogg", "aac")
