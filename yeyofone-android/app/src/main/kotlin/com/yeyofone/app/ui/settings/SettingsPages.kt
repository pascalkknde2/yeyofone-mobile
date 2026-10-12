package com.yeyofone.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.label
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentOrange
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.Appearance
import com.yeyofone.app.ui.theme.AppearancePreferences
import com.yeyofone.app.ui.theme.CardGradientEnd
import com.yeyofone.app.ui.theme.CardGradientStart
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.Purple
import com.yeyofone.app.ui.theme.PurpleLight
import com.yeyofone.core.model.AudioRoute

// Settings pages that replaced popups (dialogs and "Coming soon" messages).

/** Settings › Appearance: System, Light or Dark, applied at once. */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val subtitles = mapOf(
        Appearance.SYSTEM to R.string.appearance_system_subtitle,
        Appearance.LIGHT to R.string.appearance_light_subtitle,
        Appearance.DARK to R.string.appearance_dark_subtitle,
    )
    SettingsPage(stringResource(R.string.settings_appearance), onBack) {
        item {
            MoreSection(stringResource(R.string.appearance_theme)) {
                Appearance.entries.forEachIndexed { index, option ->
                    if (index > 0) MoreDivider(start = 20.dp)
                    OptionRow(
                        title = stringResource(option.label),
                        subtitle = stringResource(subtitles.getValue(option)),
                        selected = option == AppearancePreferences.current,
                    ) { AppearancePreferences.set(context, option) }
                }
            }
        }
        item { PageNote(stringResource(R.string.appearance_note)) }
    }
}

/** Settings › Audio › Output device: where call audio plays. */
@Composable
fun OutputDeviceScreen(routes: List<AudioRoute>, selected: AudioRoute?, onSelect: (AudioRoute) -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.output_device), onBack) {
        item {
            MoreSection(stringResource(R.string.output_device_available)) {
                routes.forEachIndexed { index, route ->
                    if (index > 0) MoreDivider(start = 20.dp)
                    OptionRow(title = route.label(), selected = route == selected) { onSelect(route) }
                }
            }
        }
        item { PageNote(stringResource(R.string.output_device_note)) }
    }
}

@Composable
private fun HeroCard(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(CardGradientStart, CardGradientEnd)))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.White.copy(alpha = .9f), fontSize = 14.sp, lineHeight = 19.sp)
    }
}

private data class Feature(val icon: ImageVector, val tint: Color, val background: Color, @StringRes val title: Int, @StringRes val subtitle: Int)

@Composable
private fun FeatureList(@StringRes section: Int, features: List<Feature>) {
    MoreSection(stringResource(section)) {
        features.forEachIndexed { index, f ->
            if (index > 0) MoreDivider()
            MoreRow(f.icon, f.tint, f.background, stringResource(f.title), stringResource(f.subtitle))
        }
    }
}

/** Settings › Premium Features: what Premium adds. It isn't on sale yet, and the page says so. */
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.settings_premium), onBack) {
        item { HeroCard(Icons.Default.WorkspacePremium, stringResource(R.string.premium_title), stringResource(R.string.premium_intro)) }
        item {
            FeatureList(
                R.string.premium_section_features,
                listOf(
                    Feature(Icons.Default.GraphicEq, AccentBlue, PrimaryLight, R.string.premium_hd_title, R.string.premium_hd_subtitle),
                    Feature(Icons.Default.FiberManualRecord, AccentRed, AccentRed.copy(alpha = .12f), R.string.premium_recording_title, R.string.premium_recording_subtitle),
                    Feature(Icons.Default.AutoAwesome, Purple, PurpleLight, R.string.premium_ai_title, R.string.premium_ai_subtitle),
                    Feature(Icons.Default.Videocam, AccentGreen, AccentGreen.copy(alpha = .12f), R.string.premium_video_title, R.string.premium_video_subtitle),
                    Feature(Icons.Default.SupportAgent, AccentOrange, AccentOrange.copy(alpha = .12f), R.string.premium_support_title, R.string.premium_support_subtitle),
                ),
            )
        }
        item {
            MoreSection(stringResource(R.string.premium_section_plan)) {
                ValueRow(stringResource(R.string.premium_current_plan), stringResource(R.string.premium_plan_free))
            }
        }
        item { PageButton(stringResource(R.string.feature_coming_soon), enabled = false) {} }
        item { PageNote(stringResource(R.string.premium_note)) }
    }
}

/** Settings › Enterprise Sign In: explains work-account sign-in and offers the manual route meanwhile. */
@Composable
fun EnterpriseScreen(onAddAccount: () -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.settings_enterprise), onBack) {
        item { HeroCard(Icons.Default.Business, stringResource(R.string.enterprise_title), stringResource(R.string.enterprise_intro)) }
        item {
            FeatureList(
                R.string.enterprise_section_how,
                listOf(
                    Feature(Icons.Default.ManageAccounts, AccentBlue, PrimaryLight, R.string.enterprise_step_email_title, R.string.enterprise_step_email_subtitle),
                    Feature(Icons.Default.Lock, Purple, PurpleLight, R.string.enterprise_step_signin_title, R.string.enterprise_step_signin_subtitle),
                    Feature(Icons.Default.AutoAwesome, AccentGreen, AccentGreen.copy(alpha = .12f), R.string.enterprise_step_ready_title, R.string.enterprise_step_ready_subtitle),
                ),
            )
        }
        item { PageNote(stringResource(R.string.enterprise_note)) }
        item { PageButton(stringResource(R.string.enterprise_add_manually), onClick = onAddAccount) }
    }
}

/** Settings › Translate: how live translation will work, and a link to language settings. */
@Composable
fun TranslateScreen(onLanguage: () -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.settings_translate), onBack) {
        item { HeroCard(Icons.Default.Translate, stringResource(R.string.translate_title), stringResource(R.string.translate_intro)) }
        item {
            FeatureList(
                R.string.translate_section_how,
                listOf(
                    Feature(Icons.Default.ClosedCaption, AccentBlue, PrimaryLight, R.string.translate_captions_title, R.string.translate_captions_subtitle),
                    Feature(Icons.Default.RecordVoiceOver, Purple, PurpleLight, R.string.translate_voice_title, R.string.translate_voice_subtitle),
                    Feature(Icons.AutoMirrored.Filled.Notes, AccentGreen, AccentGreen.copy(alpha = .12f), R.string.translate_notes_title, R.string.translate_notes_subtitle),
                ),
            )
        }
        item {
            MoreSection(stringResource(R.string.translate_section_status)) {
                MoreRow(Icons.Default.HourglassTop, AccentOrange, AccentOrange.copy(alpha = .12f), stringResource(R.string.feature_coming_soon), stringResource(R.string.translate_status))
            }
        }
        item { PageButton(stringResource(R.string.translate_open_language), onClick = onLanguage) }
    }
}

/** A setting that release builds don't offer yet (video, call recording). */
@Composable
fun FeatureUnavailableScreen(@StringRes title: Int, onBack: () -> Unit) {
    SettingsPage(stringResource(title), onBack) {
        item {
            MoreSection(stringResource(R.string.translate_section_status)) {
                MoreRow(Icons.Default.Hearing, AccentOrange, AccentOrange.copy(alpha = .12f), stringResource(R.string.feature_coming_soon), stringResource(R.string.feature_unavailable_subtitle))
            }
        }
        item { PageNote(stringResource(R.string.feature_unavailable_note)) }
    }
}
