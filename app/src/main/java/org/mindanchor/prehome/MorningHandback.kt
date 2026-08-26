package org.mindanchor.prehome

import java.util.Locale
import org.mindanchor.friction.LoopPhase

/**
 * PreHome's morning additions: the open-loop handback (Masicampo &
 * Baumeister 2011 — writing the plan releases the loop) and at most one
 * sleep fact. Pure decisions; the activity does the DataStore work.
 *
 * @wording-reviewed — the sleep-fact line reaches the person every
 * deviating morning: two clock readings and a semicolon, no verdict.
 */
object MorningHandback {

    data class Handback(val note: String, val shouldClear: Boolean)

    /** Only RETURN speaks, and speaking means clearing — one handback each. */
    fun decide(phase: LoopPhase, note: String?): Handback? {
        if (phase != LoopPhase.RETURN) return null
        val body = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Handback(body, shouldClear = true)
    }

    /** Late only when 45+ minutes past the person's own usual onset. */
    const val LATE_BY_MINUTES = 45

    private const val SIX_PM_MINUTE = 18 * 60
    private const val MINUTES_IN_DAY = 24 * 60

    /**
     * Both parameters are minutes after 18:00 (Deviation.minutesAfterSixPm),
     * so a bedtime past midnight compares as later, never as earlier —
     * the same frame every sleep surface in this app uses.
     */
    fun sleepFact(lastOnsetAfterSixPm: Int?, usualOnsetAfterSixPm: Int?): String? {
        val last = lastOnsetAfterSixPm ?: return null
        val usual = usualOnsetAfterSixPm ?: return null
        if (last - usual < LATE_BY_MINUTES) return null
        return "Up until ${clock(last)}; your usual is ${clock(usual)}."
    }

    /** Minutes-after-18:00 back to a 12-hour clock reading. */
    private fun clock(afterSixPm: Int): String {
        val minuteOfDay = (afterSixPm + SIX_PM_MINUTE) % MINUTES_IN_DAY
        val hour12 = ((minuteOfDay / 60) % 12).let { if (it == 0) 12 else it }
        val amPm = if (minuteOfDay / 60 >= 12) "pm" else "am"
        return String.format(Locale.ROOT, "%d:%02d %s", hour12, minuteOfDay % 60, amPm)
    }
}
