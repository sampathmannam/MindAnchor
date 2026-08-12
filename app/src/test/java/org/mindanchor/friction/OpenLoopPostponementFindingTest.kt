package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The v0.25.5 worry-postponement shape pins. Each test below is
 * deliberately file-shape + pure-function: the worry-postponement
 * protocol (Borkovec 1994 + Watkins 2008) is a *user-facing* contract
 * — the launcher is silent while the user-chosen clock is in the
 * future, and falls back to the hand-it-back flow when the clock
 * arrives. A regression that drops the POSTPONED phase, or that
 * compares wall-clock instead of Instant, would let the launcher nag
 * the user before the time they picked. The five tests below are the
 * boundary.
 */
class OpenLoopPostponementFindingTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 10)
    private val yesterday: LocalDate = today.minusDays(1)

    @Test
    fun `POSTPONED is a distinct phase in the enum`() {
        // The four-phase enum is the contract. A regression that
        // collapses POSTPONED back into NONE would silently revert
        // the v0.25.5 feature to the v0.25.0 behaviour, where the
        // launcher could not honour the user's choice of revisit time.
        val values = LoopPhase.entries.toSet()
        assertEquals(4, values.size)
        assertTrue(LoopPhase.POSTPONED in values)
        assertNotEquals(LoopPhase.NONE, LoopPhase.POSTPONED)
        assertNotEquals(LoopPhase.CAPTURE, LoopPhase.POSTPONED)
        assertNotEquals(LoopPhase.RETURN, LoopPhase.POSTPONED)
    }

    @Test
    fun `phase returns POSTPONED when a note exists and the revisit time is in the future`() {
        // The Borkovec protocol's active ingredient is *scheduling* a
        // specific time; the launcher must respect it. A note +
        // a future postponed-at = POSTPONED, full stop.
        val now = Instant.parse("2026-03-10T08:00:00Z")
        val future = Instant.parse("2026-03-10T15:00:00Z")
        assertEquals(
            LoopPhase.POSTPONED,
            OpenLoop.phase(
                quietHours = false,
                note = "email Ravi back",
                notedDay = today.toString(),
                today = today,
                postponedAt = future,
                now = now,
            ),
        )
    }

    @Test
    fun `phase falls back to RETURN once the postponed clock has arrived`() {
        // The user picked a time. When that time arrives, the worry
        // should be handed back like any other morning. A regression
        // that keeps the launcher silent past the postponed-at would
        // bury the worry — the opposite of what the user asked for.
        val now = Instant.parse("2026-03-10T15:30:00Z")
        val arrived = Instant.parse("2026-03-10T15:00:00Z")
        assertEquals(
            LoopPhase.RETURN,
            OpenLoop.phase(
                quietHours = false,
                note = "email Ravi back",
                notedDay = yesterday.toString(),
                today = today,
                postponedAt = arrived,
                now = now,
            ),
        )
    }

    @Test
    fun `phase ignores postponedAt when there is no note`() {
        // The postponement is *of* a worry. With no worry, there is
        // nothing to postpone. A regression that returned POSTPONED
        // for an empty note would render a phantom "Back at 3pm" line
        // — confusing, and a privacy leak (the launcher would be
        // scheduling a non-existent thing).
        val now = Instant.parse("2026-03-10T08:00:00Z")
        val future = Instant.parse("2026-03-10T15:00:00Z")
        assertEquals(
            LoopPhase.NONE,
            OpenLoop.phase(
                quietHours = false,
                note = null,
                notedDay = null,
                today = today,
                postponedAt = future,
                now = now,
            ),
        )
        // Whitespace counts as no note.
        assertEquals(
            LoopPhase.NONE,
            OpenLoop.phase(
                quietHours = false,
                note = "   ",
                notedDay = yesterday.toString(),
                today = today,
                postponedAt = future,
                now = now,
            ),
        )
    }

    @Test
    fun `phase is timezone-stable - the postponed-at is an Instant, not a wall-clock`() {
        // The user picked 3pm local time. The stored value is an
        // Instant in UTC. A regression that compared against
        // LocalDateTime.now() would break across DST shifts and
        // timezone changes: a user on the east coast who picks 3pm
        // EST would see the launcher fire at noon UTC if the
        // comparison forgot the zone. The test below uses two
        // Instants in UTC; if the implementation parses one of them
        // as a wall-clock string, the comparison will be off by
        // hours and the test will fail.
        val now = Instant.parse("2026-03-10T13:00:00Z")
        val future = Instant.parse("2026-03-10T15:00:00Z")
        assertEquals(
            LoopPhase.POSTPONED,
            OpenLoop.phase(
                quietHours = false,
                note = "call mom",
                notedDay = today.toString(),
                today = today,
                postponedAt = future,
                now = now,
            ),
        )
        // The same comparison an hour later: the postponed clock
        // has now passed, so the worry is RETURNed. The two Instants
        // differ by 60 minutes; the result must change accordingly.
        val laterNow = Instant.parse("2026-03-10T16:00:00Z")
        assertEquals(
            LoopPhase.RETURN,
            OpenLoop.phase(
                quietHours = false,
                note = "call mom",
                notedDay = yesterday.toString(),
                today = today,
                postponedAt = future,
                now = laterNow,
            ),
        )
    }
}
