package com.yeyofone.core.account

import com.yeyofone.core.model.AccountPreferences
import com.yeyofone.core.model.SipAccountId
import kotlinx.coroutines.flow.Flow

interface AccountPreferencesRepository {
    /** Emits the all-default [AccountPreferences] when no row has been saved for this account yet. */
    fun observe(accountId: SipAccountId): Flow<AccountPreferences>
    suspend fun update(accountId: SipAccountId, transform: (AccountPreferences) -> AccountPreferences)
    suspend fun delete(accountId: SipAccountId)
}
