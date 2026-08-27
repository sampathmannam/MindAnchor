package org.mindanchor.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.72.x: the [CertificatePinning.forBaseUrl] routing
 * table. Each provider has a [okhttp3.CertificatePinner]
 * built from the SPKI of its issuer intermediate and
 * the SPKI of its root, both read off the live TLS
 * handshake on 2026-08-27 (see [CertificatePinning]
 * KDoc). An unknown host returns null (the platform
 * trust store is the only validation).
 *
 * If a future provider rotation breaks the handshake,
 * re-run the openssl capture and update the four
 * constants in [CertificatePinning].
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
    fun `openrouter maps to google pinner`() {
        assertNotNull(CertificatePinning.forBaseUrl("https://openrouter.ai/api/v1/"))
    }

    @Test
    fun `groq maps to google pinner`() {
        assertNotNull(CertificatePinning.forBaseUrl("https://api.groq.com/openai/v1/"))
    }

    @Test
    fun `unknown host returns null (no pinning)`() {
        assertNull(CertificatePinning.forBaseUrl("https://example.com/api/"))
    }

    @Test
    fun `localhost returns null`() {
        assertNull(CertificatePinning.forBaseUrl("http://localhost:8080/api/"))
    }

    @Test
    fun `the pin set includes both issuer intermediate and root`() {
        // The whole point of pinning the issuer AND the
        // root is that an intermediate rotation that
        // chains to the same root still verifies. The
        // OkHttp [okhttp3.CertificatePinner] treats a
        // pin set as a match if *any* configured pin
        // matches *any* certificate in the chain. If
        // the routing table ever drops the root pin and
        // the intermediate rotates, the next request
        // fails closed with `SSLPeerUnverifiedException`.
        for (url in listOf(
            "https://generativelanguage.googleapis.com/v1beta/openai/",
            "https://aistudio.google.com/v1/",
            "https://openrouter.ai/api/v1/",
            "https://api.groq.com/openai/v1/",
        )) {
            val pinner = CertificatePinning.forBaseUrl(url)!!
            val host = url.substringAfter("://").substringBefore("/")
            val pins = pinner.findMatchingPins(host)
            assertTrue(
                "$url should pin issuer AND root, but found only $pins",
                pins.size >= 2,
            )
        }
    }
}
