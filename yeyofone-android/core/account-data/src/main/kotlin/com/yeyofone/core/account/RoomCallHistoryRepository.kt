package com.yeyofone.core.account

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallEndReason
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallHistoryId
import com.yeyofone.core.model.SipAccountId
import com.yeyofone.core.voip.CallHistoryRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCallHistoryRepository private constructor(
    private val dao: CallHistoryDao,
) : CallHistoryRepository {
    override fun observeHistory(): Flow<List<CallHistoryEntry>> =
        dao.observeAll().map { entries -> entries.map(CallHistoryEntity::toModel) }

    override suspend fun upsert(entry: CallHistoryEntry) = dao.upsert(entry.toEntity())

    override suspend fun delete(id: CallHistoryId) = dao.delete(id.value)

    override suspend fun clear() = dao.clear()

    companion object {
        fun create(context: Context): RoomCallHistoryRepository = RoomCallHistoryRepository(
            CallHistoryDatabase.open(context.applicationContext).history(),
        )
    }
}

@Entity(tableName = "call_history")
internal data class CallHistoryEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val remoteUri: String,
    val direction: String,
    val startedAtEpochMillis: Long,
    val connectedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val endReason: String?,
)

@Dao
internal interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<CallHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CallHistoryEntity)

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM call_history")
    suspend fun clear()
}

@Database(entities = [CallHistoryEntity::class], version = 1, exportSchema = true)
internal abstract class CallHistoryDatabase : RoomDatabase() {
    abstract fun history(): CallHistoryDao

    companion object {
        fun open(context: Context): CallHistoryDatabase =
            Room.databaseBuilder(context, CallHistoryDatabase::class.java, "yeyofone-call-history.db").build()
    }
}

private fun CallHistoryEntry.toEntity() = CallHistoryEntity(
    id = id.value,
    accountId = accountId.value,
    remoteUri = remoteUri,
    direction = direction.name,
    startedAtEpochMillis = startedAt.toEpochMilli(),
    connectedAtEpochMillis = connectedAt?.toEpochMilli(),
    endedAtEpochMillis = endedAt?.toEpochMilli(),
    endReason = endReason?.name,
)

private fun CallHistoryEntity.toModel() = CallHistoryEntry(
    id = CallHistoryId(id),
    accountId = SipAccountId(accountId),
    remoteUri = remoteUri,
    direction = CallDirection.valueOf(direction),
    startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
    connectedAt = connectedAtEpochMillis?.let(Instant::ofEpochMilli),
    endedAt = endedAtEpochMillis?.let(Instant::ofEpochMilli),
    endReason = endReason?.let(CallEndReason::valueOf),
)
