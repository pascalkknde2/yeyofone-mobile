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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.toSipIdentity
import com.yeyofone.app.ui.components.DialPad
import com.yeyofone.app.ui.components.PrimaryCallActionButton
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.CallBackground
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.MediaState
import kotlinx.coroutines.delay

/**
 * The in-call keypad as a dedicated screen, laid out like [TransferCallScreen], so the dial pad
 * no longer squeezes in between the caller identity and the call controls. Cancel (or system
 * back) returns to the call screen; the call itself keeps running underneath.
 */
@Composable
fun InCallKeypadScreen(
    session: CallSession,
    media: MediaState,
    onDtmf: (Char) -> Unit,
    onCancel: () -> Unit,
    onEndCall: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var tones by remember(session.id) { mutableStateOf("") }
    var elapsedSeconds by remember(session.id) { mutableLongStateOf(callElapsedSeconds(session)) }

    LaunchedEffect(session.connectedAt, session.state) {
        while (true) {
            elapsedSeconds = callElapsedSeconds(session)
            delay(1_000)
        }
    }

    Column(Modifier.fillMaxSize().background(CallBackground).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel), tint = CallTextPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(session.remoteUri.toSipIdentity().displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
                Text(
                    if (media.held) callStatus(CallState.Held) else if (session.connectedAt == null) callStatus(session.state) else formatDuration(elapsedSeconds),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CallTextSecondary,
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = tones,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = CallTextPrimary,
                letterSpacing = 1.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(44.dp),
            )
            if (media.held) {
                Text(stringResource(R.string.dtmf_unavailable_on_hold), color = AccentRed, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DialPad(
                    onNumberClick = {
                        if (!media.held) {
                            tones = (tones + it).takeLast(24)
                            onDtmf(it.first())
                        }
                    },
                    modifier = Modifier.widthIn(max = 288.dp).fillMaxWidth(),
                    rowSpacing = 16.dp,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(CallBackground, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PrimaryCallActionButton(
                icon = Icons.Default.Close,
                label = stringResource(R.string.cancel),
                backgroundColor = CallTextSecondary,
                onClick = onCancel,
            )
            PrimaryCallActionButton(
                icon = Icons.Default.CallEnd,
                label = stringResource(R.string.hang_up),
                backgroundColor = AccentRed,
                onClick = onEndCall,
            )
        }
    }
}
