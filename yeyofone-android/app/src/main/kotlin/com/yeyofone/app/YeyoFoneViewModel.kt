package com.yeyofone.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.AudioRouteManager
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.MediaManager
import com.yeyofone.core.voip.RegistrationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Accounts : AppScreen
    data object History : AppScreen
    data object Chat : AppScreen
    data object Settings : AppScreen
    data class Detail(val accountId: SipAccountId) : AppScreen
    data class Edit(val accountId: SipAccountId?) : AppScreen
    data class Dial(val accountId: SipAccountId, val destination: String = "") : AppScreen
}

enum class PreferenceToggle { AutoAnswer, CallWaiting, Voicemail, DoNotDisturb }

data class AccountPreferences(
    val autoAnswer: Boolean = true,
    val callWaiting: Boolean = true,
    val voicemail: Boolean = false,
    val doNotDisturb: Boolean = false,
)

data class YeyoFoneUiState(
    val screen: AppScreen = AppScreen.Accounts,
    val accounts: List<SipAccount> = emptyList(),
    val sessions: List<CallSession> = emptyList(),
    val availableRoutes: List<AudioRoute> = emptyList(),
    val selectedRoute: AudioRoute? = null,
    val activeCallId: CallId? = null,
    val consultationCallId: CallId? = null,
    val dismissedIncomingCalls: Set<CallId> = emptySet(),
)

private data class CoreUiState(
    val screen: AppScreen,
    val accounts: List<SipAccount>,
    val sessions: List<CallSession>,
    val routes: Pair<List<AudioRoute>, AudioRoute?>,
)

class YeyoFoneViewModel(
    private val accounts: AccountRepository,
    private val calls: CallManager,
    private val media: MediaManager,
    private val audioRoutes: AudioRouteManager,
    private val registration: RegistrationManager,
) : ViewModel() {
    private val screen = MutableStateFlow<AppScreen>(AppScreen.Accounts)
    private val activeCallId = MutableStateFlow<CallId?>(null)
    private val consultationCallId = MutableStateFlow<CallId?>(null)
    private val dismissedIncomingCalls = MutableStateFlow<Set<CallId>>(emptySet())

    // Session-level UI state until product defines persistence semantics.
    private val preferences = MutableStateFlow<Map<String, AccountPreferences>>(emptyMap())

    private val coreUiState = combine(
        screen,
        accounts.observeAccounts(),
        calls.sessions,
        combine(audioRoutes.availableRoutes, audioRoutes.selectedRoute) { routes, selected -> routes to selected },
    ) { currentScreen, accountList, sessions, routes ->
        CoreUiState(currentScreen, accountList, sessions, routes)
    }

    val uiState: StateFlow<YeyoFoneUiState> = combine(
        coreUiState,
        combine(activeCallId, consultationCallId, dismissedIncomingCalls) { active, consultation, dismissed ->
            Triple(active, consultation, dismissed)
        },
    ) { core, callSelection ->
        YeyoFoneUiState(
            screen = core.screen,
            accounts = core.accounts,
            sessions = core.sessions,
            availableRoutes = core.routes.first,
            selectedRoute = core.routes.second,
            activeCallId = callSelection.first,
            consultationCallId = callSelection.second,
            dismissedIncomingCalls = callSelection.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YeyoFoneUiState())

    fun showAccounts() { screen.value = AppScreen.Accounts }
    fun showHistory() { screen.value = AppScreen.History }
    fun showChat() { screen.value = AppScreen.Chat }
    fun showSettings() { screen.value = AppScreen.Settings }
    fun showAccount(accountId: SipAccountId) { screen.value = AppScreen.Detail(accountId) }
    fun editAccount(accountId: SipAccountId?) { screen.value = AppScreen.Edit(accountId) }
    fun dial(accountId: SipAccountId, destination: String = "") {
        activeCallId.value = null
        screen.value = AppScreen.Dial(accountId, destination)
    }

    fun dismissIncoming(callId: CallId) {
        dismissedIncomingCalls.value = dismissedIncomingCalls.value + callId
    }

    fun setAccountEnabled(accountId: SipAccountId, enabled: Boolean) = launch { accounts.setEnabled(accountId, enabled) }
    fun deleteAccount(accountId: SipAccountId, onDeleted: () -> Unit) = launch {
        accounts.delete(accountId)
        onDeleted()
    }

    fun observeRegistration(accountId: SipAccountId): StateFlow<RegistrationState> = registration.observe(accountId)
    fun reregister(accountId: SipAccountId) = launch { registration.register(accountId) }

    val accountPreferences: StateFlow<Map<String, AccountPreferences>> = preferences.asStateFlow()

    fun setPreference(accountId: SipAccountId, toggle: PreferenceToggle, enabled: Boolean) {
        preferences.value += (accountId.value to
                    (preferences.value[accountId.value] ?: AccountPreferences()).let { current ->
                        when (toggle) {
                            PreferenceToggle.AutoAnswer -> current.copy(autoAnswer = enabled)
                            PreferenceToggle.CallWaiting -> current.copy(callWaiting = enabled)
                            PreferenceToggle.Voicemail -> current.copy(voicemail = enabled)
                            PreferenceToggle.DoNotDisturb -> current.copy(doNotDisturb = enabled)
                        }
                    })
    }

    fun saveAccount(draft: AccountDraft, onResult: (Result<SipAccountId>) -> Unit) = launch {
        onResult(accounts.save(draft))
    }

    fun startCall(accountId: SipAccountId, destination: String) = launch {
        audioRoutes.prepareForCall()
        activeCallId.value = calls.call(accountId, destination)
    }

    fun answer(callId: CallId) = launch {
        audioRoutes.prepareForCall()
        calls.answer(callId)
    }
    fun reject(callId: CallId) = launch { calls.reject(callId) }
    fun end(callId: CallId) = launch { calls.end(callId) }
    fun sendDtmf(callId: CallId, digit: Char) = launch { calls.sendDtmf(callId, digit) }
    fun transfer(callId: CallId, destination: String) = launch { calls.transfer(callId, destination) }
    fun completeTransfer(callId: CallId, consultationId: CallId) = launch {
        calls.attendedTransfer(callId, consultationId)
    }

    fun startConsultation(accountId: SipAccountId, destination: String) = launch {
        audioRoutes.prepareForCall()
        consultationCallId.value = calls.call(accountId, destination)
    }

    fun returnToCaller(callId: CallId, consultation: CallSession) = launch {
        if (!consultation.state.isTerminal()) calls.end(consultation.id)
        media.setHeld(callId, false)
        consultationCallId.value = null
    }

    fun observeMedia(callId: CallId): StateFlow<MediaState> = media.observe(callId)
    fun setMuted(callId: CallId, muted: Boolean) = launch { media.setMuted(callId, muted) }
    fun setHeld(callId: CallId, held: Boolean) = launch { media.setHeld(callId, held) }
    fun selectAudioRoute(route: AudioRoute) = launch { audioRoutes.select(route) }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    class Factory(private val app: YeyoFoneApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(YeyoFoneViewModel::class.java))
            return YeyoFoneViewModel(
                app.accountRepository,
                app.callManager,
                app.callManager,
                app.audioRouteManager,
                app.registration,
            ) as T
        }
    }
}

private fun com.yeyofone.core.model.CallState.isTerminal(): Boolean =
    this is com.yeyofone.core.model.CallState.Disconnected || this is com.yeyofone.core.model.CallState.Failed
