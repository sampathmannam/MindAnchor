package org.mindanchor.pulse

/**
 * The cadence of the WHO-5 pulse reminder.
 *
 * ## Why this is not a constant
 *
 * `docs/research/11` reviewed the primary evidence for the WHO-5
 * cadence (Lally 2010, *Eur J Soc Psychol* 40(6):998–1009; the WHO 1998
 * DepCare Project; Topp et al. 2015, *Psychother Psychosom* 84(3):167–176;
 * the smartphone-EMA compliance meta-analysis Williams et al. 2021,
 * *JMIR* 23(3):e17023; Fogg *Tiny Habits* 2019; Wood *Good Habits, Bad
 * Habits* 2019; Lally/Gardner 2023, *Psychol Health* 38(4):518–540) and
 * concluded:
 *
 *  - The 14-day *floor* is correct: the WHO-5 stem asks about the past
 *    two weeks, so anything longer than 14 days creates recall error.
 *  - The 14-day *ceiling* is wrong: Lally's median habit-formation
 *    curve is 66 days (range 18–254); a fixed fortnightly schedule
 *    cannot *form* the check-in habit, and Wood's "double law of
 *    habit" predicts the reminder itself will become wallpaper.
 *  - Habituation is the most consistent finding in the broader
 *    literature (HeartSteps decay; Sense2Stop null; Meinhardt 2025
 *    CHI). A fixed schedule bakes habituation in.
 *
 * The replacement is a per-user, response-streak-conditioned taper:
 *
 *  | Pulses completed | Cadence |
 *  |------------------|---------|
 *  | 1–3              | every 7 days  |
 *  | 4–6              | every 10 days |
 *  | 7+ with ≥ 4 of last 5 completed | every 14 days |
 *
 * The user-specific component (the "≥ 4 of last 5 completed" rule) is
 * the habit-formation signal from Lally 2010 + Gardner 2023: the
 * cadence is allowed to lengthen only when the user's own pattern
 * shows the check-in has stabilised. Two consecutive missed pulses
 * drop the cadence back to 7 days — the EMA "missed opportunity
 * recovery" from Lally 2010 (one miss does not break the curve, two
 * compound).
 *
 * This is a *pure function* of (completed count, missed count, recent
 * completions) so it is testable without a device, an alarm, or a
 * database, and so the design decision is reviewable as code rather
 * than reverse-engineered from `PulseReminder`.
 */
object PulseCadence {

    /** Initial cadence — the most-frequent end of the taper. */
    const val EARLY_DAYS = 7L

    /** Mid cadence — the second stage of the taper. */
    const val MID_DAYS = 10L

    /** Final cadence — the WHO-5's natural recall window. */
    const val LATE_DAYS = 14L

    /** Pulses required before the cadence can advance. */
    const val EARLY_PULSES = 3

    /** Pulses required before the cadence can advance again. */
    const val MID_PULSES = 6

    /** Of the last N readings, how many must be "completed" to taper to LATE. */
    const val RECENT_WINDOW = 5
    const val RECENT_REQUIRED = 4

    /** Consecutive misses that bounce the cadence back to EARLY. */
    const val MISS_STREAK_TO_RESET = 2

    /**
     * Cadence in days, given the user's own history.
     *
     * - [completedCount] is the total number of completed pulses ever
     *   recorded for this user.
     * - [recentOutcomes] is the list of recent outcomes, *oldest first*,
     *   where each entry is `true` for completed and `false` for missed
     *   or skipped. The function looks at the last [RECENT_WINDOW]
     *   entries to decide whether the late cadence has been earned.
     * - [consecutiveMisses] is the run of misses leading up to now; a
     *   non-zero value short-circuits to [EARLY_DAYS].
     */
    fun cadenceDays(
        completedCount: Int,
        recentOutcomes: List<Boolean>,
        consecutiveMisses: Int,
    ): Long {
        // The streak-break rule goes first: a run of misses always
        // resets to the most-frequent end. Two missed pulses
        // compounding is the EMA evidence (Lally 2010, on missed
        // opportunities; Williams 2021 JMIR mEMA compliance).
        if (consecutiveMisses >= MISS_STREAK_TO_RESET) return EARLY_DAYS

        // Not enough data to taper: stay at the most-frequent end of
        // the schedule, so the new habit can form (Lally 2010:
        // median 66 days; Fogg 2019: anchor before taper).
        if (completedCount < EARLY_PULSES) return EARLY_DAYS
        if (completedCount < MID_PULSES) return MID_DAYS

        // Late cadence is gated on a user-specific signal: at least
        // RECENT_REQUIRED of the last RECENT_WINDOW outcomes were
        // completed. Below the threshold, the cadence is held at MID
        // even with a high lifetime count, because lifetime count
        // alone cannot distinguish a formed habit from a recent lapse.
        val window = recentOutcomes.takeLast(RECENT_WINDOW)
        if (window.size < RECENT_WINDOW) return MID_DAYS
        val completedInWindow = window.count { it }
        return if (completedInWindow >= RECENT_REQUIRED) LATE_DAYS else MID_DAYS
    }

    /**
     * The number of consecutive misses leading up to now, given a list
     * of recent outcomes (oldest first).
     *
     * The most-recent run, counted from the *end* of the list. A list
     * ending in [..., true, false, false] returns 2; a list ending in
     * [..., true] returns 0.
     */
    fun consecutiveMissesAtEnd(recentOutcomes: List<Boolean>): Int {
        var count = 0
        for (outcome in recentOutcomes.asReversed()) {
            if (outcome) break
            count++
        }
        return count
    }
}
