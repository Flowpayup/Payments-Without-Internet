package com.flowpay.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides the SQLCipher passphrase for the transaction database.
 *
 * A random 32-byte passphrase is generated once (kept as its Base64 string
 * so the same value can be used both as the SupportOpenHelperFactory key
 * and inside an ATTACH ... KEY SQL literal), wrapped with AES/GCM under a
 * non-exportable Android Keystore key, and stored as a wrapped blob in a
 * dedicated SharedPreferences file. That prefs file is excluded from all
 * backups (see backup_rules.xml / data_extraction_rules.xml): a wrapped
 * blob restored onto another device would be undecryptable anyway, since
 * the Keystore key never leaves this device's hardware.
 */
internal object DatabaseKeyManager {

    private const val KEYSTORE_ALIAS = "flowpay_db_master_key"
    internal const val PREFS_NAME = "flowpay_db_key"
    private const val PREF_WRAPPED = "wrapped_passphrase"
    private const val PREF_IV = "wrap_iv"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /**
     * Returns the stable database passphrase, creating and wrapping it on
     * first call. The returned string is Base64 (A-Za-z0-9+/=) — safe to
     * embed in a single-quoted SQL literal.
     */
    fun getOrCreatePassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(PREF_WRAPPED, null)
        val iv = prefs.getString(PREF_IV, null)
        if (wrapped != null && iv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            return String(cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP)), Charsets.UTF_8)
        }

        val raw = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(raw, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val sealed = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_WRAPPED, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
