package org.mindanchor.continuity

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
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
 * twenty fields against it would make every export written before Program 1
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

    /** Decode strictly after the raw version gate; [json] remains the byte-for-byte frozen encoder. */
    private val strictJson = Json

    private val programZeroFields = setOf(
        "dataDictionaryVersion",
        "exportedAt",
        "appVersionCode",
        "appVersionName",
        "contentSha256",
        "journalEntries",
        "contextFacts",
        "contextInferences",
        "morningMeasures",
    )
    private val programOneFields = setOf(
        "ledgerEvents",
        "ledgerHeadHash",
        "ledgerEventCount",
        "ledgerHighWaterCount",
        "ledgerIntegrity",
        "studyPhases",
        "protocolRegistry",
        "protocolCatalogSha256",
        "transformations",
        "transformationSetVersion",
        "missingData",
        "missingDataWindowStart",
        "missingDataWindowThrough",
        "missingDataPolicyVersion",
        "missingDataStatement",
        "dataDictionary",
        "dataDictionarySha256",
    )
    private val programTwoFields = setOf(
        "passiveRawProvenance",
        "passiveSourceReads",
        "passiveSourceLags",
        "passiveBaselineSegments",
        "passivePipelineRuns",
        "passiveWindowRevisions",
        "passiveDailyRevisions",
        "passiveObservationDecisions",
    )
    private val programThreeFields = setOf("advisoryOpportunities", "interventionEpisodeEvents")

    sealed class DecodeResult {
        data class Success(val export: ResearchExport) : DecodeResult()

        /** A document from a build this one cannot interpret. Never a silent partial read. */
        data class UnsupportedVersion(val version: String) : DecodeResult()

        data object Corrupt : DecodeResult()
    }

    fun encode(export: ResearchExport): String = json.encodeToString(export)

    @Suppress("ReturnCount")
    fun decode(text: String): DecodeResult {
        val raw = runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrElse { return DecodeResult.Corrupt }
        val versionPrimitive = raw["dataDictionaryVersion"] as? JsonPrimitive
            ?: return DecodeResult.Corrupt
        if (!versionPrimitive.isString) return DecodeResult.Corrupt
        val version = versionPrimitive.contentOrNull ?: return DecodeResult.Corrupt
        if (version !in ContinuityContract.SUPPORTED_RESEARCH_DICTIONARY_VERSIONS) {
            return DecodeResult.UnsupportedVersion(version)
        }
        if (!rawFieldsAreCompatible(
                raw, version, programZeroFields, programOneFields, programTwoFields, programThreeFields,
            )
        ) {
            return DecodeResult.Corrupt
        }

        val parsed = runCatching { strictJson.decodeFromString<ResearchExport>(text) }
            .getOrElse { return DecodeResult.Corrupt }
        // A version-1 document is hashed over four content lists, so any
        // Program 1 field it carries is outside its own hash — somebody
        // could paste a fabricated ledger into a genuine Program 0 export
        // and `verify` would still say yes. A v1 document that carries
        // Program 1 content is not a v1 document.
        if (smugglesNewerContent(parsed)) return DecodeResult.Corrupt
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
            !smugglesNewerContent(export) &&
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
        passiveRawProvenance = export.passiveRawProvenance.sortedWith(compareBy({ it.eventStart }, { it.id })),
        passiveSourceReads = export.passiveSourceReads.sortedWith(
            compareBy({ it.attemptedAt }, { it.sourceFamily }, { it.id }),
        ),
        passiveSourceLags = export.passiveSourceLags.sortedWith(
            compareBy({ it.observedAt }, { it.sourceFamily }, { it.id }),
        ),
        passiveBaselineSegments = export.passiveBaselineSegments.sortedWith(compareBy({ it.openedAt }, { it.id })),
        passivePipelineRuns = export.passivePipelineRuns.sortedWith(compareBy({ it.completedAt }, { it.id })),
        passiveWindowRevisions = export.passiveWindowRevisions.sortedWith(
            compareBy({ it.windowStart }, { it.asOfTime }, { it.id }),
        ),
        passiveDailyRevisions = export.passiveDailyRevisions.sortedWith(
            compareBy({ it.localDate }, { it.asOfTime }, { it.id }),
        ),
        passiveObservationDecisions = export.passiveObservationDecisions.sortedWith(
            compareBy({ it.localDate }, { it.asOfTime }, { it.id }),
        ),
        advisoryOpportunities = export.advisoryOpportunities.sortedWith(
            compareBy({ it.presentedAt }, { it.id }),
        ),
        interventionEpisodeEvents = export.interventionEpisodeEvents.sortedWith(
            compareBy({ it.occurredAt }, { it.episodeId }, { it.sequence }, { it.id }),
        ),
    )

    private fun hashContent(export: ResearchExport, version: String): String {
        val text = when (version) {
            ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION ->
                json.encodeToString(projectV1(export))
            ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV2(export))
            ContinuityContract.PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV3(export))
            ContinuityContract.RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV4(export))
            else -> error("no content projection for research export version $version")
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(text.encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }

    private fun projectV1(export: ResearchExport) = ResearchContentV1(
        journalEntries = export.journalEntries,
        contextFacts = export.contextFacts,
        contextInferences = export.contextInferences,
        morningMeasures = export.morningMeasures,
    )

    private fun projectV2(export: ResearchExport) = ResearchContentV2(
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
        missingDataWindowStart = export.missingDataWindowStart,
        missingDataWindowThrough = export.missingDataWindowThrough,
        missingDataPolicyVersion = export.missingDataPolicyVersion,
        missingDataStatement = export.missingDataStatement,
        dataDictionarySha256 = export.dataDictionarySha256,
    )

    private fun projectV3(export: ResearchExport) = ResearchContentV3(
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
        missingDataWindowStart = export.missingDataWindowStart,
        missingDataWindowThrough = export.missingDataWindowThrough,
        missingDataPolicyVersion = export.missingDataPolicyVersion,
        missingDataStatement = export.missingDataStatement,
        dataDictionarySha256 = export.dataDictionarySha256,
        passiveRawProvenance = export.passiveRawProvenance,
        passiveSourceReads = export.passiveSourceReads,
        passiveSourceLags = export.passiveSourceLags,
        passiveBaselineSegments = export.passiveBaselineSegments,
        passivePipelineRuns = export.passivePipelineRuns,
        passiveWindowRevisions = export.passiveWindowRevisions,
        passiveDailyRevisions = export.passiveDailyRevisions,
        passiveObservationDecisions = export.passiveObservationDecisions,
    )

    private fun projectV4(export: ResearchExport) = ResearchContentV4(
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
        missingDataWindowStart = export.missingDataWindowStart,
        missingDataWindowThrough = export.missingDataWindowThrough,
        missingDataPolicyVersion = export.missingDataPolicyVersion,
        missingDataStatement = export.missingDataStatement,
        dataDictionarySha256 = export.dataDictionarySha256,
        passiveRawProvenance = export.passiveRawProvenance,
        passiveSourceReads = export.passiveSourceReads,
        passiveSourceLags = export.passiveSourceLags,
        passiveBaselineSegments = export.passiveBaselineSegments,
        passivePipelineRuns = export.passivePipelineRuns,
        passiveWindowRevisions = export.passiveWindowRevisions,
        passiveDailyRevisions = export.passiveDailyRevisions,
        passiveObservationDecisions = export.passiveObservationDecisions,
        advisoryOpportunities = export.advisoryOpportunities,
        interventionEpisodeEvents = export.interventionEpisodeEvents,
    )

    /**
     * Program 0's export content, in Program 0's declaration order. Field
     * order is wire format; nothing here may change.
     */
    @Serializable
    private data class ResearchContentV1(
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
    private data class ResearchContentV2(
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
        val missingDataWindowStart: String?,
        val missingDataWindowThrough: String?,
        val missingDataPolicyVersion: String,
        val missingDataStatement: String,
        val dataDictionarySha256: String,
    )

    /** Program 2's complete content projection: frozen v2 followed by eight operational datasets. */
    @Serializable
    private data class ResearchContentV3(
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
        val missingDataWindowStart: String?,
        val missingDataWindowThrough: String?,
        val missingDataPolicyVersion: String,
        val missingDataStatement: String,
        val dataDictionarySha256: String,
        val passiveRawProvenance: List<PassiveRawProvenanceDto>,
        val passiveSourceReads: List<PassiveSourceReadDto>,
        val passiveSourceLags: List<PassiveSourceLagDto>,
        val passiveBaselineSegments: List<PassiveBaselineSegmentDto>,
        val passivePipelineRuns: List<PassivePipelineRunDto>,
        val passiveWindowRevisions: List<PassiveWindowRevisionDto>,
        val passiveDailyRevisions: List<PassiveDailyRevisionDto>,
        val passiveObservationDecisions: List<PassiveObservationDecisionDto>,
    )

    /** Program 3's complete content projection: frozen v3 followed by the two advisory datasets. */
    @Serializable
    private data class ResearchContentV4(
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
        val missingDataWindowStart: String?,
        val missingDataWindowThrough: String?,
        val missingDataPolicyVersion: String,
        val missingDataStatement: String,
        val dataDictionarySha256: String,
        val passiveRawProvenance: List<PassiveRawProvenanceDto>,
        val passiveSourceReads: List<PassiveSourceReadDto>,
        val passiveSourceLags: List<PassiveSourceLagDto>,
        val passiveBaselineSegments: List<PassiveBaselineSegmentDto>,
        val passivePipelineRuns: List<PassivePipelineRunDto>,
        val passiveWindowRevisions: List<PassiveWindowRevisionDto>,
        val passiveDailyRevisions: List<PassiveDailyRevisionDto>,
        val passiveObservationDecisions: List<PassiveObservationDecisionDto>,
        val advisoryOpportunities: List<AdvisoryOpportunityDto>,
        val interventionEpisodeEvents: List<InterventionEpisodeEventDto>,
    )
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
 * Checked by [ResearchExportCodec.verify] as well as [ResearchExportCodec.decode].
 * `decode` alone was not enough: a recipient checking an already-parsed
 * export never passes through it, and `verify` answered yes for exactly
 * that forgery.
 *
 * At file scope rather than inside [ResearchExportCodec] for the same
 * reason [rawFieldsAreCompatible] is: detekt's `TooManyFunctions`
 * threshold inside objects is 11, and the object is already at ten.
 *
 * The predicates are a list rather than a chain of `||` so that adding
 * a field to [ResearchExport] is a one-line change here that does not
 * push the function over a complexity threshold — the pressure to
 * leave a new field out is the thing worth designing against.
 */
private fun smugglesNewerContent(export: ResearchExport): Boolean = when (export.dataDictionaryVersion) {
    ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION ->
        programOneContentByField(export).values.any { it } ||
            programTwoContentByField(export).values.any { it } ||
            programThreeContentByField(export).values.any { it }
    ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION ->
        programTwoContentByField(export).values.any { it } ||
            programThreeContentByField(export).values.any { it }
    ContinuityContract.PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION ->
        programThreeContentByField(export).values.any { it }
    else -> false
}

private fun carriedDictionaryMatchesItsHash(export: ResearchExport): Boolean {
    val carried = export.dataDictionary
        // Absent is only legitimate in a Program 0 document, which had no
        // dictionary. In v2/v3 the dictionary is the whole "readable in
        // five years" claim, and stripping it out must not leave a file
        // that still verifies.
        ?: return export.dataDictionaryVersion ==
            ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION
    return ResearchDataDictionary.sha256Of(carried) == export.dataDictionarySha256
}

private fun allowedResearchFieldsFor(
    version: String,
    programZeroFields: Set<String>,
    programOneFields: Set<String>,
    programTwoFields: Set<String>,
    programThreeFields: Set<String>,
): Set<String> = when (version) {
    ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION -> programZeroFields
    ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION -> programZeroFields + programOneFields
    ContinuityContract.PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION ->
        programZeroFields + programOneFields + programTwoFields
    else -> programZeroFields + programOneFields + programTwoFields + programThreeFields
}

private fun knownLaterResearchFieldsFor(
    version: String,
    programOneFields: Set<String>,
    programTwoFields: Set<String>,
    programThreeFields: Set<String>,
): Set<String> = when (version) {
    ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION ->
        programOneFields + programTwoFields + programThreeFields
    ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION -> programTwoFields + programThreeFields
    ContinuityContract.PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION -> programThreeFields
    else -> emptySet()
}

private fun rawFieldsAreCompatible(
    root: JsonObject,
    version: String,
    programZeroFields: Set<String>,
    programOneFields: Set<String>,
    programTwoFields: Set<String>,
    programThreeFields: Set<String>,
): Boolean {
    val allowedFields = allowedResearchFieldsFor(
        version, programZeroFields, programOneFields, programTwoFields, programThreeFields,
    )
    val knownLaterFields = knownLaterResearchFieldsFor(version, programOneFields, programTwoFields, programThreeFields)
    if (root.any { (field, value) ->
            field !in allowedFields && (field !in knownLaterFields || !isEmptyLaterField(field, value))
        }
    ) {
        return false
    }
    return programTwoFields.none { field ->
        root[field]?.containsRawSampleValue(inPassiveContent = true) == true
    }
}

private fun isEmptyLaterField(field: String, value: JsonElement): Boolean = when {
    value is JsonNull -> true
    value is JsonArray -> value.isEmpty()
    value is JsonObject -> value.isEmpty()
    value !is JsonPrimitive -> false
    field == "ledgerIntegrity" -> value.contentOrNull == LedgerIntegrity.NOT_APPLICABLE.name
    value.booleanOrNull != null -> value.booleanOrNull == false
    value.doubleOrNull != null -> value.doubleOrNull == 0.0
    else -> value.contentOrNull.isNullOrEmpty()
}

private fun JsonElement.containsRawSampleValue(inPassiveContent: Boolean): Boolean = when (this) {
    is JsonArray -> any { it.containsRawSampleValue(inPassiveContent) }
    is JsonObject -> any { (field, value) ->
        val normalized = field.lowercase()
        val forbiddenName = normalized == "passiverawsamples" ||
            normalized == "rawvalue" ||
            normalized == "samplevalue" ||
            normalized == "sensorvalue" ||
            (normalized.contains("raw") && normalized.contains("sample")) ||
            (inPassiveContent && normalized == "value")
        forbiddenName || value.containsRawSampleValue(inPassiveContent)
    }
    else -> false
}

/**
 * At file scope rather than inside [ResearchExportCodec] because detekt's
 * `TooManyFunctions` threshold *inside objects* is 11, and the object is
 * at ten. `internal` either way; the object's public surface is unchanged.
 *
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
    "missingDataWindowStart" to (export.missingDataWindowStart != null),
    "missingDataWindowThrough" to (export.missingDataWindowThrough != null),
    "missingDataPolicyVersion" to export.missingDataPolicyVersion.isNotEmpty(),
    "missingDataStatement" to export.missingDataStatement.isNotEmpty(),
    "dataDictionary" to (export.dataDictionary != null),
    "dataDictionarySha256" to export.dataDictionarySha256.isNotEmpty(),
)

internal fun programTwoContentByField(export: ResearchExport): Map<String, Boolean> = mapOf(
    "passiveRawProvenance" to export.passiveRawProvenance.isNotEmpty(),
    "passiveSourceReads" to export.passiveSourceReads.isNotEmpty(),
    "passiveSourceLags" to export.passiveSourceLags.isNotEmpty(),
    "passiveBaselineSegments" to export.passiveBaselineSegments.isNotEmpty(),
    "passivePipelineRuns" to export.passivePipelineRuns.isNotEmpty(),
    "passiveWindowRevisions" to export.passiveWindowRevisions.isNotEmpty(),
    "passiveDailyRevisions" to export.passiveDailyRevisions.isNotEmpty(),
    "passiveObservationDecisions" to export.passiveObservationDecisions.isNotEmpty(),
)

internal fun programThreeContentByField(export: ResearchExport): Map<String, Boolean> = mapOf(
    "advisoryOpportunities" to export.advisoryOpportunities.isNotEmpty(),
    "interventionEpisodeEvents" to export.interventionEpisodeEvents.isNotEmpty(),
)
