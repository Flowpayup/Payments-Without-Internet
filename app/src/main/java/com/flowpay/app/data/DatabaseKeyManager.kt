// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
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
 *
 * Key-loss recovery: if the Keystore entry is lost or invalidated (OS
 * update, keystore corruption, some device restores) while the wrapped blob
 * survives, unwrapping throws. That must NEVER propagate — this runs on the
 * app's first database touch, so an unhandled throw is a permanent launch
 * crash loop. Instead the stale blob is discarded and a fresh passphrase is
 * generated; [DatabaseEncryptionMigrator.recoverIfUnreadable] then sets the
 * now-unreadable database aside and the app starts with an empty history.
 * History is convenience data; the bank remains the source of truth.
 *
 * "Never propagate" covers *wrapping* as well as unwrapping, and covers the
 * unchecked failures too — see [recoverable]. Both directions go through it,
 * so there is no path from a sick Keystore to a launch crash.
 */
internal object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"
    private const val KEYSTORE_ALIAS = "flowpay_db_master_key"
    internal const val PREFS_NAME = "flowpay_db_key"
    internal const val PREF_WRAPPED = "wrapped_passphrase"
    internal const val PREF_IV = "wrap_iv"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /** A sealed passphrase blob plus the GCM IV it was sealed with. */
    internal class WrappedBlob(val sealed: ByteArray, val iv: ByteArray)

    /**
     * The crypto seam: wraps/unwraps the passphrase under the device key.
     * The production implementation is the Android Keystore ([KeystoreWrapper]);
     * tests substitute a fake to exercise the key-loss recovery contract on
     * the JVM, where no AndroidKeyStore provider exists.
     */
    internal interface Wrapper {
        @Throws(GeneralSecurityException::class)
        fun wrap(plain: ByteArray): WrappedBlob

        @Throws(GeneralSecurityException::class)
        fun unwrap(sealed: ByteArray, iv: ByteArray): ByteArray

        /** Discard the wrapping key so the next wrap() uses a fresh one. */
        fun discardKey()
    }

    /**
     * Returns the stable database passphrase, creating and wrapping it on
     * first call. The returned string is Base64 (A-Za-z0-9+/=) — safe to
     * embed in a single-quoted SQL literal.
     *
     * Never throws for a lost/invalidated wrapping key: recovery falls
     * through to generating a fresh passphrase (see class KDoc).
     */
    fun getOrCreatePassphrase(context: Context, wrapper: Wrapper = KeystoreWrapper): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(PREF_WRAPPED, null)
        val iv = prefs.getString(PREF_IV, null)
        if (wrapped != null && iv != null) {
            val existing = recoverable("passphrase unwrap") {
                String(
                    wrapper.unwrap(
                        Base64.decode(wrapped, Base64.NO_WRAP),
                        Base64.decode(iv, Base64.NO_WRAP)
                    ),
                    Charsets.UTF_8
                )
            }
            if (existing != null) return existing
            // Keystore key lost/invalidated (or blob corrupted). Discard both
            // sides and fall through to a clean regeneration — the old
            // database is unreadable either way, and the migrator will set it
            // aside rather than let the app crash-loop.
            prefs.edit().remove(PREF_WRAPPED).remove(PREF_IV).apply()
            discardQuietly(wrapper)
        }

        val raw = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(raw, Base64.NO_WRAP)

        // Wrapping can fail too, and used to be unguarded — a sick Keystore
        // threw straight out of here into a launch crash loop. The usual cause
        // is an existing entry that can no longer be used, so drop the entry
        // and try once with a freshly generated wrapping key.
        val plain = passphrase.toByteArray(Charsets.UTF_8)
        val blob = recoverable("passphrase wrap") { wrapper.wrap(plain) }
            ?: run {
                discardQuietly(wrapper)
                recoverable("passphrase wrap retry") { wrapper.wrap(plain) }
            }

        if (blob != null) {
            prefs.edit()
                .putString(PREF_WRAPPED, Base64.encodeToString(blob.sealed, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(blob.iv, Base64.NO_WRAP))
                .apply()
        } else {
            // Nothing can be persisted. Returning the unpersisted passphrase
            // still keeps the app launchable: the next start generates a
            // different one and the migrator sets this database aside, so
            // history stops surviving restarts. Degraded, but far better than
            // an app that cannot open at all.
            Log.e(TAG, "Keystore unusable - passphrase not persisted, history will not survive restart")
        }
        return passphrase
    }

    /**
     * Runs [block], returning null when the Keystore failed in a way we can
     * recover from by regenerating key material.
     *
     * Three distinct failure families, all reaching us on the app's first
     * database touch, where a throw is a permanent launch crash loop:
     *  - [GeneralSecurityException] — the documented case: entry lost or
     *    invalidated, blob corrupt, GCM tag mismatch.
     *  - [ProviderException] — an **unchecked** wrapper the AndroidKeyStore
     *    provider raises on some OEM/StrongBox builds when the keystore daemon
     *    is unhealthy. Being a RuntimeException it slipped past the original
     *    `catch (GeneralSecurityException)` entirely.
     *  - [IOException] — `KeyStore.load` against a corrupted keystore file.
     *
     * Deliberately narrow: any other throwable is a bug in our own code and
     * must still surface rather than being laundered into key regeneration.
     */
    private fun <T> recoverable(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "$what failed (${e.javaClass.simpleName}) - regenerating key material", e)
            null
        } catch (e: ProviderException) {
            Log.w(TAG, "$what failed in the Keystore provider - regenerating key material", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "$what failed reading the Keystore - regenerating key material", e)
            null
        }

    /** Best-effort key removal; failing to discard must never block recovery. */
    private fun discardQuietly(wrapper: Wrapper) {
        runCatching { wrapper.discardKey() }
            .onFailure { Log.w(TAG, "Discarding stale wrapping key failed: ${it.message}") }
    }

    /** Production wrapper: AES/GCM under a non-exportable Android Keystore key. */
    private object KeystoreWrapper : Wrapper {

        override fun wrap(plain: ByteArray): WrappedBlob {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey())
            return WrappedBlob(sealed = cipher.doFinal(plain), iv = cipher.iv)
        }

        override fun unwrap(sealed: ByteArray, iv: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(sealed)
        }

        override fun discardKey() {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        }

        private fun masterKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            try {
                (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
            } catch (e: UnrecoverableKeyException) {
                // Entry exists but can't be loaded (invalidated) — replace it.
                Log.w(TAG, "Master key unrecoverable - regenerating", e)
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }

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
}
