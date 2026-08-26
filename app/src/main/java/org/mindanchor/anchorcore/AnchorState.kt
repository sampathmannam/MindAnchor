package org.mindanchor.anchorcore

/**
 * The loop's whole output: either warming up or steady, and when steady,
 * which facts are live and whether the trailing week is flagged.
 */
sealed interface AnchorState {
    data class WarmingUp(val daysObserved: Int) : AnchorState

    data class Steady(
        val facts: List<DayFact>,
        val weekFlagged: Boolean,
        val computedAtEpochMillis: Long,
    ) : AnchorState {
        /** Convenience for the one hook that only cares about late nights. */
        val lateNightCluster: DayFact?
            get() = facts.firstOrNull { it.kind == FactKind.LATE_NIGHT_CLUSTER }
    }

    companion object {
        /**
         * Below this there is no baseline to read anything against — the
         * spec's cold-start rule: the app says nothing until it knows
         * something. Counted over the trailing 14 days (AnchorCoreSource).
         */
        const val MIN_OBSERVED_DAYS = 7

        fun of(
            daysObserved: Int,
            facts: List<DayFact>,
            weekFlagged: Boolean = facts.isNotEmpty(),
            now: Long,
        ): AnchorState =
            if (daysObserved < MIN_OBSERVED_DAYS) {
                WarmingUp(daysObserved)
            } else {
                Steady(facts = facts, weekFlagged = weekFlagged, computedAtEpochMillis = now)
            }
    }
}

/**
 * The flagged-week hysteresis: any fact keeps the week flagged; seven
 * consecutive clean days unflag it. Stored as one int streak
 * (AnchorPrefs, Task 5), whose *default is 7*, so a person whose loop
 * has never flagged anything starts unflagged rather than serving a
 * seven-day sentence for data they never produced.
 */
object WeekPicture {
    const val CLEAN_DAYS_TO_UNFLAG = 7

    /** New streak length after today. A flag resets it to zero. */
    fun reduce(flaggedToday: Boolean, cleanStreak: Int): Int =
        if (flaggedToday) 0 else (cleanStreak + 1).coerceAtMost(CLEAN_DAYS_TO_UNFLAG)

    /** Flagged while a fact fired today, or before the streak completes. */
    fun isFlagged(flaggedToday: Boolean, cleanStreak: Int): Boolean =
        flaggedToday || cleanStreak < CLEAN_DAYS_TO_UNFLAG
}
