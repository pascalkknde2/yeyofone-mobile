package com.yeyofone.app.ui.callended

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.CallSummary
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.CallDirection

@Composable
fun CallEndedScreen(
    summary: CallSummary,
    onCallAgain: () -> Unit,
    onSendMessage: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(BackgroundGray)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = TextPrimary)
            }
            Text(stringResource(R.string.call_ended_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.size(48.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0x0D000000))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CallSummaryCard(summary)
            CallEndedActions(onCallAgain, onSendMessage, onClose)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CallSummaryCard(summary: CallSummary) {
    Column(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(Color.White).padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(AccentRed.copy(alpha = 0.1f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.CallEnd, null, tint = AccentRed, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.call_ended_badge), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentRed)
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(100.dp).clip(CircleShape).background(Color(0xFFE4E8EC)), contentAlignment = Alignment.Center) {
            Text(summary.callerName.initials(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(16.dp))
        Text(summary.callerName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(callTypeLabel(summary), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(thickness = 1.dp, color = Color(0x0F000000))
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            DetailColumn(stringResource(R.string.duration), summary.formattedDuration, Modifier.weight(1f))
            DetailColumn(stringResource(R.string.time), summary.timestamp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun callTypeLabel(summary: CallSummary): String = when {
    summary.direction == CallDirection.INCOMING && !summary.wasAnswered -> stringResource(R.string.missed_call)
    summary.direction == CallDirection.INCOMING -> stringResource(R.string.incoming_call)
    else -> stringResource(R.string.outgoing_call)
}

@Composable
private fun DetailColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CallEndedActions(onCallAgain: () -> Unit, onSendMessage: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EndedAction(Icons.Default.Call, stringResource(R.string.call_again), Color(0xFF1A1A1A), Color.White, 6.dp, onCallAgain)
        EndedAction(Icons.Default.ChatBubble, stringResource(R.string.send_message), Color.White, TextPrimary, 2.dp, onSendMessage)
        EndedAction(Icons.Default.Close, stringResource(R.string.close), AccentRed.copy(alpha = 0.1f), AccentRed, 0.dp, onClose)
    }
}

@Composable
private fun EndedAction(icon: ImageVector, label: String, background: Color, foreground: Color, elevation: Dp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(elevation, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(background).clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = foreground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = foreground)
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
