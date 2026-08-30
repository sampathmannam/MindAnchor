package org.mindanchor.intelligence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PassiveBaselineSegmentEntity
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.research.TransformationRegistry

@RunWith(AndroidJUnit4::class)
@Suppress("LargeClass", "LongMethod")
class PassivePipelineAcceptanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val firstNow = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
    private val retainedOnlyAsProvenanceAt = Instant.parse("2026-08-10T00:00:00Z").toEpochMilli()

    @Test
    fun operationalHistoryIsAppendOnlyRestorableAndRawValuesExpire() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        try {
            val health = FakeHealthSource(initialHealthRecords())
            val usage = FakeUsageSource(usageRecords())
            var phaseChecks = 0
            var refreshes = 0
            val repository = PassivePipelineRepository(
                database = database,
                healthSource = health,
                usageSource = usage,
                historyPermissionGranted = { true },
                ensureCurrentPhase = { phaseChecks++ },
                refreshProvenanceAfterCommit = { refreshes++ },
            )
            val dao = database.passive()

            val first = repository.run(firstNow, zone)

            assertTrue(first is PassivePipelineResult.Completed)
            assertEquals(120, health.requestedLocalDayCounts.single())
            assertTrue(dao.windowRevisionsNow().isNotEmpty())
            assertTrue(dao.dailyRevisionsNow().any { it.dataStatus == "AVAILABLE_PROVISIONAL" })
            assertPersistedWindowAndSourceSemantics(database)

            health.missingFamilies += PassiveSourceFamily.HRV_RMSSD
            health.addLateSleepRecord(sourceUpdatedTime = firstNow + 49L * HOUR_MILLIS)
            val segmentsBeforeBackfill = dao.baselineSegmentsNow().map { it.id }
            val backfill = repository.run(firstNow + 54L * HOUR_MILLIS, zone)

            assertTrue(backfill is PassivePipelineResult.Completed)
            assertEquals(7, health.requestedLocalDayCounts.last())
            assertEquals(segmentsBeforeBackfill, dao.baselineSegmentsNow().map { it.id })
            assertTrue(dao.sourceReadsNow().any {
                it.sourceFamily == PassiveSourceFamily.HRV_RMSSD.name && it.state == "SUCCESS" && it.recordCount == 0
            })
            val revisions = dao.dailyRevisionsNow().groupBy { it.localDate }.values.maxBy { it.size }
            assertTrue(revisions.any { it.revisionReason == "BACKFILL" })
            assertTrue(revisions.any { it.dataStatus == "AVAILABLE_FINAL" })
            val decisions = dao.observationDecisionsNow()
            val backfillDecisions = decisions.filter { it.localDate == "2026-08-27" }
            assertEquals(listOf("INITIAL", "BACKFILL"), backfillDecisions.map { it.revisionReason })
            assertEquals(
                listOf(firstNow, firstNow + 54L * HOUR_MILLIS),
                backfillDecisions.map { it.asOfTime },
            )
            assertEquals(
                listOf("BASELINE_BUILDING", "BASELINE_BUILDING"),
                backfillDecisions.map { it.dataStatus },
            )
            val finalityDecisions = decisions.filter { it.localDate == "2026-08-29" }
            assertEquals(listOf("INITIAL", "FINALITY"), finalityDecisions.map { it.revisionReason })
            assertEquals(
                listOf(firstNow, firstNow + 54L * HOUR_MILLIS),
                finalityDecisions.map { it.asOfTime },
            )
            assertEquals(
                listOf("AVAILABLE_PROVISIONAL", "BASELINE_BUILDING"),
                finalityDecisions.map { it.dataStatus },
            )

            val beforeNoOp = dao.dailyRevisionsNow().size
            repository.run(firstNow + 60L * HOUR_MILLIS, zone)
            assertEquals(beforeNoOp, dao.dailyRevisionsNow().size)

            val provenanceBeforePrune = dao.rawProvenanceNow()
            val readsBeforePrune = dao.sourceReadsNow()
            val lagsBeforePrune = dao.sourceLagsNow()
            val segmentsBeforePrune = dao.baselineSegmentsNow()
            val runsBeforePrune = dao.pipelineRunsNow()
            val windowsBeforePrune = dao.windowRevisionsNow()
            val daysBeforePrune = dao.dailyRevisionsNow()
            val decisionsBeforePrune = dao.observationDecisionsNow()
            dao.pruneRawSamples(firstNow + 60L * HOUR_MILLIS - PassivePipelineWorker.RAW_RETENTION_MILLIS)
            assertTrue(dao.rawRecords(0L, firstNow).isEmpty())
            assertEquals(provenanceBeforePrune, dao.rawProvenanceNow())
            assertEquals(readsBeforePrune, dao.sourceReadsNow())
            assertEquals(lagsBeforePrune, dao.sourceLagsNow())
            assertEquals(segmentsBeforePrune, dao.baselineSegmentsNow())
            assertEquals(runsBeforePrune, dao.pipelineRunsNow())
            assertEquals(windowsBeforePrune, dao.windowRevisionsNow())
            assertEquals(daysBeforePrune, dao.dailyRevisionsNow())
            assertEquals(decisionsBeforePrune, dao.observationDecisionsNow())
            assertEquals(listOf("INITIAL", "BACKFILL"),
                dao.observationDecisionsNow().filter { it.localDate == "2026-08-27" }.map { it.revisionReason })
            assertEquals(listOf("INITIAL", "FINALITY"),
                dao.observationDecisionsNow().filter { it.localDate == "2026-08-29" }.map { it.revisionReason })
            assertEquals(3, phaseChecks)
            assertEquals(3, refreshes)

            assertPureAggregationAndFinalitySemantics()
        } finally {
            database.close()
        }
    }

    @Test
    fun sourceOutcomesArePersistedThroughRepositoryRuns() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val deniedUsage = OutcomeSource { range ->
                listOf(outcome(PassiveSourceFamily.USAGE_STATS, PassiveReadState.PERMISSION_DENIED, range))
            }
            val successfulEmpty = OutcomeSource { range ->
                listOf(outcome(PassiveSourceFamily.SLEEP, PassiveReadState.SUCCESS, range))
            }
            assertTrue(
                repository(database, successfulEmpty, deniedUsage).run(firstNow, zone) is PassivePipelineResult.Completed,
            )

            val deniedAndUnavailable = OutcomeSource { range ->
                listOf(
                    outcome(PassiveSourceFamily.HRV_RMSSD, PassiveReadState.PERMISSION_DENIED, range),
                    outcome(PassiveSourceFamily.OXYGEN_SATURATION, PassiveReadState.UNAVAILABLE, range),
                )
            }
            assertTrue(
                repository(database, deniedAndUnavailable, deniedUsage)
                    .run(firstNow + HOUR_MILLIS, zone) is PassivePipelineResult.Completed,
            )

            val permanent = OutcomeSource { range ->
                listOf(outcome(PassiveSourceFamily.EXERCISE, PassiveReadState.READ_FAILURE_PERMANENT, range))
            }
            assertTrue(
                repository(database, permanent, deniedUsage)
                    .run(firstNow + 2L * HOUR_MILLIS, zone) is PassivePipelineResult.Completed,
            )

            val transientSource = OutcomeSource { range ->
                listOf(outcome(PassiveSourceFamily.HEART_RATE, PassiveReadState.READ_FAILURE_TRANSIENT, range))
            }
            assertTrue(
                repository(database, transientSource, deniedUsage)
                    .run(firstNow + 3L * HOUR_MILLIS, zone) is PassivePipelineResult.Retry,
            )

            val runs = database.passive().pipelineRunsNow()
            assertEquals(
                listOf("SUCCESS_PERMISSIONED", "SUCCESS_NO_PERMISSION", "SUCCESS_WITH_FAILURES", "RETRY_TRANSIENT"),
                runs.map { it.result },
            )
            val reads = database.passive().sourceReadsNow()
            val successfulRead = reads.single { it.sourceFamily == "SLEEP" }
            assertEquals("SUCCESS", successfulRead.state)
            assertEquals(0, successfulRead.recordCount)
            val deniedRead = reads.first { it.sourceFamily == "HRV_RMSSD" }
            assertEquals("PERMISSION_DENIED", deniedRead.state)
            assertEquals("PERMISSION_DENIED", deniedRead.errorCode)
            assertEquals("UNAVAILABLE", reads.single { it.sourceFamily == "OXYGEN_SATURATION" }.state)
            assertEquals("READ_FAILURE_PERMANENT", reads.single { it.sourceFamily == "EXERCISE" }.state)
            val transientRead = reads.single {
                it.sourceFamily == "HEART_RATE"
            }
            assertEquals("READ_FAILURE_TRANSIENT", transientRead.state)
            assertEquals("READ_FAILURE_TRANSIENT", transientRead.errorCode)
            assertTrue(runs[2].sourceStatesJson.contains("READ_FAILURE_PERMANENT"))
            assertTrue(runs.last().sourceStatesJson.contains("READ_FAILURE_TRANSIENT"))
        } finally {
            database.close()
        }
    }

    @Test
    fun repositoryPreservesAndChangesStoredSegmentsWithDeviceFingerprint() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = MutableRecordSource(
                record(
                    PassiveSourceFamily.STEPS,
                    PassiveRecordKind.STEPS_INTERVAL,
                    firstNow - 2L * HOUR_MILLIS,
                    firstNow - HOUR_MILLIS,
                    500.0,
                    "band-steps",
                ),
            )
            val repository = repository(database, source, EmptySuccessSource(PassiveSourceFamily.USAGE_STATS))

            repository.run(firstNow, zone)
            val original = database.passive().baselineSegmentsNow().single()

            source.records = emptyList()
            repository.run(firstNow + 6L * HOUR_MILLIS, zone)
            assertEquals(listOf(original), database.passive().baselineSegmentsNow())

            source.records = listOf(
                record(
                    PassiveSourceFamily.STEPS,
                    PassiveRecordKind.STEPS_INTERVAL,
                    firstNow + 7L * HOUR_MILLIS,
                    firstNow + 8L * HOUR_MILLIS,
                    750.0,
                    "band-2-steps",
                    deviceModel = "Band 2",
                ),
            )
            repository.run(firstNow + 12L * HOUR_MILLIS, zone)

            val segments = database.passive().baselineSegmentsNow()
            assertEquals(2, segments.size)
            assertEquals(original, segments.first())
            assertFalse(original.id == segments.last().id)
            val fingerprints = PassivePipelineCodec.decodeFingerprints(segments.last().fingerprintsJson)
            assertEquals(setOf("Band", "Band 2"), fingerprints.mapNotNull { it.deviceModel }.toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun repositoryPersistsTheIndependentlyDerivedSignedCalibrationSeed() = runBlocking {
        val database = inMemoryDatabase()
        val utc = ZoneId.of("UTC")
        try {
            val target = LocalDate.parse("2026-08-27")
            val fingerprints = setOf(PassiveSourceFamily.STEPS, PassiveSourceFamily.SLEEP).map { family ->
                PassiveSourceFingerprint(family, "fake.health", "Maker", "Band", "WATCH")
            }.toSet()
            val segmentId = PassiveBaselineSegment.id(
                fingerprints,
                PassiveWindowAggregator.TRANSFORMATION_VERSION,
                PassiveDailyAggregator.TRANSFORMATION_VERSION,
            )
            database.passive().insertBaselineSegment(
                PassiveBaselineSegmentEntity(
                    segmentId,
                    target.minusDays(61L).atStartOfDay(utc).toInstant().toEpochMilli(),
                    PassivePipelineCodec.sortedFingerprintJson(fingerprints),
                    PassiveWindowAggregator.TRANSFORMATION_VERSION,
                    PassiveDailyAggregator.TRANSFORMATION_VERSION,
                ),
            )
            val history = (60 downTo 1).mapIndexed { index, daysBefore ->
                val date = target.minusDays(daysBefore.toLong())
                val ingestedAt = date.plusDays(1L).atStartOfDay(utc).toInstant().toEpochMilli() + HOUR_MILLIS
                PassivePipelineCodec.dailyEntity(
                    baselineAggregate(date, segmentId, ingestedAt, index),
                    emptySet(),
                    RevisionReason.INITIAL,
                    ingestedAt,
                )
            }
            database.passive().insertDailyRevisions(history)
            val dayStart = target.atStartOfDay(utc).toInstant().toEpochMilli()
            val targetRecords = listOf(
                record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL,
                    dayStart + 8L * HOUR_MILLIS, dayStart + 9L * HOUR_MILLIS,
                    8_000.0, "seed-steps", zoneOverride = utc),
                record(PassiveSourceFamily.SLEEP, PassiveRecordKind.SLEEP_SESSION,
                    dayStart - 8L * HOUR_MILLIS, dayStart + 7L * HOUR_MILLIS,
                    null, "seed-sleep", zoneOverride = utc),
            )
            val source = OutcomeSource { range ->
                targetRecords.groupBy { it.sourceFamily }.map { (family, records) ->
                    PassiveSourceRead(family, PassiveReadState.SUCCESS, range, range.endExclusive, records)
                }
            }

            repository(database, source, EmptySuccessSource(PassiveSourceFamily.USAGE_STATS))
                .run(firstNow, utc)

            val decision = database.passive().observationDecisionsNow().single { it.localDate == target.toString() }
            val frozenAsOf = history.maxOf { it.ingestedAt }
            val calibrationVersion = requireNotNull(TransformationRegistry.versionOf("passive-block-calibration"))
            val expected = ByteBuffer.wrap(
                MessageDigest.getInstance("SHA-256")
                    .digest("$segmentId|$frozenAsOf|$calibrationVersion".encodeToByteArray()),
            ).long
            assertEquals(expected, decision.calibrationSeed)
            assertEquals(expected, PassivePipelineCodec.decisionToDomain(decision).calibration?.seed)
            assertEquals(60, PassivePipelineCodec.decisionToDomain(decision).baselineDays)
        } finally {
            database.close()
        }
    }

    private suspend fun assertPersistedWindowAndSourceSemantics(database: AnchorDatabase) {
        val dao = database.passive()
        val day = LocalDate.parse("2026-08-29")
        val eligibleStart = at(day, 10, 0)
        val sevenStart = at(day, 10, 15)
        val exerciseStart = at(day, 10, 30)
        val windows = dao.windowRevisionsNow().associateBy { it.windowStart }
        val eligible = requireNotNull(windows[eligibleStart])
        val seven = requireNotNull(windows[sevenStart])
        val exercise = requireNotNull(windows[exerciseStart])

        assertEquals(8.0 / 15.0, eligible.heartRateCoverage, 0.000_001)
        assertTrue(eligible.physiologyEligible)
        assertEquals(7.0 / 15.0, seven.heartRateCoverage, 0.000_001)
        assertFalse(seven.physiologyEligible)
        assertTrue(exercise.exerciseOverlapMillis > 0L)
        assertFalse(exercise.physiologyEligible)
        assertTrue(exercise.featureRowsJson.contains("STEPS"))
        assertTrue(exercise.featureRowsJson.contains("\"eligible\":true"))
        assertTrue(eligible.featureRowsJson.contains("SPO2_PERCENT"))
        assertTrue(dao.rawProvenanceNow().any { it.recordKind == PassiveRecordKind.SPO2.name })
        assertTrue(dao.observationDecisionsNow().none { it.decisionJson.contains("SPO2_PERCENT") })
        assertFalse(PassiveFeature.SPO2_PERCENT.scored)

        val reads = dao.sourceReadsNow()
        assertTrue(reads.any { it.state == "SUCCESS" && it.recordCount > 0 })
        assertTrue(dao.sourceLagsNow().any { it.usedIngestedAtFallback })
        val target = dao.dailyRevisionsNow().first { it.localDate == day.toString() }
        assertTrue(target.featuresJson.contains("SLEEP_MINUTES"))
        assertTrue(target.featuresJson.contains("FIRST_UNLOCK_MINUTE"))
        assertTrue(target.featuresJson.contains("SCREEN_MINUTES"))
    }

    private fun assertPureAggregationAndFinalitySemantics() {
        val target = LocalDate.parse("2026-08-29")
        val sleep = initialHealthRecords().single { it.recordId == "target-sleep" }
        val wrongDay = PassiveDailyAggregator.aggregate(
            target.minusDays(1L),
            zone,
            emptyList(),
            listOf(sleep),
            emptyList(),
            "segment",
            firstNow,
            PassiveFinalityDecision(firstNow, true, emptyMap()),
        )
        val wakeDay = PassiveDailyAggregator.aggregate(
            target,
            zone,
            emptyList(),
            listOf(sleep),
            emptyList(),
            "segment",
            firstNow,
            PassiveFinalityDecision(firstNow, true, emptyMap()),
        )
        assertFalse(wrongDay.passiveDay.features.containsKey(PassiveFeature.SLEEP_MINUTES))
        assertEquals(480.0, wakeDay.passiveDay.features[PassiveFeature.SLEEP_MINUTES]!!, 0.0)

        val dstZone = ZoneId.of("America/New_York")
        val dstDay = LocalDate.parse("2026-03-08")
        val dstStart = dstDay.atStartOfDay(dstZone).toInstant().toEpochMilli()
        val dstEnd = dstDay.plusDays(1L).atStartOfDay(dstZone).toInstant().toEpochMilli()
        val crossingSteps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dstStart - HOUR_MILLIS,
            dstStart + HOUR_MILLIS,
            240.0,
            "dst-steps",
            zoneOverride = dstZone,
        )
        val crossingExercise = record(
            PassiveSourceFamily.EXERCISE,
            PassiveRecordKind.EXERCISE_SESSION,
            dstStart - HOUR_MILLIS,
            dstStart + HOUR_MILLIS,
            null,
            "dst-exercise",
            zoneOverride = dstZone,
        )
        val clipped = PassiveDailyAggregator.aggregate(
            dstDay,
            dstZone,
            emptyList(),
            listOf(crossingSteps, crossingExercise),
            emptyList(),
            "segment",
            dstEnd,
            PassiveFinalityDecision(dstEnd, true, emptyMap()),
        )
        assertEquals(23L * HOUR_MILLIS, dstEnd - dstStart)
        assertEquals(120.0, clipped.passiveDay.features[PassiveFeature.STEPS]!!, 0.0)
        assertEquals(60.0, clipped.passiveDay.features[PassiveFeature.ACTIVE_MINUTES]!!, 0.0)

        val stepOnly = PassiveDailyAggregator.aggregate(
            target,
            zone,
            emptyList(),
            initialHealthRecords().filter { it.recordId == "target-steps" },
            emptyList(),
            "segment",
            firstNow,
            PassiveFinalityDecision(firstNow, true, emptyMap()),
        )
        assertEquals(PassiveDataStatus.INSUFFICIENT_DATA, stepOnly.passiveDay.dataStatus)

        val dayEnd = Instant.parse("2026-08-31T00:00:00Z").toEpochMilli()
        val first29 = (1L..29L).map { SourceLag(PassiveSourceFamily.SLEEP, it * HOUR_MILLIS, false) }
        val bootstrap = PassiveFinality.watermark(
            dayEnd,
            setOf(PassiveSourceFamily.SLEEP),
            first29,
            dayEnd + PassiveFinality.BOOTSTRAP_LAG_MILLIS,
        )
        assertEquals(PassiveFinality.BOOTSTRAP_LAG_MILLIS, bootstrap.perSourceLagMillis[PassiveSourceFamily.SLEEP])
        val thirty = PassiveFinality.watermark(
            dayEnd,
            setOf(PassiveSourceFamily.SLEEP),
            first29 + SourceLag(PassiveSourceFamily.SLEEP, 30L * HOUR_MILLIS, true),
            dayEnd + 1L,
        )
        assertEquals(30L * HOUR_MILLIS, thirty.perSourceLagMillis[PassiveSourceFamily.SLEEP])
        val clamped = PassiveFinality.watermark(
            dayEnd,
            setOf(PassiveSourceFamily.STEPS, PassiveSourceFamily.EXERCISE),
            List(30) { SourceLag(PassiveSourceFamily.STEPS, 1L, false) } +
                List(30) { SourceLag(PassiveSourceFamily.EXERCISE, Long.MAX_VALUE, false) },
            Long.MAX_VALUE,
        )
        assertEquals(PassiveFinality.MIN_LAG_MILLIS, clamped.perSourceLagMillis[PassiveSourceFamily.STEPS])
        assertEquals(PassiveFinality.MAX_LAG_MILLIS, clamped.perSourceLagMillis[PassiveSourceFamily.EXERCISE])
    }

    private fun initialHealthRecords(): List<PassiveSourceRecord> {
        val target = LocalDate.parse("2026-08-29")
        val old = LocalDate.parse("2026-08-27")
        val eligible = at(target, 10, 0)
        val seven = at(target, 10, 15)
        val exercise = at(target, 10, 30)
        fun heartRateBins(start: Long, count: Int, prefix: String) = (0 until count).map { minute ->
            record(
                PassiveSourceFamily.HEART_RATE,
                PassiveRecordKind.HEART_RATE_SAMPLE,
                start + minute * 60_000L,
                start + minute * 60_000L,
                70.0 + minute,
                "$prefix-$minute",
            )
        }
        return heartRateBins(eligible, 8, "eligible-hr") +
            heartRateBins(seven, 7, "seven-hr") +
            heartRateBins(exercise, 8, "exercise-hr") + listOf(
                record(PassiveSourceFamily.RESTING_HEART_RATE, PassiveRecordKind.RESTING_HEART_RATE,
                    eligible + 30_000L, eligible + 30_000L, 60.0, "eligible-rhr"),
                record(PassiveSourceFamily.RESTING_HEART_RATE, PassiveRecordKind.RESTING_HEART_RATE,
                    seven + 30_000L, seven + 30_000L, 61.0, "seven-rhr"),
                record(PassiveSourceFamily.RESTING_HEART_RATE, PassiveRecordKind.RESTING_HEART_RATE,
                    exercise + 30_000L, exercise + 30_000L, 62.0, "exercise-rhr"),
                record(PassiveSourceFamily.HRV_RMSSD, PassiveRecordKind.HRV_RMSSD,
                    eligible + 45_000L, eligible + 45_000L, 42.0, "eligible-hrv"),
                record(PassiveSourceFamily.HRV_RMSSD, PassiveRecordKind.HRV_RMSSD,
                    exercise + 45_000L, exercise + 45_000L, 40.0, "exercise-hrv"),
                record(PassiveSourceFamily.OXYGEN_SATURATION, PassiveRecordKind.SPO2,
                    eligible + 60_000L, eligible + 60_000L, 98.0, "target-spo2"),
                record(PassiveSourceFamily.EXERCISE, PassiveRecordKind.EXERCISE_SESSION,
                    exercise + 5L * 60_000L, exercise + 10L * 60_000L, null, "target-exercise"),
                record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL,
                    exercise, exercise + 15L * 60_000L, 150.0, "target-steps"),
                record(PassiveSourceFamily.SLEEP, PassiveRecordKind.SLEEP_SESSION,
                    at(target.minusDays(1L), 22, 0), at(target, 6, 0), null, "target-sleep"),
                record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL,
                    at(old, 12, 0), at(old, 13, 0), 1_000.0, "old-steps"),
                record(PassiveSourceFamily.SLEEP, PassiveRecordKind.SLEEP_SESSION,
                    at(old.minusDays(1L), 23, 0), at(old, 7, 0), null, "old-sleep"),
            )
    }

    private fun usageRecords(): List<PassiveSourceRecord> {
        val day = LocalDate.parse("2026-08-29")
        return listOf(
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_NON_INTERACTIVE,
                at(day.minusDays(1L), 23, 50), at(day.minusDays(1L), 23, 50), null, "screen-anchor",
                sourceUpdatedTime = null),
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_UNLOCKED,
                at(day, 7, 15), at(day, 7, 15), null, "first-unlock"),
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_INTERACTIVE,
                at(day, 7, 15), at(day, 7, 15), null, "screen-on"),
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_NON_INTERACTIVE,
                at(day, 7, 45), at(day, 7, 45), null, "screen-off"),
        )
    }

    @Suppress("LongParameterList")
    private fun record(
        family: PassiveSourceFamily,
        kind: PassiveRecordKind,
        start: Long,
        end: Long,
        value: Double?,
        id: String,
        sourceUpdatedTime: Long? = end + HOUR_MILLIS,
        deviceModel: String = "Band",
        zoneOverride: ZoneId = zone,
    ) = PassiveSourceRecord(
        sourceFamily = family,
        kind = kind,
        eventStart = start,
        eventEnd = end,
        value = value,
        unit = when (kind) {
            PassiveRecordKind.HEART_RATE_SAMPLE, PassiveRecordKind.RESTING_HEART_RATE -> "bpm"
            PassiveRecordKind.HRV_RMSSD -> "ms"
            PassiveRecordKind.SPO2 -> "percent"
            PassiveRecordKind.STEPS_INTERVAL -> "count"
            else -> "milliseconds"
        },
        dataOriginPackage = if (family == PassiveSourceFamily.USAGE_STATS) "fake.usage" else "fake.health",
        deviceManufacturer = "Maker",
        deviceModel = deviceModel,
        deviceType = if (family == PassiveSourceFamily.USAGE_STATS) "PHONE" else "WATCH",
        sourceUpdatedTime = sourceUpdatedTime,
        ingestedAt = retainedOnlyAsProvenanceAt,
        zoneId = zoneOverride.id,
        zoneOffsetSeconds = zoneOverride.rules.getOffset(Instant.ofEpochMilli(start)).totalSeconds,
        recordId = id,
        recordVersion = 1L,
    )

    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun baselineAggregate(
        date: LocalDate,
        segment: String,
        ingestedAt: Long,
        index: Int,
    ) = PassiveDailyAggregate(
        passiveDay = PassiveDay(
            date,
            PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(
                PassiveFeature.STEPS to 5_000.0 + index * 25.0,
                PassiveFeature.SLEEP_MINUTES to 420.0 + (index % 7) * 5.0,
            ),
            baselineSegment = segment,
            sourceUpdatedTime = ingestedAt,
            ingestedAt = ingestedAt,
        ),
        windows = emptyList(),
        readStates = mapOf(
            PassiveSourceFamily.STEPS to PassiveReadState.SUCCESS,
            PassiveSourceFamily.SLEEP to PassiveReadState.SUCCESS,
        ),
        coverageByFeature = mapOf(PassiveFeature.STEPS to 1.0, PassiveFeature.SLEEP_MINUTES to 1.0),
        missingFeatures = PassiveFeature.entries.filter {
            it != PassiveFeature.STEPS && it != PassiveFeature.SLEEP_MINUTES
        }.toSet(),
        exclusions = emptyMap(),
        finality = PassiveFinalityDecision(ingestedAt, true, emptyMap()),
        sourceLags = emptyList(),
    )

    private fun inMemoryDatabase(): AnchorDatabase = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
        .withResearchImmutability()
        .build()

    private fun repository(
        database: AnchorDatabase,
        health: PassiveRecordSource,
        usage: PassiveRecordSource,
    ) = PassivePipelineRepository(
        database = database,
        healthSource = health,
        usageSource = usage,
        historyPermissionGranted = { true },
        ensureCurrentPhase = {},
        refreshProvenanceAfterCommit = {},
    )

    private fun outcome(
        family: PassiveSourceFamily,
        state: PassiveReadState,
        range: PassiveReadRange,
    ) = PassiveSourceRead(
        sourceFamily = family,
        state = state,
        range = range,
        attemptedAt = range.endExclusive,
        errorCode = state.name.takeUnless { state == PassiveReadState.SUCCESS },
    )

    private class OutcomeSource(
        private val reads: (PassiveReadRange) -> List<PassiveSourceRead>,
    ) : PassiveRecordSource {
        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> = reads(range)
    }

    private class EmptySuccessSource(
        private val family: PassiveSourceFamily,
    ) : PassiveRecordSource {
        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> = listOf(
            PassiveSourceRead(family, PassiveReadState.SUCCESS, range, range.endExclusive),
        )
    }

    private class MutableRecordSource(vararg initial: PassiveSourceRecord) : PassiveRecordSource {
        var records: List<PassiveSourceRecord> = initial.toList()

        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> = listOf(
            PassiveSourceRead(
                PassiveSourceFamily.STEPS,
                PassiveReadState.SUCCESS,
                range,
                range.endExclusive,
                records.filter { it.eventStart < range.endExclusive && it.eventEnd > range.startInclusive },
            ),
        )
    }

    private inner class FakeHealthSource(initial: List<PassiveSourceRecord>) : PassiveRecordSource {
        private val records = initial.toMutableList()
        val requestedLocalDayCounts = mutableListOf<Int>()
        val missingFamilies = mutableSetOf<PassiveSourceFamily>()

        fun addLateSleepRecord(sourceUpdatedTime: Long) {
            val day = LocalDate.parse("2026-08-27")
            records += record(
                PassiveSourceFamily.SLEEP,
                PassiveRecordKind.SLEEP_SESSION,
                at(day.minusDays(1L), 21, 30),
                at(day, 6, 30),
                null,
                "late-old-sleep",
                sourceUpdatedTime = sourceUpdatedTime,
            )
        }

        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
            val start = Instant.ofEpochMilli(range.startInclusive).atZone(zone).toLocalDate()
            val end = Instant.ofEpochMilli(range.endExclusive - 1L).atZone(zone).toLocalDate()
            requestedLocalDayCounts += ChronoUnit.DAYS.between(start, end).toInt() + 1
            return PassiveSourceFamily.entries.filter { it != PassiveSourceFamily.USAGE_STATS }.map { family ->
                val matching = if (family in missingFamilies) emptyList() else records.filter { record ->
                    record.sourceFamily == family && record.eventStart < range.endExclusive &&
                        record.eventEnd.coerceAtLeast(record.eventStart + 1L) > range.startInclusive
                }
                PassiveSourceRead(family, PassiveReadState.SUCCESS, range, range.endExclusive, matching)
            }
        }
    }

    private inner class FakeUsageSource(private val records: List<PassiveSourceRecord>) : PassiveRecordSource {
        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> = listOf(
            PassiveSourceRead(
                PassiveSourceFamily.USAGE_STATS,
                PassiveReadState.SUCCESS,
                range,
                range.endExclusive,
                records.filter { record ->
                    record.eventStart < range.endExclusive &&
                        record.eventEnd.coerceAtLeast(record.eventStart + 1L) > range.startInclusive
                },
            ),
        )
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1_000L
    }
}
