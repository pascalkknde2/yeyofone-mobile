Here is the complete Android implementation of the **Main Screen (Home Dashboard)** using **Kotlin**, **Jetpack Compose**, and the **MVVM** design pattern, following the exact architecture and design language of the previous screens.

---

### 1. Update Project Structure
```text
com.yourpackage.voipapp
│
├── data
│   └── model
│       ├── FavoriteContact.kt          <-- New
│       ├── RecentCall.kt               <-- New
│       └── AccountStatus.kt            <-- New
│
├── ui
│   ├── components
│   │   ├── MainHeader.kt               <-- New
│   │   ├── AccountStatusCard.kt        <-- New
│   │   ├── QuickActionsSection.kt      <-- New
│   │   ├── FavoritesSection.kt         <-- New
│   │   ├── RecentCallsSection.kt       <-- New
│   │   └── MainBottomNavBar.kt         <-- New
│   │
│   └── main
│       ├── MainScreen.kt               <-- New
│       └── MainViewModel.kt            <-- New
```

---

### 2. The Models
**`data/model/FavoriteContact.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

data class FavoriteContact(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val hasQuickCall: Boolean = true
)
```

**`data/model/RecentCall.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

data class RecentCall(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val direction: CallDirection,
    val timestamp: String
)
```

**`data/model/AccountStatus.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

data class AccountStatus(
    val isConnected: Boolean = true,
    val accountNumber: String = "sip:1001@voip.net",
    val balance: String = "$24.50"
)
```

---

### 3. The ViewModel
**`ui/main/MainViewModel.kt`**
```kotlin
package com.yourpackage.voipapp.ui.main

import androidx.lifecycle.ViewModel
import com.yourpackage.voipapp.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainUiState(
    val userName: String = "Noah Anderson",
    val profileAvatarUrl: String = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100",
    val accountStatus: AccountStatus = AccountStatus(),
    val favorites: List<FavoriteContact> = emptyList(),
    val recentCalls: List<RecentCall> = emptyList()
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        val mockFavorites = listOf(
            FavoriteContact("1", "Sarah", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100"),
            FavoriteContact("2", "Marcus", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100"),
            FavoriteContact("3", "Emily", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=100")
        )

        val mockRecentCalls = listOf(
            RecentCall("1", "Noah Anderson", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100", CallDirection.MISSED, "10:42 AM"),
            RecentCall("2", "Sarah Jenkins", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100", CallDirection.OUTGOING, "09:15 AM")
        )

        _uiState.value = _uiState.value.copy(
            favorites = mockFavorites,
            recentCalls = mockRecentCalls
        )
    }

    // --- Action Handlers ---
    fun onKeypadClick() { /* Navigate to Keypad */ }
    fun onContactsClick() { /* Navigate to Contacts */ }
    fun onHistoryClick() { /* Navigate to Call History */ }
    fun onFavoriteClick(id: String) { /* Call favorite */ }
    fun onAddFavoriteClick() { /* Add to favorites */ }
    fun onRecentCallClick(id: String) { /* Open call details */ }
    fun onCallBackClick(id: String) { /* Call back */ }
    fun onTopUpClick() { /* Navigate to billing */ }
}
```

---

### 4. UI Components

**`ui/components/MainHeader.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourpackage.voipapp.ui.theme.CallAccentGreen
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun MainHeader(
    userName: String,
    avatarUrl: String,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Good morning,",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CallTextSecondary
            )
            Text(
                text = userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        Box {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Profile",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .shadow(4.dp, CircleShape),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CallAccentGreen)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}
```

**`ui/components/AccountStatusCard.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.voipapp.data.model.AccountStatus
import com.yourpackage.voipapp.ui.theme.CallAccentGreen

@Composable
fun AccountStatusCard(
    status: AccountStatus,
    onTopUpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF2C2C2E))
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRIMARY ACCOUNT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CallAccentGreen.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CallAccentGreen)
                )
                Text(
                    text = "CONNECTED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CallAccentGreen,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Text(
            text = status.accountNumber,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Balance",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = status.balance,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CallAccentGreen
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onTopUpClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Top Up",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
```

**`ui/components/QuickActionsSection.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.voipapp.ui.theme.CallTextPrimary

@Composable
fun QuickActionsSection(
    onKeypadClick: () -> Unit,
    onContactsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.Dialpad,
            label = "Keypad",
            iconBg = Color(0xFF007AFF),
            modifier = Modifier.weight(1f),
            onClick = onKeypadClick
        )
        QuickActionButton(
            icon = Icons.Default.Person,
            label = "Contacts",
            iconBg = Color(0xFF34C759),
            modifier = Modifier.weight(1f),
            onClick = onContactsClick
        )
        QuickActionButton(
            icon = Icons.Default.History,
            label = "History",
            iconBg = Color(0xFFFF9500),
            modifier = Modifier.weight(1f),
            onClick = onHistoryClick
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = CallTextPrimary
        )
    }
}
```

**`ui/components/FavoritesSection.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourpackage.voipapp.data.model.FavoriteContact
import com.yourpackage.voipapp.ui.theme.CallAccentGreen
import com.yourpackage.voipapp.ui.theme.CallTextPrimary

@Composable
fun FavoritesSection(
    favorites: List<FavoriteContact>,
    onFavoriteClick: (String) -> Unit,
    onAddClick: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        items(favorites) { favorite ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { onFavoriteClick(favorite.id) }
            ) {
                Box {
                    if (favorite.avatarUrl != null) {
                        AsyncImage(
                            model = favorite.avatarUrl,
                            contentDescription = favorite.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .shadow(4.dp, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                        )
                    }
                    if (favorite.hasQuickCall) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(CallAccentGreen)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
                Text(
                    text = favorite.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = CallTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { onAddClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color(0xFFD1D1D6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Add",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = CallTextPrimary
                )
            }
        }
    }
}
```

**`ui/components/RecentCallsSection.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourpackage.voipapp.data.model.CallDirection
import com.yourpackage.voipapp.data.model.RecentCall
import com.yourpackage.voipapp.ui.theme.CallAccentBlue
import com.yourpackage.voipapp.ui.theme.CallAccentGreen
import com.yourpackage.voipapp.ui.theme.CallAccentRed
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun RecentCallsSection(
    calls: List<RecentCall>,
    onCallClick: (String) -> Unit,
    onCallBack: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        calls.forEach { call ->
            RecentCallItem(
                call = call,
                onClick = { onCallClick(call.id) },
                onCallBack = { onCallBack(call.id) }
            )
        }
    }
}

@Composable
private fun RecentCallItem(
    call: RecentCall,
    onClick: () -> Unit,
    onCallBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar with badge
        Box {
            if (call.avatarUrl != null) {
                AsyncImage(
                    model = call.avatarUrl,
                    contentDescription = call.name,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = when (call.direction) {
                        CallDirection.INCOMING -> Icons.Default.CallReceived
                        CallDirection.OUTGOING -> Icons.Default.CallMade
                        CallDirection.MISSED -> Icons.Default.CallMissed
                    },
                    contentDescription = null,
                    tint = when (call.direction) {
                        CallDirection.INCOMING -> CallAccentGreen
                        CallDirection.OUTGOING -> CallAccentBlue
                        CallDirection.MISSED -> CallAccentRed
                    },
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = call.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (call.direction == CallDirection.MISSED) CallAccentRed else CallTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val typeLabel = when (call.direction) {
                CallDirection.INCOMING -> "Incoming"
                CallDirection.OUTGOING -> "Outgoing"
                CallDirection.MISSED -> "Missed"
            }
            Text(
                text = "$typeLabel • ${call.timestamp}",
                fontSize = 12.sp,
                color = CallTextSecondary
            )
        }

        // Call back button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CallAccentGreen.copy(alpha = 0.1f))
                .clickable { onCallBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call Back",
                tint = CallAccentGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
```

**`ui/components/MainBottomNavBar.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MainNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainBottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val items = listOf(
        MainNavItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        MainNavItem("Calls", Icons.Filled.Call, Icons.Outlined.Call),
        MainNavItem("Chat", Icons.Filled.Chat, Icons.Outlined.Chat),
        MainNavItem("Contacts", Icons.Filled.Person, Icons.Outlined.Person),
        MainNavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .shadow(8.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clickable { onItemSelected(index) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color.Black.copy(alpha = 0.05f) else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) Color(0xFF1A1A1A) else Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = item.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color(0xFF1A1A1A) else Color(0xFF8E8E93)
                )
            }
        }
    }
}
```

---

### 5. The Main Screen
**`ui/main/MainScreen.kt`**
```kotlin
package com.yourpackage.voipapp.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourpackage.voipapp.ui.components.*
import com.yourpackage.voipapp.ui.theme.CallBackground
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToKeypad: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallBackground)
            .systemBarsPadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            item {
                MainHeader(
                    userName = uiState.userName,
                    avatarUrl = uiState.profileAvatarUrl,
                    onProfileClick = { /* Navigate to profile */ }
                )
            }

            // Account Status Card
            item {
                AccountStatusCard(
                    status = uiState.accountStatus,
                    onTopUpClick = { viewModel.onTopUpClick() }
                )
            }

            // Quick Actions
            item {
                QuickActionsSection(
                    onKeypadClick = { viewModel.onKeypadClick(); onNavigateToKeypad() },
                    onContactsClick = { viewModel.onContactsClick() },
                    onHistoryClick = { viewModel.onHistoryClick(); onNavigateToHistory() }
                )
            }

            // Favorites Section Header
            item {
                SectionHeader(
                    title = "Favorites",
                    actionText = "Edit",
                    onActionClick = { /* Edit favorites */ }
                )
            }

            // Favorites List
            item {
                FavoritesSection(
                    favorites = uiState.favorites,
                    onFavoriteClick = { viewModel.onFavoriteClick(it) },
                    onAddClick = { viewModel.onAddFavoriteClick() }
                )
            }

            // Recent Section Header
            item {
                SectionHeader(
                    title = "Recent",
                    actionText = "See All",
                    onActionClick = { onNavigateToHistory() }
                )
            }

            // Recent Calls List
            item {
                RecentCallsSection(
                    calls = uiState.recentCalls,
                    onCallClick = { viewModel.onRecentCallClick(it) },
                    onCallBack = { viewModel.onCallBackClick(it) }
                )
            }
        }

        // Bottom Navigation
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MainBottomNavBar(
                selectedIndex = 0, // Home
                onItemSelected = { index ->
                    when (index) {
                        0 -> { /* Home - already here */ }
                        1 -> { onNavigateToHistory() }
                        2 -> { /* Navigate to Chat */ }
                        3 -> { /* Navigate to Contacts */ }
                        4 -> { onNavigateToSettings() }
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CallTextSecondary,
            letterSpacing = 0.5.sp
        )
        Text(
            text = actionText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF007AFF),
            modifier = Modifier.clickable { onActionClick() }
        )
    }
}
```

---

### 6. MainActivity Update
**`MainActivity.kt`**
```kotlin
package com.yourpackage.voipapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yourpackage.voipapp.ui.main.MainScreen
import com.yourpackage.voipapp.ui.theme.VoipAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoipAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}
```

---

### Key Android/MVVM Highlights:

1.  **State-Driven Dashboard:** The `MainViewModel` holds all the data needed for the dashboard (user info, account status, favorites, recent calls) in a single `MainUiState`. The UI observes this state and automatically updates when data changes.

2.  **Reusable Component Architecture:** The screen is broken down into modular components:
    *   `MainHeader` – Greeting and profile avatar with status.
    *   `AccountStatusCard` – Dark gradient card with live connection status.
    *   `QuickActionsSection` – Three large colored action tiles.
    *   `FavoritesSection` – Horizontally scrolling list with quick-call badges.
    *   `RecentCallsSection` – Vertical list of the latest calls with directional badges.
    *   `MainBottomNavBar` – Floating glassmorphism navigation with 5 tabs.

3.  **Gradient & Shadows:** The `AccountStatusCard` uses `Brush.linearGradient` to create the dark premium look. Buttons use `Modifier.shadow` with custom `ambientColor` and `spotColor` for that soft, colored glow.

4.  **Floating Bottom Navigation:** Uses `Box` with `Modifier.align(Alignment.BottomCenter)` to float the nav bar over the scrolling content. The nav bar uses a semi-transparent white background (`Color.White.copy(alpha = 0.95f)`) to mimic glassmorphism.

5.  **Consistent Design Language:** Uses the same `#F8F9FA` background, `#1A1A1A` primary text, `#6E6E73` secondary text, `#007AFF` blue, `#34C759` green, and `#FF9500` orange as the rest of the VoIP app, ensuring visual continuity.

6.  **Navigation Callbacks:** The `MainScreen` accepts `onNavigateToKeypad`, `onNavigateToHistory`, and `onNavigateToSettings` lambdas, making it easy to wire up to your `NavController` and maintain the single-activity architecture.