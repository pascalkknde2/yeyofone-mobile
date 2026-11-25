package com.yeyofone.app

import com.yeyofone.core.calling.HandoffCoordinator
import com.yeyofone.core.calling.HandoffOffer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountPreferencesRepository
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.account.ContactDraft
import com.yeyofone.core.account.ContactRepository
import com.yeyofone.core.account.ForwardingStateRepository
import com.yeyofone.core.model.AccountPreferences
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.Contact
import com.yeyofone.core.model.ContactId
import com.yeyofone.core.model.ForwardingState
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.PreferenceToggle
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.AudioRouteManager
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.MediaManager
import com.yeyofone.core.voip.RegistrationManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Home : AppScreen
    data object Accounts : AppScreen
    data object Contacts : AppScreen
    data object History : AppScreen
    data object Chat : AppScreen
    data object Settings : AppScreen
    data object AudioSettings : AppScreen
    data object VideoSettings : AppScreen
    data object IncomingCallsSettings : AppScreen
    data object LanguageSettings : AppScreen
    data object Recordings : AppScreen
    data object AdvancedSettings : AppScreen
    data object Social : AppScreen
    data object About : AppScreen
    data object Licenses : AppScreen
    data object Appearance : AppScreen
    data object Premium : AppScreen
    data object Enterprise : AppScreen
    data object Translate : AppScreen
    /** A setting release builds don't offer yet; [title] is its string resource. */
    data class Unavailable(val title: Int) : AppScreen
    data class Detail(val accountId: SipAccountId) : AppScreen
    data class Edit(val accountId: SipAccountId?) : AppScreen
    data class Dial(val accountId: SipAccountId, val destination: String = "") : AppScreen
}

data class YeyoFoneUiState(
    val screen: AppScreen = AppScreen.Home,
    val accounts: List<SipAccount> = emptyList(),
    val sessions: List<CallSession> = emptyList(),
    val availableRoutes: List<AudioRoute> = emptyList(),
    val selectedRoute: AudioRoute? = null,
    val activeCallId: CallId? = null,
    val consultationCallId: CallId? = null,
    /** True once the consultation call has been merged into a three-way conference. */
    val conferenceMerged: Boolean = false,
    /** Calls currently being recorded to a file. */
    val recordingCallIds: Set<CallId> = emptySet(),
    /** Calls where the AI assistant is on; its notes are made when the call ends. */
    val assistantCallIds: Set<CallId> = emptySet(),
    /** The assistant's progress or result for calls that have ended. */
    val callNotes: Map<CallId, com.yeyofone.app.ai.NotesState> = emptyMap(),
    val dismissedIncomingCalls: Set<CallId> = emptySet(),
)

private data class CallSelection(
    val active: CallId?,
    val consultation: CallId?,
    val dismissed: Set<CallId>,
    val merged: Boolean,
    val recording: Set<CallId>,
    val assistant: Set<CallId>,
    val notes: Map<CallId, com.yeyofone.app.ai.NotesState>,
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
    private val preferencesRepo: AccountPreferencesRepository,
    private val forwardingStateRepo: ForwardingStateRepository,
    private val forwarding: ForwardingCoordinator,
    private val contactsRepo: ContactRepository,
    private val handoff: HandoffCoordinator? = null,
    private val notesProcessor: com.yeyofone.app.ai.CallNotesProcessor? = null,
) : ViewModel() {
    /** Calls on this account's other devices that can be continued here. */
    val handoffOffers: StateFlow<List<HandoffOffer>> = handoff?.offers ?: MutableStateFlow(emptyList())
    private val mutableTeamRoomOpen = MutableStateFlow(false)
    /** The Team room video conference preview, shown over everything except calls. */
    val teamRoomOpen: StateFlow<Boolean> = mutableTeamRoomOpen
    fun openTeamRoom() { mutableTeamRoomOpen.value = true }
    fun closeTeamRoom() { mutableTeamRoomOpen.value = false }

    /** Moves an offered call here; the call screen opens as for a dialed call. */
    fun continueHandoff(offer: HandoffOffer) = launch {
        val coordinator = handoff ?: return@launch
        screen.value = AppScreen.Dial(offer.accountId, "")
        activeCallId.value = runCatching { coordinator.takeOver(offer) }.getOrNull()
    }

    fun dismissHandoff(offer: HandoffOffer) {
        handoff?.dismiss(offer)
    }

    private val screen = MutableStateFlow<AppScreen>(AppScreen.Home)
    private val activeCallId = MutableStateFlow<CallId?>(null)
    private val consultationCallId = MutableStateFlow<CallId?>(null)
    private val conferenceMerged = MutableStateFlow(false)
    private val recordingCallIds = MutableStateFlow<Set<CallId>>(emptySet())
    private val assistantCallIds = MutableStateFlow<Set<CallId>>(emptySet())
    private val callNotes = MutableStateFlow<Map<CallId, com.yeyofone.app.ai.NotesState>>(emptyMap())
    private val recordingFiles = mutableMapOf<CallId, java.io.File>()
    /** Recordings the assistant started itself, so turning it off stops only those. */
    private val assistantStartedRecording = mutableSetOf<CallId>()
    private val dismissedIncomingCalls = MutableStateFlow<Set<CallId>>(emptySet())

    private val _speaking = MutableStateFlow<Set<String>>(emptySet())
    /** Who is talking in the conference: "you", "held" (the original caller) and/or "consult". */
    val speaking: StateFlow<Set<String>> = _speaking

    // Re-subscribes to each account's preferences whenever the account list itself changes;
    // CallCoordinator reads the same repository to enforce Allow Incoming/DND/Auto-Answer, so
    // this is just the UI's read-side view of that persisted state (see CALL-FEATURES-AUDIT.md
    // Phase B).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val preferencesByAccount: Flow<Map<String, AccountPreferences>> =
        accounts.observeAccounts().flatMapLatest { accountList ->
            if (accountList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    accountList.map { account -> preferencesRepo.observe(account.id).map { account.id.value to it } },
                ) { pairs -> pairs.toMap() }
            }
        }

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
        combine(
            activeCallId, consultationCallId, dismissedIncomingCalls, conferenceMerged,
            combine(recordingCallIds, assistantCallIds, callNotes) { r, a, n -> Triple(r, a, n) },
        ) { active, consultation, dismissed, merged, extras ->
            CallSelection(active, consultation, dismissed, merged, extras.first, extras.second, extras.third)
        },
    ) { core, callSelection ->
        YeyoFoneUiState(
            screen = core.screen,
            accounts = core.accounts,
            sessions = core.sessions,
            availableRoutes = core.routes.first,
            selectedRoute = core.routes.second,
            activeCallId = callSelection.active,
            consultationCallId = callSelection.consultation,
            conferenceMerged = callSelection.merged,
            recordingCallIds = callSelection.recording,
            assistantCallIds = callSelection.assistant,
            callNotes = callSelection.notes,
            dismissedIncomingCalls = callSelection.dismissed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YeyoFoneUiState())

    init {
        startSpeakerDetection()
        // completeTransfer() hands both legs to the PBX and never clears the consultation, so
        // forget it once no call is live; otherwise the next call's More screen reopens the old,
        // ended consultation. (While a call is still live, an ended consultation is kept so the
        // user can see it and tap Return to caller.)
        viewModelScope.launch {
            calls.sessions.collect { sessions ->
                if (consultationCallId.value != null && sessions.all { it.state.isTerminal() }) {
                    consultationCallId.value = null
                }
                // Calls that ended with the assistant on get their notes made now.
                sessions.filter { it.state.isTerminal() && it.id in assistantCallIds.value }.forEach { ended ->
                    assistantCallIds.update { it - ended.id }
                    makeNotes(ended.id)
                }
                // A recording ends with its call (the engine finalizes the file on disconnect).
                val live = sessions.filter { !it.state.isTerminal() }.map { it.id }.toSet()
                if (!live.containsAll(recordingCallIds.value)) recordingCallIds.update { it intersect live }
                // The conference is over once the consultation leg is gone.
                val consultation = sessions.firstOrNull { it.id == consultationCallId.value }
                if (conferenceMerged.value && (consultation == null || consultation.state.isTerminal())) {
                    conferenceMerged.value = false
                }
            }
        }
    }

    /** While a conference is up, samples each voice's level a few times a second to find who is talking. */
    private fun startSpeakerDetection() = viewModelScope.launch {
        conferenceMerged.collectLatest { merged ->
            _speaking.value = emptySet()
            if (!merged) return@collectLatest
            val lastLoud = mutableMapOf<String, Long>()
            while (true) {
                val now = android.os.SystemClock.elapsedRealtime()
                val held = activeCallId.value
                val consult = consultationCallId.value
                val levels = buildMap {
                    if (held != null) put("held", runCatching { calls.inputLevel(held) }.getOrDefault(0f))
                    if (consult != null) put("consult", runCatching { calls.inputLevel(consult) }.getOrDefault(0f))
                    put("you", if (held != null && media.observe(held).value.muted) 0f else runCatching { calls.inputLevel(null) }.getOrDefault(0f))
                }
                levels.forEach { (id, level) -> if (level > SPEAKING_LEVEL) lastLoud[id] = now }
                // Keep the ring on briefly after a voice drops so it doesn't flicker between words.
                _speaking.value = lastLoud.filterValues { now - it < SPEAKING_HOLD_MS }.keys
                kotlinx.coroutines.delay(200)
            }
        }
    }

    fun showHome() { screen.value = AppScreen.Home }
    fun showAccounts() { screen.value = AppScreen.Accounts }
    fun showContacts() { screen.value = AppScreen.Contacts }
    fun showHistory() { screen.value = AppScreen.History }
    fun showChat() { screen.value = AppScreen.Chat }
    fun showSettings() { screen.value = AppScreen.Settings }
    fun showAudioSettings() { screen.value = AppScreen.AudioSettings }
    fun showVideoSettings() { screen.value = AppScreen.VideoSettings }
    fun showIncomingCallsSettings() { screen.value = AppScreen.IncomingCallsSettings }
    fun showLanguageSettings() { screen.value = AppScreen.LanguageSettings }
    fun showRecordings() { screen.value = AppScreen.Recordings }
    fun showAdvancedSettings() { screen.value = AppScreen.AdvancedSettings }
    fun showSocial() { screen.value = AppScreen.Social }
    fun showAbout() { screen.value = AppScreen.About }
    fun showLicenses() { screen.value = AppScreen.Licenses }
    fun showAppearance() { screen.value = AppScreen.Appearance }
    fun showPremium() { screen.value = AppScreen.Premium }
    fun showEnterprise() { screen.value = AppScreen.Enterprise }
    fun showTranslate() { screen.value = AppScreen.Translate }
    fun showUnavailable(title: Int) { screen.value = AppScreen.Unavailable(title) }
    fun showAccount(accountId: SipAccountId) { screen.value = AppScreen.Detail(accountId) }
    fun editAccount(accountId: SipAccountId?) { screen.value = AppScreen.Edit(accountId) }
    fun dial(accountId: SipAccountId, destination: String = "") {
        activeCallId.value = null
        screen.value = AppScreen.Dial(accountId, destination)
    }

    fun dismissIncoming(callId: CallId) {
        dismissedIncomingCalls.value = dismissedIncomingCalls.value + callId
    }

    fun dismissCallSummary(callId: CallId) {
        dismissedIncomingCalls.value = dismissedIncomingCalls.value + callId
        if (activeCallId.value == callId) activeCallId.value = null
    }

    fun setAccountEnabled(accountId: SipAccountId, enabled: Boolean) = launch { accounts.setEnabled(accountId, enabled) }
    fun deleteAccount(accountId: SipAccountId, onDeleted: () -> Unit) = launch {
        accounts.delete(accountId)
        onDeleted()
    }

    fun observeRegistration(accountId: SipAccountId): StateFlow<RegistrationState> = registration.observe(accountId)
    fun reregister(accountId: SipAccountId) = launch { registration.register(accountId) }

    // Eager, unlike uiState's WhileSubscribed: a toggle write should be readable from .value
    // immediately afterward (settings screens and tests alike) without requiring something to
    // already be collecting this flow for it to be live.
    val accountPreferences: StateFlow<Map<String, AccountPreferences>> =
        preferencesByAccount.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setPreference(accountId: SipAccountId, toggle: PreferenceToggle, enabled: Boolean) = launch {
        preferencesRepo.update(accountId) { current ->
            when (toggle) {
                PreferenceToggle.AutoAnswer -> current.copy(autoAnswer = enabled)
                PreferenceToggle.CallWaiting -> current.copy(callWaiting = enabled)
                PreferenceToggle.Voicemail -> current.copy(voicemail = enabled)
                PreferenceToggle.DoNotDisturb -> current.copy(doNotDisturb = enabled)
                PreferenceToggle.AllowIncoming -> current.copy(allowIncoming = enabled)
                PreferenceToggle.Vibrate -> current.copy(vibrate = enabled)
                PreferenceToggle.FlipToMute -> current.copy(flipToMute = enabled)
                PreferenceToggle.AnnounceCaller -> current.copy(announceCaller = enabled)
            }
        }
    }

    // Eager for the same reason as accountPreferences: a save should be readable from .value
    // immediately, without requiring an active collector.
    @OptIn(ExperimentalCoroutinesApi::class)
    val forwardingState: StateFlow<Map<String, ForwardingState>> = accounts.observeAccounts().flatMapLatest { accountList ->
        if (accountList.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                accountList.map { account -> forwardingStateRepo.observe(account.id).map { account.id.value to it } },
            ) { pairs -> pairs.toMap() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Pulls the relay's current forwarding state into the local cache; silent on failure. */
    fun refreshForwarding(account: SipAccount) = launch { forwarding.refresh(account) }

    /** Pushes a forwarding change to the relay; the cache only updates once the relay confirms it. */
    fun setForwarding(account: SipAccount, state: ForwardingState, onResult: (ForwardingClient.Result) -> Unit) =
        launch { onResult(forwarding.update(account, state)) }

    fun saveAccount(draft: AccountDraft, onResult: (Result<SipAccountId>) -> Unit) = launch {
        onResult(accounts.save(draft))
    }

    // Eager, same reasoning as accountPreferences/forwardingState.
    val contacts: StateFlow<List<Contact>> = contactsRepo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveContact(draft: ContactDraft, onResult: (Result<ContactId>) -> Unit = {}) = launch {
        onResult(runCatching { contactsRepo.save(draft) })
    }

    fun setContactFavorite(id: ContactId, favorite: Boolean) = launch { contactsRepo.setFavorite(id, favorite) }
    fun deleteContact(id: ContactId) = launch { contactsRepo.delete(id) }

    private val mutableCallErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when an outgoing call could not be placed (for example the account is disabled), so the UI can say so. */
    val callErrors: SharedFlow<Unit> = mutableCallErrors.asSharedFlow()

    // An exception escaping viewModelScope.launch kills the app; placing a call can legitimately
    // fail (account disabled or not registered with the SIP stack, engine stopped), so report it instead.
    private suspend fun placeCall(accountId: SipAccountId, destination: String): CallId? = try {
        calls.call(accountId, destination)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        runCatching { Log.w("YeyoFoneCall", "Could not place call to $destination", error) }
        mutableCallErrors.tryEmit(Unit)
        null
    }

    fun startCall(accountId: SipAccountId, destination: String) = launch {
        placeCall(accountId, destination)?.let { activeCallId.value = it }
    }

    fun answer(callId: CallId) = launch {
        activeCallId.value = callId
        calls.answer(callId)
    }
    fun reject(callId: CallId) = launch { calls.reject(callId) }
    fun end(callId: CallId) = launch { calls.end(callId) }

    /**
     * Call waiting: a second incoming call is answered while [currentCallId] (if any) is already
     * in the foreground. Holds the current call first, same order attended transfer already uses
     * before consulting, so the new call never joins audio with an unheld first call.
     */
    fun answerWaitingCall(waitingCallId: CallId, currentCallId: CallId?) = launch {
        if (currentCallId != null) media.setHeld(currentCallId, true)
        activeCallId.value = waitingCallId
        calls.answer(waitingCallId)
    }

    /** Switches which of two concurrent calls is in the foreground; the other goes on hold. */
    fun swapActiveCall(currentId: CallId, otherId: CallId) = launch {
        media.setHeld(currentId, true)
        media.setHeld(otherId, false)
        activeCallId.value = otherId
    }
    // A rejected digit (call just ended, remote has no RFC 2833) must not take the app down.
    fun sendDtmf(callId: CallId, digit: Char) = launch {
        try {
            calls.sendDtmf(callId, digit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.w("YeyoFoneCall", "DTMF $digit was not sent", error) }
        }
    }
    fun transfer(callId: CallId, destination: String) = launch { calls.transfer(callId, destination) }
    fun completeTransfer(callId: CallId, consultationId: CallId) = launch {
        calls.attendedTransfer(callId, consultationId)
    }

    fun startConsultation(accountId: SipAccountId, destination: String) = launch {
        placeCall(accountId, destination)?.let { consultationCallId.value = it }
    }

    /** Starts recording [callId] to [file], or stops the recording when [file] is null. */
    fun setRecording(callId: CallId, file: java.io.File?) = launch {
        try {
            if (file != null) {
                calls.startRecording(callId, file.absolutePath)
                recordingFiles[callId] = file
                recordingCallIds.update { it + callId }
            } else {
                calls.stopRecording(callId)
                recordingCallIds.update { it - callId }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.w("YeyoFoneCall", "Call recording could not be changed", error) }
        }
    }

    /**
     * Turns the AI assistant on or off for a call. Turning it on records the call (to [file]) unless
     * it is already being recorded; the notes are made on the phone once the call ends.
     */
    fun setAssistant(callId: CallId, on: Boolean, file: java.io.File?) = launch {
        try {
            if (on) {
                if (callId !in recordingCallIds.value && file != null) {
                    calls.startRecording(callId, file.absolutePath)
                    recordingFiles[callId] = file
                    recordingCallIds.update { it + callId }
                    assistantStartedRecording += callId
                }
                if (callId in recordingCallIds.value) assistantCallIds.update { it + callId }
            } else {
                assistantCallIds.update { it - callId }
                if (assistantStartedRecording.remove(callId)) {
                    calls.stopRecording(callId)
                    recordingCallIds.update { it - callId }
                    recordingFiles.remove(callId)?.delete()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.w("YeyoFoneCall", "AI assistant could not be changed", error) }
        }
    }

    private fun makeNotes(callId: CallId) {
        val file = recordingFiles[callId]
        val processor = notesProcessor
        if (file == null || processor == null) return
        viewModelScope.launch {
            callNotes.update { it + (callId to com.yeyofone.app.ai.NotesState.Preparing(null)) }
            processor.process(file) { state -> callNotes.update { it + (callId to state) } }
        }
    }

    /** Joins the held call and the consultation call into one three-way conference. */
    fun mergeCalls(callId: CallId, consultationId: CallId) = launch {
        try {
            calls.mergeCalls(callId, consultationId)
            conferenceMerged.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.w("YeyoFoneCall", "Merge into conference failed", error) }
        }
    }

    fun returnToCaller(callId: CallId, consultation: CallSession) = launch {
        if (!consultation.state.isTerminal()) calls.end(consultation.id)
        media.setHeld(callId, false)
        conferenceMerged.value = false
        consultationCallId.value = null
    }

    fun observeMedia(callId: CallId): StateFlow<MediaState> = media.observe(callId)
    fun setMuted(callId: CallId, muted: Boolean) = launch { media.setMuted(callId, muted) }
    fun setHeld(callId: CallId, held: Boolean) = launch { media.setHeld(callId, held) }
    private val mutableAudioRouteErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val audioRouteErrors: SharedFlow<Unit> = mutableAudioRouteErrors.asSharedFlow()

    fun selectAudioRoute(route: AudioRoute) = launch {
        try {
            audioRoutes.select(route)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.w("YeyoFoneAudioRoute", "Could not select audio device", error) }
            mutableAudioRouteErrors.tryEmit(Unit)
        }
    }

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
                app.accountPreferences,
                app.forwardingState,
                app.forwardingCoordinator,
                app.contacts,
                app.handoff,
                com.yeyofone.app.ai.CallNotesProcessor(app),
            ) as T
        }
    }
}

private fun com.yeyofone.core.model.CallState.isTerminal(): Boolean =
    this is com.yeyofone.core.model.CallState.Disconnected || this is com.yeyofone.core.model.CallState.Failed

private const val SPEAKING_LEVEL = 0.06f
private const val SPEAKING_HOLD_MS = 500L
