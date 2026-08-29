package org.mindanchor.continuity

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.research.EvidenceProtocol
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.MissingDataRecord
import org.mindanchor.research.ResearchDataDictionary
import org.mindanchor.research.Transformation

/**
 * Builds, seals, verifies and (de)serializes a [ResearchExport].
 *
 * [ResearchExport.contentSha256] deliberately excludes `exportedAt`,
 * `appVersionCode`, and `appVersionName` — two devices with identical
 * content must produce the identical hash even when exported at different
 * times or from different app builds, so a researcher (or the person
 * themselves) can tell "did anything actually change" apart from "was this
 * exported again". The data dictionary itself is excluded so a dictionary
 * version bump cannot look like a data change -- but its hash *is* inside,
 * and [verify] recomputes that hash against the carried document, so the
 * dictionary is still tamper-evident.
 *
 * Neither [decode] nor [verify] has a caller in this app: nothing imports a
 * research export, by design. They exist so the algorithm a recipient needs
 * is executable code in this repository rather than prose they have to
 * reimplement from the design document.
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
        // A version-1 document is hashed over four content lists, so any
        // Program 1 field it carries is outside its own hash — somebody
        // could paste a fabricated ledger into a genuine Program 0 export
        // and `verify` would still say yes. A v1 document that carries
        // Program 1 content is not a v1 document.
        if (smugglesProgramOneContent(parsed)) return DecodeResult.Corrupt
        return DecodeResult.Success(parsed)
    }

    /**
     * A version-1 document carrying content only Program 1 can write.
     *
     * A v1 document is hashed over four content lists, so any Program 1
     * field it carries sits outside its own hash — somebody could paste a
     * fabricated ledger into a genuine Program 0 export and the seal would
     * still check out. A v1 document that carries Program 1 content is not
     * a v1 document.
     *
     * Checked by [verify] as well as [decode]. `decode` alone was not
     * enough: a recipient checking an already-parsed export never passes
     * through it, and `verify` answered yes for exactly that forgery.
     *
     * The predicates are a list rather than a chain of `||` so that adding
     * a field to [ResearchExport] is a one-line change here that does not
     * push the function over a complexity threshold — the pressure to
     * leave a new field out is the thing worth designing against.
     */
    private fun smugglesProgramOneContent(export: ResearchExport): Boolean =
        export.dataDictionaryVersion == ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION &&
            programOneContentByField(export).values.any { it }


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
            !smugglesProgramOneContent(export) &&
            carriedDictionaryMatchesItsHash(export) &&
            hashContent(sorted(export), export.dataDictionaryVersion) == export.contentSha256

    /**
     * The carried dictionary has to be the one its hash names.
     *
     * `dataDictionarySha256` is inside the content hash, so it cannot be
     * edited freely — but the dictionary itself is not, deliberately, so
     * that a dictionary version bump does not read as a data change. That
     * leaves exactly one gap: rewriting the carried dictionary and leaving
     * the hash alone. Recomputing it here closes it, and means a file's
     * claim about what its own columns mean is checkable rather than
     * assumed.
     */
    private fun carriedDictionaryMatchesItsHash(export: ResearchExport): Boolean {
        val carried = export.dataDictionary
            // Absent is only legitimate in a Program 0 document, which had
            // no dictionary. In a version-2 document the dictionary is the
            // whole "readable in five years" claim, and stripping it out
            // must not leave a file that still verifies -- the same
            // argument that puts `protocolRegistry` in the hash in full.
            ?: return export.dataDictionaryVersion ==
                ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION
        return ResearchDataDictionary.sha256Of(carried) == export.dataDictionarySha256
    }

    /** Every list in its stable canonical order — the same keys the continuity hasher uses. */
    private fun sorted(export: ResearchExport): ResearchExport = export.copy(
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
        ledgerHighWaterCount = export.ledgerHighWaterCount,
        studyPhases = export.studyPhases,
        protocolRegistry = export.protocolRegistry,
        protocolCatalogSha256 = export.protocolCatalogSha256,
        transformations = export.transformations,
        transformationSetVersion = export.transformationSetVersion,
        missingData = export.missingData,
        missingDataPolicyVersion = export.missingDataPolicyVersion,
        missingDataStatement = export.missingDataStatement,
        dataDictionarySha256 = export.dataDictionarySha256,
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
     * The protocol registry is carried in full rather than as its
     * catalogue hash alone. The hash does fold in each protocol's own
     * `definitionSha256`, but only for the protocols it was computed
     * over — emptying the carried list while leaving the hash intact
     * would otherwise verify, and a file that claims a catalogue hash for
     * protocols it does not contain is not self-describing.
     *
     * The transformation registry is carried in full for a related but
     * different reason.
     * `TransformationRegistry.setVersionOf` hashes `id@version` only — by
     * design, so a typo fix in a description does not split the study
     * series — which means the descriptions are not covered by
     * `transformationSetVersion`. One of those descriptions is the file's
     * own statement that MindAnchor reads no meaning from journal text;
     * left out of the hash, it could be inverted in an exported file with
     * the hash still verifying. So the list is carried in full here.
     *
     * The data dictionary is still absent, on purpose: its own hash
     * travels beside this one so that bumping the dictionary does not read
     * as a data change. `dataDictionarySha256` **is** here, and `verify`
     * separately recomputes it against the carried dictionary — together
     * those make the dictionary tamper-evident without making a version
     * bump look like edited data.
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
        val ledgerHighWaterCount: Int,
        val studyPhases: List<StudyPhaseDto>,
        val protocolRegistry: List<EvidenceProtocol>,
        val protocolCatalogSha256: String,
        val transformations: List<Transformation>,
        val transformationSetVersion: String,
        val missingData: List<MissingDataRecord>,
        val missingDataPolicyVersion: String,
        val missingDataStatement: String,
        val dataDictionarySha256: String,
    )
}

/**
 * Every field a Program 0 document could not have written, keyed by
 * name so a test can check the set against [ResearchExport]'s declared
 * fields.
 *
 * Keyed rather than a bare list because this is the third place in
 * this feature where a hand-maintained field list silently fell behind
 * the class it described. The other two now have reflection guards;
 * this is the third.
 */
internal fun programOneContentByField(export: ResearchExport): Map<String, Boolean> = mapOf(
    "ledgerEvents" to export.ledgerEvents.isNotEmpty(),
    "ledgerHeadHash" to export.ledgerHeadHash.isNotEmpty(),
    "ledgerEventCount" to (export.ledgerEventCount != 0),
    "ledgerHighWaterCount" to (export.ledgerHighWaterCount != 0),
    "ledgerIntegrity" to (export.ledgerIntegrity != LedgerIntegrity.NOT_APPLICABLE),
    "studyPhases" to export.studyPhases.isNotEmpty(),
    "protocolRegistry" to export.protocolRegistry.isNotEmpty(),
    "protocolCatalogSha256" to export.protocolCatalogSha256.isNotEmpty(),
    "transformations" to export.transformations.isNotEmpty(),
    "transformationSetVersion" to export.transformationSetVersion.isNotEmpty(),
    "missingData" to export.missingData.isNotEmpty(),
    "missingDataPolicyVersion" to export.missingDataPolicyVersion.isNotEmpty(),
    "missingDataStatement" to export.missingDataStatement.isNotEmpty(),
    "dataDictionary" to (export.dataDictionary != null),
    "dataDictionarySha256" to export.dataDictionarySha256.isNotEmpty(),
)
