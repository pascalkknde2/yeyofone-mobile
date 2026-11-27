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
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.app.data.model.toSipIdentity
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
import kotlinx.coroutines.flow.first
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
    /** Everyone joined into the conference, in the order they joined. Empty when there is none. */
    val conferenceCallIds: List<CallId> = emptyList(),
    /** Someone being added to the conference who has not answered yet. */
    val pendingConferenceCallId: CallId? = null,
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
    val conferenceMembers: List<CallId>,
    val pendingConference: CallId?,
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
    private val peers: com.yeyofone.core.voip.SipPeerMessageGateway? = null,
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
    private val conferenceMembers = MutableStateFlow<List<CallId>>(emptyList())
    private val pendingConferenceCall = MutableStateFlow<CallId?>(null)
    private var autoMergingCall: CallId? = null
    /** True while a person is being dialled for the conference, so it isn't dissolved before they appear. */
    private var addingToConference = false
    private val recordingCallIds = MutableStateFlow<Set<CallId>>(emptySet())
    private val assistantCallIds = MutableStateFlow<Set<CallId>>(emptySet())
    private val callNotes = MutableStateFlow<Map<CallId, com.yeyofone.app.ai.NotesState>>(emptyMap())
    private val recordingFiles = mutableMapOf<CallId, java.io.File>()
    /** Recordings the assistant started itself, so turning it off stops only those. */
    private val assistantStartedRecording = mutableSetOf<CallId>()
    private val dismissedIncomingCalls = MutableStateFlow<Set<CallId>>(emptySet())

    private val _remoteConference = MutableStateFlow<com.yeyofone.app.conference.ConferenceRoster?>(null)
    /** The conference someone else organised that this phone is part of, shared by its organiser. */
    val remoteConference: StateFlow<com.yeyofone.app.conference.ConferenceRoster?> = _remoteConference
    /** Extensions already sent the current roster, so they can be told when the conference ends. */
    private val rosterRecipients = mutableSetOf<String>()

    private val _conferenceLeft = MutableStateFlow<List<com.yeyofone.app.conference.RosterMember>>(emptyList())
    /** People who hung up during my conference: still shown, as "Left", and let back in if they call. */
    val conferenceLeft: StateFlow<List<com.yeyofone.app.conference.RosterMember>> = _conferenceLeft
    /** Calls I removed from the conference myself, so they aren't listed as having left. */
    private val removedFromConference = mutableSetOf<CallId>()
    /** A call from someone who left, being answered so they can rejoin. */
    private var rejoiningCall: CallId? = null

    /** A conference someone else organised that I left, so the ended-call screen can offer to rejoin it. */
    data class LeftConference(val callId: CallId, val accountId: SipAccountId, val host: String)
    private val _leftConference = MutableStateFlow<LeftConference?>(null)
    val leftConference: StateFlow<LeftConference?> = _leftConference

    private val _speaking = MutableStateFlow<Set<String>>(emptySet())
    /** Who is talking in the conference: "you" and/or the call id of each person. */
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
            combine(recordingCallIds, assistantCallIds, callNotes, conferenceMembers, pendingConferenceCall) { r, a, n, m, p ->
                CallSelection(null, null, emptySet(), false, r, a, n, m, p)
            },
        ) { active, consultation, dismissed, merged, extras ->
            extras.copy(active = active, consultation = consultation, dismissed = dismissed, merged = merged)
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
            conferenceCallIds = callSelection.conferenceMembers,
            pendingConferenceCallId = callSelection.pendingConference,
            dismissedIncomingCalls = callSelection.dismissed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YeyoFoneUiState())

    init {
        startSpeakerDetection()
        startRosterSharing()
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
                updateConference(sessions)
                _remoteConference.value?.let { remote ->
                    if (sessions.none { !it.state.isTerminal() && it.remoteUri.toSipIdentity().extension == remote.host }) _remoteConference.value = null
                }
            }
        }
    }

    /**
     * Shares who is on the conference with each person on it (so their phones show the same
     * conference screen), and picks up rosters other organisers send to this phone.
     */
    private fun startRosterSharing() {
        val gateway = peers ?: return
        viewModelScope.launch {
            gateway.peerMessages.collect { message ->
                val (roster, ended) = com.yeyofone.app.conference.ConferenceRoster.parse(message.body) ?: return@collect
                // Only believe the person I am actually on a call with.
                val inCallWithHost = calls.sessions.value.any {
                    !it.state.isTerminal() && it.remoteUri.toSipIdentity().extension == message.fromUser
                }
                if (!inCallWithHost || roster.host != message.fromUser) return@collect
                _remoteConference.value = if (ended) null else roster
            }
        }
        viewModelScope.launch {
            combine(
                combine(conferenceMembers, pendingConferenceCall, _conferenceLeft, ::Triple),
                calls.sessions, contactsRepo.observeAll(), _speaking,
            ) { (members, pending, left), sessions, contactList, talking ->
                RosterInputs(members, pending, sessions, contactList, talking, left)
            }.collectLatest { inputs ->
                kotlinx.coroutines.delay(150)
                shareRoster(gateway, inputs)
            }
        }
    }

    private class RosterInputs(
        val members: List<CallId>,
        val pending: CallId?,
        val sessions: List<CallSession>,
        val contacts: List<Contact>,
        val speaking: Set<String>,
        val left: List<com.yeyofone.app.conference.RosterMember>,
    )

    private suspend fun shareRoster(gateway: com.yeyofone.core.voip.SipPeerMessageGateway, inputs: RosterInputs) {
        val byId = inputs.sessions.associateBy { it.id }
        val live = inputs.members.mapNotNull { byId[it] }.filter { !it.state.isTerminal() }
        val pendingSession = inputs.pending?.let { byId[it] }?.takeIf { !it.state.isTerminal() }
        val anchor = live.firstOrNull()
        if (anchor == null || (live.size < 2 && pendingSession == null && inputs.left.isEmpty())) {
            // No conference any more: tell everyone who was told about it.
            val account = accounts.observeAccounts().first().firstOrNull()
            if (rosterRecipients.isNotEmpty() && account != null) {
                val ended = com.yeyofone.app.conference.ConferenceRoster(account.username, emptyList()).toJson(ended = true)
                rosterRecipients.toList().forEach { runCatching { gateway.sendPeerMessage(account.id, it, ended) } }
            }
            rosterRecipients.clear()
            return
        }
        val account = accounts.observeAccounts().first().firstOrNull { it.id == anchor.accountId } ?: return
        fun nameFor(session: CallSession): String {
            val identity = session.remoteUri.toSipIdentity()
            return inputs.contacts.firstOrNull { it.number == identity.extension }?.displayName ?: identity.displayName
        }
        val members = buildList {
            add(com.yeyofone.app.conference.RosterMember(account.username, account.displayName.ifBlank { account.username }, false))
            live.forEach { add(com.yeyofone.app.conference.RosterMember(it.remoteUri.toSipIdentity().extension, nameFor(it), false)) }
            pendingSession?.let {
                add(com.yeyofone.app.conference.RosterMember(it.remoteUri.toSipIdentity().extension, nameFor(it), it.state != CallState.Connected))
            }
        }
        // Who is talking, as extensions, so people on the conference can light the same rings.
        val talking = inputs.speaking.mapNotNull { id ->
            if (id == "you") account.username else byId.values.firstOrNull { it.id.value == id }?.remoteUri?.toSipIdentity()?.extension
        }
        val left = inputs.left.filter { gone -> members.none { it.ext == gone.ext } }.map { gone ->
            gone.copy(name = inputs.contacts.firstOrNull { it.number == gone.ext }?.displayName ?: gone.name)
        }
        val json = com.yeyofone.app.conference.ConferenceRoster(account.username, members, talking, left).toJson()
        val recipients = live.map { it.remoteUri.toSipIdentity().extension }.toSet()
        recipients.forEach { runCatching { gateway.sendPeerMessage(account.id, it, json) } }
        // Anyone who left since the last roster is told the conference is over for them.
        val ended = com.yeyofone.app.conference.ConferenceRoster(account.username, emptyList()).toJson(ended = true)
        (rosterRecipients - recipients).forEach { runCatching { gateway.sendPeerMessage(account.id, it, ended) } }
        rosterRecipients.clear()
        rosterRecipients.addAll(recipients)
    }

    /** While a conference is up, samples each voice's level a few times a second to find who is talking. */
    private fun startSpeakerDetection() = viewModelScope.launch {
        conferenceMerged.collectLatest { merged ->
            _speaking.value = emptySet()
            if (!merged) return@collectLatest
            val lastLoud = mutableMapOf<String, Long>()
            while (true) {
                val now = android.os.SystemClock.elapsedRealtime()
                val members = conferenceMembers.value + listOfNotNull(pendingConferenceCall.value)
                val first = members.firstOrNull()
                val levels = buildMap {
                    members.forEach { put(it.value, runCatching { calls.inputLevel(it) }.getOrDefault(0f)) }
                    put("you", if (first != null && media.observe(first).value.muted) 0f else runCatching { calls.inputLevel(null) }.getOrDefault(0f))
                }
                levels.forEach { (id, level) -> if (level > SPEAKING_LEVEL) lastLoud[id] = now }
                // Keep the ring on briefly after a voice drops so it doesn't flicker between words.
                _speaking.value = lastLoud.filterValues { now - it < SPEAKING_HOLD_MS }.keys
                kotlinx.coroutines.delay(200)
            }
        }
    }

    /**
     * Keeps the conference in step with the calls: drops people who hung up, ends the conference when
     * fewer than two remain, and joins a person being added as soon as they answer.
     */
    private fun updateConference(sessions: List<CallSession>) {
        val byId = sessions.associateBy { it.id }
        val live = conferenceMembers.value.filter { byId[it]?.state?.isTerminal() == false }
        if (live != conferenceMembers.value) {
            // Someone who hung up (rather than being removed) stays on the screen as "Left".
            if (conferenceMerged.value) {
                val gone = (conferenceMembers.value - live.toSet() - removedFromConference).mapNotNull { byId[it] }
                    .map { com.yeyofone.app.conference.RosterMember(it.remoteUri.toSipIdentity().extension, it.remoteUri.toSipIdentity().displayName, false) }
                if (gone.isNotEmpty()) _conferenceLeft.update { left -> left + gone.filter { g -> left.none { it.ext == g.ext } } }
            }
            // The conference carries on: the screen follows someone still on it, not the leg that hung up,
            // and the legs that ended don't pop up their own ended-call summaries.
            if (live.isNotEmpty()) {
                val ended = conferenceMembers.value - live.toSet()
                dismissedIncomingCalls.update { it + ended }
                if (activeCallId.value in ended) activeCallId.value = live.first()
            }
            conferenceMembers.value = live
        }
        // Whoever is back on the conference is no longer listed as having left.
        val liveExtensions = live.mapNotNull { byId[it]?.remoteUri?.toSipIdentity()?.extension }.toSet()
        if (_conferenceLeft.value.any { it.ext in liveExtensions }) _conferenceLeft.update { left -> left.filter { it.ext !in liveExtensions } }
        val pending = pendingConferenceCall.value
        val pendingSession = pending?.let { byId[it] }
        if (pending != null && (pendingSession == null || pendingSession.state.isTerminal())) {
            pendingConferenceCall.value = null
            if (autoMergingCall == pending) autoMergingCall = null
            if (rejoiningCall == pending) rejoiningCall = null
        }
        // With people who left still listed, the conference stays up (so they can rejoin) while anyone else is on it.
        val keepForLeft = live.isNotEmpty() && _conferenceLeft.value.isNotEmpty()
        if (live.size < 2 && !keepForLeft && pendingConferenceCall.value == null && !addingToConference && (live.isNotEmpty() || conferenceMerged.value)) {
            conferenceMembers.value = emptyList()
            conferenceMerged.value = false
        }
        if (conferenceMembers.value.isEmpty() && pendingConferenceCall.value == null && !addingToConference) {
            _conferenceLeft.value = emptyList()
            removedFromConference.clear()
        }
        // Someone who left is calling back: answer and join them to the conference straight away.
        if (conferenceMerged.value && live.isNotEmpty() && pendingConferenceCall.value == null && rejoiningCall == null) {
            val leftExtensions = _conferenceLeft.value.map { it.ext }.toSet()
            sessions.firstOrNull {
                it.direction == CallDirection.INCOMING && (it.state == CallState.Incoming || it.state == CallState.Ringing) &&
                    it.remoteUri.toSipIdentity().extension in leftExtensions
            }?.let { returning ->
                rejoiningCall = returning.id
                pendingConferenceCall.value = returning.id
                viewModelScope.launch {
                    try {
                        calls.answer(returning.id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        runCatching { Log.w("YeyoFoneCall", "Could not let someone back into the conference", error) }
                    }
                }
                return
            }
        }
        if (pendingSession != null && pendingSession.state == CallState.Connected && live.isNotEmpty() && autoMergingCall != pending) {
            autoMergingCall = pending
            val anchor = live.first()
            viewModelScope.launch {
                try {
                    calls.mergeCalls(anchor, pendingSession.id)
                    // Someone joining while you are muted must not hear you either.
                    if (media.observe(anchor).value.muted) media.setMuted(pendingSession.id, true)
                    conferenceMembers.update { if (pendingSession.id in it) it else it + pendingSession.id }
                    conferenceMerged.value = true
                    consultationCallId.value = null
                    pendingConferenceCall.value = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    runCatching { Log.w("YeyoFoneCall", "Adding to conference failed", error) }
                    runCatching { calls.end(pendingSession.id) }
                } finally {
                    autoMergingCall = null
                }
            }
        }
    }

    /** Calls [destination] and joins them to the conference (starting one with [activeId]) once they answer. */
    fun addToConference(activeId: CallId, accountId: SipAccountId, destination: String) = launch {
        // Show the conference stage straight away; the new person appears as "Calling…" once dialling starts.
        if (conferenceMembers.value.isEmpty()) conferenceMembers.value = listOf(activeId)
        addingToConference = true
        val id = try { placeCall(accountId, destination) } finally { addingToConference = false }
        if (id == null) {
            if (pendingConferenceCall.value == null && !conferenceMerged.value) conferenceMembers.value = emptyList()
            return@launch
        }
        pendingConferenceCall.value = id
    }

    fun removeFromConference(callId: CallId) = launch {
        removedFromConference += callId
        calls.end(callId)
    }

    /** Hangs up on a conference someone else organised, remembering it so I can rejoin. */
    fun leaveConference(callId: CallId) = launch {
        val session = calls.sessions.value.firstOrNull { it.id == callId }
        val host = _remoteConference.value?.host
        if (session != null && host != null && session.remoteUri.toSipIdentity().extension == host) {
            _leftConference.value = LeftConference(callId, session.accountId, host)
        }
        calls.end(callId)
    }

    /** Calls the organiser of the conference I left; their phone joins me back in. */
    fun rejoinConference() {
        val left = _leftConference.value ?: return
        _leftConference.value = null
        dismissCallSummary(left.callId)
        startCall(left.accountId, left.host)
    }

    fun endConference() = launch {
        (conferenceMembers.value + listOfNotNull(pendingConferenceCall.value)).forEach { runCatching { calls.end(it) } }
    }

    /** Mutes or unmutes the microphone for everyone in the conference. */
    fun setConferenceMuted(muted: Boolean) = launch {
        (conferenceMembers.value + listOfNotNull(pendingConferenceCall.value)).forEach { media.setMuted(it, muted) }
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
        if (_leftConference.value?.callId == callId) _leftConference.value = null
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
            conferenceMembers.value = listOf(callId, consultationId)
            consultationCallId.value = null
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
        conferenceMembers.value = emptyList()
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
                app.peerMessages,
            ) as T
        }
    }
}

private fun com.yeyofone.core.model.CallState.isTerminal(): Boolean =
    this is com.yeyofone.core.model.CallState.Disconnected || this is com.yeyofone.core.model.CallState.Failed

private const val SPEAKING_LEVEL = 0.03f
private const val SPEAKING_HOLD_MS = 500L
