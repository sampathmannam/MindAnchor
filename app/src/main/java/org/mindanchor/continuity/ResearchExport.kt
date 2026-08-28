package org.mindanchor.continuity

import kotlinx.serialization.Serializable

/**
 * The structured, plaintext research export: Journal originals and their
 * structural facts, in a shape a researcher (or the person themselves) can
 * read without decoding the opaque [ContinuityPayload.legacyBackupJson]
 * carrier. Reuses the same [JournalEntryDto] / [JournalContextDto] /
 * [MorningMeasureDto] types [ContinuitySnapshot] uses — this is a
 * differently-shaped view of the same rows, not a parallel copy.
 *
 * Wiring this into a document picker / privacy warning / UI is a later
 * task; this file only defines the shape and the codec that builds and
 * (de)serializes it.
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
) {
    companion object {
        const val DATA_DICTIONARY_VERSION = ContinuityContract.RESEARCH_DICTIONARY_VERSION
    }
}
