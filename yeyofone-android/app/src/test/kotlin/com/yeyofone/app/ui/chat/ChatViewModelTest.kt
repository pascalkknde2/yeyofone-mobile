package com.yeyofone.app.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatViewModelTest {
    @Test
    fun `send appends trimmed message and clears input`() {
        val viewModel = ChatViewModel()
        val originalCount = viewModel.uiState.value.messages.size

        viewModel.onInputChanged("  Hello  ")
        viewModel.sendMessage()

        assertEquals(originalCount + 1, viewModel.uiState.value.messages.size)
        assertEquals("Hello", viewModel.uiState.value.messages.last().content)
        assertTrue(viewModel.uiState.value.messages.last().isOutgoing)
        assertEquals("", viewModel.uiState.value.inputText)
        assertFalse(viewModel.uiState.value.isContactTyping)
    }

    @Test
    fun `blank message is ignored`() {
        val viewModel = ChatViewModel()
        val original = viewModel.uiState.value.messages
        viewModel.onInputChanged("   ")
        viewModel.sendMessage()
        assertEquals(original, viewModel.uiState.value.messages)
    }

    @Test
    fun `each contact keeps its own conversation`() {
        val viewModel = ChatViewModel()
        viewModel.openConversation("1001", "Pascal")
        viewModel.onInputChanged("Hi Pascal")
        viewModel.sendMessage()

        viewModel.openConversation("1005", "Moto")
        assertEquals("Moto", viewModel.uiState.value.contact?.name)
        assertTrue(viewModel.uiState.value.messages.isEmpty())

        viewModel.openConversation("1001", "Pascal")
        assertEquals(listOf("Hi Pascal"), viewModel.uiState.value.messages.map { it.content })
        assertEquals(setOf("1001"), viewModel.uiState.value.conversations.keys)
    }

    @Test
    fun `call summary conversation is keyed by the caller's extension`() {
        val viewModel = ChatViewModel()
        viewModel.openConversation("sip:1005@sysinfos.co.uk")
        assertEquals("1005", viewModel.uiState.value.contact?.id)
    }
}
