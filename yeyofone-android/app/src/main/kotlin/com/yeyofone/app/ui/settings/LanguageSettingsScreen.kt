package com.yeyofone.app.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.*
import java.util.Locale

private data class AppLanguage(val tag: String, val label: Int)

private val appLanguages = listOf(
    AppLanguage("en-US", R.string.language_english_us),
    AppLanguage("en-GB", R.string.language_english_uk),
    AppLanguage("es", R.string.language_spanish),
    AppLanguage("fr", R.string.language_french),
    AppLanguage("de", R.string.language_german),
)

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(LanguagePreferences.languageTag(context)) }
    var autoTranslate by remember { mutableStateOf(LanguagePreferences.autoTranslate(context)) }
    var accentTag by remember { mutableStateOf(LanguagePreferences.voiceAccent(context)) }
    var accentDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = BackgroundGray) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary)
                }
                Text(stringResource(R.string.language_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    LanguageSection(stringResource(R.string.language_app_section)) {
                        appLanguages.forEachIndexed { index, language ->
                            if (index > 0) LanguageDivider()
                            LanguageChoice(
                                label = stringResource(language.label),
                                selected = selectedLanguage.equals(language.tag, ignoreCase = true),
                                meta = if (language.tag == "en-US") stringResource(R.string.language_default) else null,
                                onClick = {
                                    selectedLanguage = language.tag
                                    LanguagePreferences.setLanguage(context, language.tag)
                                    (context as? Activity)?.recreate()
                                },
                            )
                        }
                    }
                }
                item {
                    LanguageSection(stringResource(R.string.language_voice_section)) {
                        LanguageSettingRow(
                            icon = Icons.Default.Language,
                            iconTint = Color(0xFF8B5CF6),
                            iconBackground = Color(0xFFF5F3FF),
                            title = stringResource(R.string.language_auto_translate),
                            subtitle = stringResource(R.string.language_auto_translate_subtitle),
                            trailing = {
                                Switch(
                                    checked = autoTranslate,
                                    onCheckedChange = {
                                        autoTranslate = it
                                        LanguagePreferences.setAutoTranslate(context, it)
                                    },
                                )
                            },
                            onClick = {
                                autoTranslate = !autoTranslate
                                LanguagePreferences.setAutoTranslate(context, autoTranslate)
                            },
                        )
                        LanguageDivider()
                        LanguageSettingRow(
                            icon = Icons.Default.Mic,
                            iconTint = Color(0xFFDB2777),
                            iconBackground = Color(0xFFFDF2F8),
                            title = stringResource(R.string.language_voice_accent),
                            subtitle = stringResource(accentLabel(accentTag)),
                            trailing = { Icon(Icons.Default.ChevronRight, null, tint = InactiveGray) },
                            onClick = { accentDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (accentDialog) {
        AlertDialog(
            onDismissRequest = { accentDialog = false },
            title = { Text(stringResource(R.string.language_voice_accent)) },
            text = {
                Column {
                    appLanguages.forEach { language ->
                        val tag = language.tag
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                accentTag = tag
                                LanguagePreferences.setVoiceAccent(context, tag)
                                accentDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LanguageRadio(selected = accentTag.equals(tag, ignoreCase = true))
                            Text(stringResource(language.label), Modifier.padding(start = 12.dp), color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accentDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LanguageSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            Modifier.padding(start = 4.dp, bottom = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = InactiveGray,
            letterSpacing = 1.sp,
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                .border(1.dp, BorderLight.copy(alpha = .55f), RoundedCornerShape(20.dp)),
            content = content,
        )
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, meta: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageRadio(selected)
        Text(label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        if (selected && meta != null) Text(meta, fontSize = 13.sp, color = InactiveGray)
    }
}

@Composable
private fun LanguageRadio(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(2.dp, if (selected) AccentBlue else Color(0xFFCBD5E1), CircleShape)
            .background(if (selected) AccentBlue else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun LanguageSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        Box(Modifier.padding(start = 8.dp)) { trailing() }
    }
}

@Composable
private fun LanguageDivider() {
    Spacer(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(BorderLight.copy(alpha = .55f)))
}

private fun accentLabel(tag: String): Int = when (tag.lowercase()) {
    "en-gb" -> R.string.language_english_uk
    "es" -> R.string.language_spanish
    "fr" -> R.string.language_french
    "de" -> R.string.language_german
    else -> R.string.language_accent_american
}

internal object LanguagePreferences {
    private const val PREFS = "language_settings"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_AUTO_TRANSLATE = "auto_translate"
    private const val KEY_VOICE_ACCENT = "voice_accent"

    fun languageTag(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, "en-US") ?: "en-US"

    fun autoTranslate(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_TRANSLATE, true)

    fun voiceAccent(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_VOICE_ACCENT, "en-US") ?: "en-US"

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LANGUAGE, tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        }
    }

    fun setAutoTranslate(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_AUTO_TRANSLATE, enabled) }
    }

    fun setVoiceAccent(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_VOICE_ACCENT, tag) }
    }

    internal fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag(context)))
        return context.createConfigurationContext(configuration)
    }
}
