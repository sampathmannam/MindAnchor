package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The failure this guards against is subtle and one-directional: a
 * baseline that quietly absorbs the bad stretch it exists to notice, and
 * then reports that everything is fine.
 */
class BaselineTest {

    private val steady = List(20) { 50.0 }

    @Test
    fun `median handles odd and even counts`() {
        assertEquals(3.0, Baseline.median(listOf(1.0, 3.0, 5.0))!!, 1e-9)
        assertEquals(3.0, Baseline.median(listOf(1.0, 2.0, 4.0, 5.0))!!, 1e-9)
    }

    @Test
    fun `no history means no usual, and that is an answer`() {
        assertNull(Baseline.median(emptyList()))
        assertNull(Baseline.robustSigma(emptyList()))
        assertNull(Baseline.robustZ(50.0, emptyList()))
    }

    @Test
    fun `too little history refuses rather than guessing`() {
        val thirteen = List(13) { 40.0 + it }
        assertNull(Baseline.robustZ(200.0, thirteen))
        val fourteen = List(14) { 40.0 + it }
        assertTrue(Baseline.robustZ(200.0, fourteen) != null)
    }

    @Test
    fun `an ordinary day is not notable`() {
        val history = List(20) { 45.0 + (it % 5) }
        assertFalse(Baseline.isNotable(Baseline.robustZ(47.0, history)))
    }

    @Test
    fun `a genuinely unusual day is notable in both directions`() {
        val history = List(20) { 45.0 + (it % 5) }
        assertTrue(Baseline.isNotable(Baseline.robustZ(90.0, history)))
        assertTrue(Baseline.isNotable(Baseline.robustZ(5.0, history)))
    }

    @Test
    fun `one terrible week does not redefine usual`() {
        // The whole reason for median and MAD. Twenty ordinary nights plus
        // six catastrophic ones: the baseline must still describe the
        // ordinary nights, so that night twenty-seven still reads as bad.
        val ordinary = List(20) { 45.0 + (it % 4) }
        val awful = List(6) { 5.0 }
        val history = ordinary + awful

        val centre = Baseline.median(history)!!
        assertTrue("median dragged to $centre by the bad week", centre >= 44.0)

        val z = Baseline.robustZ(5.0, history)!!
        assertTrue("another awful night no longer reads as unusual: z=$z", abs(z) >= 2.0)
    }

    @Test
    fun `a mean and standard deviation would have absorbed that week`() {
        // Stated as a test so the claim in the KDoc is checkable rather
        // than merely asserted.
        val history = List(20) { 46.0 } + List(6) { 5.0 }
        val mean = history.average()
        val sd = kotlin.math.sqrt(history.sumOf { (it - mean) * (it - mean) } / history.size)
        val naiveZ = abs((5.0 - mean) / sd)
        val robustZ = abs(Baseline.robustZ(5.0, history)!!)
        assertTrue("naive z should be unremarkable: $naiveZ", naiveZ < 2.0)
        assertTrue("robust z should still flag it: $robustZ", robustZ >= 2.0)
    }

    @Test
    fun `a perfectly regular history does not make every wobble enormous`() {
        // Without a dispersion floor, MAD is zero here and a five-minute
        // difference becomes a z-score of forty.
        val z = Baseline.robustZ(51.0, steady)
        assertTrue("z was $z", z != null && abs(z) < 5.0)
    }

    @Test
    fun `zero-centred history does not divide by zero`() {
        val zeros = List(20) { 0.0 }
        val z = Baseline.robustZ(0.0, zeros)
        assertTrue("must not be infinite or NaN", z == null || z.isFinite())
    }

    @Test
    fun `null z is never notable`() {
        assertFalse(Baseline.isNotable(null))
    }

    @Test
    fun `robust sigma is comparable to a standard deviation on clean data`() {
        // The 1.4826 scaling is what lets a robust z be read on the
        // familiar scale. On normal-ish symmetric data the two should be
        // in the same ballpark rather than differing by a factor.
        val symmetric = listOf(
            40.0, 42.0, 44.0, 46.0, 48.0, 50.0, 52.0, 54.0, 56.0, 58.0,
            60.0, 44.0, 46.0, 48.0, 50.0, 52.0, 54.0, 56.0, 48.0, 52.0,
        )
        val mean = symmetric.average()
        val sd = kotlin.math.sqrt(symmetric.sumOf { (it - mean) * (it - mean) } / symmetric.size)
        val robust = Baseline.robustSigma(symmetric)!!
        assertTrue("sd=$sd robust=$robust", robust in (sd * 0.5)..(sd * 1.6))
    }
}
