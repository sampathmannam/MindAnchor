package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveEstimatorTest {
    private val start = LocalDate.parse("2026-01-01")

    @Test fun `nonfinal and otherwise ineligible inputs produce no observation`() {
        val nonFinal = PassiveDataStatus.entries.filterNot { it.canEstimate }
        assertEquals(4, nonFinal.size)

        val nonFinalObservations = nonFinal.map { status ->
            PassiveEstimator.observe(day(start, status), start.toEpochDay(), emptyList(), emptyList(), 42L)
        }
        val targetDay = start.plusDays(60)
        val finalButIneligible = PassiveEstimator.observe(
            day(targetDay, features = mapOf(PassiveFeature.RESTING_HEART_RATE to 61.0)),
            targetDay.toEpochDay(),
            history(),
            emptyList(),
            42L,
        )

        (nonFinalObservations + finalButIneligible).forEach { observation ->
            assertEquals(PassiveObservationState.NO_OBSERVATION, observation.state)
            assertNull(observation.threshold)
            assertNull(observation.calibration)
            assertFalse(observation.crossed)
            assertTrue(observation.domains.isEmpty())
        }
        assertEquals(PassiveDataStatus.INSUFFICIENT_DATA, finalButIneligible.dataStatus)
    }

    @Test fun `fifty nine days cannot activate a baseline or threshold`() {
        val targetDay = start.plusDays(59)
        val observation = PassiveEstimator.observe(
            day(targetDay),
            targetDay.toEpochDay(),
            history(59),
            emptyList(),
            42L,
        )

        assertEquals(PassiveDataStatus.BASELINE_BUILDING, observation.dataStatus)
        assertEquals(PassiveObservationState.NO_OBSERVATION, observation.state)
        assertEquals(59, observation.baselineDays)
        assertNull(observation.threshold)
    }

    @Test fun `first crossing is transient once baseline and calibration are available`() {
        val observation = crossingObservation()

        assertEquals(PassiveObservationState.TRANSIENT_DEVIATION, observation.state)
        assertTrue(observation.crossed)
        assertNotNull(observation.threshold)
        assertEquals(42L, requireNotNull(observation.calibration).seed)
        assertEquals(BlockThresholdCalibrator.CONFIGURATION, observation.calibration?.configuration)
        assertEquals(start.plusDays(59).toEpochDay(), observation.frozenBaselineAsOfTime)
        assertEquals(start.plusDays(59), observation.frozenBaselineThroughDay)
        val explanation = observation.explanation.lowercase()
        listOf("recorded", "physiology", "sleep", "personal range", "recorded data only").forEach { token ->
            assertTrue("missing `$token` in $explanation", explanation.contains(token))
        }
    }

    @Test fun `calibration scores each historical day against its own calendar stratum`() {
        val referenceHistory = calendarRhythmHistory(60)
        val targetDay = referenceHistory.last().day.plusDays(1)
        val asOfTime = targetDay.toEpochDay()
        val observation = PassiveEstimator.observe(
            day(targetDay, features = calendarRhythmFeatures(targetDay)),
            asOfTime,
            referenceHistory,
            emptyList(),
            42L,
        )
        val frozen = PassiveBaselineBuilder.freeze(
            referenceHistory,
            targetDay,
            asOfTime,
            "device-a",
        )!!
        val expectedScores = referenceHistory.map { historicalDay ->
            PassiveScorer.score(
                historicalDay,
                PassiveBaselineBuilder.build(frozen, historicalDay.day),
                asOfTime,
            )!!.score
        }

        assertEquals(
            BlockThresholdCalibrator.calibrate(expectedScores, 42L),
            observation.calibration,
        )
    }

    @Test fun `second crossing among three eligible observations is sustained`() {
        val targetDay = start.plusDays(60)
        val prior = listOf(
            priorObservation(targetDay.minusDays(2), crossed = true),
            priorObservation(targetDay.minusDays(1), crossed = false),
        )

        val observation = crossingObservation(prior)

        assertEquals(PassiveObservationState.SUSTAINED_DEVIATION, observation.state)
        assertTrue(observation.crossed)
        assertNotNull(observation.threshold)
    }

    @Test fun `only the latest eligible prior revision before the target can sustain a crossing`() {
        val targetDay = start.plusDays(60)
        val supersededDay = targetDay.minusDays(2)
        val prior = listOf(
            priorObservation(supersededDay, crossed = true),
            priorObservation(
                supersededDay,
                crossed = false,
                asOfTime = targetDay.minusDays(1).toEpochDay(),
            ),
            priorObservation(targetDay.minusDays(1), crossed = true, baselineSegment = "other"),
            priorObservation(targetDay, crossed = true),
            priorObservation(targetDay.plusDays(1), crossed = true),
            priorObservation(
                targetDay.minusDays(1),
                crossed = true,
                asOfTime = targetDay.toEpochDay() + 1L,
            ),
        ).shuffled(java.util.Random(42L))

        val observation = crossingObservation(prior)

        assertEquals(PassiveObservationState.TRANSIENT_DEVIATION, observation.state)
    }

    @Test fun `first eligible in-range day after a deviation is range-return pending`() {
        val targetDay = start.plusDays(60)
        val observation = inRangeObservation(
            listOf(priorObservation(targetDay.minusDays(1), crossed = true)),
        )

        assertEquals(PassiveObservationState.RANGE_RETURN_PENDING, observation.state)
        assertFalse(observation.crossed)
    }

    @Test fun `second consecutive eligible in-range day returns to within-person range`() {
        val targetDay = start.plusDays(60)
        val prior = listOf(
            priorObservation(targetDay.minusDays(2), crossed = true),
            priorObservation(
                targetDay.minusDays(1),
                crossed = false,
                state = PassiveObservationState.RANGE_RETURN_PENDING,
            ),
        )

        val observation = inRangeObservation(prior)

        assertEquals(PassiveObservationState.WITHIN_PERSON_RANGE, observation.state)
    }

    @Test fun `ineligible days neither count nor break consecutive eligible in-range recovery`() {
        val targetDay = start.plusDays(60)
        val deviation = priorObservation(targetDay.minusDays(3), crossed = true)
        val ineligible = priorObservation(
            targetDay.minusDays(1),
            crossed = false,
            status = PassiveDataStatus.INSUFFICIENT_DATA,
            state = PassiveObservationState.NO_OBSERVATION,
        )
        val firstAfterGap = inRangeObservation(listOf(deviation, ineligible))
        val secondAcrossGap = inRangeObservation(
            listOf(
                deviation,
                priorObservation(
                    targetDay.minusDays(2),
                    crossed = false,
                    state = PassiveObservationState.RANGE_RETURN_PENDING,
                ),
                ineligible,
            ),
        )

        assertEquals(PassiveObservationState.RANGE_RETURN_PENDING, firstAfterGap.state)
        assertEquals(PassiveObservationState.WITHIN_PERSON_RANGE, secondAcrossGap.state)
    }

    @Test fun `seven shifted weekday comparisons persist across unchanged weekends`() {
        val history = primedWeekdayShiftHistory()
        val prior = mutableListOf<PassiveObservation>()

        val observations = observeShiftedWeekdays(history, prior, weekdayCount = 7)
        val weekdays = observations.filter { it.day.dayOfWeek.value <= 5 }
        val weekends = observations.filter { it.day.dayOfWeek.value >= 6 }
        val observation = weekdays.last()

        assertEquals(7, weekdays.size)
        assertEquals(2, weekends.size)
        assertTrue(weekdays.all { it.baselineShift?.disagrees == true })
        assertTrue(
            weekdays.all {
                it.baselineShift?.comparisonPopulation == BaselineComparisonPopulation.WEEKDAY
            },
        )
        assertTrue(weekends.all { it.baselineShift?.disagrees == false })
        assertTrue(
            weekends.all {
                it.baselineShift?.comparisonPopulation == BaselineComparisonPopulation.WEEKEND
            },
        )
        assertTrue(weekends.none { it.state == PassiveObservationState.BASELINE_SHIFT_CANDIDATE })
        assertEquals(PassiveObservationState.BASELINE_SHIFT_CANDIDATE, observation.state)
        assertTrue(observation.crossed)
        assertEquals(2, requireNotNull(observation.baselineShift).domains.size)
        val explanation = observation.explanation.lowercase()
        assertTrue(explanation.contains("frozen reference"))
        assertTrue(explanation.contains("trailing candidate"))
        assertFalse(observation.explanation.contains("improvement", ignoreCase = true))
        assertFalse(observation.explanation.contains("deterioration", ignoreCase = true))
    }

    @Test fun `an unchanged weekday comparison breaks weekday shift persistence`() {
        val history = primedWeekdayShiftHistory()
        val prior = mutableListOf<PassiveObservation>()
        observeShiftedWeekdays(history, prior, weekdayCount = 6)
        appendCalendarHistory(history, count = 56, shiftWeekdays = false)
        val gapDay = history.last().day.plusDays(1)
        val gap = PassiveEstimator.observe(
            day(gapDay, features = calendarRhythmFeatures(gapDay)),
            gapDay.toEpochDay(),
            history,
            prior,
            42L,
        )
        prior += gap
        history += day(gapDay, features = calendarRhythmFeatures(gapDay))
        appendCalendarHistory(history, count = 56, shiftWeekdays = true)
        val targetDay = history.last().day.plusDays(1)

        val observation = PassiveEstimator.observe(
            day(targetDay, features = weekdayShiftFeatures(targetDay, crossing = true)),
            targetDay.toEpochDay(),
            history,
            prior,
            42L,
        )

        assertFalse(requireNotNull(gap.baselineShift).disagrees)
        assertEquals(BaselineComparisonPopulation.WEEKDAY, gap.baselineShift?.comparisonPopulation)
        assertTrue(requireNotNull(observation.baselineShift).disagrees)
        assertEquals(BaselineComparisonPopulation.WEEKDAY, observation.baselineShift?.comparisonPopulation)
        assertTrue(observation.crossed)
        assertFalse(observation.state == PassiveObservationState.BASELINE_SHIFT_CANDIDATE)
    }

    @Test fun `fixed explanations are observation only and exclude banned terms`() {
        val reference = history()
        val targetDay = start.plusDays(60)
        val noObservations = PassiveDataStatus.entries.filterNot { it.canEstimate }.map { status ->
            PassiveEstimator.observe(day(targetDay, status), targetDay.toEpochDay(), reference, emptyList(), 42L)
        }
        val baselineBuildingDay = start.plusDays(59)
        val baselineBuilding = PassiveEstimator.observe(
            day(baselineBuildingDay), baselineBuildingDay.toEpochDay(), history(59), emptyList(), 42L,
        )
        val insufficient = PassiveEstimator.observe(
            day(targetDay, features = mapOf(PassiveFeature.RESTING_HEART_RATE to 61.0)),
            targetDay.toEpochDay(),
            reference,
            emptyList(),
            42L,
        )
        val inRange = PassiveEstimator.observe(
            day(targetDay), targetDay.toEpochDay(), reference, emptyList(), 42L,
        )
        val transient = crossingObservation()
        val sustained = crossingObservation(listOf(priorObservation(targetDay.minusDays(1), crossed = true)))
        val observations = noObservations + baselineBuilding + insufficient + inRange + transient + sustained

        val statusTokens = mapOf(
            PassiveDataStatus.AVAILABLE_FINAL to listOf("coverage", "scored"),
            PassiveDataStatus.AVAILABLE_PROVISIONAL to listOf("not final"),
            PassiveDataStatus.INSUFFICIENT_DATA to listOf("insufficient"),
            PassiveDataStatus.SUPPRESSED_EXERCISE to listOf("exercise", "excluded"),
            PassiveDataStatus.BASELINE_BUILDING to listOf("baseline", "building", "13", "60"),
        )
        statusTokens.forEach { (status, tokens) ->
            val explanation = PassiveExplanation.noObservation(status, baselineDays = 13).lowercase()
            tokens.forEach { token ->
                assertTrue("$status missing `$token` in $explanation", explanation.contains(token))
            }
        }
        listOf("within", "personal range").forEach { token ->
            assertTrue(inRange.explanation.lowercase().contains(token))
        }
        assertNotNull(inRange.threshold)
        observations.filter { it.state == PassiveObservationState.NO_OBSERVATION }
            .forEach { assertNull(it.threshold) }

        val banned = listOf("anxiety", "depression", "bpd", "panic", "anger", "diagnosis", "illness", "disorder")
        observations.forEach { observation ->
            assertTrue(observation.explanation.isNotBlank())
            val explanation = observation.explanation.lowercase()
            banned.forEach { term ->
                assertFalse("${observation.dataStatus}: $term in $explanation", explanation.contains(term))
            }
        }
    }

    private fun crossingObservation(prior: List<PassiveObservation> = emptyList()): PassiveObservation =
        PassiveEstimator.observe(
            day(
                start.plusDays(60),
                features = mapOf(
                    PassiveFeature.RESTING_HEART_RATE to 80.0,
                    PassiveFeature.SLEEP_MINUTES to 300.0,
                ),
            ),
            start.plusDays(60).toEpochDay(),
            history(),
            prior,
            42L,
        )

    private fun inRangeObservation(prior: List<PassiveObservation>): PassiveObservation {
        val targetDay = start.plusDays(60)
        return PassiveEstimator.observe(
            day(targetDay),
            targetDay.toEpochDay(),
            history(),
            prior,
            42L,
        )
    }

    private fun history(count: Int = 60): List<PassiveDay> = List(count) { index ->
        val even = index % 2 == 0
        day(
            start.plusDays(index.toLong()),
            features = mapOf(
                PassiveFeature.RESTING_HEART_RATE to if (even) 60.0 else 62.0,
                PassiveFeature.SLEEP_MINUTES to if (even) 420.0 else 440.0,
            ),
        )
    }

    private fun calendarRhythmHistory(count: Int): List<PassiveDay> = List(count) { index ->
        val date = start.plusDays(index.toLong())
        day(date, features = calendarRhythmFeatures(date))
    }

    private fun calendarRhythmFeatures(date: LocalDate): Map<PassiveFeature, Double> {
        val alternate = (date.toEpochDay() and 1L).toDouble()
        val weekend = date.dayOfWeek.value >= 6
        return mapOf(
            PassiveFeature.RESTING_HEART_RATE to if (weekend) 100.0 + 2.0 * alternate else 60.0 + 2.0 * alternate,
            PassiveFeature.SLEEP_MINUTES to if (weekend) 220.0 + 20.0 * alternate else 420.0 + 20.0 * alternate,
        )
    }

    private fun primedWeekdayShiftHistory(): MutableList<PassiveDay> =
        calendarRhythmHistory(60).toMutableList().also { history ->
            appendCalendarHistory(history, count = 56, shiftWeekdays = true)
        }

    private fun appendCalendarHistory(
        history: MutableList<PassiveDay>,
        count: Int,
        shiftWeekdays: Boolean,
    ) {
        repeat(count) {
            val date = history.last().day.plusDays(1)
            val features = if (shiftWeekdays) {
                weekdayShiftFeatures(date, crossing = false)
            } else {
                calendarRhythmFeatures(date)
            }
            history += day(date, features = features)
        }
    }

    private fun observeShiftedWeekdays(
        history: MutableList<PassiveDay>,
        prior: MutableList<PassiveObservation>,
        weekdayCount: Int,
    ): List<PassiveObservation> {
        val observations = mutableListOf<PassiveObservation>()
        while (observations.count { it.day.dayOfWeek.value <= 5 } < weekdayCount) {
            val date = history.last().day.plusDays(1)
            val current = day(date, features = weekdayShiftFeatures(date, crossing = true))
            val observation = PassiveEstimator.observe(
                current,
                date.toEpochDay(),
                history,
                prior,
                42L,
            )
            observations += observation
            prior += observation
            history += current
        }
        return observations
    }

    private fun weekdayShiftFeatures(date: LocalDate, crossing: Boolean): Map<PassiveFeature, Double> {
        val features = calendarRhythmFeatures(date)
        if (date.dayOfWeek.value >= 6) return features
        return mapOf(
            PassiveFeature.RESTING_HEART_RATE to features.getValue(PassiveFeature.RESTING_HEART_RATE) +
                if (crossing) 30.0 else 20.0,
            PassiveFeature.SLEEP_MINUTES to features.getValue(PassiveFeature.SLEEP_MINUTES) -
                if (crossing) 180.0 else 120.0,
        )
    }

    private fun day(
        date: LocalDate,
        status: PassiveDataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        features: Map<PassiveFeature, Double> = mapOf(
            PassiveFeature.RESTING_HEART_RATE to 60.0,
            PassiveFeature.SLEEP_MINUTES to 420.0,
        ),
    ) = PassiveDay(
        date,
        status,
        features,
        baselineSegment = "device-a",
        sourceUpdatedTime = date.toEpochDay(),
        ingestedAt = date.toEpochDay(),
    )

    private fun priorObservation(
        date: LocalDate,
        crossed: Boolean,
        asOfTime: Long = date.toEpochDay(),
        baselineSegment: String = "device-a",
        status: PassiveDataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        state: PassiveObservationState = if (crossed) {
            PassiveObservationState.TRANSIENT_DEVIATION
        } else {
            PassiveObservationState.WITHIN_PERSON_RANGE
        },
    ) = PassiveObservation(
        day = date,
        asOfTime = asOfTime,
        dataStatus = status,
        state = state,
        threshold = 1.0,
        crossed = crossed,
        baselineDays = 60,
        frozenBaselineAsOfTime = null,
        frozenBaselineThroughDay = null,
        baselineSegment = baselineSegment,
        domains = emptyList(),
        calibration = null,
        baselineShift = null,
        explanation = "prior observation",
    )
}
