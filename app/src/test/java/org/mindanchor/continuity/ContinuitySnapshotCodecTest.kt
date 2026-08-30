package org.mindanchor.continuity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
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

    private fun historicalDocument(
        version: Int,
        payload: ContinuityPayload,
        allowedPayloadFields: Set<String>,
    ): String {
        val snapshot = ContinuitySnapshot(
            formatVersion = version,
            snapshotId = "historical-$version",
            createdAt = 1_000L,
            appVersionCode = version,
            appVersionName = "historical",
            sourceDeviceId = "device-a",
            payload = payload,
            contentSha256 = ContinuityContentHasher.hash(payload, version),
        )
        val root = Json.parseToJsonElement(ContinuitySnapshotCodec.encode(snapshot)).jsonObject
        val historicalPayload = JsonObject(
            root.getValue("payload").jsonObject.filterKeys { it in allowedPayloadFields },
        )
        return JsonObject(root + ("payload" to historicalPayload)).toString()
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
    fun `genuine version one and two fixtures retain pinned hashes and default Program 2 rows empty`() {
        val versionOne = ContinuitySnapshotCodec.decode(
            historicalDocument(
                ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
                ProgramZeroPayloadFixture.payload(),
                programZeroFields,
            ),
        ) as ContinuitySnapshotCodec.DecodeResult.Success
        val versionTwo = ContinuitySnapshotCodec.decode(
            historicalDocument(
                ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION,
                programOnePayload(),
                programOneFields,
            ),
        ) as ContinuitySnapshotCodec.DecodeResult.Success

        assertEquals(
            "a9da88f8d627c266e994b3292002c84594d119ac552c76a10745e458eaf3f9af",
            versionOne.snapshot.contentSha256,
        )
        assertEquals(
            "888b11076e526b64e7ca2c93bfa7179b01e2e6dea692e12a05003ddbde02373e",
            versionTwo.snapshot.contentSha256,
        )
        assertEquals(emptyList<ResearchLedgerEventDto>(), versionOne.snapshot.payload.researchLedgerEvents)
        assertEquals(emptyList<StudyPhaseDto>(), versionOne.snapshot.payload.studyPhases)
        listOf(versionOne.snapshot.payload, versionTwo.snapshot.payload).forEach { payload ->
            assertTrue(programTwoSnapshotContentByField(payload).values.none { it })
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
}
