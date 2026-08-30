package org.mindanchor.continuity

import java.lang.reflect.Modifier
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
            ledgerHighWaterCount = 1,
            ledgerIntegrity = LedgerIntegrity.VERIFIED,
            studyPhases = listOf(phase("phase-0", 0)),
            protocolRegistry = EvidenceProtocolCatalog.registry.protocols,
            protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            transformations = TransformationRegistry.transformations,
            transformationSetVersion = TransformationRegistry.setVersion,
            missingData = listOf(
                MissingDataRecord("2026-08-28", "morning_measure", MissingDataReason.NOT_RECORDED),
            ),
            missingDataWindowStart = "2026-08-27",
            missingDataWindowThrough = "2026-08-29",
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

    /**
     * Fields that describe the export rather than its content, and are
     * therefore outside the content hash on purpose.
     *
     * `dataDictionary` is excluded so that bumping the dictionary does not
     * read as a data change; it is made tamper-evident a different way, by
     * `verify` recomputing `dataDictionarySha256` against it.
     */
    private val outsideTheContentHash = setOf(
        "dataDictionaryVersion",
        "exportedAt",
        "appVersionCode",
        "appVersionName",
        "contentSha256",
        "dataDictionary",
    )

    /** One mutation per content field, each of which must break verification. */
    private fun contentMutations(original: ResearchExport): Map<String, ResearchExport> = mapOf(
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
        "protocolRegistry" to original.copy(protocolRegistry = emptyList()),
        "protocolCatalogSha256" to original.copy(protocolCatalogSha256 = "another-catalogue"),
        "transformations" to original.copy(
            transformations = original.transformations.map { it.copy(description = "the opposite") },
        ),
        "transformationSetVersion" to original.copy(transformationSetVersion = "another-set"),
        "missingData" to original.copy(missingData = emptyList()),
        "missingDataWindowStart" to original.copy(missingDataWindowStart = "1999-01-01"),
        // Null, not another date: an export claiming it reported on no
        // window at all, while carrying a full report, must not verify.
        "missingDataWindowThrough" to original.copy(missingDataWindowThrough = null),
        "missingDataPolicyVersion" to original.copy(missingDataPolicyVersion = "missing-data-v9"),
        "missingDataStatement" to original.copy(
            missingDataStatement = "Absences are carried forward from the previous day.",
        ),
        "dataDictionarySha256" to original.copy(dataDictionarySha256 = "a different dictionary"),
        "ledgerHighWaterCount" to original.copy(ledgerHighWaterCount = 4096),
    )

    @Test
    fun `every content field is inside the hash`() {
        val original = sample()
        contentMutations(original).forEach { (field, mutated) ->
            assertFalse("changing $field must break verification", ResearchExportCodec.verify(mutated))
        }
    }

    @Test
    fun `no export field escapes both the content hash and the exclusion list`() {
        // The previous version of this test listed exactly the fields the
        // projection already covered, so it restated the implementation
        // rather than checking it: it passed unchanged while the projection
        // quietly omitted five fields, which is what happened. Reflecting
        // over the declared fields is what makes a newly added export field
        // fail the build until somebody decides whether it is content.
        val declared = ResearchExport::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(
            "a field was added to ResearchExport without deciding whether it is content: " +
                "give it a mutation in `contentMutations`, or name it in `outsideTheContentHash`",
            declared,
            contentMutations(sample()).keys + outsideTheContentHash,
        )
    }

    @Test
    fun `a rewritten transformation description breaks verification`() {
        // The transformation set version hashes id@version only, by design,
        // so descriptions are not covered by it. One of those descriptions
        // is the file's own statement that MindAnchor reads no meaning from
        // journal text; outside the content hash, it could be inverted in
        // an exported file that still verified.
        val original = sample()
        val inverted = original.copy(
            transformations = original.transformations.map {
                it.copy(description = "Derives sentiment and clinical interpretation from the body text.")
            },
        )
        assertEquals(original.transformationSetVersion, inverted.transformationSetVersion)
        assertFalse(ResearchExportCodec.verify(inverted))
    }

    @Test
    fun `a rewritten carried dictionary breaks verification`() {
        val original = sample()
        val dictionary = requireNotNull(original.dataDictionary)
        val rewritten = dictionary.copy(
            variables = dictionary.variables.map { variable ->
                if (variable.name == "mood") variable.copy(description = "A clinical severity score.") else variable
            },
        )
        // The forger updates the stated hash too, which is the point:
        // without recomputing it against the carried document, that would
        // read as authentic.
        val forged = original.copy(
            dataDictionary = rewritten,
            dataDictionarySha256 = ResearchDataDictionary.sha256Of(rewritten),
        )
        assertFalse("a rewritten dictionary must not verify", ResearchExportCodec.verify(forged))
    }

    @Test
    fun `the dictionary payload is outside the content hash but its digest is inside`() {
        val original = sample()
        val dictionary = requireNotNull(original.dataDictionary)
        assertEquals(ResearchDataDictionary.sha256, original.dataDictionarySha256)
        val rewritten = dictionary.copy(statement = "rewritten payload")
        val resealed = ResearchExportCodec.seal(
            original.copy(contentSha256 = "", dataDictionary = rewritten),
        )

        assertEquals(
            "only the carried digest, not the repeated dictionary payload, is projected",
            original.contentSha256,
            resealed.contentSha256,
        )
        assertFalse(
            "verification must still reject a payload that disagrees with its digest",
            ResearchExportCodec.verify(resealed),
        )
    }

    @Test
    fun `a version 2 document with its dictionary stripped out does not verify`() {
        // This previously passed with the dictionary removed, which made
        // the file's whole "still readable in five years" claim optional.
        // It is the same argument that carries `protocolRegistry` in full:
        // emptying a self-describing part while leaving the seal intact
        // must not produce a document that verifies.
        assertFalse(ResearchExportCodec.verify(sample().copy(dataDictionary = null)))
    }

    @Test
    fun `a Program 0 document still verifies without a dictionary`() {
        // The v1 shape never had one, so absent is correct there and the
        // stricter v2 rule must not reach back and invalidate it.
        assertTrue(ResearchExportCodec.verify(programZeroExport().copy(dataDictionary = null)))
    }

    @Test
    fun `sealing puts every list in canonical order`() {
        // Multi-element and out of order, because the previous version of
        // this test reversed single-element lists and so compared a hash to
        // itself.
        val ordered = ResearchExportCodec.seal(
            sample().copy(
                contentSha256 = "",
                journalEntries = listOf(entry("entry-1"), entry("entry-2")),
                contextFacts = listOf(contextRow("context-1"), contextRow("context-2")),
                contextInferences = listOf(
                    contextRow("inference-1", "INFERENCE"),
                    contextRow("inference-2", "INFERENCE"),
                ),
                morningMeasures = listOf(measure("measure-1"), measure("measure-2")),
                ledgerEvents = listOf(ledgerEvent("event-1", 1L), ledgerEvent("event-2", 2L)),
                studyPhases = listOf(phase("phase-0", 0), phase("phase-1", 1)),
                protocolRegistry = listOf(
                    sample().protocolRegistry.single().copy(id = "protocol-a"),
                    sample().protocolRegistry.single().copy(id = "protocol-b"),
                ),
                transformations = TransformationRegistry.transformations,
                missingData = listOf(
                    MissingDataRecord("2026-08-28", "morning_measure", MissingDataReason.NOT_RECORDED),
                    MissingDataRecord("2026-08-29", "journal_context", MissingDataReason.CONTEXT_NOT_DERIVED),
                ),
            ),
        )
        val shuffled = ResearchExportCodec.seal(
            ordered.copy(
                contentSha256 = "",
                journalEntries = ordered.journalEntries.reversed(),
                contextFacts = ordered.contextFacts.reversed(),
                contextInferences = ordered.contextInferences.reversed(),
                morningMeasures = ordered.morningMeasures.reversed(),
                ledgerEvents = ordered.ledgerEvents.reversed(),
                studyPhases = ordered.studyPhases.reversed(),
                protocolRegistry = ordered.protocolRegistry.reversed(),
                transformations = ordered.transformations.reversed(),
                missingData = ordered.missingData.reversed(),
            ),
        )

        assertEquals(ordered.contentSha256, shuffled.contentSha256)
        assertEquals(ordered.journalEntries, shuffled.journalEntries)
        assertEquals(ordered.contextInferences, shuffled.contextInferences)
        assertEquals(ordered.ledgerEvents, shuffled.ledgerEvents)
        assertEquals(ordered.protocolRegistry, shuffled.protocolRegistry)
        assertEquals(ordered.transformations, shuffled.transformations)
        assertEquals(ordered.missingData, shuffled.missingData)
        assertNotEquals(
            "the fixture must actually exercise the sort keys",
            ordered.journalEntries,
            ordered.journalEntries.reversed(),
        )
        assertNotEquals(ordered.contextInferences, ordered.contextInferences.reversed())
        assertNotEquals(ordered.protocolRegistry, ordered.protocolRegistry.reversed())
        assertNotEquals(ordered.transformations, ordered.transformations.reversed())
    }

    @Test
    fun `the current content hash is frozen`() {
        // The v1 pin protects Program 0's files. This one protects the
        // files this build writes: reordering a field of the version-2
        // projection, or retuning the encoder, would otherwise invalidate
        // every export already taken, with the suite still green.
        //
        // When this goes red, the question is which of two things happened.
        // If the *projection or encoder* changed, that invalidates files
        // already in people's hands and needs a new dictionary version, not
        // a new number here. If only the *content* changed -- a protocol
        // added, a transformation reworded, the dictionary bumped -- then
        // the hash is expected to move and re-pinning is correct. This pin
        // cannot tell those apart on its own, so the reasoning belongs in
        // the commit message that changes it.
        assertEquals("860581818e1f08d165adfad6b53bb3c2836de5ce59b9f4ef82dad054d9f6e559", sample().contentSha256)
    }

    /**
     * The nine fields a Program 0 document could legitimately carry.
     * Everything else in [ResearchExport] arrived with Program 1 and must
     * be named in the smuggle predicate.
     */
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

    @Test
    fun `no export field escapes the version 1 smuggle check`() {
        // Two other hand-maintained field lists in this feature silently
        // fell behind the class they described; both now have reflection
        // guards. This is the third.
        val declared = ResearchExport::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(
            "a field was added to ResearchExport without deciding whether a version-1 " +
                "document could have carried it: name it in `programOneContentByField`, " +
                "or add it to `programZeroFields` here",
            declared,
            programOneContentByField(sample()).keys + programZeroFields,
        )
    }

    /**
     * One mutation per Program 1 field, applied to a genuine Program 0
     * document. Each must, on its own, make that document refuse to verify.
     */
    private fun programOneSmuggles(base: ResearchExport): Map<String, ResearchExport> = mapOf(
        "ledgerEvents" to base.copy(ledgerEvents = listOf(ledgerEvent("smuggled", 1L))),
        "ledgerHeadHash" to base.copy(ledgerHeadHash = "a fabricated head"),
        "ledgerEventCount" to base.copy(ledgerEventCount = 7),
        "ledgerHighWaterCount" to base.copy(ledgerHighWaterCount = 7),
        "ledgerIntegrity" to base.copy(ledgerIntegrity = LedgerIntegrity.VERIFIED),
        "studyPhases" to base.copy(studyPhases = listOf(phase("phase-0", 0))),
        "protocolRegistry" to base.copy(protocolRegistry = EvidenceProtocolCatalog.registry.protocols),
        "protocolCatalogSha256" to base.copy(protocolCatalogSha256 = "a catalogue"),
        "transformations" to base.copy(transformations = TransformationRegistry.transformations),
        "transformationSetVersion" to base.copy(transformationSetVersion = "a set"),
        "missingData" to base.copy(
            missingData = listOf(
                MissingDataRecord("2026-08-28", "morning_measure", MissingDataReason.NOT_RECORDED),
            ),
        ),
        "missingDataWindowStart" to base.copy(missingDataWindowStart = "2026-08-01"),
        "missingDataWindowThrough" to base.copy(missingDataWindowThrough = "2026-08-29"),
        "missingDataPolicyVersion" to base.copy(missingDataPolicyVersion = "missing-data-v2"),
        "missingDataStatement" to base.copy(missingDataStatement = "Absences are carried forward."),
        "dataDictionary" to base.copy(dataDictionary = ResearchDataDictionary.dictionary),
        "dataDictionarySha256" to base.copy(dataDictionarySha256 = "a dictionary"),
    )

    @Test
    fun `every Program 1 field alone is enough to condemn a version 1 document`() {
        // Not merely that the field is named: that the predicate bound to
        // that name actually reads that field. A mapping keyed "foo" that
        // read `bar` would satisfy a set-membership check while smuggling
        // `foo` straight through.
        val base = programZeroExport()
        assertTrue("the base fixture must itself be a valid v1 document", ResearchExportCodec.verify(base))
        programOneSmuggles(base).forEach { (field, smuggled) ->
            assertTrue(
                "`$field` alone must trip the smuggle check",
                programOneContentByField(smuggled)[field] == true,
            )
            assertFalse(
                "a version 1 document carrying `$field` must not verify",
                ResearchExportCodec.verify(smuggled),
            )
        }
        assertEquals(
            "every field the smuggle check knows about must have a mutation here",
            programOneContentByField(sample()).keys,
            programOneSmuggles(base).keys,
        )
        assertTrue(
            "an empty Program 0 document must trip nothing",
            programOneContentByField(base).values.none { it },
        )
    }

    @Test
    fun `verify refuses a version 1 document carrying Program 1 content`() {
        // `decode` already refuses these, but `verify` is a separate public
        // entry point and a recipient checking an already-parsed export
        // never passes through `decode`. A probe found it answering `true`
        // for a genuine Program 0 file with a fabricated ledger pasted in:
        // the v1 projection hashes four lists, so everything pasted beside
        // them sits outside the hash it is checked against.
        val forged = programZeroExport().copy(
            ledgerEvents = listOf(ledgerEvent("fabricated", 1L)),
            ledgerHeadHash = "a fabricated head",
            ledgerEventCount = 1,
            ledgerIntegrity = LedgerIntegrity.VERIFIED,
            missingDataStatement = "Absences are carried forward from the previous day.",
        )
        assertTrue("the fixture must still be a genuine v1 document", ResearchExportCodec.verify(programZeroExport()))
        assertFalse("a v1 document carrying Program 1 content must not verify", ResearchExportCodec.verify(forged))
    }

    @Test
    fun `a version 1 document carrying Program 1 content is refused`() {
        // A v1 document is hashed over four lists, so anything else it
        // carries sits outside its own hash. Pasting a fabricated ledger
        // into a genuine Program 0 export must not yield something that
        // verifies.
        val document = programZeroDocument
            .replace("PROGRAM_ZERO_HASH", programZeroExport().contentSha256)
            .replace(
                "\"morningMeasures\": []",
                "\"morningMeasures\": [],\n  \"ledgerHeadHash\": \"a fabricated head\"",
            )
        assertEquals(ResearchExportCodec.DecodeResult.Corrupt, ResearchExportCodec.decode(document))
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
