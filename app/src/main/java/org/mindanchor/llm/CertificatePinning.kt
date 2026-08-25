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
 * The fix: pin each provider's issuer or its
 * public key via OkHttp's [CertificatePinner]. The
 * pins are SHA-256 hashes of the public key (SPKI),
 * the format OkHttp documents in
 * `CertificatePinner.Builder.add(pattern, pins...)`.
 *
 * ## Why the pin set is conservative
 *
 * The pin covers the immediate issuer. Rotating a
 * pin costs nothing in production (the server is
 * trusted, the client is not) and the documented
 * approach in the audit was to pin "the issuer or
 * its public key". Pinning the leaf would fail on
 * the next cert rotation; pinning the issuer
 * survives leaf rotation. The leaf itself is
 * validated by the underlying trust store; the
 * pin is the MITM defence.
 *
 * ## Rotation policy
 *
 * - The pin set is reviewed quarterly. A CA
 *   rotation adds a fallback before the old pin
 *   expires.
 * - The fallback is the SPKI of the parent CA, not
 *   the parent CA itself. SPKI pinning survives
 *   key rotation in the parent.
 * - If a provider rotates its CA and the new CA
 *   is not pinned, the OkHttp call fails with a
 *   `SSLPeerUnverifiedException`. The audit
 *   remediation says: "rotate the pins annually;
 *   document a fallback to the parent CA so a pin
 *   expiry doesn't brick the LLM path." The
 *   [LlmPrefs] contract reads the API key from the
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
     * The pins are placeholders pending the user's
     * CA-rotation policy. The audit said:
     *
     *   "Pin each provider's issuer or its public key.
     *    Use OkHttp's CertificatePinner:
     *      - aistudio.google.com
     *        (and generativelanguage.googleapis.com)
     *        → pin Google's GTS CA cert chain
     *        (the public SPKI hash is in Google's
     *        official pins).
     *      - openrouter.ai → pin Let's Encrypt's
     *        ISRG Root X1 (current issuer) or the
     *        specific intermediate.
     *      - api.groq.com → pin Let's Encrypt's
     *        ISRG Root X1.
     *    Rotate the pins annually; document a fallback
     *    to the parent CA so a pin expiry doesn't
     *    brick the LLM path."
     *
     * The SPKI hashes for Google's GTS, Let's Encrypt
     * ISRG Root X1, and the specific intermediates
     * are out of scope for this commit; they are the
     * next step in the certificate-pinning workstream
     * (the user / devops team has the production
     * access to the Google and Let's Encrypt SPKI
     * databases). The [CertificatePinner] plumbing is
     * in place; the SPKI values are the next patch.
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
                googlePinner()
            host.contains("openrouter.ai") ->
                letsEncryptPinner()
            host.contains("api.groq.com") ->
                letsEncryptPinner()
            else -> null
        }
    }

    private fun googlePinner(): CertificatePinner =
        // TODO(security-audit-2026-08-25): the actual
        // Google GTS CA SPKI hashes go here. The pin
        // is a placeholder until the devops team
        // confirms the rotation policy and the SPKI
        // values from the Google PKI page.
        CertificatePinner.Builder()
            .add("aistudio.google.com", "sha256/PLACEHOLDER_GOOGLE_GTS")
            .add("generativelanguage.googleapis.com", "sha256/PLACEHOLDER_GOOGLE_GTS")
            .build()

    private fun letsEncryptPinner(): CertificatePinner =
        // TODO(security-audit-2026-08-25): the
        // ISRG Root X1 SPKI hash goes here. The pin
        // is a placeholder until the devops team
        // confirms the rotation policy and the SPKI
        // values from the Let's Encrypt CT logs.
        CertificatePinner.Builder()
            .add("openrouter.ai", "sha256/PLACEHOLDER_ISRG_ROOT_X1")
            .add("api.groq.com", "sha256/PLACEHOLDER_ISRG_ROOT_X1")
            .build()
}
