package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.MissingDataPolicy
import org.mindanchor.research.MissingDataReason
import org.mindanchor.research.MissingDataRecord
import org.mindanchor.research.ResearchDataDictionary
import org.mindanchor.research.TransformationRegistry

/**
 * Program 1 Task 11 — the research export is versioned, self-describing,
 * and independently verifiable.
 *
 * The two that matter most are the version-1 pair. A Program 0 export may
 * already exist on the owner's phone or in somebody else's hands; it has
 * to keep decoding and keep verifying, and its hash has to stay the value
 * Program 0 computed rather than whatever today's field set produces.
 */
class ResearchExportCodecTest {

    private fun entry(id: String, body: String = "Body") = JournalEntryDto(
        id = id,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        localDate = "2026-08-27",
        title = "Morning",
        body = body,
        kind = "DAILY",
        sourceDeviceId = "device-a",
        deletedAt = null,
    )

    private fun contextRow(id: String, recordType: String = "FACT") = JournalContextDto(
        id = id,
        entryId = "entry-1",
        recordType = recordType,
        key = "word_count",
        value = "1",
        sourceStart = null,
        sourceEnd = null,
        confidence = 1.0,
        extractorVersion = "structural-v1",
        createdAt = 1_000L,
    )

    private fun measure(id: String) = MorningMeasureDto(
        id = id,
        localDate = "2026-08-27",
        createdAt = 900L,
        updatedAt = 900L,
        mood = 3,
        anxiety = 2,
        angerUrge = 1,
        energyFunction = 4,
        sleepQuality = 3,
        instrumentVersion = "morning-v1",
        sourceDeviceId = "device-a",
    )

    private fun ledgerEvent(id: String, sequence: Long) = ResearchLedgerEventDto(
        id = id,
        sequence = sequence,
        kind = "EXERCISE",
        occurredAt = 1_000L,
        recordedAt = 1_000L,
        localDate = "2026-08-27",
        studyPhaseId = "phase-0",
        sourceDeviceId = "device-a",
        note = "a walk",
        payloadJson = "{}",
        previousEventHash = "",
        eventHash = id,
    )

    private fun phase(id: String, ordinal: Int) = StudyPhaseDto(
        id = id,
        ordinal = ordinal,
        startedAt = 900L,
        reason = "INITIAL",
        appVersionCode = 95,
        appVersionName = "0.71.0",
        protocolCatalogSha256 = "catalogue",
        ruleSetVersion = "rule-set-none-v1",
        modelSetVersion = "model-set-none-v1",
        transformationSetVersion = "transformations",
        missingDataPolicyVersion = "missing-data-v1",
        instrumentVersion = "morning-v1",
        dictionaryVersion = "mindanchor-research-v2",
        sourceDeviceId = "device-a",
    )

    /** A fully populated current-version export, sealed. */
    private fun sample(): ResearchExport = ResearchExportCodec.seal(
        ResearchExport(
            exportedAt = 5_000L,
            appVersionCode = 95,
            appVersionName = "0.71.0",
            contentSha256 = "",
            journalEntries = listOf(entry("entry-1")),
            contextFacts = listOf(contextRow("context-1")),
            contextInferences = emptyList(),
            morningMeasures = listOf(measure("measure-1")),
            ledgerEvents = listOf(ledgerEvent("event-1", 1L)),
            ledgerHeadHash = "event-1",
            ledgerEventCount = 1,
            ledgerIntegrity = LedgerIntegrity.VERIFIED,
            studyPhases = listOf(phase("phase-0", 0)),
            protocolRegistry = EvidenceProtocolCatalog.registry.protocols,
            protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            transformations = TransformationRegistry.transformations,
            transformationSetVersion = TransformationRegistry.setVersion,
            missingData = listOf(
                MissingDataRecord("2026-08-28", "morning_measure", MissingDataReason.NOT_RECORDED),
            ),
            missingDataPolicyVersion = MissingDataPolicy.VERSION,
            missingDataStatement = MissingDataPolicy.STATEMENT,
            dataDictionary = ResearchDataDictionary.dictionary,
            dataDictionarySha256 = ResearchDataDictionary.sha256,
        ),
    )

    /**
     * A document exactly as a Program 0 build wrote one: the four content
     * lists, the v1 version string, and the hash Program 0's own algorithm
     * produced over them.
     */
    private val programZeroDocument = """
        {
          "dataDictionaryVersion": "mindanchor-research-v1",
          "exportedAt": 5000,
          "appVersionCode": 95,
          "appVersionName": "0.70.0",
          "contentSha256": "PROGRAM_ZERO_HASH",
          "journalEntries": [
            {
              "id": "entry-1",
              "createdAt": 1000,
              "updatedAt": 1000,
              "localDate": "2026-08-27",
              "title": "Morning",
              "body": "Body",
              "kind": "DAILY",
              "sourceDeviceId": "device-a",
              "deletedAt": null
            }
          ],
          "contextFacts": [],
          "contextInferences": [],
          "morningMeasures": []
        }
    """.trimIndent()

    private fun programZeroExport(): ResearchExport {
        val unsealed = ResearchExport(
            dataDictionaryVersion = ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION,
            exportedAt = 5_000L,
            appVersionCode = 95,
            appVersionName = "0.70.0",
            contentSha256 = "",
            journalEntries = listOf(entry("entry-1")),
            contextFacts = emptyList(),
            contextInferences = emptyList(),
            morningMeasures = emptyList(),
        )
        return ResearchExportCodec.seal(unsealed)
    }

    @Test
    fun `a current export round trips every field`() {
        val original = sample()
        val decoded = ResearchExportCodec.decode(ResearchExportCodec.encode(original))
        assertTrue(decoded is ResearchExportCodec.DecodeResult.Success)
        assertEquals(original, (decoded as ResearchExportCodec.DecodeResult.Success).export)
    }

    @Test
    fun `a Program 0 document still decodes, with every Program 1 field empty`() {
        val hash = programZeroExport().contentSha256
        val document = programZeroDocument.replace("PROGRAM_ZERO_HASH", hash)

        val decoded = ResearchExportCodec.decode(document)
        assertTrue("$decoded", decoded is ResearchExportCodec.DecodeResult.Success)
        val export = (decoded as ResearchExportCodec.DecodeResult.Success).export

        assertEquals(ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION, export.dataDictionaryVersion)
        assertEquals(1, export.journalEntries.size)
        assertEquals(emptyList<ResearchLedgerEventDto>(), export.ledgerEvents)
        assertEquals(emptyList<StudyPhaseDto>(), export.studyPhases)
        assertEquals(LedgerIntegrity.NOT_APPLICABLE, export.ledgerIntegrity)
        assertNull(export.dataDictionary)
    }

    @Test
    fun `a Program 0 document still verifies`() {
        val hash = programZeroExport().contentSha256
        val document = programZeroDocument.replace("PROGRAM_ZERO_HASH", hash)
        val export = (ResearchExportCodec.decode(document) as ResearchExportCodec.DecodeResult.Success).export
        assertTrue("a Program 0 export must stay verifiable on this build", ResearchExportCodec.verify(export))
    }

    @Test
    fun `the Program 0 content hash is frozen`() {
        assertEquals(
            "96fa55e11383b408f2c4b2bfb91623170ce22a38eb15f726eabd17dd2e58f7e9",
            programZeroExport().contentSha256,
        )
    }

    @Test
    fun `the two versions of the same content disagree`() {
        val asProgramZero = programZeroExport()
        val asCurrent = ResearchExportCodec.seal(
            asProgramZero.copy(
                dataDictionaryVersion = ContinuityContract.RESEARCH_DICTIONARY_VERSION,
                contentSha256 = "",
            ),
        )
        assertNotEquals(asProgramZero.contentSha256, asCurrent.contentSha256)
    }

    @Test
    fun `a document from a version this build does not know is refused`() {
        val document = programZeroDocument
            .replace("PROGRAM_ZERO_HASH", "irrelevant")
            .replace("mindanchor-research-v1", "mindanchor-research-v99")
        val decoded = ResearchExportCodec.decode(document)
        assertTrue(decoded is ResearchExportCodec.DecodeResult.UnsupportedVersion)
        assertEquals(
            "mindanchor-research-v99",
            (decoded as ResearchExportCodec.DecodeResult.UnsupportedVersion).version,
        )
    }

    @Test
    fun `corrupt text is a typed result, not a thrown exception`() {
        assertEquals(ResearchExportCodec.DecodeResult.Corrupt, ResearchExportCodec.decode("not json at all"))
    }

    @Test
    fun `a single changed character breaks verification`() {
        val original = sample()
        assertTrue(ResearchExportCodec.verify(original))
        val tampered = original.copy(
            journalEntries = original.journalEntries.map { it.copy(body = "${it.body}.") },
        )
        assertFalse("an edited journal body must break the content hash", ResearchExportCodec.verify(tampered))
    }

    @Test
    fun `the content hash ignores when and where the export was taken`() {
        val original = sample()
        assertTrue(
            ResearchExportCodec.verify(
                original.copy(exportedAt = 9_999L, appVersionCode = 999, appVersionName = "later"),
            ),
        )
    }

    @Test
    fun `every content list is inside the hash`() {
        val original = sample()
        val mutations: List<Pair<String, ResearchExport>> = listOf(
            "journalEntries" to original.copy(journalEntries = original.journalEntries + entry("entry-2")),
            "contextFacts" to original.copy(contextFacts = original.contextFacts + contextRow("context-2")),
            "contextInferences" to original.copy(
                contextInferences = listOf(contextRow("context-3", recordType = "INFERENCE")),
            ),
            "morningMeasures" to original.copy(morningMeasures = original.morningMeasures + measure("measure-2")),
            "ledgerEvents" to original.copy(ledgerEvents = original.ledgerEvents + ledgerEvent("event-2", 2L)),
            "ledgerHeadHash" to original.copy(ledgerHeadHash = "somebody-elses-head"),
            "ledgerEventCount" to original.copy(ledgerEventCount = 99),
            "ledgerIntegrity" to original.copy(ledgerIntegrity = LedgerIntegrity.BROKEN),
            "studyPhases" to original.copy(studyPhases = original.studyPhases + phase("phase-1", 1)),
            "protocolCatalogSha256" to original.copy(protocolCatalogSha256 = "another-catalogue"),
            "transformationSetVersion" to original.copy(transformationSetVersion = "another-set"),
            "missingData" to original.copy(missingData = emptyList()),
        )
        mutations.forEach { (field, mutated) ->
            assertFalse("changing $field must break verification", ResearchExportCodec.verify(mutated))
        }
    }

    @Test
    fun `the dictionary travels beside the content hash, never inside it`() {
        val original = sample()
        assertEquals(ResearchDataDictionary.sha256, original.dataDictionarySha256)
        // Swapping the carried dictionary must not read as a data change.
        assertTrue(ResearchExportCodec.verify(original.copy(dataDictionary = null)))
    }

    @Test
    fun `sealing puts every list in canonical order`() {
        val shuffled = sample().let { sealed ->
            sealed.copy(
                journalEntries = sealed.journalEntries.reversed(),
                ledgerEvents = sealed.ledgerEvents.reversed(),
                studyPhases = sealed.studyPhases.reversed(),
            )
        }
        assertEquals(sample().contentSha256, ResearchExportCodec.seal(shuffled.copy(contentSha256 = "")).contentSha256)
    }

    @Test
    fun `a sealed export carries the current version and its own dictionary`() {
        val export = sample()
        assertEquals(ContinuityContract.RESEARCH_DICTIONARY_VERSION, export.dataDictionaryVersion)
        assertEquals(ResearchDataDictionary.dictionary, export.dataDictionary)
        assertEquals(EvidenceProtocolCatalog.registry.protocols, export.protocolRegistry)
        assertEquals(MissingDataPolicy.STATEMENT, export.missingDataStatement)
    }
}
