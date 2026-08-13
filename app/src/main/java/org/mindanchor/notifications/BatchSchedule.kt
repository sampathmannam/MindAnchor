package org.mindanchor.notifications

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure schedule math for batch releases. Default release times follow the
 * studied dosage of three batches per day (Fitz et al. 2019).
 *
 * v0.25.10 (SOTA v2 bug-hunt B5/B9): the math is now zone-aware. The
 * pre-fix shape was `LocalDateTime.now()` + `atZone(systemDefault())`
 * at the call site, which silently shifts a spring-forward alarm
 * (2:30 AM doesn't exist, conversion lands at 3:30) and silently
 * conflates two `LocalDate.now()` reads across a midnight boundary
 * (the formatted time came from one system instant, the "today"
 * comparison came from a separate system instant). The fix: take
 * and return a [ZonedDateTime]; the call site reads `ZonedDateTime.now(zone)`
 * once and uses the result for both the comparison and the alarm.
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

    /**
     * The next release strictly after [now]. Returns a [ZonedDateTime]
     * so the caller can call `.toInstant().toEpochMilli()` without a
     * second zone conversion. On a spring-forward day, the requested
     * wall-clock time that does not exist in the zone is auto-shifted
     * to the next valid instant by [ZonedDateTime.of] (e.g. 2:30 →
     * 3:30 on a US spring-forward day, the documented SpringForwardGap
     * resolution). On a fall-back day, the first occurrence of the
     * ambiguous time wins (the documented FallBackOverlap resolution).
     */
    fun nextRelease(
        now: ZonedDateTime,
        times: List<LocalTime> = DEFAULT_TIMES,
    ): ZonedDateTime {
        val zone = now.zone
        val today: LocalDate = now.toLocalDate()
        val sorted = times.sorted()
        val todayNext = sorted.firstOrNull { it.isAfter(now.toLocalTime()) }
        val targetDate: LocalDate = if (todayNext != null) today else today.plusDays(1)
        val targetTime: LocalTime = todayNext ?: sorted.first()
        // ZonedDateTime.of(date, time, zone) handles DST gaps
        // (2:30 AM on a spring-forward day → 3:30 AM) and fall-back
        // overlaps (1:30 AM on a fall-back day → the first 1:30 AM).
        return ZonedDateTime.of(targetDate, targetTime, zone)
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
