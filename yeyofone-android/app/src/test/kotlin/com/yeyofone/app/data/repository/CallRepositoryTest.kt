package com.yeyofone.app.data.repository

import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.CallHistoryRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import com.yeyofone.app.data.model.CallType

class CallRepositoryTest {
    @Test
    fun `observeCallLogs maps core history entries to UI models`() = runTest {
        val source = FakeHistoryRepository()
        val entry = CallHistoryEntry(
            id = CallHistoryId("call-1"),
            accountId = SipAccountId("account-1"),
            remoteUri = "1001",
            direction = CallDirection.OUTGOING,
            startedAt = Instant.parse("2026-09-17T12:00:00Z"),
            connectedAt = Instant.parse("2026-09-17T12:00:05Z"),
            endedAt = Instant.parse("2026-09-17T12:01:05Z"),
        )
        source.entries.value = listOf(entry)

        val callLog = CallRepository(source).getCallHistory().first().single()

        assertEquals(entry.id, callLog.id)
        assertEquals(entry.accountId, callLog.accountId)
        assertEquals("1001", callLog.remoteUri)
        assertEquals("1001", callLog.dialDestination)
        assertEquals(60, callLog.duration?.seconds)
        assertEquals(CallType.OUTGOING, callLog.callType)
        assertFalse(callLog.hasVoicemail)
    }

    @Test
    fun `dial destination strips display name and SIP host`() = runTest {
        val source = FakeHistoryRepository()
        source.entries.value = listOf(
            CallHistoryEntry(
                id = CallHistoryId("call-2"),
                accountId = SipAccountId("account-1"),
                remoteUri = "\"Sarah Jenkins\" <sip:1005@sysinfos.co.uk>",
                direction = CallDirection.INCOMING,
                startedAt = Instant.parse("2026-09-17T12:00:00Z"),
                endedAt = Instant.parse("2026-09-17T12:01:00Z"),
            ),
        )

        val callLog = CallRepository(source).getCallHistory().first().single()

        assertEquals("1005", callLog.dialDestination)
    }

    @Test
    fun `dial destination uses the bracket target even without a SIP scheme`() = runTest {
        val source = FakeHistoryRepository()
        source.entries.value = listOf(
            CallHistoryEntry(
                id = CallHistoryId("call-3"),
                accountId = SipAccountId("account-1"),
                remoteUri = "\"Sarah Jenkins\" <1005@sysinfos.co.uk>",
                direction = CallDirection.OUTGOING,
                startedAt = Instant.parse("2026-09-17T12:00:00Z"),
            ),
        )

        val callLog = CallRepository(source).getCallHistory().first().single()

        assertEquals("1005", callLog.dialDestination)
    }

    @Test
    fun `delete and clear delegate to core repository`() = runTest {
        val source = FakeHistoryRepository()
        val repository = CallRepository(source)
        val id = CallHistoryId("call-1")

        repository.delete(id)
        repository.clear()

        assertEquals(listOf(id), source.deleted)
        assertEquals(1, source.clearCount)
    }

    private class FakeHistoryRepository : CallHistoryRepository {
        val entries = MutableStateFlow<List<CallHistoryEntry>>(emptyList())
        val deleted = mutableListOf<CallHistoryId>()
        var clearCount = 0

        override fun observeHistory(): Flow<List<CallHistoryEntry>> = entries
        override suspend fun upsert(entry: CallHistoryEntry) = Unit
        override suspend fun delete(id: CallHistoryId) { deleted += id }
        override suspend fun clear() { clearCount++ }
    }
}
