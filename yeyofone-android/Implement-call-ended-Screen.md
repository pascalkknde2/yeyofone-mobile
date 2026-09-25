Here is the complete **Android implementation** of the Call Ended Screen using **Kotlin**, **Jetpack Compose**, and the **MVVM** pattern, following the exact same architecture as all previous screens.

---

### 1. Update Project Structure
```text
com.yourpackage.voipapp
│
├── data
│   └── model
│       └── CallSummary.kt              <-- New
│
├── ui
│   ├── components
│   │   ├── CallSummaryCard.kt          <-- New
│   │   └── CallEndedActions.kt         <-- New
│   │
│   └── callended
│       ├── CallEndedScreen.kt          <-- New
│       └── CallEndedViewModel.kt       <-- New
│
└── MainActivity.kt                     <-- Update
```

---

### 2. The Model
**`data/model/CallSummary.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

enum class CallDirection {
    INCOMING, OUTGOING, MISSED
}

data class CallSummary(
    val id: String = "",
    val callerName: String,
    val callerAvatarUrl: String? = null,
    val direction: CallDirection = CallDirection.OUTGOING,
    val durationSeconds: Int = 0,
    val timestamp: String = ""
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val callTypeLabel: String
        get() = when (direction) {
            CallDirection.INCOMING -> "Incoming Call"
            CallDirection.OUTGOING -> "Outgoing Call"
            CallDirection.MISSED -> "Missed Call"
        }
}
```

---

### 3. The ViewModel
**`ui/callended/CallEndedViewModel.kt`**
```kotlin
package com.yourpackage.voipapp.ui.callended

import androidx.lifecycle.ViewModel
import com.yourpackage.voipapp.data.model.CallDirection
import com.yourpackage.voipapp.data.model.CallSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallEndedUiState(
    val summary: CallSummary = CallSummary(
        id = "1",
        callerName = "Noah Anderson",
        callerAvatarUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=200",
        direction = CallDirection.OUTGOING,
        durationSeconds = 263, // 04:23
        timestamp = "10:42 AM"
    )
)

class CallEndedViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallEndedUiState())
    val uiState: StateFlow<CallEndedUiState> = _uiState.asStateFlow()

    // --- Action Handlers ---

    fun onCallAgain() {
        // In a real app, navigate to the call screen or trigger the dialer
        // e.g., navController.navigate("outgoing_call/${summary.callerName}")
    }

    fun onSendMessage() {
        // In a real app, navigate to the ChatScreen for this contact
        // e.g., navController.navigate("chat/${summary.id}")
    }

    fun onClose() {
        // Navigate back to home or call history
        // e.g., navController.popBackStack("home", inclusive = false)
    }

    fun onDismiss() {
        // Handle swipe-to-dismiss or system back
        onClose()
    }
}
```

---

### 4. UI Components

**`ui/components/CallSummaryCard.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourpackage.voipapp.data.model.CallSummary
import com.yourpackage.voipapp.ui.theme.CallAccentRed
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun CallSummaryCard(
    summary: CallSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x0A000000),
                spotColor = Color(0x0A000000)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CallAccentRed.copy(alpha = 0.1f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = null,
                tint = CallAccentRed,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "CALL ENDED",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CallAccentRed,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Avatar
        if (summary.callerAvatarUrl != null) {
            AsyncImage(
                model = summary.callerAvatarUrl,
                contentDescription = "Caller Avatar",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .shadow(8.dp, CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = summary.callerName.take(2).uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Caller Name
        Text(
            text = summary.callerName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = CallTextPrimary,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Call Type
        Text(
            text = summary.callTypeLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = CallTextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x0F000000))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Details Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailColumn(
                label = "Duration",
                value = summary.formattedDuration,
                modifier = Modifier.weight(1f)
            )
            DetailColumn(
                label = "Time",
                value = summary.timestamp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DetailColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = CallTextSecondary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = CallTextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}
```

**`ui/components/CallEndedActions.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
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

@Composable
fun CallEndedActions(
    onCallAgain: () -> Unit,
    onSendMessage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary Action - Call Again
        ActionButton(
            icon = Icons.Default.Call,
            label = "Call Again",
            backgroundColor = Color(0xFF1A1A1A),
            contentColor = Color.White,
            elevation = 6.dp,
            onClick = onCallAgain
        )

        // Secondary Action - Send Message
        ActionButton(
            icon = Icons.Default.ChatBubble,
            label = "Send Message",
            backgroundColor = Color.White,
            contentColor = Color(0xFF1A1A1A),
            elevation = 2.dp,
            onClick = onSendMessage
        )

        // Destructive Action - Close
        ActionButton(
            icon = Icons.Default.Close,
            label = "Close",
            backgroundColor = Color(0x1AFF3B30),
            contentColor = Color(0xFFFF3B30),
            elevation = 0.dp,
            onClick = onClose
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x0D000000),
                spotColor = Color(0x0D000000)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
```

---

### 5. The Main Screen
**`ui/callended/CallEndedScreen.kt`**
```kotlin
package com.yourpackage.voipapp.ui.callended

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.yourpackage.voipapp.ui.components.CallEndedActions
import com.yourpackage.voipapp.ui.components.CallSummaryCard
import com.yourpackage.voipapp.ui.theme.CallBackground
import com.yourpackage.voipapp.ui.theme.CallTextPrimary

@Composable
fun CallEndedScreen(
    viewModel: CallEndedViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.onClose()
                    onNavigateBack()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = CallTextPrimary
                )
            }
            Text(
                text = "Call Ended",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary,
                letterSpacing = (-0.3).sp
            )
            IconButton(onClick = { /* More options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
                    tint = CallTextPrimary
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color(0x0D000000)
        )

        // --- Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Summary Card
            CallSummaryCard(summary = uiState.summary)

            // Action Buttons
            CallEndedActions(
                onCallAgain = { viewModel.onCallAgain() },
                onSendMessage = { viewModel.onSendMessage() },
                onClose = {
                    viewModel.onClose()
                    onNavigateBack()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
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
import com.yourpackage.voipapp.ui.callended.CallEndedScreen
import com.yourpackage.voipapp.ui.theme.VoipAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoipAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CallEndedScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}
```

---

### Key Android/MVVM Highlights:

1. **State-Driven UI:** The `CallEndedViewModel` exposes an `IncomingCallUiState` (renamed `CallEndedUiState`) via `StateFlow`. When the screen loads, the ViewModel is initialized with a default `CallSummary`. In a real app, you would pass the call details from the previous screen via `SavedStateHandle` or navigation arguments.

2. **Computed Properties:** The `CallSummary` model has computed properties `formattedDuration` and `callTypeLabel` that automatically convert raw data (`durationSeconds`, `CallDirection`) into display-ready strings. This keeps the UI clean and the formatting logic reusable.

3. **Reusable Summary Card:** `CallSummaryCard` is a self-contained composable that handles the entire layout: status badge, avatar, caller name, call type, divider, and the duration/time details row. It uses `weight(1f)` on the detail columns to evenly distribute space.

4. **Modular Action Buttons:** `CallEndedActions` renders three distinct buttons, each with a clear visual hierarchy:
    - **Primary (Call Again):** Dark background, highest elevation, white text.
    - **Secondary (Send Message):** White background, low elevation, dark text.
    - **Destructive (Close):** Red-tinted background, no elevation.

5. **Clean Navigation Callbacks:** The `onCallAgain`, `onSendMessage`, and `onClose` handlers are all routed through the ViewModel, which is best practice. You can later hook these up to your `NavController` for proper navigation.

6. **Consistent Design Language:** Uses the exact same `#F8F9FA` background, `#1A1A1A` primary text, `#6E6E73` secondary text, `#FF3B30` destructive red, and the same rounded corner radius (16–24dp) as all previous screens.

7. **Divider Consistency:** Uses `HorizontalDivider` with a `0.5.dp` thickness and a subtle `0x0D000000` color to match the header separators in the Chat, Settings, and Accounts screens.