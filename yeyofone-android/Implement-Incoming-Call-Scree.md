Here is the complete Android implementation of the **Incoming Call Screen with Full Controls** using **Kotlin**, **Jetpack Compose**, and the **MVVM** design pattern, following the exact same architecture as the previous screens.

---

### 1. Update Project Structure
```text
com.yourpackage.voipapp
│
├── data
│   └── model
│       └── IncomingCaller.kt       <-- New
│
├── ui
│   ├── components
│   │   └── IncomingCallActions.kt  <-- New
│   │
│   └── incomingcall
│       ├── IncomingCallScreen.kt   <-- New
│       └── IncomingCallViewModel.kt <-- New
```

---

### 2. The Model
**`data/model/IncomingCaller.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

data class IncomingCaller(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val avatarUrl: String? = null,
    val accountType: String = "Mobile"
)
```

---

### 3. The ViewModel (MVVM)
**`ui/incomingcall/IncomingCallViewModel.kt`**
This holds the state of the incoming call and manages all 7 toggle/action buttons.

```kotlin
package com.yourpackage.voipapp.ui.incomingcall

import androidx.lifecycle.ViewModel
import com.yourpackage.voipapp.data.model.IncomingCaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncomingCallUiState(
    val caller: IncomingCaller = IncomingCaller(
        id = "1",
        name = "Noah Anderson",
        phoneNumber = "+1 (555) 019-2834",
        avatarUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=300",
        accountType = "Mobile"
    ),
    val isRinging: Boolean = true,
    val isMuted: Boolean = false,
    val isOnHold: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isKeypadVisible: Boolean = false
)

class IncomingCallViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingCallUiState())
    val uiState: StateFlow<IncomingCallUiState> = _uiState.asStateFlow()

    // --- Action Handlers ---

    fun toggleMute() {
        _uiState.value = _uiState.value.copy(isMuted = !_uiState.value.isMuted)
    }

    fun toggleHold() {
        _uiState.value = _uiState.value.copy(isOnHold = !_uiState.value.isOnHold)
    }

    fun toggleSpeaker() {
        _uiState.value = _uiState.value.copy(isSpeakerOn = !_uiState.value.isSpeakerOn)
    }

    fun toggleKeypad() {
        _uiState.value = _uiState.value.copy(isKeypadVisible = !_uiState.value.isKeypadVisible)
    }

    fun onTransfer() {
        // Handle blind transfer (send call to another extension immediately)
    }

    fun onConsultTransfer() {
        // Handle consult transfer (speak with third party before transferring)
    }

    fun onHangUp() {
        // Disconnect the call
        _uiState.value = _uiState.value.copy(isRinging = false)
    }
}
```

---

### 4. UI Components
**`ui/components/IncomingCallActions.kt`**
A reusable component for both the secondary grid buttons and the prominent hang-up button.

```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun CallGridActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFFE5E5EA) else Color.White
    val iconColor = Color(0xFF1A1A1A)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x0D000000),
                    spotColor = Color(0x0D000000)
                )
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = CallTextSecondary
        )
    }
}

@Composable
fun HangUpButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color(0x4DFF3B30),
                spotColor = Color(0x4DFF3B30)
            )
            .clip(CircleShape)
            .background(Color(0xFFFF3B30))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.CallEnd,
            contentDescription = "Hang Up",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}
```

---

### 5. The Main Screen
**`ui/incomingcall/IncomingCallScreen.kt`**

```kotlin
package com.yourpackage.voipapp.ui.incomingcall

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yourpackage.voipapp.ui.components.CallGridActionButton
import com.yourpackage.voipapp.ui.components.HangUpButton
import com.yourpackage.voipapp.ui.theme.CallAccentGreen
import com.yourpackage.voipapp.ui.theme.CallBackground
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun IncomingCallScreen(
    viewModel: IncomingCallViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallBackground)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // --- Top Section ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Incoming Call Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(CallAccentGreen.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = CallAccentGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "INCOMING CALL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CallAccentGreen,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Avatar with Pulsing Rings
            Box(contentAlignment = Alignment.Center) {
                PulsingRings()

                if (uiState.caller.avatarUrl != null) {
                    AsyncImage(
                        model = uiState.caller.avatarUrl,
                        contentDescription = "Caller Avatar",
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .shadow(15.dp, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("NA", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Info
            Text(
                text = uiState.caller.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = CallTextPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = uiState.caller.phoneNumber,
                fontSize = 15.sp,
                color = CallTextSecondary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Bottom Action Area ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 6-Button Grid (Mute, Hold, Keypad, Transfer, Consult, Speaker)
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallGridActionButton(
                        icon = Icons.Default.MicOff,
                        label = "Mute",
                        isActive = uiState.isMuted,
                        onClick = { viewModel.toggleMute() }
                    )
                    CallGridActionButton(
                        icon = Icons.Default.Pause,
                        label = "Hold",
                        isActive = uiState.isOnHold,
                        onClick = { viewModel.toggleHold() }
                    )
                    CallGridActionButton(
                        icon = Icons.Default.Dialpad,
                        label = "Keypad",
                        isActive = uiState.isKeypadVisible,
                        onClick = { viewModel.toggleKeypad() }
                    )
                }

                // Row 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallGridActionButton(
                        icon = Icons.Default.SwapHoriz,
                        label = "Transfer",
                        onClick = { viewModel.onTransfer() }
                    )
                    CallGridActionButton(
                        icon = Icons.Default.PersonAdd,
                        label = "Consult",
                        onClick = { viewModel.onConsultTransfer() }
                    )
                    CallGridActionButton(
                        icon = Icons.Default.VolumeUp,
                        label = "Speaker",
                        isActive = uiState.isSpeakerOn,
                        onClick = { viewModel.toggleSpeaker() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Centered Hang Up Button
            HangUpButton(onClick = { viewModel.onHangUp() })
        }
    }
}

@Composable
private fun PulsingRings() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Ring 1
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    // Ring 2 (Delayed by 500ms)
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale2"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(scale1)
                .clip(CircleShape)
                .border(2.dp, CallAccentGreen.copy(alpha = alpha1), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(scale2)
                .clip(CircleShape)
                .border(2.dp, CallAccentGreen.copy(alpha = alpha2), CircleShape)
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
import com.yourpackage.voipapp.ui.incomingcall.IncomingCallScreen
import com.yourpackage.voipapp.ui.theme.VoipAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoipAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    IncomingCallScreen(
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

1.  **State-Driven Toggle Buttons:** The `IncomingCallViewModel` tracks `isMuted`, `isOnHold`, `isSpeakerOn`, and `isKeypadVisible` in the `IncomingCallUiState`. When a user taps one of these buttons, the ViewModel flips the boolean. The UI automatically recomposes with the button's background changing to grey (`#E5E5EA`) to indicate an active state.

2.  **Reusable Action Components:**
    *   `CallGridActionButton` handles all 6 secondary actions in the grid (with a toggle state built-in).
    *   `HangUpButton` is a dedicated component for the prominent red destructive action, with a colored glow shadow.

3.  **Two-Row Grid Layout:** Instead of a rigid `Grid`, I used two `Row` composables with `Arrangement.spacedBy(16.dp)` to precisely control the spacing and keep the buttons compact and close together, matching the web design's `gap: 24px 16px`.

4.  **Animated Pulsing Rings:** Using `rememberInfiniteTransition` and `animateFloat`, two expanding/fading rings pulse around the avatar, with the second ring delayed by 500ms to create a staggered effect that visually signals an incoming call.

5.  **Custom Icons for Consult & Transfer:**
    *   **Transfer:** `Icons.Default.SwapHoriz` (opposing arrows).
    *   **Consult:** `Icons.Default.PersonAdd` (person with a plus, indicating adding a third party).
    *   **Hold:** `Icons.Default.Pause` (pause bars).

6.  **Action Separation:** The hang-up button is visually separated from the secondary actions by a `Spacer(30.dp)`, making it unmistakable as the primary, destructive action.