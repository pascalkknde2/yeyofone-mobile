package com.yeyofone.app.ui.chat

import androidx.lifecycle.ViewModel
import com.yeyofone.app.data.model.ChatContact
import com.yeyofone.app.data.model.ChatMessage
import com.yeyofone.app.data.model.MessageStatus
import com.yeyofone.app.data.model.toSipIdentity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatUiState(
    /** Null until a conversation is opened (e.g. from a call summary). */
    val contact: ChatContact? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isContactTyping: Boolean = false,
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

