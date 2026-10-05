package com.yeyofone.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.*

private val AudioPink = Color(0xFFDB2777)
private val AudioPinkLight = Color(0xFFFDF2F8)

@Composable
fun AudioSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var outputVolume by remember { mutableFloatStateOf(.70f) }
    var microphoneLevel by remember { mutableFloatStateOf(.85f) }
    var muted by remember { mutableStateOf(false) }
    var selectedRingtone by remember { mutableIntStateOf(0) }
    var playing by remember { mutableIntStateOf(-1) }
    var showDevices by remember { mutableStateOf(false) }
    var outputDevice by remember { mutableIntStateOf(0) }
    val devices = listOf(R.string.device_speaker, R.string.device_earpiece, R.string.device_headphones)

    Scaffold(containerColor = BackgroundGray) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            AudioHeader(stringResource(R.string.audio_title), onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    AudioGroup(stringResource(R.string.output_section)) {
                        AudioSliderRow(stringResource(R.string.speaker_volume), Icons.AutoMirrored.Filled.VolumeUp, outputVolume, { outputVolume = it }, AccentBlue)
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = BorderLight.copy(alpha = .55f))
                        AudioOptionRow(stringResource(R.string.output_device), stringResource(devices[outputDevice]), Icons.Default.MusicNote, AccentBlue) { showDevices = true }
                    }
                }
                item {
                    AudioGroup(stringResource(R.string.input_section)) {
                        AudioSliderRow(stringResource(R.string.microphone_level), Icons.Default.Mic, microphoneLevel, { microphoneLevel = it }, AudioPink)
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = BorderLight.copy(alpha = .55f))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AudioIcon(Icons.Default.Mic, AudioPink, AudioPinkLight)
                            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.mute_microphone), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(stringResource(R.string.mute_microphone_subtitle), fontSize = 13.sp, color = TextSecondary)
                            }
                            Switch(checked = muted, onCheckedChange = { muted = it })
                        }
                    }
                }
                item {
                    AudioGroup(stringResource(R.string.ringtones_section)) {
                        listOf(R.string.ringtone_default, R.string.ringtone_classic, R.string.ringtone_digital, R.string.ringtone_silent).forEachIndexed { index, title ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 20.dp), color = BorderLight.copy(alpha = .55f))
                            Row(Modifier.fillMaxWidth().clickable { selectedRingtone = index }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                        drawCircle(if (selectedRingtone == index) AccentBlue else BorderLight, style = Stroke(2.dp.toPx()))
                                        if (selectedRingtone == index) drawCircle(AccentBlue, radius = size.minDimension / 2)
                                    }
                                    if (selectedRingtone == index) Box(Modifier.size(8.dp).clip(CircleShape).background(CardWhite))
                                }
                                Text(stringResource(title), Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                IconButton(onClick = { playing = if (playing == index) -1 else index }) {
                                    Icon(if (playing == index) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.preview_ringtone), tint = AccentBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDevices) AlertDialog(
        onDismissRequest = { showDevices = false },
        title = { Text(stringResource(R.string.output_device)) },
        text = { Column { devices.forEachIndexed { index, device ->
            Row(Modifier.fillMaxWidth().clickable { outputDevice = index; showDevices = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = outputDevice == index, onClick = { outputDevice = index; showDevices = false })
                Text(stringResource(device), color = TextPrimary)
            }
        } } },
        confirmButton = { TextButton(onClick = { showDevices = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AudioHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardWhite).statusBarsPadding()
            .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.cd_back),
                tint = TextPrimary,
            )
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun AudioGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), Modifier.padding(start = 4.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InactiveGray, letterSpacing = 1.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite)) { content() }
    }
}

@Composable
private fun AudioIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, background: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun AudioSliderRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float, change: (Float) -> Unit, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AudioIcon(icon, tint, if (tint == AudioPink) AudioPinkLight else PrimaryLight)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, Modifier.padding(bottom = 4.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Slider(value, change, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AudioOptionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        AudioIcon(icon, tint, PrimaryLight)
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray, modifier = Modifier.size(18.dp))
    }
}
