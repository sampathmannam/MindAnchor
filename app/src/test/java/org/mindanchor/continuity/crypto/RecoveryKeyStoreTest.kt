package org.mindanchor.continuity.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Robolectric round-trip for [RecoveryKeyStore]. Mirrors
 * `GoogleDriveAuthTokenStoreRoundTripFindingTest`'s setup exactly: the
 * production form wraps a Keystore-backed `EncryptedSharedPreferences`
 * file, but Robolectric's Keystore stub does not back the [MasterKey][androidx.security.crypto.MasterKey]
 * the encrypted form needs, so the test injects a plain [SharedPreferences]
 * into the same constructor production code uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecoveryKeyStoreTest {

    private val prefsName = "test_mindanchor_recovery_key"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun sampleKey(seed: Int): RecoveryKey =
        RecoveryKeyCodec.generate { ByteArray(32) { i -> ((seed + i) and 0xFF).toByte() } }

    @Before fun resetStore() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs(ctx).edit().clear().commit()
    }

    @Test fun `empty store reads null and unverified`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = RecoveryKeyStore(prefs(ctx))

        assertNull(store.current())
        assertFalse(store.isVerified())
    }

    @Test fun `save then current round-trips the key`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = RecoveryKeyStore(prefs(ctx))
        val key = sampleKey(1)

        store.save(key)
        val restored = store.current()

        assertTrue(restored != null && key.bytes.contentEquals(restored.bytes))
        assertEquals(key.keyId, restored?.keyId)
    }

    @Test fun `isVerified is false initially and true after markVerified`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = RecoveryKeyStore(prefs(ctx))
        store.save(sampleKey(2))

        assertFalse(store.isVerified())

        store.markVerified()

        assertTrue(store.isVerified())
    }

    @Test fun `saving a new key resets the verified flag`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = RecoveryKeyStore(prefs(ctx))
        store.save(sampleKey(3))
        store.markVerified()
        assertTrue(store.isVerified())

        store.save(sampleKey(4))

        assertFalse("a freshly saved key must not inherit the previous key's verified state", store.isVerified())
    }

    @Test fun `clear removes the key, its id, and the verified flag`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = RecoveryKeyStore(prefs(ctx))
        store.save(sampleKey(5))
        store.markVerified()

        store.clear()

        assertNull(store.current())
        assertFalse(store.isVerified())
    }

    @Test fun `a fresh RecoveryKeyStore instance reads the last save (file is process-wide)`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val key = sampleKey(6)
        RecoveryKeyStore(prefs(ctx)).save(key)

        val fresh = RecoveryKeyStore(prefs(ctx))

        val restored = fresh.current()
        assertTrue(restored != null && key.bytes.contentEquals(restored.bytes))
    }
}
