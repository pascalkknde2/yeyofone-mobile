package com.yeyofone.app.ui.main

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.DangerLight
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.SuccessLight
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.Contact
import com.yeyofone.core.model.SipAccount
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    primaryAccount: SipAccount?,
    isRegistered: Boolean,
    favoriteContacts: List<Contact>,
    recentCalls: List<CallLog>,
    onKeypad: () -> Unit,
    onContacts: () -> Unit,
    onHistory: () -> Unit,
    onAccountClick: (SipAccount) -> Unit,
    onAddAccount: () -> Unit,
    onCallContact: (Contact) -> Unit,
    onAddContact: () -> Unit,
    onCallBack: (CallLog) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
    forwardingDestination: String? = null,
    doNotDisturb: Boolean = false,
    onToggleDoNotDisturb: () -> Unit = {},
) {
    Scaffold(
        containerColor = CardWhite,
        bottomBar = { BottomNavigationBar(HOME_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { MainHeader(primaryAccount?.displayName ?: stringResource(R.string.app_name), primaryAccount?.username, isRegistered, doNotDisturb) }
            // FWD-04: a persistent reminder on the main screen, not only in Settings, so a user
            // doesn't forget calls are being redirected elsewhere.
            if (forwardingDestination != null) {
                item {
                    ForwardingBanner(
                        accountNumber = primaryAccount?.username,
                        destination = forwardingDestination,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            item {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    AccountStatusCard(primaryAccount, doNotDisturb, onToggleDoNotDisturb) {
                        primaryAccount?.let(onAccountClick) ?: onAddAccount()
                    }
                }
            }
            item {
                SearchBar(onKeypad, Modifier.padding(horizontal = 20.dp))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(R.string.dashboard_accounts), stringResource(R.string.dashboard_edit), onContacts)
                    ContactStrip(favoriteContacts, onCallContact, onAddContact)
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
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recentCalls.take(3).forEach { call -> RecentCallRow(call, { onCallBack(call) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainHeader(name: String, extension: String?, online: Boolean, doNotDisturb: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(CardWhite).padding(start = 20.dp, end = 20.dp, top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = TextPrimary,
                letterSpacing = (-0.5).sp,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (doNotDisturb) AccentRed else if (online) AccentGreen else InactiveGray))
                val status = stringResource(if (doNotDisturb) R.string.do_not_disturb_setting else if (online) R.string.home_available else R.string.dashboard_offline)
                Text(
                    if (extension != null) "$status • Ext $extension" else status,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.size(52.dp).background(CardWhite, CircleShape).border(2.dp, BorderLight, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.initials(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                    .background(if (online) AccentGreen else InactiveGray).border(2.dp, CardWhite, CircleShape),
            )
        }
    }
}

@Composable
private fun ForwardingBanner(accountNumber: String?, destination: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PrimaryLight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PhoneForwarded, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
        Text(
            if (accountNumber != null) {
                stringResource(R.string.call_forwarding_banner, accountNumber, destination)
            } else {
                stringResource(R.string.call_forwarding_active, destination)
            },
            Modifier.padding(start = 10.dp),
            color = AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AccountStatusCard(account: SipAccount?, doNotDisturb: Boolean, onToggleDnd: () -> Unit, onManage: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().shadow(12.dp, shape, ambientColor = AccentBlue.copy(alpha = 0.3f), spotColor = AccentBlue.copy(alpha = 0.4f))
            .clip(shape).background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))))
            .clickable(onClick = onManage).padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.primary_account).uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f))
            Text(
                account?.let { stringResource(R.string.dashboard_extension, it.username) } ?: stringResource(R.string.add_account),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (account != null) {
            Row(
                Modifier.clip(CircleShape)
                    .background(if (doNotDisturb) Color.White else Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable(onClick = onToggleDnd).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val content = if (doNotDisturb) AccentRed else Color.White
                Icon(Icons.Default.PowerSettingsNew, null, tint = content, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.dnd_short), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = content)
            }
        }
    }
}

@Composable
private fun SearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(20.dp)).background(CardWhite).border(1.dp, BorderLight, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.search_or_dial), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InactiveGray)
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
private fun ContactStrip(contacts: List<Contact>, onClick: (Contact) -> Unit, onAdd: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(contacts, key = { _, c -> c.id.value }) { index, contact ->
            val (tint, bg) = FAVORITE_TINTS[index % FAVORITE_TINTS.size]
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick(contact) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(bg()),
                    contentAlignment = Alignment.Center,
                ) { Text(contact.displayName.initials(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint()) }
                Text(contact.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Column(Modifier.clickable(onClick = onAdd), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).border(1.5.dp, BorderLight, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, stringResource(R.string.add_account), tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.dashboard_add), fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentCallRow(call: CallLog, onCall: () -> Unit, modifier: Modifier = Modifier) {
    val missed = call.callType == CallType.MISSED
    val tint = if (missed) AccentRed else AccentGreen
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardWhite)
            .border(1.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onCall).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(if (missed) DangerLight else SuccessLight), contentAlignment = Alignment.Center) {
            Icon(when (call.callType) {
                CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
            }, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(call.contactName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    when (call.callType) {
                        CallType.INCOMING -> R.string.incoming_call
                        CallType.OUTGOING -> R.string.outgoing_call
                        CallType.MISSED -> R.string.missed_call
                    },
                ),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (missed) AccentRed else TextSecondary,
            )
        }
        Text(
            call.timestamp.atZone(ZoneId.systemDefault()).format(TIME_FORMAT),
            Modifier.clip(RoundedCornerShape(10.dp)).background(BackgroundGray).padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
        )
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private val FAVORITE_TINTS: List<Pair<() -> Color, () -> Color>> = listOf(
    Pair({ AccentBlue }, { PrimaryLight }),
    Pair({ Color(0xFFDB2777) }, { Color(0xFFDB2777).copy(alpha = 0.14f) }),
    Pair({ Color(0xFFD97706) }, { Color(0xFFD97706).copy(alpha = 0.14f) }),
    Pair({ AccentGreen }, { SuccessLight }),
)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
