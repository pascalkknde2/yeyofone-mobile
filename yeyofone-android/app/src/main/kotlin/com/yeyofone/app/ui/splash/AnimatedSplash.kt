package com.yeyofone.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val SplashBlue = Color(0xFF2A4CE5)

/**
 * The opening animation after the system splash: the bird pops in, "YeyoFone" rises under it, then
 * the whole screen fades away. It blocks touches while it plays and calls [onFinished] when done.
 */
@Composable
fun AnimatedSplash(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.6f) }
    val textAlpha = remember { Animatable(0f) }
    val textRise = remember { Animatable(24f) }
    val screenAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(420)) }
            launch { logoScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
            launch {
                kotlinx.coroutines.delay(380)
                launch { textAlpha.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
                launch { textRise.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
            }
        }
        kotlinx.coroutines.delay(750)
        screenAlpha.animateTo(0f, tween(380))
        onFinished()
    }

    Column(
        Modifier.fillMaxSize()
            .alpha(screenAlpha.value)
            .background(SplashBlue)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_bird),
            contentDescription = null,
            modifier = Modifier.width(128.dp).height(122.dp).graphicsLayer {
                alpha = logoAlpha.value
                scaleX = logoScale.value
                scaleY = logoScale.value
            },
        )
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.splash_app_name),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.graphicsLayer {
                alpha = textAlpha.value
                translationY = textRise.value * density
            },
        )
    }
}
