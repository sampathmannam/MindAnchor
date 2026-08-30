package org.mindanchor.intelligence

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PassivePipelineCodecTest {
    @Test
    fun `raw identity includes exact revision identity and has a frozen hash`() {
        val record = record()

        assertEquals(
            "6c152d8f0f6de0c7b3dac407db5a163fd7bc1bbd5bdaa894b380b53b21918af2",
            PassivePipelineCodec.rawIdentity(record),
        )
        assertEquals(PassivePipelineCodec.rawIdentity(record), PassivePipelineCodec.rawIdentity(record.copy()))
        assertNotEquals(
            PassivePipelineCodec.rawIdentity(record),
            PassivePipelineCodec.rawIdentity(record.copy(recordVersion = record.recordVersion + 1L)),
        )
        assertNotEquals(
            PassivePipelineCodec.rawIdentity(record),
            PassivePipelineCodec.rawIdentity(record.copy(eventEnd = record.eventEnd + 1L)),
        )
    }

    @Test
    fun `content hash seed and append policy are deterministic`() {
        assertEquals(
            "015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862",
            PassivePipelineCodec.contentHash("{\"a\":1}"),
        )
        assertEquals(
            PassivePipelineCodec.calibrationSeed("segment-a", 123L, "calibration-v1"),
            PassivePipelineCodec.calibrationSeed("segment-a", 123L, "calibration-v1"),
        )
        assertFalse(PassivePipelineCodec.shouldAppend("same", "same", RevisionReason.CONTENT_CHANGED))
        assertTrue(PassivePipelineCodec.shouldAppend("same", "same", RevisionReason.FINALITY))
        assertTrue(PassivePipelineCodec.shouldAppend("same", "same", RevisionReason.BACKFILL))
    }

    @Test
    fun `source read and per-record lag entities preserve exact outcome semantics`() {
        val range = PassiveReadRange(1L, 2L, "UTC")
        val read = PassiveSourceRead(
            PassiveSourceFamily.STEPS,
            PassiveReadState.PERMISSION_DENIED,
            range,
            attemptedAt = 3L,
            errorCode = "DENIED",
        )

        val readEntity = PassivePipelineCodec.sourceReadEntity(read, "run-a")
        val lagWithUpdate = PassivePipelineCodec.sourceLagEntity(record(), observedAt = 10L)
        val fallbackRecord = record().copy(sourceUpdatedTime = null, ingestedAt = record().eventEnd + 25L)
        val lagWithFallback = PassivePipelineCodec.sourceLagEntity(fallbackRecord, observedAt = 11L)

        assertEquals("PERMISSION_DENIED", readEntity.state)
        assertEquals("DENIED", readEntity.errorCode)
        assertFalse(lagWithUpdate.usedIngestedAtFallback)
        assertEquals(requireNotNull(record().sourceUpdatedTime) - record().eventEnd, lagWithUpdate.lagMillis)
        assertTrue(lagWithFallback.usedIngestedAtFallback)
        assertEquals(25L, lagWithFallback.lagMillis)
        assertEquals(
            lagWithFallback.id,
            PassivePipelineCodec.sourceLagEntity(fallbackRecord, observedAt = 999L).id,
        )
    }

    @Test
    fun `fingerprint json is canonical and round trips complete nullable values`() {
        val first = fingerprint(PassiveSourceFamily.STEPS, "z.app", null)
        val second = fingerprint(PassiveSourceFamily.HRV_RMSSD, "a.app", "Band")

        val forward = PassivePipelineCodec.sortedFingerprintJson(linkedSetOf(first, second))
        val reverse = PassivePipelineCodec.sortedFingerprintJson(linkedSetOf(second, first))

        assertEquals(forward, reverse)
        assertEquals(setOf(first, second), PassivePipelineCodec.decodeFingerprints(forward))
    }

    @Test
    fun `window content canonicalizes feature and provenance order and has a frozen hash`() {
        val window = window()
        val reverse = window.copy(
            features = window.features.reversed(),
            provenanceRecordIds = window.provenanceRecordIds.reversed(),
        )

        val first = PassivePipelineCodec.windowEntity(
            window,
            baselineSegment = "segment-a",
            sourceUpdatedTime = 30L,
            ingestedAt = 40L,
            final = false,
            reason = RevisionReason.INITIAL,
            asOfTime = 50L,
        )
        val second = PassivePipelineCodec.windowEntity(
            reverse,
            baselineSegment = "segment-a",
            sourceUpdatedTime = 30L,
            ingestedAt = 40L,
            final = false,
            reason = RevisionReason.BACKFILL,
            asOfTime = 99L,
        )

        assertEquals(first.contentHash, second.contentHash)
        assertEquals("614b63e38f06819ef1b3ef19f811aea768aa624f262e31044f41a8db2b768f1c", first.contentHash)
        assertEquals("INITIAL", first.revisionReason)
        assertEquals("BACKFILL", second.revisionReason)
    }

    @Test
    fun `daily content is order independent and domain round trip is complete`() {
        val aggregate = aggregate()
        val reversed = aggregate.copy(
            passiveDay = aggregate.passiveDay.copy(
                features = aggregate.passiveDay.features.entries.reversed().associate { it.toPair() },
            ),
            windows = aggregate.windows.reversed().map {
                it.copy(
                    features = it.features.reversed(),
                    provenanceRecordIds = it.provenanceRecordIds.reversed(),
                )
            },
            readStates = aggregate.readStates.entries.reversed().associate { it.toPair() },
            coverageByFeature = aggregate.coverageByFeature.entries.reversed().associate { it.toPair() },
            missingFeatures = aggregate.missingFeatures.reversed().toSet(),
            exclusions = aggregate.exclusions.entries.reversed().associate { it.toPair() },
            finality = aggregate.finality.copy(
                perSourceLagMillis = aggregate.finality.perSourceLagMillis.entries.reversed()
                    .associate { it.toPair() },
            ),
            sourceLags = aggregate.sourceLags.reversed(),
        )

        val first = PassivePipelineCodec.dailyEntity(
            aggregate,
            provenanceIds = linkedSetOf("raw-z", "raw-a"),
            reason = RevisionReason.INITIAL,
            asOfTime = 50L,
        )
        val second = PassivePipelineCodec.dailyEntity(
            reversed,
            provenanceIds = linkedSetOf("raw-a", "raw-z"),
            reason = RevisionReason.BACKFILL,
            asOfTime = 99L,
        )

        assertEquals(aggregate.passiveDay, PassivePipelineCodec.dailyToDomain(first))
        assertEquals(first.contentHash, second.contentHash)
        assertEquals("76f552270008791873df84ba2b3cd1a66786aef9e010341013c5c237ab87a4f2", first.contentHash)
        assertEquals("BACKFILL", second.revisionReason)
    }

    @Test
    fun `duplicate feature rows use their complete stable identity`() {
        val aggregate = aggregate()
        val duplicate = PassiveWindowFeature(PassiveFeature.STEPS, 5.0, "count", 0.5, false, "PARTIAL")
        val forward = aggregate.copy(
            windows = aggregate.windows.map { it.copy(features = it.features + duplicate) },
        )
        val reverse = forward.copy(
            windows = forward.windows.map { it.copy(features = it.features.reversed()) }.reversed(),
        )

        val first = PassivePipelineCodec.dailyEntity(forward, emptySet(), RevisionReason.INITIAL, 50L)
        val second = PassivePipelineCodec.dailyEntity(reverse, emptySet(), RevisionReason.INITIAL, 50L)

        assertEquals(first.contentHash, second.contentHash)
    }

    @Test
    fun `daily decoder rejects unknown enum names`() {
        val entity = PassivePipelineCodec.dailyEntity(
            aggregate(),
            provenanceIds = emptySet(),
            reason = RevisionReason.INITIAL,
            asOfTime = 50L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            PassivePipelineCodec.dailyToDomain(entity.copy(dataStatus = "NOT_A_STATUS"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PassivePipelineCodec.dailyToDomain(entity.copy(featuresJson = "{\"NOT_A_FEATURE\":1.0}"))
        }
    }

    @Test
    fun `decision content round trips every nested value and has a frozen hash`() {
        val observation = observation()
        val reversed = observation.copy(
            domains = observation.domains.reversed().map { it.copy(features = it.features.reversed()) },
            baselineShift = observation.baselineShift?.let { shift ->
                shift.copy(domains = shift.domains.reversed().map { it.copy(features = it.features.reversed()) })
            },
        )

        val first = PassivePipelineCodec.decisionEntity(observation, RevisionReason.INITIAL)
        val second = PassivePipelineCodec.decisionEntity(reversed, RevisionReason.FINALITY)

        assertEquals(observation, PassivePipelineCodec.decisionToDomain(first))
        assertEquals(first.contentHash, second.contentHash)
        assertEquals("f8b938529e4a9407ddf06b6e8996b2c9a3f08f357746a6f385d762616a5798d8", first.contentHash)
        assertEquals(observation.calibration?.seed, first.calibrationSeed)
        assertEquals("FINALITY", second.revisionReason)
    }

    @Test
    fun `decision decoder rejects unknown enum names`() {
        val entity = PassivePipelineCodec.decisionEntity(observation(), RevisionReason.INITIAL)

        assertThrows(IllegalArgumentException::class.java) {
            PassivePipelineCodec.decisionToDomain(entity.copy(observationState = "NOT_A_STATE"))
        }
    }

    private fun record() = PassiveSourceRecord(
        sourceFamily = PassiveSourceFamily.STEPS,
        kind = PassiveRecordKind.STEPS_INTERVAL,
        eventStart = Instant.parse("2026-08-30T01:00:00Z").toEpochMilli(),
        eventEnd = Instant.parse("2026-08-30T01:15:00Z").toEpochMilli(),
        value = 120.0,
        unit = "count",
        dataOriginPackage = "source.app",
        deviceManufacturer = "Maker",
        deviceModel = "Model",
        deviceType = "WATCH",
        sourceUpdatedTime = Instant.parse("2026-08-30T02:00:00Z").toEpochMilli(),
        ingestedAt = Instant.parse("2026-08-30T03:00:00Z").toEpochMilli(),
        zoneId = "UTC",
        zoneOffsetSeconds = 0,
        recordId = "steps-1",
        recordVersion = 2L,
    )

    private fun fingerprint(family: PassiveSourceFamily, origin: String, model: String?) = PassiveSourceFingerprint(
        sourceFamily = family,
        dataOriginPackage = origin,
        deviceManufacturer = "Maker",
        deviceModel = model,
        deviceType = "WATCH",
    )

    private fun window(start: Long = 0L) = PassiveFeatureWindow(
        startInclusive = start,
        endExclusive = start + PassiveWindowAggregator.WINDOW_MILLIS,
        zoneId = "UTC",
        zoneOffsetSeconds = 0,
        quality = PassiveWindowQuality(0.8, true, 0L, 15),
        features = listOf(
            PassiveWindowFeature(PassiveFeature.STEPS, 120.0, "count", 1.0, true, null),
            PassiveWindowFeature(PassiveFeature.HRV_RMSSD, 42.0, "ms", 0.8, true, null),
        ),
        provenanceRecordIds = listOf("raw-z", "raw-a"),
    )

    private fun aggregate(): PassiveDailyAggregate {
        val day = PassiveDay(
            LocalDate.parse("2026-08-30"),
            PassiveDataStatus.AVAILABLE_FINAL,
            linkedMapOf(PassiveFeature.STEPS to 120.0, PassiveFeature.HRV_RMSSD to 42.0),
            linkedSetOf(PassiveFeature.RESTING_HEART_RATE),
            "segment-a",
            sourceUpdatedTime = 30L,
            ingestedAt = 40L,
        )
        return PassiveDailyAggregate(
            passiveDay = day,
            windows = listOf(window(), window(PassiveWindowAggregator.WINDOW_MILLIS)),
            readStates = linkedMapOf(
                PassiveSourceFamily.STEPS to PassiveReadState.SUCCESS,
                PassiveSourceFamily.HRV_RMSSD to PassiveReadState.SUCCESS,
            ),
            coverageByFeature = linkedMapOf(PassiveFeature.STEPS to 1.0, PassiveFeature.HRV_RMSSD to 0.8),
            missingFeatures = linkedSetOf(PassiveFeature.SLEEP_MINUTES, PassiveFeature.SCREEN_MINUTES),
            exclusions = linkedMapOf(PassiveFeature.RESTING_HEART_RATE to "EXERCISE_OVERLAP"),
            finality = PassiveFinalityDecision(
                watermark = 45L,
                final = true,
                perSourceLagMillis = linkedMapOf(
                    PassiveSourceFamily.STEPS to 10L,
                    PassiveSourceFamily.HRV_RMSSD to 20L,
                ),
            ),
            sourceLags = listOf(
                SourceLag(PassiveSourceFamily.STEPS, 10L, false),
                SourceLag(PassiveSourceFamily.HRV_RMSSD, 20L, true),
            ),
        )
    }

    private fun observation() = PassiveObservation(
        day = LocalDate.parse("2026-08-30"),
        asOfTime = 50L,
        dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        state = PassiveObservationState.BASELINE_SHIFT_CANDIDATE,
        threshold = 2.5,
        crossed = true,
        baselineDays = 60,
        frozenBaselineAsOfTime = 40L,
        frozenBaselineThroughDay = LocalDate.parse("2026-08-29"),
        baselineSegment = "segment-a",
        domains = listOf(
            DomainEvidence(
                PassiveDomain.ACTIVITY,
                2.75,
                listOf(FeatureEvidence(PassiveFeature.STEPS, 4_000.0, 7_000.0, 1_090.0, -2.75, 60, true)),
            ),
            DomainEvidence(
                PassiveDomain.SLEEP,
                3.0,
                listOf(FeatureEvidence(PassiveFeature.SLEEP_MINUTES, 420.0, 480.0, 20.0, -3.0, 60, false)),
            ),
        ),
        calibration = CalibrationResult(
            threshold = 2.5,
            expectedEpisodesPer30 = 1.0,
            simulations = 512,
            seed = 77L,
            configuration = CalibrationConfiguration(7, 30, 512, 1.0, 2),
        ),
        baselineShift = BaselineShiftAssessment(
            candidateDays = 56,
            standardizedDisagreementThreshold = 1.0,
            minimumCorroboratingDomains = 2,
            persistenceDays = 7,
            comparisonPopulation = BaselineComparisonPopulation.WEEKDAY,
            domains = listOf(
                BaselineShiftDomainEvidence(PassiveDomain.ACTIVITY, 1.2, listOf(PassiveFeature.STEPS)),
                BaselineShiftDomainEvidence(PassiveDomain.SLEEP, 1.5, listOf(PassiveFeature.SLEEP_MINUTES)),
            ),
            disagrees = true,
        ),
        explanation = "Recorded data only.",
    )
}
