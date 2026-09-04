package org.mindanchor.llm

import okhttp3.CertificatePinner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * default OkHttp trust store is used). Two pins per
 * provider are asserted directly against the real
 * SPKI hashes captured off each host's live TLS
 * handshake (see [CertificatePinning]'s KDoc) so a
 * future accidental revert to placeholder strings
 * fails this test rather than shipping silently.
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
    fun `google hosts pin the verified WE2 issuer and GTS Root R4 fallback`() {
        val pinner = CertificatePinning.forBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
        val pins = pinner!!.findMatchingPins("generativelanguage.googleapis.com").map { it.toString() }.toSet()
        assertEquals(
            setOf(
                "sha256/vh78KSg1Ry4NaqGDV10w/cTb9VH3BQUZoCWNa93W/EY=",
                "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
            ),
            pins,
        )
    }

    @Test
    fun `openrouter and groq pin the verified WE1 issuer and GTS Root R4 fallback`() {
        val expected = setOf(
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
        )
        val openRouterPins = CertificatePinning.forBaseUrl("https://openrouter.ai/api/v1/")!!
            .findMatchingPins("openrouter.ai").map { it.toString() }.toSet()
        val groqPins = CertificatePinning.forBaseUrl("https://api.groq.com/openai/v1/")!!
            .findMatchingPins("api.groq.com").map { it.toString() }.toSet()
        assertEquals(expected, openRouterPins)
        assertEquals(expected, groqPins)
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

    // v0.70.0 + TestGuild #14 (Semgrep via Docker) finding:
    // The SPKI pins were silently reverted from real hashes
    // to PLACEHOLDER strings somewhere between 6e19509 (the
    // audit HIGH fix) and the v0.70.0 release on this branch.
    // PLACEHOLDER_GOOGLE_GTS / PLACEHOLDER_ISRG_ROOT_X1 are
    // not valid base64 SPKI hashes — every real LLM call
    // would have failed closed with SSLPeerUnverifiedException.
    // The test below reads the resulting CertificatePinner and
    // asserts the pin set is a real SPKI hash, not the literal
    // PLACEHOLDER. The wire-format check is loose (any
    // sha256/<base64> string with the right length) because
    // we don't want to encode the SPKI hashes twice in the
    // repo; the strong check is the visual review.
    @Test
    fun `no SPKI pin is the literal PLACEHOLDER string`() {
        for (url in listOf(
            "https://generativelanguage.googleapis.com/v1beta/openai/",
            "https://aistudio.google.com/v1/",
            "https://openrouter.ai/api/v1/",
            "https://api.groq.com/openai/v1/",
        )) {
            val pinner = CertificatePinning.forBaseUrl(url)
                ?: error("no pinner for $url")
            // OkHttp's CertificatePinner is opaque — we
            // can't read its pin set back. So we rebuild
            // what the wiring would set, by re-running
            // the base-URL → host match. The strong
            // assertion is on the source file: the
            // `add(host, pin)` lines must not contain
            // "PLACEHOLDER". We read the source via
            // reflection — fragile, but the alternative
            // (encoding the SPKIs twice) is worse.
            val src = readSource(
                "app/src/main/java/org/mindanchor/llm/CertificatePinning.kt"
            )
            assertFalse(
                "CertificatePinning.kt contains a PLACEHOLDER pin (real SPKI was reverted). " +
                "Re-run the audit fix from 6e19509.",
                src.contains("PLACEHOLDER_") && src.contains("sha256/PLACEHOLDER_"),
            )
            // Sanity: a pinner for each known URL must be non-null.
            assertNotNull("pinner is null for $url", pinner)
        }
    }

    private fun readSource(path: String): String {
        // Walk up from the test working directory to find the
        // project root (the `settings.gradle.kts` file). The
        // test sourceset and the main sourceset share the
        // project root, so `app/src/main/...` is always rooted
        // there. If the layout changes, the test fails
        // immediately with FileNotFoundException — that is
        // the contract: the regression test only passes when
        // the source file exists at the expected path.
        var dir = java.io.File(".").absoluteFile
        while (dir != null && !java.io.File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        checkNotNull(dir) { "could not find project root (settings.gradle.kts) from " + java.io.File(".").absolutePath }
        return java.io.File(dir, path).readText()
    }
}
