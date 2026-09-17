package com.yourpackage.voipapp.data.model

enum class CallType {
INCOMING, OUTGOING, MISSED
}

data class CallLog(
val id: String,
val contactName: String,
val contactImageUrl: String? = null, // Null means fallback to initials
val callType: CallType,
val timestamp: String,
val duration: String? = null,
val hasVoicemail: Boolean = false
)

---------------------------------------------------------------------

package com.yourpackage.voipapp.data.repository

import com.yourpackage.voipapp.data.model.CallLog
import com.yourpackage.voipapp.data.model.CallType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class CallRepository {
// Simulating data fetching
fun getCallHistory(): Flow<List<CallLog>> {
val mockData = listOf(
CallLog("1", "Noah Anderson", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100", CallType.MISSED, "10:42 AM", null, true),
CallLog("2", "Sarah Jenkins", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100", CallType.OUTGOING, "09:15 AM", "4m 12s"),
CallLog("3", "Marcus Chen", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100", CallType.INCOMING, "Yesterday, 04:30 PM", "12m 05s")
)
return flowOf(mockData)
}
}


------------------------------------------------------

package com.yourpackage.voipapp.ui.callhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourpackage.voipapp.data.model.CallLog
import com.yourpackage.voipapp.data.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CallHistoryUiState(
val isLoading: Boolean = false,
val calls: List<CallLog> = emptyList(),
val selectedTab: Int = 0 // 0: All, 1: Missed, 2: Voicemail
)

class CallHistoryViewModel(
private val repository: CallRepository = CallRepository() // In real app, inject via Hilt/Koin
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallHistoryUiState(isLoading = true))
    val uiState: StateFlow<CallHistoryUiState> = _uiState.asStateFlow()

    init {
        loadCallHistory()
    }

    private fun loadCallHistory() {
        viewModelScope.launch {
            repository.getCallHistory().collectLatest { calls ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    calls = calls
                )
            }
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        // In a real app, you would filter the list based on the tab here
    }
}

------------------------------------------------------------

package com.yourpackage.voipapp.ui.theme

import androidx.compose.ui.graphics.Color

val BackgroundGray = Color(0xFFF8F9FA)
val CardWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF6E6E73)
val AccentGreen = Color(0xFF34C759)
val AccentBlue = Color(0xFF007AFF)
val AccentRed = Color(0xFFFF3B30)
val InactiveGray = Color(0xFF8E8E93)
val NavBackground = Color(0xD9FFFFFF) // 85% white

---------------------------------------------------------------

package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourpackage.voipapp.data.model.CallLog
import com.yourpackage.voipapp.data.model.CallType
import com.yourpackage.voipapp.ui.theme.*

@Composable
fun CallHistoryItem(
call: CallLog,
onCallClick: () -> Unit
) {
Card(
modifier = Modifier
.fillMaxWidth()
.clickable { onCallClick() },
shape = RoundedCornerShape(16.dp),
colors = CardDefaults.cardColors(containerColor = CardWhite),
elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
) {
Row(
modifier = Modifier
.padding(14.dp)
.fillMaxWidth(),
verticalAlignment = Alignment.CenterVertically
) {
// Avatar with Badge
Box {
if (call.contactImageUrl != null) {
AsyncImage(
model = call.contactImageUrl,
contentDescription = "Avatar",
modifier = Modifier
.size(46.dp)
.clip(CircleShape),
contentScale = ContentScale.Crop
)
} else {
Box(
modifier = Modifier
.size(46.dp)
.clip(CircleShape)
.background(BackgroundGray),
contentAlignment = Alignment.Center
) {
Text("VM", fontWeight = FontWeight.Bold, color = TextPrimary)
}
}

                // Call Type Badge
                val (badgeColor, badgeIcon) = when(call.callType) {
                    CallType.INCOMING -> AccentGreen to Icons.Outlined.CallReceived
                    CallType.OUTGOING -> AccentBlue to Icons.Outlined.CallMade
                    CallType.MISSED -> AccentRed to Icons.Outlined.CallMissed
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(CardWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                val nameColor = if (call.callType == CallType.MISSED) AccentRed else TextPrimary
                Text(
                    text = call.contactName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = nameColor
                )
                val metaText = buildString {
                    append(call.callType.name.lowercase().replaceFirstChar { it.uppercase() })
                    append(" • ${call.timestamp}")
                    if (call.duration != null) append(" • ${call.duration}")
                }
                Text(
                    text = metaText,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (call.hasVoicemail) {
                    ActionButton(icon = Icons.Default.Voicemail, onClick = {})
                }
                ActionButton(
                    icon = Icons.Default.Call, 
                    onClick = {},
                    isPrimary = true
                )
            }
        }
    }
}

@Composable
fun ActionButton(
icon: ImageVector,
onClick: () -> Unit,
isPrimary: Boolean = false
) {
val bgColor = if (isPrimary) AccentGreen.copy(alpha = 0.1f) else BackgroundGray
val iconColor = if (isPrimary) AccentGreen else TextPrimary

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}


-----------------------------------------------------------------------

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
import com.yourpackage.voipapp.ui.theme.*

@Composable
fun BottomNavigationBar(
selectedIndex: Int,
onItemSelected: (Int) -> Unit
) {
val items = listOf(
NavItem("Calls", Icons.Filled.Call, Icons.Outlined.Call),
NavItem("Contacts", Icons.Filled.Person, Icons.Outlined.Person),
NavItem("Keypad", Icons.Filled.Apps, Icons.Outlined.Apps), // Using Apps as placeholder for 9-dot keypad
NavItem("Search", Icons.Outlined.Search, Icons.Outlined.Search)
)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .shadow(8.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(NavBackground)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onItemSelected(index) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color.Black.copy(alpha = 0.05f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) TextPrimary else InactiveGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) TextPrimary else InactiveGray
                )
            }
        }
    }
}

data class NavItem(
val label: String,
val selectedIcon: ImageVector,
val unselectedIcon: ImageVector
)


----------------------------------------------

package com.yourpackage.voipapp.ui.callhistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourpackage.voipapp.ui.components.BottomNavigationBar
import com.yourpackage.voipapp.ui.components.CallHistoryItem
import com.yourpackage.voipapp.ui.theme.*

@Composable
fun CallHistoryScreen(
viewModel: CallHistoryViewModel = viewModel()
) {
val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BackgroundGray,
        bottomBar = {
            // We use a Box to float the bottom bar over the content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp), // Float above bottom edge
                contentAlignment = Alignment.BottomCenter
            ) {
                BottomNavigationBar(
                    selectedIndex = 0, // Hardcoded for this screen
                    onItemSelected = { /* Handle navigation */ }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(top = 48.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recents",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    // Edit Icon placeholder
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Filter Tabs
                val tabs = listOf("All", "Missed", "Voicemail")
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
                                .background(if (isSelected) CardWhite else Color.Transparent)
                                .clickable { viewModel.onTabSelected(index) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // Call List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp), // Extra bottom padding for floating nav
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // In a real app, you would group by date here
                item {
                    Text(
                        text = "TODAY",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                
                items(uiState.calls) { call ->
                    CallHistoryItem(call = call, onCallClick = { /* Handle call */ })
                }
            }
        }
    }
}

-----------------------------------

Key Android/MVVM Considerations:

    StateFlow & Compose: The CallHistoryViewModel uses StateFlow to hold the CallHistoryUiState. The Compose UI observes this state using collectAsState(). This ensures that when the data changes, the UI automatically recomposes.

    Separation of Concerns: The UI (CallHistoryScreen and components) knows nothing about where the data comes from. The CallRepository handles data fetching. This makes it easy to swap the mock data for a real Room database or API call later without changing the UI.

    Floating Bottom Navigation: In the Scaffold, I placed the BottomNavigationBar inside a Box within the bottomBar slot. This allows it to float over the content instead of pushing the content up, mimicking the web design. I also added padding(bottom = 120.dp) to the LazyColumn so the last call item isn't hidden behind the floating menu.

    Icons: The design uses standard Material Icons (Icons.Filled.Call, Icons.Outlined.CallReceived, etc.). For the exact 9-circle Keypad icon, you would typically create a custom ImageVector in Android Studio or use an SVG import, but the logic remains the same.

    Dynamic Theming: The colors used in Color.kt match the hex codes from the web design, ensuring visual consistency across platforms.