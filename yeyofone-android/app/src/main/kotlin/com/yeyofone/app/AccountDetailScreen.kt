@file:OptIn(ExperimentalMaterial3Api::class)

package com.yeyofone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccount

private val Ink = Color(0xFF1A1A1A)
private val Gray = Color(0xFF6E6E73)
private val GrayMid = Color(0xFF8E8E93)
private val PageBackground = Color(0xFFF8F9FA)
private val Success = Color(0xFF34C759)
private val Warning = Color(0xFFFF9500)
private val Danger = Color(0xFFFF3B30)
private val SwitchTrackOff = Color(0xFFE5E5EA)
private val Hairline = Color(0x0A000000)

@Composable
fun AccountDetail(account: SipAccount, viewModel: YeyoFoneViewModel) {
    val registrationState by viewModel.observeRegistration(account.id).collectAsStateWithLifecycle()
    val allPreferences by viewModel.accountPreferences.collectAsStateWithLifecycle()
    val preferences = allPreferences[account.id.value] ?: AccountPreferences()
    var showSignOutDialog by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            background = PageBackground,
            surface = Color.White,
            surfaceVariant = SwitchTrackOff,
            error = Danger,
        ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(stringResource(R.string.account_details), style = MaterialTheme.typography.titleLarge)
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::showAccounts) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = Ink,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.editAccount(account.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_edit_account),
                                tint = Ink,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.padding(padding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item { ProfileHeader(account, registrationState) }
                item {
                    Section(R.string.section_connection) {
                        ConnectionRows(account)
                    }
                }
                item {
                    Section(R.string.section_preferences) {
                        SwitchRow(
                            R.string.pref_auto_answer,
                            preferences.autoAnswer,
                        ) { viewModel.setPreference(account.id, PreferenceToggle.AutoAnswer, it) }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = Hairline)
                        SwitchRow(
                            R.string.pref_call_waiting,
                            preferences.callWaiting,
                        ) { viewModel.setPreference(account.id, PreferenceToggle.CallWaiting, it) }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = Hairline)
                        SwitchRow(
                            R.string.pref_voicemail,
                            preferences.voicemail,
                        ) { viewModel.setPreference(account.id, PreferenceToggle.Voicemail, it) }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = Hairline)
                        SwitchRow(
                            R.string.pref_do_not_disturb,
                            preferences.doNotDisturb,
                        ) { viewModel.setPreference(account.id, PreferenceToggle.DoNotDisturb, it) }
                    }
                }
                item {
                    ActionButtons(account, registrationState, viewModel) { showSignOutDialog = true }
                }
            }
        }

        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                text = { Text(stringResource(R.string.confirm_sign_out, account.displayName)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSignOutDialog = false
                        viewModel.setAccountEnabled(account.id, false)
                    }) {
                        Text(stringResource(R.string.sign_out), color = Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileHeader(account: SipAccount, registrationState: RegistrationState) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).background(Color(0xFFE8EEFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(84.dp).background(Ink, CircleShape).border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(76.dp).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                ) {
                    Text(
                        account.displayName.initials(),
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Text(
            account.displayName,
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            letterSpacing = (-0.3).sp,
        )
        Text(
            stringResource(R.string.extension_value, account.username),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Gray,
            letterSpacing = 0.2.sp,
        )
        val (label, statusColor) = status(account, registrationState)
        Box(Modifier.padding(top = 12.dp)) {
            StatusBadge(label, statusColor)
        }
    }
}

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            stringResource(titleRes),
            Modifier.padding(start = 4.dp, bottom = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gray,
            letterSpacing = 0.5.sp,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ConnectionRows(account: SipAccount) {
    InfoRow(R.string.label_sip_server, account.server.domain, mono = true)
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = Hairline)
    InfoRow(R.string.label_username, account.username, mono = true)
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = Hairline)
    InfoRow(R.string.label_password, stringResource(R.string.password_masked), mono = true)
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = Hairline)
    InfoRow(R.string.label_transport, account.server.transport.name)
}

@Composable
private fun InfoRow(labelRes: Int, value: String, mono: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
        Text(
            value,
            fontSize = if (mono) 14.sp else 15.sp,
            fontWeight = FontWeight.Normal,
            color = Gray,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Success,
                uncheckedTrackColor = SwitchTrackOff,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActionButtons(
    account: SipAccount,
    registrationState: RegistrationState,
    viewModel: YeyoFoneViewModel,
    onSignOut: () -> Unit,
) {
    val registrationBusy = registrationState is RegistrationState.Registering ||
        registrationState is RegistrationState.Refreshing
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                if (account.enabled) viewModel.reregister(account.id)
                else viewModel.setAccountEnabled(account.id, true)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            enabled = !registrationBusy,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (account.enabled) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(
                    stringResource(if (account.enabled) R.string.re_register_account else R.string.sign_in),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (account.enabled) {
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger.copy(alpha = 0.1f),
                    contentColor = Danger,
                ),
            ) {
                Text(stringResource(R.string.sign_out), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, statusColor: Color) {
    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(percent = 50)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(12.dp).background(statusColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).background(statusColor, CircleShape))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
        }
    }
}

@Composable
private fun status(account: SipAccount, registrationState: RegistrationState): Pair<String, Color> = when {
    !account.enabled -> stringResource(R.string.status_disabled) to GrayMid
    registrationState is RegistrationState.Registered || registrationState is RegistrationState.Refreshing ->
        stringResource(R.string.status_registered) to Success
    registrationState is RegistrationState.Registering -> stringResource(R.string.status_registering) to Warning
    registrationState is RegistrationState.Failed -> stringResource(R.string.status_registration_failed) to Danger
    registrationState is RegistrationState.Unregistering -> stringResource(R.string.status_unregistering) to GrayMid
    else -> stringResource(R.string.status_not_registered) to GrayMid
}

private fun String.initials(): String {
    val words = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return if (words.isEmpty()) "?" else words.take(2).joinToString("") { it.first().uppercase() }
}
