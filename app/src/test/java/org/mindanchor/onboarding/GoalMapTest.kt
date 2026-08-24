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
    fun `compulsive apps points at where the pause applies, at enforcement, and at the Going Light content-blocker`() {
        // v0.26+: Going Light (Castelo 2025 content-blocker)
        // is the natural follow-on for a person who named
        // compulsive apps. The mapping adds it to the set;
        // the test name grew to match.
        assertEquals(
            setOf(
                SettingsSection.WATCH,
                SettingsSection.OWNER,
                SettingsSection.GOING_LIGHT,
            ),
            GoalMap.sectionsFor(setOf(Goal.COMPULSIVE_APPS)),
        )
    }

    @Test
    fun `sleep points at the quiet hours, the rhythm view, the grey screen, and Going Light`() {
        // v0.26+: Going Light targets the same hours as
        // SUNSET but reaches for the mobile-internet
        // traffic (Castelo 2025). A person who named
        // sleep is the audience.
        assertEquals(
            setOf(
                SettingsSection.SUNSET,
                SettingsSection.SLEEP,
                SettingsSection.GRAYSCALE,
                SettingsSection.GOING_LIGHT,
            ),
            GoalMap.sectionsFor(setOf(Goal.SLEEP)),
        )
    }

    @Test
    fun `measurement points at the pulse and the wearable section`() {
        // v0.20.4: the wearable (Health Connect) section is
        // also a measurement surface — the N-of-1 wellness
        // signals are read here, not in the pulse section.
        // The goal maps to both, so a person who said
        // "I want to track" gets a marked section wherever
        // they look.
        assertEquals(
            setOf(SettingsSection.PULSE, SettingsSection.HEALTH_CONNECT),
            GoalMap.sectionsFor(setOf(Goal.MEASUREMENT)),
        )
    }

    @Test
    fun `several struggles combine rather than one winning`() {
        // v0.26+: SLEEP now also maps to GOING_LIGHT, so
        // the INTERRUPTIONS + SLEEP combination is
        // BATCHING (INTERRUPTIONS) + SUNSET/SLEEP/
        // GRAYSCALE/GOING_LIGHT (SLEEP) = 5 sections,
        // not 4.
        val sections = GoalMap.sectionsFor(setOf(Goal.INTERRUPTIONS, Goal.SLEEP))
        assertTrue(SettingsSection.BATCHING in sections)
        assertTrue(SettingsSection.SUNSET in sections)
        assertTrue(SettingsSection.GOING_LIGHT in sections)
        assertEquals(5, sections.size)
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
