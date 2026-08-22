package org.mindanchor.llm

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * GroqClient's behavior is the 9-way branch on HTTP status
 * + IOException subclasses. The test pins each branch
 * against a scripted MockWebServer response.
 *
 * The `baseUrl` constructor parameter on [GroqClient] is
 * the seam that lets the test point the client at the
 * MockWebServer's localhost address. Production callers
 * leave it default ([GroqClient.BASE_URL]).
 */
class GroqClientTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 happy path returns LlmResponse with content and metadata`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"It was a quiet Tuesday. The note about the meeting sat there. The morning asked nothing of you, and that was allowed.\n\nWhat was the loudest thing in the room just now?"}}],"usage":{"prompt_tokens":1240,"completion_tokens":380}}""",
                ),
        )
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "gsk_test",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.complete(
            LlmRequest(
                model = GroqModels.DEFAULT,
                messages = listOf(
                    LlmMessage.System("system"),
                    LlmMessage.User("user"),
                ),
            ),
        )
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertTrue(response.content.startsWith("It was a quiet Tuesday."))
        assertEquals(1240, response.promptTokens)
        assertEquals(380, response.completionTokens)
        assertTrue(response.durationMs >= 0)
    }

    @Test
    fun `401 maps to InvalidApiKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_api_key"}"""))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "bad",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.complete(testRequest())
        assertTrue(result.isFailure)
        assertEquals(LetterError.InvalidApiKey()::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `403 maps to AccountUnauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "x",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.complete(testRequest())
        assertEquals(LetterError.AccountUnauthorized()::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `404 maps to ModelNotFound`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"model_not_found"}"""))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "x",
            model = "no-such-model",
            httpClient = httpClient,
        )
        val result = client.complete(testRequest())
        assertEquals(LetterError.ModelNotFound()::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `429 maps to RateLimited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited"}"""))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "x",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.complete(testRequest())
        assertEquals(LetterError.RateLimited()::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `500 maps to ServerError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "x",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.complete(testRequest())
        assertEquals(LetterError.ServerError()::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `malformed JSON maps to Unknown`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "x",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.complete(testRequest())
        assertEquals(LetterError.Unknown()::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `testConnection returns success on 2xx`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"OK"}}],"usage":{"prompt_tokens":10,"completion_tokens":1}}"""),
        )
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "x",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.testConnection()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection returns failure on 401`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(""))
        val client = GroqClient(
            baseUrl = server.url("/").toString(),
            apiKey = "bad",
            model = GroqModels.DEFAULT,
            httpClient = httpClient,
        )
        val result = client.testConnection()
        assertEquals(LetterError.InvalidApiKey()::class, result.exceptionOrNull()!!::class)
    }

    private fun testRequest() = LlmRequest(
        model = GroqModels.DEFAULT,
        messages = listOf(
            LlmMessage.System("system"),
            LlmMessage.User("user"),
        ),
    )
}
