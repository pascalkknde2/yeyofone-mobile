package com.yeyofone.core.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.yeyofone.core.model.SipAccountId
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
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
internal class AndroidKeystoreSecretStore(context: Context) : SecretStore, AccountSecretProvider {
    private val preferences = context.getSharedPreferences("yeyofone_account_secrets", Context.MODE_PRIVATE)

    override fun put(accountId: SipAccountId, secret: CharArray) {
        val plain = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret))
        val bytes = ByteArray(plain.remaining()).also(plain::get)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
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

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    override fun read(accountId: SipAccountId): CharArray? {
        val encoded = preferences.getString(accountId.preferenceKey, null) ?: return null
        val payload = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
        val iv = ByteArray(payload.get().toInt() and 0xff).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(encrypted)
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
        const val KEY_ALIAS = "yeyofone.account.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
