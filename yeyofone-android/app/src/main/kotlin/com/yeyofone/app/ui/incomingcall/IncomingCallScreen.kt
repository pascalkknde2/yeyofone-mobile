package com.yeyofone.app.ui.incomingcall

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.toSipIdentity
import com.yeyofone.app.ui.components.PrimaryCallActionButton
import com.yeyofone.app.ui.components.SecondaryCallActionButton
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.CallBackground
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary

@Composable
fun IncomingCallScreen(
    remoteUri: String,
    permissionDenied: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    actionsEnabled: Boolean = true,
    onMessage: (() -> Unit)? = null,
    onRemind: (() -> Unit)? = null,
) {
    val caller = remoteUri.toSipIdentity()
    Column(
        Modifier.fillMaxSize().background(CallBackground).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 48.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(AccentGreen.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Call, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.incoming_call_badge),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                PulsingRings()
                Box(
                    Modifier.size(140.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(Color(0xFFE4E8EC)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(callerInitials(caller.displayName), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                caller.displayName,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.extension_value, caller.extension),
                fontSize = 16.sp,
                color = CallTextSecondary,
                textAlign = TextAlign.Center,
            )
            if (permissionDenied) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.microphone_permission_required),
                    color = AccentRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(60.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryCallActionButton(
                    Icons.Default.CallEnd,
                    stringResource(R.string.decline),
                    AccentRed,
                    onDecline,
                    enabled = actionsEnabled,
                )
                PrimaryCallActionButton(
                    Icons.Default.Call,
                    stringResource(R.string.accept),
                    AccentGreen,
                    onAccept,
                    enabled = actionsEnabled,
                )
            }
            if (onMessage != null || onRemind != null) {
                Spacer(Modifier.height(42.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    onMessage?.let {
                        SecondaryCallActionButton(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.message), it)
                    }
                    onRemind?.let {
                        SecondaryCallActionButton(Icons.Outlined.NotificationsActive, stringResource(R.string.remind_me), it)
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingRings() {
    val transition = rememberInfiniteTransition(label = "incoming-call-pulse")
    val firstScale by transition.animateFloat(
        0.92f, 1.28f,
        infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "first-ring-scale",
    )
    val firstAlpha by transition.animateFloat(
        0.7f, 0f,
        infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "first-ring-alpha",
    )
    val secondScale by transition.animateFloat(
        0.92f, 1.28f,
        infiniteRepeatable(tween(2_000, delayMillis = 650, easing = LinearEasing), RepeatMode.Restart),
        label = "second-ring-scale",
    )
    val secondAlpha by transition.animateFloat(
        0.7f, 0f,
        infiniteRepeatable(tween(2_000, delayMillis = 650, easing = LinearEasing), RepeatMode.Restart),
        label = "second-ring-alpha",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(140.dp).scale(firstScale).border(2.dp, AccentGreen.copy(alpha = firstAlpha), CircleShape))
        Box(Modifier.size(140.dp).scale(secondScale).border(2.dp, AccentGreen.copy(alpha = secondAlpha), CircleShape))
    }
}

private fun callerInitials(name: String): String = name.split(' ', '.', '-', '_')
    .filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
