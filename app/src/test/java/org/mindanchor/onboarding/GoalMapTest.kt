package org.mindanchor.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMapTest {

    @Test
    fun `naming nothing marks nothing`() {
        // Somebody who skipped the question gets the plain screen, not a
        // screen with everything highlighted, which would be the same as
        // nothing highlighted.
        assertTrue(GoalMap.sectionsFor(emptySet()).isEmpty())
    }

    @Test
    fun `interruptions points at batching`() {
        assertEquals(
            setOf(SettingsSection.BATCHING),
            GoalMap.sectionsFor(setOf(Goal.INTERRUPTIONS)),
        )
    }

    @Test
    fun `compulsive apps points at where the pause applies and at enforcement`() {
        assertEquals(
            setOf(SettingsSection.WATCH, SettingsSection.OWNER),
            GoalMap.sectionsFor(setOf(Goal.COMPULSIVE_APPS)),
        )
    }

    @Test
    fun `sleep points at the quiet hours, the rhythm view and the grey screen`() {
        assertEquals(
            setOf(SettingsSection.SUNSET, SettingsSection.SLEEP, SettingsSection.GRAYSCALE),
            GoalMap.sectionsFor(setOf(Goal.SLEEP)),
        )
    }

    @Test
    fun `measurement points at the pulse`() {
        assertEquals(
            setOf(SettingsSection.PULSE),
            GoalMap.sectionsFor(setOf(Goal.MEASUREMENT)),
        )
    }

    @Test
    fun `several struggles combine rather than one winning`() {
        val sections = GoalMap.sectionsFor(setOf(Goal.INTERRUPTIONS, Goal.SLEEP))
        assertTrue(SettingsSection.BATCHING in sections)
        assertTrue(SettingsSection.SUNSET in sections)
        assertEquals(4, sections.size)
    }

    @Test
    fun `every goal leads somewhere`() {
        // A goal that maps to nothing is a question asked for no reason.
        Goal.entries.forEach { goal ->
            assertTrue(
                "$goal maps to no section",
                GoalMap.sectionsFor(setOf(goal)).isNotEmpty(),
            )
        }
    }

    @Test
    fun `naming everything still leaves nothing unreachable`() {
        // Not every section belongs to a goal — appearance and backup are
        // for everyone — but nothing a goal claims may be missing.
        val all = GoalMap.sectionsFor(Goal.entries.toSet())
        assertTrue(all.containsAll(GoalMap.sectionsFor(setOf(Goal.SLEEP))))
        assertTrue(all.containsAll(GoalMap.sectionsFor(setOf(Goal.COMPULSIVE_APPS))))
    }

    @Test
    fun `isChosen agrees with sectionsFor`() {
        val goals = setOf(Goal.SLEEP)
        SettingsSection.entries.forEach { section ->
            assertEquals(
                "disagreement at $section",
                section in GoalMap.sectionsFor(goals),
                GoalMap.isChosen(section, goals),
            )
        }
        assertFalse(GoalMap.isChosen(SettingsSection.BATCHING, goals))
    }
}
