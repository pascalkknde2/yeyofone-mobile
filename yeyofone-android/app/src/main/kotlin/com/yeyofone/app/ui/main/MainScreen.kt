package com.yeyofone.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.yeyofone.app.ui.theme.AccentOrange
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CardAccent
import com.yeyofone.app.ui.theme.CardGradientEnd
import com.yeyofone.app.ui.theme.CardGradientStart
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.DangerLight
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.OrangeLight
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.SuccessLight
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
    onAddAccount: () -> Unit,
    onCallBack: (CallLog) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    Scaffold(
        containerColor = CardWhite,
        bottomBar = { BottomNavigationBar(HOME_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MainHeader(primaryAccount?.displayName ?: stringResource(R.string.app_name), isRegistered) }
            item {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    AccountStatusCard(primaryAccount, isRegistered, onContacts)
                }
            }
            item {
                QuickActions(
                    onKeypad = onKeypad,
                    onContacts = onContacts,
                    onHistory = onHistory,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(R.string.dashboard_accounts), stringResource(R.string.manage), onContacts)
                    AccountStrip(accounts, onAccountClick, onAddAccount)
                }
            }
            item { SectionHeader(stringResource(R.string.recent_calls), stringResource(R.string.see_all), onHistory) }
            if (recentCalls.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_recent_calls),
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        color = TextSecondary,
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        recentCalls.take(3).forEachIndexed { index, call ->
                            RecentCallRow(call, { onCallBack(call) })
                            if (index < minOf(recentCalls.size, 3) - 1) HorizontalDivider(color = BorderLight.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainHeader(name: String, online: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(start = 20.dp, end = 20.dp, top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(stringResource(R.string.welcome_back).uppercase(), letterSpacing = 0.5.sp, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.5).sp)
        }
        Box(Modifier.size(44.dp).background(PrimaryLight, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.initials(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                    .background(if (online) AccentGreen else InactiveGray).border(2.dp, CardWhite, CircleShape),
            )
        }
    }
}

@Composable
private fun AccountStatusCard(account: SipAccount?, connected: Boolean, onManage: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(CardGradientStart, CardGradientEnd))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = 150.dp.toPx()
            val blueCenter = Offset(size.width, 0f)
            drawCircle(Brush.radialGradient(listOf(CardAccent.copy(alpha = 0.22f), Color.Transparent), blueCenter, radius), radius, blueCenter)
            val greenCenter = Offset(0f, size.height)
            drawCircle(Brush.radialGradient(listOf(AccentGreen.copy(alpha = 0.14f), Color.Transparent), greenCenter, radius), radius, greenCenter)
        }
        Column(Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.primary_account).uppercase(), Modifier.weight(1f), fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = CardWhite.copy(alpha = 0.6f))
                val statusColor = if (connected) AccentGreen else AccentRed
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                    Text(stringResource(if (connected) R.string.dashboard_active else R.string.dashboard_offline), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                }
            }
            Text(
                account?.let { "sip:${it.username}@${it.server.domain}" } ?: stringResource(R.string.no_accounts),
                Modifier.padding(vertical = 20.dp), fontSize = 20.sp,
                fontWeight = FontWeight.Medium, color = CardWhite,
                fontFamily = FontFamily.Monospace, letterSpacing = (-0.5).sp,
            )
            HorizontalDivider(color = CardWhite.copy(alpha = 0.1f))
            TextButton(onClick = onManage, contentPadding = PaddingValues(top = 12.dp, bottom = 0.dp)) {
                Text(stringResource(R.string.dashboard_manage_account), color = CardWhite.copy(alpha = 0.7f), fontSize = 13.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CardWhite.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun QuickActions(onKeypad: () -> Unit, onContacts: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Default.Apps, stringResource(R.string.keypad), AccentBlue, Modifier.weight(1f), onKeypad)
        QuickAction(Icons.Default.Person, stringResource(R.string.contacts_navigation), AccentGreen, Modifier.weight(1f), onContacts)
        QuickAction(Icons.Default.History, stringResource(R.string.dashboard_recent), AccentOrange, Modifier.weight(1f), onHistory)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.border(1.dp, BorderLight, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .background(Color.White).clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(when (color) { AccentBlue -> PrimaryLight; AccentGreen -> SuccessLight; else -> OrangeLight }), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue) }
    }
}

@Composable
private fun AccountStrip(accounts: List<SipAccount>, onClick: (SipAccount) -> Unit, onAdd: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(accounts, key = { it.id.value }) { account ->
            Column(
                Modifier.clickable { onClick(account) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (account.enabled) PrimaryLight else BackgroundGray),
                    contentAlignment = Alignment.Center,
                ) { Text(account.displayName.initials(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue) }
                Text(account.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary, maxLines = 1)
            }
        }
        item {
            Column(Modifier.clickable(onClick = onAdd), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(BackgroundGray).border(1.dp, BorderLight, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, stringResource(R.string.add_account), tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.dashboard_add), fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentCallRow(call: CallLog, onCall: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (call.callType == CallType.MISSED) DangerLight else BackgroundGray), contentAlignment = Alignment.Center) {
            Text(call.contactName.initials(), fontWeight = FontWeight.SemiBold, color = if (call.callType == CallType.MISSED) AccentRed else TextSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                call.contactName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(when (call.callType) {
                    CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                    CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                    CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                }, null, tint = if (call.callType == CallType.MISSED) AccentRed else AccentGreen, modifier = Modifier.size(14.dp))
                Text("$type • ${call.timestamp.atZone(ZoneId.systemDefault()).format(TIME_FORMAT)}", fontSize = 13.sp, color = if (call.callType == CallType.MISSED) AccentRed else TextSecondary)
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(SuccessLight).clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Call, stringResource(R.string.call_back), tint = AccentGreen, modifier = Modifier.size(18.dp)) }
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
