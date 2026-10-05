package com.yeyofone.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AppColors = lightColorScheme(
    primary = AccentBlue, onPrimary = CardWhite,
    primaryContainer = PrimaryLight, onPrimaryContainer = AccentBlue,
    secondary = AccentGreen, onSecondary = CardWhite,
    secondaryContainer = SuccessLight, onSecondaryContainer = TextPrimary,
    tertiary = AccentOrange, onTertiary = CardWhite,
    tertiaryContainer = OrangeLight, onTertiaryContainer = TextPrimary,
    background = BackgroundGray, onBackground = TextPrimary,
    surface = CardWhite, onSurface = TextPrimary,
    surfaceVariant = BackgroundGray, onSurfaceVariant = TextSecondary,
    surfaceTint = CardWhite,
    outline = InactiveGray, outlineVariant = BorderLight,
    error = AccentRed, onError = CardWhite,
    errorContainer = DangerLight, onErrorContainer = AccentRed,
    inverseSurface = CardGradientEnd, inverseOnSurface = CardWhite,
)

// The supplied design uses one consistent light palette on every screen.
@Composable
fun YeyoFoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
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
