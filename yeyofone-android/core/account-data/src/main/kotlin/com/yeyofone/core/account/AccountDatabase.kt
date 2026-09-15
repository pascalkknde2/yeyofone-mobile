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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sip_accounts")
internal data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val username: String,
    val authenticationUsername: String,
    val domain: String,
    val registrarUri: String,
    val outboundProxyUri: String?,
    val port: Int,
    val transport: String,
    val securityMode: String,
    val stunServer: String?,
    val turnServer: String?,
    val turnUsername: String?,
    val iceEnabled: Boolean,
    val srtpEnabled: Boolean,
    val registrationExpirySeconds: Int,
    val voicemailNumber: String?,
    val callerId: String?,
    val enabled: Boolean,
)

@Dao
internal interface AccountDao {
    @Query("SELECT * FROM sip_accounts ORDER BY displayName COLLATE NOCASE, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM sip_accounts WHERE id = :id")
    fun observe(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM sip_accounts WHERE authenticationUsername = :username COLLATE NOCASE AND domain = :domain COLLATE NOCASE AND transport = :transport AND id != :excludedId LIMIT 1")
    suspend fun findDuplicate(username: String, domain: String, transport: String, excludedId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE sip_accounts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean): Int

    @Query("DELETE FROM sip_accounts WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Database(entities = [AccountEntity::class], version = 2, exportSchema = true)
internal abstract class AccountDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sip_accounts ADD COLUMN turnUsername TEXT")
                db.execSQL("ALTER TABLE sip_accounts ADD COLUMN srtpEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sip_accounts ADD COLUMN registrationExpirySeconds INTEGER NOT NULL DEFAULT 300")
                db.execSQL("ALTER TABLE sip_accounts ADD COLUMN voicemailNumber TEXT")
                db.execSQL("ALTER TABLE sip_accounts ADD COLUMN callerId TEXT")
            }
        }

        fun open(context: Context): AccountDatabase =
            Room.databaseBuilder(context, AccountDatabase::class.java, "yeyofone-accounts.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
