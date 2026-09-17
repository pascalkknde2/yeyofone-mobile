package com.yeyofone.app.ui.dialpad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DialPadViewModelTest {
    @Test
    fun `number keys append and backspace removes the last character`() {
        val viewModel = DialPadViewModel()

        viewModel.onNumberPressed("1")
        viewModel.onNumberPressed("#")
        viewModel.onBackspacePressed()

        assertEquals("1", viewModel.uiState.value.typedNumber)
        assertTrue(viewModel.uiState.value.canBackspace)
        assertEquals("1", viewModel.numberToCall())
    }

    @Test
    fun `manual input stops at maximum length`() {
        val viewModel = DialPadViewModel()

        repeat(20) { viewModel.onNumberPressed("1") }

        assertEquals(15, viewModel.uiState.value.typedNumber.length)
    }

    @Test
    fun `initial SIP destination remains intact and clear resets state`() {
        val viewModel = DialPadViewModel()
        val destination = "sip:1001@example.com"

        viewModel.setInitialNumber(destination)
        assertEquals(destination, viewModel.numberToCall())

        viewModel.clearNumber()
        assertTrue(viewModel.uiState.value.isNumberEmpty)
        assertFalse(viewModel.uiState.value.canBackspace)
        assertNull(viewModel.numberToCall())
    }

    @Test
    fun `multi-character key input is rejected`() {
        val viewModel = DialPadViewModel()

        viewModel.onNumberPressed("12")

        assertTrue(viewModel.uiState.value.isNumberEmpty)
    }
}
