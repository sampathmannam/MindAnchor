package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mindanchor.friction.FrictionContext
import org.mindanchor.friction.FrictionTone

class FrictionToneHoldTest {

    @Test
    fun `unflagged weeks keep the current ladder`() {
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(1, false, weekFlagged = false))
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(3, false, weekFlagged = false))
    }

    @Test
    fun `flagged week holds full one reach longer outside the sleep window`() {
        assertEquals(FrictionTone.FULL, FrictionContext.toneFor(1, false, weekFlagged = true))
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(2, false, weekFlagged = true))
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(4, false, weekFlagged = true))
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(5, false, weekFlagged = true))
    }

    @Test
    fun `inside the sleep window full wins regardless`() {
        for (opens in 0..9) {
            assertEquals(FrictionTone.FULL, FrictionContext.toneFor(opens, true, weekFlagged = true))
        }
    }
}
