package org.mindanchor.research

import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.advisory.AdvisoryBuildMode
import org.mindanchor.advisory.EligibilityAttestedPayloadV1
import org.mindanchor.continuity.AdvisoryOpportunityDto
import org.mindanchor.continuity.ContinuityContract
import org.mindanchor.continuity.InterventionEpisodeEventDto
import org.mindanchor.continuity.JournalContextDto
import org.mindanchor.continuity.JournalEntryDto
import org.mindanchor.continuity.MorningMeasureDto
import org.mindanchor.continuity.PassiveBaselineSegmentDto
import org.mindanchor.continuity.PassiveDailyRevisionDto
import org.mindanchor.continuity.PassiveObservationDecisionDto
import org.mindanchor.continuity.PassivePipelineRunDto
import org.mindanchor.continuity.PassiveRawProvenanceDto
import org.mindanchor.continuity.PassiveSourceLagDto
import org.mindanchor.continuity.PassiveSourceReadDto
import org.mindanchor.continuity.PassiveWindowRevisionDto
import org.mindanchor.data.db.ResearchLedgerEventEntity
import org.mindanchor.data.db.StudyPhaseEntity
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.journal.JournalEntry
import org.mindanchor.journal.StructuralContextExtractor

/**
 * Program 1 Task 9 — the data dictionary is frozen, and the freeze is
 * enforced rather than asserted.
 *
 * The strongest test here is `every exported field has a dictionary entry`.
 * A dictionary that merely happens to be complete today is worth little; a
 * dictionary that makes the build fail when somebody adds an export field
 * without describing it is the thing a researcher can rely on.
 */
class ResearchDataDictionaryTest {

    /**
     * Named by version, so v2's document survives v3's arrival. A single
     * `data-dictionary.json` would have to be overwritten, and the version
     * it described would then exist nowhere — which is the opposite of
     * "the old version stays readable forever".
     */
    private val golden = File(
        "src/test/resources/research/data-dictionary-${ContinuityContract.RESEARCH_DICTIONARY_VERSION}.json",
    )

    private fun instanceFieldNames(type: Class<*>): List<String> = type.declaredFields
        .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
        .map { it.name }

    private fun namesIn(dataset: DictionaryDataset): List<String> =
        ResearchDataDictionary.dictionary.variables.filter { it.dataset == dataset }.map { it.name }

    @Test
    fun `the dictionary version tracks the export it describes`() {
        assertEquals(ContinuityContract.RESEARCH_DICTIONARY_VERSION, ResearchDataDictionary.dictionary.version)
        assertEquals(MissingDataPolicy.VERSION, ResearchDataDictionary.dictionary.missingDataPolicyVersion)
        assertEquals(MissingDataPolicy.STATEMENT, ResearchDataDictionary.dictionary.missingDataStatement)
    }

    @Test
    fun `the dictionary hash is frozen, keyed by version`() {
        // Keyed rather than a bare constant: bumping the version without
        // changing the content, or changing the content without bumping
        // the version, both have to be visible here rather than looking
        // like the same one-line re-pin.
        val frozen = mapOf(
            "mindanchor-research-v2" to "1cbfa2cf7552b675500583959511b8df069bf2aa932beeade1196ca6302393a9",
            "mindanchor-research-v3" to "4713050fc555021556d3f4fcb269dc4c03ded712bbc24cf7a9d3caa55901aaba",
            "mindanchor-research-v4" to "ffd008984df316e765ac97f38db489b4c176445170c5e798b35272f32364688f",
        )
        assertEquals(
            "the current dictionary version needs a frozen hash",
            true,
            ContinuityContract.RESEARCH_DICTIONARY_VERSION in frozen,
        )
        assertEquals(
            frozen.getValue(ContinuityContract.RESEARCH_DICTIONARY_VERSION),
            ResearchDataDictionary.sha256,
        )
    }

    @Test
    fun `the dictionary matches its golden file byte for byte`() {
        assertTrue("the golden dictionary must be readable", golden.isFile)
        assertEquals(golden.readText(Charsets.UTF_8).replace("\r\n", "\n"), ResearchDataDictionary.canonicalJson())
    }

    @Test
    fun `the hash is stable across calls`() {
        assertEquals(ResearchDataDictionary.sha256, ResearchDataDictionary.sha256)
        assertEquals(ResearchDataDictionary.canonicalJson(), ResearchDataDictionary.canonicalJson())
    }

    @Test
    fun `every variable is fully described`() {
        ResearchDataDictionary.dictionary.variables.forEach { v ->
            assertTrue("name", v.name.isNotBlank())
            assertTrue("type of ${v.name}", v.type.isNotBlank())
            assertTrue("description of ${v.name}", v.description.length > 15)
            assertEquals("missing policy of ${v.name}", MissingDataPolicy.VERSION, v.missingPolicy)
            if (v.type == "enum") {
                assertTrue("an enum variable needs allowed values: ${v.name}", v.allowedValues.isNotEmpty())
            }
        }
    }

    @Test
    fun `variable names are unique within a dataset`() {
        DictionaryDataset.entries.forEach { dataset ->
            val names = namesIn(dataset)
            assertEquals("duplicate variable in $dataset", names.distinct(), names)
            assertTrue("$dataset must describe something", names.isNotEmpty())
        }
    }

    @Test
    fun `every exported field has a dictionary entry`() {
        val coverage = mapOf(
            DictionaryDataset.JOURNAL_ENTRIES to JournalEntryDto::class.java,
            DictionaryDataset.JOURNAL_CONTEXT to JournalContextDto::class.java,
            DictionaryDataset.MORNING_MEASURES to MorningMeasureDto::class.java,
            DictionaryDataset.RESEARCH_LEDGER_EVENTS to ResearchLedgerEventEntity::class.java,
            DictionaryDataset.STUDY_PHASES to StudyPhaseEntity::class.java,
            DictionaryDataset.MISSING_DATA to MissingDataRecord::class.java,
            DictionaryDataset.PASSIVE_RAW_PROVENANCE to PassiveRawProvenanceDto::class.java,
            DictionaryDataset.PASSIVE_SOURCE_READS to PassiveSourceReadDto::class.java,
            DictionaryDataset.PASSIVE_SOURCE_LAGS to PassiveSourceLagDto::class.java,
            DictionaryDataset.PASSIVE_BASELINE_SEGMENTS to PassiveBaselineSegmentDto::class.java,
            DictionaryDataset.PASSIVE_PIPELINE_RUNS to PassivePipelineRunDto::class.java,
            DictionaryDataset.PASSIVE_WINDOW_REVISIONS to PassiveWindowRevisionDto::class.java,
            DictionaryDataset.PASSIVE_DAILY_REVISIONS to PassiveDailyRevisionDto::class.java,
            DictionaryDataset.PASSIVE_OBSERVATION_DECISIONS to PassiveObservationDecisionDto::class.java,
            DictionaryDataset.ADVISORY_OPPORTUNITIES to AdvisoryOpportunityDto::class.java,
            DictionaryDataset.INTERVENTION_EPISODE_EVENTS to InterventionEpisodeEventDto::class.java,
        )
        assertEquals(
            "every dataset must be covered",
            DictionaryDataset.entries.toSet(),
            coverage.keys,
        )
        coverage.forEach { (dataset, type) ->
            assertEquals(
                "${type.simpleName}'s fields must each have a $dataset dictionary entry",
                instanceFieldNames(type).sorted(),
                namesIn(dataset).sorted(),
            )
        }
    }

    @Test
    fun `passive variables follow provenance and transformation contracts`() {
        val byDataset = ResearchDataDictionary.dictionary.variables.groupBy { it.dataset }
        byDataset.getValue(DictionaryDataset.PASSIVE_RAW_PROVENANCE).forEach { variable ->
            assertEquals(VariableProvenance.SYSTEM_RECORDED, variable.provenance)
            assertEquals(null, variable.transformationId)
            assertTrue(variable.description.contains("raw measurement values are omitted", ignoreCase = true))
        }
        byDataset.getValue(DictionaryDataset.PASSIVE_WINDOW_REVISIONS).forEach { variable ->
            assertEquals(VariableProvenance.DERIVED_STRUCTURAL, variable.provenance)
            assertEquals("passive-window-features", variable.transformationId)
        }
        byDataset.getValue(DictionaryDataset.PASSIVE_DAILY_REVISIONS).forEach { variable ->
            assertEquals(VariableProvenance.DERIVED_STRUCTURAL, variable.provenance)
            assertEquals("passive-daily-features", variable.transformationId)
        }
        listOf(
            DictionaryDataset.PASSIVE_SOURCE_READS,
            DictionaryDataset.PASSIVE_SOURCE_LAGS,
            DictionaryDataset.PASSIVE_BASELINE_SEGMENTS,
            DictionaryDataset.PASSIVE_PIPELINE_RUNS,
        ).flatMap { byDataset.getValue(it) }.forEach { variable ->
            assertEquals(VariableProvenance.SYSTEM_RECORDED, variable.provenance)
            assertEquals(null, variable.transformationId)
        }
        val decisions = byDataset.getValue(DictionaryDataset.PASSIVE_OBSERVATION_DECISIONS).associateBy { it.name }
        decisions.values.forEach { variable ->
            assertEquals(VariableProvenance.DERIVED_STRUCTURAL, variable.provenance)
        }
        assertEquals("passive-block-calibration", decisions.getValue("calibrationSeed").transformationId)
        assertEquals("passive-personal-baseline", decisions.getValue("frozenBaselineAsOfTime").transformationId)
        assertEquals("passive-personal-baseline", decisions.getValue("frozenBaselineThroughDay").transformationId)
        assertEquals("passive-observation-explanation", decisions.getValue("decisionJson").transformationId)
        (decisions.keys - setOf(
            "calibrationSeed", "frozenBaselineAsOfTime", "frozenBaselineThroughDay", "decisionJson",
        )).forEach { name -> assertEquals(null, decisions.getValue(name).transformationId) }
    }

    @Test
    fun `passive units and allowed values are explicit`() {
        val passive = ResearchDataDictionary.dictionary.variables.filter { it.dataset.name.startsWith("PASSIVE_") }
        passive.filter { it.type == "timestamp_epoch_millis" }.forEach {
            assertEquals("unit of ${it.dataset}.${it.name}", "milliseconds since the Unix epoch", it.unit)
        }
        passive.filter { it.type == "boolean" }.forEach {
            assertEquals("unit of ${it.dataset}.${it.name}", "boolean", it.unit)
            assertEquals("allowed values of ${it.dataset}.${it.name}", listOf("false", "true"), it.allowedValues)
        }
        passive.filter { it.type == "enum" }.forEach {
            assertTrue("allowed values of ${it.dataset}.${it.name}", it.allowedValues.isNotEmpty())
        }
        assertEquals(
            "seconds offset from UTC",
            passive.single {
                it.dataset == DictionaryDataset.PASSIVE_RAW_PROVENANCE && it.name == "zoneOffsetSeconds"
            }.unit,
        )
        assertEquals(
            "fraction from 0.0 through 1.0",
            passive.single {
                it.dataset == DictionaryDataset.PASSIVE_WINDOW_REVISIONS && it.name == "heartRateCoverage"
            }.unit,
        )
    }

    @Test
    fun `the morning measure is described as the personal measure it is`() {
        val ratings = listOf("mood", "anxiety", "angerUrge", "energyFunction", "sleepQuality")
        ratings.forEach { name ->
            val variable = ResearchDataDictionary.dictionary.variables
                .single { it.dataset == DictionaryDataset.MORNING_MEASURES && it.name == name }
            assertEquals(listOf("1", "2", "3", "4", "5"), variable.allowedValues)
            assertEquals(VariableProvenance.USER_REPORTED, variable.provenance)
            assertTrue("$name must disclaim a threshold", variable.description.contains("No total, threshold"))
        }
        assertTrue(
            "the instrument version must be described, so a future instrument change is legible",
            ResearchDataDictionary.dictionary.variables.any {
                it.dataset == DictionaryDataset.MORNING_MEASURES && it.name == "instrumentVersion"
            },
        )
    }

    @Test
    fun `authorship is kept separate from anything derived`() {
        val body = ResearchDataDictionary.dictionary.variables
            .single { it.dataset == DictionaryDataset.JOURNAL_ENTRIES && it.name == "body" }
        assertEquals(VariableProvenance.USER_AUTHORED, body.provenance)
        assertEquals(null, body.transformationId)

        val note = ResearchDataDictionary.dictionary.variables
            .single { it.dataset == DictionaryDataset.RESEARCH_LEDGER_EVENTS && it.name == "note" }
        assertEquals(VariableProvenance.MIXED, note.provenance)

        ResearchDataDictionary.dictionary.variables
            .filter { it.dataset == DictionaryDataset.JOURNAL_CONTEXT }
            .forEach {
                assertEquals(VariableProvenance.DERIVED_STRUCTURAL, it.provenance)
                assertEquals("structural-context", it.transformationId)
            }
    }

    @Test
    fun `mixed-origin ledger fields say their origin depends on event kind`() {
        val mixed = setOf("kind", "occurredAt", "note")
        val ledger = ResearchDataDictionary.dictionary.variables
            .filter { it.dataset == DictionaryDataset.RESEARCH_LEDGER_EVENTS }
            .associateBy { it.name }

        mixed.forEach { name ->
            assertEquals(VariableProvenance.MIXED, ledger.getValue(name).provenance)
            assertTrue(ledger.getValue(name).description.contains("event kind", ignoreCase = true))
        }
    }

    @Test
    fun `every named transformation exists in the registry`() {
        ResearchDataDictionary.dictionary.variables.mapNotNull { it.transformationId }.distinct().forEach { id ->
            assertTrue("$id must be a registered transformation", TransformationRegistry.versionOf(id) != null)
        }
    }

    @Test
    fun `the closed context key list matches what the unchanged extractor emits`() {
        val entry = JournalEntry.create(
            title = "A title",
            body = "two words",
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 29),
            sourceDeviceId = "device-a",
        )
        val emitted = StructuralContextExtractor().extract(entry, now = 1_000L).map { it.key }
        val declared = ResearchDataDictionary.dictionary.variables.single {
            it.dataset == DictionaryDataset.JOURNAL_CONTEXT && it.name == "key"
        }.allowedValues

        assertEquals(emitted, declared)
    }

    @Test
    fun `advisory opportunity and episode event variables never claim a current or diagnostic state`() {
        val advisory = ResearchDataDictionary.dictionary.variables.filter {
            it.dataset == DictionaryDataset.ADVISORY_OPPORTUNITIES ||
                it.dataset == DictionaryDataset.INTERVENTION_EPISODE_EVENTS
        }
        assertTrue(advisory.isNotEmpty())
        val sourceDataStatus = advisory.single { it.name == "sourceDataStatus" }
        assertEquals(listOf(PassiveDataStatus.AVAILABLE_FINAL.name), sourceDataStatus.allowedValues)
        val sourceObservationState = advisory.single { it.name == "sourceObservationState" }
        assertEquals(listOf(PassiveObservationState.SUSTAINED_DEVIATION.name), sourceObservationState.allowedValues)
        val buildModes = listOf(
            advisory.single { it.dataset == DictionaryDataset.ADVISORY_OPPORTUNITIES && it.name == "buildMode" },
            advisory.single { it.dataset == DictionaryDataset.INTERVENTION_EPISODE_EVENTS && it.name == "buildMode" },
        )
        buildModes.forEach { variable ->
            assertEquals(AdvisoryBuildMode.entries.map { it.name }, variable.allowedValues)
        }
    }

    /**
     * The plan's "four attestation Booleans" -- [EligibilityAttestedPayloadV1]'s
     * fields -- live inside `payloadJson`, a JSON blob whose shape depends
     * on `eventType`. This dictionary describes one variable per DTO
     * field (the same convention every other JSON-blob field here follows:
     * `decisionJson`, the research ledger's own `payloadJson`), so there is
     * no separate per-subfield `DictionaryVariable` to give
     * `VariableProvenance.SELF_REPORTED` to directly. Instead this asserts
     * the documented claim itself: every one of those four facts is named
     * by `payloadJson`'s description and stated there as self-reported.
     */
    @Test
    fun `the four eligibility-attestation facts are documented as self-reported in payloadJson`() {
        val payloadJson = ResearchDataDictionary.dictionary.variables.single {
            it.dataset == DictionaryDataset.INTERVENTION_EPISODE_EVENTS && it.name == "payloadJson"
        }
        assertEquals(VariableProvenance.MIXED, payloadJson.provenance)
        assertTrue(payloadJson.description.contains("self-reported", ignoreCase = true))
        val attestationFields = EligibilityAttestedPayloadV1::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertEquals(4, attestationFields.size)
        attestationFields.forEach { name ->
            assertTrue(
                "payloadJson's description must name the self-reported fact `$name`",
                payloadJson.description.contains(name),
            )
        }
    }

    @Test
    fun `no description reads meaning into a person`() {
        val clinical = listOf("diagnos", "disorder", "severity", "clinically", "symptom score", "patholog")
        ResearchDataDictionary.dictionary.variables.forEach { v ->
            val lowered = v.description.lowercase()
            clinical.forEach { word ->
                assertFalse("${v.name} must not say '$word': ${v.description}", lowered.contains(word))
            }
        }
    }
}
