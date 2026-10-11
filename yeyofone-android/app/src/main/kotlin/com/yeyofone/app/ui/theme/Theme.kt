package com.yeyofone.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Built on each call so it picks up the light or dark values of the palette in Color.kt.
private fun appColors(dark: Boolean): ColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = AccentBlue, onPrimary = Color.White,
    primaryContainer = PrimaryLight, onPrimaryContainer = AccentBlue,
    secondary = AccentGreen, onSecondary = Color.White,
    secondaryContainer = SuccessLight, onSecondaryContainer = TextPrimary,
    tertiary = AccentOrange, onTertiary = Color.White,
    tertiaryContainer = OrangeLight, onTertiaryContainer = TextPrimary,
    background = BackgroundGray, onBackground = TextPrimary,
    surface = CardWhite, onSurface = TextPrimary,
    surfaceVariant = BackgroundGray, onSurfaceVariant = TextSecondary,
    surfaceTint = CardWhite,
    surfaceContainerLowest = CardWhite, surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite, surfaceContainerHigh = CardWhite, surfaceContainerHighest = CardWhite,
    outline = InactiveGray, outlineVariant = BorderLight,
    error = AccentRed, onError = Color.White,
    errorContainer = DangerLight, onErrorContainer = AccentRed,
    inverseSurface = TextPrimary, inverseOnSurface = TextOnPrimary,
)

/** Resolves the Appearance setting against the device's dark-mode setting. */
@Composable
fun isAppInDarkTheme(): Boolean = when (AppearancePreferences.current) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable
fun YeyoFoneTheme(content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme()
    // Set during composition so the palette read by this frame already matches.
    Palette.isDark = dark
    MaterialTheme(
        colorScheme = appColors(dark),
        typography = YeyoTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
