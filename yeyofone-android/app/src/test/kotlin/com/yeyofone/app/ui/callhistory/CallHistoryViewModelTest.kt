package com.yeyofone.app.ui.callhistory

import com.yeyofone.app.data.model.CallType
import com.yeyofone.app.data.repository.CallRepository
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.CallHistoryRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CallHistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `tabs filter real call history`() = runTest {
        val source = FakeHistoryRepository()
        source.entries.value = listOf(
            entry("outgoing", CallDirection.OUTGOING, connected = true),
            entry("missed", CallDirection.INCOMING, connected = false),
        )
        val viewModel = CallHistoryViewModel(CallRepository(source))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(2, viewModel.uiState.value.calls.size)

        viewModel.onTabSelected(MISSED_TAB)
        assertEquals(listOf(CallType.MISSED), viewModel.uiState.value.calls.map { it.callType })

        viewModel.onTabSelected(VOICEMAIL_TAB)
        assertEquals(emptyList(), viewModel.uiState.value.calls)
    }

    private fun entry(id: String, direction: CallDirection, connected: Boolean) = CallHistoryEntry(
        id = CallHistoryId(id),
        accountId = SipAccountId("account"),
        remoteUri = "sip:1001@example.com",
        direction = direction,
        startedAt = Instant.parse("2026-09-17T12:00:00Z"),
        connectedAt = Instant.parse("2026-09-17T12:00:05Z").takeIf { connected },
        endedAt = Instant.parse("2026-09-17T12:01:00Z"),
    )

    private class FakeHistoryRepository : CallHistoryRepository {
        val entries = MutableStateFlow<List<CallHistoryEntry>>(emptyList())
        override fun observeHistory(): Flow<List<CallHistoryEntry>> = entries
        override suspend fun upsert(entry: CallHistoryEntry) = Unit
        override suspend fun delete(id: CallHistoryId) = Unit
        override suspend fun clear() = Unit
    }
}
