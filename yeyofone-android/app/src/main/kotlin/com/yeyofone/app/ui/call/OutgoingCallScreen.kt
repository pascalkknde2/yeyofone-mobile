package com.yeyofone.app.ui.call

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.components.DialPad
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.CallBackground
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.MediaState
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.max

@Composable
fun OutgoingCallScreen(
    session: CallSession,
    media: MediaState,
    speakerOn: Boolean,
    onNavigateBack: () -> Unit,
    onSpeakerChange: (Boolean) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onHoldChange: (Boolean) -> Unit,
    onDtmf: (Char) -> Unit,
    onTransfer: (String) -> Unit,
    onEndCall: () -> Unit,
) {
    var keypadOpen by remember(session.id) { mutableStateOf(false) }
    var transferOpen by remember(session.id) { mutableStateOf(false) }
    var transferDestination by remember(session.id) { mutableStateOf("") }
    var elapsedSeconds by remember(session.id) { mutableLongStateOf(callElapsedSeconds(session)) }

    LaunchedEffect(session.connectedAt, session.state) {
        while (true) {
            elapsedSeconds = callElapsedSeconds(session)
            delay(1_000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(CallBackground).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = CallTextPrimary)
            }
            IconButton(onClick = { onHoldChange(!media.held) }) {
                Icon(Icons.Default.Pause, stringResource(if (media.held) R.string.resume else R.string.hold), tint = CallTextPrimary)
            }
        }

        CallerIdentity(
            name = displayName(session.remoteUri),
            status = if (session.connectedAt == null) callStatus(session.state) else formatDuration(elapsedSeconds),
        )

        Spacer(Modifier.weight(1f))

        if (keypadOpen) {
            DialPad(
                onNumberClick = { if (!media.held) onDtmf(it.first()) },
                modifier = Modifier.padding(horizontal = 56.dp),
            )
            if (media.held) Text(stringResource(R.string.dtmf_unavailable_on_hold), color = AccentRed, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallActionButton(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.speaker), speakerOn) {
                    onSpeakerChange(!speakerOn)
                }
                CallActionButton(Icons.Default.Dialpad, stringResource(R.string.keypad), keypadOpen) {
                    keypadOpen = !keypadOpen
                }
                CallActionButton(Icons.Default.MicOff, stringResource(R.string.mute), media.muted, enabled = !media.held) {
                    onMuteChange(!media.muted)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallActionButton(Icons.Default.SwapHoriz, stringResource(R.string.transfer), enabled = !media.held) {
                    transferOpen = true
                }
                CallActionButton(Icons.Default.CallEnd, stringResource(R.string.hang_up), destructive = true, size = 72) {
                    onEndCall()
                }
                CallActionButton(Icons.Default.MoreHoriz, stringResource(if (media.held) R.string.resume else R.string.hold), media.held) {
                    onHoldChange(!media.held)
                }
            }
        }
    }

    if (transferOpen) {
        AlertDialog(
            onDismissRequest = { transferOpen = false },
            title = { Text(stringResource(R.string.transfer)) },
            text = {
                OutlinedTextField(
                    value = transferDestination,
                    onValueChange = { transferDestination = it },
                    label = { Text(stringResource(R.string.transfer_destination)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = transferDestination.isNotBlank(),
                    onClick = {
                        onTransfer(transferDestination.trim())
                        transferOpen = false
                    },
                ) { Text(stringResource(R.string.transfer_now)) }
            },
            dismissButton = { TextButton(onClick = { transferOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CallerIdentity(name: String, status: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(142.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(130.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color(0xFFE4E8EC)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(name), fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(name, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(status, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CallTextSecondary)
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    size: Int = 64,
    onClick: () -> Unit,
) {
    val background = when {
        destructive -> AccentRed
        active -> Color(0xFFE1E5E9)
        else -> Color.White
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 5.dp),
    ) {
        Box(
            Modifier.size(size.dp)
                .shadow(if (destructive) 8.dp else 3.dp, CircleShape)
                .clip(CircleShape)
                .background(if (enabled) background else background.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                label,
                tint = if (destructive) Color.White else CallTextPrimary.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(if (destructive) 30.dp else 25.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CallTextSecondary)
    }
}

internal fun formatDuration(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun callElapsedSeconds(session: CallSession): Long =
    session.connectedAt?.let { max(0, Instant.now().epochSecond - it.epochSecond) } ?: 0

private fun displayName(remoteUri: String): String = remoteUri
    .removePrefix("sip:")
    .substringBefore('@')
    .substringBefore(';')
    .takeIf { it.isNotBlank() }
    ?: remoteUri

private fun initials(name: String): String = name.split(' ', '.', '-', '_')
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }

private fun callStatus(state: CallState): String = when (state) {
    CallState.Preparing -> "Preparing…"
    CallState.Calling -> "Calling…"
    CallState.EarlyMedia, CallState.Ringing -> "Ringing…"
    CallState.Answering, CallState.Connecting -> "Connecting…"
    CallState.Connected -> "Connected"
    CallState.Held -> "On hold"
    CallState.Transferring -> "Transferring…"
    CallState.Disconnecting -> "Ending…"
    is CallState.Disconnected -> "Call ended"
    is CallState.Failed -> "Call failed"
    CallState.Incoming -> "Incoming call"
}
