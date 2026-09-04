package org.mindanchor.research

/**
 * The longest note the research log will store. A confounder note is a
 * short annotation in the person's own words, not a journal entry —
 * `JournalEntry.MAX_BODY_LENGTH` is where long-form writing belongs.
 */
const val MAX_LEDGER_NOTE_LENGTH = 500

/**
 * Everything the research ledger can record, from the mental-health OS
 * design's §10.4 (confounders and provenance) and §12.3 (adverse events).
 *
 * [isSelfReported] separates the two halves and is the only classification
 * this enum makes. Self-reported kinds are entered by the person in the
 * research log; the rest are written by MindAnchor itself when a version,
 * a device, or a study phase changes. Nothing in MindAnchor infers a
 * self-reported kind, and nothing interprets one after it is written.
 *
 * ## Two kinds that are deliberately capability-only
 *
 *  - [SENSOR_GAP] — Program 1 owns no sensors, so no production code path
 *    records one. The kind exists end to end (serialised, hashed, backed
 *    up, restored, exported) so Program 2 can record real gaps without a
 *    schema change and without reinterpreting existing history. Inventing
 *    a Program 1 detector would mean fabricating data.
 *  - [RULE_VERSION_CHANGE] / [MODEL_VERSION_CHANGE] — this build ships no
 *    decision rules and no models (see `ProvenanceVersions`), so these
 *    fire for the first time when Program 2 does.
 *
 * [MEDICATION_CHANGE] records only *that* something changed, plus the
 * person's own note. Nothing reads it, reacts to it, or advises on it.
 */
enum class LedgerEventKind(val isSelfReported: Boolean) {
    SHIFT_SCHEDULE(true),
    EXERCISE(true),
    ILLNESS(true),
    CAFFEINE(true),
    MEDICATION_CHANGE(true),
    LIFE_EVENT(true),
    ADVERSE_OR_UNINTENDED_EFFECT(true),

    STUDY_PHASE_STARTED(false),
    PROTOCOL_VERSION_REGISTERED(false),
    APP_VERSION_CHANGE(false),
    RULE_VERSION_CHANGE(false),
    MODEL_VERSION_CHANGE(false),
    TRANSFORMATION_VERSION_CHANGE(false),
    MISSING_DATA_POLICY_CHANGE(false),
    INSTRUMENT_VERSION_CHANGE(false),
    DICTIONARY_VERSION_CHANGE(false),
    DEVICE_CHANGE(false),
    SENSOR_GAP(false),
}

/**
 * A ledger event before it has been linked into the chain — everything
 * about the event itself, and nothing about its position in the chain.
 *
 * [LedgerChain.link] turns one of these into a [ResearchLedgerEvent] by
 * adding the previous event's hash and computing this event's. Keeping the
 * two types apart is what makes it impossible to construct a
 * [ResearchLedgerEvent] with a hash that does not describe its own
 * contents: there is no other constructor path.
 *
 * [note] is the person's own words, stored verbatim. [payloadJson] carries
 * the structured detail of a system-recorded event (a version vector, a
 * protocol id) and is `"{}"` for a self-reported one.
 */
data class UnlinkedLedgerEvent(
    val sequence: Long,
    val kind: LedgerEventKind,
    val occurredAt: Long,
    val recordedAt: Long,
    val localDate: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val note: String,
    val payloadJson: String,
)

/**
 * One immutable, chained research-ledger event.
 *
 * [id] is [eventHash] — the event is content-addressed. That is what makes
 * a replacement-phone restore duplicate-free without a de-duplication
 * pass: re-inserting an event the database already holds is an
 * `INSERT OR IGNORE` on the same primary key.
 */
data class ResearchLedgerEvent(
    val id: String,
    val sequence: Long,
    val kind: LedgerEventKind,
    val occurredAt: Long,
    val recordedAt: Long,
    val localDate: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val note: String,
    val payloadJson: String,
    val previousEventHash: String,
    val eventHash: String,
) {
    /** The event stripped back to its own contents, for re-linking during verification. */
    fun unlinked(): UnlinkedLedgerEvent = UnlinkedLedgerEvent(
        sequence = sequence,
        kind = kind,
        occurredAt = occurredAt,
        recordedAt = recordedAt,
        localDate = localDate,
        studyPhaseId = studyPhaseId,
        sourceDeviceId = sourceDeviceId,
        note = note,
        payloadJson = payloadJson,
    )
}
