package org.mindanchor.vitals.coros

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MockWebServer-driven tests for the four COROS Training
 * Hub endpoints the side-channel uses.
 *
 * The server is bound to a localhost ephemeral port; the
 * [CorosApi] under test is rebuilt with a base URL
 * pointing at it. Every test enqueues a single scripted
 * response and asserts the request that the client built
 * (path, body, `accesstoken` header for the authenticated
 * calls) and the parsed result.
 *
 * v0.20.7 (CodeRabbit audit on the bridge): these tests
 * also pin the wire-format details that future refactors
 * are most likely to break silently — the lowercase
 * `accesstoken` header (the COROS server rejects mixed
 * case), the `accountType: 2` field, and the
 * `application/json; charset=utf-8` content type.
 */
class CorosApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CorosApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = CorosApi(
            client = OkHttpClient(),
            baseUrlOverride = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private val testAuth = CorosAuthPayload(
        accessToken = "test-access-token-123",
        userId = "test-user-id",
        region = "us",
        timestampMs = 1_700_000_000_000L,
    )

    @Test
    fun `login sends the documented wire format and parses the auth payload`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "0000",
                  "data": {
                    "accessToken": "abc123",
                    "userId": "user-42"
                  }
                }
                """.trimIndent(),
            ).setResponseCode(200),
        )
        val payload = api.login(
            email = "u@example.com",
            passwordHashHex = "5f4dcc3b5aa765d61d8327deb882cf99",
            region = "us",
        )
        assertEquals("abc123", payload.accessToken)
        assertEquals("user-42", payload.userId)
        assertEquals("us", payload.region)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/account/login", request.path)
        val body = request.body.readUtf8()
        assertTrue("body must contain accountType=2: $body", body.contains("\"accountType\":2"))
        assertTrue("body must contain pwd field: $body", body.contains("\"pwd\":\"5f4dcc3b5aa765d61d8327deb882cf99\""))
        assertTrue("body must contain account field: $body", body.contains("\"account\":\"u@example.com\""))
        val ct = request.getHeader("Content-Type").orEmpty()
        assertTrue(
            "Content-Type must declare JSON+UTF-8: $ct",
            ct.startsWith("application/json") && ct.contains("utf-8"),
        )
    }

    @Test
    fun `login failure result code throws CorosApiException with the result string`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"result":"1001","message":"invalid credentials"}""",
            ).setResponseCode(200),
        )
        val ex = assertThrows(CorosApiException::class.java) {
            runBlocking {
                api.login(
                    email = "bad@example.com",
                    passwordHashHex = "0".repeat(32),
                    region = "eu",
                )
            }
        }
        assertEquals("1001", ex.corosResult)
    }

    @Test
    fun `dashboard fetch sends the lowercase accesstoken header`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "0000",
                  "data": {
                    "summaryInfo": {
                      "sleepHrvData": {
                        "happenDay": "2025-08-09",
                        "avgSleepHrv": 42.5,
                        "sleepHrvBase": 40.0,
                        "sleepHrvSd": 5.0,
                        "sleepHrvList": [
                          {"happenDay": "2025-08-08", "avgSleepHrv": 41.0, "sleepHrvBase": 40.0, "sleepHrvSd": 5.0}
                        ]
                      }
                    }
                  }
                }
                """.trimIndent(),
            ).setResponseCode(200),
        )
        val hrv = api.fetchDashboard(testAuth)
        assertEquals(2, hrv.size)
        assertEquals("2025-08-08", hrv[0].date)
        assertEquals(41.0, hrv[0].rmssd!!, 0.001)
        assertEquals("2025-08-09", hrv[1].date)
        assertEquals(42.5, hrv[1].rmssd!!, 0.001)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/dashboard/query", request.path)
        // The header must be lowercase, exactly one word, no
        // underscore. The COROS server's reverse-proxy
        // normalises the case the way nginx does, and a
        // mismatched header returns 401.
        val tokenHeader = request.getHeader("accesstoken")
        assertNotNull("expected accesstoken header, got: $tokenHeader", tokenHeader)
        assertNull(
            "Authorization header must NOT be set",
            request.getHeader("Authorization"),
        )
        assertEquals("test-access-token-123", tokenHeader)
    }

    @Test
    fun `analyse fetch parses the t7dayList summary`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "0000",
                  "data": {
                    "t7dayList": [
                      {"happenDay": "2025-08-08", "rhr": 55, "trainingLoad": 42.0, "vo2max": 48.0},
                      {"happenDay": "2025-08-09", "rhr": 56, "trainingLoad": null, "vo2max": 48.0}
                    ]
                  }
                }
                """.trimIndent(),
            ).setResponseCode(200),
        )
        val daily = api.fetchAnalyse(testAuth)
        assertEquals(2, daily.size)
        assertEquals("2025-08-08", daily[0].date)
        assertEquals(55.0, daily[0].rhr!!, 0.001)
        assertEquals(42.0, daily[0].trainingLoad!!, 0.001)
        assertEquals(48.0, daily[0].vo2Max!!, 0.001)
        // Null training load on day 2 — verify the parser
        // does not blow up on a missing field.
        assertEquals(null, daily[1].trainingLoad)
    }

    @Test
    fun `activities fetch sends the documented query parameters`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "0000",
                  "data": {
                    "totalCount": 1,
                    "dataList": [
                      {
                        "labelId": "act-1",
                        "name": "Morning run",
                        "sportType": 100,
                        "startTime": 1723200000000,
                        "endTime": 1723203600000,
                        "totalTime": 3600,
                        "distance": 5000,
                        "avgHr": 145,
                        "maxHr": 168,
                        "calorie": 350000,
                        "trainingLoad": 50
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ).setResponseCode(200),
        )
        val activities = api.fetchActivities(
            auth = testAuth,
            startDay = "2025-08-01",
            endDay = "2025-08-09",
        )
        assertEquals(1, activities.size)
        assertEquals("act-1", activities[0].activityId)
        assertEquals("Morning run", activities[0].name)
        assertEquals(100, activities[0].sportType)
        assertEquals("Running", activities[0].sportName)
        assertEquals(5000L, activities[0].distanceMeters)
        assertEquals(3600L, activities[0].durationSeconds)
        assertEquals(145, activities[0].avgHr)
        // 350,000 raw calories / 1000 = 350 kcal
        assertEquals(350_000L, activities[0].caloriesRaw)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("path includes activity/query: ${request.path}", request.path!!.startsWith("/activity/query"))
        // OkHttp URL-encodes the spaces and slashes; assert the
        // parameters are all present rather than the exact string.
        val url = request.path.orEmpty()
        assertTrue("startDay present: $url", url.contains("startDay=2025-08-01"))
        assertTrue("endDay present: $url", url.contains("endDay=2025-08-09"))
        assertTrue("pageNumber present: $url", url.contains("pageNumber=1"))
        assertTrue("size present: $url", url.contains("size=30"))
    }

    @Test
    fun `HTTP transport failure throws CorosApiException with the http code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val ex = assertThrows(CorosApiException::class.java) {
            runBlocking { api.fetchDashboard(testAuth) }
        }
        assertEquals(503, ex.httpCode)
    }

    @Test
    fun `baseUrl maps the three supported regions`() {
        // Use a fresh API without the test override so
        // the production region table is what the
        // assertions exercise.
        val productionApi = CorosApi(client = OkHttpClient())
        assertEquals("https://teamapi.coros.com", productionApi.baseUrl("us"))
        assertEquals("https://teameuapi.coros.com", productionApi.baseUrl("eu"))
        assertEquals("https://teamcnapi.coros.com", productionApi.baseUrl("asia"))
        assertEquals("https://teamcnapi.coros.com", productionApi.baseUrl("cn"))
        // Unknown region defaults to the EU base — the
        // user setting is free-form and a typo should not
        // crash the bridge; it should land on the most
        // common default and the login failure will be
        // the honest signal that the region is wrong.
        assertEquals("https://teameuapi.coros.com", productionApi.baseUrl("xx"))
    }

    @Test
    fun `CorosSportNames resolves the activity-side sport IDs`() {
        assertEquals("Running", CorosSportNames.name(100))
        assertEquals("Trail Running", CorosSportNames.name(102))
        assertEquals("Road Bike", CorosSportNames.name(200))
        assertEquals("Cardio", CorosSportNames.name(400))
        assertEquals("Walking", CorosSportNames.name(900))
        assertEquals("Bike Commute", CorosSportNames.name(9807))
        // Unknown sport id: the helper returns a
        // "Sport <id>" placeholder rather than null, so
        // the UI never has to special-case it.
        assertEquals("Sport 1234", CorosSportNames.name(1234))
        assertNull(CorosSportNames.name(null))
    }
}
