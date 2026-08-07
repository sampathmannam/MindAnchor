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

    /** How many releases a day there are. Three, per Fitz et al. */
    const val SLOTS = 3

    /**
     * How far one nudge moves a release time.
     *
     * Half an hour, the same step the quiet-hours nudgers use. Fine enough
     * to put a batch where somebody's lunch actually is, coarse enough
     * that moving a time by two hours is four taps rather than twenty-four.
     */
    const val NUDGE_MINUTES = 30L

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

    /**
     * Moves one release time, or refuses.
     *
     * Returns null when the move would land on another release. Two
     * batches at the same minute is not harmful — [nextRelease] takes the
     * first strictly after now, so a duplicate is simply skipped — but the
     * settings screen would show the same time twice and there would be no
     * way to tell three batches from two. Refusing is the same choice
     * [org.mindanchor.data.SunsetPrefs.setWindow] makes for a window whose
     * ends are equal, and for the same reason: a setting that cannot be
     * read back honestly should not be storable.
     *
     * Wrapping is deliberate. Nudging 23:45 later gives 00:15, and a night
     * worker's batches belong in their evening whatever the clock calls
     * it — the same reasoning as the quiet-hours window, which has always
     * been allowed to cross midnight.
     */
    fun nudged(times: List<LocalTime>, index: Int, byMinutes: Long): List<LocalTime>? {
        if (index !in times.indices) return null
        val moved = times[index].plusMinutes(byMinutes)
        if (times.withIndex().any { (other, time) -> other != index && time == moved }) return null
        return times.toMutableList().also { it[index] = moved }
    }

    /**
     * Whether a stored set of times can be used as it stands.
     *
     * A set that fails this is discarded in favour of [DEFAULT_TIMES]
     * rather than repaired: a corrupt preference should cost somebody
     * their setting, never their notifications.
     */
    fun isUsable(times: List<LocalTime>): Boolean =
        times.size == SLOTS && times.distinct().size == SLOTS
}
