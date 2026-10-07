package com.yeyofone.core.account

import com.yeyofone.core.model.ForwardingState
import com.yeyofone.core.model.SipAccountId
import kotlinx.coroutines.flow.Flow

interface ForwardingStateRepository {
    /** Emits the all-default (not forwarding) [ForwardingState] when nothing has been cached yet. */
    fun observe(accountId: SipAccountId): Flow<ForwardingState>
    suspend fun save(accountId: SipAccountId, state: ForwardingState)
    suspend fun delete(accountId: SipAccountId)
}
