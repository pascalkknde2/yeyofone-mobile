package com.yeyofone.app.ui.accounts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.components.AccountCard
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.CONTACTS_NAVIGATION
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.SipAccount

@Composable
fun AccountsScreen(
    accounts: List<SipAccount>,
    onAdd: () -> Unit,
    onOpen: (SipAccount) -> Unit,
    onEnabledChange: (SipAccount, Boolean) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.sip_accounts_tab),
        stringResource(R.string.iax_accounts_tab),
        stringResource(R.string.webrtc_accounts_tab),
    )

    Scaffold(
        containerColor = BackgroundGray,
        bottomBar = { BottomNavigationBar(CONTACTS_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.accounts_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                    )
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, stringResource(R.string.add_account), tint = TextPrimary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F0F5)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { selectedTab = index }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) TextPrimary else TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0 && accounts.isNotEmpty()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(accounts, key = { it.id.value }) { account ->
                        AccountCard(
                            account = account,
                            onEnabledChange = { onEnabledChange(account, it) },
                            onClick = { onOpen(account) },
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.no_accounts_for_type, tabs[selectedTab]),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (selectedTab == 0) stringResource(R.string.tap_add_sip_account)
                        else stringResource(R.string.account_type_not_supported),
                        fontSize = 14.sp,
                        color = Color(0xFFA0A0A5),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
