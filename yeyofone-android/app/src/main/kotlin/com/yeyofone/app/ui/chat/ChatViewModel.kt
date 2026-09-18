package com.yeyofone.app.ui.chat

import androidx.lifecycle.ViewModel
import com.yeyofone.app.data.model.ChatContact
import com.yeyofone.app.data.model.ChatMessage
import com.yeyofone.app.data.model.MessageStatus
import com.yeyofone.app.data.model.MessageType
import com.yeyofone.app.data.model.toSipIdentity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatUiState(
    val contact: ChatContact = ChatContact("demo-contact", "Noah Anderson", true),
    val messages: List<ChatMessage> = demoMessages(),
    val inputText: String = "",
    val isContactTyping: Boolean = true,
)

class ChatViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = mutableUiState.asStateFlow()

    fun onInputChanged(text: String) {
        mutableUiState.value = mutableUiState.value.copy(inputText = text.take(MAX_MESSAGE_LENGTH))
    }

    fun sendMessage() {
        val text = mutableUiState.value.inputText.trim()
        if (text.isEmpty()) return
        mutableUiState.value = mutableUiState.value.copy(
            messages = mutableUiState.value.messages + ChatMessage(
                id = UUID.randomUUID().toString(),
                content = text,
                timestamp = LocalTime.now().format(TIME_FORMAT),
                isOutgoing = true,
                status = MessageStatus.SENT,
            ),
            inputText = "",
            isContactTyping = false,
        )
    }

    fun openConversation(remoteUri: String) {
        val name = remoteUri.toSipIdentity().displayName
        mutableUiState.value = mutableUiState.value.copy(
            contact = ChatContact(remoteUri, name, statusText = "Offline"),
            messages = emptyList(),
            inputText = "",
            isContactTyping = false,
        )
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 2_000
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun demoMessages() = listOf(
    ChatMessage("1", "Hey! Are you available for a quick call?", "09:42", false),
    ChatMessage("2", "Sure, give me 5 minutes to wrap up something.", "09:43", true, status = MessageStatus.READ),
    ChatMessage(
        "3", "0:24", "09:45", false, MessageType.VOICE,
        waveformHeights = listOf(8, 14, 10, 18, 12, 16, 8, 14, 10, 6, 16, 12),
    ),
    ChatMessage("4", "Got your voice note. Calling you now!", "09:46", true, status = MessageStatus.DELIVERED),
    ChatMessage("5", "Perfect, thanks! 👍", "09:46", false),
)
