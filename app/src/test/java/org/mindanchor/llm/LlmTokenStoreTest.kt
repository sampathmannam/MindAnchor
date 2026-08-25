package org.mindanchor.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.30+ (security audit 2026-08-25) — pin the
 * [LlmTokenStore] round-trip and the shared
 * validation rules. The class shape is the same
 * as the existing
 * [org.mindanchor.backup.TokenStore] test pattern:
 * Robolectric cannot back the
 * [androidx.security.crypto.MasterKey] (no
 * AndroidKeyStore on the test runner), so the
 * production Keystore-backed path is not
 * exercised here. The class shape (read / write /
 * clear) IS exercised; the [LlmTokenStore.create]
 * factory wraps the Keystore-backed
 * [EncryptedSharedPreferences] in production and
 * is covered by the integration suite (which runs
 * on a real device with a real Keystore).
 *
 * The tests below verify:
 *  1. Empty store reads null.
 *  2. Write then read round-trips.
 *  3. Blank token is a no-op.
 *  4. [LlmTokenStore.clear] makes read return null.
 *  5. A fresh [LlmTokenStore] instance on the same
 *     context reads the last write.
 *  6. The shared validation rules (trim, cap,
 *     control-char filter) are applied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class LlmTokenStoreTest {

    private val prefsName = "test_llm_token"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    @Before fun resetStore() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs(ctx).edit().clear().commit()
    }

    @Test fun `empty store reads null`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        assertNull("a fresh store must read null", store.apiKey)
    }

    @Test fun `write then read round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        store.setApiKey("sk-test-12345")
        assertEquals("sk-test-12345", store.apiKey)
    }

    @Test fun `blank token is a no-op`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        store.setApiKey("")
        store.setApiKey("   ")
        // A blank write must not persist a placeholder.
        // The store stays empty; the caller treats
        // blank as "no key configured".
        assertNull(store.apiKey)
    }

    @Test fun `clear makes read return null`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        store.setApiKey("sk-before-clear")
        store.clear()
        assertNull("clear must wipe the store", store.apiKey)
    }

    @Test fun `a fresh LlmTokenStore instance reads the last write (file is process-wide)`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        LlmTokenStore(prefs(ctx)).setApiKey("sk-persisted")
        // New instance, same context, same
        // SharedPreferences file: should read the
        // persisted value.
        val fresh = LlmTokenStore(prefs(ctx))
        assertEquals("sk-persisted", fresh.apiKey)
    }

    @Test fun `setApiKey with null preferences is a safe no-op`() = runBlocking {
        // v0.30+ — when the [LlmTokenStore.create]
        // factory fails (Keystore unavailable), the
        // resulting [LlmTokenStore] has a null
        // [SharedPreferences] and every read returns
        // null. The writes are no-ops. This is the
        // graceful-degradation path; the user is not
        // stranded, the Settings flow asks them to
        // configure the LLM provider.
        val store = LlmTokenStore(null)
        store.setApiKey("sk-anything")
        assertNull(store.apiKey)
    }

    @Test fun `setApiKey with null preferences does not throw`() = runBlocking {
        val store = LlmTokenStore(null)
        // No exception — the contract is "never crash
        // the launcher". The encryption layer is a
        // wrapper, not a behaviour; the wrapper's
        // failure mode is silent.
        org.junit.Assert.assertNotNull(
            runCatching { store.setApiKey("sk-anything") }.getOrNull(),
        )
    }

    @Test fun `setApiKey trims whitespace`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        store.setApiKey("  sk-abc-12345  ")
        assertEquals("sk-abc-12345", store.apiKey)
    }

    @Test fun `setApiKey strips CRLF from header-injection payload`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        store.setApiKey("abc\r\nX-Evil-Header: pwned")
        val stored = store.apiKey
        assertNotNull("a clean input should be stored", stored)
        assertEquals(false, stored!!.contains("\r"))
        assertEquals(false, stored.contains("\n"))
        assertEquals("abcX-Evil-Header: pwned", stored)
    }

    @Test fun `setApiKey caps at MAX_KEY_LEN`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LlmTokenStore(prefs(ctx))
        // 10 MB ASCII payload — the validation caps
        // at [LlmPrefs.MAX_KEY_LEN] = 256. The store
        // never sees the full 10 MB.
        val payload = "A".repeat(10 * 1024 * 1024)
        store.setApiKey(payload)
        val stored = store.apiKey
        assertNotNull("a clean input should be stored", stored)
        assertEquals(LlmPrefs.MAX_KEY_LEN, stored!!.length)
    }

    @Test fun `companion create returns LlmTokenStore even on Keystore failure`() {
        // v0.30+ — the [LlmTokenStore.create] factory
        // catches the Keystore failure and returns a
        // [LlmTokenStore] with a null preferences.
        // The contract: never throw at class-load
        // time, never crash the launcher.
        val store = LlmTokenStore.create(ApplicationProvider.getApplicationContext())
        assertNotNull(store)
    }
}
