package com.yeyofone.core.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeyofone.core.model.SipAccountId
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEC-09: exercises the real Android Keystore. Uses its own key alias and preferences file inside
 * the test APK's sandbox, so it never touches the app's stored credentials.
 */
@RunWith(AndroidJUnit4::class)
class SecretStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val id = SipAccountId("sec09-account")
    private val secret = "not-a-real-password"

    private fun store(alias: String = ALIAS, prefs: String = PREFS) =
        AndroidKeystoreSecretStore(context, preferencesName = prefs, keyAlias = alias)

    private fun storedValue(prefs: String = PREFS) =
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString("account.${id.value}.password", null)

    @Before
    @After
    fun reset() {
        listOf(PREFS, OTHER_PREFS).forEach { context.deleteSharedPreferences(it) }
        listOf(ALIAS, OTHER_ALIAS).forEach(::deleteKey)
    }

    @Test
    fun secretRoundTripsAcrossInstancesAndIsStoredAsCiphertext() {
        store().put(id, secret.toCharArray())

        assertArrayEquals(secret.toCharArray(), store().read(id))
        val stored = storedValue().orEmpty()
        assertTrue(stored.isNotEmpty())
        assertFalse(stored.contains(secret))
    }

    @Test
    fun deletionRemovesTheSecretAndIsIdempotent() {
        store().put(id, secret.toCharArray())
        store().delete(id)

        assertFalse(store().contains(id))
        assertNull(store().read(id))
        store().delete(id)
    }

    @Test
    fun lostOrInvalidatedKeyMakesTheSecretUnavailableAndIsCleanedUp() {
        // Same state as a Keystore reset, or app data restored/transferred without its key.
        store().put(id, secret.toCharArray())
        deleteKey(ALIAS)

        assertNull(store().read(id))
        assertFalse("unreadable ciphertext must be erased", store().contains(id))
        assertNull(storedValue())

        // A new key is created transparently and the account can be saved again.
        store().put(id, "replacement".toCharArray())
        assertArrayEquals("replacement".toCharArray(), store().read(id))
    }

    @Test
    fun corruptCiphertextIsDiscarded() {
        store().put(id, secret.toCharArray())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("account.${id.value}.password", "AAAA").commit()

        assertNull(store().read(id))
        assertFalse(store().contains(id))
    }

    @Test
    fun unreadableSecretIsReportedAsUnavailableToRegistration() = runBlocking {
        store().put(id, secret.toCharArray())
        deleteKey(ALIAS)

        var consumed = false
        assertFalse(store().consume(id) { consumed = true })
        assertFalse(consumed)
    }

    @Test
    fun storesWithDifferentKeysAreIsolated() {
        store().put(id, secret.toCharArray())
        store(OTHER_ALIAS, OTHER_PREFS).put(id, "push-credential".toCharArray())

        deleteKey(ALIAS)

        assertNull(store().read(id))
        assertArrayEquals("push-credential".toCharArray(), store(OTHER_ALIAS, OTHER_PREFS).read(id))
    }

    private fun deleteKey(alias: String) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private companion object {
        const val ALIAS = "yeyofone.sec09.test.v1"
        const val PREFS = "yeyofone_sec09_test_secrets"
        const val OTHER_ALIAS = "yeyofone.sec09.test.other.v1"
        const val OTHER_PREFS = "yeyofone_sec09_test_other"
    }
}
