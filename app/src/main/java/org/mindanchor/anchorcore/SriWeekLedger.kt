package org.mindanchor.anchorcore

import java.time.LocalDate

/**
 * Two dated snapshots of the trailing-7-night regularity score, rolled
 * weekly, so "vs the prior week" has a real number to point at. The
 * sources only hold ~8 days of screen events; without this ledger the
 * SLEEP_IRREGULAR fact would be a function nothing could ever call.
 * Missing or stale data yields null, and null yields silence.
 */
object SriWeekLedger {

    data class Slot(val day: LocalDate, val score: Int)

    data class Roll(val prev: Slot?, val cur: Slot?, val lastWeekSri: Int?)

    /** A current slot this old hands its score to prev and re-anchors. */
    const val ROLL_AFTER_DAYS = 7L

    /** A prev slot older than this is history, not "last week". */
    const val STALE_AFTER_DAYS = 13L

    fun roll(prev: Slot?, cur: Slot?, today: LocalDate, liveScore: Int?): Roll {
        var p = prev
        var c = cur
        if (liveScore != null) {
            c = when {
                c == null -> Slot(today, liveScore)
                !today.isBefore(c.day.plusDays(ROLL_AFTER_DAYS)) -> {
                    p = c
                    Slot(today, liveScore)
                }
                // Same anchor: keep the date, carry the newest score, so
                // prev ends up holding "the score as of the last roll".
                else -> Slot(c.day, liveScore)
            }
        }
        val last = p?.takeIf { !today.isAfter(it.day.plusDays(STALE_AFTER_DAYS)) }?.score
        return Roll(p, c, last)
    }
}
