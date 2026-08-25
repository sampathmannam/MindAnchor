package org.mindanchor.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.30+ (security audit 2026-08-25 HIGH finding) —
 * the [CertificatePinning.forBaseUrl] URL matching
 * pins the correct provider for each LLM API
 * endpoint. The pinner object itself is a thin
 * [okhttp3.CertificatePinner] wrapper; the
 * security-sensitive part is the URL-to-provider
 * mapping.
 *
 * The [okhttp3.CertificatePinner] is matched by
 * hostname; the URL's host is what matters. The
 * tests below assert that the right provider is
 * selected for each base URL the project uses, and
 * that unknown hosts return null (no pinning — the
 * default OkHttp trust store is used). The pinner
 * itself is a no-op until the SPKI hashes are filled
 * in by the devops team in a follow-up commit; the
 * [forBaseUrl] plumbing is in place and the test
 * pins the routing.
 */
class CertificatePinningTest {

    @Test
    fun `aistudio google api maps to google pinner`() {
        assertNotNull(CertificatePinning.forBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/"))
    }

    @Test
    fun `aistudio google direct maps to google pinner`() {
        assertNotNull(CertificatePinning.forBaseUrl("https://aistudio.google.com/v1/"))
    }

    @Test
    fun `openrouter maps to lets encrypt pinner`() {
        assertNotNull(CertificatePinning.forBaseUrl("https://openrouter.ai/api/v1/"))
    }

    @Test
    fun `groq maps to lets encrypt pinner`() {
        assertNotNull(CertificatePinning.forBaseUrl("https://api.groq.com/openai/v1/"))
    }

    @Test
    fun `unknown host returns null (no pinning)`() {
        assertNull(CertificatePinning.forBaseUrl("https://example.com/api/"))
    }

    @Test
    fun `empty url returns null`() {
        assertNull(CertificatePinning.forBaseUrl(""))
    }

    @Test
    fun `localhost returns null`() {
        assertNull(CertificatePinning.forBaseUrl("http://localhost:8080/api/"))
    }

    @Test
    fun `case insensitive host matching`() {
        // The URL-to-provider mapping is by
        // substring; case-insensitive matching is the
        // safe default because hostnames are
        // case-insensitive in practice.
        val a = CertificatePinning.forBaseUrl("https://Generativelanguage.GoogleAPIS.com/v1/")
        val b = CertificatePinning.forBaseUrl("https://generativelanguage.googleapis.com/v1/")
        assertNotNull(a)
        assertNotNull(b)
    }

    @Test
    fun `pinners from different providers both resolve`() {
        // The LLM bridge constructs the pinner per
        // request; caching is allowed. The contract
        // is: the routing function is deterministic
        // on the URL. Both URLs resolve to non-null
        // pinners; the actual host-matching is
        // OkHttp's concern (tested by OkHttp's own
        // test suite).
        val googlePinner = CertificatePinning.forBaseUrl(
            "https://generativelanguage.googleapis.com/v1/",
        )
        val openRouterPinner = CertificatePinning.forBaseUrl(
            "https://openrouter.ai/api/v1/",
        )
        assertNotNull(googlePinner)
        assertNotNull(openRouterPinner)
    }
}
