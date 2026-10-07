package com.yeyofone.app

import android.content.Context
import com.yeyofone.core.account.ForwardingStateRepository
import com.yeyofone.core.model.ForwardingState
import com.yeyofone.core.model.SipAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges the relay's forwarding endpoints and [ForwardingStateRepository] (the local cache the
 * UI reads). The cache is only ever written from a relay response, never from what the user
 * typed, so it can't show a state the PBX never actually confirmed.
 *
 * Not internal: it's a constructor parameter of the public [YeyoFoneViewModel].
 */
interface ForwardingCoordinator {
    suspend fun refresh(account: SipAccount)
    suspend fun update(account: SipAccount, state: ForwardingState): ForwardingClient.Result
}

internal class AndroidForwardingCoordinator(
    private val context: Context,
    private val repository: ForwardingStateRepository,
) : ForwardingCoordinator {
    override suspend fun refresh(account: SipAccount) {
        val result = withContext(Dispatchers.IO) { ForwardingClient.get(context, account) }
        if (result is ForwardingClient.Result.Success) repository.save(account.id, result.state)
    }

    override suspend fun update(account: SipAccount, state: ForwardingState): ForwardingClient.Result {
        val result = withContext(Dispatchers.IO) { ForwardingClient.set(context, account, state) }
        if (result is ForwardingClient.Result.Success) repository.save(account.id, result.state)
        return result
    }
}
