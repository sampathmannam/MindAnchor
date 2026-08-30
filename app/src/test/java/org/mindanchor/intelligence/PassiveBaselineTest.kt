package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveBaselineTest {
    private fun days(count: Int, start: LocalDate = LocalDate.parse("2026-01-01")) =
        List(count) { i ->
            PassiveDay(start.plusDays(i.toLong()), PassiveDataStatus.AVAILABLE_FINAL,
                mapOf(PassiveFeature.STEPS to (5_000 + (i % 9) * 100).toDouble()), baselineSegment = "a")
        }

    @Test fun `baseline stays unavailable below sixty eligible days`() {
        assertFalse(PassiveBaselineBuilder.evaluate(days(59), "a").ready)
    }

    @Test fun `baseline requires weekday and weekend coverage`() {
        val weekdays = generateSequence(LocalDate.parse("2026-01-05")) { it.plusDays(1) }
            .filter { it.dayOfWeek.value <= 5 }.take(60)
            .map { PassiveDay(it, PassiveDataStatus.AVAILABLE_FINAL,
                mapOf(PassiveFeature.STEPS to 5_000.0), baselineSegment = "a") }.toList()
        assertFalse(PassiveBaselineBuilder.evaluate(weekdays, "a").ready)
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
        val feature = PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
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
        val scale = PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
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
        val feature = PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
            .features.getValue(PassiveFeature.STEPS)
        assertTrue(feature.pooledStratum)
        assertEquals(31, feature.sampleCount)
    }

    @Test fun `nonfinal statuses do not contribute to eligibility or feature samples`() {
        val history = days(60)
        val targetDay = history.last().day.plusDays(5)
        val reference = PassiveBaselineBuilder.build(history, targetDay, "a")!!
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
            )
            val extended = history + ineligible
            assertEquals(status.name, 60, PassiveBaselineBuilder.evaluate(extended, "a").eligibleDays)
            val feature = PassiveBaselineBuilder.build(extended, targetDay, "a")!!
                .features.getValue(PassiveFeature.STEPS)
            assertEquals(status.name, reference.sampleCount, feature.sampleCount)
        }
    }

    @Test fun `constant feature is omitted instead of divided by epsilon`() {
        val history = days(60).map { it.copy(features = mapOf(PassiveFeature.STEPS to 10.0)) }
        assertFalse(PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
            .features.containsKey(PassiveFeature.STEPS))
    }
}
