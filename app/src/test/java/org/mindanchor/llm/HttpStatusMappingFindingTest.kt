package org.mindanchor.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * v0.72.x: locks in the HTTP-status-to-LetterError
 * mapping that the LLM call path uses. The mapping is
 * the user-visible contract: "401" means "your key is
 * wrong" and "429" means "wait a minute" — and these
 * mappings should not silently change. The test reads
 * [OpenAiCompatibleClient]'s source and asserts the
 * mapping table has not drifted.
 */
class HttpStatusMappingFindingTest {

    private val clientSource = java.io.File(
        "../app/src/main/java/org/mindanchor/llm/OpenAiCompatibleClient.kt"
    )

    @Test
    fun `the status-code mapping is the v0_72_x contract`() {
        val src = clientSource.readText()
        // 401 → InvalidApiKey, 403 → AccountUnauthorized,
        // 404 → ModelNotFound, 429 → RateLimited, 5xx →
        // ServerError, else → Unknown(body).
        // Lock the wording so a future "rename" doesn't
        // silently break user-facing error messages.
        assertTrue(
            "401 must map to InvalidApiKey",
            src.contains("401 -> LetterError.InvalidApiKey()"),
        )
        assertTrue(
            "403 must map to AccountUnauthorized",
            src.contains("403 -> LetterError.AccountUnauthorized()"),
        )
        assertTrue(
            "404 must map to ModelNotFound",
            src.contains("404 -> LetterError.ModelNotFound()"),
        )
        assertTrue(
            "429 must map to RateLimited",
            src.contains("429 -> LetterError.RateLimited()"),
        )
        assertTrue(
            "5xx must map to ServerError",
            src.contains("in 500..599 -> LetterError.ServerError()"),
        )
        assertTrue(
            "else must map to Unknown(body) so the actual server response is shown",
            src.contains("else -> LetterError.Unknown(body)"),
        )
    }

    @Test
    fun `every mapped IO failure has a corresponding LetterError class`() {
        // The transport-layer mapping (Timeout,
        // TlsFailed, NetworkUnreachable) is a runtime
        // contract; it must not regress to a generic
        // Unknown without a deliberate change.
        val src = clientSource.readText()
        assertTrue(
            "SocketTimeoutException must map to LetterError.Timeout",
            src.contains("is SocketTimeoutException -> LetterError.Timeout()"),
        )
        assertTrue(
            "SSLException must map to LetterError.TlsFailed",
            src.contains("is SSLException -> LetterError.TlsFailed()"),
        )
        assertTrue(
            "ConnectException must map to LetterError.NetworkUnreachable",
            src.contains("is ConnectException -> LetterError.NetworkUnreachable()"),
        )
        assertTrue(
            "IOException must map to LetterError.NetworkUnreachable",
            src.contains("is IOException -> LetterError.NetworkUnreachable()"),
        )
    }

    @Test
    fun `LetterError carries the response body for 4xx errors`() {
        // The whole point of v0.72.x's "Unknown(body)"
        // change was to surface the server's actual reason
        // when the status is unmapped. The body is set
        // in the v0.72.x path; the test pins that.
        val letterErrorSource = java.io.File(
            "../app/src/main/java/org/mindanchor/llm/LetterError.kt"
        )
        val src = letterErrorSource.readText()
        assertTrue(
            "LetterError.Unknown must take a body parameter so the server's reason is shown",
            src.contains("class Unknown(val body: String = \"\")"),
        )
    }
}
