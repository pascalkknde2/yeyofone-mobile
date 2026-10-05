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
import com.yeyofone.app.ui.theme.KeypadBackground
import com.yeyofone.app.ui.theme.SuccessLight
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.PrimaryLight

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
        Modifier.fillMaxSize().background(Color.White).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.shadow(2.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)).background(SuccessLight)
                    .border(1.dp, AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Call, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.incoming_call_badge),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    letterSpacing = 1.sp,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).background(KeypadBackground).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PulsingRings()
                Box(
                    Modifier.size(156.dp).background(PrimaryLight, CircleShape).padding(8.dp)
                        .clip(CircleShape).background(Color.White)
                        .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(callerInitials(caller.displayName), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                caller.displayName,
                fontSize = 36.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.extension_value, caller.extension),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
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

        Column(
            Modifier.fillMaxWidth().background(KeypadBackground)
                .shadow(4.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
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
        1f, 1.15f,
        infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "first-ring-scale",
    )
    val firstAlpha by transition.animateFloat(
        0.2f, 0f,
        infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "first-ring-alpha",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(172.dp).scale(firstScale).background(AccentGreen.copy(alpha = firstAlpha), CircleShape))
    }
}

private fun callerInitials(name: String): String = name.split(' ', '.', '-', '_')
    .filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
