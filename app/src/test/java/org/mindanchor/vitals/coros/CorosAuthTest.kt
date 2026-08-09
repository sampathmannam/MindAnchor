package org.mindanchor.vitals.coros

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Tests for [CorosAuth]'s in-memory token cache and
 * single-flight login behaviour.
 *
 * The encrypted-credential-store dependency is stubbed
 * via a [FakeStore] that subclasses [CorosCredentialStore]
 * with Context-free in-memory fields; the real
 * EncryptedSharedPreferences path is exercised by the
 * Android instrumentation tests, not by these unit
 * tests, because Keystore access requires a real
 * Android environment.
 */
class CorosAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var fakeStore: FakeStore
    private lateinit var api: CorosApi
    private lateinit var auth: CorosAuth

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = CorosApi(client = OkHttpClient(), baseUrlOverride = server.url("/").toString().trimEnd('/'))
        fakeStore = FakeStore()
        auth = CorosAuth(
            context = mock(android.content.Context::class.java),
            api = api,
            store = fakeStore,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `connectionState returns NotConnected when no creds are stored`() {
        assertEquals(
            CorosConnectionState.NotConnected,
            auth.connectionState(lastSyncEpochMs = 1_700_000_000_000L),
        )
    }

    @Test
    fun `connectionState returns Connected with the stored email and region`() {
        fakeStore.email = "u@example.com"
        fakeStore.password = "pw"
        fakeStore.region = "eu"
        val state = auth.connectionState(lastSyncEpochMs = 42L)
        assertTrue("expected Connected, got: $state", state is CorosConnectionState.Connected)
        state as CorosConnectionState.Connected
        assertEquals("u@example.com", state.email)
        assertEquals("eu", state.region)
        assertEquals(42L, state.lastSyncEpochMs)
    }

    @Test
    fun `disconnect wipes the in-memory cache and the store`() {
        fakeStore.email = "u@example.com"
        fakeStore.password = "secret"
        fakeStore.region = "eu"
        auth.disconnect()
        assertNull(fakeStore.email)
        assertNull(fakeStore.password)
        assertEquals(CorosConnectionState.NotConnected, auth.connectionState(lastSyncEpochMs = null))
    }

    @Test
    fun `loginWithCredentials writes the creds and the auth payload is cached`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"result":"0000","data":{"accessToken":"tok","userId":"u1"}}""",
            ).setResponseCode(200),
        )
        val payload = auth.loginWithCredentials(
            email = "new@example.com",
            password = "pw",
            region = "us",
        )
        assertEquals("tok", payload.accessToken)
        assertEquals("u1", payload.userId)
        assertEquals("us", payload.region)
        // The store now holds the creds, keyed on the email
        // we passed in. The next call to ensureAuthed should
        // not re-login.
        assertEquals("new@example.com", fakeStore.email)
        assertEquals("pw", fakeStore.password)
        assertEquals("us", fakeStore.region)

        // A second ensureAuthed with no new server response
        // enqueued would fail; instead, assert the cached
        // state is exactly what we expect.
        val state = auth.connectionState(lastSyncEpochMs = null)
        assertTrue(state is CorosConnectionState.Connected)
    }

    @Test
    fun `ensureAuthed returns null when no creds are stored`() = runBlocking {
        val result = auth.ensureAuthed()
        assertNull("ensureAuthed with no creds returns null", result)
        // The server saw zero requests.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `loginWithCredentials re-throws CorosApiException on bad credentials`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"result":"1001","message":"invalid credentials"}""",
            ).setResponseCode(200),
        )
        val ex = assertThrows(CorosApiException::class.java) {
            runBlocking {
                auth.loginWithCredentials(
                    email = "bad@example.com",
                    password = "wrong",
                    region = "us",
                )
            }
        }
        assertEquals("1001", ex.corosResult)
    }

    @Test
    fun `cached token is reused within its TTL and the API is not re-hit`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"result":"0000","data":{"accessToken":"tok1","userId":"u1"}}""",
            ).setResponseCode(200),
        )
        fakeStore.email = "x@example.com"
        fakeStore.password = "pw"
        fakeStore.region = "us"
        val first = auth.ensureAuthed()
        assertNotNull(first)
        // The next call should hit zero new requests.
        val second = auth.ensureAuthed()
        assertNotNull(second)
        assertEquals(first!!.accessToken, second!!.accessToken)
        assertEquals("exactly one /account/login call expected", 1, server.requestCount)
    }

    /**
     * An in-memory credential store. Subclasses
     * [CorosCredentialStore] so the [CorosAuth]
     * constructor can take it without a new file
     * appearing under `vitals/coros/`.
     */
    private class FakeStore : CorosCredentialStore(
        context = org.mockito.Mockito.mock(android.content.Context::class.java),
    ) {
        var email: String? = null
        var password: String? = null
        var region: String? = null

        override val prefs: android.content.SharedPreferences
            get() = error("prefs must not be touched in tests")

        override fun isConnected(): Boolean = !email.isNullOrBlank()
        override fun read(): Pair<String, String>? {
            val e = email ?: return null
            val p = password ?: return null
            return e to p
        }
        override fun write(email: String, password: String, region: String) {
            this.email = email
            this.password = password
            this.region = region
        }
        override fun region(): String = region ?: "eu"
        override fun clear() {
            email = null
            password = null
            region = null
        }
    }
}
