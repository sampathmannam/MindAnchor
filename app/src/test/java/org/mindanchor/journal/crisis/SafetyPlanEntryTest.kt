/*
 * v0.66.0 (DBT-grounded journal) — Task 3.
 *
 * The SafetyPlanEntry is the structured "if-then" plan a user writes for
 * themselves before they ever need it (Stanley-Brown safety plan, 6 steps).
 * Two tests guard the two invariants the downstream tasks depend on:
 *
 *  1. `empty()` returns a plan with every step blank. Single instance per
 *     user (not per-date — there is exactly one plan, updated over time),
 *     so "blank" means the user has not filled anything in yet, not a
 *     particular date. The UI must render an empty plan as six empty
 *     text fields, not a pre-populated template.
 *
 *  2. `LABELS` is the display order, 1-indexed for the user (the
 *     Stanley-Brown card numbers them 1..6) but 0-indexed in code.
 *     The labels themselves are the exact wording from the card so the
 *     screen the user fills in matches the card in their hand.
 *
 * The Stanley-Brown 6 steps in display order are:
 *   1. Warning signs
 *   2. Internal coping
 *   3. Social distractions
 *   4. People to ask for help
 *   5. Professionals / agencies
 *   6. Means restriction
 */
package org.mindanchor.journal.crisis

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyPlanEntryTest {

    @Test
    fun `empty plan has all 6 steps blank`() {
        val p = SafetyPlanEntry.empty()
        assertEquals("", p.warningSigns)
        assertEquals("", p.internalCoping)
        assertEquals("", p.socialDistractions)
        assertEquals("", p.people)
        assertEquals("", p.professionals)
        assertEquals("", p.meansRestriction)
    }

    @Test
    fun `step labels are 1-indexed for display`() {
        // Display order is 1-indexed (Stanley-Brown card numbers 1..6),
        // but the list itself is 0-indexed in code. The first and last
        // labels are the ones most likely to be reordered by accident,
        // so they are the ones we pin.
        assertEquals("Warning signs", SafetyPlanEntry.LABELS[0])
        assertEquals("Means restriction", SafetyPlanEntry.LABELS[5])
    }
}
