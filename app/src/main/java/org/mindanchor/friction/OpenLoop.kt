package org.mindanchor.friction

import java.time.Instant
import java.time.LocalDate

/**
 * What, if anything, the home screen should say about an unfinished thing.
 *
 * The four phases are deliberately distinct:
 *
 *  - [CAPTURE]   : quiet hours, nothing written — invite the user to put a
 *                  line down.
 *  - [POSTPONED] : there is a note, but the user has scheduled a future
 *                  revisit. The launcher stays silent until that time
 *                  arrives. The "I will deal with this at 3pm" state, per
 *                  Borkovec 1994 and Watkins 2008: scheduling the worry is
 *                  the active ingredient, not avoidance.
 *  - [RETURN]    : out of quiet hours, something written, the
 *                  hand-it-back window — morning, or the next minute after
 *                  the postponed-at clock if the user picked a time.
 *  - [NONE]      : say nothing. The card is silent.
 */
enum class LoopPhase {
    /** In the quiet hours with nothing written down: offer to take it. */
    CAPTURE,

    /**
     * Out of the quiet hours with something written, but the user has
     * picked a specific revisit time that has not yet arrived. The
     * launcher is silent — the user said "I'll deal with it then", and
     * a reminder before then is the opposite of what they asked for.
     */
    POSTPONED,

    /** Out of the quiet hours with something written: hand it back. */
    RETURN,

    /** Say nothing. */
    NONE,
}

/**
 * The one unfinished thing that will not sit still at 1am.
 *
 * ## The two findings this joins
 *
 * The Zeigarnik effect: unfinished tasks intrude on cognition until they
 * are closed. Masicampo & Baumeister (2011, *J. Pers. Soc. Psychol.*
 * 101(4):667-683, DOI 10.1037/a0024192) found the release valve —
 * *writing a plan* for the unfinished task removes the intrusion about
 * as well as finishing it does. The "open loop" does not need to be
 * closed; it needs to be anchored.
 *
 * Separately, the mind-wanders-→-unhappy finding: Killingsworth &
 * Gilbert (2010, *Science* 330(6006):932, DOI 10.1126/science.1192439)
 * found people were less happy in 46.9% of waking samples (mind
 * wandering) than in the non-wandering samples, in every activity
 * category. The 1am scroll is frequently not a craving for the feed;
 * it is one open loop that will not close, and the feed is what is to
 * hand while it is open.
 *
 * And: sleep regularity is the strongest-evidenced target a phone
 * can act on at all. Windred et al. (2024, *SLEEP* 47(1):zsad285,
 * DOI 10.1093/sleep/zsad285), N=60,977 UK Biobank participants,
 * >10 million hours of wrist accelerometry: higher sleep regularity
 * was associated with 20-48% lower all-cause mortality, and was a
 * stronger predictor than sleep duration. Regularity beats duration.
 * The 1am-doomscroll pattern is exactly the thing that wrecks it.
 *
 * Nobody has put these together at the point where phones actually cost
 * people sleep. So: in the quiet hours, one line — what is still open?
 * Written down, put away, and handed back in the morning at a time
 * the person can do something about it. Two evidence-based mechanisms,
 * joined at a moment only a launcher can stand in.
 *
 * ## What it will not do
 *
 * It is not a task list and must never grow into one. One line, replaced
 * each night, cleared when it is handed back. A todo app that follows you
 * to bed is the opposite of the thing being attempted here — and an
 * accumulating list of things you did not do is a machine for making
 * somebody feel worse.
 */
object OpenLoop {

    /** Long enough for a sentence, short enough not to become a project. */
    const val MAX_LENGTH = 140

    /**
     * What to show, given the clock and what is stored.
     *
     * [notedDay] is the ISO date the note was written. A note is handed
     * back the morning after it was written and then not again — a line
     * from three weeks ago is not an open loop, it is clutter, and being
     * shown it is being reminded of something already let go.
     *
     * [postponedAt] is the user's optional explicit revisit time
     * (v0.25.5, worry postponement per Borkovec 1994 + Watkins 2008).
     * While the wall-clock is before [postponedAt], the launcher is
     * silent; the user said "I'll deal with it then" and an earlier
     * reminder is the opposite of what they asked for. Once the
     * postponed-at time is reached, the worry falls back into the
     * normal hand-it-back flow.
     *
     * [now] is the comparison instant. Defaults to [Instant.now] but is
     * an explicit parameter so the test surface can pin a clock.
     */
    fun phase(
        quietHours: Boolean,
        note: String?,
        notedDay: String?,
        today: LocalDate,
        postponedAt: Instant? = null,
        now: Instant = Instant.now(),
    ): LoopPhase {
        val written = !note.isNullOrBlank()
        // Worry postponement: while the user-chosen clock is in the
        // future, the launcher is silent. The check is on the Instant
        // (UTC), not on wall-clock — the user picked a wall-clock
        // moment, but the comparison has to be against a stable point
        // in time, which is what Instant gives us across DST shifts
        // and timezone changes.
        val postponed = written && postponedAt != null && postponedAt.isAfter(now)
        if (postponed) return LoopPhase.POSTPONED
        // A note from three weeks ago is not an open loop, it is
        // clutter, and being shown it is being reminded of something
        // already let go. Only notes dated today or yesterday
        // survive into the morning return window.
        val day = notedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val inWindow = day != null && (day == today || day == today.minusDays(1))
        // A single expression that covers all four phases. The
        // priority is: postponed > quiet-hours > return-window >
        // silence, and any priority without a precondition falls
        // through to the next.
        return when {
            postponed -> LoopPhase.POSTPONED
            quietHours -> if (written) LoopPhase.NONE else LoopPhase.CAPTURE
            !written -> LoopPhase.NONE
            inWindow -> LoopPhase.RETURN
            else -> LoopPhase.NONE
        }
    }

    /** Trims and caps. Blank in means nothing stored. */
    fun clean(note: String): String? =
        note.trim().replace('\n', ' ').take(MAX_LENGTH).ifEmpty { null }
}
