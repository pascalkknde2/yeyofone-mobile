Here is the complete Android implementation of the **Keypad Dialing Screen** using **Kotlin**, **Jetpack Compose**, and the **MVVM** design pattern, matching the exact visual language and architecture of the previous screens.

---

### 1. Update Project Structure
Add these files to your existing MVVM structure:

```text
com.yourpackage.voipapp
│
├── ui
│   ├── components
│   │   └── DialPad.kt          <-- New
│   │
│   └── dialpad
│       ├── DialPadScreen.kt    <-- New
│       └── DialPadViewModel.kt <-- New
│
└── MainActivity.kt             <-- Update to host the new screen
```

---

### 2. The ViewModel (MVVM)
**`ui/dialpad/DialPadViewModel.kt`**
This handles the state of the typed number and backspace logic.

```kotlin
package com.yourpackage.voipapp.ui.dialpad

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DialPadUiState(
    val typedNumber: String = "",
    val maxLength: Int = 15
) {
    val isNumberEmpty: Boolean get() = typedNumber.isEmpty()
    val canBackspace: Boolean get() = typedNumber.isNotEmpty()
}

class DialPadViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DialPadUiState())
    val uiState: StateFlow<DialPadUiState> = _uiState.asStateFlow()

    fun onNumberPressed(number: String) {
        val current = _uiState.value.typedNumber
        if (current.length < _uiState.value.maxLength) {
            _uiState.value = _uiState.value.copy(typedNumber = current + number)
        }
    }

    fun onBackspacePressed() {
        val current = _uiState.value.typedNumber
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(typedNumber = current.dropLast(1))
        }
    }

    fun onCallPressed() {
        // Handle initiating the call with _uiState.value.typedNumber
    }

    fun clearNumber() {
        _uiState.value = _uiState.value.copy(typedNumber = "")
    }
}
```

---

### 3. UI Components
**`ui/components/DialPad.kt`**
A reusable dial pad grid component.

```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

data class DialKey(
    val number: String,
    val letters: String? = null
)

@Composable
fun DialPad(
    onNumberClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        DialKey("1"), DialKey("2", "ABC"), DialKey("3", "DEF"),
        DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
        DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
        DialKey("*"), DialKey("0", "+"), DialKey("#")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chunk keys into rows of 3
        keys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowKeys.forEach { key ->
                    DialKeyButton(
                        key = key,
                        onClick = { onNumberClick(key.number) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DialKeyButton(
    key: DialKey,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFFE8E8E8))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                color = CallTextPrimary,
                lineHeight = 28.sp
            )
            if (key.letters != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = key.letters,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = CallTextSecondary,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
```

**`ui/components/DialPadTopBar.kt`** (If you want it modular, otherwise inline it)
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourpackage.voipapp.ui.theme.CallTextPrimary

@Composable
fun DialPadTopBar(
    onNavigateBack: () -> Unit,
    onAddContact: () -> Unit,
    onMoreOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onAddContact) {
                Icon(
                    imageVector = Icons.Outlined.PersonAdd,
                    contentDescription = "Add Contact",
                    tint = CallTextPrimary
                )
            }
            IconButton(onClick = onMoreOptions) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
                    tint = CallTextPrimary
                )
            }
        }
    }
}
```

---

### 4. The Main Screen
**`ui/dialpad/DialPadScreen.kt`**

```kotlin
package com.yourpackage.voipapp.ui.dialpad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourpackage.voipapp.ui.components.DialPad
import com.yourpackage.voipapp.ui.components.DialPadTopBar
import com.yourpackage.voipapp.ui.theme.CallBackground
import com.yourpackage.voipapp.ui.theme.CallTextPrimary
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun DialPadScreen(
    viewModel: DialPadViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallBackground)
            .systemBarsPadding()
    ) {
        // Top Bar
        DialPadTopBar(
            onNavigateBack = onNavigateBack,
            onAddContact = { /* Add contact logic */ },
            onMoreOptions = { /* Show more options */ }
        )

        // Number Display Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Typed Number or Placeholder
            if (uiState.isNumberEmpty) {
                Text(
                    text = "Enter number",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = CallTextSecondary
                )
            } else {
                Text(
                    text = uiState.typedNumber,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = CallTextPrimary,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backspace Button (only visible when there's a number)
            if (uiState.canBackspace) {
                IconButton(
                    onClick = { viewModel.onBackspacePressed() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = CallTextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                // Placeholder to maintain layout height
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Keypad Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            DialPad(
                onNumberClick = { viewModel.onNumberPressed(it) },
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }

        // Bottom Call Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 60.dp, top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.onCallPressed() },
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x4D34C759),
                        spotColor = Color(0x4D34C759)
                    )
                    .clip(CircleShape)
                    .background(Color(0xFF34C759))
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
```

---

### 5. MainActivity
**`MainActivity.kt`**

```kotlin
package com.yourpackage.voipapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yourpackage.voipapp.ui.dialpad.DialPadScreen
import com.yourpackage.voipapp.ui.theme.VoipAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoipAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // You can swap this out for OutgoingCallScreen() or CallHistoryScreen()
                    DialPadScreen(
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

1.  **State-Driven UI:** The `DialPadViewModel` holds `typedNumber` in a `StateFlow`. The UI observes this with `collectAsState()`. When a user taps a number, the ViewModel appends it to the string, and Compose automatically re-renders the display area and backspace button.
2.  **Reusable Keypad Component:** I extracted the 3x4 grid into a `DialPad.kt` composable. It uses `chunked(3)` to neatly arrange the keys into rows without hardcoding every row. This makes it very easy to modify or reuse elsewhere.
3.  **Smart Backspace Visibility:** The backspace button only appears when `uiState.canBackspace` is true. When the display is empty, a `Spacer` of the same height is used to prevent the layout from shifting vertically.
4.  **Shadow & Elevation for Call Button:** The green call button uses a `Modifier.shadow()` with a custom green ambient/spot color (`0x4D34C759`) to create that subtle, premium glow effect matching the web design.
5.  **Consistent Design Language:** The screen uses the exact same `CallBackground`, `CallTextPrimary`, and top bar layout as the previous screens, ensuring a cohesive app experience.