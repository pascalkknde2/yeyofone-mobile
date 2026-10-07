package com.yeyofone.core.account

import android.content.Context
import com.yeyofone.core.model.ForwardingState
import com.yeyofone.core.model.SipAccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomForwardingStateRepository internal constructor(
    private val dao: ForwardingStateDao,
) : ForwardingStateRepository {
    override fun observe(accountId: SipAccountId): Flow<ForwardingState> =
        dao.observe(accountId.value).map { it?.toModel() ?: ForwardingState() }

    override suspend fun save(accountId: SipAccountId, state: ForwardingState) {
        dao.upsert(state.toEntity(accountId))
    }

    override suspend fun delete(accountId: SipAccountId) {
        dao.delete(accountId.value)
    }

    companion object {
        fun create(context: Context): RoomForwardingStateRepository =
            RoomForwardingStateRepository(AccountDatabase.open(context.applicationContext).forwardingState())
    }
}

private fun ForwardingStateEntity.toModel() = ForwardingState(enabled = enabled, destination = destination)

private fun ForwardingState.toEntity(accountId: SipAccountId) =
    ForwardingStateEntity(accountId = accountId.value, enabled = enabled, destination = destination)
