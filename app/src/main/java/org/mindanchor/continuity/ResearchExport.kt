package org.mindanchor.continuity

import kotlinx.serialization.Serializable
import org.mindanchor.research.DataDictionary
import org.mindanchor.research.EvidenceProtocol
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.MissingDataRecord
import org.mindanchor.research.Transformation

/**
 * The structured, plaintext research export: what the person wrote, what
 * MindAnchor derived from it structurally, what they reported about their
 * days, and the complete provenance of the software that recorded all of
 * it — in a shape a researcher (or the person themselves) can read without
 * decoding the opaque [ContinuityPayload.legacyBackupJson] carrier.
 *
 * ## Self-describing on purpose
 *
 * The document carries its own [dataDictionary]. An export that has to be
 * read alongside a version of the source that produced it is not a
 * research artefact; it is a database dump. Carrying the dictionary means
 * a file found in five years still says what every column means, what its
 * units are, what values it may take, and whether a person wrote it or
 * MindAnchor did.
 *
 * ## Versioning
 *
 * [dataDictionaryVersion] versions the dictionary and the document shape
 * together, because they are frozen together. Program 0 files carry
 * `mindanchor-research-v1` and decode into this shape with every later
 * field empty; Program 1 files carry `mindanchor-research-v2`, and new
 * files carry `mindanchor-research-v3`.
 *
 * ## Two hashes, doing different jobs
 *
 * [contentSha256] covers the content only — never [exportedAt], never the
 * app version — so "did the data change" stays answerable independently of
 * "was this exported again". The full dictionary payload is not repeated
 * inside that projection, but [dataDictionarySha256] is: changing the
 * carried dictionary is therefore visible and tamper-evident.
 * [ResearchExportCodec.verify] recomputes both using the projection for
 * this file's own version.
 */
@Serializable
data class ResearchExport(
    val dataDictionaryVersion: String = DATA_DICTIONARY_VERSION,
    val exportedAt: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val contentSha256: String,
    val journalEntries: List<JournalEntryDto>,
    val contextFacts: List<JournalContextDto>,
    val contextInferences: List<JournalContextDto>,
    val morningMeasures: List<MorningMeasureDto>,

    // --- Program 1. All defaulted, so a Program 0 file still decodes. ---

    /** The whole chained ledger, in sequence order. */
    val ledgerEvents: List<ResearchLedgerEventDto> = emptyList(),

    /**
     * A reproducible summary of the carried ledger: the newest event's
     * hash and the number of carried events. Both are derived from the
     * list, so they detect file tampering but not rows lost before export.
     * [ledgerHighWaterCount] is the independent truncation check.
     */
    val ledgerHeadHash: String = "",
    val ledgerEventCount: Int = 0,

    /**
     * The largest ledger the exporting device ever recorded holding, from
     * its own storage rather than from the list above.
     *
     * This is the one number here that is not derived from the carried
     * events, and it is what makes truncation visible: [ledgerHeadHash]
     * and [ledgerEventCount] are computed from the very list they describe,
     * so they can only show that the *file* changed after it was written.
     * A count kept elsewhere can show that events were already gone when
     * the file was written.
     *
     * Zero means the device had no mark to report -- a phone that had not
     * written since a restore, or a build older than the mark. Zero is not
     * evidence of anything; only [ledgerEventCount] falling *below* a
     * non-zero value here is.
     */
    val ledgerHighWaterCount: Int = 0,

    /**
     * What this build made of the chain when it exported.
     *
     * Reproducible rather than trusted: re-run `LedgerChain.verify` over
     * [ledgerEvents], and separately compare [ledgerEventCount] against
     * [ledgerHighWaterCount]. Those two checks also tell the two failures
     * apart, which this single field cannot -- a chain that verifies while
     * the count sits below the high-water mark is a truncation, not a
     * broken chain, and reporting it as `BROKEN` without that comparison
     * would send a reader looking for corruption that is not there.
     */
    val ledgerIntegrity: LedgerIntegrity = LedgerIntegrity.NOT_APPLICABLE,

    /** Every study phase, in order. The phase covering a record is the last one started at or before it. */
    val studyPhases: List<StudyPhaseDto> = emptyList(),

    /** The full evidence contract of every catalogued protocol, so the file needs no other source. */
    val protocolRegistry: List<EvidenceProtocol> = emptyList(),
    val protocolCatalogSha256: String = "",

    val transformations: List<Transformation> = emptyList(),
    val transformationSetVersion: String = "",

    /** Every absence, with its reason. Nothing is imputed to fill one. */
    val missingData: List<MissingDataRecord> = emptyList(),

    /**
     * The span [missingData] describes, as ISO-8601 local dates, or null
     * when no record fell inside a reportable window.
     *
     * Carried because an empty [missingData] is otherwise indistinguishable
     * from a person who missed nothing. That is not hypothetical: a
     * replacement phone with no network time boots to its build date, and
     * a restore-then-export before the clock syncs leaves every record
     * dated after the export date and so outside any window this policy
     * will vouch for. Without these two fields the file would show a year
     * of data with zero missing values — perfect adherence — which is an
     * assertion about something that did not happen.
     *
     * Null here says "no window", which is a different statement from "no
     * absences", and the difference is the whole point.
     */
    val missingDataWindowStart: String? = null,
    val missingDataWindowThrough: String? = null,
    val missingDataPolicyVersion: String = "",
    val missingDataStatement: String = "",

    val dataDictionary: DataDictionary? = null,
    val dataDictionarySha256: String = "",

    // --- Program 2. Appended so the frozen v1/v2 projections do not move. ---

    val passiveRawProvenance: List<PassiveRawProvenanceDto> = emptyList(),
    val passiveSourceReads: List<PassiveSourceReadDto> = emptyList(),
    val passiveSourceLags: List<PassiveSourceLagDto> = emptyList(),
    val passiveBaselineSegments: List<PassiveBaselineSegmentDto> = emptyList(),
    val passivePipelineRuns: List<PassivePipelineRunDto> = emptyList(),
    val passiveWindowRevisions: List<PassiveWindowRevisionDto> = emptyList(),
    val passiveDailyRevisions: List<PassiveDailyRevisionDto> = emptyList(),
    val passiveObservationDecisions: List<PassiveObservationDecisionDto> = emptyList(),
) {
    companion object {
        const val DATA_DICTIONARY_VERSION = ContinuityContract.RESEARCH_DICTIONARY_VERSION
    }
}
