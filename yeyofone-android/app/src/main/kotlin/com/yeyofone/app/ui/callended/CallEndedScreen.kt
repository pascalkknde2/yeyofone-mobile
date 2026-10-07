package com.yeyofone.app.ui.callended

import androidx.activity.compose.BackHandler
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
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CallBackground
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.core.model.CallDirection

@Composable
fun CallEndedScreen(
    summary: CallSummary,
    onCallAgain: () -> Unit,
    onSendMessage: (() -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val failed = summary.mediaRejected || (summary.direction == CallDirection.INCOMING && !summary.wasAnswered)
    val accent = if (failed) AccentRed else AccentGreen

    Column(Modifier.fillMaxSize().background(CallBackground).systemBarsPadding()) {
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
                Modifier.size(136.dp).clip(CircleShape).background(Color.White)
                    .border(2.dp, accent.copy(alpha = 0.25f), CircleShape),
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
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .border(1.dp, BorderLight, RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                DetailColumn(stringResource(R.string.duration), summary.formattedDuration)
                Box(Modifier.width(1.dp).height(32.dp).background(BorderLight))
                DetailColumn(stringResource(R.string.time), summary.timestamp)
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryCallActionButton(Icons.Default.Call, stringResource(R.string.call_again), AccentGreen, onCallAgain)
            if (onSendMessage != null) {
                Spacer(Modifier.height(28.dp))
                SecondaryCallActionButton(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.send_message), onSendMessage)
            }
        }
    }
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
