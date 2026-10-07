package com.yeyofone.core.account

import android.content.Context
import com.yeyofone.core.model.AccountPreferences
import com.yeyofone.core.model.SipAccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomAccountPreferencesRepository internal constructor(
    private val dao: AccountPreferencesDao,
) : AccountPreferencesRepository {
    private val writes = Mutex()

    override fun observe(accountId: SipAccountId): Flow<AccountPreferences> =
        dao.observe(accountId.value).map { it?.toModel() ?: AccountPreferences() }

    override suspend fun update(accountId: SipAccountId, transform: (AccountPreferences) -> AccountPreferences) {
        writes.withLock {
            val current = observe(accountId).first()
            dao.upsert(transform(current).toEntity(accountId))
        }
    }

    override suspend fun delete(accountId: SipAccountId) {
        dao.delete(accountId.value)
    }

    companion object {
        fun create(context: Context): RoomAccountPreferencesRepository =
            RoomAccountPreferencesRepository(AccountDatabase.open(context.applicationContext).accountPreferences())
    }
}

private fun AccountPreferencesEntity.toModel() = AccountPreferences(
    autoAnswer = autoAnswer,
    callWaiting = callWaiting,
    voicemail = voicemail,
    doNotDisturb = doNotDisturb,
    allowIncoming = allowIncoming,
    vibrate = vibrate,
    flipToMute = flipToMute,
    announceCaller = announceCaller,
)

private fun AccountPreferences.toEntity(accountId: SipAccountId) = AccountPreferencesEntity(
    accountId = accountId.value,
    autoAnswer = autoAnswer,
    callWaiting = callWaiting,
    voicemail = voicemail,
    doNotDisturb = doNotDisturb,
    allowIncoming = allowIncoming,
    vibrate = vibrate,
    flipToMute = flipToMute,
    announceCaller = announceCaller,
)
