package com.yeyofone.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.BuildConfig
import com.yeyofone.app.DebugLog
import com.yeyofone.app.R
import com.yeyofone.app.YeyoFoneApplication
import com.yeyofone.app.registrationStatus
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentOrange
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.OrangeLight
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.SuccessLight
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.pjsip.AudioCodecInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Settings › More: Advanced (diagnostics), Social (invite and share) and About (version and licenses).

// MARK: Advanced

@Composable
fun AdvancedSettingsScreen(
    accounts: List<SipAccount>,
    observeRegistration: (SipAccountId) -> StateFlow<RegistrationState>,
    inCall: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as YeyoFoneApplication
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val network = rememberNetworkType()
    val codecs by produceState(emptyList<AudioCodecInfo>()) { value = app.audioCodecs() }
    var debugLogSetting by remember { mutableStateOf(DebugLog.isEnabled(context)) }
    val emptyLog = stringResource(R.string.debug_log_empty)
    // Release builds apply the setting when the engine starts; debug builds always log.
    val restartNeeded = !BuildConfig.DEBUG && debugLogSetting != app.debugLogActive

    SettingsPage(stringResource(R.string.settings_advanced), onBack, snackbar) {
        item {
            MoreSection(stringResource(R.string.advanced_network)) {
                MoreRow(network.icon, AccentBlue, PrimaryLight, stringResource(R.string.advanced_connection), stringResource(network.label))
            }
        }
        item {
            MoreSection(stringResource(R.string.settings_accounts)) {
                if (accounts.isEmpty()) {
                    InfoRow(stringResource(R.string.advanced_no_accounts), stringResource(R.string.advanced_no_accounts_hint))
                }
                accounts.forEachIndexed { index, account ->
                    val state by observeRegistration(account.id).collectAsState()
                    val (label, color) = registrationStatus(account, state)
                    if (index > 0) MoreDivider(start = 20.dp)
                    InfoRow(account.displayName, "sip:${account.username}@${account.server.domain}") {
                        StatusBadge(label, color)
                    }
                }
            }
        }
        item {
            MoreSection(stringResource(R.string.advanced_codecs)) {
                if (codecs.isEmpty()) {
                    InfoRow(
                        stringResource(R.string.advanced_codecs),
                        stringResource(
                            if (app.engineUnsupported) R.string.advanced_engine_unsupported else R.string.advanced_codecs_unavailable,
                        ),
                    )
                }
                codecs.forEachIndexed { index, codec ->
                    if (index > 0) MoreDivider(start = 20.dp)
                    InfoRow(codecName(codec.id), codecDetail(codec.id))
                }
            }
        }
        item {
            MoreSection(stringResource(R.string.advanced_debugging)) {
                val subtitle = when {
                    BuildConfig.DEBUG -> R.string.debug_log_always_on
                    restartNeeded && debugLogSetting -> R.string.debug_log_pending_on
                    restartNeeded -> R.string.debug_log_pending_off
                    else -> R.string.debug_log_subtitle
                }
                MoreRow(
                    Icons.Default.BugReport, AccentOrange, OrangeLight,
                    stringResource(R.string.debug_log), stringResource(subtitle),
                    onClick = if (BuildConfig.DEBUG) null else ({
                        debugLogSetting = !debugLogSetting
                        DebugLog.setEnabled(context, debugLogSetting)
                    }),
                ) {
                    Switch(
                        checked = BuildConfig.DEBUG || debugLogSetting,
                        onCheckedChange = { enabled ->
                            debugLogSetting = enabled
                            DebugLog.setEnabled(context, enabled)
                        },
                        enabled = !BuildConfig.DEBUG,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = BorderLight,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                if (restartNeeded) {
                    MoreDivider()
                    MoreRow(
                        Icons.Default.RestartAlt, AccentBlue, PrimaryLight,
                        stringResource(R.string.debug_log_restart),
                        stringResource(if (inCall) R.string.debug_log_restart_in_call else R.string.debug_log_restart_subtitle),
                        onClick = if (inCall) null else ({ DebugLog.restart(context) }),
                    )
                }
                if (app.debugLogActive) {
                    MoreDivider()
                    MoreRow(
                        Icons.Default.Share, AccentBlue, PrimaryLight,
                        stringResource(R.string.debug_log_share), stringResource(R.string.debug_log_share_subtitle),
                        onClick = {
                            scope.launch {
                                val intent = withContext(Dispatchers.IO) { runCatching { DebugLog.shareIntent(context) }.getOrNull() }
                                if (intent == null) snackbar.showSnackbar(emptyLog) else context.startActivity(intent)
                            }
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.debug_log_privacy),
                Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

/** Codec IDs look like `G722/16000/1`; show the familiar name for the common ones. */
private fun codecName(id: String): String {
    val name = id.substringBefore('/')
    return when (name.uppercase()) {
        "G722" -> "G.722"
        "PCMU" -> "G.711 μ-law (PCMU)"
        "PCMA" -> "G.711 A-law (PCMA)"
        "OPUS" -> "Opus"
        "SPEEX" -> "Speex"
        else -> name
    }
}

@Composable
private fun codecDetail(id: String): String {
    val rate = id.split('/').getOrNull(1)?.toIntOrNull() ?: return id
    val kHz = rate / 1000
    // G.722 advertises 8000 in SDP for historical reasons but samples at 16 kHz; PJSIP reports 16000.
    return stringResource(if (rate >= 16_000) R.string.codec_wideband else R.string.codec_narrowband, kHz)
}

private class NetworkType(val label: Int, val icon: ImageVector)

/** The default network's type, updated as it changes. */
@Composable
private fun rememberNetworkType(): NetworkType {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(ConnectivityManager::class.java) }
    var capabilities by remember { mutableStateOf(manager.getNetworkCapabilities(manager.activeNetwork)) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                capabilities = networkCapabilities
            }

            override fun onLost(network: Network) {
                capabilities = null
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    val current = capabilities
    return when {
        current == null -> NetworkType(R.string.network_offline, Icons.Default.WifiOff)
        current.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType(R.string.network_wifi, Icons.Default.Wifi)
        current.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType(R.string.network_mobile, Icons.Default.SignalCellularAlt)
        current.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType(R.string.network_ethernet, Icons.Default.Wifi)
        else -> NetworkType(R.string.network_connected, Icons.Default.Wifi)
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// MARK: Social

@Composable
fun SocialScreen(account: SipAccount?, onBack: () -> Unit) {
    val context = LocalContext.current
    val invite = if (account != null) {
        stringResource(R.string.social_invite_message, account.username)
    } else {
        stringResource(R.string.social_invite_message_no_account)
    }
    val card = account?.let {
        listOf(it.displayName, stringResource(R.string.dashboard_extension, it.username), "sip:${it.username}@${it.server.domain}")
            .joinToString("\n")
    }

    SettingsPage(stringResource(R.string.settings_social), onBack) {
        item {
            MoreSection(stringResource(R.string.social_share_section)) {
                MoreRow(
                    Icons.Default.PersonAdd, AccentGreen, SuccessLight,
                    stringResource(R.string.social_invite), stringResource(R.string.social_invite_subtitle),
                    onClick = { shareText(context, invite) },
                )
                MoreDivider()
                MoreRow(
                    Icons.Default.ContactPage,
                    if (card != null) AccentBlue else InactiveGray,
                    if (card != null) PrimaryLight else BackgroundGray,
                    stringResource(R.string.social_contact),
                    stringResource(if (card != null) R.string.social_contact_subtitle else R.string.social_no_account),
                    onClick = card?.let { { shareText(context, it) } },
                )
            }
        }
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

// MARK: About

@Composable
fun AboutScreen(onLicenses: () -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.settings_about), onBack) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(AccentBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Text("YeyoFone", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    stringResource(R.string.about_version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()),
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            }
        }
        item {
            MoreSection(stringResource(R.string.about_app)) {
                ValueRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                MoreDivider(start = 20.dp)
                ValueRow(stringResource(R.string.about_build), BuildConfig.VERSION_CODE.toString())
                MoreDivider(start = 20.dp)
                ValueRow(stringResource(R.string.about_android), Build.VERSION.RELEASE)
            }
        }
        item {
            MoreSection(stringResource(R.string.about_legal)) {
                MoreRow(
                    Icons.Default.Description, AccentBlue, PrimaryLight,
                    stringResource(R.string.about_licenses), stringResource(R.string.about_licenses_subtitle),
                    onClick = onLicenses,
                )
            }
        }
    }
}

private class License(val name: String, val license: String, val description: Int, val url: String)

private val engineLicenses = listOf(
    License("PJSIP", "GNU GPL v2", R.string.licenses_pjsip, "https://www.pjsip.org"),
    License("OpenSSL", "Apache 2.0", R.string.licenses_openssl, "https://www.openssl.org"),
)

private val libraryLicenses = listOf(
    License("AndroidX", "Apache 2.0", R.string.licenses_androidx, "https://developer.android.com/jetpack/androidx"),
    License("kotlinx.coroutines", "Apache 2.0", R.string.licenses_coroutines, "https://github.com/Kotlin/kotlinx.coroutines"),
    License("Coil", "Apache 2.0", R.string.licenses_coil, "https://coil-kt.github.io/coil/"),
    License("Firebase Cloud Messaging", "Apache 2.0", R.string.licenses_firebase, "https://github.com/firebase/firebase-android-sdk"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsPage(stringResource(R.string.about_licenses), onBack) {
        listOf(R.string.licenses_engine to engineLicenses, R.string.licenses_libraries to libraryLicenses).forEach { (title, licenses) ->
            item {
                MoreSection(stringResource(title)) {
                    licenses.forEachIndexed { index, item ->
                        if (index > 0) MoreDivider(start = 20.dp)
                        Column(
                            Modifier.fillMaxWidth().clickable { uriHandler.openUri(item.url) }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(item.license, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AccentBlue)
                            }
                            Text(stringResource(item.description), fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// MARK: Shared layout

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(containerColor = BackgroundGray, snackbarHost = { snackbar?.let { SnackbarHost(it) } }) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            Row(
                Modifier.fillMaxWidth().background(CardWhite).statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary)
                }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun MoreSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite)
                .border(1.dp, BorderLight.copy(alpha = .55f), RoundedCornerShape(20.dp)),
            content = content,
        )
    }
}

/** Icon tile, title and subtitle; a chevron shows when it's tappable and there is no trailing content. */
@Composable
private fun MoreRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        when {
            trailing != null -> Box(Modifier.padding(start = 8.dp)) { trailing() }
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InactiveGray)
        }
    }
}

@Composable
private fun InfoRow(title: String, detail: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(detail, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        if (trailing != null) Box(Modifier.padding(start = 8.dp)) { trailing() }
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Text(value, fontSize = 15.sp, color = TextSecondary)
    }
}

@Composable
private fun MoreDivider(start: androidx.compose.ui.unit.Dp = 72.dp) {
    Spacer(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(BorderLight.copy(alpha = .55f)))
}
