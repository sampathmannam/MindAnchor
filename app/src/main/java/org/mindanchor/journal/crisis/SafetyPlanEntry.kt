/*
 * v0.66.0 (DBT-grounded journal) — Task 3.
 *
 * The SafetyPlanEntry is the structured "if-then" plan a user writes for
 * themselves before they ever need it. It follows the Stanley-Brown
 * Safety Plan (Stanley & Brown 2012), the same 6-step card used in
 * DBT/CBT suicide-prevention and crisis-stabilisation work. The plan is
 * one instance per user (not per date) — it is updated over time, not
 * recorded day by day like the diary card.
 *
 * The 6 steps, in display order:
 *   1. Warning signs       — thoughts, images, mood, situations, behaviours
 *                            that signal a crisis is building
 *   2. Internal coping     — things the user can do alone to take the edge
 *                            off without contacting anyone
 *   3. Social distractions  — people or settings that take the person out
 *                            of their head for a while
 *   4. People to ask for help — friends or family the user can reach out to
 *   5. Professionals / agencies — clinicians, hotlines, crisis lines
 *   6. Means restriction   — steps the user has taken to limit access to
 *                            lethal means (the evidence-based single
 *                            strongest predictor of reduced suicide risk)
 *
 * The order is load-bearing. "Means restriction" is step 6 because the
 * Stanley-Brown card is intentionally structured to build the user up
 * (warning signs → coping → social → people → professionals) before
 * the last step, which is the most confronting. Reordering the labels
 * is not a cosmetic change.
 *
 * Shape is deliberately narrow:
 *   - Every field is free-text. There is no enum, no rating, no count.
 *     The plan is the user's own words; the app's job is to hold them
 *     and surface them at the right moment (long-press on the crisis
 *     dials, JournalCrisis.kt).
 *   - No `id`, no `createdAt`, no `updatedAt` on the data class itself.
 *     Those belong on the persistence wrapper, not on the value object.
 *
 * BPD-safe defaults carried from v0.65.0 apply unchanged: no streak
 * counter on the plan, no "your plan is N% complete" copy, no leaderboard,
 * no ranking. The plan is a tool the user owns; the app does not score it.
 */
package org.mindanchor.journal.crisis

data class SafetyPlanEntry(
    val warningSigns: String,
    val internalCoping: String,
    val socialDistractions: String,
    val people: String,
    val professionals: String,
    val meansRestriction: String,
) {
    companion object {
        /**
         * The 6 Stanley-Brown step labels, in display order. The list is
         * the source of truth for both the order of the steps on the
         * screen and the order of the fields the user fills in. Reorder
         * with care — see the file header for why "Means restriction"
         * is intentionally the last step.
         */
        val LABELS = listOf(
            "Warning signs",
            "Internal coping",
            "Social distractions",
            "People to ask for help",
            "Professionals / agencies",
            "Means restriction",
        )

        /**
         * The "user has not yet written a plan" placeholder. All six
         * fields are blank strings. The UI renders one of these on
         * first launch so the user sees a card with six empty text
         * fields, not a screen that says "no plan yet".
         */
        fun empty() = SafetyPlanEntry("", "", "", "", "", "")
    }
}
