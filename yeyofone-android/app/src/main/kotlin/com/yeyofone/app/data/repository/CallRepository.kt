package com.yeyofone.app.data.repository

import com.yeyofone.app.data.model.CallLog
import com.yeyofone.app.data.model.toCallLog
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.voip.CallHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallRepository(private val source: CallHistoryRepository) {
    fun getCallHistory(): Flow<List<CallLog>> =
        source.observeHistory().map { entries -> entries.map { it.toCallLog() } }

    suspend fun delete(id: CallHistoryId) = source.delete(id)

    suspend fun clear() = source.clear()
}
