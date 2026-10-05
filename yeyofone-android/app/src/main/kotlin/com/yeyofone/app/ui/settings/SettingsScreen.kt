package com.yeyofone.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.BuildConfig
import com.yeyofone.app.R
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.SETTINGS_NAVIGATION
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentOrange
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CardGradientEnd
import com.yeyofone.app.ui.theme.CardGradientStart
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.SuccessLight
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class SettingEntry(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val action: (() -> Unit)? = null,
)

@Composable
fun SettingsScreen(
    accountCount: Int,
    onAccountsClick: () -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.feature_coming_soon)
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val unavailable: () -> Unit = { scope.launch { snackbar.showSnackbar(comingSoon) } }

    val sections = listOf(
        stringResource(R.string.settings_section_account) to listOf(
            SettingEntry(
                Icons.Default.AccountCircle,
                AccentBlue,
                stringResource(R.string.settings_accounts),
                stringResource(R.string.settings_accounts_connected, accountCount),
                action = onAccountsClick,
            ),
            SettingEntry(
                Icons.Default.Business,
                TextPrimary,
                stringResource(R.string.settings_enterprise),
                stringResource(R.string.settings_enterprise_subtitle),
                stringResource(R.string.new_badge),
            ),
        ),
        stringResource(R.string.settings_section_audio_video) to listOf(
            SettingEntry(Icons.Default.MusicNote, AccentOrange, stringResource(R.string.settings_audio), stringResource(R.string.settings_audio_subtitle)),
            SettingEntry(Icons.Default.Videocam, AccentRed, stringResource(R.string.settings_video), stringResource(R.string.settings_video_subtitle)),
            SettingEntry(Icons.Default.Translate, AccentRed, stringResource(R.string.settings_translate), stringResource(R.string.settings_translate_subtitle)),
        ),
        stringResource(R.string.settings_section_calls) to listOf(
            SettingEntry(Icons.AutoMirrored.Filled.PhoneCallback, AccentGreen, stringResource(R.string.settings_incoming), stringResource(R.string.settings_incoming_subtitle)),
            SettingEntry(Icons.Default.FiberManualRecord, AccentBlue, stringResource(R.string.settings_recording), stringResource(R.string.settings_recording_subtitle)),
        ),
        stringResource(R.string.settings_section_more) to listOf(
            SettingEntry(Icons.Default.Settings, AccentBlue, stringResource(R.string.settings_advanced), stringResource(R.string.settings_advanced_subtitle)),
            SettingEntry(Icons.Default.Share, AccentGreen, stringResource(R.string.settings_social), stringResource(R.string.settings_social_subtitle)),
            SettingEntry(Icons.Default.Info, InactiveGray, stringResource(R.string.settings_about), stringResource(R.string.settings_about_subtitle, BuildConfig.VERSION_NAME)),
        ),
    )
    val visibleSections = sections.mapNotNull { (title, entries) ->
        val visible = if (query.isBlank()) entries else entries.filter {
            it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
        }
        visible.takeIf(List<*>::isNotEmpty)?.let { title to it }
    }

    Scaffold(
        containerColor = BackgroundGray,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomNavigationBar(SETTINGS_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            SettingsHeader(
                searching = searching,
                query = query,
                onQueryChange = { query = it },
                onSearchToggle = {
                    searching = !searching
                    if (!searching) query = ""
                },
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (query.isBlank()) item { PremiumBanner(unavailable) }
                visibleSections.forEach { (title, entries) ->
                    item(key = title) {
                        SettingsSection(title, entries, unavailable)
                    }
                }
                if (visibleSections.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.settings_no_results),
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME),
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        fontSize = 12.sp,
                        color = InactiveGray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()
            .padding(start = 24.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.settings_search)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        } else {
            Text(
                stringResource(R.string.settings_navigation),
                modifier = Modifier.weight(1f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp,
            )
        }
        IconButton(onClick = onSearchToggle) {
            Icon(
                if (searching) Icons.Default.Close else Icons.Default.Search,
                stringResource(if (searching) R.string.cancel else R.string.settings_search),
                tint = TextPrimary,
            )
        }
    }
}

@Composable
private fun PremiumBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = AccentBlue.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(CardGradientStart, CardGradientEnd)))
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_premium), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_premium_subtitle), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsSection(title: String, entries: List<SettingEntry>, unavailable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            Modifier.padding(start = 4.dp),
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            entries.forEachIndexed { index, entry ->
                SettingRow(entry, entry.action ?: unavailable)
                if (index < entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 0.5.dp, color = BorderLight)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(entry: SettingEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(entry.iconColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, null, tint = entry.iconColor, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(entry.subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        entry.badge?.let {
            Text(
                it.uppercase(),
                Modifier.clip(RoundedCornerShape(8.dp)).background(SuccessLight).padding(horizontal = 8.dp, vertical = 3.dp),
                color = AccentGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray, modifier = Modifier.size(18.dp))
    }
}
