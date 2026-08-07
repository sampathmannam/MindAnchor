package org.mindanchor.grayscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Grayscale borrows the setting that colour-blind people depend on. These
 * tests are about giving it back.
 */
class GrayscalePolicyTest {

    private val deuteranomaly = 1
    private val protanomaly = 2

    @Test
    fun `a configured colour correction is remembered before grayscale takes the slot`() {
        assertEquals(
            ColourState(enabled = true, mode = deuteranomaly),
            GrayscalePolicy.stateToRemember(
                current = ColourState(enabled = true, mode = deuteranomaly),
                alreadyRemembered = false,
            ),
        )
    }

    @Test
    fun `a correction configured but switched off is remembered too`() {
        // Someone who turns their correction on and off through the day
        // still has a mode chosen, and it is still theirs to lose.
        assertEquals(
            ColourState(enabled = false, mode = protanomaly),
            GrayscalePolicy.stateToRemember(
                current = ColourState(enabled = false, mode = protanomaly),
                alreadyRemembered = false,
            ),
        )
    }

    @Test
    fun `the first record wins, so our own grey is never mistaken for theirs`() {
        // Grayscale on at 22:00, on again at 22:05. Without this the second
        // write would record monochromacy as the person's preference and
        // the real one would be gone.
        assertNull(
            GrayscalePolicy.stateToRemember(
                current = ColourState(enabled = true, mode = GrayscalePolicy.MONOCHROMACY),
                alreadyRemembered = true,
            ),
        )
        assertNull(
            GrayscalePolicy.stateToRemember(
                current = ColourState(enabled = true, mode = deuteranomaly),
                alreadyRemembered = true,
            ),
        )
    }

    @Test
    fun `nothing is recorded when the screen is already fully grey`() {
        assertNull(
            GrayscalePolicy.stateToRemember(
                current = ColourState(enabled = true, mode = GrayscalePolicy.MONOCHROMACY),
                alreadyRemembered = false,
            ),
        )
    }

    @Test
    fun `turning grayscale off puts the correction back exactly as it was`() {
        val theirs = ColourState(enabled = true, mode = deuteranomaly)
        assertEquals(theirs, GrayscalePolicy.stateToRestore(theirs))
    }

    @Test
    fun `with nothing remembered the mode is cleared rather than left on monochromacy`() {
        // The old behaviour only cleared the enabled flag, leaving the mode
        // reading "monochromacy" in Android's own accessibility settings —
        // a setting the person never chose, sitting there ready to surprise
        // them the next time they switch colour correction on.
        assertEquals(
            ColourState(enabled = false, mode = GrayscalePolicy.MONOCHROMACY),
            GrayscalePolicy.stateToRestore(null),
        )
    }

    @Test
    fun `a full on-then-off cycle leaves the person exactly where they started`() {
        listOf(
            ColourState(enabled = true, mode = deuteranomaly),
            ColourState(enabled = false, mode = protanomaly),
            ColourState(enabled = false, mode = GrayscalePolicy.MONOCHROMACY),
        ).forEach { start ->
            val saved = GrayscalePolicy.stateToRemember(start, alreadyRemembered = false)
            assertEquals("round trip failed from $start", start, GrayscalePolicy.stateToRestore(saved))
        }
    }
}
