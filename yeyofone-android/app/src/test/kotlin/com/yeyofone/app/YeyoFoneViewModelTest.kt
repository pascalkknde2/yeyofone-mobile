package com.yeyofone.app

import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.RegistrationDetails
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.AudioRouteManager
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.MediaManager
import com.yeyofone.core.voip.RegistrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun `setPreference creates an entry defaulting other toggles`() {
        val viewModel = viewModel()
        val id = SipAccountId("one")

        viewModel.setPreference(id, PreferenceToggle.AutoAnswer, false)

        assertEquals(
            AccountPreferences(autoAnswer = false, callWaiting = true, voicemail = false, doNotDisturb = false),
            viewModel.accountPreferences.value[id.value],
        )
    }

    @Test
    fun `setPreference updates only the targeted toggle and preserves others`() {
        val viewModel = viewModel()
        val id = SipAccountId("one")

        viewModel.setPreference(id, PreferenceToggle.AutoAnswer, false)
        viewModel.setPreference(id, PreferenceToggle.Voicemail, true)

        assertEquals(
            AccountPreferences(autoAnswer = false, callWaiting = true, voicemail = true, doNotDisturb = false),
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
        val viewModel = YeyoFoneViewModel(accounts, FakeCalls(), FakeMedia(), FakeRoutes(), FakeRegistration())
        val id = SipAccountId("one")

        viewModel.setAccountEnabled(id, false)
        runCurrent()

        assertEquals(listOf(id to false), accounts.setEnabledCalls)
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

    private fun viewModel(registration: FakeRegistration = FakeRegistration()) =
        YeyoFoneViewModel(FakeAccounts(), FakeCalls(), FakeMedia(), FakeRoutes(), registration)

    private class FakeAccounts : AccountRepository {
        val setEnabledCalls = mutableListOf<Pair<SipAccountId, Boolean>>()
        override fun observeAccounts(): Flow<List<SipAccount>> = MutableStateFlow(emptyList())
        override fun observeAccount(id: SipAccountId): Flow<SipAccount?> = MutableStateFlow(null)
        override suspend fun save(draft: AccountDraft) = error("not used")
        override suspend fun setEnabled(id: SipAccountId, enabled: Boolean) {
            setEnabledCalls += id to enabled
        }
        override suspend fun delete(id: SipAccountId) = Unit
    }

    private class FakeCalls : CallManager {
        override val sessions: StateFlow<List<CallSession>> = MutableStateFlow(emptyList())
        override suspend fun call(accountId: SipAccountId, destination: String): CallId = error("not used")
        override suspend fun answer(callId: CallId) = Unit
        override suspend fun reject(callId: CallId) = Unit
        override suspend fun end(callId: CallId) = Unit
        override suspend fun sendDtmf(callId: CallId, digit: Char) = Unit
        override suspend fun transfer(callId: CallId, destination: String) = Unit
        override suspend fun attendedTransfer(callId: CallId, destinationCallId: CallId) = Unit
    }

    private class FakeMedia : MediaManager {
        override fun observe(callId: CallId): StateFlow<MediaState> = error("not used")
        override suspend fun setMuted(callId: CallId, muted: Boolean) = Unit
        override suspend fun setHeld(callId: CallId, held: Boolean) = Unit
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
