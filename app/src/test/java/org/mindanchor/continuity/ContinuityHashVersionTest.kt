package org.mindanchor.continuity

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 1 — the continuity content hash takes a format version,
 * and the version Program 0 shipped is frozen twice over: by the digest of
 * a fully populated fixture, and structurally by the field names and order
 * the version-1 projection serialises.
 *
 * The structural assertions matter more than the digest. The digest was
 * produced by running this code, so on its own it would freeze whatever
 * the projection happens to do — two transposed fields would simply have
 * produced a different constant and both tests would still pass, while
 * every Program 0 backup silently failed to verify. Asserting the element
 * names against a literal list is what makes the freeze independent of the
 * value it pins.
 */
@OptIn(ExperimentalSerializationApi::class)
class ContinuityHashVersionTest {

    /** Program 0's payload keys, in Program 0's order. This list is history; it does not change. */
    private val programZeroFields = listOf(
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

    private val programOneFields = programZeroFields + listOf(
        "researchLedgerEvents",
        "studyPhases",
    )

    private val programTwoFields = listOf(
        "passiveRawProvenance",
        "passiveSourceReads",
        "passiveSourceLags",
        "passiveBaselineSegments",
        "passivePipelineRuns",
        "passiveWindowRevisions",
        "passiveDailyRevisions",
        "passiveObservationDecisions",
    )

    @Test
    fun `the program zero hash is frozen`() {
        assertEquals(
            "a9da88f8d627c266e994b3292002c84594d119ac552c76a10745e458eaf3f9af",
            ContinuityContentHasher.hash(
                ProgramZeroPayloadFixture.payload(),
                formatVersion = ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `the version one projection serialises exactly program zero's fields`() {
        assertEquals(
            programZeroFields,
            serializer<ContinuityPayloadV1>().descriptor.elementNames.toList(),
        )
    }

    @Test
    fun `the live payload still begins with program zero's fields`() {
        assertEquals(
            programZeroFields,
            serializer<ContinuityPayload>().descriptor.elementNames.toList().take(programZeroFields.size),
        )
    }

    @Test
    fun `version two projection remains the exact Program 1 payload`() {
        assertEquals(
            programOneFields,
            serializer<ContinuityPayloadV2>().descriptor.elementNames.toList(),
        )
    }

    @Test
    fun `snapshot carries passive provenance but no raw sample value shape`() {
        val payloadFields = ContinuityPayload::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("passiveRawProvenance" in payloadFields)
        assertFalse("passiveRawSamples" in payloadFields)
        assertFalse(PassiveRawProvenanceDto::class.java.declaredFields.any { it.name == "value" })
    }

    @Test
    fun `newer content maps cover every field appended after their frozen projections`() {
        val payloadFields = serializer<ContinuityPayload>().descriptor.elementNames.toList()
        assertEquals(
            payloadFields.drop(programZeroFields.size).toSet(),
            programOneSnapshotContentByField(ContinuityPayload()).keys +
                programTwoSnapshotContentByField(ContinuityPayload()).keys,
        )
        assertEquals(
            payloadFields.drop(programOneFields.size).toSet(),
            programTwoSnapshotContentByField(ContinuityPayload()).keys,
        )
    }

    @Test
    fun `newer content predicates inspect their own fields`() {
        val programOnePayload = withResearchRows(ContinuityPayload())
        assertEquals(
            setOf("researchLedgerEvents", "studyPhases"),
            programOneSnapshotContentByField(programOnePayload).filterValues { it }.keys,
        )

        val populated = ProgramTwoPayloadFixture.payload()
        programTwoFields.forEach { field ->
            val oneField = when (field) {
                "passiveRawProvenance" -> ContinuityPayload(passiveRawProvenance = populated.passiveRawProvenance)
                "passiveSourceReads" -> ContinuityPayload(passiveSourceReads = populated.passiveSourceReads)
                "passiveSourceLags" -> ContinuityPayload(passiveSourceLags = populated.passiveSourceLags)
                "passiveBaselineSegments" ->
                    ContinuityPayload(passiveBaselineSegments = populated.passiveBaselineSegments)
                "passivePipelineRuns" -> ContinuityPayload(passivePipelineRuns = populated.passivePipelineRuns)
                "passiveWindowRevisions" -> ContinuityPayload(passiveWindowRevisions = populated.passiveWindowRevisions)
                "passiveDailyRevisions" -> ContinuityPayload(passiveDailyRevisions = populated.passiveDailyRevisions)
                "passiveObservationDecisions" ->
                    ContinuityPayload(passiveObservationDecisions = populated.passiveObservationDecisions)
                else -> error("unexpected Program 2 field $field")
            }
            assertEquals(
                "only $field should be reported as populated",
                setOf(field),
                programTwoSnapshotContentByField(oneField).filterValues { it }.keys,
            )
        }
    }

    @Test
    fun `an unsupported format version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuityContentHasher.hash(ProgramZeroPayloadFixture.payload(), formatVersion = 99)
        }
    }

    @Test
    fun `the freeze covers the canonical sort order`() {
        val payload = ProgramZeroPayloadFixture.payload()
        val shuffled = payload.copy(
            journalEntries = payload.journalEntries.reversed(),
            contextRows = payload.contextRows.reversed(),
            morningMeasures = payload.morningMeasures.reversed(),
            notes = payload.notes.reversed(),
            letters = payload.letters.reversed(),
            readLetterDates = payload.readLetterDates.reversed(),
            frictionedApps = payload.frictionedApps.reversed(),
            alwaysOpenApps = payload.alwaysOpenApps.reversed(),
            continuityChanges = payload.continuityChanges.reversed(),
        )
        assertEquals(
            ContinuityContentHasher.hash(payload, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(shuffled, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION),
        )
        assertNotEquals(
            "the fixture must actually exercise the sort keys",
            payload.journalEntries,
            payload.journalEntries.reversed(),
        )
    }

    private fun withResearchRows(payload: ContinuityPayload) = payload.copy(
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

    @Test
    fun `version one ignores the research rows entirely`() {
        val bare = ProgramZeroPayloadFixture.payload()
        assertEquals(
            "adding research rows must not move a Program 0 hash",
            ContinuityContentHasher.hash(bare, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(
                withResearchRows(bare),
                ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `version two covers the research rows`() {
        val bare = ProgramZeroPayloadFixture.payload()
        assertNotEquals(
            ContinuityContentHasher.hash(bare, ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(
                withResearchRows(bare),
                ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `the program one hash is frozen`() {
        assertEquals(
            "888b11076e526b64e7ca2c93bfa7179b01e2e6dea692e12a05003ddbde02373e",
            ContinuityContentHasher.hash(
                withResearchRows(ProgramZeroPayloadFixture.payload()),
                formatVersion = ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `the two versions of the same payload disagree`() {
        val payload = withResearchRows(ProgramZeroPayloadFixture.payload())
        assertNotEquals(
            "hashing a v2 payload as v1 must not silently succeed",
            ContinuityContentHasher.hash(payload, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(payload, ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION),
        )
    }

    @Test
    fun `program two rows sort into canonical order before hashing`() {
        val payload = ProgramTwoPayloadFixture.payloadWithReversedRows()
        assertEquals(
            ContinuityContentHasher.hash(payload),
            ContinuityContentHasher.hash(
                payload.copy(
                    passiveRawProvenance = payload.passiveRawProvenance.reversed(),
                    passiveSourceReads = payload.passiveSourceReads.reversed(),
                    passiveSourceLags = payload.passiveSourceLags.reversed(),
                    passiveBaselineSegments = payload.passiveBaselineSegments.reversed(),
                    passivePipelineRuns = payload.passivePipelineRuns.reversed(),
                    passiveWindowRevisions = payload.passiveWindowRevisions.reversed(),
                    passiveDailyRevisions = payload.passiveDailyRevisions.reversed(),
                    passiveObservationDecisions = payload.passiveObservationDecisions.reversed(),
                ),
            ),
        )
    }

    @Test
    fun `research rows sort into chain order before hashing`() {
        val payload = ProgramZeroPayloadFixture.payload().copy(
            researchLedgerEvents = withResearchRows(ProgramZeroPayloadFixture.payload()).researchLedgerEvents,
            studyPhases = withResearchRows(ProgramZeroPayloadFixture.payload()).studyPhases,
        )
        val doubled = payload.copy(
            researchLedgerEvents = payload.researchLedgerEvents +
                payload.researchLedgerEvents.map { it.copy(id = "event-2", sequence = 2L, eventHash = "event-2") },
        )
        assertEquals(
            ContinuityContentHasher.hash(doubled, ContinuityContract.SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(
                doubled.copy(researchLedgerEvents = doubled.researchLedgerEvents.reversed()),
                ContinuityContract.SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }
}

internal object ProgramTwoPayloadFixture {
    @Suppress("LongMethod")
    fun payload(): ContinuityPayload = ContinuityPayload(
        passiveRawProvenance = listOf(
            PassiveRawProvenanceDto(
                id = "raw-1",
                sourceFamily = "HEART_RATE",
                recordKind = "HeartRateRecord",
                eventStart = 1_000L,
                eventEnd = 2_000L,
                unit = "bpm",
                dataOriginPackage = "com.example.health",
                deviceManufacturer = "Example",
                deviceModel = "Watch",
                deviceType = "WATCH",
                sourceUpdatedTime = 2_100L,
                ingestedAt = 2_200L,
                zoneId = "Asia/Calcutta",
                zoneOffsetSeconds = 19_800,
                recordId = "record-1",
                recordVersion = 1L,
            ),
        ),
        passiveSourceReads = listOf(
            PassiveSourceReadDto(
                id = "read-1",
                runId = "run-1",
                sourceFamily = "HEART_RATE",
                state = "AVAILABLE",
                rangeStart = 1_000L,
                rangeEnd = 2_000L,
                zoneId = "Asia/Calcutta",
                attemptedAt = 2_200L,
                recordCount = 1,
                errorCode = null,
            ),
        ),
        passiveSourceLags = listOf(
            PassiveSourceLagDto(
                id = "lag-1",
                sourceFamily = "HEART_RATE",
                eventEnd = 2_000L,
                observedUpdatedAt = 2_100L,
                ingestedAt = 2_200L,
                lagMillis = 100L,
                usedIngestedAtFallback = false,
                observedAt = 2_200L,
            ),
        ),
        passiveBaselineSegments = listOf(
            PassiveBaselineSegmentDto(
                id = "segment-1",
                openedAt = 500L,
                fingerprintsJson = "{}",
                windowTransformationVersion = "window-v1",
                dailyTransformationVersion = "daily-v1",
            ),
        ),
        passivePipelineRuns = listOf(
            PassivePipelineRunDto(
                id = "run-1",
                startedAt = 2_000L,
                completedAt = 2_300L,
                scanStart = 1_000L,
                scanEnd = 2_000L,
                zoneId = "Asia/Calcutta",
                historyPermissionGranted = true,
                firstSuccessfulPermissionedRun = true,
                result = "SUCCESS_PERMISSIONED",
                sourceStatesJson = "{}",
            ),
        ),
        passiveWindowRevisions = listOf(
            PassiveWindowRevisionDto(
                id = "window-1",
                windowStart = 1_000L,
                windowEnd = 1_900L,
                asOfTime = 2_200L,
                zoneId = "Asia/Calcutta",
                zoneOffsetSeconds = 19_800,
                wakeRelativeMinute = 15,
                baselineSegment = "segment-1",
                featureRowsJson = "[]",
                heartRateCoverage = 1.0,
                physiologyEligible = true,
                exerciseOverlapMillis = 0L,
                provenanceRecordIdsJson = "[\"raw-1\"]",
                missingnessJson = "[]",
                exclusionsJson = "[]",
                transformationVersion = "window-v1",
                sourceUpdatedTime = 2_100L,
                ingestedAt = 2_200L,
                final = false,
                revisionReason = "INITIAL",
                contentHash = "window-hash-1",
            ),
        ),
        passiveDailyRevisions = listOf(
            PassiveDailyRevisionDto(
                id = "daily-1",
                localDate = "2026-08-30",
                asOfTime = 2_200L,
                dataStatus = "AVAILABLE_PROVISIONAL",
                featuresJson = "{}",
                excludedFeaturesJson = "[]",
                baselineSegment = "segment-1",
                sourceUpdatedTime = 2_100L,
                ingestedAt = 2_200L,
                sourceReadStatesJson = "{}",
                coverageJson = "{}",
                missingnessJson = "[]",
                exclusionsJson = "[]",
                provenanceJson = "[\"raw-1\"]",
                windowTransformationVersion = "window-v1",
                dailyTransformationVersion = "daily-v1",
                watermark = 2_000L,
                revisionReason = "INITIAL",
                contentHash = "daily-hash-1",
            ),
        ),
        passiveObservationDecisions = listOf(
            PassiveObservationDecisionDto(
                id = "decision-1",
                localDate = "2026-08-30",
                asOfTime = 2_200L,
                dataStatus = "AVAILABLE_PROVISIONAL",
                observationState = "NO_OBSERVATION",
                baselineSegment = "segment-1",
                calibrationSeed = null,
                frozenBaselineAsOfTime = null,
                frozenBaselineThroughDay = null,
                decisionJson = "{}",
                revisionReason = "INITIAL",
                contentHash = "decision-hash-1",
            ),
        ),
    )

    fun payloadWithReversedRows(): ContinuityPayload {
        val payload = payload()
        return payload.copy(
            passiveRawProvenance = listOf(
                payload.passiveRawProvenance.single().copy(id = "raw-2", eventStart = 2_000L),
            ) + payload.passiveRawProvenance,
            passiveSourceReads = listOf(payload.passiveSourceReads.single().copy(id = "read-2", attemptedAt = 3_000L)) +
                payload.passiveSourceReads,
            passiveSourceLags = listOf(payload.passiveSourceLags.single().copy(id = "lag-2", observedAt = 3_000L)) +
                payload.passiveSourceLags,
            passiveBaselineSegments =
                listOf(payload.passiveBaselineSegments.single().copy(id = "segment-2", openedAt = 1_000L)) +
                    payload.passiveBaselineSegments,
            passivePipelineRuns =
                listOf(payload.passivePipelineRuns.single().copy(id = "run-2", completedAt = 3_000L)) +
                    payload.passivePipelineRuns,
            passiveWindowRevisions =
                listOf(payload.passiveWindowRevisions.single().copy(id = "window-2", windowStart = 2_000L)) +
                    payload.passiveWindowRevisions,
            passiveDailyRevisions =
                listOf(payload.passiveDailyRevisions.single().copy(id = "daily-2", localDate = "2026-08-31")) +
                    payload.passiveDailyRevisions,
            passiveObservationDecisions = listOf(
                payload.passiveObservationDecisions.single().copy(id = "decision-2", localDate = "2026-08-31"),
            ) + payload.passiveObservationDecisions,
        )
    }
}
