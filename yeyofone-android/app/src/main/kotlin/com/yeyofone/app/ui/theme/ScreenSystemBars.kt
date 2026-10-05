package com.yeyofone.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/** Matches the system chrome to the screen while retaining safe space for system icons. */
@Composable
fun ScreenSystemBars(background: Color) {
    val context = LocalContext.current
    DisposableEffect(context, background) {
        val window = context.findActivity()?.window
        val previousStatus = window?.statusBarColor
        val previousNavigation = window?.navigationBarColor
        if (window != null) {
            window.statusBarColor = background.toArgb()
            // API 26 only supports light navigation icons, which need a dark background.
            window.navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                background.toArgb()
            } else {
                Color(0xFF0F172A).toArgb()
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        onDispose {
            if (window != null && previousStatus != null && previousNavigation != null) {
                window.statusBarColor = previousStatus
                window.navigationBarColor = previousNavigation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
