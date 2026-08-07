package org.mindanchor.friction

/**
 * How hard the pause should push, this time.
 *
 * The gate used to be identical always: the same breath, the same
 * question, at nine in the morning and at three in the morning. That is
 * exactly the design "Scrolling in the Deep" (CHI 2025, 72 participants
 * over seven days) found stops working — people desensitise to
 * interventions "due to the lack of contextual relevance", and a ceremony
 * repeated without regard to the moment becomes wallpaper to swipe
 * through.
 *
 * Two findings from that study drive what follows, and only those two,
 * because they are the ones a launcher can actually observe:
 *
 *  - **Sleepiness lowers reactance.** Late at night people accept
 *    interruption more readily rather than resenting it, so that is when
 *    the pause is worth its full weight.
 *  - **Repetition breeds desensitisation.** Running the same ceremony five
 *    times in ten minutes trains a person to dismiss it, and the sixth
 *    time teaches nothing.
 *
 * Deliberately *not* modelled: mood. The same study found low valence at
 * home slows responsiveness, which is a real effect, but a launcher cannot
 * see mood — it could only guess from behaviour, and a wrong guess here
 * means pushing hardest at someone already struggling. Guessing at somebody's
 * emotional state from how often they opened an app is not a thing this
 * app is entitled to do.
 */
enum class FrictionTone {
    /** The full pause: breath, then the question. */
    FULL,

    /** The question alone, no breath. Something already asked, recently. */
    BRIEF,

    /** One line and a way through. Asking again would only teach dismissal. */
    FEATHER,
}

object FrictionContext {

    /**
     * Re-opens within this window count as the same reaching-for-the-phone
     * rather than as separate decisions.
     */
    const val RECENT_WINDOW_MILLIS = 10 * 60 * 1000L

    /** After this many re-opens in the window, the ceremony has stopped landing. */
    const val REPEATS_BEFORE_FEATHER = 3

    /** One prior reach already means the first pause did not settle it. */
    const val REPEATS_BEFORE_BRIEF = 1

    /**
     * Chooses the tone.
     *
     * [recentOpens] counts how many times this same app has *already* been
     * opened through the gate inside [RECENT_WINDOW_MILLIS] — so nought on
     * the first reach, one on the second, and so on. [insideSleepWindow]
     * is true when the phone's own estimated quiet hours have begun — the
     * app already works this out for the sleep rhythm view, so nothing new
     * is sensed and no permission is added.
     *
     * Lateness wins over repetition. Someone opening an app for the fourth
     * time at two in the morning is the exact case the bedtime-procrastination
     * literature is about, and the moment a pause is most likely to be
     * accepted rather than resented.
     */
    fun toneFor(recentOpens: Int, insideSleepWindow: Boolean): FrictionTone = when {
        insideSleepWindow -> FrictionTone.FULL
        recentOpens >= REPEATS_BEFORE_FEATHER -> FrictionTone.FEATHER
        recentOpens >= REPEATS_BEFORE_BRIEF -> FrictionTone.BRIEF
        else -> FrictionTone.FULL
    }
}
