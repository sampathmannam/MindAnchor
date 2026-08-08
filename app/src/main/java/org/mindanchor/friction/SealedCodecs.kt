package org.mindanchor.friction

/**
 * The integrity-wrapped versions of the v0.20.0 plaintext
 * codecs. Each wraps the existing codec with the
 * [IntegritySealedCodec] layer.
 *
 * The codecs are not yet wired into [FrictionPrefs]; the
 * adoption is a follow-up commit because the production
 * path needs the Keystore-backed key (the test path uses
 * a fixed key for unit-testability). This file provides
 * the codecs and the seam; the wiring is a single line
 * in [FrictionPrefs] per codec.
 *
 * @wording-reviewed — the migration path message is
 * clinical-review-required.
 */
object SealedCodecs {
    /**
     * A wrapper around any String <-> String codec with
     * the integrity layer applied. The inner codec is
     * provided at construction time; the HMAC key is the
     * Keystore-backed key (see [KeystoreHmacKey]).
     */
    fun <T : IntegritySealedCodec.Codec<String>> wrap(
        inner: T,
    ): IntegritySealedCodec = IntegritySealedCodec(inner, KeystoreHmacKey.getOrCreate())
}
