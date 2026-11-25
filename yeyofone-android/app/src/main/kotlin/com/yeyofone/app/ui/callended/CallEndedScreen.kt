package com.yeyofone.app.ui.callended

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yeyofone.app.ai.NotesState
import com.yeyofone.app.ui.theme.AccentBlue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.CallSummary
import com.yeyofone.app.ui.components.PrimaryCallActionButton
import com.yeyofone.app.ui.components.SecondaryCallActionButton
import com.yeyofone.app.ui.theme.Palette
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.GlassCard
import com.yeyofone.core.model.CallDirection

@Composable
fun CallEndedScreen(
    summary: CallSummary,
    notes: NotesState? = null,
    onCallAgain: () -> Unit,
    onSendMessage: (() -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val failed = summary.mediaRejected || (summary.direction == CallDirection.INCOMING && !summary.wasAnswered)
    val accent = if (failed) AccentRed else AccentGreen

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = CallTextPrimary)
            }
            StatusBadge(summary, accent)
            Spacer(Modifier.size(48.dp))
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(136.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f))
                    .border(2.dp, accent.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(summary.callerName.initials(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                summary.callerName,
                fontSize = 32.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary,
                textAlign = TextAlign.Center,
            )
            if (summary.callerNumber.isNotBlank() && summary.callerNumber != summary.callerName) {
                Spacer(Modifier.height(4.dp))
                Text(summary.callerNumber, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CallTextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Text(callTypeLabel(summary), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CallTextSecondary)
            }
            if (summary.mediaRejected) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.call_failed_media),
                    fontSize = 13.sp,
                    color = AccentRed,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.1f))
                    .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                DetailColumn(stringResource(R.string.duration), summary.formattedDuration)
                Box(Modifier.width(1.dp).height(32.dp).background(accent.copy(alpha = 0.25f)))
                DetailColumn(stringResource(R.string.time), summary.timestamp)
            }
            Spacer(Modifier.height(24.dp))
            if (notes != null) {
                NotesCard(notes)
                Spacer(Modifier.height(24.dp))
            }
        }

        // No surrounding card: the two actions sit side by side on the screen background.
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            PrimaryCallActionButton(Icons.Default.Call, stringResource(R.string.call_again), AccentGreen, onCallAgain)
            if (onSendMessage != null) {
                PrimaryCallActionButton(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.send_message), AccentBlue, onSendMessage)
            }
        }
    }
}

@Composable
private fun NotesCard(notes: NotesState) {
    var showTranscript by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GlassCard)
            .border(1.dp, BorderLight, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.ai_notes_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
        }
        when (notes) {
            is NotesState.Preparing -> Text(
                notes.percent?.let { stringResource(R.string.ai_notes_preparing, it) } ?: stringResource(R.string.ai_notes_working),
                fontSize = 13.sp, color = CallTextSecondary,
            )
            NotesState.Transcribing -> Text(stringResource(R.string.ai_notes_transcribing), fontSize = 13.sp, color = CallTextSecondary)
            is NotesState.Failed -> Text(stringResource(R.string.ai_notes_failed, notes.reason), fontSize = 13.sp, color = AccentRed)
            is NotesState.Ready -> {
                val result = notes.notes
                if (result.isEmpty) {
                    Text(stringResource(R.string.ai_notes_empty), fontSize = 13.sp, color = CallTextSecondary)
                } else {
                    if (result.summary.isNotBlank()) {
                        NotesHeading(R.string.ai_notes_summary)
                        Text(result.summary, fontSize = 14.sp, color = CallTextPrimary)
                    }
                    if (result.actionItems.isNotEmpty()) {
                        NotesHeading(R.string.ai_notes_actions)
                        result.actionItems.forEach { Text("\u2022 $it", fontSize = 14.sp, color = CallTextPrimary) }
                    }
                    Text(
                        stringResource(if (showTranscript) R.string.ai_notes_hide_transcript else R.string.ai_notes_show_transcript),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue,
                        modifier = Modifier.clickable { showTranscript = !showTranscript },
                    )
                    if (showTranscript) Text(result.transcript, fontSize = 13.sp, color = CallTextSecondary)
                    Text(stringResource(R.string.ai_notes_disclaimer), fontSize = 11.sp, color = CallTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun NotesHeading(@androidx.annotation.StringRes id: Int) {
    Text(stringResource(id).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CallTextSecondary, letterSpacing = 0.5.sp)
}

@Composable
private fun StatusBadge(summary: CallSummary, accent: Color) {
    val label = when {
        summary.direction == CallDirection.INCOMING && !summary.wasAnswered -> stringResource(R.string.missed_call)
        else -> stringResource(R.string.call_ended_badge)
    }
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.CallEnd, null, tint = accent, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun callTypeLabel(summary: CallSummary): String = when {
    summary.direction == CallDirection.INCOMING && !summary.wasAnswered -> stringResource(R.string.missed_call)
    summary.direction == CallDirection.INCOMING -> stringResource(R.string.incoming_call)
    else -> stringResource(R.string.outgoing_call)
}

@Composable
private fun DetailColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CallTextSecondary, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary, fontFamily = FontFamily.Monospace)
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
