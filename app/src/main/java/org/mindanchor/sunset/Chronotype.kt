package org.mindanchor.sunset

import java.time.LocalTime

/**
 * The user's preferred phase of the day, captured at onboarding and
 * editable from the settings panel. The chronotype is the launcher's
 * *first* input into the default quiet-hours window — a 22:00 wind-down
 * is correct for a morning lark, wrong for a night owl, and three
 * hours early for a rotating shift worker.
 *
 * ## Why a chronotype, not a bedtime
 *
 * People know what they are. "Are you a morning person, a night
 * person, or somewhere in between?" is a one-tap question that
 * 95% of users can answer without thinking. "What time do you
 * want the wind-down to start?" is a question that requires the
 * user to *compute* an answer — most will pick the default.
 * Asking the chronotype first and inferring the window from it
 * puts the right default in front of the right person.
 *
 * ## What the chronotype is NOT
 *
 * It is not a clinical assessment (Horne-Ostberg), not a sleep-
 * tracker export, and not a permanent label. A user who picks
 * "night owl" can become a morning person after a job change,
 * and the chronotype should be re-editable in settings without
 * ceremony.
 *
 * The default windows below are research-anchored — see
 * `docs/research/22-research-index.md` (Roenneberg 2007 for the
 * chronotype distribution, Wittmann 2006 for social jetlag,
 * Åkerstedt 2003 + Kecklund 2016 for shift work). The specific
 * minute values are *design choices*: they are the wind-down
 * start (not the bedtime), and the shift-worker's window is
 * 09:00 → 17:00 (their *daytime* wind-down) rather than a night
 * wind-down. The launcher makes the window editable so the user
 * is never stuck with a default.
 */
enum class Chronotype {
    /**
     * The user is at their best in the morning, naturally tired by
     * 21:00. Roenneberg 2007's early-type population. Default
     * window: 21:00 → 06:00.
     */
    MORNING_LARK,

    /**
     * The user has no strong preference, and 22:00 → 07:00 (the
     * launcher default) is fine. The "neutral" is the explicit
     * answer to the onboarding question, not the absence of one.
     */
    NEUTRAL,

    /**
     * The user is at their best in the evening, naturally tired
     * by 01:00 or later. Roenneberg 2007's late-type population;
     * Wittmann 2006 documents the social-jetlag cost. Default
     * window: 00:00 → 08:00.
     */
    NIGHT_OWL,

    /**
     * The user works non-day shifts and sleeps during the day.
     * Åkerstedt 2003 + Kecklund 2016. Default window: 09:00 → 17:00
     * (the *daytime* wind-down — their "evening" is the morning
     * for everyone else).
     */
    SHIFT_WORKER,

    /**
     * The user has not answered the onboarding question. Treated
     * as [NEUTRAL] for default-window purposes; the settings
     * panel shows the chronotype as "not set" until the user
     * picks one.
     */
    UNKNOWN,
    ;

    /**
     * The default quiet-hours window for this chronotype.
     *
     * The window is the *wind-down* window, not the sleep
     * window. The intent is the launcher should start dimming
     * the phone 1 hour before the user's natural bedtime so
     * the wind-down lands *before* sleep, not *during* it.
     *
     * The specific minute values are design choices, not
     * research findings — see the file KDoc. The constants
     * are named rather than inline so the detekt MagicNumber
     * rule can read them.
     */
    fun defaultWindow(): Pair<LocalTime, LocalTime> = when (this) {
        MORNING_LARK -> LARK_START to LARK_END
        NEUTRAL -> NEUTRAL_START to NEUTRAL_END
        NIGHT_OWL -> OWL_START to OWL_END
        SHIFT_WORKER -> SHIFT_START to SHIFT_END
        UNKNOWN -> NEUTRAL_START to NEUTRAL_END
    }

    private companion object {
        // The four named windows. The minutes are the launcher's
        // design choice; the file KDoc documents which cited
        // research (if any) each window's *placement* rests on.
        val LARK_START: LocalTime = LocalTime.of(21, 0)
        val LARK_END: LocalTime = LocalTime.of(6, 0)
        val NEUTRAL_START: LocalTime = LocalTime.of(22, 0)
        val NEUTRAL_END: LocalTime = LocalTime.of(7, 0)
        val OWL_START: LocalTime = LocalTime.of(0, 0)
        val OWL_END: LocalTime = LocalTime.of(8, 0)
        val SHIFT_START: LocalTime = LocalTime.of(9, 0)
        val SHIFT_END: LocalTime = LocalTime.of(17, 0)
    }
}
