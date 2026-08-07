package org.mindanchor.sleep

/**
 * Counts how often somebody's nights have run later than their own usual,
 * and says nothing whatsoever about why.
 *
 * ## The finding this is a response to
 *
 * Müller, Harari et al. (*Scientific Reports* 2021): a GPS-mobility
 * depression pipeline scoring AUC 0.82 in N=57 students fell to **AUC 0.57
 * — chance — in N=5,262**. The 2025 JMIR review of mobile sensing for
 * depression: only 1 study in 9 could tell worsening from relapse from
 * recovery. Cross-person inference from phone signals does not transfer.
 *
 * The field's response to that has been to add sensors. The opposite
 * response is available and, as far as the surveys in `docs/research` go,
 * untried: keep the within-person baseline — the part that does
 * generalise — and **remove the inference step entirely**.
 *
 * So this counts nights. It does not name a mood, a state, an episode or
 * a risk. "You have gone to sleep more than an hour and a half later than
 * you usually do, on three of the last seven nights" is a fact about a
 * person's own screen, checkable by them, and wrong only if the arithmetic
 * is wrong. What it means is left with the only party who has the context
 * to know — and who may well already know, and be reading it as company
 * rather than as news.
 *
 * ## Why it is opt-in and lives in settings
 *
 * Delivered wrong, "you usually aren't" reads as surveillance or as
 * reproach, and it lands hardest on the person already keeping score
 * against themselves. It is off until asked for, it never notifies, and
 * the wording here is the part of this app most in want of somebody
 * clinically trained reading it before it reaches anybody who is
 * struggling.
 */
object Deviation {

    /** Fewer nights than this and "usual" is not a thing that exists yet. */
    const val MIN_NIGHTS = 5

    /**
     * How much later than usual counts as later.
     *
     * Ninety minutes rather than thirty: half an hour is an evening that
     * ran on, and a tool that remarks on it is a tool that remarks on
     * everything.
     */
    const val LATE_BY_MINUTES = 90

    /**
     * Sleep onset as minutes after 18:00, so that an evening running past
     * midnight increases instead of wrapping to zero.
     *
     * The honest limitation: a window starting before 18:00 lands near the
     * top of the range rather than below zero, so this is the wrong frame
     * for a day sleeper. [worthShowing] therefore refuses to speak at all
     * unless the nights are clustered, which a day sleeper's are — around
     * a different hour, but still clustered — so they simply get nothing
     * rather than something wrong.
     */
    fun minutesAfterSixPm(minuteOfDay: Int): Int = ((minuteOfDay - 18 * 60) + 1440) % 1440

    /** The middle value. Median rather than mean: one all-nighter should not move "usual". */
    fun usual(onsets: List<Int>): Int? {
        if (onsets.isEmpty()) return null
        val sorted = onsets.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }

    /** How many of [onsets] run at least [LATE_BY_MINUTES] past the usual. */
    fun laterThanUsual(onsets: List<Int>): Int {
        val usual = usual(onsets) ?: return 0
        return onsets.count { it - usual >= LATE_BY_MINUTES }
    }

    /**
     * Whether there is anything worth putting on screen.
     *
     * Needs enough nights to have a usual at all, and needs at least one
     * night that actually ran late — with no late nights the only honest
     * output is silence, and a screen that says "nothing unusual" every
     * day is a screen that has taught somebody to check it.
     */
    fun worthShowing(onsets: List<Int>): Boolean =
        onsets.size >= MIN_NIGHTS && laterThanUsual(onsets) > 0
}
