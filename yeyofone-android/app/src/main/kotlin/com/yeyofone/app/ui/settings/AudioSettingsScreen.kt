package com.yeyofone.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.RingtonePreference
import com.yeyofone.app.label
import com.yeyofone.app.ui.theme.*
import com.yeyofone.core.model.AudioRoute

@Composable
fun AudioSettingsScreen(
    availableRoutes: List<AudioRoute>,
    selectedRoute: AudioRoute?,
    onSelectRoute: (AudioRoute) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1) }
    var outputVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) / maxVolume.toFloat())
    }
    var ringtoneTitle by remember { mutableStateOf(RingtonePreference.title(context)) }
    var showDevices by remember { mutableStateOf(false) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.ringtoneExtra()
            RingtonePreference.set(context, uri)
            ringtoneTitle = RingtonePreference.title(context)
        }
    }

    Scaffold(containerColor = BackgroundGray) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            AudioHeader(stringResource(R.string.audio_title), onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    AudioGroup(stringResource(R.string.output_section)) {
                        AudioSliderRow(
                            stringResource(R.string.speaker_volume),
                            Icons.AutoMirrored.Filled.VolumeUp,
                            outputVolume,
                            { value ->
                                outputVolume = value
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_VOICE_CALL,
                                    (value * maxVolume).toInt().coerceIn(0, maxVolume),
                                    0,
                                )
                            },
                            AccentBlue,
                        )
                        if (availableRoutes.size > 1) {
                            HorizontalDivider(Modifier.padding(start = 76.dp), color = BorderLight.copy(alpha = .55f))
                            AudioOptionRow(
                                stringResource(R.string.output_device),
                                selectedRoute?.label() ?: stringResource(R.string.device_speaker),
                                Icons.Default.MusicNote,
                                AccentBlue,
                            ) { showDevices = true }
                        }
                    }
                }
                item {
                    AudioGroup(stringResource(R.string.ringtones_section)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                ringtonePicker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, RingtonePreference.get(context))
                                    },
                                )
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AudioIcon(Icons.Default.MusicNote, AccentBlue, PrimaryLight)
                            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.ringtone_setting), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(ringtoneTitle, fontSize = 13.sp, color = TextSecondary)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray)
                        }
                    }
                }
            }
        }
    }
    if (showDevices) {
        AlertDialog(
            onDismissRequest = { showDevices = false },
            title = { Text(stringResource(R.string.output_device)) },
            text = {
                Column {
                    availableRoutes.forEach { route ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelectRoute(route); showDevices = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = route == selectedRoute, onClick = { onSelectRoute(route); showDevices = false })
                            Text(route.label(), color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDevices = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Suppress("DEPRECATION")
private fun Intent.ringtoneExtra(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
} else {
    getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
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
private fun AudioIcon(icon: ImageVector, color: Color, background: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun AudioSliderRow(title: String, icon: ImageVector, value: Float, change: (Float) -> Unit, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AudioIcon(icon, tint, PrimaryLight)
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
private fun AudioOptionRow(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        AudioIcon(icon, tint, PrimaryLight)
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray, modifier = Modifier.size(18.dp))
    }
}
