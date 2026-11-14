package com.harisudhan.suras.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher passphrase.
 *
 * The passphrase itself is 32 random bytes generated on first launch. It is
 * encrypted with an AES-256-GCM key that lives in the Android Keystore, so the
 * plaintext key never touches disk and cannot be extracted from a backup
 * (`allowBackup` is disabled and the database is excluded from device transfer).
 *
 * [SECURITY] Losing the Keystore key (factory reset, app data clear, or some OS
 * upgrade paths) makes the database unreadable. That is intentional: it is
 * better to fail closed than to store an unencrypted fallback. The caller
 * surfaces a typed error instead of deleting data.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(@ApplicationContext context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun passphrase(): ByteArray {
        val stored = prefs.getString(KEY_WRAPPED_PASSPHRASE, null)
        val iv = prefs.getString(KEY_IV, null)
        if (stored == null || iv == null) {
            return createAndStorePassphrase()
        }
        return try {
            unwrap(
                Base64.decode(stored, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP),
            )
        } catch (error: GeneralSecurityException) {
            throw DatabaseKeyException("The database key could not be unlocked", error)
        } catch (error: IllegalArgumentException) {
            throw DatabaseKeyException("The stored database key is corrupt", error)
        }
    }

    private fun createAndStorePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(passphrase)
        // Commit synchronously so the wrapped key is durably stored before the
        // database is opened with it (apply() would be asynchronous).
        prefs.edit(commit = true) {
            putString(KEY_WRAPPED_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        return passphrase
    }

    private fun unwrap(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Not tied to user authentication: the app must be able to open
                // its own database after a cold start without a biometric prompt.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    class DatabaseKeyException(message: String, cause: Throwable? = null) :
        GeneralSecurityException(message, cause)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "suras_db_key_v1"
        const val PREFS_NAME = "suras_db_key"
        const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"
        const val KEY_IV = "iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PASSPHRASE_BYTES = 32
    }
}
