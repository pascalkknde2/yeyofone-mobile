package com.yeyofone.app.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.components.DialPad
import com.yeyofone.app.ui.components.PrimaryCallActionButton
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.CallBackground
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.InactiveGray

@Composable
fun TransferCallScreen(
    currentCallerName: String,
    onCancel: () -> Unit,
    onTransfer: (String) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var destination by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(CallBackground).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel), tint = CallTextPrimary)
            }
            Text(stringResource(R.string.transfer), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
            Spacer(Modifier.size(48.dp))
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.transfer_call_subtitle, currentCallerName),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = CallTextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = destination.ifEmpty { stringResource(R.string.transfer_destination) },
                fontSize = if (destination.isEmpty()) 16.sp else 32.sp,
                fontWeight = if (destination.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                color = if (destination.isEmpty()) InactiveGray else CallTextPrimary,
                letterSpacing = if (destination.isEmpty()) 0.sp else 1.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            if (destination.isNotEmpty()) {
                IconButton(onClick = { destination = destination.dropLast(1) }, modifier = Modifier.size(48.dp).clip(CircleShape)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Backspace,
                        stringResource(R.string.backspace),
                        tint = CallTextSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DialPad(onNumberClick = { destination += it }, modifier = Modifier.widthIn(max = 288.dp).fillMaxWidth(), rowSpacing = 16.dp)
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(CallBackground, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryCallActionButton(
                icon = Icons.Outlined.Call,
                label = stringResource(R.string.transfer_now),
                backgroundColor = AccentBlue,
                onClick = { onTransfer(destination) },
                enabled = destination.isNotBlank(),
            )
        }
    }
}
