package org.mindanchor.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The three screen-state facts the usage event log can tell this app. */
enum class ScreenEventKind { INTERACTIVE, NON_INTERACTIVE, UNLOCKED }

data class ScreenEvent(val kind: ScreenEventKind, val timeMillis: Long)

/**
 * One day's screen rhythm, each half honest about absence: null means the
 * log could not say, never that the answer was zero.
 */
data class DayRhythm(val firstUnlockMinute: Int?, val screenMinutes: Int?)

/**
 * Turns the phone's own screen event log into two daily numbers: the
 * minute of the first unlock after 03:00, and the minutes the screen
 * spent lit.
 *
 * Pure arithmetic over a list, like [org.mindanchor.sleep.SleepMath] and
 * for the same reason — the event log is an Android service, but what is
 * done with it has to be testable on a bare JVM.
 *
 * ## Why the first pickup starts counting at 03:00
 *
 * "First unlock of the day" is a morning fact, and a phone checked one
 * last time at half past midnight is the end of an evening, not the start
 * of a day. Cutting at 03:00 uses the quietest hour of ordinary use, the
 * same reasoning the sleep estimate leans on. The honest cost: for
 * someone whose day genuinely starts at 02:00 the first pickup lands on
 * the floor's far side and reads later than it was. That is the day
 * sleepers' caveat [org.mindanchor.sleep.Deviation] already documents,
 * carried by the same signal family.
 *
 * ## Why days outside the observed log are null, not zero
 *
 * Android keeps detailed usage events for only a few days. A day from
 * before the log's reach has no events, and "no events" read naively is
 * a day of zero screen minutes — a fabricated perfect abstinence that
 * would sit in the baseline forever. So days at or before the first
 * observed event's day report null for both halves, and the first
 * observed day itself is excluded too, because the log may have started
 * mid-way through it.
 */
object ScreenRhythm {

    /** Unlocks before this minute are the tail of a night, not a morning. */
    const val MORNING_FLOOR_MINUTE = 3 * 60

    /**
     * Aggregates [events] into per-day rhythms for [days].
     *
     * [until] closes a screen-on span still open when the log ends — the
     * caller's "now". Events need not be sorted.
     */
    fun days(
        events: List<ScreenEvent>,
        days: List<LocalDate>,
        zone: ZoneId,
        until: Long,
    ): Map<LocalDate, DayRhythm> {
        if (days.isEmpty()) return emptyMap()
        val sorted = events.sortedBy { it.timeMillis }
        val firstObserved = sorted.firstOrNull()
            ?.let { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }

        // First unlock at or after the morning floor, per day. Ascending
        // order means the first candidate seen for a day is the answer.
        val unlockByDay = mutableMapOf<LocalDate, Int>()
        for (event in sorted) {
            if (event.kind != ScreenEventKind.UNLOCKED) continue
            val at = Instant.ofEpochMilli(event.timeMillis).atZone(zone)
            val minute = at.hour * 60 + at.minute
            if (minute < MORNING_FLOOR_MINUTE) continue
            val day = at.toLocalDate()
            if (day !in unlockByDay) unlockByDay[day] = minute
        }

        // Screen-on time, credited to the calendar day it happened in,
        // spans split at midnight so an evening running past twelve
        // charges each day its own share.
        val millisByDay = mutableMapOf<LocalDate, Long>()
        fun credit(fromMillis: Long, toMillis: Long) {
            var cursor = fromMillis
            while (cursor < toMillis) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = minOf(toMillis, dayEnd)
                millisByDay[day] = (millisByDay[day] ?: 0L) + (end - cursor)
                cursor = end
            }
        }
        var onSince: Long? = null
        for (event in sorted) {
            when (event.kind) {
                // An unlock implies a lit screen, so it may open a span
                // when the log dropped the interactive event itself —
                // defensive, and a no-op when the span is already open.
                ScreenEventKind.INTERACTIVE, ScreenEventKind.UNLOCKED ->
                    if (onSince == null) onSince = event.timeMillis

                ScreenEventKind.NON_INTERACTIVE -> {
                    onSince?.let { credit(it, event.timeMillis) }
                    onSince = null
                }
            }
        }
        onSince?.let { credit(it, until) }

        return days.associateWith { day ->
            val observed = firstObserved != null && day > firstObserved
            DayRhythm(
                firstUnlockMinute = if (observed) unlockByDay[day] else null,
                screenMinutes = if (observed) ((millisByDay[day] ?: 0L) / 60_000L).toInt() else null,
            )
        }
    }
}
