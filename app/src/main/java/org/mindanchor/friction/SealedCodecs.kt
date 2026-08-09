package org.mindanchor.friction

import org.mindanchor.model.CheckIn
import org.mindanchor.model.CheckInState
import org.mindanchor.model.CheckInStore
import org.mindanchor.model.Note
import org.mindanchor.model.NotesState
import org.mindanchor.model.NoteStore
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
 * ## v0.20.1 round 2 (CodeRabbit #4)
 *
 * The v0.20.1 round 1 wrapped the codecs with a
 * captured `Key`. The key could become invalid (Keystore
 * corruption, post-OTA failure) and the codec would not
 * recover. v0.20.1 round 2 passes a *key provider* — a
 * `() -> Key` function the codec calls each time it
 * needs a key. The provider owns the Keystore and the
 * recovery path; the codec never holds the key. The
 * provider is recomputed on every MAC operation; a
 * failure on one operation is isolated to that
 * operation.
 *
 * The provider also addresses CodeRabbit #4's second
 * concern: `SealedCodecs` previously resolved its lazy
 * key before constructing a codec, so a `getOrCreate()`
 * failure escaped. v0.20.1 round 2 wraps the provider
 * in a `try { ... } catch { ... }` that returns the
 * reset value rather than throwing.
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

    /**
     * The key provider. Each call returns a fresh
     * [java.security.Key] from the Keystore. The
     * provider does not cache: a failure on one call
     * is isolated to that call. The provider swallows
     * Keystore exceptions and returns null; the codec
     * treats a null key as "use the reset value" (the
     * MAC cannot be computed; the integrity layer
     * fail-closes).
     */
    private val keyProvider: () -> java.security.Key? = {
        try {
            KeystoreHmacKey.getOrCreate()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The sealed small-things codec. The codecId is
     * the FrictionPrefs DataStore key, "small_things".
     * The MAC is bound to this codecId so a sealed
     * value from another preference cannot be replayed
     * against small-things.
     */
    val smallThings: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "small_things",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = SmallThings.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for small things
     * via the sealed codec, returning the empty list on
     * any failure.
     */
    fun decodeSmallThings(raw: String): List<String> =
        try {
            SmallThings.decode(smallThings.decode(raw))
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Helper: encode a list of small things via the
     * sealed codec.
     */
    fun encodeSmallThings(value: List<String>): String =
        smallThings.encode(SmallThings.encode(value))

    /**
     * The sealed bedtime-list codec. codecId is
     * "bedtime_list_items".
     */
    val bedtimeList: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "bedtime_list_items",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = BedtimeList.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for the bedtime
     * list via the sealed codec, returning the empty
     * list on any failure.
     */
    fun decodeBedtimeList(raw: String): List<String> =
        try {
            BedtimeList.decode(bedtimeList.decode(raw))
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Helper: encode a list of bedtime items via the
     * sealed codec.
     */
    fun encodeBedtimeList(value: List<String>): String =
        bedtimeList.encode(BedtimeList.encode(value))

    /**
     * The sealed compassion-moments codec. codecId is
     * "compassion_moments".
     */
    val compassion: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "compassion_moments",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = CompassionStore.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for compassion
     * moments via the sealed codec.
     */
    fun decodeCompassion(raw: String): List<CompassionMoment> =
        try {
            CompassionStore.decode(compassion.decode(raw))
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Helper: encode a list of compassion moments via
     * the sealed codec.
     */
    fun encodeCompassion(value: List<CompassionMoment>): String =
        compassion.encode(CompassionStore.encode(value))

    /**
     * The sealed if-then-plans codec. codecId is
     * "if_then_plans".
     */
    val ifThenPlans: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "if_then_plans",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = IfThenPlanStore.encode(emptyMap()),
    )

    /**
     * The sealed gate-tally codec. codecId is
     * "gate_tallies".
     *
     * CodeRabbit audit #20 (2026-08-08): the v0.20.1
     * round 1 documentation claimed GateLedger was
     * wrapped, but FrictionPrefs still read and wrote
     * GateLedger without SealedCodecs. The primary
     * threat the integrity layer was supposed to fix
     * — forged gate tallies that silence the gate — was
     * left unprotected. v0.20.1 round 2 wires the
     * gate-tally codec and exposes the helpers below
     * for FrictionPrefs to use.
     */
    val gateTallies: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "gate_tallies",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = GateLedger.encode(emptyMap()),
    )

    /**
     * Helper: decode the on-disk string for gate tallies
     * via the sealed codec, returning the empty map on
     * any failure.
     */
    fun decodeGateTallies(raw: String): Map<String, GateTally> =
        try {
            GateLedger.decode(gateTallies.decode(raw))
        } catch (e: Exception) {
            emptyMap()
        }

    /**
     * Helper: encode a map of gate tallies via the
     * sealed codec.
     */
    fun encodeGateTallies(value: Map<String, GateTally>): String =
        gateTallies.encode(GateLedger.encode(value))

    /**
     * Helper: decode the on-disk string for if-then
     * plans via the sealed codec.
     */
    fun decodeIfThenPlans(raw: String): Map<String, IfThenPlan> =
        try {
            IfThenPlanStore.decode(ifThenPlans.decode(raw))
        } catch (e: Exception) {
            emptyMap()
        }

    /**
     * Helper: encode a map of if-then plans via the
     * sealed codec.
     */
    fun encodeIfThenPlans(value: Map<String, IfThenPlan>): String =
        ifThenPlans.encode(IfThenPlanStore.encode(value))

    /**
     * The sealed per-app session-length codec. codecId
     * is "per_app_session_length".
     *
     * v0.20.1 round 4 (item M): the data layer is in
     * [PerAppSessionLength]. The integrity layer
     * closes the same threat as the other codecs — a
     * motivated user with root can rewrite the per-app
     * time-box map and prime the user toward a longer
     * or shorter default for a specific app, with the
     * gate silently applying the change. Sealing the
     * data ensures the on-disk form cannot be tampered
     * with without invalidating the MAC.
     */
    val perAppSessionLength: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "per_app_session_length",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = PerAppSessionLengthStore.encode(PerAppSessionLength()),
    )

    /**
     * Helper: decode the on-disk string for per-app
     * session length via the sealed codec, returning
     * the empty state on any failure.
     */
    fun decodePerAppSessionLength(raw: String): PerAppSessionLength =
        try {
            PerAppSessionLengthStore.decode(perAppSessionLength.decode(raw))
        } catch (e: Exception) {
            PerAppSessionLength()
        }

    /**
     * Helper: encode a per-app session length state
     * via the sealed codec.
     */
    fun encodePerAppSessionLength(value: PerAppSessionLength): String =
        perAppSessionLength.encode(PerAppSessionLengthStore.encode(value))

    /**
     * The sealed notes codec. codecId is
     * "notes". v0.20.1 round 5.
     *
     * Threat model: a motivated user with root
     * could rewrite the on-disk notes and either
     * impersonate a note the user did not write or
     * delete a note the user did write. The seal
     * makes the on-disk form tamper-evident; a
     * forged note (or a deleted note) fails the
     * MAC and falls back to the empty state. The
     * user re-enters the note (capture pattern
     * survives, history does not).
     */
    val notes: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "notes",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = NoteStore.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for notes
     * via the sealed codec, returning the empty
     * state on any failure.
     */
    fun decodeNotes(raw: String): NotesState =
        try {
            NotesState(NoteStore.decode(notes.decode(raw)))
        } catch (e: Exception) {
            NotesState()
        }

    /**
     * Helper: encode a notes state via the sealed
     * codec.
     */
    fun encodeNotes(value: NotesState): String =
        notes.encode(NoteStore.encode(value.notes))

    /**
     * The sealed check-ins codec. codecId is
     * "checkins". v0.20.1 round 5.
     *
     * The threat model is identical to notes: a
     * motivated user with root could rewrite the
     * accepted check-ins. The seal makes the
     * on-disk form tamper-evident; a forged or
     * deleted record fails the MAC and falls back
     * to the empty state. The next legitimate
     * check-in is the first sealed record.
     */
    val checkIns: IntegritySealedCodec = IntegritySealedCodec(
        inner = object : Codec<String> {
            override fun encode(value: String): String = value
            override fun decode(encoded: String): String = encoded
        },
        codecId = "checkins",
        keyProvider = { keyProvider() ?: throw IllegalStateException("Keystore unavailable") },
        resetValue = CheckInStore.encode(emptyList()),
    )

    /**
     * Helper: decode the on-disk string for
     * check-ins via the sealed codec, returning
     * the empty state on any failure.
     */
    fun decodeCheckIns(raw: String): CheckInState =
        try {
            CheckInState(CheckInStore.decode(checkIns.decode(raw)))
        } catch (e: Exception) {
            CheckInState()
        }

    /**
     * Helper: encode a check-in state via the
     * sealed codec.
     */
    fun encodeCheckIns(value: CheckInState): String =
        checkIns.encode(CheckInStore.encode(value.checkIns))
}
