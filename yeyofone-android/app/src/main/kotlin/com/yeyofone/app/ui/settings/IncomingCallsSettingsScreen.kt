package com.yeyofone.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.core.model.AccountPreferences
import com.yeyofone.core.model.PreferenceToggle
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.*

private val Purple = Color(0xFF8B5CF6)
private val PurpleLight = Color(0xFFF5F3FF)
private val RingtoneOrange = Color(0xFFF97316)
private val RingtoneOrangeLight = Color(0xFFFFF7ED)
private val SettingPink = Color(0xFFDB2777)
private val SettingPinkLight = Color(0xFFFDF2F8)

@Composable
fun IncomingCallsSettingsScreen(
    preferences: AccountPreferences,
    onBack: () -> Unit,
    onToggle: (PreferenceToggle, Boolean) -> Unit,
) {
    var ringtone by remember { mutableStateOf("Default") }
    var playing by remember { mutableStateOf(false) }
    var showRingtones by remember { mutableStateOf(false) }
    var showForwarding by remember { mutableStateOf(false) }
    var forwardingNumber by remember { mutableStateOf("") }
    var savedForwardingNumber by remember { mutableStateOf("") }

    Scaffold(containerColor = BackgroundGray) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            Row(Modifier.fillMaxWidth().background(CardWhite).padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = TextPrimary) }
                Text(stringResource(R.string.incoming_calls_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    IncomingGroup(stringResource(R.string.general_section)) {
                        IncomingToggleRow(Icons.Default.Notifications, AccentBlue, PrimaryLight, R.string.allow_incoming_calls, R.string.allow_incoming_calls_subtitle, preferences.allowIncoming) { onToggle(PreferenceToggle.AllowIncoming, it) }
                        IncomingDivider()
                        Row(Modifier.fillMaxWidth().clickable { showRingtones = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IncomingIcon(Icons.Default.MusicNote, RingtoneOrange, RingtoneOrangeLight)
                            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.ringtone_setting), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(ringtone, fontSize = 13.sp, color = TextSecondary)
                            }
                            IconButton(onClick = { playing = !playing }) {
                                Icon(if (playing) Icons.Default.Notifications else Icons.Default.MusicNote, stringResource(R.string.preview_ringtone), tint = AccentBlue)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray)
                        }
                        IncomingDivider()
                        IncomingToggleRow(Icons.Default.Vibration, Purple, PurpleLight, R.string.vibrate_setting, R.string.vibrate_subtitle, preferences.vibrate) { onToggle(PreferenceToggle.Vibrate, it) }
                    }
                }
                item {
                    IncomingGroup(stringResource(R.string.call_handling_section)) {
                        IncomingToggleRow(Icons.Default.PhoneInTalk, AccentBlue, PrimaryLight, R.string.call_waiting_setting, R.string.call_waiting_subtitle, preferences.callWaiting) { onToggle(PreferenceToggle.CallWaiting, it) }
                        IncomingDivider()
                        Row(Modifier.fillMaxWidth().clickable { forwardingNumber = savedForwardingNumber; showForwarding = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IncomingIcon(Icons.Default.PhoneForwarded, AccentBlue, PrimaryLight)
                            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.call_forwarding_setting), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(savedForwardingNumber.ifBlank { stringResource(R.string.call_forwarding_subtitle) }, fontSize = 13.sp, color = TextSecondary)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray)
                        }
                        IncomingDivider()
                        IncomingToggleRow(Icons.Default.DoNotDisturbOn, SettingPink, SettingPinkLight, R.string.do_not_disturb_setting, R.string.do_not_disturb_subtitle, preferences.doNotDisturb) { onToggle(PreferenceToggle.DoNotDisturb, it) }
                    }
                }
                item {
                    IncomingGroup(stringResource(R.string.advanced_section)) {
                        IncomingToggleRow(Icons.Default.Vibration, Purple, PurpleLight, R.string.flip_to_mute_setting, R.string.flip_to_mute_subtitle, preferences.flipToMute) { onToggle(PreferenceToggle.FlipToMute, it) }
                        IncomingDivider()
                        IncomingToggleRow(Icons.Default.Notifications, AccentBlue, PrimaryLight, R.string.call_announce_setting, R.string.call_announce_subtitle, preferences.announceCaller) { onToggle(PreferenceToggle.AnnounceCaller, it) }
                    }
                }
            }
        }
    }

    if (showRingtones) {
        val choices = listOf("Default", "Classic", "Digital", "Silent")
        AlertDialog(
            onDismissRequest = { showRingtones = false },
            title = { Text(stringResource(R.string.choose_ringtone)) },
            text = { Column { choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable { ringtone = choice; showRingtones = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ringtone == choice, onClick = { ringtone = choice; showRingtones = false })
                    Text(choice, color = TextPrimary)
                }
            } } },
            confirmButton = { TextButton(onClick = { showRingtones = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showForwarding) {
        AlertDialog(
            onDismissRequest = { showForwarding = false },
            title = { Text(stringResource(R.string.call_forwarding_setting)) },
            text = { OutlinedTextField(value = forwardingNumber, onValueChange = { forwardingNumber = it }, label = { Text(stringResource(R.string.forward_to_number)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { savedForwardingNumber = forwardingNumber.trim(); showForwarding = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { showForwarding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun IncomingGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), Modifier.padding(start = 4.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InactiveGray, letterSpacing = 1.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite)) { content() }
    }
}

@Composable
private fun IncomingIcon(icon: ImageVector, tint: Color, background: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun IncomingToggleRow(icon: ImageVector, tint: Color, background: Color, title: Int, subtitle: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IncomingIcon(icon, tint, background)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(stringResource(subtitle), fontSize = 13.sp, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun IncomingDivider() { HorizontalDivider(Modifier.padding(start = 72.dp), color = BorderLight.copy(alpha = .55f)) }
