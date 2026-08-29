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
 * `mindanchor-research-v1` and decode into this shape with every Program 1
 * field empty; new files carry `mindanchor-research-v2`.
 *
 * ## Two hashes, doing different jobs
 *
 * [contentSha256] covers the content only — never [exportedAt], never the
 * app version — so "did the data change" stays answerable independently of
 * "was this exported again". [dataDictionarySha256] is carried beside it
 * rather than inside it, so a dictionary version bump does not masquerade
 * as a data change. [ResearchExportCodec.verify] recomputes the first
 * using the projection for this file's own version.
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
     * The ledger's anchor: the head event's hash and how many events
     * precede it. Carried because it is the part of the chain that cannot
     * live *in* the chain — without it, dropping the newest events leaves
     * a shorter but perfectly self-consistent history.
     */
    val ledgerHeadHash: String = "",
    val ledgerEventCount: Int = 0,

    /**
     * What this build made of the chain when it exported. A convenience,
     * not the authority: a reader holding the file should re-run
     * `LedgerChain.verify` against [ledgerHeadHash] and [ledgerEventCount]
     * themselves rather than trust a verdict the file carries about itself.
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
    val missingDataPolicyVersion: String = "",
    val missingDataStatement: String = "",

    val dataDictionary: DataDictionary? = null,
    val dataDictionarySha256: String = "",
) {
    companion object {
        const val DATA_DICTIONARY_VERSION = ContinuityContract.RESEARCH_DICTIONARY_VERSION
    }
}
