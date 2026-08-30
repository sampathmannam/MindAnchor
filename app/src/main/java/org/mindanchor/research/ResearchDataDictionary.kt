package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.continuity.ContinuityContract
import org.mindanchor.journal.ContextRecordType
import org.mindanchor.journal.JournalKind

/** The datasets a research export carries. A closed set, so a variable cannot name a table that does not exist. */
@Serializable
enum class DictionaryDataset {
    JOURNAL_ENTRIES,
    JOURNAL_CONTEXT,
    MORNING_MEASURES,
    RESEARCH_LEDGER_EVENTS,
    STUDY_PHASES,
    MISSING_DATA,
}

/**
 * Where a value came from. The distinction between [USER_AUTHORED] and
 * [DERIVED_STRUCTURAL] is the design's rule that Journal authorship stays
 * separate from anything MindAnchor worked out, made machine-readable.
 */
@Serializable
enum class VariableProvenance {
    /** The person's own words, stored exactly as written. */
    USER_AUTHORED,

    /** The person's own answer on a scale or a chip they chose. */
    USER_REPORTED,

    /** Derived from the person's text by a versioned structural transformation. Never interpreted. */
    DERIVED_STRUCTURAL,

    /** Written by MindAnchor about itself: versions, hashes, timestamps, device identity. */
    SYSTEM_RECORDED,

    /** The origin depends on the row's event kind; the variable description names both cases. */
    MIXED,
}

/** One variable in the research export, described well enough to analyse without reading the source. */
@Serializable
data class DictionaryVariable(
    val name: String,
    val dataset: DictionaryDataset,
    val type: String,
    /** Empty when the value is unitless. */
    val unit: String,
    /** Empty when the value is unconstrained. */
    val allowedValues: List<String>,
    val description: String,
    val provenance: VariableProvenance,
    val missingPolicy: String,
    /** The `TransformationRegistry` id that produced this, or null if nothing derived it. */
    val transformationId: String?,
)

/** The frozen, machine-readable description of a research export. */
@Serializable
data class DataDictionary(
    val version: String,
    val statement: String,
    val missingDataPolicyVersion: String,
    val missingDataStatement: String,
    val variables: List<DictionaryVariable>,
)

/**
 * The frozen data dictionary.
 *
 * "Frozen" is enforced three ways, not asserted:
 *
 *  1. `ResearchDataDictionaryTest` pins [sha256].
 *  2. A checked-in golden file makes any change reviewable as a diff.
 *  3. A reflection test asserts every field of every exported record has a
 *     variable here, so adding an export field without describing it fails
 *     the build.
 *
 * The version moves only with the export document it describes, which is
 * why [DataDictionary.version] reads [ContinuityContract.RESEARCH_DICTIONARY_VERSION]
 * rather than declaring one of its own.
 *
 * Nothing here interprets anything. Descriptions say what a value *is*,
 * never what it means about a person, and a test scans them for
 * clinical-interpretation vocabulary.
 */
object ResearchDataDictionary {

    /** Pinned: this configuration is part of [sha256], which the export carries. */
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = true
    }

    private const val TIMESTAMP = "timestamp_epoch_millis"
    private const val ISO_DATE = "iso_local_date"
    private const val TEXT = "string"
    private const val INTEGER = "integer"
    private const val NUMBER = "number"
    private const val ENUM = "enum"
    private const val JSON = "json"
    private const val SHA256 = "sha256_hex"

    private const val EPOCH_MILLIS = "milliseconds since the Unix epoch"
    private const val STRUCTURAL_CONTEXT = "structural-context"
    private val STRUCTURAL_CONTEXT_KEYS = listOf("entry_kind", "local_date", "word_count", "user_title")
    private const val STABLE_ID = "Stable identifier for this "
    private const val RECORDING_PHONE = "The phone that recorded the "

    private val RATING_1_TO_5 = listOf("1", "2", "3", "4", "5")

    val dictionary: DataDictionary by lazy {
        DataDictionary(
            version = ContinuityContract.RESEARCH_DICTIONARY_VERSION,
            statement = "Every variable a MindAnchor research export can contain. Frozen: a change to " +
                "this document requires a new version, and the old version stays readable forever.",
            missingDataPolicyVersion = MissingDataPolicy.VERSION,
            missingDataStatement = MissingDataPolicy.STATEMENT,
            variables = journalEntries() + journalContext() + morningMeasures() +
                ledgerEvents() + studyPhases() + missingData(),
        )
    }

    /** SHA-256 of this build's own [dictionary]. */
    val sha256: String by lazy { sha256Of(dictionary) }

    /**
     * SHA-256 of an arbitrary [DataDictionary].
     *
     * Exists so a *carried* dictionary — the one inside a file somebody
     * hands you — can be checked, rather than only the one this build
     * happens to hold. Without it, `dataDictionarySha256` in an export
     * would be a number nobody could recompute, and a rewritten dictionary
     * with a matching hash would read as authentic.
     */
    fun sha256Of(dictionary: DataDictionary): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalJsonOf(dictionary).encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }

    /** The dictionary as it appears in the golden file and in an export. */
    fun canonicalJson(): String = canonicalJsonOf(dictionary)

    private fun canonicalJsonOf(dictionary: DataDictionary): String = json.encodeToString(dictionary)

    /**
     * Builds the variables of one dataset. Holding the dataset here rather
     * than passing it to every call keeps each variable's declaration
     * short enough to read as a table.
     */
    private class Dataset(private val dataset: DictionaryDataset) {
        val variables = mutableListOf<DictionaryVariable>()

        @Suppress("LongParameterList")
        fun add(
            name: String,
            type: String,
            description: String,
            provenance: VariableProvenance,
            unit: String = "",
            allowedValues: List<String> = emptyList(),
            transformationId: String? = null,
        ) {
            variables += DictionaryVariable(
                name = name,
                dataset = dataset,
                type = type,
                unit = unit,
                allowedValues = allowedValues,
                description = description,
                provenance = provenance,
                missingPolicy = MissingDataPolicy.VERSION,
                transformationId = transformationId,
            )
        }
    }

    private fun dataset(of: DictionaryDataset, build: Dataset.() -> Unit): List<DictionaryVariable> =
        Dataset(of).apply(build).variables

    private fun journalEntries() = dataset(DictionaryDataset.JOURNAL_ENTRIES) {
        add("id", TEXT, "${STABLE_ID}entry.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "createdAt", TIMESTAMP, "When the entry was first saved.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
        add(
            "updatedAt", TIMESTAMP, "When the entry was last written.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
        add(
            "localDate", ISO_DATE,
            "The local calendar date the entry belongs to. The join key for daily analysis.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "title", TEXT, "The title the person typed. Empty if they typed none.",
            VariableProvenance.USER_AUTHORED,
        )
        add(
            "body", TEXT,
            "The entry in the person's own words, stored exactly as written. " +
                "Nothing reads meaning from it.",
            VariableProvenance.USER_AUTHORED,
        )
        add(
            "kind", ENUM, "Which writing surface produced the entry.", VariableProvenance.USER_REPORTED,
            allowedValues = JournalKind.entries.map { it.name },
        )
        add("sourceDeviceId", TEXT, "${RECORDING_PHONE}entry.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "deletedAt", TIMESTAMP,
            "When the person removed the entry, or null. The row is kept; content is never destroyed.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
    }

    private fun journalContext() = dataset(DictionaryDataset.JOURNAL_CONTEXT) {
        add(
            "id", TEXT, "${STABLE_ID}context row.", VariableProvenance.DERIVED_STRUCTURAL,
            transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "entryId", TEXT, "The Journal entry this was derived from.",
            VariableProvenance.DERIVED_STRUCTURAL, transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "recordType", ENUM,
            "FACT for a structural observation. INFERENCE exists in the schema but this build derives none.",
            VariableProvenance.DERIVED_STRUCTURAL,
            allowedValues = ContextRecordType.entries.map { it.name },
            transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "key", ENUM, "Which structural fact this row holds.", VariableProvenance.DERIVED_STRUCTURAL,
            allowedValues = STRUCTURAL_CONTEXT_KEYS,
            transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "value", TEXT, "The fact's value, as text.", VariableProvenance.DERIVED_STRUCTURAL,
            transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "sourceStart", INTEGER, "Start offset in the entry body, or null.",
            VariableProvenance.DERIVED_STRUCTURAL, unit = "characters", transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "sourceEnd", INTEGER, "End offset in the entry body, or null.",
            VariableProvenance.DERIVED_STRUCTURAL, unit = "characters", transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "confidence", NUMBER, "Always 1.0 in this build: a structural count is not a guess.",
            VariableProvenance.DERIVED_STRUCTURAL, transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "extractorVersion", TEXT, "The transformation version that produced the row.",
            VariableProvenance.DERIVED_STRUCTURAL, transformationId = STRUCTURAL_CONTEXT,
        )
        add(
            "createdAt", TIMESTAMP, "When the row was derived.", VariableProvenance.DERIVED_STRUCTURAL,
            unit = EPOCH_MILLIS, transformationId = STRUCTURAL_CONTEXT,
        )
    }

    private fun morningMeasures() = dataset(DictionaryDataset.MORNING_MEASURES) {
        fun rating(name: String, low: String, high: String) = add(
            name, INTEGER,
            "Self-rated 1 to 5, where 1 is \"$low\" and 5 is \"$high\". A personal research measure. " +
                "No total, threshold, or cut-off is computed from it anywhere.",
            VariableProvenance.USER_REPORTED, allowedValues = RATING_1_TO_5,
        )
        add("id", TEXT, "${STABLE_ID}measure.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "localDate", ISO_DATE,
            "The local calendar date the measure covers. One measure per date.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "createdAt", TIMESTAMP, "When the measure was first saved.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
        add(
            "updatedAt", TIMESTAMP, "When the measure was last edited.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
        rating("mood", "Low", "High")
        rating("anxiety", "Calm", "Tense")
        rating("angerUrge", "Steady", "Reactive")
        rating("energyFunction", "Depleted", "Energized")
        rating("sleepQuality", "Poor", "Great")
        add(
            "instrumentVersion", TEXT, "The version of the five-item measure that was answered.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add("sourceDeviceId", TEXT, "${RECORDING_PHONE}measure.", VariableProvenance.SYSTEM_RECORDED)
    }

    private fun ledgerEvents() = dataset(DictionaryDataset.RESEARCH_LEDGER_EVENTS) {
        add(
            "id", SHA256, "The event's own hash. The row is content-addressed.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "sequence", INTEGER, "Position in the chain, 1-based and contiguous.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "kind", ENUM,
            "What the event records. Its origin depends on event kind: chosen by the person for a " +
                "self-reported event and assigned by MindAnchor for a provenance event.",
            VariableProvenance.MIXED,
            allowedValues = LedgerEventKind.entries.map { it.name },
        )
        add(
            "occurredAt", TIMESTAMP,
            "When the recorded thing happened. Its origin depends on event kind: supplied by the person " +
                "for a self-reported event and assigned by MindAnchor for a provenance event.",
            VariableProvenance.MIXED, unit = EPOCH_MILLIS,
        )
        add(
            "recordedAt", TIMESTAMP, "When the row was written. Never rewritten.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
        add(
            "localDate", ISO_DATE,
            "The local calendar date of occurredAt. The join key for daily analysis.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "studyPhaseId", SHA256, "The study phase in effect when the row was written.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add("sourceDeviceId", TEXT, "The phone that wrote the row.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "note", TEXT,
            "Text whose origin depends on event kind: the person's exact words for a self-reported event, " +
                "and an empty value assigned by MindAnchor for a provenance event. Never interpreted.",
            VariableProvenance.MIXED,
        )
        add(
            "payloadJson", JSON,
            "Structured detail for an event MindAnchor wrote about itself. \"{}\" for a self-reported one.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "previousEventHash", SHA256, "The preceding event's hash. Empty at sequence 1.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "eventHash", SHA256, "This event's hash, over its own contents plus the previous hash.",
            VariableProvenance.SYSTEM_RECORDED,
        )
    }

    private fun studyPhases() = dataset(DictionaryDataset.STUDY_PHASES) {
        fun version(name: String, description: String) =
            add(name, TEXT, description, VariableProvenance.SYSTEM_RECORDED)
        add(
            "id", SHA256, "The phase's own hash over its ordinal, start and version vector.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "ordinal", INTEGER, "Position in the sequence of phases, 0-based.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "startedAt", TIMESTAMP,
            "When the phase opened. A phase has no end: it runs until the next one starts.",
            VariableProvenance.SYSTEM_RECORDED, unit = EPOCH_MILLIS,
        )
        add(
            "reason", ENUM, "Which part of the version vector changed to open this phase.",
            VariableProvenance.SYSTEM_RECORDED, allowedValues = StudyPhaseReason.entries.map { it.name },
        )
        add(
            "appVersionCode", INTEGER, "The app build in effect during the phase.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        version("appVersionName", "The app version name in effect during the phase.")
        version("protocolCatalogSha256", "Content hash of the evidence protocol catalogue in effect.")
        version("ruleSetVersion", "The decision-rule set in effect. This build ships none.")
        version("modelSetVersion", "The model set in effect. This build ships none.")
        version("transformationSetVersion", "Content hash of the transformation registry in effect.")
        version("missingDataPolicyVersion", "The missing-data policy in effect.")
        version("instrumentVersion", "The morning-measure instrument in effect.")
        version("dictionaryVersion", "The data dictionary in effect.")
        version("sourceDeviceId", "The phone the phase opened on.")
    }

    private fun missingData() = dataset(DictionaryDataset.MISSING_DATA) {
        add(
            "localDate", ISO_DATE, "The local calendar date the value is absent for.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "variable", ENUM, "Which variable is absent.", VariableProvenance.SYSTEM_RECORDED,
            allowedValues = listOf(
                MissingDataPolicy.VARIABLE_MORNING_MEASURE,
                MissingDataPolicy.VARIABLE_JOURNAL_CONTEXT,
            ),
        )
        add(
            "reason", ENUM, "Why it is absent. Nothing is filled in to replace it.",
            VariableProvenance.SYSTEM_RECORDED, allowedValues = MissingDataReason.entries.map { it.name },
        )
    }
}
