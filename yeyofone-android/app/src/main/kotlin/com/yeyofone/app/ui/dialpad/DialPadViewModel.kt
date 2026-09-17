package com.yeyofone.app.ui.dialpad

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DialPadUiState(
    val typedNumber: String = "",
    val maxLength: Int = 15,
) {
    val isNumberEmpty: Boolean get() = typedNumber.isEmpty()
    val canBackspace: Boolean get() = typedNumber.isNotEmpty()
}

class DialPadViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DialPadUiState())
    val uiState: StateFlow<DialPadUiState> = mutableUiState.asStateFlow()

    fun onNumberPressed(number: String) {
        val state = mutableUiState.value
        if (number.length == 1 && state.typedNumber.length < state.maxLength) {
            mutableUiState.value = state.copy(typedNumber = state.typedNumber + number)
        }
    }

    fun onBackspacePressed() {
        val current = mutableUiState.value.typedNumber
        if (current.isNotEmpty()) mutableUiState.value = mutableUiState.value.copy(typedNumber = current.dropLast(1))
    }

    fun numberToCall(): String? = mutableUiState.value.typedNumber.trim().takeIf(String::isNotEmpty)

    fun setInitialNumber(number: String) {
        mutableUiState.value = mutableUiState.value.copy(typedNumber = number)
    }

    fun clearNumber() {
        mutableUiState.value = mutableUiState.value.copy(typedNumber = "")
    }
}
