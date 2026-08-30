package org.mindanchor.intelligence

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass")
class PassiveAggregationTest {
    @Test fun `windows are UTC quarter hours and exercise removes only physiology`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val range = PassiveReadRange(
            Instant.parse("2026-08-30T00:07:00Z").toEpochMilli(),
            Instant.parse("2026-08-30T00:31:00Z").toEpochMilli(),
            zone.id,
        )
        val records = (0 until 8).map { minute ->
            record(
                family = PassiveSourceFamily.HEART_RATE,
                kind = PassiveRecordKind.HEART_RATE_SAMPLE,
                start = Instant.parse("2026-08-30T00:0${minute}:10Z").toEpochMilli(),
                value = 70.0 + minute,
                id = "hr-$minute",
            )
        } + listOf(
            record(
                PassiveSourceFamily.RESTING_HEART_RATE,
                PassiveRecordKind.RESTING_HEART_RATE,
                Instant.parse("2026-08-30T00:08:00Z").toEpochMilli(),
                61.0,
                "rhr",
            ),
            record(
                PassiveSourceFamily.HRV_RMSSD,
                PassiveRecordKind.HRV_RMSSD,
                Instant.parse("2026-08-30T00:09:00Z").toEpochMilli(),
                42.0,
                "hrv",
            ),
            record(
                PassiveSourceFamily.EXERCISE,
                PassiveRecordKind.EXERCISE_SESSION,
                Instant.parse("2026-08-30T00:10:00Z").toEpochMilli(),
                null,
                "exercise",
                Instant.parse("2026-08-30T00:12:00Z").toEpochMilli(),
            ),
            record(
                PassiveSourceFamily.STEPS,
                PassiveRecordKind.STEPS_INTERVAL,
                Instant.parse("2026-08-30T00:00:00Z").toEpochMilli(),
                150.0,
                "steps",
                Instant.parse("2026-08-30T00:15:00Z").toEpochMilli(),
            ),
        )

        val windows = PassiveWindowAggregator.aggregate(records, range, zone, wakeTimeMillis = null)

        assertEquals(Instant.parse("2026-08-30T00:00:00Z").toEpochMilli(), windows.first().startInclusive)
        assertEquals(Instant.parse("2026-08-30T00:15:00Z").toEpochMilli(), windows.first().endExclusive)
        assertEquals(8.0 / 15.0, windows.first().quality.heartRateCoverage, 0.000_001)
        assertFalse(windows.first().quality.physiologyEligible)
        assertFalse(
            windows.first().features.single { it.feature == PassiveFeature.RESTING_HEART_RATE }.eligible,
        )
        assertTrue(windows.first().features.single { it.feature == PassiveFeature.STEPS }.eligible)
        assertEquals(firstWindowProvenance(), windows.first().provenanceRecordIds)
    }

    @Test fun `half open UTC windows keep boundary records in exactly one window`() {
        val zone = ZoneId.of("UTC")
        val midnight = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()
        val quarter = midnight + PassiveWindowAggregator.WINDOW_MILLIS
        val records = listOf(
            record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL, midnight, 15.0, "first", quarter),
            record(PassiveSourceFamily.OXYGEN_SATURATION, PassiveRecordKind.SPO2, quarter, 98.0, "boundary"),
        )

        val windows = PassiveWindowAggregator.aggregate(
            records,
            PassiveReadRange(midnight, quarter + 1L, zone.id),
            zone,
            wakeTimeMillis = midnight - 60_000L,
        )

        assertEquals(2, windows.size)
        assertEquals(listOf("first"), windows[0].provenanceRecordIds)
        assertEquals(listOf("boundary"), windows[1].provenanceRecordIds)
        assertEquals(1, windows[0].quality.wakeRelativeMinute)
        assertEquals(16, windows[1].quality.wakeRelativeMinute)
    }

    @Test fun `absolute windows retain changing DST presentation offsets`() {
        val zone = ZoneId.of("America/New_York")
        val range = PassiveReadRange(
            Instant.parse("2026-03-08T06:55:00Z").toEpochMilli(),
            Instant.parse("2026-03-08T07:05:00Z").toEpochMilli(),
            zone.id,
        )

        val windows = PassiveWindowAggregator.aggregate(emptyList(), range, zone, null)

        assertEquals(2, windows.size)
        assertEquals(-18_000, windows[0].zoneOffsetSeconds)
        assertEquals(-14_400, windows[1].zoneOffsetSeconds)
        assertEquals(PassiveWindowAggregator.WINDOW_MILLIS, windows[1].startInclusive - windows[0].startInclusive)
    }

    @Test fun `eight distinct heart rate bins are eligible while seven are not`() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()
        fun aggregate(count: Int) = PassiveWindowAggregator.aggregate(
            (0 until count).map { minute ->
                record(
                    PassiveSourceFamily.HEART_RATE,
                    PassiveRecordKind.HEART_RATE_SAMPLE,
                    start + minute * 60_000L,
                    70.0,
                    "hr-$minute",
                )
            } + record(
                PassiveSourceFamily.RESTING_HEART_RATE,
                PassiveRecordKind.RESTING_HEART_RATE,
                start + 30_000L,
                60.0,
                "rhr",
            ),
            PassiveReadRange(start, start + PassiveWindowAggregator.WINDOW_MILLIS, zone.id),
            zone,
            null,
        ).single()

        val seven = aggregate(7)
        val eight = aggregate(8)

        assertFalse(seven.quality.physiologyEligible)
        assertFalse(seven.features.single { it.feature == PassiveFeature.RESTING_HEART_RATE }.eligible)
        assertTrue(eight.quality.physiologyEligible)
        assertTrue(eight.features.single { it.feature == PassiveFeature.RESTING_HEART_RATE }.eligible)
    }

    @Test fun `instant physiology rows require readings and use arithmetic means`() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()
        val heartRate = (0 until 8).map { minute ->
            record(
                PassiveSourceFamily.HEART_RATE,
                PassiveRecordKind.HEART_RATE_SAMPLE,
                start + minute * 60_000L,
                70.0,
                "hr-$minute",
            )
        }
        val rows = PassiveWindowAggregator.aggregate(
            heartRate + listOf(
                record(PassiveSourceFamily.HRV_RMSSD, PassiveRecordKind.HRV_RMSSD, start, 40.0, "hrv-1"),
                record(PassiveSourceFamily.HRV_RMSSD, PassiveRecordKind.HRV_RMSSD, start + 1L, 44.0, "hrv-2"),
                record(PassiveSourceFamily.OXYGEN_SATURATION, PassiveRecordKind.SPO2, start, 97.0, "spo2-1"),
                record(PassiveSourceFamily.OXYGEN_SATURATION, PassiveRecordKind.SPO2, start + 1L, 99.0, "spo2-2"),
            ),
            PassiveReadRange(start, start + PassiveWindowAggregator.WINDOW_MILLIS, zone.id),
            zone,
            null,
        ).single().features

        assertFalse(rows.any { it.feature == PassiveFeature.RESTING_HEART_RATE })
        assertEquals(42.0, rows.single { it.feature == PassiveFeature.HRV_RMSSD }.value!!, 0.0)
        assertEquals(98.0, rows.single { it.feature == PassiveFeature.SPO2_PERCENT }.value!!, 0.0)
        assertFalse(PassiveFeature.SPO2_PERCENT.scored)
    }

    @Test fun `interval values are proportionally clipped to each window`() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()
        val records = listOf(
            record(
                PassiveSourceFamily.STEPS,
                PassiveRecordKind.STEPS_INTERVAL,
                start + 10L * 60_000L,
                100.0,
                "steps",
                start + 20L * 60_000L,
            ),
            record(
                PassiveSourceFamily.EXERCISE,
                PassiveRecordKind.EXERCISE_SESSION,
                start + 10L * 60_000L,
                null,
                "exercise",
                start + 20L * 60_000L,
            ),
        )

        val windows = PassiveWindowAggregator.aggregate(
            records,
            PassiveReadRange(start, start + 30L * 60_000L, zone.id),
            zone,
            null,
        )

        windows.forEach { window ->
            assertEquals(50.0, window.features.single { it.feature == PassiveFeature.STEPS }.value!!, 0.0)
            assertEquals(5.0, window.features.single { it.feature == PassiveFeature.ACTIVE_MINUTES }.value!!, 0.0)
        }
    }

    @Test fun `sleep belongs to wake date and clipped activity stays on the local day`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val day = LocalDate.parse("2026-08-30")
        val start = day.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val end = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val sleep = record(PassiveSourceFamily.SLEEP, PassiveRecordKind.SLEEP_SESSION, start, null, "sleep", end)
        val exercise = record(
            PassiveSourceFamily.EXERCISE,
            PassiveRecordKind.EXERCISE_SESSION,
            day.minusDays(1).atTime(23, 50).atZone(zone).toInstant().toEpochMilli(),
            null,
            "exercise",
            day.atTime(0, 20).atZone(zone).toInstant().toEpochMilli(),
        )
        val reads = listOf(
            successRead(PassiveSourceFamily.SLEEP, start, end + 1L, zone, end, listOf(sleep)),
            successRead(PassiveSourceFamily.EXERCISE, start, end + 1L, zone, end, listOf(exercise)),
        )
        val finality = PassiveFinalityDecision(end, true, emptyMap())

        val aggregate = PassiveDailyAggregator.aggregate(
            day,
            zone,
            emptyList(),
            listOf(sleep, exercise),
            reads,
            "segment-a",
            end,
            finality,
        )

        assertEquals(480.0, aggregate.passiveDay.features[PassiveFeature.SLEEP_MINUTES]!!, 0.0)
        assertEquals(300.0, aggregate.passiveDay.features[PassiveFeature.SLEEP_ONSET_AFTER_SIX_PM]!!, 0.0)
        assertEquals(20.0, aggregate.passiveDay.features[PassiveFeature.ACTIVE_MINUTES]!!, 0.0)
    }

    @Test fun `daily step clipping respects a DST local day`() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.parse("2026-03-08")
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayStart - 60L * 60_000L,
            240.0,
            "steps",
            dayStart + 60L * 60_000L,
        )

        val aggregate = PassiveDailyAggregator.aggregate(
            day,
            zone,
            emptyList(),
            listOf(steps),
            listOf(successRead(PassiveSourceFamily.STEPS, dayStart - 1L, dayEnd, zone, dayEnd, listOf(steps))),
            "segment",
            dayEnd,
            PassiveFinalityDecision(dayEnd, true, emptyMap()),
        )

        assertEquals(120.0, aggregate.passiveDay.features[PassiveFeature.STEPS]!!, 0.0)
        assertEquals(23L * 60L * 60_000L, dayEnd - dayStart)
    }

    @Test fun `intervals outside the local day stay absent instead of becoming zero`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val priorStart = dayStart - 2L * 60L * 60_000L
        val priorEnd = dayStart - 60L * 60_000L
        val records = listOf(
            record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL,
                priorStart, 100.0, "steps", priorEnd),
            record(PassiveSourceFamily.EXERCISE, PassiveRecordKind.EXERCISE_SESSION,
                priorStart, null, "exercise", priorEnd),
        )

        val aggregate = daily(day, zone, records, emptyList(), dayStart)

        assertFalse(aggregate.passiveDay.features.containsKey(PassiveFeature.STEPS))
        assertFalse(aggregate.passiveDay.features.containsKey(PassiveFeature.ACTIVE_MINUTES))
    }

    @Test fun `routine is derived only from successful raw event reads`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val prior = day.minusDays(1).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val unlock = day.atTime(7, 15).atZone(zone).toInstant().toEpochMilli()
        val off = day.atTime(7, 45).atZone(zone).toInstant().toEpochMilli()
        val records = listOf(
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_NON_INTERACTIVE, prior, null, "anchor"),
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_UNLOCKED, unlock, null, "unlock"),
            record(PassiveSourceFamily.USAGE_STATS, PassiveRecordKind.SCREEN_NON_INTERACTIVE, off, null, "off"),
        )
        val range = PassiveReadRange(prior, off + 1L, zone.id)

        val success = daily(day, zone, records, listOf(
            PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.SUCCESS, range, off, records),
        ), off)
        assertEquals(435.0, success.passiveDay.features[PassiveFeature.FIRST_UNLOCK_MINUTE]!!, 0.0)
        assertEquals(30.0, success.passiveDay.features[PassiveFeature.SCREEN_MINUTES]!!, 0.0)

        val openRecords = records.dropLast(1)
        val tenMinutesLater = unlock + 10L * 60_000L
        val open = daily(day, zone, openRecords, listOf(
            PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.SUCCESS,
                range, tenMinutesLater, openRecords),
        ), tenMinutesLater)
        assertEquals(10.0, open.passiveDay.features[PassiveFeature.SCREEN_MINUTES]!!, 0.0)

        val successfulEmpty = daily(day, zone, emptyList(), listOf(
            PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.SUCCESS, range, off),
        ), off)
        assertFalse(successfulEmpty.passiveDay.features.containsKey(PassiveFeature.FIRST_UNLOCK_MINUTE))
        assertFalse(successfulEmpty.passiveDay.features.containsKey(PassiveFeature.SCREEN_MINUTES))

        PassiveReadState.entries.filter { it != PassiveReadState.SUCCESS }.forEach { state ->
            val failed = daily(day, zone, records, listOf(
                PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, state, range, off, errorCode = state.name),
            ), off)
            assertFalse("$state must not derive routine", failed.passiveDay.features.keys.any {
                it.domain == PassiveDomain.ROUTINE
            })
        }
    }

    @Suppress("LongMethod")
    @Test fun `routine provenance includes only the prior anchor and target day events`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val unrelatedPrior = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_NON_INTERACTIVE,
            dayStart - 36L * 3_600_000L,
            null,
            "unrelated-prior",
        ).copy(sourceUpdatedTime = dayEnd + 3L * 24L * 3_600_000L)
        val anchor = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_INTERACTIVE,
            dayStart - 10L * 60_000L,
            null,
            "anchor",
        ).copy(sourceUpdatedTime = null, ingestedAt = dayStart + 23L * 3_600_000L)
        val midnightOff = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_NON_INTERACTIVE,
            dayStart + 10L * 60_000L,
            null,
            "midnight-off",
        )
        val unlock = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_UNLOCKED,
            dayStart + 7L * 3_600_000L,
            null,
            "unlock",
        )
        val morningOff = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_NON_INTERACTIVE,
            dayStart + 7L * 3_600_000L + 20L * 60_000L,
            null,
            "morning-off",
        )
        val unrelatedFuture = record(
            PassiveSourceFamily.USAGE_STATS,
            PassiveRecordKind.SCREEN_INTERACTIVE,
            dayEnd,
            null,
            "unrelated-future",
        ).copy(sourceUpdatedTime = dayEnd + 4L * 24L * 3_600_000L)
        val records = listOf(
            unrelatedPrior,
            anchor,
            midnightOff,
            unlock,
            morningOff,
            unrelatedFuture,
        )
        val read = successRead(
            PassiveSourceFamily.USAGE_STATS,
            unrelatedPrior.eventStart,
            unrelatedFuture.eventStart + 1L,
            zone,
            dayEnd,
            records,
        )

        val aggregate = daily(day, zone, records, listOf(read), dayEnd)

        assertEquals(420.0, aggregate.passiveDay.features[PassiveFeature.FIRST_UNLOCK_MINUTE]!!, 0.0)
        assertEquals(30.0, aggregate.passiveDay.features[PassiveFeature.SCREEN_MINUTES]!!, 0.0)
        assertEquals(anchor.ingestedAt, aggregate.passiveDay.sourceUpdatedTime)
        assertEquals(anchor.ingestedAt, aggregate.passiveDay.ingestedAt)
        val contributing = listOf(anchor, midnightOff, unlock, morningOff)
        assertEquals(
            contributing.map { record ->
                SourceLag(
                    PassiveSourceFamily.USAGE_STATS,
                    ((record.sourceUpdatedTime ?: record.ingestedAt) - record.eventEnd).coerceAtLeast(0L),
                    usedIngestedAtFallback = record.sourceUpdatedTime == null,
                )
            },
            aggregate.sourceLags,
        )
    }

    @Test fun `daily status honors provisional final exercise and insufficient ordering`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val asOf = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            asOf - PassiveWindowAggregator.WINDOW_MILLIS,
            10.0,
            "steps",
            asOf,
        )
        val physiology = featureWindow(
            start = asOf - PassiveWindowAggregator.WINDOW_MILLIS,
            features = listOf(PassiveWindowFeature(PassiveFeature.RESTING_HEART_RATE, 60.0, "bpm", 1.0, true, null)),
        )
        val suppressed = featureWindow(
            start = asOf - PassiveWindowAggregator.WINDOW_MILLIS,
            exerciseMillis = 60_000L,
            features = listOf(
                PassiveWindowFeature(
                    PassiveFeature.RESTING_HEART_RATE,
                    60.0,
                    "bpm",
                    1.0,
                    false,
                    "EXERCISE_OVERLAP",
                ),
            ),
        )

        assertEquals(PassiveDataStatus.AVAILABLE_PROVISIONAL,
            daily(day, zone, listOf(steps), emptyList(), asOf, final = false).passiveDay.dataStatus)
        assertEquals(PassiveDataStatus.AVAILABLE_FINAL,
            daily(day, zone, listOf(steps), emptyList(), asOf, listOf(physiology)).passiveDay.dataStatus)
        assertEquals(PassiveDataStatus.SUPPRESSED_EXERCISE,
            daily(day, zone, listOf(steps), emptyList(), asOf, listOf(suppressed)).passiveDay.dataStatus)
        assertEquals(PassiveDataStatus.INSUFFICIENT_DATA,
            daily(day, zone, listOf(steps), emptyList(), asOf).passiveDay.dataStatus)
    }

    @Test fun `adjacent day windows cannot contaminate a target local day`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val day = LocalDate.parse("2026-08-30")
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayEnd - PassiveWindowAggregator.WINDOW_MILLIS,
            10.0,
            "steps",
            dayEnd,
        )
        val adjacentWindows = listOf(
            featureWindow(
                start = dayEnd,
                features = listOf(
                    PassiveWindowFeature(
                        PassiveFeature.RESTING_HEART_RATE, 60.0, "bpm", 1.0, true, null,
                    ),
                ),
            ),
            featureWindow(
                start = dayEnd + PassiveWindowAggregator.WINDOW_MILLIS,
                exerciseMillis = 60_000L,
                features = listOf(
                    PassiveWindowFeature(
                        PassiveFeature.HRV_RMSSD,
                        40.0,
                        "ms",
                        1.0,
                        false,
                        "EXERCISE_OVERLAP",
                    ),
                ),
            ),
        )

        val aggregate = daily(day, zone, listOf(steps), emptyList(), dayEnd, adjacentWindows)

        assertTrue(aggregate.windows.isEmpty())
        assertFalse(aggregate.passiveDay.features.containsKey(PassiveFeature.RESTING_HEART_RATE))
        assertFalse(aggregate.coverageByFeature.containsKey(PassiveFeature.RESTING_HEART_RATE))
        assertFalse(aggregate.coverageByFeature.containsKey(PassiveFeature.HRV_RMSSD))
        assertFalse(aggregate.exclusions.containsKey(PassiveFeature.HRV_RMSSD))
        assertTrue(PassiveFeature.RESTING_HEART_RATE in aggregate.missingFeatures)
        assertTrue(PassiveFeature.HRV_RMSSD in aggregate.missingFeatures)
        assertEquals(PassiveDataStatus.INSUFFICIENT_DATA, aggregate.passiveDay.dataStatus)
    }

    @Suppress("LongMethod")
    @Test fun `exercise counterfactual restores only physiology that passes heart rate coverage`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + PassiveWindowAggregator.WINDOW_MILLIS

        mapOf(
            0 to PassiveDataStatus.INSUFFICIENT_DATA,
            7 to PassiveDataStatus.INSUFFICIENT_DATA,
            8 to PassiveDataStatus.SUPPRESSED_EXERCISE,
        ).forEach { (heartRateBins, expectedStatus) ->
            val records = (0 until heartRateBins).map { minute ->
                record(
                    PassiveSourceFamily.HEART_RATE,
                    PassiveRecordKind.HEART_RATE_SAMPLE,
                    start + minute * 60_000L,
                    70.0,
                    "hr-$heartRateBins-$minute",
                )
            } + listOf(
                record(
                    PassiveSourceFamily.RESTING_HEART_RATE,
                    PassiveRecordKind.RESTING_HEART_RATE,
                    start + 30_000L,
                    60.0,
                    "rhr-$heartRateBins",
                ),
                record(
                    PassiveSourceFamily.HRV_RMSSD,
                    PassiveRecordKind.HRV_RMSSD,
                    start + 45_000L,
                    40.0,
                    "hrv-$heartRateBins",
                ),
                record(
                    PassiveSourceFamily.EXERCISE,
                    PassiveRecordKind.EXERCISE_SESSION,
                    start + 60_000L,
                    null,
                    "exercise-$heartRateBins",
                    start + 120_000L,
                ),
                record(
                    PassiveSourceFamily.STEPS,
                    PassiveRecordKind.STEPS_INTERVAL,
                    start,
                    100.0,
                    "steps-$heartRateBins",
                    end,
                ),
            )
            val windows = PassiveWindowAggregator.aggregate(
                records,
                PassiveReadRange(start, end, zone.id),
                zone,
                wakeTimeMillis = null,
            )

            val aggregate = daily(day, zone, records, emptyList(), end, windows)

            assertEquals("$heartRateBins HR bins", expectedStatus, aggregate.passiveDay.dataStatus)
        }
    }

    @Test fun `daily aggregate records coverage missingness and exercise exclusions`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val asOf = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val suppressed = featureWindow(
            start = asOf - PassiveWindowAggregator.WINDOW_MILLIS,
            exerciseMillis = 60_000L,
            features = listOf(
                PassiveWindowFeature(
                    PassiveFeature.HRV_RMSSD,
                    42.0,
                    "ms",
                    8.0 / 15.0,
                    false,
                    "EXERCISE_OVERLAP",
                ),
            ),
        )

        val aggregate = daily(day, zone, emptyList(), emptyList(), asOf, listOf(suppressed))

        assertEquals(8.0 / 15.0, aggregate.coverageByFeature[PassiveFeature.HRV_RMSSD]!!, 0.0)
        assertTrue(PassiveFeature.HRV_RMSSD in aggregate.missingFeatures)
        assertEquals("EXERCISE_OVERLAP", aggregate.exclusions[PassiveFeature.HRV_RMSSD])
        assertTrue(PassiveFeature.HRV_RMSSD in aggregate.passiveDay.excludedFeatures)
    }

    @Test fun `daily timestamps use per-record fallback and expose lag evidence`() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.parse("2026-08-30")
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val steps = record(
            PassiveSourceFamily.STEPS,
            PassiveRecordKind.STEPS_INTERVAL,
            dayStart + 1L * 3_600_000L,
            100.0,
            "steps",
            dayStart + 2L * 3_600_000L,
        ).copy(
            sourceUpdatedTime = dayStart + 3L * 3_600_000L,
            ingestedAt = dayStart + 4L * 3_600_000L,
        )
        val exercise = record(
            PassiveSourceFamily.EXERCISE,
            PassiveRecordKind.EXERCISE_SESSION,
            dayStart + 5L * 3_600_000L,
            null,
            "exercise",
            dayStart + 6L * 3_600_000L,
        ).copy(
            sourceUpdatedTime = null,
            ingestedAt = dayStart + 10L * 3_600_000L,
        )

        val aggregate = daily(day, zone, listOf(steps, exercise), emptyList(), exercise.ingestedAt)

        assertEquals(dayStart + 10L * 3_600_000L, aggregate.passiveDay.sourceUpdatedTime)
        assertEquals(
            SourceLag(PassiveSourceFamily.STEPS, 3_600_000L, usedIngestedAtFallback = false),
            aggregate.sourceLags.single { it.sourceFamily == PassiveSourceFamily.STEPS },
        )
        assertEquals(
            SourceLag(PassiveSourceFamily.EXERCISE, 4L * 3_600_000L, usedIngestedAtFallback = true),
            aggregate.sourceLags.single { it.sourceFamily == PassiveSourceFamily.EXERCISE },
        )
    }

    @Test fun `nearest rank lag uses bootstrap at 29 and clamps p99 at 30`() {
        val dayEnd = Instant.parse("2026-08-31T00:00:00Z").toEpochMilli()
        val first29 = (1L..29L).map { hours ->
            SourceLag(PassiveSourceFamily.SLEEP, hours * 3_600_000L, usedIngestedAtFallback = false)
        }
        val bootstrap = PassiveFinality.watermark(
            dayEnd,
            setOf(PassiveSourceFamily.SLEEP),
            first29,
            dayEnd + PassiveFinality.BOOTSTRAP_LAG_MILLIS,
        )
        assertEquals(PassiveFinality.BOOTSTRAP_LAG_MILLIS,
            bootstrap.perSourceLagMillis[PassiveSourceFamily.SLEEP])
        assertTrue(bootstrap.final)

        val thirty = first29 + SourceLag(
            PassiveSourceFamily.SLEEP,
            30L * 3_600_000L,
            usedIngestedAtFallback = true,
        )
        val finality = PassiveFinality.watermark(
            dayEnd,
            setOf(PassiveSourceFamily.SLEEP),
            thirty,
            dayEnd + 1L,
        )
        assertEquals(dayEnd + 30L * 3_600_000L, finality.watermark)
        assertFalse(finality.final)

        val low = List(30) { SourceLag(PassiveSourceFamily.STEPS, 1L, false) }
        val high = List(30) { SourceLag(PassiveSourceFamily.EXERCISE, Long.MAX_VALUE, false) }
        val clamped = PassiveFinality.watermark(
            dayEnd,
            setOf(PassiveSourceFamily.STEPS, PassiveSourceFamily.EXERCISE),
            low + high,
            Long.MAX_VALUE,
        )
        assertEquals(PassiveFinality.MIN_LAG_MILLIS, clamped.perSourceLagMillis[PassiveSourceFamily.STEPS])
        assertEquals(PassiveFinality.MAX_LAG_MILLIS, clamped.perSourceLagMillis[PassiveSourceFamily.EXERCISE])
    }

    @Test fun `canonical segment and signed seed hashes are deterministic`() {
        val sleep = PassiveSourceFingerprint(
            PassiveSourceFamily.SLEEP,
            "source.app",
            "Maker",
            "Model",
            "WATCH",
        )
        val steps = PassiveSourceFingerprint(PassiveSourceFamily.STEPS, "other.app", null, null, "PHONE")
        assertEquals("5:SLEEP10:source.app5:Maker5:Model5:WATCH", sleep.canonical())
        assertEquals(
            PassiveBaselineSegment.id(setOf(sleep, steps), "passive-window-v1", "passive-daily-v1"),
            PassiveBaselineSegment.id(setOf(steps, sleep), "passive-window-v1", "passive-daily-v1"),
        )
        assertNotEquals(
            PassiveBaselineSegment.id(setOf(sleep), "passive-window-v1", "passive-daily-v1"),
            PassiveBaselineSegment.id(setOf(sleep.copy(deviceModel = "New")), "passive-window-v1", "passive-daily-v1"),
        )
        val delimiterCollisionA = sleep.copy(
            dataOriginPackage = "source|app\nwear",
            deviceManufacturer = "Maker",
        )
        val delimiterCollisionB = sleep.copy(
            dataOriginPackage = "source",
            deviceManufacturer = "app\nwear|Maker",
        )
        assertNotEquals(delimiterCollisionA.canonical(), delimiterCollisionB.canonical())
        assertNotEquals(
            PassiveBaselineSegment.id(
                setOf(delimiterCollisionA), "passive-window-v1", "passive-daily-v1",
            ),
            PassiveBaselineSegment.id(
                setOf(delimiterCollisionB), "passive-window-v1", "passive-daily-v1",
            ),
        )
        assertEquals(
            -3_426_751_757_403_841_055L,
            PassiveSeed.firstSigned64Bits("segment|1234|block-calibration-v3"),
        )
    }

    @Test fun `source contracts distinguish successful empty reads from failures`() {
        val range = PassiveReadRange(1L, 2L, "UTC")
        assertTrue(
            PassiveSourceRead(
                PassiveSourceFamily.SLEEP,
                PassiveReadState.SUCCESS,
                range,
                attemptedAt = 2L,
            ).records.isEmpty(),
        )
        PassiveReadState.entries.filter { it != PassiveReadState.SUCCESS }.forEach { state ->
            assertEquals(
                state,
                PassiveSourceRead(
                    PassiveSourceFamily.SLEEP,
                    state,
                    range,
                    attemptedAt = 2L,
                    errorCode = state.name,
                ).state,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PassiveReadRange(2L, 2L, "UTC")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PassiveSourceRead(
                PassiveSourceFamily.SLEEP,
                PassiveReadState.PERMISSION_DENIED,
                range,
                attemptedAt = 2L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PassiveSourceRead(
                PassiveSourceFamily.SLEEP,
                PassiveReadState.UNAVAILABLE,
                range,
                attemptedAt = 2L,
                records = listOf(record(PassiveSourceFamily.SLEEP, PassiveRecordKind.SLEEP_SESSION, 1L, null, "x")),
                errorCode = "unavailable",
            )
        }
    }

    @Test fun `every source family accepts exactly its legal record kinds`() {
        val allKinds = PassiveRecordKind.entries.toSet()
        assertEquals(allKinds, PassiveSourceFamily.entries.flatMap { it.legalRecordKinds }.toSet())
        allKinds.forEach { kind ->
            assertEquals(1, PassiveSourceFamily.entries.count { kind in it.legalRecordKinds })
        }

        PassiveSourceFamily.entries.forEach { family ->
            PassiveRecordKind.entries.forEach { kind ->
                val accepted = runCatching {
                    record(
                        family,
                        kind,
                        1L,
                        if (kind == PassiveRecordKind.STEPS_INTERVAL) 1.0 else null,
                        "$family-$kind",
                        if (kind in setOf(
                                PassiveRecordKind.SLEEP_SESSION,
                                PassiveRecordKind.STEPS_INTERVAL,
                                PassiveRecordKind.EXERCISE_SESSION,
                            )
                        ) {
                            2L
                        } else {
                            1L
                        },
                    )
                }.isSuccess
                assertEquals("$family + $kind", kind in family.legalRecordKinds, accepted)
            }
        }
    }

    @Test fun `source records reject a kind owned by another family`() {
        assertThrows(IllegalArgumentException::class.java) {
            val mismatched = record(
                PassiveSourceFamily.SLEEP,
                PassiveRecordKind.STEPS_INTERVAL,
                1L,
                10.0,
                "mismatch",
                2L,
            )
            PassiveSourceRead(
                PassiveSourceFamily.SLEEP,
                PassiveReadState.SUCCESS,
                PassiveReadRange(0L, 3L, "UTC"),
                attemptedAt = 3L,
                records = listOf(mismatched),
            )
        }
    }

    @Test fun `source records reject invalid identity interval and values`() {
        assertThrows(IllegalArgumentException::class.java) {
            record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL, 2L, 1.0, "x", 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL, 1L, Double.NaN, "x", 2L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL, 1L, 1.0, "", 2L)
        }
    }

    private fun daily(
        day: LocalDate,
        zone: ZoneId,
        records: List<PassiveSourceRecord>,
        reads: List<PassiveSourceRead>,
        asOf: Long,
        windows: List<PassiveFeatureWindow> = emptyList(),
        final: Boolean = true,
    ): PassiveDailyAggregate = PassiveDailyAggregator.aggregate(
        day,
        zone,
        windows,
        records,
        reads,
        "segment",
        asOf,
        PassiveFinalityDecision(asOf, final, emptyMap()),
    )

    private fun firstWindowProvenance() = listOf(
        "exercise", "hr-0", "hr-1", "hr-2", "hr-3", "hr-4",
        "hr-5", "hr-6", "hr-7", "hrv", "rhr", "steps",
    )

    private fun featureWindow(
        start: Long,
        exerciseMillis: Long = 0L,
        features: List<PassiveWindowFeature>,
    ) = PassiveFeatureWindow(
        start,
        start + PassiveWindowAggregator.WINDOW_MILLIS,
        "UTC",
        0,
        PassiveWindowQuality(1.0, exerciseMillis == 0L, exerciseMillis, null),
        features,
        emptyList(),
    )

    private fun successRead(
        family: PassiveSourceFamily,
        start: Long,
        end: Long,
        zone: ZoneId,
        attemptedAt: Long,
        records: List<PassiveSourceRecord>,
    ) = PassiveSourceRead(
        family,
        PassiveReadState.SUCCESS,
        PassiveReadRange(start, end, zone.id),
        attemptedAt,
        records,
    )

    private fun record(
        family: PassiveSourceFamily,
        kind: PassiveRecordKind,
        start: Long,
        value: Double?,
        id: String,
        end: Long = start,
    ) = PassiveSourceRecord(
        sourceFamily = family,
        kind = kind,
        eventStart = start,
        eventEnd = end,
        value = value,
        unit = if (kind == PassiveRecordKind.STEPS_INTERVAL) "count" else "unit",
        dataOriginPackage = "source.app",
        deviceManufacturer = "Maker",
        deviceModel = "Model",
        deviceType = "WATCH",
        sourceUpdatedTime = start + 1_000L,
        ingestedAt = start + 2_000L,
        zoneId = "Asia/Kolkata",
        zoneOffsetSeconds = 19_800,
        recordId = id,
        recordVersion = 1L,
    )
}
