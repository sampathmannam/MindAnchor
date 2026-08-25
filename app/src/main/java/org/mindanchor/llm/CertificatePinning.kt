package org.mindanchor.llm

import okhttp3.CertificatePinner

/**
 * v0.30+ (security audit 2026-08-25 HIGH finding) —
 * the [OpenAiCompatibleClient] previously made HTTPS
 * calls without certificate pinning. A corporate
 * TLS-intercepting proxy (Burp / mitmproxy user CA),
 * a malicious Wi-Fi captive portal, or any
 * installed user CA could MITM the LLM HTTPS call;
 * the bearer token and the conversation would be
 * visible to the passive observer.
 *
 * The fix: pin each provider's issuer *and* the
 * issuer's root, via OkHttp's [CertificatePinner].
 * The pins are SHA-256 hashes of the public key
 * (SPKI), the format OkHttp documents in
 * `CertificatePinner.Builder.add(pattern, pins...)`.
 * [CertificatePinner] treats a pin set as a match if
 * *any* certificate in the verified chain matches
 * *any* configured pin, so pinning both the issuer
 * and the root is a fallback, not a stricter
 * requirement: a leaf-issuer rotation that still
 * chains to the same root keeps working.
 *
 * ## Where the pins came from
 *
 * The original commit shipped placeholder strings
 * (`PLACEHOLDER_GOOGLE_GTS`, `PLACEHOLDER_ISRG_ROOT_X1`)
 * — those are not valid base64 SPKI hashes, so every
 * real request would have failed closed with
 * `SSLPeerUnverifiedException`. This revision
 * replaces them with SPKI hashes read directly off
 * each host's live TLS handshake
 * (`openssl s_client -connect <host>:443 -showcerts`,
 * verified 2026-08-25):
 *
 * - `generativelanguage.googleapis.com` and
 *   `aistudio.google.com` both chain
 *   leaf → **WR2** (Google Trust Services) →
 *   **GTS Root R1** (Google Trust Services LLC).
 * - `openrouter.ai` and `api.groq.com` both chain
 *   leaf → **WE1** (Google Trust Services) →
 *   **GTS Root R4** (Google Trust Services LLC).
 *
 * The original audit note assumed openrouter.ai and
 * api.groq.com were issued by Let's Encrypt (ISRG
 * Root X1); the live handshake shows both are
 * actually served off Google Trust Services'
 * WE1/GTS-R4 chain. Pinning the assumed-wrong CA
 * would have hard-failed every OpenRouter/Groq call,
 * so the fix pins what the servers actually present,
 * not what the audit guessed.
 *
 * ## Why the pin set is conservative
 *
 * The pin set is the issuer intermediate (WR2 or
 * WE1) plus its root (GTS Root R1 or GTS Root R4).
 * Pinning the leaf would fail on the next cert
 * rotation; pinning the issuer survives leaf
 * rotation, and the root pin survives an intermediate
 * rotation within the same root. The leaf itself is
 * still validated by the platform trust store; these
 * pins are the MITM defence layered on top.
 *
 * ## Rotation policy
 *
 * - The pin set is reviewed quarterly, or whenever a
 *   provider's LLM calls start failing with
 *   `SSLPeerUnverifiedException` — re-run the same
 *   `openssl s_client` capture against the failing
 *   host and update the pins here.
 * - If a provider rotates to a new root the pins
 *   don't cover, the OkHttp call fails closed rather
 *   than silently trusting an unpinned chain — that
 *   is the intended behavior of certificate pinning,
 *   not a bug to route around with a broader pin set.
 * - The [LlmPrefs] contract reads the API key from the
 *   [LlmTokenStore]; the [CertificatePinner] is set
 *   at client construction in [OpenAiCompatibleClient].
 *
 * ## Why this is a [CertificatePinner] and not a
 *   custom TrustManager
 *
 * [CertificatePinner] is OkHttp's documented API
 * for this. A custom [TrustManager] would re-
 * implement the platform's chain validation; the
 * [CertificatePinner] runs *after* the platform's
 * chain validation, comparing the validated chain's
 * SPKI to the configured pin set. This is the
 * correct layering: do not reinvent the trust
 * store.
 */
internal object CertificatePinning {

    /**
     * The [CertificatePinner] for each LLM provider.
     * The [CertificatePinner] is keyed by the API base
     * URL's hostname (the OkHttp [CertificatePinner]
     * matches by hostname; wildcards are supported
     * but not used here because each provider is a
     * single host).
     *
     * See the class KDoc for where these pins came
     * from and the rotation policy.
     */
    fun forBaseUrl(baseUrl: String): CertificatePinner? {
        // v0.30+ — hostnames are case-insensitive in
        // practice; normalize before matching so the
        // routing is independent of how the URL is
        // cased.
        val host = baseUrl.lowercase()
        return when {
            host.contains("generativelanguage.googleapis.com") ||
                host.contains("aistudio.google.com") ->
                googleGtsR1Pinner()
            host.contains("openrouter.ai") ||
                host.contains("api.groq.com") ->
                googleGtsR4Pinner()
            else -> null
        }
    }

    // generativelanguage.googleapis.com / aistudio.google.com:
    // leaf -> WR2 -> GTS Root R1 (Google Trust Services LLC).
    // Pins WR2 (the issuer) and GTS Root R1 (its root) so an
    // intermediate rotation within the same root still verifies.
    private fun googleGtsR1Pinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add("aistudio.google.com", GOOGLE_WR2_PIN, GOOGLE_GTS_ROOT_R1_PIN)
            .add("generativelanguage.googleapis.com", GOOGLE_WR2_PIN, GOOGLE_GTS_ROOT_R1_PIN)
            .build()

    // openrouter.ai / api.groq.com: leaf -> WE1 -> GTS Root R4
    // (Google Trust Services LLC) — not Let's Encrypt, despite
    // the original audit note's assumption; see class KDoc.
    private fun googleGtsR4Pinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add("openrouter.ai", GOOGLE_WE1_PIN, GOOGLE_GTS_ROOT_R4_PIN)
            .add("api.groq.com", GOOGLE_WE1_PIN, GOOGLE_GTS_ROOT_R4_PIN)
            .build()

    // SPKI (public-key) SHA-256 pins, read from each host's
    // live TLS handshake on 2026-08-25 — see class KDoc.
    private const val GOOGLE_WR2_PIN = "sha256/YPtHaftLw6/0vnc2BnNKGF54xiCA28WFcccjkA4ypCM="
    private const val GOOGLE_GTS_ROOT_R1_PIN = "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="
    private const val GOOGLE_WE1_PIN = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
    private const val GOOGLE_GTS_ROOT_R4_PIN = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="
}
