package org.mindanchor.notifications

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure schedule math for batch releases. Default release times follow the
 * studied dosage of three batches per day (Fitz et al. 2019).
 */
object BatchSchedule {

    val DEFAULT_TIMES = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(12, 30),
        LocalTime.of(18, 0),
    )

    /** The next release strictly after [now]. */
    fun nextRelease(now: LocalDateTime, times: List<LocalTime> = DEFAULT_TIMES): LocalDateTime {
        val sorted = times.sorted()
        val todayNext = sorted.firstOrNull { it.isAfter(now.toLocalTime()) }
        return if (todayNext != null) {
            now.toLocalDate().atTime(todayNext)
        } else {
            now.toLocalDate().plusDays(1).atTime(sorted.first())
        }
    }
}
