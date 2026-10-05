package com.yeyofone.app.ui.dialpad

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeyofone.app.R
import com.yeyofone.app.ui.components.DialPad
import com.yeyofone.app.ui.components.DialPadTopBar
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.KeypadBackground

@Composable
fun DialPadScreen(
    viewModel: DialPadViewModel,
    onNavigateBack: () -> Unit,
    onCall: (String) -> Unit,
    onAddContact: () -> Unit = {},
    onMoreOptions: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.numberToCall()?.let(onCall)
    }

    com.yeyofone.app.ui.theme.ScreenSystemBars(KeypadBackground)
    BoxWithConstraints(Modifier.fillMaxSize().background(KeypadBackground).systemBarsPadding()) {
        val contentHeight = maxOf(maxHeight, 680.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().height(contentHeight)) {
                DialPadTopBar(onNavigateBack, onAddContact, onMoreOptions)
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.typedNumber.ifEmpty { stringResource(R.string.enter_number) },
                        fontSize = if (uiState.isNumberEmpty) 16.sp else 32.sp,
                        fontWeight = if (uiState.isNumberEmpty) FontWeight.Normal else FontWeight.Medium,
                        color = if (uiState.isNumberEmpty) InactiveGray else CallTextPrimary,
                        letterSpacing = if (uiState.isNumberEmpty) 0.sp else 1.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (uiState.canBackspace) {
                        IconButton(onClick = viewModel::onBackspacePressed, modifier = Modifier.size(48.dp).clip(CircleShape)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Backspace,
                                stringResource(R.string.backspace),
                                tint = CallTextSecondary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    } else Spacer(Modifier.height(48.dp))
                    if (permissionDenied) {
                        Text(
                            stringResource(R.string.microphone_permission_required),
                            color = AccentRed,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    DialPad(viewModel::onNumberPressed, Modifier.widthIn(max = 288.dp).fillMaxWidth(), rowSpacing = 16.dp)
                }
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 40.dp, top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!uiState.isNumberEmpty) CallPulse()
                    IconButton(
                        onClick = {
                            val destination = viewModel.numberToCall() ?: return@IconButton
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                permissionDenied = false
                                onCall(destination)
                            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = !uiState.isNumberEmpty,
                        modifier = Modifier.size(72.dp)
                            .shadow(8.dp, CircleShape, ambientColor = AccentGreen.copy(alpha = 0.3f), spotColor = AccentGreen.copy(alpha = 0.3f))
                            .clip(CircleShape).background(AccentGreen),
                    ) {
                        Icon(Icons.Outlined.Call, stringResource(R.string.call), tint = Color.White.copy(alpha = if (uiState.isNumberEmpty) 0.65f else 1f), modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CallPulse() {
    val transition = rememberInfiniteTransition(label = "call ready")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart),
        label = "call halo",
    )
    Box(Modifier.size(72.dp).graphicsLayer {
        scaleX = 1f + 0.3f * progress
        scaleY = scaleX
        alpha = 0.3f * (1f - progress)
    }.background(AccentGreen, CircleShape))
}
