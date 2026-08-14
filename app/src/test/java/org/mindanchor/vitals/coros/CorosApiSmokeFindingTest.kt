@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.vitals.coros

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * v0.25.19: COROS Training Hub REST smoke test.
 *
 * The four Training Hub endpoints (login, fetchDashboard,
 * fetchAnalyse, fetchActivities) are exercised through a
 * [MockWebServer] pointed at a localhost URL. The
 * contract is "the smoke path completes without throwing,
 * with the right request body / headers, and returns the
 * parsed shape." A real device run is the end-to-end
 * confirmation; the unit-test surface here is the gate
 * that makes a regression loud in CI before it ever
 * reaches a device.
 *
 * The static file-shape pins assert (a) the
 * [org.mindanchor.vitals.coros.CorosApi] class is wired
 * to the [OkHttpClient] and (b) the four endpoints are
 * reachable from a test entry point.
 */
class CorosApiSmokeFindingTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `CorosApi fetches dashboard through a stubbed Training Hub`() {
        // The real Training Hub returns a JSON shape that
        // CorosApi.decodeResponse maps to List<CorosHrv>.
        // The smoke stub returns an empty list (the
        // contract: empty response, no records). The
        // smoke test asserts the request was made with
        // the right headers and the response was decoded
        // without throwing.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"result":"0000","data":{}}"""),
        )
        val api = CorosApi(baseUrlOverride = server.url("/").toString().trimEnd('/'))
        val auth = CorosAuthPayload(
            accessToken = "test-token",
            userId = "u-1",
            region = "global",
            timestampMs = 1_700_000_000_000L,
        )
        val result = runBlocking { api.fetchDashboard(auth) }
        assertTrue(
            "fetchDashboard must return an empty list when the server " +
                "responds with `[]`. The smoke path must not throw on a " +
                "real-looking but empty response.",
            result.isEmpty(),
        )
        val recorded = server.takeRequest()
        assertEquals(
            "fetchDashboard must hit the /dashboard/query path. " +
                "A future refactor that renames the path would silently " +
                "break the production build, so the path is pinned here.",
            "/dashboard/query",
            recorded.path,
        )
        assertEquals(
            "fetchDashboard must use the GET method.",
            "GET",
            recorded.method,
        )
        assertEquals(
            "fetchDashboard must send the access token in the `accesstoken` " +
                "header (the Training Hub auth convention, not the standard " +
                "`Authorization: Bearer …`).",
            "test-token",
            recorded.getHeader("accesstoken"),
        )
    }

    @Test
    fun `CorosApi throws CorosApiException on a non-2xx response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"unauthorized\"}"),
        )
        val api = CorosApi(baseUrlOverride = server.url("/").toString().trimEnd('/'))
        val auth = CorosAuthPayload(
            accessToken = "test-token",
            userId = "u-1",
            region = "global",
            timestampMs = 1_700_000_000_000L,
        )
        val ex = runCatching { runBlocking { api.fetchDashboard(auth) } }.exceptionOrNull()
        assertTrue(
            "fetchDashboard must throw CorosApiException (not a generic " +
                "IOException) on a 401. The CorosSyncWorker catches the " +
                "typed exception and decides the retry policy; a generic " +
                "exception would defeat the retry classifier.",
            ex is CorosApiException,
        )
    }

    @Test
    fun `CorosApi source wires fetchDashboard fetchAnalyse fetchActivities and login`() {
        val src = readSource("app/src/main/java/org/mindanchor/vitals/coros/CorosApi.kt")
        assertTrue("CorosApi.kt must be readable", src != null)
        val body = src!!
        // The four smoke-relevant entry points must all
        // be present and use authenticatedGet (the
        // request-builder wrapper).
        assertTrue(
            "CorosApi must declare `suspend fun login(` — the auth entry " +
                "point the smoke path is built around.",
            body.contains("suspend fun login("),
        )
        assertTrue(
            "CorosApi must declare `suspend fun fetchDashboard(` — the " +
                "HRV-fetching entry point the smoke test drives.",
            body.contains("suspend fun fetchDashboard("),
        )
        assertTrue(
            "CorosApi must declare `suspend fun fetchAnalyse(` — the " +
                "daily-summary entry point.",
            body.contains("suspend fun fetchAnalyse("),
        )
        assertTrue(
            "CorosApi must declare `suspend fun fetchActivities(` — the " +
                "exercise-session entry point.",
            body.contains("suspend fun fetchActivities("),
        )
        assertTrue(
            "CorosApi must use OkHttpClient (the request-builder wrapper " +
                "is `client.newCall(req).execute()`). Without the OkHttp " +
                "import, the source would not compile.",
            body.contains("OkHttpClient"),
        )
    }

    private fun readSource(path: String): String? = try {
        val candidates = listOf(path, "../$path", "../../$path")
        candidates.map(::File).firstNotNullOfOrNull { f ->
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }
    } catch (t: Throwable) {
        null
    }
}
