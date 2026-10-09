package com.yeyofone.app.ui.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.toSipIdentity
import com.yeyofone.app.ui.components.DialPad
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.CallBackground
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.MediaState
import java.time.Instant
import kotlin.math.max
import kotlinx.coroutines.delay

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
    onOpenTransfer: () -> Unit,
    onOpenMore: () -> Unit,
    onEndCall: () -> Unit,
) {
    var keypadOpen by remember(session.id) { mutableStateOf(false) }
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
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = CallTextPrimary)
            }
            IconButton(onClick = { onHoldChange(!media.held) }) {
                Icon(if (media.held) Icons.Default.PlayArrow else Icons.Default.Pause, stringResource(if (media.held) R.string.resume else R.string.hold), tint = if (media.held) AccentBlue else CallTextPrimary)
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CallerIdentity(
                name = session.remoteUri.toSipIdentity().displayName,
                status = if (media.held) callStatus(CallState.Held) else if (session.connectedAt == null) callStatus(session.state) else formatDuration(elapsedSeconds),
            )

            if (keypadOpen) {
                DialPad(
                    onNumberClick = { if (!media.held) onDtmf(it.first()) },
                    modifier = Modifier.padding(horizontal = 56.dp),
                )
                if (media.held) Text(stringResource(R.string.dtmf_unavailable_on_hold), color = AccentRed, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(CallBackground, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallActionButton(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.speaker), speakerOn) {
                    onSpeakerChange(!speakerOn)
                }
                CallActionButton(Icons.Default.Dialpad, stringResource(R.string.keypad), keypadOpen) {
                    keypadOpen = !keypadOpen
                }
                CallActionButton(if (media.muted) Icons.Default.MicOff else Icons.Default.Mic, stringResource(R.string.mute), media.muted, enabled = !media.held) {
                    onMuteChange(!media.muted)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallActionButton(Icons.Default.SwapHoriz, stringResource(R.string.transfer), enabled = !media.held) {
                    onOpenTransfer()
                }
                CallActionButton(Icons.Default.CallEnd, stringResource(R.string.hang_up), destructive = true) {
                    onEndCall()
                }
                CallActionButton(Icons.Default.MoreHoriz, stringResource(R.string.more), media.held) {
                    onOpenMore()
                }
            }
        }
    }

}

@Composable
private fun CallerIdentity(name: String, status: String) {
    val pulse = rememberInfiniteTransition(label = "call-avatar-pulse")
    val pulseScale by pulse.animateFloat(
        1f, 1.1f,
        infiniteRepeatable(tween(1_000), RepeatMode.Reverse),
        label = "call-avatar-scale",
    )
    val pulseAlpha by pulse.animateFloat(
        0.3f, 0.1f,
        infiniteRepeatable(tween(1_000), RepeatMode.Reverse),
        label = "call-avatar-alpha",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(136.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(136.dp).scale(pulseScale).background(AccentGreen.copy(alpha = pulseAlpha), CircleShape))
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(Color.White)
                    .border(1.dp, AccentGreen.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(name), fontSize = 48.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(name, fontSize = 32.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Bold, color = CallTextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).shadow(4.dp, CircleShape, ambientColor = AccentGreen, spotColor = AccentGreen).background(AccentGreen, CircleShape))
            Text(status, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CallTextSecondary)
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        destructive -> AccentRed
        active -> PrimaryLight
        else -> Color.White
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { if (!destructive) selected = active }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 5.dp),
    ) {
        Box(
            Modifier.size(64.dp)
                .shadow(if (destructive) 10.dp else 3.dp, CircleShape,
                    ambientColor = if (destructive) AccentRed.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.05f),
                    spotColor = if (destructive) AccentRed.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.05f))
                .clip(CircleShape)
                .background(if (enabled) background else background.copy(alpha = 0.45f))
                .border(1.dp, if (destructive) AccentRed else if (active) Color(0xFFBFDBFE) else Color(0xFFE2E8F0), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (destructive) Color.White else (if (active) AccentBlue else CallTextPrimary).copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(label.uppercase(), fontSize = 12.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold, color = CallTextSecondary)
    }
}

internal fun formatDuration(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun callElapsedSeconds(session: CallSession): Long =
    session.connectedAt?.let { max(0, Instant.now().epochSecond - it.epochSecond) } ?: 0

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
