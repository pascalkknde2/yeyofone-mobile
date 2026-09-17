package com.yeyofone.app.ui.callhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yeyofone.app.data.model.CallLog
import com.yeyofone.app.data.model.CallType
import com.yeyofone.app.data.repository.CallRepository
import com.yeyofone.core.model.CallHistoryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CallHistoryUiState(
    val isLoading: Boolean = true,
    val calls: List<CallLog> = emptyList(),
    val selectedTab: Int = ALL_TAB,
)

class CallHistoryViewModel(private val repository: CallRepository) : ViewModel() {
    private val allCalls = MutableStateFlow<List<CallLog>>(emptyList())
    private val mutableUiState = MutableStateFlow(CallHistoryUiState())
    val uiState: StateFlow<CallHistoryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCallHistory().collectLatest { calls ->
                allCalls.value = calls
                updateVisibleCalls()
            }
        }
    }

    fun onTabSelected(index: Int) {
        if (index !in ALL_TAB..VOICEMAIL_TAB) return
        mutableUiState.value = mutableUiState.value.copy(selectedTab = index)
        updateVisibleCalls()
    }

    fun delete(id: CallHistoryId) = viewModelScope.launch { repository.delete(id) }

    fun clear() = viewModelScope.launch { repository.clear() }

    private fun updateVisibleCalls() {
        val selectedTab = mutableUiState.value.selectedTab
        val visibleCalls = when (selectedTab) {
            MISSED_TAB -> allCalls.value.filter { it.callType == CallType.MISSED }
            VOICEMAIL_TAB -> allCalls.value.filter(CallLog::hasVoicemail)
            else -> allCalls.value
        }
        mutableUiState.value = mutableUiState.value.copy(isLoading = false, calls = visibleCalls)
    }

    class Factory(private val repository: CallRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CallHistoryViewModel::class.java))
            return CallHistoryViewModel(repository) as T
        }
    }
}

const val ALL_TAB = 0
const val MISSED_TAB = 1
const val VOICEMAIL_TAB = 2
