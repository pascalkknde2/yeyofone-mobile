package com.yeyofone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yeyofone.app.R
import com.yeyofone.app.data.model.CallLog
import com.yeyofone.app.data.model.CallType
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CallHistoryItem(
    call: CallLog,
    canCallBack: Boolean,
    onCallClick: () -> Unit,
    onVoicemailClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = canCallBack, onClick = onCallClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CallAvatar(call)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    call.contactName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (call.callType == CallType.MISSED) AccentRed else TextPrimary,
                )
                Text(call.metadata(), fontSize = 13.sp, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (call.hasVoicemail) {
                    ActionButton(
                        Icons.Default.Voicemail,
                        stringResource(R.string.play_voicemail, call.contactName),
                        onVoicemailClick,
                    )
                }
                ActionButton(
                    Icons.Default.Call,
                    stringResource(R.string.call_contact, call.contactName),
                    onCallClick,
                    isPrimary = true,
                    enabled = canCallBack,
                )
            }
        }
    }
}

@Composable
private fun CallAvatar(call: CallLog) {
    Box {
        if (call.contactImageUrl != null) {
            AsyncImage(
                model = call.contactImageUrl,
                contentDescription = call.contactName,
                modifier = Modifier.size(46.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(BackgroundGray),
                contentAlignment = Alignment.Center,
            ) {
                Text(call.contactName.initials(), fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
        val (badgeColor, badgeIcon) = when (call.callType) {
            CallType.INCOMING -> AccentGreen to Icons.AutoMirrored.Outlined.CallReceived
            CallType.OUTGOING -> AccentBlue to Icons.AutoMirrored.Outlined.CallMade
            CallType.MISSED -> AccentRed to Icons.AutoMirrored.Outlined.CallMissed
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(CardWhite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(badgeIcon, null, tint = badgeColor, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
) {
    val background = if (isPrimary) AccentGreen.copy(alpha = 0.1f) else BackgroundGray
    val foreground = if (isPrimary) AccentGreen else TextPrimary
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp).clip(CircleShape).background(background),
    ) {
        Icon(icon, contentDescription, tint = foreground, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CallLog.metadata(): String {
    val type = stringResource(
        when (callType) {
            CallType.INCOMING -> R.string.incoming_call
            CallType.OUTGOING -> R.string.outgoing_call
            CallType.MISSED -> R.string.missed_call
        },
    )
    val time = CallTimeFormatter.format(timestamp)
    val formattedDuration = duration?.formatted()
    return if (formattedDuration == null) stringResource(R.string.call_metadata, type, time)
    else stringResource(R.string.call_metadata_with_duration, type, time, formattedDuration)
}

@Composable
private fun Duration.formatted(): String = if (seconds >= 60) {
    stringResource(R.string.call_duration_minutes_seconds, seconds / 60, seconds % 60)
} else stringResource(R.string.call_duration_seconds, seconds)

private fun String.initials(): String = split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifEmpty { "?" }

private val CallTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
