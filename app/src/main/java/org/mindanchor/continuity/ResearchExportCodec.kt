package org.mindanchor.continuity

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.MissingDataRecord

/**
 * Builds, seals, verifies and (de)serializes a [ResearchExport].
 *
 * [ResearchExport.contentSha256] deliberately excludes `exportedAt`,
 * `appVersionCode`, and `appVersionName` — two devices with identical
 * content must produce the identical hash even when exported at different
 * times or from different app builds, so a researcher (or the person
 * themselves) can tell "did anything actually change" apart from "was this
 * exported again". It also excludes the data dictionary, which is carried
 * with its own hash so a dictionary version bump cannot look like a data
 * change.
 *
 * ## The hash is versioned, for the same reason the snapshot's is
 *
 * A Program 0 export's hash covers four content lists. Digesting today's
 * twelve fields against it would make every export written before Program 1
 * fail [verify] — a file a person may already have handed to somebody.
 * [hashContent] therefore projects onto the field set of the document's own
 * `dataDictionaryVersion`, and both projections are frozen by test.
 */
object ResearchExportCodec {

    /**
     * Pinned, and pinned to *Program 0's* configuration: this encoder
     * produces both the file's bytes and the bytes the content hash
     * digests, so `prettyPrint = true` is not a formatting preference —
     * it is what every Program 0 export's hash was computed under.
     * Changing any line here invalidates the hash of every export already
     * in existence, including ones a person may have handed to somebody.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    sealed class DecodeResult {
        data class Success(val export: ResearchExport) : DecodeResult()

        /** A document from a build this one cannot interpret. Never a silent partial read. */
        data class UnsupportedVersion(val version: String) : DecodeResult()

        data object Corrupt : DecodeResult()
    }

    fun encode(export: ResearchExport): String = json.encodeToString(export)

    fun decode(text: String): DecodeResult {
        val parsed = runCatching { json.decodeFromString<ResearchExport>(text) }
            .getOrElse { return DecodeResult.Corrupt }
        if (parsed.dataDictionaryVersion !in ContinuityContract.SUPPORTED_RESEARCH_DICTIONARY_VERSIONS) {
            return DecodeResult.UnsupportedVersion(parsed.dataDictionaryVersion)
        }
        return DecodeResult.Success(parsed)
    }

    /**
     * Returns [export] with its lists in canonical order and
     * [ResearchExport.contentSha256] computed over them.
     *
     * The caller assembles the document; sealing it is one step so the
     * hash cannot be computed over a different ordering than the one
     * written to the file.
     */
    fun seal(export: ResearchExport): ResearchExport {
        val canonical = sorted(export)
        return canonical.copy(contentSha256 = hashContent(canonical, canonical.dataDictionaryVersion))
    }

    /**
     * Whether [export]'s stored hash still describes its content, recomputed
     * with the projection for that file's own version. A Program 0 export
     * written months ago stays verifiable by this build.
     */
    fun verify(export: ResearchExport): Boolean =
        export.dataDictionaryVersion in ContinuityContract.SUPPORTED_RESEARCH_DICTIONARY_VERSIONS &&
            hashContent(sorted(export), export.dataDictionaryVersion) == export.contentSha256

    /** Every list in its stable canonical order — the same keys the continuity hasher uses. */
    fun sorted(export: ResearchExport): ResearchExport = export.copy(
        journalEntries = export.journalEntries.sortedBy { it.id },
        contextFacts = export.contextFacts.sortedBy { it.id },
        contextInferences = export.contextInferences.sortedBy { it.id },
        morningMeasures = export.morningMeasures.sortedBy { it.id },
        ledgerEvents = export.ledgerEvents.sortedWith(compareBy({ it.sequence }, { it.id })),
        studyPhases = export.studyPhases.sortedWith(compareBy({ it.ordinal }, { it.id })),
        protocolRegistry = export.protocolRegistry.sortedWith(compareBy({ it.id }, { it.version })),
        transformations = export.transformations.sortedBy { it.id },
        missingData = export.missingData.sortedWith(compareBy({ it.localDate }, { it.variable })),
    )

    private fun hashContent(export: ResearchExport, version: String): String {
        val text = when (version) {
            ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION ->
                json.encodeToString(projectV1(export))
            ContinuityContract.RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV2(export))
            else -> error("no content projection for research export version $version")
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(text.encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }

    private fun projectV1(export: ResearchExport) = V1Content(
        journalEntries = export.journalEntries,
        contextFacts = export.contextFacts,
        contextInferences = export.contextInferences,
        morningMeasures = export.morningMeasures,
    )

    private fun projectV2(export: ResearchExport) = V2Content(
        journalEntries = export.journalEntries,
        contextFacts = export.contextFacts,
        contextInferences = export.contextInferences,
        morningMeasures = export.morningMeasures,
        ledgerEvents = export.ledgerEvents,
        ledgerHeadHash = export.ledgerHeadHash,
        ledgerEventCount = export.ledgerEventCount,
        ledgerIntegrity = export.ledgerIntegrity,
        studyPhases = export.studyPhases,
        protocolCatalogSha256 = export.protocolCatalogSha256,
        transformationSetVersion = export.transformationSetVersion,
        missingData = export.missingData,
    )

    /**
     * Program 0's export content, in Program 0's declaration order. Field
     * order is wire format; nothing here may change.
     */
    @Serializable
    private data class V1Content(
        val journalEntries: List<JournalEntryDto>,
        val contextFacts: List<JournalContextDto>,
        val contextInferences: List<JournalContextDto>,
        val morningMeasures: List<MorningMeasureDto>,
    )

    /**
     * Program 1's export content.
     *
     * The protocol registry and the transformation registry appear as their
     * content hashes rather than in full: the hashes already cover them, and
     * a digest is not the place to carry a page of prose. The data
     * dictionary is absent on purpose — its own hash travels beside this
     * one, so bumping the dictionary does not read as a data change.
     */
    @Serializable
    private data class V2Content(
        val journalEntries: List<JournalEntryDto>,
        val contextFacts: List<JournalContextDto>,
        val contextInferences: List<JournalContextDto>,
        val morningMeasures: List<MorningMeasureDto>,
        val ledgerEvents: List<ResearchLedgerEventDto>,
        val ledgerHeadHash: String,
        val ledgerEventCount: Int,
        val ledgerIntegrity: LedgerIntegrity,
        val studyPhases: List<StudyPhaseDto>,
        val protocolCatalogSha256: String,
        val transformationSetVersion: String,
        val missingData: List<MissingDataRecord>,
    )
}
