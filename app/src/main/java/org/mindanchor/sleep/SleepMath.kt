package org.mindanchor.sleep

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure sleep-window estimation from screen-off gaps, and a simplified
 * regularity score. Regularity, not duration, is the evidence-backed target
 * (UK Biobank SRI work, docs/research/01 §4): consistent sleep timing
 * predicts better mental-health outcomes than hours slept.
 */
data class ScreenGap(val startMillis: Long, val endMillis: Long)

data class SleepWindow(
    val wakeDate: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationHours: Double get() = (endMillis - startMillis) / 3_600_000.0
}

object SleepMath {

    private const val MIN_SLEEP_HOURS = 3.0

    /**
     * For each night (evening 20:00 → next-day 12:00), the longest
     * screen-untouched gap of at least [MIN_SLEEP_HOURS] is taken as the
     * sleep window.
     */
    fun sleepWindows(gaps: List<ScreenGap>, zone: ZoneId): List<SleepWindow> {
        if (gaps.isEmpty()) return emptyList()
        val nights = gaps.groupBy { gap ->
            // A gap belongs to the night it starts in: gaps starting before
            // noon belong to the previous evening's night.
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(gap.startMillis), zone)
            if (start.hour < 12) start.toLocalDate().minusDays(1) else start.toLocalDate()
        }
        return nights.mapNotNull { (nightOf, nightGaps) ->
            val longest = nightGaps.maxByOrNull { it.endMillis - it.startMillis } ?: return@mapNotNull null
            val hours = (longest.endMillis - longest.startMillis) / 3_600_000.0
            if (hours < MIN_SLEEP_HOURS) return@mapNotNull null
            SleepWindow(
                wakeDate = nightOf.plusDays(1),
                startMillis = longest.startMillis,
                endMillis = longest.endMillis,
            )
        }.sortedBy { it.wakeDate }
    }

    /**
     * 0–100: mean overlap (intersection/union) of consecutive nights' sleep
     * windows on a noon-to-noon axis. 100 = identical timing every night.
     */
    fun regularityScore(windows: List<SleepWindow>, zone: ZoneId): Int? {
        if (windows.size < 2) return null
        val ranges = windows.map { window ->
            minutesFromNoon(window.startMillis, zone) to minutesFromNoon(window.endMillis, zone)
        }
        val overlaps = ranges.zipWithNext().map { (a, b) ->
            val intersection =
                (minOf(a.second, b.second) - maxOf(a.first, b.first)).coerceAtLeast(0)
            val union = maxOf(a.second, b.second) - minOf(a.first, b.first)
            if (union <= 0) 0.0 else intersection.toDouble() / union
        }
        return (overlaps.average() * 100).toInt()
    }

    private fun minutesFromNoon(millis: Long, zone: ZoneId): Int {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val minutesOfDay = dateTime.hour * 60 + dateTime.minute
        // Noon-to-noon axis keeps overnight windows contiguous.
        return if (minutesOfDay >= 720) minutesOfDay - 720 else minutesOfDay + 720
    }
}
