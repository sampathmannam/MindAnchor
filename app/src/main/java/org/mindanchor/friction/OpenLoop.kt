package org.mindanchor.friction

import java.time.LocalDate

/** What, if anything, the home screen should say about an unfinished thing. */
enum class LoopPhase {
    /** In the quiet hours with nothing written down: offer to take it. */
    CAPTURE,

    /** Out of the quiet hours with something written down: hand it back. */
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
 * are closed. Masicampo & Baumeister (2011) found the release valve —
 * *writing a plan* for the unfinished task removes the intrusion about as
 * well as finishing it does. Separately, sleep regularity is the
 * strongest-evidenced target a phone can act on at all (Windred et al.,
 * ~60k people), and it is wrecked by exactly this.
 *
 * Nobody has put the two together at the point where phones actually cost
 * people sleep. The 1am scroll is frequently not a craving for the feed;
 * it is one open loop that will not close, and the feed is what is to hand
 * while it is open.
 *
 * So: in the quiet hours, one line — what is still open? Written down, put
 * away, and handed back in the morning at a time the person can do
 * something about it. Two evidence-based mechanisms, joined at a moment
 * only a launcher can stand in.
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
     */
    fun phase(
        quietHours: Boolean,
        note: String?,
        notedDay: String?,
        today: LocalDate,
    ): LoopPhase {
        val written = !note.isNullOrBlank()
        if (quietHours) return if (written) LoopPhase.NONE else LoopPhase.CAPTURE
        if (!written) return LoopPhase.NONE
        val day = notedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return LoopPhase.NONE
        // Written last night, or earlier today before the window ended.
        return if (day == today || day == today.minusDays(1)) LoopPhase.RETURN else LoopPhase.NONE
    }

    /** Trims and caps. Blank in means nothing stored. */
    fun clean(note: String): String? =
        note.trim().replace('\n', ' ').take(MAX_LENGTH).ifEmpty { null }
}
