package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OpenLoopTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 10)
    private val lastNight = today.minusDays(1).toString()

    @Test
    fun `in the quiet hours with nothing written, it offers to take it`() {
        assertEquals(
            LoopPhase.CAPTURE,
            OpenLoop.phase(quietHours = true, note = null, notedDay = null, today = today),
        )
    }

    @Test
    fun `in the quiet hours with something already written, it says nothing more`() {
        // Asking twice in one night turns a release valve into a nag.
        assertEquals(
            LoopPhase.NONE,
            OpenLoop.phase(true, "email Ravi back", today.toString(), today),
        )
    }

    @Test
    fun `in the morning it hands last night's note back`() {
        assertEquals(
            LoopPhase.RETURN,
            OpenLoop.phase(false, "email Ravi back", lastNight, today),
        )
    }

    @Test
    fun `a note written after midnight still comes back the same morning`() {
        // The 1am scroll is the whole target. Its note is dated today, and
        // it must not be treated as already handed back.
        assertEquals(
            LoopPhase.RETURN,
            OpenLoop.phase(false, "email Ravi back", today.toString(), today),
        )
    }

    @Test
    fun `a note from three weeks ago is not an open loop`() {
        // Being shown it is being reminded of something already let go.
        assertEquals(
            LoopPhase.NONE,
            OpenLoop.phase(false, "email Ravi back", today.minusDays(21).toString(), today),
        )
    }

    @Test
    fun `out of hours with nothing written, it says nothing`() {
        assertEquals(LoopPhase.NONE, OpenLoop.phase(false, null, null, today))
        assertEquals(LoopPhase.NONE, OpenLoop.phase(false, "   ", lastNight, today))
    }

    @Test
    fun `a corrupt stored date says nothing rather than crashing the home screen`() {
        assertEquals(LoopPhase.NONE, OpenLoop.phase(false, "something", "not-a-date", today))
        assertEquals(LoopPhase.NONE, OpenLoop.phase(false, "something", null, today))
    }

    @Test
    fun `notes are trimmed, flattened and capped so this cannot become a task list`() {
        assertEquals("email Ravi back", OpenLoop.clean("  email Ravi back  "))
        assertEquals("one two", OpenLoop.clean("one\ntwo"))
        assertNull(OpenLoop.clean("   "))
        assertEquals(OpenLoop.MAX_LENGTH, OpenLoop.clean("x".repeat(500))!!.length)
    }
}
