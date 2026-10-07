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

@Entity(tableName = "account_preferences")
internal data class AccountPreferencesEntity(
    @PrimaryKey val accountId: String,
    val autoAnswer: Boolean,
    val callWaiting: Boolean,
    val voicemail: Boolean,
    val doNotDisturb: Boolean,
    val allowIncoming: Boolean,
    val vibrate: Boolean,
    val flipToMute: Boolean,
    val announceCaller: Boolean,
)

@Dao
internal interface AccountPreferencesDao {
    @Query("SELECT * FROM account_preferences WHERE accountId = :accountId")
    fun observe(accountId: String): Flow<AccountPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferences: AccountPreferencesEntity)

    @Query("DELETE FROM account_preferences WHERE accountId = :accountId")
    suspend fun delete(accountId: String): Int
}

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

@Database(entities = [AccountEntity::class, AccountPreferencesEntity::class], version = 3, exportSchema = true)
internal abstract class AccountDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun accountPreferences(): AccountPreferencesDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `account_preferences` (
                        `accountId` TEXT NOT NULL PRIMARY KEY,
                        `autoAnswer` INTEGER NOT NULL,
                        `callWaiting` INTEGER NOT NULL,
                        `voicemail` INTEGER NOT NULL,
                        `doNotDisturb` INTEGER NOT NULL,
                        `allowIncoming` INTEGER NOT NULL,
                        `vibrate` INTEGER NOT NULL,
                        `flipToMute` INTEGER NOT NULL,
                        `announceCaller` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile private var instance: AccountDatabase? = null

        // AccountRepository and AccountPreferencesRepository each open this database
        // independently; two live Room instances against the same file risk missed
        // invalidation notifications between them, so this is memoized per process.
        fun open(context: Context): AccountDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AccountDatabase::class.java, "yeyofone-accounts.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
