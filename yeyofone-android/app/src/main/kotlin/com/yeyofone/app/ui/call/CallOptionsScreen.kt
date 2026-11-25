package com.yeyofone.app.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.toSipIdentity
import com.yeyofone.app.label
import com.yeyofone.app.statusLabel
import com.yeyofone.app.ui.components.DialPad
import com.yeyofone.app.ui.components.PrimaryCallActionButton
import com.yeyofone.app.ui.components.SecondaryCallActionButton
import com.yeyofone.app.ui.theme.Palette
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.GlassCard
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState

/**
 * The call's "More" panel - a dedicated screen (not an inline overlay) so Hold, Consult transfer,
 * and audio routing each get room to breathe instead of competing with the active-call UI.
 */
@Composable
fun CallOptionsScreen(
    held: Boolean,
    onHoldChange: (Boolean) -> Unit,
    availableRoutes: List<AudioRoute>,
    selectedRoute: AudioRoute?,
    onSelectRoute: (AudioRoute) -> Unit,
    heldCallerName: String,
    consultationSession: CallSession?,
    conferenceMerged: Boolean,
    muted: Boolean = false,
    speakingIds: Set<String> = emptySet(),
    onMergeCalls: () -> Unit,
    recording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    assistant: Boolean,
    onAssistantChange: (Boolean) -> Unit,
    onStartConsultation: (String) -> Unit,
    onCompleteTransfer: () -> Unit,
    onReturnToCaller: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var enteringDestination by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }
    var showRoutePicker by remember { mutableStateOf(false) }
    // Add call and Consult transfer share the dial-and-hold flow; only Consult transfer can complete a transfer.
    var addingCall by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel), tint = CallTextPrimary)
            }
            val title = when {
                consultationSession != null || enteringDestination ->
                    if (addingCall || conferenceMerged) R.string.add_call else R.string.consult_transfer
                else -> R.string.more_options
            }
            Text(stringResource(title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
            Spacer(Modifier.size(48.dp))
        }

        when {
            consultationSession != null -> {
                ConsultationCenter(consultationSession, heldCallerName, conferenceMerged, muted, speakingIds, Modifier.weight(1f))
                ConsultationTray(consultationSession, conferenceMerged, addingCall, onMergeCalls, onCompleteTransfer, onReturnToCaller)
            }
            enteringDestination -> {
                ConsultDestinationCenter(
                    destination = destination,
                    held = held,
                    onDigit = { destination += it },
                    onBackspace = { destination = destination.dropLast(1) },
                    modifier = Modifier.weight(1f),
                )
                ConsultDestinationTray(
                    enabled = destination.isNotBlank() && held,
                    addingCall = addingCall,
                    onStart = {
                        onStartConsultation(destination)
                        enteringDestination = false
                        destination = ""
                    },
                )
            }
            else -> Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                CallOptionsGroup {
                    CallOptionRow(
                        icon = if (held) Icons.Default.PlayArrow else Icons.Default.Pause,
                        title = stringResource(if (held) R.string.resume else R.string.hold),
                        subtitle = stringResource(
                            if (held) R.string.call_options_resume_subtitle else R.string.call_options_hold_subtitle,
                        ),
                        onClick = { onHoldChange(!held) },
                    )
                    OptionDivider()
                    CallOptionRow(
                        icon = Icons.Default.PersonAdd,
                        title = stringResource(R.string.consult_transfer),
                        subtitle = stringResource(R.string.call_options_consult_subtitle),
                        onClick = {
                            if (!held) onHoldChange(true)
                            enteringDestination = true
                        },
                    )
                    OptionDivider()
                    CallOptionRow(
                        icon = Icons.Default.AddIcCall,
                        title = stringResource(R.string.add_call),
                        subtitle = stringResource(R.string.call_options_add_call_subtitle),
                        onClick = {
                            if (!held) onHoldChange(true)
                            addingCall = true
                            enteringDestination = true
                        },
                    )
                    OptionDivider()
                    CallOptionRow(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(if (assistant) R.string.stop_ai_assistant else R.string.ai_assistant),
                        subtitle = stringResource(
                            if (assistant) R.string.call_options_ai_on_subtitle else R.string.call_options_ai_subtitle,
                        ),
                        iconTint = if (assistant) AccentGreen else AccentBlue,
                        onClick = { onAssistantChange(!assistant) },
                    )
                    OptionDivider()
                    CallOptionRow(
                        icon = Icons.Default.FiberManualRecord,
                        title = stringResource(if (recording) R.string.stop_recording else R.string.record_call),
                        subtitle = stringResource(
                            if (recording) R.string.call_options_recording_subtitle else R.string.call_options_record_subtitle,
                        ),
                        iconTint = if (recording) AccentRed else AccentBlue,
                        onClick = { onRecordingChange(!recording) },
                    )
                    if (availableRoutes.size > 1) {
                        OptionDivider()
                        CallOptionRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = stringResource(R.string.output_device),
                            subtitle = selectedRoute?.label() ?: stringResource(R.string.device_speaker),
                            onClick = { showRoutePicker = true },
                        )
                    }
                }
            }
        }
    }

    if (showRoutePicker) {
        AlertDialog(
            onDismissRequest = { showRoutePicker = false },
            title = { Text(stringResource(R.string.output_device)) },
            text = {
                Column {
                    availableRoutes.forEach { route ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelectRoute(route); showRoutePicker = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = route == selectedRoute, onClick = { onSelectRoute(route); showRoutePicker = false })
                            Text(route.label(), color = CallTextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRoutePicker = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ConsultDestinationCenter(
    destination: String,
    held: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.consultation_destination),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = CallTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = destination.ifEmpty { stringResource(R.string.enter_number) },
            fontSize = if (destination.isEmpty()) 16.sp else 32.sp,
            fontWeight = if (destination.isEmpty()) FontWeight.Normal else FontWeight.Medium,
            color = if (destination.isEmpty()) InactiveGray else CallTextPrimary,
            letterSpacing = if (destination.isEmpty()) 0.sp else 1.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (destination.isNotEmpty()) {
            IconButton(onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.backspace), tint = CallTextSecondary, modifier = Modifier.size(28.dp))
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        if (!held) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.waiting_for_hold), fontSize = 12.sp, color = CallTextSecondary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DialPad(onNumberClick = onDigit, modifier = Modifier.widthIn(max = 288.dp).fillMaxWidth(), rowSpacing = 16.dp)
        }
    }
}

@Composable
private fun ConsultDestinationTray(enabled: Boolean, addingCall: Boolean, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .shadow(if (Palette.isDark) 0.dp else 4.dp, RoundedCornerShape(32.dp))
            .background(GlassCard, RoundedCornerShape(32.dp))
            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryCallActionButton(
            icon = Icons.Default.PersonAdd,
            label = stringResource(if (addingCall) R.string.add_call else R.string.start_consultation),
            backgroundColor = AccentGreen,
            onClick = onStart,
            enabled = enabled,
        )
    }
}

@Composable
private fun ConsultationCenter(
    session: CallSession,
    heldCallerName: String,
    merged: Boolean,
    muted: Boolean,
    speakingIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    val identity = session.remoteUri.toSipIdentity()
    if (merged) {
        ConferenceStage(
            participants = listOf(
                Participant("you", stringResource(R.string.conference_you), isSelf = true, muted = muted),
                Participant("held", heldCallerName),
                Participant("consult", identity.displayName),
            ),
            speakingIds = speakingIds,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // A bare extension has no separate display name; don't repeat it as "1002 · Ext. 1002".
            if (identity.displayName == identity.extension) {
                stringResource(R.string.consultation_call, identity.displayName)
            } else {
                stringResource(R.string.consultation_identity, identity.displayName, identity.extension)
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CallTextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(session.state.statusLabel()), fontSize = 15.sp, color = CallTextSecondary)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.consultation_caller_on_hold, heldCallerName),
            fontSize = 13.sp,
            color = CallTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConsultationTray(
    session: CallSession,
    merged: Boolean,
    addingCall: Boolean,
    onMergeCalls: () -> Unit,
    onCompleteTransfer: () -> Unit,
    onReturnToCaller: () -> Unit,
) {
    val connected = session.state == CallState.Connected
    Column(
        Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .shadow(if (Palette.isDark) 0.dp else 4.dp, RoundedCornerShape(32.dp))
            .background(GlassCard, RoundedCornerShape(32.dp))
            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!merged) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PrimaryCallActionButton(
                    icon = Icons.Default.CallMerge,
                    label = stringResource(R.string.merge_calls),
                    backgroundColor = AccentBlue,
                    onClick = onMergeCalls,
                    enabled = connected,
                )
                if (!addingCall) {
                    PrimaryCallActionButton(
                        icon = Icons.Default.Check,
                        label = stringResource(R.string.complete_transfer),
                        backgroundColor = AccentGreen,
                        onClick = onCompleteTransfer,
                        enabled = connected,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        SecondaryCallActionButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            stringResource(if (merged) R.string.remove_from_call else R.string.return_to_caller),
            onReturnToCaller,
        )
    }
}

@Composable
private fun CallOptionsGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GlassCard)) { content() }
}

@Composable
private fun CallOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = AccentBlue,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PrimaryLight), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary)
            Text(subtitle, fontSize = 13.sp, color = CallTextSecondary)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = InactiveGray, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OptionDivider() {
    Spacer(Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp).background(BorderLight.copy(alpha = .55f)))
}

private data class Participant(val id: String, val name: String, val isSelf: Boolean = false, val muted: Boolean = false)

/**
 * Participant stage: one person fills the stage as a spotlight, two to four people share a 2-column
 * grid (scrolls beyond that). A blue ring marks whoever is speaking; a badge marks muted people.
 */
@Composable
private fun ConferenceStage(participants: List<Participant>, speakingIds: Set<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
        Text(
            stringResource(R.string.conference_title),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.conference_count, participants.size),
            fontSize = 14.sp, color = CallTextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp), textAlign = TextAlign.Center,
        )
        val spotlight = participants.firstOrNull { it.id in speakingIds && !it.isSelf } ?: participants.firstOrNull { !it.isSelf }
        if (participants.size > 2 && spotlight != null) {
            // Spotlight on the active (or first remote) speaker, everyone else in a strip below.
            ParticipantTile(spotlight, spotlight.id in speakingIds, Modifier.fillMaxWidth().height(170.dp), large = true)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                participants.filter { it != spotlight }.forEach {
                    ParticipantTile(it, it.id in speakingIds, Modifier.weight(1f).height(110.dp))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                participants.forEach { ParticipantTile(it, it.id in speakingIds, Modifier.weight(1f).height(150.dp)) }
            }
        }
    }
}

@Composable
private fun ParticipantTile(p: Participant, speaking: Boolean, modifier: Modifier = Modifier, large: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(GlassCard)
            .border(if (speaking) 3.dp else 1.dp, if (speaking) AccentBlue else BorderLight, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(if (large) 72.dp else 52.dp).clip(CircleShape).background(PrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(initialsOf(p.name), fontSize = if (large) 26.sp else 18.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (p.muted) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(AccentRed),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.MicOff, stringResource(R.string.mute), tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
    }
}

private fun initialsOf(name: String): String = name.split(' ', '.', '-', '_')
    .filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
