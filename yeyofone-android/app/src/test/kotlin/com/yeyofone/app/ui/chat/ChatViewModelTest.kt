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
}
