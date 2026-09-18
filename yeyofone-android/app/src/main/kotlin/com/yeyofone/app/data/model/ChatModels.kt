package com.yeyofone.app.data.model

data class ChatContact(
    val id: String,
    val name: String,
    val isOnline: Boolean = false,
    val statusText: String = "Online",
)

enum class MessageType { TEXT, VOICE }

enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

data class ChatMessage(
    val id: String,
    val content: String,
    val timestamp: String,
    val isOutgoing: Boolean,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val waveformHeights: List<Int> = emptyList(),
)
