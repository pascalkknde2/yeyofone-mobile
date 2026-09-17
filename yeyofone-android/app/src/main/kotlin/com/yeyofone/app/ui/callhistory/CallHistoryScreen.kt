package com.yeyofone.app.ui.callhistory

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.CallLog
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.CALLS_NAVIGATION
import com.yeyofone.app.ui.components.CallHistoryItem
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.CardWhite
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.SipAccountId

@Composable
fun CallHistoryScreen(
    uiState: CallHistoryUiState,
    accountIds: Set<SipAccountId>,
    onTabSelected: (Int) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
    onCallBack: (CallLog) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = BackgroundGray,
        bottomBar = {
            BottomNavigationBar(
                selectedIndex = CALLS_NAVIGATION,
                onItemSelected = onNavigationItemSelected,
            )
        },
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding())) {
            CallHistoryHeader(
                selectedTab = uiState.selectedTab,
                hasCalls = uiState.calls.isNotEmpty(),
                onTabSelected = onTabSelected,
                onEdit = { confirmClear = true },
            )
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier, color = TextPrimary)
                }
                uiState.calls.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_recent_calls), color = TextSecondary)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.today),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                    items(uiState.calls, key = { it.id.value }) { call ->
                        CallHistoryItem(
                            call = call,
                            canCallBack = call.accountId in accountIds,
                            onCallClick = { onCallBack(call) },
                            onVoicemailClick = {},
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.confirm_clear_history)) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) {
                    Text(stringResource(R.string.clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CallHistoryHeader(
    selectedTab: Int,
    hasCalls: Boolean,
    onTabSelected: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(CardWhite).padding(top = 32.dp, bottom = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.recents_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            IconButton(onClick = onEdit, enabled = hasCalls) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.edit_call_history), tint = TextPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        val tabs = listOf(R.string.filter_all, R.string.filter_missed, R.string.filter_voicemail)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F0F5)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) CardWhite else Color.Transparent)
                        .clickable { onTabSelected(index) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) TextPrimary else TextSecondary,
                    )
                }
            }
        }
    }
}
