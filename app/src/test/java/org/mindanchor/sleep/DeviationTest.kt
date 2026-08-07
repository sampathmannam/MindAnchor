package org.mindanchor.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviationTest {

    /** Minutes after 18:00, for readability in the cases below. */
    private fun at(hour: Int, minute: Int = 0) =
        Deviation.minutesAfterSixPm(hour * 60 + minute)

    @Test
    fun `an evening running past midnight counts as later, not as earlier`() {
        // The whole point of the 18:00 origin. In minute-of-day terms 01:00
        // is 60 and 23:00 is 1380, so a naive comparison would call 1am the
        // earliest night of the week.
        assertTrue(at(1) > at(23))
        assertEquals(0, at(18))
        assertEquals(240, at(22))
        assertEquals(420, at(1))
    }

    @Test
    fun `usual is the median, so one all-nighter does not become the baseline`() {
        val nights = listOf(at(22), at(22, 30), at(23), at(22, 15), at(4))
        assertEquals(at(22, 30), Deviation.usual(nights))
    }

    @Test
    fun `usual of an even number of nights is the midpoint`() {
        assertEquals(at(22, 30), Deviation.usual(listOf(at(22), at(23))))
    }

    @Test
    fun `no nights means there is no usual yet`() {
        assertNull(Deviation.usual(emptyList()))
        assertEquals(0, Deviation.laterThanUsual(emptyList()))
    }

    @Test
    fun `a steady week says nothing`() {
        // Silence is the correct output. A screen that reports "nothing
        // unusual" daily has taught somebody to check it.
        val steady = listOf(at(22), at(22, 20), at(22, 40), at(23), at(22, 10))
        assertEquals(0, Deviation.laterThanUsual(steady))
        assertFalse(Deviation.worthShowing(steady))
    }

    @Test
    fun `a half-hour drift is an evening that ran on, not a finding`() {
        val drifted = listOf(at(22), at(22, 30), at(22, 15), at(22, 45), at(22, 20))
        assertEquals(0, Deviation.laterThanUsual(drifted))
    }

    @Test
    fun `nights well past the usual are counted`() {
        val week = listOf(at(22), at(22, 30), at(22, 15), at(1), at(2), at(22, 20), at(1, 30))
        assertEquals(3, Deviation.laterThanUsual(week))
        assertTrue(Deviation.worthShowing(week))
    }

    @Test
    fun `too few nights reaches no verdict, however lopsided`() {
        val four = listOf(at(22), at(22), at(3), at(4))
        assertFalse(Deviation.worthShowing(four))
        val five = listOf(at(22), at(22), at(22), at(3), at(4))
        assertTrue(Deviation.worthShowing(five))
    }

    @Test
    fun `the boundary is inclusive at exactly ninety minutes`() {
        val nights = listOf(at(22), at(22), at(22), at(22), at(23, 30))
        assertEquals(1, Deviation.laterThanUsual(nights))
        val justUnder = listOf(at(22), at(22), at(22), at(22), at(23, 29))
        assertEquals(0, Deviation.laterThanUsual(justUnder))
    }
}
