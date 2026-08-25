package org.mindanchor.llm

import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `default client pins a known provider host`() {
        val client = OpenAiCompatibleClient.defaultClient("https://openrouter.ai/api/v1/")
        assertTrue(client.certificatePinner.findMatchingPins("openrouter.ai").isNotEmpty())
    }

    @Test
    fun `default client does not pin an unknown host`() {
        val client = OpenAiCompatibleClient.defaultClient("https://example.com/api/")
        assertTrue(client.certificatePinner.findMatchingPins("example.com").isEmpty())
    }
}
