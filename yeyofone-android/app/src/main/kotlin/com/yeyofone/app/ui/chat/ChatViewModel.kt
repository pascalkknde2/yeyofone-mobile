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
    /** Every conversation this session, keyed by [ChatContact.id] (the contact's number). */
    val conversations: Map<String, List<ChatMessage>> = emptyMap(),
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
        val state = mutableUiState.value
        val messages = state.messages + ChatMessage(
            id = UUID.randomUUID().toString(),
            content = text,
            timestamp = LocalTime.now().format(TIME_FORMAT),
            isOutgoing = true,
            status = MessageStatus.SENT,
        )
        mutableUiState.value = state.copy(
            messages = messages,
            inputText = "",
            isContactTyping = false,
            conversations = state.contact?.let { state.conversations + (it.id to messages) } ?: state.conversations,
        )
    }

    /** Opens the conversation with the caller of an ended call. */
    fun openConversation(remoteUri: String) {
        val identity = remoteUri.toSipIdentity()
        openConversation(identity.extension.ifEmpty { remoteUri }, identity.displayName)
    }

    /** Opens (or reopens, keeping its messages) the conversation with [number]. */
    fun openConversation(number: String, name: String) {
        val state = mutableUiState.value
        val id = number.trim()
        mutableUiState.value = state.copy(
            contact = ChatContact(id, name.ifBlank { id }, statusText = ""),
            messages = state.conversations[id].orEmpty(),
            inputText = if (state.contact?.id == id) state.inputText else "",
            isContactTyping = false,
        )
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 2_000
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}

