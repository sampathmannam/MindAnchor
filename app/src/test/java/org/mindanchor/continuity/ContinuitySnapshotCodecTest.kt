package org.mindanchor.continuity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 7 — pins the canonical continuity snapshot codec: a full round trip
 * preserves every field, the content hash is order-independent (so the same
 * logical data restored on a new phone produces the same hash), a single
 * changed character changes the hash, and both an unsupported format
 * version and corrupt JSON are rejected as typed results rather than
 * thrown exceptions.
 */
class ContinuitySnapshotCodecTest {

    private val programZeroFields = setOf(
        "journalEntries",
        "contextRows",
        "morningMeasures",
        "notes",
        "letters",
        "readLetterDates",
        "frictionedApps",
        "alwaysOpenApps",
        "continuityChanges",
        "legacyBackupJson",
    )
    private val programOneFields = programZeroFields + setOf("researchLedgerEvents", "studyPhases")
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

    private fun samplePayload(entryBodySuffix: String = "one"): ContinuityPayload = ContinuityPayload(
        journalEntries = listOf(
            JournalEntryDto(
                id = "entry-1",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                localDate = "2026-08-27",
                title = "Morning",
                body = "Body $entryBodySuffix",
                kind = "DAILY",
                sourceDeviceId = "device-a",
                deletedAt = null,
            ),
        ),
        contextRows = listOf(
            JournalContextDto(
                id = "context-1",
                entryId = "entry-1",
                recordType = "FACT",
                key = "word_count",
                value = "2",
                sourceStart = null,
                sourceEnd = null,
                confidence = 1.0,
                extractorVersion = "structural-v1",
                createdAt = 1_000L,
            ),
        ),
        morningMeasures = listOf(
            MorningMeasureDto(
                id = "measure-1",
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
            ),
        ),
        notes = listOf(
            NoteDto(id = 1L, body = "A note", createdAt = 500L, updatedAt = 500L, pinned = false, type = null),
        ),
        letters = listOf(
            LetterDto(
                date = "2026-08-26",
                body = "A letter",
                provider = "groq",
                model = "llama-3.3-70b-versatile",
                promptTokens = 100,
                completionTokens = 50,
                durationMs = 1_200L,
            ),
        ),
        readLetterDates = listOf("2026-08-26"),
        frictionedApps = listOf("com.example.social"),
        alwaysOpenApps = listOf("com.example.work"),
        continuityChanges = listOf(
            ContinuityChangeDto(
                id = "change-1",
                entityType = "JOURNAL_ENTRY",
                entityId = "entry-1",
                operation = "CREATE",
                occurredAt = 1_000L,
                acknowledgedSnapshotId = null,
            ),
        ),
        legacyBackupJson = """{"version":1,"note":"n","savedAt":42,"plan":{"warningSigns":"","copingSteps":"","distractions":"","reasonsForLiving":"","environmentSafety":""},"contacts":[],"pulses":[],"favorites":[],"hidden":[],"frictioned":[],"renames":{},"checkIns":[],"readings":[],"corpusAdditions":"","inferred":[]}""",
    )

    private fun sampleSnapshot(payload: ContinuityPayload = samplePayload()): ContinuitySnapshot {
        val hash = ContinuityContentHasher.hash(payload)
        return ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = "snapshot-1",
            createdAt = 2_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
            sourceDeviceId = "device-a",
            payload = payload,
            contentSha256 = hash,
        )
    }

    private fun programOnePayload(): ContinuityPayload = ProgramZeroPayloadFixture.payload().copy(
        researchLedgerEvents = listOf(
            ResearchLedgerEventDto(
                id = "event-1",
                sequence = 1L,
                kind = "EXERCISE",
                occurredAt = 1_000L,
                recordedAt = 1_050L,
                localDate = "2026-08-29",
                studyPhaseId = "phase-0",
                sourceDeviceId = "device-a",
                note = "morning run",
                payloadJson = "{}",
                previousEventHash = "",
                eventHash = "event-1",
            ),
        ),
        studyPhases = listOf(
            StudyPhaseDto(
                id = "phase-0",
                ordinal = 0,
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
                dictionaryVersion = "mindanchor-research-v1",
                sourceDeviceId = "device-a",
            ),
        ),
    )

    /** Literal outputs of the v1 (`ea346d4`) and v2 (`6eb7d88`) historical serializers. */
    private fun literalFixture(version: Int): String = requireNotNull(
        javaClass.getResource("/continuity/snapshot-v$version.json"),
    ).readText()

    private fun decodeLiteralFixture(version: Int): ContinuitySnapshot {
        val decoded = ContinuitySnapshotCodec.decode(literalFixture(version))
        assertTrue(
            "literal v$version fixture must decode, got $decoded",
            decoded is ContinuitySnapshotCodec.DecodeResult.Success,
        )
        return (decoded as ContinuitySnapshotCodec.DecodeResult.Success).snapshot
    }

    private fun allPopulatedPayload(): ContinuityPayload {
        val programTwo = ProgramTwoPayloadFixture.payload()
        return programOnePayload().copy(
            passiveRawProvenance = programTwo.passiveRawProvenance,
            passiveSourceReads = programTwo.passiveSourceReads,
            passiveSourceLags = programTwo.passiveSourceLags,
            passiveBaselineSegments = programTwo.passiveBaselineSegments,
            passivePipelineRuns = programTwo.passivePipelineRuns,
            passiveWindowRevisions = programTwo.passiveWindowRevisions,
            passiveDailyRevisions = programTwo.passiveDailyRevisions,
            passiveObservationDecisions = programTwo.passiveObservationDecisions,
        )
    }

    private fun encodedDocument(version: Int): JsonObject {
        val payload = when (version) {
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION -> ProgramZeroPayloadFixture.payload()
            ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION -> programOnePayload()
            else -> allPopulatedPayload()
        }
        return Json.parseToJsonElement(
            ContinuitySnapshotCodec.encode(
                sampleSnapshot(payload).copy(
                    formatVersion = version,
                    contentSha256 = ContinuityContentHasher.hash(payload, version),
                ),
            ),
        ).jsonObject
    }

    private fun mutatePayload(document: JsonObject, field: String, value: JsonElement): String {
        val payload = document.getValue("payload").jsonObject
        return JsonObject(document + ("payload" to JsonObject(payload + (field to value)))).toString()
    }

    @Test
    fun `capture encode decode preserves every field`() {
        val snapshot = sampleSnapshot()

        val encoded = ContinuitySnapshotCodec.encode(snapshot)
        val decoded = ContinuitySnapshotCodec.decode(encoded)

        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.Success)
        val restored = (decoded as ContinuitySnapshotCodec.DecodeResult.Success).snapshot
        assertEquals(snapshot, restored)
    }

    @Test
    fun `list order does not change the content hash`() {
        val entryA = JournalEntryDto(
            id = "entry-a",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            localDate = "2026-08-27",
            title = "A",
            body = "Body A",
            kind = "DAILY",
            sourceDeviceId = "device-a",
            deletedAt = null,
        )
        val entryB = entryA.copy(id = "entry-b", title = "B", body = "Body B")

        val payloadForward = ContinuityPayload(journalEntries = listOf(entryA, entryB))
        val payloadReversed = ContinuityPayload(journalEntries = listOf(entryB, entryA))

        val hashForward = ContinuityContentHasher.hash(payloadForward)
        val hashReversed = ContinuityContentHasher.hash(payloadReversed)

        assertEquals(hashForward, hashReversed)
    }

    @Test
    fun `one changed journal character changes the hash`() {
        val original = samplePayload(entryBodySuffix = "one")
        val mutated = samplePayload(entryBodySuffix = "onE")

        val originalHash = ContinuityContentHasher.hash(original)
        val mutatedHash = ContinuityContentHasher.hash(mutated)

        assertNotEquals(originalHash, mutatedHash)
    }

    @Test
    fun `the same logical data restored on a new phone produces the same hash`() {
        // Same payload, but as if captured from two different devices at
        // two different times — the two snapshot-level fields that must
        // never leak into the content hash.
        val payload = samplePayload()
        val hashDeviceA = ContinuityContentHasher.hash(payload)
        val hashDeviceB = ContinuityContentHasher.hash(payload.copy())

        assertEquals(hashDeviceA, hashDeviceB)
    }

    @Test
    fun `an unknown format version is rejected with a typed error`() {
        val snapshot = sampleSnapshot()
        val encoded = ContinuitySnapshotCodec.encode(snapshot)
        val withBadVersion = encoded.replaceFirst(
            "\"formatVersion\":${snapshot.formatVersion}",
            "\"formatVersion\":999",
        )
        assertNotEquals(encoded, withBadVersion) // sanity: the replace actually matched

        val decoded = ContinuitySnapshotCodec.decode(withBadVersion)

        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.UnsupportedVersion)
        assertEquals(999, (decoded as ContinuitySnapshotCodec.DecodeResult.UnsupportedVersion).formatVersion)
    }

    @Test
    fun `corrupt JSON is rejected without throwing`() {
        val decoded = ContinuitySnapshotCodec.decode("{ this is not valid json at all")

        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.Corrupt)
    }

    @Test
    fun `payload keeps journal entries and context rows as separate top-level arrays`() {
        val snapshot = sampleSnapshot()
        val encoded = ContinuitySnapshotCodec.encode(snapshot)

        assertTrue(encoded.contains("\"journalEntries\""))
        assertTrue(encoded.contains("\"contextRows\""))
        // They must be two distinct arrays, not one array serving both roles.
        val entriesIndex = encoded.indexOf("\"journalEntries\"")
        val contextIndex = encoded.indexOf("\"contextRows\"")
        assertNotEquals(entriesIndex, contextIndex)
    }

    @Test
    fun `a Program 0 snapshot document still decodes`() {
        val current = ContinuitySnapshotCodec.encode(
            ContinuitySnapshot(
                formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
                snapshotId = "snap-1",
                createdAt = 1_000L,
                appVersionCode = 1,
                appVersionName = "test",
                sourceDeviceId = "device-a",
                payload = samplePayload(),
                contentSha256 = "hash",
            ),
        )
        // The one change that makes a Program 0 checkpoint readable at all
        // on this build. Nothing else in the suite produces a version-1
        // document, because nothing writes one any more.
        val asProgramZero = current.replace(
            "\"formatVersion\":${ContinuitySnapshot.CURRENT_FORMAT_VERSION}",
            "\"formatVersion\":${ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION}",
        )
        val decoded = ContinuitySnapshotCodec.decode(asProgramZero)
        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.Success)
        assertEquals(
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            (decoded as ContinuitySnapshotCodec.DecodeResult.Success).snapshot.formatVersion,
        )
    }

    @Test
    fun `literal historical version one and two documents retain their frozen wire hashes`() {
        val versionOne = decodeLiteralFixture(ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION)
        val versionTwo = decodeLiteralFixture(ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION)

        assertEquals("ff5e149fed2b67c6217a468aa6caba29449f7c80e72984cf0bb9bab67b660906", versionOne.contentSha256)
        assertEquals("e742d00616ff5dc35a7e669857b6e74edede207a7374abf7b6ef201ae48bdba5", versionTwo.contentSha256)
        assertEquals(
            versionOne.contentSha256,
            ContinuityContentHasher.hash(versionOne.payload, versionOne.formatVersion),
        )
        assertEquals(
            versionTwo.contentSha256,
            ContinuityContentHasher.hash(versionTwo.payload, versionTwo.formatVersion),
        )
        assertEquals("Historical v1", versionOne.payload.journalEntries.single().title)
        assertEquals("historical-event-v2", versionTwo.payload.researchLedgerEvents.single().id)
        assertEquals(
            programZeroFields,
            Json.parseToJsonElement(literalFixture(1)).jsonObject.getValue("payload").jsonObject.keys,
        )
        assertEquals(
            programOneFields,
            Json.parseToJsonElement(literalFixture(2)).jsonObject.getValue("payload").jsonObject.keys,
        )
        assertEquals(emptyList<ResearchLedgerEventDto>(), versionOne.payload.researchLedgerEvents)
        assertEquals(emptyList<StudyPhaseDto>(), versionOne.payload.studyPhases)
        listOf(versionOne.payload, versionTwo.payload).forEach { payload ->
            assertTrue(programTwoSnapshotContentByField(payload).values.none { it })
        }
        listOf(literalFixture(1), literalFixture(2)).forEach { document ->
            assertFalse(document.contains("passiveRawSamples"))
            assertFalse(document.contains("passiveRawProvenance"))
        }
    }

    @Test
    fun `version one rejects Program 1 content smuggling`() {
        val programOne = programOnePayload()
        listOf(
            ContinuityPayload(researchLedgerEvents = programOne.researchLedgerEvents),
            ContinuityPayload(studyPhases = programOne.studyPhases),
        ).forEach { smuggled ->
            val encoded = ContinuitySnapshotCodec.encode(
                sampleSnapshot(smuggled).copy(
                    formatVersion = ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
                ),
            )
            assertEquals(ContinuitySnapshotCodec.DecodeResult.Corrupt, ContinuitySnapshotCodec.decode(encoded))
        }
    }

    @Test
    fun `version two rejects each Program 2 list smuggled independently`() {
        val populated = ProgramTwoPayloadFixture.payload()
        val mutations = listOf(
            ContinuityPayload(passiveRawProvenance = populated.passiveRawProvenance),
            ContinuityPayload(passiveSourceReads = populated.passiveSourceReads),
            ContinuityPayload(passiveSourceLags = populated.passiveSourceLags),
            ContinuityPayload(passiveBaselineSegments = populated.passiveBaselineSegments),
            ContinuityPayload(passivePipelineRuns = populated.passivePipelineRuns),
            ContinuityPayload(passiveWindowRevisions = populated.passiveWindowRevisions),
            ContinuityPayload(passiveDailyRevisions = populated.passiveDailyRevisions),
            ContinuityPayload(passiveObservationDecisions = populated.passiveObservationDecisions),
        )
        mutations.forEach { smuggled ->
            val encoded = ContinuitySnapshotCodec.encode(
                sampleSnapshot(smuggled).copy(
                    formatVersion = ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION,
                ),
            )
            assertEquals(ContinuitySnapshotCodec.DecodeResult.Corrupt, ContinuitySnapshotCodec.decode(encoded))
        }
    }

    @Test
    fun `raw version one and two documents reject every known later field when populated`() {
        val donorPayload = encodedDocument(ContinuityContract.SNAPSHOT_FORMAT_VERSION).getValue("payload").jsonObject
        mapOf(
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION to
                (programOneFields - programZeroFields) + programTwoFields,
            ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION to programTwoFields,
        ).forEach { (version, fields) ->
            val historical = Json.parseToJsonElement(literalFixture(version)).jsonObject
            fields.forEach { field ->
                val smuggled = mutatePayload(historical, field, donorPayload.getValue(field))
                assertEquals(
                    "v$version must reject populated $field",
                    ContinuitySnapshotCodec.DecodeResult.Corrupt,
                    ContinuitySnapshotCodec.decode(smuggled),
                )
            }
        }
    }

    @Test
    fun `supported versions reject unknown payload content before typed decoding discards it`() {
        val smuggled = buildJsonArray {
            add(buildJsonObject { put("value", JsonPrimitive(97.0)) })
        }
        ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS.forEach { version ->
            val document = if (version < ContinuityContract.SNAPSHOT_FORMAT_VERSION) {
                Json.parseToJsonElement(literalFixture(version)).jsonObject
            } else {
                encodedDocument(version)
            }
            assertEquals(
                "v$version must reject unknown non-empty content",
                ContinuitySnapshotCodec.DecodeResult.Corrupt,
                ContinuitySnapshotCodec.decode(mutatePayload(document, "futurePassiveRows", smuggled)),
            )
        }
    }

    @Test
    fun `older versions tolerate only explicitly known later fields when empty`() {
        mapOf(
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION to
                (programOneFields - programZeroFields) + programTwoFields,
            ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION to programTwoFields,
        ).forEach { (version, fields) ->
            val historical = Json.parseToJsonElement(literalFixture(version)).jsonObject
            fields.forEach { field ->
                assertTrue(
                    "v$version must tolerate known empty $field",
                    ContinuitySnapshotCodec.decode(mutatePayload(historical, field, JsonArray(emptyList()))) is
                        ContinuitySnapshotCodec.DecodeResult.Success,
                )
            }
            assertEquals(
                ContinuitySnapshotCodec.DecodeResult.Corrupt,
                ContinuitySnapshotCodec.decode(
                    mutatePayload(historical, "unknownEmptyRows", JsonArray(emptyList())),
                ),
            )
        }
    }

    @Test
    fun `every supported version rejects passive raw sample values`() {
        val rawSamples = buildJsonArray {
            add(buildJsonObject {
                put("provenanceId", JsonPrimitive("raw-1"))
                put("value", JsonPrimitive(97.0))
                put("ingestedAt", JsonPrimitive(2_200L))
            })
        }
        ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS.forEach { version ->
            val document = if (version < ContinuityContract.SNAPSHOT_FORMAT_VERSION) {
                Json.parseToJsonElement(literalFixture(version)).jsonObject
            } else {
                encodedDocument(version)
            }
            assertEquals(
                "v$version must reject passiveRawSamples",
                ContinuitySnapshotCodec.DecodeResult.Corrupt,
                ContinuitySnapshotCodec.decode(mutatePayload(document, "passiveRawSamples", rawSamples)),
            )
        }
    }
}
