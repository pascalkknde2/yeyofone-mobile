package com.yeyofone.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.CallLog
import com.yeyofone.app.data.model.CallType
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.HOME_NAVIGATION
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.SipAccount
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    primaryAccount: SipAccount?,
    isRegistered: Boolean,
    accounts: List<SipAccount>,
    recentCalls: List<CallLog>,
    onKeypad: () -> Unit,
    onContacts: () -> Unit,
    onHistory: () -> Unit,
    onAccountClick: (SipAccount) -> Unit,
    onCallBack: (CallLog) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    Scaffold(
        containerColor = BackgroundGray,
        bottomBar = { BottomNavigationBar(HOME_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MainHeader(primaryAccount?.displayName ?: stringResource(R.string.app_name), isRegistered) }
            item {
                Box(Modifier.padding(horizontal = 24.dp)) {
                    AccountStatusCard(primaryAccount, isRegistered, onContacts)
                }
            }
            item {
                QuickActions(
                    onKeypad = onKeypad,
                    onContacts = onContacts,
                    onHistory = onHistory,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item { SectionHeader(stringResource(R.string.dashboard_accounts), stringResource(R.string.manage), onContacts) }
            item { AccountStrip(accounts, onAccountClick) }
            item { SectionHeader(stringResource(R.string.recent_calls), stringResource(R.string.see_all), onHistory) }
            if (recentCalls.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_recent_calls),
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        color = TextSecondary,
                    )
                }
            } else {
                items(recentCalls.take(3), key = { it.id.value }) { call ->
                    RecentCallRow(call, { onCallBack(call) }, Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun MainHeader(name: String, online: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(stringResource(R.string.welcome_back), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.5).sp)
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE0EAFF)), contentAlignment = Alignment.Center) {
            Text(name.initials(), fontWeight = FontWeight.Bold, color = TextPrimary)
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                    .background(if (online) AccentGreen else Color(0xFF8E8E93)),
            )
        }
    }
}

@Composable
private fun AccountStatusCard(account: SipAccount?, connected: Boolean, onManage: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF2C2C2E))))
            .clickable(onClick = onManage).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.primary_account), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background((if (connected) AccentGreen else AccentRed).copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (connected) AccentGreen else AccentRed))
                Text(
                    stringResource(if (connected) R.string.status_registered else R.string.status_not_registered).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) AccentGreen else AccentRed,
                )
            }
        }
        Text(
            account?.let { "sip:${it.username}@${it.server.domain}" } ?: stringResource(R.string.no_accounts),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
        )
        Text(stringResource(R.string.tap_to_manage_accounts), fontSize = 12.sp, color = Color.White.copy(alpha = 0.62f))
    }
}

@Composable
private fun QuickActions(onKeypad: () -> Unit, onContacts: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Default.Dialpad, stringResource(R.string.keypad), AccentBlue, Modifier.weight(1f), onKeypad)
        QuickAction(Icons.Default.Person, stringResource(R.string.contacts_navigation), AccentGreen, Modifier.weight(1f), onContacts)
        QuickAction(Icons.Default.History, stringResource(R.string.recent_calls), Color(0xFFFF9500), Modifier.weight(1f), onHistory)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(Color.White).clickable(onClick = onClick).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 0.5.sp)
        Text(action, Modifier.clickable(onClick = onAction), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
    }
}

@Composable
private fun AccountStrip(accounts: List<SipAccount>, onClick: (SipAccount) -> Unit) {
    if (accounts.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(accounts, key = { it.id.value }) { account ->
            Column(
                Modifier.clickable { onClick(account) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (account.enabled) Color(0xFFE0EAFF) else Color(0xFFF0F0F5)),
                    contentAlignment = Alignment.Center,
                ) { Text(account.displayName.initials(), fontWeight = FontWeight.Bold, color = TextPrimary) }
                Text(account.displayName, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RecentCallRow(call: CallLog, onCall: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            .background(Color.White).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE4E8EC)), contentAlignment = Alignment.Center) {
            Text(call.contactName.initials(), fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                call.contactName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (call.callType == CallType.MISSED) AccentRed else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val type = stringResource(
                when (call.callType) {
                    CallType.INCOMING -> R.string.incoming_call
                    CallType.OUTGOING -> R.string.outgoing_call
                    CallType.MISSED -> R.string.missed_call
                },
            )
            Text("$type • ${call.timestamp.atZone(ZoneId.systemDefault()).format(TIME_FORMAT)}", fontSize = 12.sp, color = TextSecondary)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.1f)).clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Call, stringResource(R.string.call_back), tint = AccentGreen, modifier = Modifier.size(16.dp)) }
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
