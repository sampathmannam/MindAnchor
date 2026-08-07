package org.mindanchor.onboarding

/**
 * The parts of settings a person can be sent to.
 *
 * Named rather than numbered so that reordering the settings screen cannot
 * silently point somebody at the wrong thing.
 */
enum class SettingsSection {
    BATCHING,
    WATCH,
    SUNSET,
    SLEEP,
    PULSE,
    OWNER,
    GRAYSCALE,
}

/**
 * Connects what somebody said they were struggling with to the parts of
 * the app that address it.
 *
 * Onboarding asked the question — the ReDD finding (Lyngs et al., CHI 2024,
 * 280 students) is that *matching* a tool to an individual's own named
 * struggle mattered more than which tools were on offer — and then the
 * answer went into storage and was never read again. Every subsequent
 * screen treated everyone identically, which is the fixed-toolset design
 * that finding argues against.
 *
 * ## What this deliberately does not do
 *
 * It does not switch anything on. Onboarding promises in as many words
 * that nothing is enabled for you, and "Going Light" (CHI 2026) is the
 * reason: imposed minimalism fails where self-endorsed structure works.
 * Auto-enabling on the strength of four checkboxes would be exactly the
 * imposition that fails, and it would make a liar of the screen that
 * asked.
 *
 * So the answer is used for the one thing it is good for: settings is a
 * long screen, and this marks the parts the person came for.
 */
object GoalMap {

    /** The sections that speak to [goals]. Empty in, empty out. */
    fun sectionsFor(goals: Set<Goal>): Set<SettingsSection> =
        goals.flatMapTo(mutableSetOf()) { goal ->
            when (goal) {
                Goal.INTERRUPTIONS -> setOf(SettingsSection.BATCHING)

                // The pause itself is set per app by long-pressing it, not
                // in settings. What settings holds for this struggle is
                // where the pause applies and whether it can be enforced.
                Goal.COMPULSIVE_APPS -> setOf(SettingsSection.WATCH, SettingsSection.OWNER)

                // Grayscale belongs here rather than under a "colour"
                // heading of its own: its strongest evidenced use is
                // through the evening, and it is scheduled by the same
                // quiet hours as everything else in this group.
                Goal.SLEEP -> setOf(
                    SettingsSection.SUNSET,
                    SettingsSection.SLEEP,
                    SettingsSection.GRAYSCALE,
                )

                Goal.MEASUREMENT -> setOf(SettingsSection.PULSE)
            }
        }

    /** Whether [section] is one the person named a reason for. */
    fun isChosen(section: SettingsSection, goals: Set<Goal>): Boolean =
        section in sectionsFor(goals)
}
