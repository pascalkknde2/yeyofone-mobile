Here is the complete implementation for the **Accounts Screen**, following the exact modern design pattern established in your previous screens.

It features a segmented tab control at the top for switching between account types (SIP, IAX, WebRTC) and a list of account cards. Each card displays the user's name, account number, and an enable/disable toggle switch.
## Android Implementation (Jetpack Compose + MVVM)

### 1. Update Project Structure
```text
com.yourpackage.voipapp
│
├── data
│   └── model
│       └── VoipAccount.kt        <-- New
│
├── ui
│   ├── components
│   │   └── AccountCard.kt        <-- New
│   │
│   └── accounts
│       ├── AccountsScreen.kt     <-- New
│       └── AccountsViewModel.kt  <-- New
```

### 2. The Model
**`data/model/VoipAccount.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

enum class AccountType {
    SIP, IAX, WEBRTC
}

data class VoipAccount(
    val id: String,
    val name: String,
    val accountNumber: String, // e.g., "sip:1001@voip.net"
    val type: AccountType,
    val isEnabled: Boolean = true
)
```

### 3. The ViewModel (MVVM)
**`ui/accounts/AccountsViewModel.kt`**
```kotlin
package com.yourpackage.voipapp.ui.accounts

import androidx.lifecycle.ViewModel
import com.yourpackage.voipapp.data.model.AccountType
import com.yourpackage.voipapp.data.model.VoipAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountsUiState(
    val accounts: List<VoipAccount> = emptyList(),
    val selectedTab: Int = 0 // 0: SIP, 1: IAX, 2: WebRTC
) {
    val filteredAccounts: List<VoipAccount>
        get() = accounts.filter {
            when (selectedTab) {
                0 -> it.type == AccountType.SIP
                1 -> it.type == AccountType.IAX
                2 -> it.type == AccountType.WEBRTC
                else -> false
            }
        }
}

class AccountsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        // In a real app, this data would come from a Repository/DataStore
        val mockAccounts = listOf(
            VoipAccount("1", "Noah Anderson", "sip:1001@voip.net", AccountType.SIP, true),
            VoipAccount("2", "Sarah Jenkins", "sip:1002@voip.net", AccountType.SIP, false),
            VoipAccount("3", "Marcus Chen", "sip:1003@voip.net", AccountType.SIP, true),
            VoipAccount("4", "IAX Account 1", "iax:2001@voip.net", AccountType.IAX, true),
            VoipAccount("5", "WebRTC Account", "webrtc:3001@voip.net", AccountType.WEBRTC, false)
        )
        _uiState.value = _uiState.value.copy(accounts = mockAccounts)
    }

    fun onTabSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun toggleAccountStatus(accountId: String) {
        val updatedAccounts = _uiState.value.accounts.map { account ->
            if (account.id == accountId) {
                account.copy(isEnabled = !account.isEnabled)
            } else {
                account
            }
        }
        _uiState.value = _uiState.value.copy(accounts = updatedAccounts)
    }

    fun onAddAccount() {
        // Handle adding a new account
    }

    fun onAccountClick(accountId: String) {
        // Handle opening account details
    }
}
```

### 4. UI Components
**`ui/components/AccountCard.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.voipapp.data.model.VoipAccount
import com.yourpackage.voipapp.ui.theme.CallAccentGreen
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun AccountCard(
    account: VoipAccount,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (account.isEnabled) Color(0xFFE0EAFF) else Color(0xFFF0F0F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (account.isEnabled) CallTextPrimary else Color(0xFFA0A0A5),
                modifier = Modifier.size(22.dp)
            )
        }

        // Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = account.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (account.isEnabled) CallTextPrimary else Color(0xFFA0A0A5),
                textDecoration = if (!account.isEnabled) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(
                text = account.accountNumber,
                fontSize = 13.sp,
                color = CallTextSecondary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        // Toggle Switch
        Switch(
            checked = account.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CallAccentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE5E5EA)
            )
        )
    }
}
```

### 5. The Main Screen
**`ui/accounts/AccountsScreen.kt`**
```kotlin
package com.yourpackage.voipapp.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourpackage.voipapp.ui.components.AccountCard
import com.yourpackage.voipapp.ui.theme.CallBackground
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("SIP Accounts", "IAX Accounts", "WebRTC")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallBackground)
            .systemBarsPadding()
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = CallTextPrimary
                )
            }
            Text(
                text = "Accounts",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary
            )
            IconButton(onClick = { viewModel.onAddAccount() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Account",
                    tint = CallTextPrimary
                )
            }
        }

        // --- Tabs ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F0F5))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = uiState.selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { viewModel.onTabSelected(index) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) CallTextPrimary else CallTextSecondary
                        )
                    }
                }
            }
        }

        // --- Account List ---
        if (uiState.filteredAccounts.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No accounts found",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CallTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the + button to add a new account",
                    fontSize = 14.sp,
                    color = Color(0xFFA0A0A5)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.filteredAccounts) { account ->
                    AccountCard(
                        account = account,
                        onToggle = { viewModel.toggleAccountStatus(account.id) },
                        onClick = { viewModel.onAccountClick(account.id) }
                    )
                }
            }
        }
    }
}
```

### 6. MainActivity Update
```kotlin
package com.yourpackage.voipapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yourpackage.voipapp.ui.accounts.AccountsScreen
import com.yourpackage.voipapp.ui.theme.VoipAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoipAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccountsScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}
```

### Key Android/MVVM Highlights:

1.  **State-Driven Tab Filtering:** The `AccountsViewModel` holds a `selectedTab` integer. The `filteredAccounts` computed property automatically filters the master list based on the active tab. When the user taps a tab, `onTabSelected` updates the state, and the list recomposes instantly.
2.  **Toggle Switch State:** The `toggleAccountStatus` function maps through the list of accounts, finds the one with the matching ID, and flips its `isEnabled` boolean. This is a pure functional update, ensuring the UI stays in sync with the data.
3.  **Disabled State Styling:** When an account is disabled, the `AccountCard` component dynamically changes the avatar background to grey, the text color to a muted grey, and applies a `LineThrough` text decoration to the name. This provides clear visual feedback that the account is inactive.
4.  **Reusable `AccountCard`:** Extracted into its own composable, making the main screen clean and easy to read. It accepts the `VoipAccount` model and two lambda callbacks (`onToggle` and `onClick`).
5.  **Empty State Handling:** Added a clean empty state UI that appears if the filtered list is empty, guiding the user to add a new account.