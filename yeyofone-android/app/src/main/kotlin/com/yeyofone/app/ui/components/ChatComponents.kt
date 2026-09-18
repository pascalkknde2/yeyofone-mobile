package com.yeyofone.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.ChatContact
import com.yeyofone.app.data.model.ChatMessage
import com.yeyofone.app.data.model.MessageStatus
import com.yeyofone.app.data.model.MessageType
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary

@Composable
fun ChatHeader(contact: ChatContact, onBack: () -> Unit, onVoiceCall: () -> Unit, onVideoCall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary)
        }
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE0EAFF)), contentAlignment = Alignment.Center) {
                Text(contact.name.initials(), fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            if (contact.isOnline) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                        .background(AccentGreen).border(2.dp, Color.White, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contact.statusText, fontSize = 12.sp, color = if (contact.isOnline) AccentGreen else TextSecondary)
        }
        IconButton(onClick = onVoiceCall) { Icon(Icons.Default.Call, stringResource(R.string.voice_call), tint = TextPrimary) }
        IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, stringResource(R.string.video_call), tint = TextPrimary) }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, onPlayVoice: () -> Unit) {
    val outgoing = message.isOutgoing
    val bubble = if (outgoing) AccentBlue else Color.White
    val foreground = if (outgoing) Color.White else TextPrimary
    val meta = if (outgoing) Color.White.copy(alpha = 0.78f) else Color(0xFF8E8E93)
    val shape = if (outgoing) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.widthIn(max = 280.dp).shadow(if (outgoing) 4.dp else 2.dp, shape)
                .clip(shape).background(bubble).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (message.type == MessageType.VOICE) {
                VoiceMessage(message, foreground, onPlayVoice)
            } else {
                Text(message.content, fontSize = 15.sp, lineHeight = 20.sp, color = foreground)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(message.timestamp, fontSize = 10.sp, color = meta)
                if (outgoing) {
                    Icon(
                        if (message.status == MessageStatus.READ || message.status == MessageStatus.DELIVERED) Icons.Default.DoneAll else Icons.Default.Done,
                        stringResource(R.string.message_status), tint = meta, modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceMessage(message: ChatMessage, foreground: Color, onPlay: () -> Unit) {
    Row(
        Modifier.widthIn(min = 190.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (message.isOutgoing) Color.White.copy(alpha = 0.22f) else AccentBlue.copy(alpha = 0.14f))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.play_voice_message), tint = if (message.isOutgoing) Color.White else AccentBlue)
        }
        Row(Modifier.weight(1f).height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            message.waveformHeights.forEach { barHeight ->
                Box(
                    Modifier.width(3.dp).height(barHeight.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (message.isOutgoing) Color.White.copy(alpha = 0.55f) else AccentBlue.copy(alpha = 0.45f)),
                )
            }
        }
        Text(message.content, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = foreground)
    }
}

@Composable
fun TypingIndicator() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.shadow(2.dp, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { TypingDot(it * 180) }
        }
    }
}

@Composable
private fun TypingDot(delay: Int) {
    val transition = rememberInfiniteTransition(label = "typing")
    val offset by transition.animateFloat(
        0f, -5f,
        infiniteRepeatable(
            keyframes { durationMillis = 1_200; 0f at 0 using LinearEasing; -5f at 250 using LinearEasing; 0f at 500 using LinearEasing },
            RepeatMode.Restart,
            StartOffset(delay),
        ),
        label = "typing-dot",
    )
    Box(Modifier.offset(y = offset.dp).size(7.dp).clip(CircleShape).background(Color(0xFFC7C7CC)))
}

@Composable
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onAttach: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = onAttach,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF0F0F5)),
        ) { Icon(Icons.Default.AttachFile, stringResource(R.string.attach_file), tint = TextSecondary) }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF0F0F5))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (text.isEmpty()) Text(stringResource(R.string.type_message), color = Color(0xFF8E8E93), fontSize = 15.sp)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary, lineHeight = 20.sp),
                cursorBrush = SolidColor(AccentBlue),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 5,
            )
        }
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (text.isNotBlank()) AccentBlue else Color(0xFFC7C7CC)),
        ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send_message), tint = Color.White) }
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
