package org.mindanchor.friction

import org.mindanchor.sleep.BedtimeList

/**
 * The integrity-wrapped versions of the v0.20.0 plaintext
 * codecs. Each wraps the existing codec with the
 * [IntegritySealedCodec] layer, keyed with the
 * Keystore-backed HMAC key.
 *
 * ## What this layer protects
 *
 * A v0.20.0 power user with root can rewrite the
 * friction-gate ledger and silence the gate. The
 * integrity layer is the threat model: a tamper-evident
 * seal on the data. Reading a v0.20.0 plaintext form
 * (the pre-HMAC layout) returns the empty/reset value;
 * the first write seals the data with the new format.
 *
 * ## What this layer does NOT protect
 *
 * MASTG-BEST-0066 is explicit: integrity checks are
 * defense-in-depth, not a standalone guarantee. A user
 * with the Keystore key (a state actor with a TEE
 * bypass) can forge. The project accepts this; the
 * threat is "a motivated user forges the gate tally,"
 * not "a state actor."
 *
 * ## Migration
 *
 * A v0.20.0 plaintext form on disk is *not* migrated
 * on read — the integrity layer returns the reset value.
 * The first write produces a sealed record. This is
 * intentionally fail-closed: a v0.20.0 form is treated
 * as either a forge or an unverified record, and the
 * right behaviour is to reset and start over.
 *
 * @wording-reviewed — the migration path message is
 * clinical-review-required.
 */
object SealedCodecs {

    /** The Keystore-backed HMAC key. Shared across all codecs in this app. */
    private val key by lazy { KeystoreHmacKey.getOrCreate() }

    /**
     * The sealed small-things codec: maps the on-disk
     * String to the canonical list form via
     * [SmallThings.decode] / [SmallThings.encode]. The
     * reset is the empty list (the inner codec of an
     * empty String is an empty list).
     */
    val smallThings: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : IntegritySealedCodec.Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        key = key,
        resetValue = SmallThings.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for small things
     * via the sealed codec, returning the empty list on
     * any failure.
     */
    fun decodeSmallThings(raw: String): List<String> =
        SmallThings.decode(smallThings.decode(raw))

    /**
     * Helper: encode a list of small things via the
     * sealed codec.
     */
    fun encodeSmallThings(value: List<String>): String =
        smallThings.encode(SmallThings.encode(value))

    /**
     * The sealed bedtime-list codec. The reset is the
     * empty list.
     */
    val bedtimeList: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : IntegritySealedCodec.Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        key = key,
        resetValue = BedtimeList.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for the bedtime
     * list via the sealed codec, returning the empty
     * list on any failure.
     */
    fun decodeBedtimeList(raw: String): List<String> =
        BedtimeList.decode(bedtimeList.decode(raw))

    /**
     * Helper: encode a list of bedtime items via the
     * sealed codec.
     */
    fun encodeBedtimeList(value: List<String>): String =
        bedtimeList.encode(BedtimeList.encode(value))

    /**
     * The sealed compassion-moments codec. The reset is
     * the empty list of compassion moments.
     */
    val compassion: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : IntegritySealedCodec.Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        key = key,
        resetValue = CompassionStore.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for compassion
     * moments via the sealed codec.
     */
    fun decodeCompassion(raw: String): List<CompassionMoment> =
        CompassionStore.decode(compassion.decode(raw))

    /**
     * Helper: encode a list of compassion moments via
     * the sealed codec.
     */
    fun encodeCompassion(value: List<CompassionMoment>): String =
        compassion.encode(CompassionStore.encode(value))

    /**
     * The sealed if-then-plans codec. The reset is the
     * empty map of plans.
     */
    val ifThenPlans: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : IntegritySealedCodec.Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        key = key,
        resetValue = IfThenPlanStore.encode(emptyMap()),
    )

    /**
     * Helper: decode the on-disk string for if-then
     * plans via the sealed codec.
     */
    fun decodeIfThenPlans(raw: String): Map<String, IfThenPlan> =
        IfThenPlanStore.decode(ifThenPlans.decode(raw))

    /**
     * Helper: encode a map of if-then plans via the
     * sealed codec.
     */
    fun encodeIfThenPlans(value: Map<String, IfThenPlan>): String =
        ifThenPlans.encode(IfThenPlanStore.encode(value))
}
