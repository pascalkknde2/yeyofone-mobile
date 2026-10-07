package com.yeyofone.app

import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountPreferencesRepository
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.AccountPreferences
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.PreferenceToggle
import com.yeyofone.core.model.RegistrationDetails
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.model.SipServerConfiguration
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.voip.AudioRouteManager
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.MediaManager
import com.yeyofone.core.voip.RegistrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class YeyoFoneViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setPreference creates an entry defaulting other toggles`() = runTest {
        val id = SipAccountId("one")
        val viewModel = viewModel(accounts = FakeAccounts(mapOf(id to account(id))))

        viewModel.setPreference(id, PreferenceToggle.AutoAnswer, true)
        runCurrent()

        assertEquals(
            AccountPreferences(autoAnswer = true, callWaiting = true, voicemail = false, doNotDisturb = false),
            viewModel.accountPreferences.value[id.value],
        )
    }

    @Test
    fun `setPreference updates only the targeted toggle and preserves others`() = runTest {
        val id = SipAccountId("one")
        val viewModel = viewModel(accounts = FakeAccounts(mapOf(id to account(id))))

        viewModel.setPreference(id, PreferenceToggle.AutoAnswer, true)
        viewModel.setPreference(id, PreferenceToggle.Voicemail, true)
        runCurrent()

        assertEquals(
            AccountPreferences(autoAnswer = true, callWaiting = true, voicemail = true, doNotDisturb = false),
            viewModel.accountPreferences.value[id.value],
        )
    }

    @Test
    fun `reregister delegates to RegistrationManager register`() = runTest {
        val registration = FakeRegistration()
        val viewModel = viewModel(registration)
        val id = SipAccountId("one")

        viewModel.reregister(id)
        runCurrent()

        assertEquals(listOf(id), registration.registerCalls)
    }

    @Test
    fun `setAccountEnabled delegates to AccountRepository setEnabled`() = runTest {
        val accounts = FakeAccounts()
        val viewModel = YeyoFoneViewModel(accounts, FakeCalls(), FakeMedia(), FakeRoutes(), FakeRegistration(), FakePreferences())
        val id = SipAccountId("one")

        viewModel.setAccountEnabled(id, false)
        runCurrent()

        assertEquals(listOf(id to false), accounts.setEnabledCalls)
    }

    @Test
    fun `answerWaitingCall holds the current call before answering the waiting one`() = runTest {
        val calls = FakeCalls()
        val media = FakeMedia()
        val viewModel = YeyoFoneViewModel(FakeAccounts(), calls, media, FakeRoutes(), FakeRegistration(), FakePreferences())
        val current = CallId("current")
        val waiting = CallId("waiting")

        viewModel.answerWaitingCall(waiting, current)
        runCurrent()

        assertEquals(listOf(current to true), media.heldCalls)
        assertEquals(listOf(waiting), calls.answered)
    }

    @Test
    fun `answerWaitingCall with no current call just answers`() = runTest {
        val calls = FakeCalls()
        val media = FakeMedia()
        val viewModel = YeyoFoneViewModel(FakeAccounts(), calls, media, FakeRoutes(), FakeRegistration(), FakePreferences())
        val waiting = CallId("waiting")

        viewModel.answerWaitingCall(waiting, null)
        runCurrent()

        assertEquals(emptyList(), media.heldCalls)
        assertEquals(listOf(waiting), calls.answered)
    }

    @Test
    fun `swapActiveCall holds the current call and resumes the other`() = runTest {
        val media = FakeMedia()
        val viewModel = YeyoFoneViewModel(FakeAccounts(), FakeCalls(), media, FakeRoutes(), FakeRegistration(), FakePreferences())
        val current = CallId("current")
        val other = CallId("other")

        viewModel.swapActiveCall(current, other)
        runCurrent()

        assertEquals(listOf(current to true, other to false), media.heldCalls)
    }

    @Test
    fun `observeRegistration returns the same flow instance the manager returns`() {
        val registration = FakeRegistration()
        val viewModel = viewModel(registration)
        val id = SipAccountId("one")
        val flow = MutableStateFlow<RegistrationState>(RegistrationState.Registering)
        registration.states[id] = flow

        assertSame(flow, viewModel.observeRegistration(id))
    }

    private fun viewModel(
        registration: FakeRegistration = FakeRegistration(),
        accounts: FakeAccounts = FakeAccounts(),
        preferences: FakePreferences = FakePreferences(),
    ) = YeyoFoneViewModel(accounts, FakeCalls(), FakeMedia(), FakeRoutes(), registration, preferences)

    private fun account(id: SipAccountId) = SipAccount(
        id = id,
        displayName = "Test",
        username = "1000",
        server = SipServerConfiguration(
            domain = "pbx.example.com",
            registrarUri = "sip:pbx.example.com",
            port = 5060,
            transport = TransportProtocol.UDP,
            securityMode = SecurityMode.ALLOW_INSECURE,
        ),
        nat = NatConfiguration(),
    )

    private class FakeAccounts(private val accounts: Map<SipAccountId, SipAccount> = emptyMap()) : AccountRepository {
        val setEnabledCalls = mutableListOf<Pair<SipAccountId, Boolean>>()
        override fun observeAccounts(): Flow<List<SipAccount>> = MutableStateFlow(accounts.values.toList())
        override fun observeAccount(id: SipAccountId): Flow<SipAccount?> = MutableStateFlow(accounts[id])
        override suspend fun save(draft: AccountDraft) = error("not used")
        override suspend fun setEnabled(id: SipAccountId, enabled: Boolean) {
            setEnabledCalls += id to enabled
        }
        override suspend fun delete(id: SipAccountId) = Unit
    }

    private class FakePreferences : AccountPreferencesRepository {
        private val state = MutableStateFlow<Map<String, AccountPreferences>>(emptyMap())
        override fun observe(accountId: SipAccountId): Flow<AccountPreferences> =
            state.map { it[accountId.value] ?: AccountPreferences() }
        override suspend fun update(accountId: SipAccountId, transform: (AccountPreferences) -> AccountPreferences) {
            val current = state.value[accountId.value] ?: AccountPreferences()
            state.value = state.value + (accountId.value to transform(current))
        }
        override suspend fun delete(accountId: SipAccountId) {
            state.value = state.value - accountId.value
        }
    }

    private class FakeCalls : CallManager {
        val answered = mutableListOf<CallId>()
        override val sessions: StateFlow<List<CallSession>> = MutableStateFlow(emptyList())
        override suspend fun call(accountId: SipAccountId, destination: String): CallId = error("not used")
        override suspend fun answer(callId: CallId) { answered += callId }
        override suspend fun reject(callId: CallId) = Unit
        override suspend fun end(callId: CallId) = Unit
        override suspend fun sendDtmf(callId: CallId, digit: Char) = Unit
        override suspend fun transfer(callId: CallId, destination: String) = Unit
        override suspend fun attendedTransfer(callId: CallId, destinationCallId: CallId) = Unit
    }

    private class FakeMedia : MediaManager {
        val heldCalls = mutableListOf<Pair<CallId, Boolean>>()
        override fun observe(callId: CallId): StateFlow<MediaState> = error("not used")
        override suspend fun setMuted(callId: CallId, muted: Boolean) = Unit
        override suspend fun setHeld(callId: CallId, held: Boolean) { heldCalls += callId to held }
    }

    private class FakeRoutes : AudioRouteManager {
        override val availableRoutes: StateFlow<List<AudioRoute>> = MutableStateFlow(emptyList())
        override val selectedRoute: StateFlow<AudioRoute?> = MutableStateFlow(null)
        override suspend fun select(route: AudioRoute) = Unit
    }

    private class FakeRegistration : RegistrationManager {
        val registerCalls = mutableListOf<SipAccountId>()
        val states = mutableMapOf<SipAccountId, StateFlow<RegistrationState>>()
        override fun observe(accountId: SipAccountId): StateFlow<RegistrationState> =
            states.getOrElse(accountId) { MutableStateFlow(RegistrationState.Disabled) }
        override fun observeDetails(accountId: SipAccountId): StateFlow<RegistrationDetails> = error("not used")
        override suspend fun register(accountId: SipAccountId) {
            registerCalls += accountId
        }
        override suspend fun unregister(accountId: SipAccountId) = Unit
    }
}
