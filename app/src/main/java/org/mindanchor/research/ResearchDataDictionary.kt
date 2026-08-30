package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.continuity.ContinuityContract
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.intelligence.PassiveReadState
import org.mindanchor.intelligence.PassiveRecordKind
import org.mindanchor.intelligence.PassiveSourceFamily
import org.mindanchor.intelligence.RevisionReason
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
    PASSIVE_RAW_PROVENANCE,
    PASSIVE_SOURCE_READS,
    PASSIVE_SOURCE_LAGS,
    PASSIVE_BASELINE_SEGMENTS,
    PASSIVE_PIPELINE_RUNS,
    PASSIVE_WINDOW_REVISIONS,
    PASSIVE_DAILY_REVISIONS,
    PASSIVE_OBSERVATION_DECISIONS,
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
@OptIn(ExperimentalSerializationApi::class)
@Suppress("TooManyFunctions")
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
    private const val BOOLEAN = "boolean"

    private const val EPOCH_MILLIS = "milliseconds since the Unix epoch"
    private const val STRUCTURAL_CONTEXT = "structural-context"
    private val STRUCTURAL_CONTEXT_KEYS = listOf("entry_kind", "local_date", "word_count", "user_title")
    private const val STABLE_ID = "Stable identifier for this "
    private const val RECORDING_PHONE = "The phone that recorded the "
    private const val PASSIVE_WINDOW = "passive-window-features"
    private const val PASSIVE_DAILY = "passive-daily-features"
    private const val PASSIVE_BASELINE = "passive-personal-baseline"
    private const val PASSIVE_CALIBRATION = "passive-block-calibration"
    private const val PASSIVE_EXPLANATION = "passive-observation-explanation"
    private const val FRACTION = "fraction from 0.0 through 1.0"
    private const val MILLISECONDS = "milliseconds"
    private const val SECONDS_OFFSET = "seconds offset from UTC"
    private const val UNITLESS_COUNT = "unitless count"
    private const val BOOLEAN_UNIT = "boolean"
    private const val LOCAL_DATE = "localDate"

    private val RATING_1_TO_5 = listOf("1", "2", "3", "4", "5")
    private val BOOLEAN_VALUES = listOf("false", "true")

    val dictionary: DataDictionary by lazy {
        DataDictionary(
            version = ContinuityContract.RESEARCH_DICTIONARY_VERSION,
            statement = "Every variable a MindAnchor research export can contain. Frozen: a change to " +
                "this document requires a new version, and the old version stays readable forever.",
            missingDataPolicyVersion = MissingDataPolicy.VERSION,
            missingDataStatement = MissingDataPolicy.STATEMENT,
            variables = journalEntries() + journalContext() + morningMeasures() +
                ledgerEvents() + studyPhases() + missingData() + passiveRawProvenance() +
                passiveSourceReads() + passiveSourceLags() + passiveBaselineSegments() +
                passivePipelineRuns() + passiveWindowRevisions() + passiveDailyRevisions() +
                passiveObservationDecisions(),
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
            LOCAL_DATE, ISO_DATE,
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
            LOCAL_DATE, ISO_DATE,
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
            LOCAL_DATE, ISO_DATE,
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
            LOCAL_DATE, ISO_DATE, "The local calendar date the value is absent for.",
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

    private fun passiveRawProvenance() = dataset(DictionaryDataset.PASSIVE_RAW_PROVENANCE) {
        val omitted = " Raw measurement values are omitted from this provenance-only dataset."
        fun recorded(
            name: String,
            type: String,
            description: String,
            unit: String = "",
            allowedValues: List<String> = emptyList(),
        ) = add(
            name, type, description + omitted, VariableProvenance.SYSTEM_RECORDED,
            unit = unit, allowedValues = allowedValues,
        )
        recorded("id", SHA256, "Content-derived stable identifier for the provenance row.")
        recorded(
            "sourceFamily", ENUM, "The configured source family that supplied the record.",
            allowedValues = PassiveSourceFamily.entries.map { it.name },
        )
        recorded(
            "recordKind", ENUM, "The normalized kind of source record.",
            allowedValues = PassiveRecordKind.entries.map { it.name },
        )
        recorded("eventStart", TIMESTAMP, "Inclusive start of the source event.", EPOCH_MILLIS)
        recorded("eventEnd", TIMESTAMP, "End of the source event.", EPOCH_MILLIS)
        recorded("unit", TEXT, "The source measurement unit label retained without its measurement.")
        recorded("dataOriginPackage", TEXT, "Android package name reported as the data origin.")
        recorded("deviceManufacturer", TEXT, "Source device manufacturer, or null when unavailable.")
        recorded("deviceModel", TEXT, "Source device model, or null when unavailable.")
        recorded("deviceType", TEXT, "Source device type label, or null when unavailable.")
        recorded(
            "sourceUpdatedTime", TIMESTAMP, "When the source last reported updating the record, or null.",
            EPOCH_MILLIS,
        )
        recorded("ingestedAt", TIMESTAMP, "When MindAnchor read the source record.", EPOCH_MILLIS)
        recorded("zoneId", TEXT, "IANA zone identifier recorded with the source event.")
        recorded("zoneOffsetSeconds", INTEGER, "UTC offset recorded with the source event.", SECONDS_OFFSET)
        recorded("recordId", TEXT, "Identifier supplied by the source, or a deterministic fallback identifier.")
        recorded("recordVersion", INTEGER, "Version number supplied for the source record.", UNITLESS_COUNT)
    }

    private fun passiveSourceReads() = dataset(DictionaryDataset.PASSIVE_SOURCE_READS) {
        add("id", SHA256, "Stable identifier for this source-read outcome.", VariableProvenance.SYSTEM_RECORDED)
        add("runId", SHA256, "Pipeline run that performed this read.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "sourceFamily", ENUM, "Source family that was read.", VariableProvenance.SYSTEM_RECORDED,
            allowedValues = PassiveSourceFamily.entries.map { it.name },
        )
        add(
            "state", ENUM, "Availability or failure outcome of the read.", VariableProvenance.SYSTEM_RECORDED,
            allowedValues = PassiveReadState.entries.map { it.name },
        )
        add(
            "rangeStart", TIMESTAMP, "Inclusive requested range start.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "rangeEnd", TIMESTAMP, "Exclusive requested range end.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add("zoneId", TEXT, "IANA zone identifier used for the requested range.", VariableProvenance.SYSTEM_RECORDED)
        add("attemptedAt", TIMESTAMP, "When the read was attempted.", VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS)
        add(
            "recordCount", INTEGER, "Number of normalized records returned by a successful read.",
            VariableProvenance.SYSTEM_RECORDED, UNITLESS_COUNT,
        )
        add(
            "errorCode", TEXT, "Stable provider error category, or null for a successful read.",
            VariableProvenance.SYSTEM_RECORDED,
        )
    }

    private fun passiveSourceLags() = dataset(DictionaryDataset.PASSIVE_SOURCE_LAGS) {
        add("id", SHA256, "Stable identifier for this observed source-lag row.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "sourceFamily", ENUM, "Source family whose lag was observed.", VariableProvenance.SYSTEM_RECORDED,
            allowedValues = PassiveSourceFamily.entries.map { it.name },
        )
        add("eventEnd", TIMESTAMP, "End time of the source event.", VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS)
        add(
            "observedUpdatedAt", TIMESTAMP, "Source-updated time used for lag calculation.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "ingestedAt", TIMESTAMP, "When MindAnchor ingested the record.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "lagMillis", INTEGER, "Observed arrival lag for finality calculation.",
            VariableProvenance.SYSTEM_RECORDED, MILLISECONDS,
        )
        add(
            "usedIngestedAtFallback", BOOLEAN,
            "True when ingestion time replaced an unavailable source-updated time.",
            VariableProvenance.SYSTEM_RECORDED, BOOLEAN_UNIT, BOOLEAN_VALUES,
        )
        add(
            "observedAt", TIMESTAMP, "When this lag evidence was recorded.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
    }

    private fun passiveBaselineSegments() = dataset(DictionaryDataset.PASSIVE_BASELINE_SEGMENTS) {
        add(
            "id", SHA256, "Hash of configured source/device fingerprints and transformation versions.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "openedAt", TIMESTAMP, "When this immutable baseline segment opened.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "fingerprintsJson", JSON, "Canonical configured source and device fingerprints.",
            VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "windowTransformationVersion", TEXT,
            "Version identifying the window transformation for this segment.", VariableProvenance.SYSTEM_RECORDED,
        )
        add(
            "dailyTransformationVersion", TEXT,
            "Version identifying the daily transformation for this segment.", VariableProvenance.SYSTEM_RECORDED,
        )
    }

    private fun passivePipelineRuns() = dataset(DictionaryDataset.PASSIVE_PIPELINE_RUNS) {
        val runResults = listOf(
            "SUCCESS_PERMISSIONED", "SUCCESS_NO_PERMISSION", "SUCCESS_WITH_FAILURES", "RETRY_TRANSIENT",
        )
        add("id", SHA256, "Stable identifier for this collection run.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "startedAt", TIMESTAMP, "When the collection run started.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "completedAt", TIMESTAMP, "When the collection run completed.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "scanStart", TIMESTAMP, "Inclusive start of the scanned interval.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add(
            "scanEnd", TIMESTAMP, "Exclusive end of the scanned interval.",
            VariableProvenance.SYSTEM_RECORDED, EPOCH_MILLIS,
        )
        add("zoneId", TEXT, "IANA zone identifier used for local-day alignment.", VariableProvenance.SYSTEM_RECORDED)
        add(
            "historyPermissionGranted", BOOLEAN, "Whether extended Health Connect history permission was available.",
            VariableProvenance.SYSTEM_RECORDED, BOOLEAN_UNIT, BOOLEAN_VALUES,
        )
        add(
            "firstSuccessfulPermissionedRun", BOOLEAN,
            "Whether this was the first successful collection with source permission.",
            VariableProvenance.SYSTEM_RECORDED, BOOLEAN_UNIT, BOOLEAN_VALUES,
        )
        add(
            "result", ENUM, "Persisted collection-run outcome.", VariableProvenance.SYSTEM_RECORDED,
            allowedValues = runResults,
        )
        add(
            "sourceStatesJson", JSON, "Per-source availability and failure states for the run.",
            VariableProvenance.SYSTEM_RECORDED,
        )
    }

    private fun passiveWindowRevisions() = dataset(DictionaryDataset.PASSIVE_WINDOW_REVISIONS) {
        fun derived(
            name: String,
            type: String,
            description: String,
            unit: String = "",
            allowedValues: List<String> = emptyList(),
        ) = add(
            name, type, description, VariableProvenance.DERIVED_STRUCTURAL,
            unit = unit, allowedValues = allowedValues, transformationId = PASSIVE_WINDOW,
        )
        derived("id", SHA256, "Stable identifier for this immutable window revision.")
        derived("windowStart", TIMESTAMP, "Inclusive start of the absolute UTC window.", EPOCH_MILLIS)
        derived("windowEnd", TIMESTAMP, "Exclusive end of the absolute UTC window.", EPOCH_MILLIS)
        derived("asOfTime", TIMESTAMP, "Point-in-time cutoff used for this revision.", EPOCH_MILLIS)
        derived("zoneId", TEXT, "IANA zone identifier retained for presentation and alignment.")
        derived("zoneOffsetSeconds", INTEGER, "UTC offset retained for presentation and alignment.", SECONDS_OFFSET)
        derived("wakeRelativeMinute", INTEGER, "Minute relative to wake time, or null when unavailable.", "minutes")
        derived("baselineSegment", SHA256, "Configured source/device segment used for this revision.")
        derived(
            "featureRowsJson", JSON,
            "Canonical feature rows with source unit, coverage, eligibility and observation-only exclusions.",
        )
        derived(
            "heartRateCoverage", NUMBER, "Distinct observed heart-rate minute bins divided by fifteen.",
            FRACTION, listOf("0.0..1.0"),
        )
        derived(
            "physiologyEligible", BOOLEAN, "Whether physiology met coverage and exercise rules.",
            BOOLEAN_UNIT, BOOLEAN_VALUES,
        )
        derived("exerciseOverlapMillis", INTEGER, "Exercise overlap inside the window.", MILLISECONDS)
        derived("provenanceRecordIdsJson", JSON, "Canonical source-record identifiers contributing to the window.")
        derived("missingnessJson", JSON, "Canonical list of unavailable window features; none are filled.")
        derived("exclusionsJson", JSON, "Canonical observation-only reasons features were ineligible.")
        derived("transformationVersion", TEXT, "Window transformation version that produced the row.")
        derived("sourceUpdatedTime", TIMESTAMP, "Newest contributing source update time.", EPOCH_MILLIS)
        derived("ingestedAt", TIMESTAMP, "Newest contributing ingestion time.", EPOCH_MILLIS)
        derived(
            "final", BOOLEAN, "Whether the source watermark made this window final.",
            BOOLEAN_UNIT, BOOLEAN_VALUES,
        )
        derived(
            "revisionReason", ENUM, "Why this immutable revision was appended.",
            allowedValues = RevisionReason.entries.map { it.name },
        )
        derived("contentHash", SHA256, "Hash of the canonical window-revision content.")
    }

    private fun passiveDailyRevisions() = dataset(DictionaryDataset.PASSIVE_DAILY_REVISIONS) {
        fun derived(
            name: String,
            type: String,
            description: String,
            unit: String = "",
            allowedValues: List<String> = emptyList(),
        ) = add(
            name, type, description, VariableProvenance.DERIVED_STRUCTURAL,
            unit = unit, allowedValues = allowedValues, transformationId = PASSIVE_DAILY,
        )
        derived("id", SHA256, "Stable identifier for this immutable daily revision.")
        derived(LOCAL_DATE, ISO_DATE, "Local calendar date represented by the daily revision.")
        derived("asOfTime", TIMESTAMP, "Point-in-time cutoff used for this daily revision.", EPOCH_MILLIS)
        derived(
            "dataStatus", ENUM, "Availability, finality or insufficiency state of the day.",
            allowedValues = PassiveDataStatus.entries.map { it.name },
        )
        derived("featuresJson", JSON, "Canonical eligible daily feature names, values and units.")
        derived("excludedFeaturesJson", JSON, "Canonical features excluded from observation scoring.")
        derived("baselineSegment", SHA256, "Configured source/device segment used for the day.")
        derived("sourceUpdatedTime", TIMESTAMP, "Newest source update represented by the day.", EPOCH_MILLIS)
        derived("ingestedAt", TIMESTAMP, "Newest ingestion time represented by the day.", EPOCH_MILLIS)
        derived("sourceReadStatesJson", JSON, "Per-source availability and failure states represented by the day.")
        derived("coverageJson", JSON, "Per-feature coverage fractions from 0.0 through 1.0.")
        derived("missingnessJson", JSON, "Canonical unavailable daily features; none are filled.")
        derived("exclusionsJson", JSON, "Canonical observation-only reasons daily features were ineligible.")
        derived("provenanceJson", JSON, "Canonical source-record identifiers contributing to the day.")
        derived("windowTransformationVersion", TEXT, "Window transformation version used by this day.")
        derived("dailyTransformationVersion", TEXT, "Daily transformation version that produced this row.")
        derived("watermark", TIMESTAMP, "Source-lag watermark used to decide finality.", EPOCH_MILLIS)
        derived(
            "revisionReason", ENUM, "Why this immutable daily revision was appended.",
            allowedValues = RevisionReason.entries.map { it.name },
        )
        derived("contentHash", SHA256, "Hash of the canonical daily-revision content.")
    }

    private fun passiveObservationDecisions() = dataset(DictionaryDataset.PASSIVE_OBSERVATION_DECISIONS) {
        fun decision(
            name: String,
            type: String,
            description: String,
            unit: String = "",
            allowedValues: List<String> = emptyList(),
            transformationId: String? = null,
        ) = add(
            name, type, description, VariableProvenance.DERIVED_STRUCTURAL,
            unit = unit, allowedValues = allowedValues, transformationId = transformationId,
        )
        decision("id", SHA256, "Stable identifier for this immutable observation decision.")
        decision(LOCAL_DATE, ISO_DATE, "Local calendar date assessed by the observation system.")
        decision("asOfTime", TIMESTAMP, "Point-in-time cutoff used for this decision.", EPOCH_MILLIS)
        decision(
            "dataStatus", ENUM, "Availability or finality state carried into the decision.",
            allowedValues = PassiveDataStatus.entries.map { it.name },
        )
        decision(
            "observationState", ENUM, "Observation-only state; it is not an intervention instruction.",
            allowedValues = PassiveObservationState.entries.map { it.name },
        )
        decision("baselineSegment", SHA256, "Configured source/device segment used for comparison.")
        decision(
            "calibrationSeed", INTEGER, "Deterministic signed seed used for block calibration, or null.",
            UNITLESS_COUNT, transformationId = PASSIVE_CALIBRATION,
        )
        decision(
            "frozenBaselineAsOfTime", TIMESTAMP, "Immutable baseline eligibility cutoff, or null.",
            EPOCH_MILLIS, transformationId = PASSIVE_BASELINE,
        )
        decision(
            "frozenBaselineThroughDay", ISO_DATE, "Last local date in the frozen baseline, or null.",
            transformationId = PASSIVE_BASELINE,
        )
        decision(
            "decisionJson", JSON,
            "Canonical observation-only evidence, calibration configuration, baseline comparison and explanation.",
            transformationId = PASSIVE_EXPLANATION,
        )
        decision(
            "revisionReason", ENUM, "Why this immutable decision revision was appended.",
            allowedValues = RevisionReason.entries.map { it.name },
        )
        decision("contentHash", SHA256, "Hash of the canonical observation-decision content.")
    }
}
