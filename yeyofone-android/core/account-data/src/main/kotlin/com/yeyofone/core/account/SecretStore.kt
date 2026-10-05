package com.yeyofone.core.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.yeyofone.core.model.SipAccountId
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SecretStore {
    fun put(accountId: SipAccountId, secret: CharArray)
    fun contains(accountId: SipAccountId): Boolean
    fun delete(accountId: SipAccountId)
    fun read(accountId: SipAccountId): CharArray?
}

fun interface AccountSecretProvider {
    suspend fun consume(accountId: SipAccountId, consumer: suspend (CharArray) -> Unit): Boolean
}

/** Stores only AES-GCM ciphertext; the non-exportable AES key remains in Android Keystore. */
internal class AndroidKeystoreSecretStore(
    context: Context,
    preferencesName: String = "yeyofone_account_secrets",
    private val keyAlias: String = "yeyofone.account.secrets.v1",
) : SecretStore, AccountSecretProvider {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun put(accountId: SipAccountId, secret: CharArray) {
        val plain = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret))
        val bytes = ByteArray(plain.remaining()).also(plain::get)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key())
            } catch (_: InvalidKeyException) {
                // Covers KeyPermanentlyInvalidatedException: replace the key once. Secrets sealed
                // with the old key become unreadable and are discarded by read().
                deleteKey()
                cipher.init(Cipher.ENCRYPT_MODE, key())
            }
            val encrypted = cipher.doFinal(bytes)
            val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
                .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
            check(preferences.edit().putString(accountId.preferenceKey, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) {
                "Unable to persist encrypted account secret"
            }
        } finally {
            bytes.fill(0)
        }
    }

    override fun contains(accountId: SipAccountId): Boolean = preferences.contains(accountId.preferenceKey)

    override fun delete(accountId: SipAccountId) {
        check(preferences.edit().remove(accountId.preferenceKey).commit()) {
            "Unable to remove encrypted account secret"
        }
    }

    private fun existingKey(): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(keyAlias, null) as? SecretKey

    private fun deleteKey() = KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)

    private fun key(): SecretKey {
        existingKey()?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    /**
     * Returns null when the secret is absent or can no longer be decrypted: the Keystore key was
     * removed or invalidated (Keystore reset, data restored or transferred without its key) or the
     * ciphertext is corrupt. An unreadable entry is erased so callers report the credential as
     * unavailable (asking for it again) instead of failing and retrying on every attempt.
     */
    override fun read(accountId: SipAccountId): CharArray? {
        val encoded = preferences.getString(accountId.preferenceKey, null) ?: return null
        val plain = try {
            val payload = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val iv = ByteArray(payload.get().toInt() and 0xff).also(payload::get)
            val encrypted = ByteArray(payload.remaining()).also(payload::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = existingKey() ?: throw InvalidKeyException("Key is missing")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            if (e !is GeneralSecurityException && e !is IllegalArgumentException && e !is BufferUnderflowException) throw e
            Log.w(TAG, "Discarding an unreadable stored secret (${e.javaClass.simpleName})")
            preferences.edit().remove(accountId.preferenceKey).commit()
            return null
        }
        return try {
            StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain)).let { chars ->
                CharArray(chars.remaining()).also(chars::get)
            }
        } finally {
            plain.fill(0)
        }
    }

    override suspend fun consume(accountId: SipAccountId, consumer: suspend (CharArray) -> Unit): Boolean {
        val secret = read(accountId) ?: return false
        try {
            consumer(secret)
        } finally {
            secret.fill('\u0000')
        }
        return true
    }

    private val SipAccountId.preferenceKey: String get() = "account.${value}.password"

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TAG = "YeyoFoneSecrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * Keystore-protected storage for each account's push-relay device credential, kept apart from SIP
 * passwords under its own key and file. Values are opaque here; the app validates their contents.
 */
class PushCredentialStore internal constructor(private val store: SecretStore) {
    fun save(accountId: SipAccountId, credential: CharArray) = store.put(accountId, credential)
    fun contains(accountId: SipAccountId): Boolean = store.contains(accountId)
    fun read(accountId: SipAccountId): CharArray? = store.read(accountId)
    fun delete(accountId: SipAccountId) = store.delete(accountId)

    companion object {
        fun create(context: Context): PushCredentialStore = PushCredentialStore(
            AndroidKeystoreSecretStore(
                context.applicationContext,
                preferencesName = "yeyofone_push_credentials",
                keyAlias = "yeyofone.push.credentials.v1",
            ),
        )
    }
}
