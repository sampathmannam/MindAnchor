package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.*
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
        assertTrue(scale > 0.0)
    }

    @Test fun `constant feature is omitted instead of divided by epsilon`() {
        val history = days(60).map { it.copy(features = mapOf(PassiveFeature.STEPS to 10.0)) }
        assertFalse(PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
            .features.containsKey(PassiveFeature.STEPS))
    }
}
