Here is the complete Android implementation of the **Chat Screen** using **Kotlin**, **Jetpack Compose**, and the **MVVM** design pattern, following the exact same architecture as the previous screens.

---

### 1. Update Project Structure
```text
com.yourpackage.voipapp
│
├── data
│   └── model
│       ├── ChatContact.kt          <-- New
│       └── ChatMessage.kt          <-- New
│
├── ui
│   ├── components
│   │   ├── ChatHeader.kt           <-- New
│   │   ├── MessageBubble.kt        <-- New
│   │   ├── TypingIndicator.kt      <-- New
│   │   └── ChatInputBar.kt         <-- New
│   │
│   └── chat
│       ├── ChatScreen.kt           <-- New
│       └── ChatViewModel.kt        <-- New
```

---

### 2. The Models
**`data/model/ChatContact.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

data class ChatContact(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val statusText: String = "Online"
)
```

**`data/model/ChatMessage.kt`**
```kotlin
package com.yourpackage.voipapp.data.model

enum class MessageType {
    TEXT, VOICE, IMAGE
}

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ
}

data class ChatMessage(
    val id: String,
    val content: String, // For VOICE, this would be the duration (e.g., "0:24")
    val timestamp: String,
    val isOutgoing: Boolean,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val waveformHeights: List<Int> = emptyList() // Only used for VOICE
)
```

---

### 3. The ViewModel (MVVM)
**`ui/chat/ChatViewModel.kt`**
```kotlin
package com.yourpackage.voipapp.ui.chat

import androidx.lifecycle.ViewModel
import com.yourpackage.voipapp.data.model.ChatContact
import com.yourpackage.voipapp.data.model.ChatMessage
import com.yourpackage.voipapp.data.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatUiState(
    val contact: ChatContact = ChatContact(
        id = "1",
        name = "Noah Anderson",
        avatarUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100",
        isOnline = true
    ),
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isContactTyping: Boolean = true,
    val dateLabel: String = "Today"
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    private fun loadMessages() {
        val mockMessages = listOf(
            ChatMessage(
                id = "1",
                content = "Hey! Are you available for a quick call?",
                timestamp = "09:42 AM",
                isOutgoing = false
            ),
            ChatMessage(
                id = "2",
                content = "Sure, give me 5 minutes to wrap up something.",
                timestamp = "09:43 AM",
                isOutgoing = true
            ),
            ChatMessage(
                id = "3",
                content = "0:24",
                timestamp = "09:45 AM",
                isOutgoing = false,
                type = MessageType.VOICE,
                waveformHeights = listOf(8, 14, 10, 18, 12, 16, 8, 14, 10, 6)
            ),
            ChatMessage(
                id = "4",
                content = "Got your voice note. Calling you now!",
                timestamp = "09:46 AM",
                isOutgoing = true
            ),
            ChatMessage(
                id = "5",
                content = "Perfect, thanks! 👍",
                timestamp = "09:46 AM",
                isOutgoing = false
            )
        )
        _uiState.value = _uiState.value.copy(messages = mockMessages)
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val newMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = text,
            timestamp = "Now",
            isOutgoing = true
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + newMessage,
            inputText = ""
        )
    }

    fun onVoiceCallClick() {
        // Handle voice call initiation
    }

    fun onVideoCallClick() {
        // Handle video call initiation
    }

    fun onPlayVoiceMessage(messageId: String) {
        // Handle audio playback
    }
}
```

---

### 4. UI Components

**`ui/components/ChatHeader.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yourpackage.voipapp.data.model.ChatContact
import com.yourpackage.voipapp.ui.theme.CallAccentGreen
import com.yourpackage.voipapp.ui.theme.CallTextPrimary

@Composable
fun ChatHeader(
    contact: ChatContact,
    onBackClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CallTextPrimary
            )
        }

        // Avatar with online indicator
        Box {
            if (contact.avatarUrl != null) {
                AsyncImage(
                    model = contact.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
            }
            if (contact.isOnline) {
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

        Spacer(modifier = Modifier.width(10.dp))

        // Name and status
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = CallTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (contact.isOnline) CallAccentGreen else Color(0xFF8E8E93)
            )
        }

        // Action icons
        IconButton(onClick = onVoiceCallClick) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Voice Call",
                tint = CallTextPrimary
            )
        }
        IconButton(onClick = onVideoCallClick) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Video Call",
                tint = CallTextPrimary
            )
        }
    }
}
```

**`ui/components/MessageBubble.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.voipapp.data.model.ChatMessage
import com.yourpackage.voipapp.data.model.MessageStatus
import com.yourpackage.voipapp.data.model.MessageType

@Composable
fun MessageBubble(
    message: ChatMessage,
    onPlayVoice: () -> Unit = {}
) {
    val bubbleColor = if (message.isOutgoing) Color(0xFF007AFF) else Color.White
    val textColor = if (message.isOutgoing) Color.White else Color(0xFF1A1A1A)
    val metaColor = if (message.isOutgoing) Color.White.copy(alpha = 0.8f) else Color(0xFF8E8E93)
    
    val shape = if (message.isOutgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(
                    elevation = if (message.isOutgoing) 4.dp else 2.dp,
                    shape = shape,
                    ambientColor = if (message.isOutgoing) Color(0x33007AFF) else Color(0x0D000000),
                    spotColor = if (message.isOutgoing) Color(0x33007AFF) else Color(0x0D000000)
                )
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.VOICE -> VoiceMessageContent(
                        message = message,
                        textColor = textColor,
                        onPlay = onPlayVoice
                    )
                    else -> {
                        Text(
                            text = message.content,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Timestamp and status
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = metaColor
                    )
                    if (message.isOutgoing) {
                        Icon(
                            imageVector = if (message.status == MessageStatus.READ) 
                                Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = "Status",
                            tint = metaColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceMessageContent(
    message: ChatMessage,
    textColor: Color,
    onPlay: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.widthIn(min = 180.dp)
    ) {
        // Play button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (message.isOutgoing) Color.White.copy(alpha = 0.25f)
                    else Color(0xFF007AFF).copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = if (message.isOutgoing) Color.White else Color(0xFF007AFF),
                modifier = Modifier.size(16.dp)
            )
        }

        // Waveform
        Row(
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            message.waveformHeights.forEach { height ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (message.isOutgoing) Color.White.copy(alpha = 0.5f)
                            else Color(0xFF007AFF).copy(alpha = 0.4f)
                        )
                )
            }
        }

        // Duration
        Text(
            text = message.content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
```

**`ui/components/TypingIndicator.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TypingDot(delayMillis = 0)
            TypingDot(delayMillis = 200)
            TypingDot(delayMillis = 400)
        }
    }
}

@Composable
private fun TypingDot(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0f at 0 with LinearEasing
                -6f at 300 with LinearEasing
                0f at 600 with LinearEasing
                0f at 1400 with LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis)
        ),
        label = "offsetY"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.4f at 0 with LinearEasing
                1f at 300 with LinearEasing
                0.4f at 600 with LinearEasing
                0.4f at 1400 with LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis)
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .offset(y = offsetY.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(Color(0xFFC7C7CC).copy(alpha = alpha))
    )
}
```

**`ui/components/ChatInputBar.kt`**
```kotlin
package com.yourpackage.voipapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Attach button
        IconButton(
            onClick = onAttach,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F5))
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Attach",
                tint = Color(0xFF6E6E73),
                modifier = Modifier.size(20.dp)
            )
        }

        // Text input
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFF0F0F5))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Type a message...",
                    color = Color(0xFF8E8E93),
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = Color(0xFF1A1A1A),
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color(0xFF007AFF)),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Send button
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x4D007AFF),
                    spotColor = Color(0x4D007AFF)
                )
                .clip(CircleShape)
                .background(Color(0xFF007AFF))
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

---

### 5. The Main Screen
**`ui/chat/ChatScreen.kt`**

```kotlin
package com.yourpackage.voipapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.yourpackage.voipapp.ui.components.ChatHeader
import com.yourpackage.voipapp.ui.components.ChatInputBar
import com.yourpackage.voipapp.ui.components.MessageBubble
import com.yourpackage.voipapp.ui.components.TypingIndicator
import com.yourpackage.voipapp.ui.theme.CallBackground
import com.yourpackage.voipapp.ui.theme.CallTextSecondary

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isContactTyping) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallBackground)
            .systemBarsPadding()
    ) {
        // --- Header ---
        ChatHeader(
            contact = uiState.contact,
            onBackClick = onNavigateBack,
            onVoiceCallClick = { viewModel.onVoiceCallClick() },
            onVideoCallClick = { viewModel.onVideoCallClick() }
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color(0x0D000000)
        )

        // --- Messages List ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Date Divider
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.dateLabel.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CallTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0F000000))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Messages
            items(uiState.messages) { message ->
                MessageBubble(
                    message = message,
                    onPlayVoice = { viewModel.onPlayVoiceMessage(message.id) }
                )
            }

            // Typing Indicator
            if (uiState.isContactTyping) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    TypingIndicator()
                }
            }
        }

        // --- Input Bar ---
        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color(0x0D000000)
        )
        ChatInputBar(
            text = uiState.inputText,
            onTextChange = { viewModel.onInputChanged(it) },
            onSend = { viewModel.onSendMessage() },
            onAttach = { /* Handle attachment */ }
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
import com.yourpackage.voipapp.ui.chat.ChatScreen
import com.yourpackage.voipapp.ui.theme.VoipAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoipAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChatScreen(
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

1.  **State-Driven Chat:** The `ChatViewModel` holds all state (messages, input text, typing status). When `onSendMessage()` is called, it appends the new message to the list and clears the input. Compose automatically recomposes the list, and `LaunchedEffect` triggers the auto-scroll to the bottom.

2.  **Auto-Scroll on New Messages:** Using `rememberLazyListState()` and `LaunchedEffect(uiState.messages.size)`, the list automatically scrolls to the newest message whenever a new one arrives or the typing indicator appears.

3.  **Custom Message Bubble Shapes:** The `MessageBubble` uses `RoundedCornerShape` with a flat corner (bottom-left for incoming, bottom-right for outgoing). This is the classic chat bubble shape that clearly indicates message direction.

4.  **Voice Message Support:** The `VoiceMessageContent` composable renders a play button, a dynamic waveform visualization (using the `waveformHeights` list), and the duration. The waveform bars are generated dynamically, so you can pass real audio amplitude data.

5.  **Animated Typing Indicator:** Uses `rememberInfiniteTransition` with `keyframes` to create a staggered bouncing animation for the three dots, with each dot starting its animation 200ms apart.

6.  **Read Receipts:** The message bubble shows a single or double checkmark based on the `MessageStatus` enum. In a real app, you would update this state when the recipient reads the message (via your VoIP/chat backend).

7.  **Reusable Components:** The `ChatHeader`, `MessageBubble`, `TypingIndicator`, and `ChatInputBar` are all extracted into `ui/components`, making the `ChatScreen` clean and focused on layout.

8.  **Consistent Design Language:** Uses the same `#F8F9FA` background, `#1A1A1A` primary text, `#007AFF` accent blue, and elevated white cards as the rest of the VoIP app.