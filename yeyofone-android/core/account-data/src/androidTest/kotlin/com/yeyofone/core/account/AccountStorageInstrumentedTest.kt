package com.yeyofone.core.account

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeyofone.core.model.SipAccountId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountStorageInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun migrationFromVersionOneAddsPhaseThreeFields() {
        val name = "account-migration-test.db"
        context.deleteDatabase(name)
        open(name, 1, createVersionOne = true).close()
        val migrated = open(name, 2, createVersionOne = false)

        val columns = buildSet {
            migrated.readableDatabase.query("PRAGMA table_info(sip_accounts)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

        assertTrue("turnUsername" in columns)
        assertTrue("srtpEnabled" in columns)
        assertTrue("registrationExpirySeconds" in columns)
        assertTrue("voicemailNumber" in columns)
        assertTrue("callerId" in columns)
        migrated.close()
        context.deleteDatabase(name)
    }

    @Test
    fun secretSurvivesStoreRecreationAsCiphertextAndCanBeDeleted() {
        val id = SipAccountId("instrumented-secret")
        val secret = "not-a-real-password".toCharArray()
        val first = AndroidKeystoreSecretStore(context)
        first.put(id, secret)

        val stored = context.getSharedPreferences("yeyofone_account_secrets", Context.MODE_PRIVATE)
            .getString("account.${id.value}.password", null).orEmpty()
        assertTrue(AndroidKeystoreSecretStore(context).contains(id))
        assertFalse(stored.contains(String(secret)))

        first.delete(id)
        assertFalse(AndroidKeystoreSecretStore(context).contains(id))
        secret.fill('\u0000')
    }

    private fun open(name: String, version: Int, createVersionOne: Boolean): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (createVersionOne) db.execSQL(VERSION_ONE_SCHEMA)
            }

            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                AccountDatabase.MIGRATION_1_2.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        ).also { it.writableDatabase }
    }

    private companion object {
        const val VERSION_ONE_SCHEMA = """
            CREATE TABLE sip_accounts (
                id TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                username TEXT NOT NULL,
                authenticationUsername TEXT NOT NULL,
                domain TEXT NOT NULL,
                registrarUri TEXT NOT NULL,
                outboundProxyUri TEXT,
                port INTEGER NOT NULL,
                transport TEXT NOT NULL,
                securityMode TEXT NOT NULL,
                stunServer TEXT,
                turnServer TEXT,
                iceEnabled INTEGER NOT NULL,
                enabled INTEGER NOT NULL
            )
        """
    }
}
