package org.mindanchor.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one piece of arithmetic the pulse screen does.
 *
 * It is arithmetic and not a reading: `docs/CLINICAL_REVIEW.md` makes
 * never interpreting a WHO-5 score an invariant, so nothing here decides
 * whether a direction is good, and no threshold from any paper appears
 * in it.
 */
class SignedChangeTest {

    @Test
    fun `a rise carries its sign`() {
        assertEquals("+8", signedChange(current = 64, previous = 56))
    }

    @Test
    fun `a fall carries its sign`() {
        assertEquals("-12", signedChange(current = 44, previous = 56))
    }

    @Test
    fun `the first ever reading has nothing to compare against`() {
        assertNull(signedChange(current = 48, previous = null))
    }

    @Test
    fun `an unchanged score says nothing rather than plus zero`() {
        // "+0" is a line that costs a person a read and tells them
        // nothing.
        assertNull(signedChange(current = 56, previous = 56))
    }

    @Test
    fun `the full range of the instrument stays readable`() {
        assertEquals("+100", signedChange(current = 100, previous = 0))
        assertEquals("-100", signedChange(current = 0, previous = 100))
    }
}
