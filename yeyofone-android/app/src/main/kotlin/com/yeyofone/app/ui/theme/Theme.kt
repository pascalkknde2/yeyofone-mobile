package com.yeyofone.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = YeyoBlue,
    surface = YeyoSurface,
    background = YeyoSurface,
    error = YeyoError,
)

private val DarkColors = darkColorScheme(
    primary = YeyoBlueDark,
    surface = YeyoSurfaceDark,
    background = YeyoSurfaceDark,
)

@Composable
fun YeyoFoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = YeyoTypography,
        content = content,
    )
}
