package org.mindanchor.intelligence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PassiveBaselineSegmentEntity
import org.mindanchor.research.TransformationRegistry
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("LargeClass")
class PassivePipelineRepositoryTest {
    private lateinit var database: AnchorDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `first history-granted run scans 120 days then normal runs rescan seven`() = runBlocking {
        val health = FakeSource(PassiveSourceFamily.STEPS)
        val usage = FakeSource(PassiveSourceFamily.USAGE_STATS)
        val repository = repository(health = health, usage = usage, historyGranted = { true })
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()

        assertTrue(repository.run(now, ZoneId.of("UTC")) is PassivePipelineResult.Completed)
        assertEquals(
            LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            health.ranges.single().startInclusive,
        )

        assertTrue(repository.run(now + 6L * 3_600_000L, ZoneId.of("UTC")) is PassivePipelineResult.Completed)
        assertEquals(
            LocalDate.parse("2026-08-24").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            health.ranges.last().startInclusive,
        )
    }

    @Test
    fun `denied and unavailable reads are persisted while transient provider errors retry`() = runBlocking {
        val now = 2_000L
        val range = PassiveReadRange(1L, now, "UTC")
        val source = FakeSource(
            reads = listOf(
                PassiveSourceRead(
                    PassiveSourceFamily.SLEEP,
                    PassiveReadState.PERMISSION_DENIED,
                    range,
                    1_000L,
                    errorCode = "READ_SLEEP_DENIED",
                ),
                PassiveSourceRead(
                    PassiveSourceFamily.USAGE_STATS,
                    PassiveReadState.UNAVAILABLE,
                    range,
                    1_000L,
                    errorCode = "USAGE_STATS_UNAVAILABLE",
                ),
                PassiveSourceRead(
                    PassiveSourceFamily.HEART_RATE,
                    PassiveReadState.READ_FAILURE_TRANSIENT,
                    range,
                    1_000L,
                    errorCode = "DeadObjectException",
                ),
            ),
        )

        val result = repository(health = source, usage = FakeSource(reads = emptyList())).run(now, ZoneId.of("UTC"))

        assertTrue(result is PassivePipelineResult.Retry)
        assertEquals(
            setOf("PERMISSION_DENIED", "UNAVAILABLE", "READ_FAILURE_TRANSIENT"),
            database.passive().sourceReadsNow().map { it.state }.toSet(),
        )
    }

    @Test
    fun `first run without history permission scans 30 days and classifies permanent failures`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val source = RangeSource { range ->
            listOf(
                PassiveSourceRead(
                    PassiveSourceFamily.STEPS,
                    PassiveReadState.READ_FAILURE_PERMANENT,
                    range,
                    now,
                    errorCode = "IllegalStateException",
                ),
            )
        }

        val result = repository(source, FakeSource(reads = emptyList())).run(now, ZoneId.of("UTC"))

        assertTrue(result is PassivePipelineResult.Completed)
        assertEquals(
            LocalDate.parse("2026-08-01").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            source.ranges.single().startInclusive,
        )
        assertEquals("SUCCESS_WITH_FAILURES", database.passive().pipelineRunsNow().single().result)
    }

    @Test
    fun `raw value provenance lag fallback and configured fingerprints are persisted`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            Instant.parse("2026-08-30T01:00:00Z").toEpochMilli(),
            Instant.parse("2026-08-30T01:15:00Z").toEpochMilli(),
            "steps-a",
            value = 120.0,
            sourceUpdatedTime = null,
            ingestedAt = Instant.parse("2026-08-30T02:00:00Z").toEpochMilli(),
        )

        val result = repository(
            RangeSource { range -> listOf(successRead(PassiveSourceFamily.STEPS, range, now, listOf(steps))) },
            FakeSource(reads = emptyList()),
        ).run(now, ZoneId.of("UTC")) as PassivePipelineResult.Completed

        val provenance = database.passive().rawProvenanceNow().single()
        val lag = database.passive().sourceLagsNow().single()
        val segment = database.passive().baselineSegmentsNow().single()
        assertEquals(PassivePipelineCodec.rawIdentity(steps), provenance.id)
        assertEquals(steps.recordId, provenance.recordId)
        assertTrue(lag.usedIngestedAtFallback)
        assertEquals(steps.ingestedAt - steps.eventEnd, lag.lagMillis)
        assertEquals(
            setOf(fingerprint(steps)),
            PassivePipelineCodec.decodeFingerprints(segment.fingerprintsJson),
        )
        assertTrue(result.insertedWindows > 0)
        assertEquals(1, result.insertedDays)
        assertEquals(1, result.insertedDecisions)
    }

    @Test
    fun `configured segment retains missing fingerprints and opens only for new scored fingerprints`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val dayStart = LocalDate.parse("2026-08-30").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayStart + 60_000L,
            dayStart + 120_000L,
            "steps-a",
            value = 10.0,
        )
        val hrv = record(
            PassiveSourceFamily.HRV_RMSSD,
            PassiveRecordKind.HRV_RMSSD,
            dayStart + 180_000L,
            dayStart + 180_000L,
            "hrv-a",
            value = 40.0,
        )
        val quality = record(
            PassiveSourceFamily.HEART_RATE,
            PassiveRecordKind.HEART_RATE_SAMPLE,
            dayStart + 240_000L,
            dayStart + 240_000L,
            "hr-a",
            value = 70.0,
        )
        val spo2 = record(
            PassiveSourceFamily.OXYGEN_SATURATION,
            PassiveRecordKind.SPO2,
            dayStart + 300_000L,
            dayStart + 300_000L,
            "spo2-a",
            value = 98.0,
        )
        val records = mutableListOf(steps)
        val source = RangeSource { range ->
            records.groupBy { it.sourceFamily }.map { (family, familyRecords) ->
                successRead(family, range, now, familyRecords)
            }
        }
        val repository = repository(source, FakeSource(reads = emptyList()))

        repository.run(now, ZoneId.of("UTC"))
        records.clear()
        records += listOf(hrv, quality, spo2)
        repository.run(now + 1_000L, ZoneId.of("UTC"))

        val segments = database.passive().baselineSegmentsNow()
        assertEquals(2, segments.size)
        assertEquals(
            setOf(fingerprint(steps), fingerprint(hrv)),
            PassivePipelineCodec.decodeFingerprints(segments.last().fingerprintsJson),
        )
        assertFalse(PassivePipelineCodec.decodeFingerprints(segments.last().fingerprintsJson).any {
            it.sourceFamily == PassiveSourceFamily.HEART_RATE ||
                it.sourceFamily == PassiveSourceFamily.OXYGEN_SATURATION
        })
        assertNotEquals(segments.first().id, segments.last().id)
    }

    @Test
    fun `same-millisecond source revisions retain distinct runs and the newly opened segment`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val first = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            now - 120_000L,
            now - 60_000L,
            "steps-a",
            value = 10.0,
        )
        val second = first.copy(
            recordId = "steps-b",
            deviceModel = "New Model",
            eventStart = first.eventStart + 1L,
            eventEnd = first.eventEnd + 1L,
        )
        val records = mutableListOf(first)
        val source = RangeSource { range ->
            listOf(successRead(PassiveSourceFamily.STEPS, range, now, records.toList()))
        }
        val repository = repository(source, FakeSource(reads = emptyList()))

        repository.run(now, ZoneId.of("UTC"))
        records += second
        repository.run(now, ZoneId.of("UTC"))

        assertEquals(2, database.passive().pipelineRunsNow().size)
        assertEquals(2, database.passive().baselineSegmentsNow().size)
        assertEquals(
            setOf(fingerprint(first), fingerprint(second)),
            PassivePipelineCodec.decodeFingerprints(database.passive().dailyRevisionsNow().last().baselineSegment.let {
                database.passive().baselineSegmentsNow().single { segment -> segment.id == it }.fingerprintsJson
            }),
        )
    }

    @Test
    fun `daily provenance includes the routine anchor but not unrelated prior events`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val day = LocalDate.parse("2026-08-30")
        val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val unrelated = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_NON_INTERACTIVE,
            start - 12L * 3_600_000L,
            start - 12L * 3_600_000L,
            "unrelated",
            value = null,
        )
        val anchor = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_INTERACTIVE,
            start - 10L * 60_000L,
            start - 10L * 60_000L,
            "anchor",
            value = null,
        )
        val unlock = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_UNLOCKED,
            start + 7L * 3_600_000L,
            start + 7L * 3_600_000L,
            "unlock",
            value = null,
        )
        val off = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_NON_INTERACTIVE,
            start + 7L * 3_600_000L + 30L * 60_000L,
            start + 7L * 3_600_000L + 30L * 60_000L,
            "off",
            value = null,
        )
        val records = listOf(unrelated, anchor, unlock, off)
        val source = RangeSource { range ->
            listOf(successRead(PassiveSourceFamily.USAGE_STATS, range, now, records))
        }

        repository(FakeSource(reads = emptyList()), source).run(now, ZoneId.of("UTC"))

        val provenance = database.passive().dailyRevisionsNow().single { it.localDate == day.toString() }
            .provenanceJson
        assertTrue(provenance.contains(PassivePipelineCodec.rawIdentity(anchor)))
        assertTrue(provenance.contains(PassivePipelineCodec.rawIdentity(unlock)))
        assertTrue(provenance.contains(PassivePipelineCodec.rawIdentity(off)))
        assertFalse(provenance.contains(PassivePipelineCodec.rawIdentity(unrelated)))
    }

    @Test
    fun `source backfill appends revisions and an ordinary equal rescan is a no-op`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val dayStart = LocalDate.parse("2026-08-30").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val first = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayStart + 60_000L,
            dayStart + 120_000L,
            "steps-a",
            value = 10.0,
        )
        val backfill = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayStart + 180_000L,
            dayStart + 240_000L,
            "steps-b",
            value = 0.0,
        )
        val records = mutableListOf(first)
        val source = RangeSource { range ->
            listOf(successRead(PassiveSourceFamily.STEPS, range, now, records.toList()))
        }
        val repository = repository(source, FakeSource(reads = emptyList()))

        repository.run(now, ZoneId.of("UTC"))
        val initialWindows = database.passive().windowRevisionsNow().size
        val initialDays = database.passive().dailyRevisionsNow().size
        val initialDecisions = database.passive().observationDecisionsNow().size
        records += backfill
        repository.run(now + 1_000L, ZoneId.of("UTC"))

        assertTrue(database.passive().windowRevisionsNow().any { it.revisionReason == "BACKFILL" })
        assertEquals("BACKFILL", database.passive().dailyRevisionsNow().last().revisionReason)
        assertEquals("BACKFILL", database.passive().observationDecisionsNow().last().revisionReason)
        assertTrue(database.passive().windowRevisionsNow().size > initialWindows)
        assertEquals(initialDays + 1, database.passive().dailyRevisionsNow().size)
        assertEquals(initialDecisions + 1, database.passive().observationDecisionsNow().size)

        val counts = Triple(
            database.passive().windowRevisionsNow().size,
            database.passive().dailyRevisionsNow().size,
            database.passive().observationDecisionsNow().size,
        )
        repository.run(now + 2_000L, ZoneId.of("UTC"))
        assertEquals(counts.first, database.passive().windowRevisionsNow().size)
        assertEquals(counts.second, database.passive().dailyRevisionsNow().size)
        assertEquals(counts.third, database.passive().observationDecisionsNow().size)
    }

    @Test
    fun `all writes roll back on phase failure and refresh runs only after commit`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val value = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            now - 120_000L,
            now - 60_000L,
            "steps-a",
            value = 10.0,
        )
        var refreshes = 0
        val repository = PassivePipelineRepository(
            database,
            RangeSource { range -> listOf(successRead(PassiveSourceFamily.STEPS, range, now, listOf(value))) },
            FakeSource(reads = emptyList()),
            historyPermissionGranted = { false },
            ensureCurrentPhase = {
                assertEquals(1, database.passive().rawProvenanceNow().size)
                assertEquals(0, database.passive().windowRevisionsNow().size)
                error("phase failed")
            },
            refreshProvenanceAfterCommit = { refreshes++ },
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.run(now, ZoneId.of("UTC")) }
        }
        assertEquals(0, database.passive().rawProvenanceNow().size)
        assertEquals(0, database.passive().sourceReadsNow().size)
        assertEquals(0, database.passive().pipelineRunsNow().size)
        assertEquals(0, refreshes)
    }

    @Test
    fun `all denied or unavailable outcomes complete without permission`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val source = RangeSource { range ->
            listOf(
                PassiveSourceRead(
                    PassiveSourceFamily.SLEEP,
                    PassiveReadState.PERMISSION_DENIED,
                    range,
                    now,
                    errorCode = "DENIED",
                ),
                PassiveSourceRead(
                    PassiveSourceFamily.USAGE_STATS,
                    PassiveReadState.UNAVAILABLE,
                    range,
                    now,
                    errorCode = "UNAVAILABLE",
                ),
            )
        }

        val result = repository(source, FakeSource(reads = emptyList())).run(now, ZoneId.of("UTC"))

        assertTrue(result is PassivePipelineResult.Completed)
        assertEquals("SUCCESS_NO_PERMISSION", database.passive().pipelineRunsNow().single().result)
    }

    @Test
    fun `refresh observes the committed run after phase ordering`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val value = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            now - 120_000L,
            now - 60_000L,
            "steps-a",
            value = 10.0,
        )
        val order = mutableListOf<String>()
        val repository = PassivePipelineRepository(
            database,
            RangeSource { range -> listOf(successRead(PassiveSourceFamily.STEPS, range, now, listOf(value))) },
            FakeSource(reads = emptyList()),
            historyPermissionGranted = { false },
            ensureCurrentPhase = {
                assertEquals(1, database.passive().rawProvenanceNow().size)
                assertEquals(0, database.passive().windowRevisionsNow().size)
                order += "phase"
            },
            refreshProvenanceAfterCommit = {
                assertEquals(1, database.passive().pipelineRunsNow().size)
                order += "refresh"
            },
        )

        repository.run(now, ZoneId.of("UTC"))

        assertEquals(listOf("phase", "refresh"), order)
    }

    @Test
    fun `thirtieth lag observation finalizes by nearest rank without promoting insufficient data`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val day = LocalDate.parse("2026-08-29")
        val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val records = (0 until 29).map { index ->
            val eventStart = start + index * 60_000L
            record(
                PassiveSourceFamily.STEPS,
                PassiveRecordKind.STEPS_INTERVAL,
                eventStart,
                eventStart + 30_000L,
                "steps-$index",
                value = 1.0,
                sourceUpdatedTime = eventStart + 30_000L + 3_600_000L,
            )
        }.toMutableList()
        val source = RangeSource { range ->
            listOf(successRead(PassiveSourceFamily.STEPS, range, now, records.toList()))
        }
        val repository = repository(source, FakeSource(reads = emptyList()))

        repository.run(now, ZoneId.of("UTC"))
        val provisional = database.passive().dailyRevisionsNow().single { it.localDate == day.toString() }
        assertEquals("AVAILABLE_PROVISIONAL", provisional.dataStatus)
        assertEquals(
            day.plusDays(1L).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() +
                PassiveFinality.BOOTSTRAP_LAG_MILLIS,
            provisional.watermark,
        )

        val lastStart = start + 29L * 60_000L
        records += record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            lastStart,
            lastStart + 30_000L,
            "steps-29",
            value = 1.0,
            sourceUpdatedTime = lastStart + 30_000L + 3_600_000L,
        )
        repository.run(now + 1_000L, ZoneId.of("UTC"))

        val finalized = database.passive().dailyRevisionsNow().last { it.localDate == day.toString() }
        assertEquals("FINALITY", finalized.revisionReason)
        assertEquals("INSUFFICIENT_DATA", finalized.dataStatus)
        assertEquals(
            day.plusDays(1L).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() +
                PassiveFinality.MIN_LAG_MILLIS,
            finalized.watermark,
        )
        assertEquals(
            listOf("INITIAL", "FINALITY"),
            database.passive().observationDecisionsNow().filter { it.localDate == day.toString() }
                .map { it.revisionReason },
        )
    }

    @Test
    @Suppress("LongMethod")
    fun `persisted Program 2A history produces the exact deterministic calibration seed`() = runBlocking {
        val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
        val target = LocalDate.parse("2026-08-27")
        val stepsFingerprint = PassiveSourceFingerprint(
            PassiveSourceFamily.STEPS,
            "source.steps",
            "Maker",
            "Model",
            "WATCH",
        )
        val sleepFingerprint = PassiveSourceFingerprint(
            PassiveSourceFamily.SLEEP,
            "source.sleep",
            "Maker",
            "Model",
            "WATCH",
        )
        val fingerprints = setOf(stepsFingerprint, sleepFingerprint)
        val segmentId = PassiveBaselineSegment.id(
            fingerprints,
            PassiveWindowAggregator.TRANSFORMATION_VERSION,
            PassiveDailyAggregator.TRANSFORMATION_VERSION,
        )
        database.passive().insertBaselineSegment(
            PassiveBaselineSegmentEntity(
                segmentId,
                openedAt = target.minusDays(61L).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                fingerprintsJson = PassivePipelineCodec.sortedFingerprintJson(fingerprints),
                windowTransformationVersion = PassiveWindowAggregator.TRANSFORMATION_VERSION,
                dailyTransformationVersion = PassiveDailyAggregator.TRANSFORMATION_VERSION,
            ),
        )
        val history = (60 downTo 1).mapIndexed { index, daysBefore ->
            val date = target.minusDays(daysBefore.toLong())
            val ingestedAt = date.plusDays(1L).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000L
            PassivePipelineCodec.dailyEntity(
                aggregate(
                    date,
                    segmentId,
                    ingestedAt,
                    steps = 5_000.0 + index * 25.0,
                    sleepMinutes = 420.0 + (index % 7) * 5.0,
                ),
                emptySet(),
                RevisionReason.INITIAL,
                ingestedAt,
            )
        }
        database.passive().insertDailyRevisions(history)
        val dayStart = target.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayStart + 8L * 3_600_000L,
            dayStart + 9L * 3_600_000L,
            "target-steps",
            value = 8_000.0,
        )
        val sleep = record(
            PassiveSourceFamily.SLEEP,
            PassiveRecordKind.SLEEP_SESSION,
            dayStart - 8L * 3_600_000L,
            dayStart + 7L * 3_600_000L,
            "target-sleep",
            value = null,
        )
        val source = RangeSource { range ->
            listOf(
                successRead(PassiveSourceFamily.STEPS, range, now, listOf(steps)),
                successRead(PassiveSourceFamily.SLEEP, range, now, listOf(sleep)),
            )
        }

        repository(source, FakeSource(reads = emptyList())).run(now, ZoneId.of("UTC"))

        val decision = database.passive().observationDecisionsNow().single { it.localDate == target.toString() }
        val frozenAsOf = history.maxOf { it.ingestedAt }
        val expectedSeed = PassivePipelineCodec.calibrationSeed(
            segmentId,
            frozenAsOf,
            requireNotNull(TransformationRegistry.versionOf("passive-block-calibration")),
        )
        assertNotNull(decision.calibrationSeed)
        assertEquals(expectedSeed, decision.calibrationSeed)
        assertEquals(expectedSeed, PassivePipelineCodec.decisionToDomain(decision).calibration?.seed)
        assertEquals(60, PassivePipelineCodec.decisionToDomain(decision).baselineDays)
    }

    private fun repository(
        health: PassiveRecordSource,
        usage: PassiveRecordSource,
        historyGranted: suspend () -> Boolean = { false },
    ) = PassivePipelineRepository(
        database = database,
        healthSource = health,
        usageSource = usage,
        historyPermissionGranted = historyGranted,
        ensureCurrentPhase = {},
        refreshProvenanceAfterCommit = {},
    )

    private class FakeSource(
        private val successFamily: PassiveSourceFamily? = null,
        private val reads: List<PassiveSourceRead>? = null,
    ) : PassiveRecordSource {
        val ranges = mutableListOf<PassiveReadRange>()

        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
            ranges += range
            return reads ?: listOfNotNull(successFamily?.let { family ->
                PassiveSourceRead(family, PassiveReadState.SUCCESS, range, range.endExclusive)
            })
        }
    }

    private class RangeSource(
        private val block: (PassiveReadRange) -> List<PassiveSourceRead>,
    ) : PassiveRecordSource {
        val ranges = mutableListOf<PassiveReadRange>()

        override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
            ranges += range
            return block(range)
        }
    }

    private fun successRead(
        family: PassiveSourceFamily,
        range: PassiveReadRange,
        attemptedAt: Long,
        records: List<PassiveSourceRecord>,
    ) = PassiveSourceRead(family, PassiveReadState.SUCCESS, range, attemptedAt, records)

    @Suppress("LongParameterList")
    private fun record(
        family: PassiveSourceFamily,
        kind: PassiveRecordKind,
        start: Long,
        end: Long,
        id: String,
        value: Double?,
        sourceUpdatedTime: Long? = end + 60_000L,
        ingestedAt: Long = end + 120_000L,
    ) = PassiveSourceRecord(
        sourceFamily = family,
        kind = kind,
        eventStart = start,
        eventEnd = end,
        value = value,
        unit = when (kind) {
            PassiveRecordKind.STEPS_INTERVAL -> "count"
            PassiveRecordKind.HRV_RMSSD -> "ms"
            PassiveRecordKind.HEART_RATE_SAMPLE -> "bpm"
            PassiveRecordKind.SPO2 -> "percent"
            else -> "milliseconds"
        },
        dataOriginPackage = "source.${family.name.lowercase()}",
        deviceManufacturer = "Maker",
        deviceModel = "Model",
        deviceType = "WATCH",
        sourceUpdatedTime = sourceUpdatedTime,
        ingestedAt = ingestedAt,
        zoneId = "UTC",
        zoneOffsetSeconds = 0,
        recordId = id,
        recordVersion = 1L,
    )

    private fun fingerprint(record: PassiveSourceRecord) = PassiveSourceFingerprint(
        record.sourceFamily,
        record.dataOriginPackage,
        record.deviceManufacturer,
        record.deviceModel,
        record.deviceType,
    )

    private fun aggregate(
        date: LocalDate,
        segment: String,
        ingestedAt: Long,
        steps: Double,
        sleepMinutes: Double,
    ) = PassiveDailyAggregate(
        passiveDay = PassiveDay(
            date,
            PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(
                PassiveFeature.STEPS to steps,
                PassiveFeature.SLEEP_MINUTES to sleepMinutes,
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
        coverageByFeature = mapOf(
            PassiveFeature.STEPS to 1.0,
            PassiveFeature.SLEEP_MINUTES to 1.0,
        ),
        missingFeatures = PassiveFeature.entries.filter {
            it != PassiveFeature.STEPS && it != PassiveFeature.SLEEP_MINUTES
        }.toSet(),
        exclusions = emptyMap(),
        finality = PassiveFinalityDecision(ingestedAt, true, emptyMap()),
        sourceLags = emptyList(),
    )
}
