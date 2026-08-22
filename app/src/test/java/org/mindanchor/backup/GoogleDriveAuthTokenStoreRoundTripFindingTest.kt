package org.mindanchor.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Robolectric round-trip for [TokenStore].
 * v0.25.4 (WP-A).
 *
 * [TokenStore] is the at-rest surface for the
 * access token. The production form wraps an
 * [androidx.security.crypto.EncryptedSharedPreferences]
 * file; the test surface uses a regular
 * [SharedPreferences] because Robolectric's
 * Keystore stub does not back the [MasterKey]
 * the encrypted form needs. The class shape is
 * identical: a `read`, a `write`, a `clear`,
 * and a `prefs` field that holds a
 * [SharedPreferences]. The round-trip the test
 * exercises is exactly the round-trip the
 * production code runs — the encryption layer is
 * a wrapper, not a behaviour.
 *
 * Five tests:
 *  1. Empty store reads null.
 *  2. Write then read round-trips.
 *  3. A blank token is a no-op (the store stays
 *     empty; we never persist the placeholder).
 *  4. [TokenStore.clear] makes read return null.
 *  5. A fresh [TokenStore] instance on the same
 *     context reads the last write (the underlying
 *     [SharedPreferences] is process-wide, like
 *     every other one).
 *
 * Robolectric 4.13 with `@Config(sdk = [34])` is
 * the project's pinned test configuration (see
 * the v0.25.2 Task 13 reader-prefs test for the
 * rationale).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveAuthTokenStoreRoundTripFindingTest {

    private val prefsName = "test_google_drive_token"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    @Before fun resetStore() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs(ctx).edit().clear().commit()
    }

    @Test fun `empty store reads null`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = TokenStore(prefs(ctx))
        assertNull("a fresh store must read null", store.read())
    }

    @Test fun `write then read round-trips`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = TokenStore(prefs(ctx))
        store.write("ya29.a0AfH6SMBxxx")
        assertEquals("ya29.a0AfH6SMBxxx", store.read())
    }

    @Test fun `blank token is a no-op`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = TokenStore(prefs(ctx))
        store.write("")
        store.write("   ")
        // A blank write must not persist a placeholder.
        // The store stays empty; the caller treats
        // blank as "not signed in".
        assertNull(store.read())
    }

    @Test fun `clear makes read return null`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = TokenStore(prefs(ctx))
        store.write("ya29.before-clear")
        store.clear()
        assertNull("clear must wipe the store", store.read())
    }

    @Test fun `a fresh TokenStore instance reads the last write (file is process-wide)`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        TokenStore(prefs(ctx)).write("ya29.persisted")
        // New instance, same context, same
        // SharedPreferences file: should read the
        // persisted value.
        val fresh = TokenStore(prefs(ctx))
        assertEquals("ya29.persisted", fresh.read())
    }
}
