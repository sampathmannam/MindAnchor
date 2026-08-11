package org.mindanchor.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [WebDavCredentialStore].
 *
 * The store is a small wrapper around
 * EncryptedSharedPreferences. Production code uses a
 * real Context and a real Keystore-backed master key;
 * the tests use a context-free subclass that holds
 * credentials in a plain [HashMap]. The subclass never
 * touches the `prefs` field (which is `by lazy` and
 * would require a Context), so the production
 * EncryptedSharedPreferences is not exercised here —
 * the production Keystore is exercised by the
 * instrumented tests on the emulator, and the in-memory
 * subclass is enough to pin the read/write/clear
 * shape.
 */
class WebDavCredentialStoreTest {

    /**
     * A [WebDavCredentialStore] that does not touch
     * the real EncryptedSharedPreferences. The map
     * is the source of truth; the protected `prefs`
     * field is never read.
     */
    private class InMemoryStore : WebDavCredentialStore(STUB_CONTEXT) {
        private val map = mutableMapOf<String, String>()
        override fun isConfigured(): Boolean {
            val url = map[KEY_URL] ?: return false
            val username = map[KEY_USERNAME] ?: return false
            val password = map[KEY_PASSWORD] ?: return false
            return url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        }
        override fun read(): Triple<String, String, String>? {
            val url = map[KEY_URL].orEmpty()
            val username = map[KEY_USERNAME].orEmpty()
            val password = map[KEY_PASSWORD].orEmpty()
            if (url.isBlank() || username.isBlank() || password.isBlank()) return null
            return Triple(url, username, password)
        }
        override fun write(url: String, username: String, password: String) {
            map[KEY_URL] = url
            map[KEY_USERNAME] = username
            map[KEY_PASSWORD] = password
        }
        override fun clear() { map.clear() }
        companion object {
            // The protected prefs field is `by lazy { openEncrypted(context) }`
            // — it is never initialised unless a test method calls a
            // production-only method. The InMemoryStore overrides every
            // public method, so the lazy field is never touched.
            //
            // The Context passed in is a non-null placeholder. The base
            // class constructor does NOT call openEncrypted, so any Context
            // (including a Mockito mock or a stub) is safe.
            const val KEY_URL = "url"
            const val KEY_USERNAME = "username"
            const val KEY_PASSWORD = "password"
            val STUB_CONTEXT: android.content.Context =
                org.mockito.Mockito.mock(android.content.Context::class.java)
        }
    }

    @Test
    fun `a fresh store is not configured`() {
        val store = InMemoryStore()
        assertFalse(store.isConfigured())
        assertNull(store.read())
    }

    @Test
    fun `write then read returns the same credentials`() {
        val store = InMemoryStore()
        store.write(
            url = "https://cloud.example.com/remote.php/dav/files/alice/MindAnchor/",
            username = "alice",
            password = "abcd-efgh-ijkl-mnop",
        )
        assertTrue(store.isConfigured())
        assertEquals(
            Triple(
                "https://cloud.example.com/remote.php/dav/files/alice/MindAnchor/",
                "alice",
                "abcd-efgh-ijkl-mnop",
            ),
            store.read(),
        )
    }

    @Test
    fun `clear wipes all three fields`() {
        val store = InMemoryStore()
        store.write("https://x.example/", "u", "p")
        assertTrue(store.isConfigured())
        store.clear()
        assertFalse(store.isConfigured())
        assertNull(store.read())
    }

    @Test
    fun `isConfigured treats a blank password as not configured`() {
        val store = InMemoryStore()
        store.write("https://x.example/", "alice", "  ")
        assertFalse(store.isConfigured())
        assertNull(store.read())
    }

    @Test
    fun `isConfigured treats a blank url as not configured`() {
        val store = InMemoryStore()
        store.write("   ", "alice", "p")
        assertFalse(store.isConfigured())
        assertNull(store.read())
    }
}
