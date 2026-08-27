package org.mindanchor.prehome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.friction.LoopPhase

class PreHomeHandbackTest {

    @Test
    fun `return phase hands the note back and clears`() {
        val hb = MorningHandback.decide(LoopPhase.RETURN, "call the bank")
        assertEquals("call the bank", hb!!.note)
        assertEquals(true, hb.shouldClear)
    }

    @Test
    fun `other phases and blank notes say nothing`() {
        assertNull(MorningHandback.decide(LoopPhase.NONE, "x"))
        assertNull(MorningHandback.decide(LoopPhase.POSTPONED, "x"))
        assertNull(MorningHandback.decide(LoopPhase.CAPTURE, null))
        assertNull(MorningHandback.decide(LoopPhase.RETURN, "   "))
    }

    @Test
    fun `sleep fact speaks only from fortyfive past usual`() {
        // Minutes after 18:00: usual 23:00 -> 300.
        assertNull(MorningHandback.sleepFact(lastOnsetAfterSixPm = 330, usualOnsetAfterSixPm = 300)) // 23:30
        assertNotNull(MorningHandback.sleepFact(lastOnsetAfterSixPm = 350, usualOnsetAfterSixPm = 300)) // 23:50
        assertNotNull(MorningHandback.sleepFact(lastOnsetAfterSixPm = 450, usualOnsetAfterSixPm = 300)) // 01:30
    }

    @Test
    fun `sleep fact renders both clocks`() {
        val line = MorningHandback.sleepFact(lastOnsetAfterSixPm = 450, usualOnsetAfterSixPm = 330)!!
        assertTrue(line.contains("1:30 am"))
        assertTrue(line.contains("11:30 pm"))
    }

    @Test
    fun `missing data stays silent`() {
        assertNull(MorningHandback.sleepFact(null, 300))
        assertNull(MorningHandback.sleepFact(450, null))
    }
}
