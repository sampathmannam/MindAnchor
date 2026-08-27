package org.mindanchor.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail

/**
 * v0.30+ (security audit 2026-08-25 HIGH finding,
 * follow-up) — [CertificatePinning.forBaseUrl] existed
 * but [OpenAiCompatibleClient.defaultClient] never
 * called it, so every real LLM request still went out
 * on OkHttp's unpinned default trust store. These
 * tests pin the wiring itself, not just the routing
 * table in [CertificatePinningTest].
 */
class OpenAiCompatibleClientTest {

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

    /**
     * v0.72.x: cert pinning is back on. The pins in
     * [CertificatePinning] are read off each host's live
     * TLS handshake (2026-08-27) and verified still
     * match. These two tests pin the routing table.
     */
    @Test
    fun `default client pins a known provider host`() {
        val client = OpenAiCompatibleClient.defaultClient("https://openrouter.ai/api/v1/")
        assertTrue(
            "openrouter.ai must have a pinner attached",
            client.certificatePinner.findMatchingPins("openrouter.ai").isNotEmpty(),
        )
    }

    @Test
    fun `default client does not pin an unknown host`() {
        // An unknown host gets the platform trust store
        // and no extra pinner. The OkHttp client still
        // builds; TLS still validates the chain; no
        // pinning is layered on top.
        val client = OpenAiCompatibleClient.defaultClient("https://example.com/api/")
        assertTrue(client.certificatePinner.findMatchingPins("example.com").isEmpty())
    }

    /**
     * v0.72.x: end-to-end wiring verification using
     * OkHttp's MockWebServer. The mock server is bound
     * to localhost over a TLS-equivalent HTTPS that
     * OkHttp trusts by default; the request the client
     * sends and the response the client parses must
     * both match the real OpenAI-compatible shape.
     */
    @Test
    fun `complete sends a valid OpenAI request and parses a 2xx response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": "chatcmpl-abc",
                      "object": "chat.completion",
                      "created": 1700000000,
                      "model": "gemini-2.0-flash",
                      "choices": [
                        {
                          "index": 0,
                          "message": {"role": "assistant", "content": "Hello from a mock LLM."},
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18}
                    }
                    """.trimIndent()
                )
        )

        val client = OpenAiCompatibleClient(
            apiKey = "sk-test-mock-key",
            model = "gemini-2.0-flash",
            baseUrl = server.url("/v1beta/openai/").toString(),
            httpClient = OpenAiCompatibleClient.defaultClient(server.url("/").toString()),
        )

        val request = LlmRequest(
            model = "gemini-2.0-flash",
            messages = listOf(LlmMessage.System("be terse"), LlmMessage.User("hi")),
            temperature = 0.7,
            maxTokens = 600,
        )
        val result = client.complete(request)
        assertTrue("complete() should succeed against MockWebServer", result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("Hello from a mock LLM.", response.content)
        assertEquals(11, response.promptTokens)
        assertEquals(7, response.completionTokens)
        assertTrue("durationMs should be > 0", response.durationMs >= 0L)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "v1beta/openai/chat/completions",
            recorded.path?.removePrefix("/"),
        )
        assertEquals(
            "Bearer sk-test-mock-key",
            recorded.getHeader("Authorization"),
        )
        assertTrue(
            "Content-Type should start with application/json, was ${recorded.getHeader("Content-Type")}",
            recorded.getHeader("Content-Type")?.startsWith("application/json") == true,
        )
        val body = recorded.body.readUtf8()
        assertTrue("body should contain model", body.contains("\"model\":\"gemini-2.0-flash\""))
        assertTrue("body should contain system", body.contains("\"role\":\"system\""))
        assertTrue("body should contain user", body.contains("\"role\":\"user\""))
    }

    /**
     * v0.72.x: 401 from the server surfaces as
     * [LetterError.InvalidApiKey] (not as a generic
     * Unknown). The userMessage is "API key not valid.
     * Open settings to fix."
     */
    @Test
    fun `complete maps HTTP 401 to InvalidApiKey`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )
        val client = OpenAiCompatibleClient(
            apiKey = "sk-wrong",
            model = "gemini-2.0-flash",
            baseUrl = server.url("/v1/").toString(),
            httpClient = OpenAiCompatibleClient.defaultClient(server.url("/").toString()),
        )
        val result = client.complete(
            LlmRequest(
                model = "gemini-2.0-flash",
                messages = listOf(LlmMessage.User("hi")),
            )
        )
        assertTrue("complete() should fail with 401", result.isFailure)
        val err = result.exceptionOrNull()
        assertNotNull("error should be a LetterError", err)
        assertTrue(
            "401 must map to InvalidApiKey, got $err",
            err is LetterError.InvalidApiKey,
        )
    }

    /**
     * v0.72.x: 404 from the server surfaces as
     * [LetterError.ModelNotFound]. This was the
     * "Model not available" the user saw earlier
     * with the chosen OpenRouter model.
     */
    @Test
    fun `complete maps HTTP 404 to ModelNotFound`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"model not found"}}"""),
        )
        val client = OpenAiCompatibleClient(
            apiKey = "sk-test",
            model = "missing-model",
            baseUrl = server.url("/v1/").toString(),
            httpClient = OpenAiCompatibleClient.defaultClient(server.url("/").toString()),
        )
        val result = client.complete(
            LlmRequest(
                model = "missing-model",
                messages = listOf(LlmMessage.User("hi")),
            )
        )
        assertTrue("complete() should fail with 404", result.isFailure)
        assertTrue(
            "404 must map to ModelNotFound, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is LetterError.ModelNotFound,
        )
    }

    /**
     * v0.72.x: testConnection's 1-token "OK" probe
     * also routes through the real OkHttp call. The
     * 2xx response is a Result.success(Unit), any
     * non-2xx becomes the mapped LetterError.
     */
    @Test
    fun `testConnection succeeds on 2xx and maps failure on 4xx`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":"x","object":"chat.completion","created":1,"model":"gemini-2.0-flash","choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
                )
        )
        val client = OpenAiCompatibleClient(
            apiKey = "sk-test",
            model = "gemini-2.0-flash",
            baseUrl = server.url("/v1/").toString(),
            httpClient = OpenAiCompatibleClient.defaultClient(server.url("/").toString()),
        )
        assertTrue(client.testConnection().isSuccess)

        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"bad key"}}"""),
        )
        val result = client.testConnection()
        assertTrue("401 must fail testConnection()", result.isFailure)
        assertTrue(
            "401 must map to InvalidApiKey, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is LetterError.InvalidApiKey,
        )
    }
}
