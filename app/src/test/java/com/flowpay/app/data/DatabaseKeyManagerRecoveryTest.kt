package com.flowpay.app.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

/**
 * The key-loss recovery contract: a wrapped passphrase blob that can no
 * longer be unwrapped (Keystore entry lost/invalidated, e.g. after some
 * device restores or OS updates) must NEVER propagate an exception — it
 * runs on the app's first database touch, so a throw is a permanent launch
 * crash loop. Instead the stale blob is discarded, the wrapping key is
 * reset, and a fresh passphrase is generated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseKeyManagerRecoveryTest {

    /** Reversible fake of the Keystore wrapper; can simulate a lost key. */
    private class FakeWrapper(var failUnwrap: Boolean = false) : DatabaseKeyManager.Wrapper {
        var discarded = false
        var unwrapAttempts = 0

        override fun wrap(plain: ByteArray) =
            DatabaseKeyManager.WrappedBlob(sealed = plain.reversedArray(), iv = ByteArray(12))

        override fun unwrap(sealed: ByteArray, iv: ByteArray): ByteArray {
            unwrapAttempts++
            if (failUnwrap) throw AEADBadTagException("wrapping key lost")
            return sealed.reversedArray()
        }

        override fun discardKey() {
            discarded = true
        }
    }

    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs
        get() = context.getSharedPreferences(DatabaseKeyManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `first call creates a passphrase and later calls return the same one`() {
        val wrapper = FakeWrapper()
        val first = DatabaseKeyManager.getOrCreatePassphrase(context, wrapper)
        val second = DatabaseKeyManager.getOrCreatePassphrase(context, wrapper)

        assertNotNull(first)
        assertTrue(first.isNotEmpty())
        assertEquals("stable across calls", first, second)
        assertEquals("second call unwraps the stored blob", 1, wrapper.unwrapAttempts)
    }

    @Test
    fun `unwrap failure recovers cleanly instead of throwing`() {
        // Seed a healthy blob first.
        val healthy = FakeWrapper()
        val original = DatabaseKeyManager.getOrCreatePassphrase(context, healthy)
        val staleBlob = prefs.getString(DatabaseKeyManager.PREF_WRAPPED, null)
        assertNotNull(staleBlob)

        // Simulate the Keystore entry being lost: unwrap now throws.
        val broken = FakeWrapper(failUnwrap = true)
        val regenerated = DatabaseKeyManager.getOrCreatePassphrase(context, broken)

        // No exception escaped; a fresh passphrase was produced and persisted.
        assertNotNull(regenerated)
        assertTrue(regenerated.isNotEmpty())
        assertNotEquals("stale passphrase must not be reused", original, regenerated)
        assertTrue("stale wrapping key must be discarded", broken.discarded)
        assertNotEquals(
            "a new blob must replace the stale one",
            staleBlob,
            prefs.getString(DatabaseKeyManager.PREF_WRAPPED, null)
        )

        // And the recovery is stable: the next call unwraps the new blob.
        broken.failUnwrap = false
        assertEquals(regenerated, DatabaseKeyManager.getOrCreatePassphrase(context, broken))
    }

    @Test
    fun `corrupted prefs blob recovers cleanly instead of throwing`() {
        // Garbage that decodes as base64 but can never unwrap.
        prefs.edit()
            .putString(DatabaseKeyManager.PREF_WRAPPED, "Z2FyYmFnZQ==")
            .putString(DatabaseKeyManager.PREF_IV, "aXZpdml2aXZpdg==")
            .commit()

        val wrapper = FakeWrapper(failUnwrap = true)
        val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context, wrapper)

        assertTrue(passphrase.isNotEmpty())
        assertTrue(wrapper.discarded)
        assertFalse(
            "garbage blob must be replaced",
            prefs.getString(DatabaseKeyManager.PREF_WRAPPED, null) == "Z2FyYmFnZQ=="
        )
    }
}
