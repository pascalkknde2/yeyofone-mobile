package com.yeyofone.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Whether the dark palette is showing. YeyoFoneTheme sets it from the Appearance setting; it is
 * snapshot state, so every composable or draw block that reads a color below redraws on change.
 */
object Palette {
    var isDark by mutableStateOf(false)
}

private fun pick(light: Long, dark: Long) = Color(if (Palette.isDark) dark else light)

// The supplied light design, with a slate-based dark counterpart for each color.
val BackgroundGray get() = pick(0xFFF4F7F9, 0xFF0B1120)
val KeypadBackground get() = pick(0xFFF8FAFC, 0xFF0F172A)
val CardWhite get() = pick(0xFFFFFFFF, 0xFF1E293B)
val TextPrimary get() = pick(0xFF0F172A, 0xFFF1F5F9)
/** Text on a [TextPrimary] fill: white in light mode, dark slate in dark mode. */
val TextOnPrimary get() = pick(0xFFFFFFFF, 0xFF0F172A)
val TextSecondary get() = pick(0xFF64748B, 0xFF94A3B8)
val InactiveGray get() = pick(0xFF94A3B8, 0xFF64748B)
val AccentBlue get() = pick(0xFF2563EB, 0xFF3B82F6)
val PrimaryLight get() = pick(0xFFEFF6FF, 0xFF1E3A5F)
val PrimaryBorder get() = pick(0xFFBFDBFE, 0xFF1D4ED8)
val AccentGreen get() = pick(0xFF10B981, 0xFF34D399)
val SuccessLight get() = pick(0xFFECFDF5, 0xFF064E3B)
val AccentRed get() = pick(0xFFEF4444, 0xFFF87171)
val DangerLight get() = pick(0xFFFEF2F2, 0xFF450A0A)
val AccentOrange get() = pick(0xFFF97316, 0xFFFB923C)
val OrangeLight get() = pick(0xFFFFF7ED, 0xFF431407)
val BorderLight get() = pick(0xFFE2E8F0, 0xFF334155)
val RadioBorder get() = pick(0xFFCBD5E1, 0xFF475569)
val CardGradientStart get() = pick(0xFF1E293B, 0xFF334155)
val CardGradientEnd get() = pick(0xFF0F172A, 0xFF1E293B)
val CardAccent = Color(0xFF3B82F6)
val Purple get() = pick(0xFF8B5CF6, 0xFFA78BFA)
val PurpleLight get() = pick(0xFFF5F3FF, 0xFF2E1065)
val Pink get() = pick(0xFFDB2777, 0xFFF472B6)
val PinkLight get() = pick(0xFFFDF2F8, 0xFF500724)
val CheckGreen get() = pick(0xFF22C55E, 0xFF4ADE80)
val NavBackground get() = CardWhite
/** Unselected bottom navigation icons: gray in light mode, white in dark mode. */
val NavInactive get() = pick(0xFF94A3B8, 0xFFFFFFFF)
val CallBackground get() = BackgroundGray
val CallTextPrimary get() = TextPrimary
val CallTextSecondary get() = TextSecondary
val YeyoBlue get() = AccentBlue
val YeyoSurface get() = BackgroundGray
val YeyoError get() = AccentRed
