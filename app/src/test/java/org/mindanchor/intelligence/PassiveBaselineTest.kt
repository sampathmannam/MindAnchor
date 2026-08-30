package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveBaselineTest {
    private fun days(count: Int, start: LocalDate = LocalDate.parse("2026-01-01")) =
        List(count) { i ->
            val date = start.plusDays(i.toLong())
            PassiveDay(
                date,
                PassiveDataStatus.AVAILABLE_FINAL,
                mapOf(PassiveFeature.STEPS to (5_000 + (i % 9) * 100).toDouble()),
                baselineSegment = "a",
                sourceUpdatedTime = date.toEpochDay(),
                ingestedAt = date.toEpochDay(),
            )
        }

    @Test fun `baseline stays unavailable below sixty eligible days`() {
        val history = days(59)
        val targetDay = history.last().day.plusDays(1)
        assertFalse(PassiveBaselineBuilder.evaluate(history, targetDay, targetDay.toEpochDay(), "a").ready)
    }

    @Test fun `baseline requires weekday and weekend coverage`() {
        val weekdays = generateSequence(LocalDate.parse("2026-01-05")) { it.plusDays(1) }
            .filter { it.dayOfWeek.value <= 5 }.take(60)
            .map {
                PassiveDay(
                    it,
                    PassiveDataStatus.AVAILABLE_FINAL,
                    mapOf(PassiveFeature.STEPS to 5_000.0),
                    baselineSegment = "a",
                    sourceUpdatedTime = it.toEpochDay(),
                    ingestedAt = it.toEpochDay(),
                )
            }.toList()
        val targetDay = weekdays.last().day.plusDays(1)
        assertFalse(PassiveBaselineBuilder.evaluate(weekdays, targetDay, targetDay.toEpochDay(), "a").ready)
    }

    @Test fun `nonzero MAD uses the exact scaled MAD`() {
        var weekdayCounter = 0
        val history = days(60).map { day ->
            val value = if (day.day.dayOfWeek.value >= 6) {
                1.0
            } else if (weekdayCounter++ % 2 == 0) {
                0.0
            } else {
                2.0
            }
            day.copy(features = mapOf(PassiveFeature.STEPS to value))
        }
        val targetDay = history.last().day.plusDays(1)
        val feature = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
            .features.getValue(PassiveFeature.STEPS)
        assertEquals(1.0, feature.centre, 0.0)
        assertEquals(1.4826, feature.scale, 0.0)
    }

    @Test fun `zero MAD falls back to nonzero IQR`() {
        var weekdayCounter = 0
        var weekendCounter = 0
        val history = days(60).map { day ->
            val counter = if (day.day.dayOfWeek.value >= 6) weekendCounter++ else weekdayCounter++
            val value = if (counter % 4 == 0) 20.0 else 10.0
            day.copy(features = mapOf(PassiveFeature.STEPS to value))
        }
        val targetDay = history.last().day.plusDays(1)
        val scale = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
            .features.getValue(PassiveFeature.STEPS).scale
        assertEquals(7.5 / 1.349, scale, 0.0)
    }

    @Test fun `undersized target stratum pools all eligible feature observations`() {
        var weekdayCounter = 0
        var featureCounter = 0
        val history = days(60).map { day ->
            val includeFeature = day.day.dayOfWeek.value >= 6 || weekdayCounter++ < 13
            val features = if (includeFeature) {
                mapOf(PassiveFeature.STEPS to (featureCounter++ % 3).toDouble())
            } else {
                emptyMap()
            }
            day.copy(features = features)
        }
        val targetDay = history.last().day.plusDays(1)
        val feature = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
            .features.getValue(PassiveFeature.STEPS)
        assertTrue(feature.pooledStratum)
        assertEquals(31, feature.sampleCount)
    }

    @Test fun `pooled feature stays absent with thirteen eligible values`() {
        val history = days(60).mapIndexed { index, day ->
            day.copy(
                features = if (index < 13) {
                    mapOf(PassiveFeature.STEPS to (index % 3).toDouble())
                } else {
                    emptyMap()
                },
            )
        }

        val targetDay = history.last().day.plusDays(1)
        val baseline = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!

        assertFalse(baseline.features.containsKey(PassiveFeature.STEPS))
    }

    @Test fun `pooled feature becomes available with fourteen eligible values`() {
        val history = days(60).mapIndexed { index, day ->
            day.copy(
                features = if (index < 14) {
                    mapOf(PassiveFeature.STEPS to (index % 3).toDouble())
                } else {
                    emptyMap()
                },
            )
        }

        val targetDay = history.last().day.plusDays(1)
        val feature = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
            .features.getValue(PassiveFeature.STEPS)

        assertEquals(14, feature.sampleCount)
        assertTrue(feature.pooledStratum)
    }

    @Test fun `nonfinal statuses do not contribute to eligibility or feature samples`() {
        val history = days(60)
        val targetDay = history.last().day.plusDays(5)
        val reference = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
            .features.getValue(PassiveFeature.STEPS)
        val statuses = listOf(
            PassiveDataStatus.AVAILABLE_PROVISIONAL,
            PassiveDataStatus.INSUFFICIENT_DATA,
            PassiveDataStatus.SUPPRESSED_EXERCISE,
            PassiveDataStatus.BASELINE_BUILDING,
        )
        statuses.forEachIndexed { index, status ->
            val ineligible = PassiveDay(
                day = history.last().day.plusDays(index + 1L),
                dataStatus = status,
                features = mapOf(PassiveFeature.STEPS to 99_999.0),
                baselineSegment = "a",
                sourceUpdatedTime = history.last().sourceUpdatedTime + index + 1L,
                ingestedAt = history.last().ingestedAt + index + 1L,
            )
            val extended = history + ineligible
            assertEquals(
                status.name,
                60,
                PassiveBaselineBuilder.evaluate(extended, targetDay, targetDay.toEpochDay(), "a").eligibleDays,
            )
            val feature = PassiveBaselineBuilder.build(extended, targetDay, targetDay.toEpochDay(), "a")!!
                .features.getValue(PassiveFeature.STEPS)
            assertEquals(status.name, reference.sampleCount, feature.sampleCount)
        }
    }

    @Test fun `constant feature is omitted instead of divided by epsilon`() {
        val history = days(60).map { it.copy(features = mapOf(PassiveFeature.STEPS to 10.0)) }
        val targetDay = history.last().day.plusDays(1)
        assertFalse(PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
            .features.containsKey(PassiveFeature.STEPS))
    }

    @Test fun `effective history is chronological and uses the newest eligible known revision`() {
        val first = LocalDate.parse("2026-01-01")
        val second = first.plusDays(1)
        val history = listOf(
            revision(second, 30.0, sourceUpdatedTime = 30L, ingestedAt = 30L),
            revision(first, 99.0, sourceUpdatedTime = 40L, ingestedAt = 400L),
            revision(first, 10.0, sourceUpdatedTime = 10L, ingestedAt = 20L),
            revision(first, 20.0, sourceUpdatedTime = 20L, ingestedAt = 10L),
            revision(
                first,
                80.0,
                sourceUpdatedTime = 25L,
                ingestedAt = 25L,
                status = PassiveDataStatus.AVAILABLE_PROVISIONAL,
            ),
            revision(second, 70.0, sourceUpdatedTime = 35L, ingestedAt = 35L, segment = "other"),
        ).shuffled(java.util.Random(42L))

        val effective = PassiveHistory.effectiveFinalDays(
            history = history,
            targetDay = second.plusDays(1),
            asOfTime = 100L,
            segment = "a",
        )

        assertEquals(listOf(first, second), effective.map { it.day })
        assertEquals(listOf(20.0, 30.0), effective.map { it.features.getValue(PassiveFeature.STEPS) })
    }

    @Test fun `effective final days use the last append when revision timestamps tie`() {
        val day = LocalDate.parse("2026-01-01")
        val older = revision(day, 99_999.0, sourceUpdatedTime = 20L, ingestedAt = 30L)
        val newer = revision(day, 1.0, sourceUpdatedTime = 20L, ingestedAt = 30L)

        val effective = PassiveHistory.effectiveFinalDays(
            history = listOf(older, newer),
            targetDay = day.plusDays(1L),
            asOfTime = 30L,
            segment = "a",
        )

        assertEquals(newer, effective.single())
    }

    @Test fun `effective observations use the last append when as-of timestamps tie`() {
        val day = LocalDate.parse("2026-01-01")
        val older = observation(day, 30L, PassiveObservationState.SUSTAINED_DEVIATION, "older append")
        val newer = observation(day, 30L, PassiveObservationState.WITHIN_PERSON_RANGE, "newer append")

        val effective = PassiveHistory.effectiveObservations(
            prior = listOf(older, newer),
            targetDay = day.plusDays(1L),
            asOfTime = 30L,
            segment = "a",
        )

        assertEquals(newer, effective.single())
    }

    @Test fun `effective observations preserve as-of precedence over append order`() {
        val day = LocalDate.parse("2026-01-01")
        val newestAsOf = observation(day, 30L, PassiveObservationState.SUSTAINED_DEVIATION, "newest as-of")
        val appendedLaterWithOlderAsOf = observation(
            day,
            20L,
            PassiveObservationState.WITHIN_PERSON_RANGE,
            "older as-of",
        )

        val effective = PassiveHistory.effectiveObservations(
            prior = listOf(newestAsOf, appendedLaterWithOlderAsOf),
            targetDay = day.plusDays(1L),
            asOfTime = 30L,
            segment = "a",
        )

        assertEquals(newestAsOf, effective.single())
    }

    @Test fun `duplicate revisions do not inflate the sixty distinct day floor`() {
        val history = days(59)
        val duplicate = history.last().copy(
            sourceUpdatedTime = history.last().sourceUpdatedTime + 1L,
            ingestedAt = history.last().ingestedAt + 1L,
        )
        val targetDay = history.last().day.plusDays(1)

        val baseline = PassiveBaselineBuilder.build(
            history = history + duplicate,
            targetDay = targetDay,
            asOfTime = targetDay.toEpochDay(),
            segment = "a",
        )

        assertNull(baseline)
    }

    @Test fun `reference baseline freezes at the first ready history prefix`() {
        val initial = days(60).mapIndexed { index, day ->
            day.copy(features = mapOf(PassiveFeature.STEPS to (index % 2 * 2).toDouble()))
        }
        val later = days(14, initial.last().day.plusDays(1)).mapIndexed { index, day ->
            day.copy(features = mapOf(PassiveFeature.STEPS to 100.0 + (index % 2) * 2.0))
        }
        val initialTarget = initial.last().day.plusDays(1)
        val laterTarget = later.last().day.plusDays(1)

        val first = PassiveBaselineBuilder.build(initial, initialTarget, initialTarget.toEpochDay(), "a")!!
        val frozen = PassiveBaselineBuilder.build(
            initial + later,
            laterTarget,
            laterTarget.toEpochDay(),
            "a",
        )!!

        assertEquals(60, frozen.referenceDays)
        assertEquals(
            first.features.getValue(PassiveFeature.STEPS).centre,
            frozen.features.getValue(PassiveFeature.STEPS).centre,
            0.0,
        )
    }

    @Test fun `reference baseline ignores revisions ingested after its first eligible cutoff`() {
        var weekdayIndex = 0
        val initial = days(60).map { day ->
            val value = if (day.day.dayOfWeek.value >= 6) {
                (day.day.toEpochDay() % 2).toDouble()
            } else {
                when (weekdayIndex++) {
                    0 -> 0.0
                    in 1..21 -> 10.0
                    else -> 20.0
                }
            }
            day.copy(features = mapOf(PassiveFeature.STEPS to value))
        }
        val original = initial.first()
        val correctionBeforeEligibility = original.copy(
            features = mapOf(PassiveFeature.STEPS to 30.0),
            sourceUpdatedTime = original.sourceUpdatedTime + 1_000L,
            ingestedAt = initial.last().ingestedAt - 1L,
        )
        val correctionAfterFreeze = original.copy(
            features = mapOf(PassiveFeature.STEPS to -30.0),
            sourceUpdatedTime = original.sourceUpdatedTime + 2_000L,
            ingestedAt = initial.last().ingestedAt + 100L,
        )
        val targetDay = initial.last().day.plusDays(1)
        val withoutCorrection = PassiveBaselineBuilder.build(
            initial,
            targetDay,
            initial.last().ingestedAt,
            "a",
        )!!
        val firstEligible = PassiveBaselineBuilder.build(
            initial + correctionBeforeEligibility,
            targetDay,
            initial.last().ingestedAt,
            "a",
        )!!
        val rebuiltLater = PassiveBaselineBuilder.build(
            initial + correctionBeforeEligibility + correctionAfterFreeze,
            targetDay.plusDays(100),
            correctionAfterFreeze.ingestedAt,
            "a",
        )!!

        assertNotEquals(
            withoutCorrection.features.getValue(PassiveFeature.STEPS).centre,
            firstEligible.features.getValue(PassiveFeature.STEPS).centre,
            0.0,
        )
        assertEquals(initial.last().ingestedAt, firstEligible.frozenAsOfTime)
        assertEquals(initial.last().day, firstEligible.frozenThroughDay)
        assertEquals(firstEligible, rebuiltLater)
    }

    @Test fun `trailing candidate uses the latest fifty six eligible distinct days`() {
        val initial = days(60)
        val later = days(56, initial.last().day.plusDays(1)).mapIndexed { index, day ->
            day.copy(features = mapOf(PassiveFeature.STEPS to 100.0 + (index % 2) * 2.0))
        }
        val targetDay = later.last().day.plusDays(1)
        val reference = PassiveBaselineBuilder.build(
            initial + later,
            targetDay,
            targetDay.toEpochDay(),
            "a",
        )!!

        val candidate = PassiveBaselineBuilder.buildTrailingCandidate(
            history = initial + later,
            targetDay = targetDay,
            asOfTime = targetDay.toEpochDay(),
            segment = "a",
            reference = reference,
        )!!

        assertEquals(56, candidate.referenceDays)
        assertEquals(101.0, candidate.features.getValue(PassiveFeature.STEPS).centre, 0.0)
    }

    @Test fun `trailing candidate matches the frozen feature stratum for stable calendar rhythm`() {
        val initial = calendarRhythmDays(60)
        val later = calendarRhythmDays(56, initial.last().day.plusDays(1))
        val targetDay = later.last().day.plusDays(1)
        val history = initial + later
        val reference = PassiveBaselineBuilder.build(history, targetDay, targetDay.toEpochDay(), "a")!!
        val candidate = PassiveBaselineBuilder.buildTrailingCandidate(
            history,
            targetDay,
            targetDay.toEpochDay(),
            "a",
            reference,
        )!!

        assertFalse(reference.features.getValue(PassiveFeature.RESTING_HEART_RATE).pooledStratum)
        assertEquals(
            reference.features.getValue(PassiveFeature.RESTING_HEART_RATE).pooledStratum,
            candidate.features.getValue(PassiveFeature.RESTING_HEART_RATE).pooledStratum,
        )
        assertEquals(
            reference.features.getValue(PassiveFeature.RESTING_HEART_RATE).centre,
            candidate.features.getValue(PassiveFeature.RESTING_HEART_RATE).centre,
            0.0,
        )
        assertFalse(BaselineShiftDetector.assess(reference, candidate, targetDay).disagrees)
    }

    @Test fun `mixed reference populations use the comparison day stratum`() {
        val reference = PassiveBaseline(
            segment = "a",
            frozenAsOfTime = 1L,
            frozenThroughDay = LocalDate.parse("2026-03-01"),
            referenceDays = 60,
            features = mapOf(
                PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                    PassiveFeature.RESTING_HEART_RATE, 60.0, 5.0, 60, false,
                ),
                PassiveFeature.SLEEP_MINUTES to FeatureBaseline(
                    PassiveFeature.SLEEP_MINUTES, 420.0, 20.0, 60, true,
                ),
                PassiveFeature.STEPS to FeatureBaseline(
                    PassiveFeature.STEPS, 5_000.0, 500.0, 60, true,
                ),
            ),
        )
        val candidate = PassiveBaseline(
            segment = "a",
            frozenAsOfTime = 1L,
            frozenThroughDay = LocalDate.parse("2026-03-01"),
            referenceDays = 56,
            features = mapOf(
                PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                    PassiveFeature.RESTING_HEART_RATE, 65.0, 4.0, 14, false,
                ),
                PassiveFeature.SLEEP_MINUTES to FeatureBaseline(
                    PassiveFeature.SLEEP_MINUTES, 400.0, 15.0, 14, true,
                ),
                PassiveFeature.STEPS to FeatureBaseline(
                    PassiveFeature.STEPS, 5_250.0, 400.0, 14, true,
                ),
            ),
        )

        val assessment = BaselineShiftDetector.assess(
            reference,
            candidate,
            comparisonDay = LocalDate.parse("2026-03-02"),
        )

        assertTrue(assessment.disagrees)
        assertEquals(BaselineComparisonPopulation.WEEKDAY, assessment.comparisonPopulation)
        assertEquals(56, assessment.candidateDays)
        assertEquals(
            listOf(PassiveDomain.PHYSIOLOGY, PassiveDomain.SLEEP),
            assessment.domains.map { it.domain },
        )
        assertEquals(listOf(1.0, 1.0), assessment.domains.map { it.standardizedDisagreement })
    }

    @Test fun `all pooled references use pooled comparison population`() {
        val reference = PassiveBaseline(
            segment = "a",
            frozenAsOfTime = 1L,
            frozenThroughDay = LocalDate.parse("2026-03-01"),
            referenceDays = 60,
            features = mapOf(
                PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                    PassiveFeature.RESTING_HEART_RATE, 60.0, 5.0, 60, true,
                ),
                PassiveFeature.SLEEP_MINUTES to FeatureBaseline(
                    PassiveFeature.SLEEP_MINUTES, 420.0, 20.0, 60, true,
                ),
            ),
        )
        val candidate = reference.copy(
            referenceDays = 56,
            features = mapOf(
                PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                    PassiveFeature.RESTING_HEART_RATE, 65.0, 4.0, 56, true,
                ),
                PassiveFeature.SLEEP_MINUTES to FeatureBaseline(
                    PassiveFeature.SLEEP_MINUTES, 400.0, 15.0, 56, true,
                ),
            ),
        )

        val assessment = BaselineShiftDetector.assess(
            reference,
            candidate,
            comparisonDay = LocalDate.parse("2026-03-07"),
        )

        assertEquals(BaselineComparisonPopulation.POOLED, assessment.comparisonPopulation)
    }

    private fun revision(
        date: LocalDate,
        value: Double,
        sourceUpdatedTime: Long,
        ingestedAt: Long,
        status: PassiveDataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        segment: String = "a",
    ) = PassiveDay(
        day = date,
        dataStatus = status,
        features = mapOf(PassiveFeature.STEPS to value),
        baselineSegment = segment,
        sourceUpdatedTime = sourceUpdatedTime,
        ingestedAt = ingestedAt,
    )

    private fun observation(
        day: LocalDate,
        asOfTime: Long,
        state: PassiveObservationState,
        explanation: String,
    ) = PassiveObservation(
        day = day,
        asOfTime = asOfTime,
        dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        state = state,
        threshold = 1.0,
        crossed = state != PassiveObservationState.WITHIN_PERSON_RANGE,
        baselineDays = 60,
        frozenBaselineAsOfTime = null,
        frozenBaselineThroughDay = null,
        baselineSegment = "a",
        domains = emptyList(),
        calibration = null,
        baselineShift = null,
        explanation = explanation,
    )

    private fun calendarRhythmDays(count: Int, start: LocalDate = LocalDate.parse("2026-01-01")) =
        List(count) { index ->
            val date = start.plusDays(index.toLong())
            val alternate = (date.toEpochDay() and 1L).toDouble()
            val weekend = date.dayOfWeek.value >= 6
            PassiveDay(
                day = date,
                dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
                features = mapOf(
                    PassiveFeature.RESTING_HEART_RATE to if (weekend) {
                        100.0 + 2.0 * alternate
                    } else {
                        60.0 + 2.0 * alternate
                    },
                    PassiveFeature.SLEEP_MINUTES to if (weekend) 220.0 + 20.0 * alternate else 420.0 + 20.0 * alternate,
                ),
                baselineSegment = "a",
                sourceUpdatedTime = date.toEpochDay(),
                ingestedAt = date.toEpochDay(),
            )
        }
}
