package org.mindanchor.research

import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.continuity.ContinuityContract
import org.mindanchor.continuity.JournalContextDto
import org.mindanchor.continuity.JournalEntryDto
import org.mindanchor.continuity.MorningMeasureDto
import org.mindanchor.data.db.ResearchLedgerEventEntity
import org.mindanchor.data.db.StudyPhaseEntity

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
            "mindanchor-research-v2" to "78b06ec1b69e85eff7a3fa6fe775af6f92d97b98fc937a5e3ddb0f4b7eacba98",
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
        assertEquals(VariableProvenance.USER_AUTHORED, note.provenance)

        ResearchDataDictionary.dictionary.variables
            .filter { it.dataset == DictionaryDataset.JOURNAL_CONTEXT }
            .forEach { assertEquals("structural-context", it.transformationId) }
    }

    @Test
    fun `every named transformation exists in the registry`() {
        ResearchDataDictionary.dictionary.variables.mapNotNull { it.transformationId }.distinct().forEach { id ->
            assertTrue("$id must be a registered transformation", TransformationRegistry.versionOf(id) != null)
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
