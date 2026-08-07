package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnticipationTest {

    private fun holding() = Link(n = 40, rho = -0.6, rawP = 0.001, adjustedP = 0.004)
    private fun notHolding() = Link(n = 40, rho = -0.6, rawP = 0.001, adjustedP = 0.4)

    /**
     * Forty days where a low signal went with a low label. Built by hand
     * rather than generated, so what the neighbourhood should contain is
     * checkable by reading it.
     */
    private fun record(): List<Paired> = (0 until 40).map { day ->
        val signal = (day % 20).toDouble()
        Paired(signal = signal, label = if (signal < 5) 2.0 else 4.0)
    }

    @Test
    fun `a link that does not hold says nothing at all`() {
        // Not a quieter version of the same claim. Looking up a
        // neighbourhood for an unconfirmed link is finding whatever
        // pattern noise happened to leave behind, which is the assuming
        // from nothing this whole design refuses.
        assertNull(Anticipation.forToday(notHolding(), record(), todaysSignal = 1.0))
    }

    @Test
    fun `on a day like the low ones, it reports what was said on those days`() {
        val found = Anticipation.forToday(holding(), record(), todaysSignal = 0.0)
        assertNotNull(found)
        assertEquals(2.0, found!!.medianWhenLikeToday, 1e-9)
        assertTrue("the low days really were lower", found.lower)
    }

    @Test
    fun `on an ordinary day it says nothing`() {
        // A system that remarks on every difference it can measure is one
        // people stop reading.
        val flat = (0 until 40).map { Paired(signal = it.toDouble(), label = 3.0) }
        assertNull(Anticipation.forToday(holding(), flat, todaysSignal = 20.0))
    }

    @Test
    fun `a difference smaller than half a point is not worth saying`() {
        val barely = (0 until 40).map { day ->
            Paired(signal = day.toDouble(), label = if (day < 20) 3.0 else 3.0)
        }
        assertNull(Anticipation.forToday(holding(), barely, todaysSignal = 0.0))
    }

    @Test
    fun `too short a record is refused, however strong the link`() {
        val short = record().take(LinkFinder.MIN_PAIRED_DAYS - 1)
        assertNull(Anticipation.forToday(holding(), short, todaysSignal = 0.0))
    }

    @Test
    fun `the neighbourhood is a quarter of the record, and never tiny`() {
        val found = Anticipation.forToday(holding(), record(), todaysSignal = 0.0)
        assertEquals(10, found!!.similarDays)

        val minimal = (0 until LinkFinder.MIN_PAIRED_DAYS).map { day ->
            Paired(signal = day.toDouble(), label = if (day < 7) 1.0 else 4.0)
        }
        val small = Anticipation.forToday(holding(), minimal, todaysSignal = 0.0)
        assertTrue(
            "a neighbourhood below the floor is one bad night wearing a median",
            small!!.similarDays >= Anticipation.MIN_NEIGHBOURS,
        )
    }

    @Test
    fun `the same day asked twice gives the same answer`() {
        // Ties among equally near days must resolve the same way every
        // time, or the answer changes overnight on unchanged data.
        val tied = (0 until 40).map { day ->
            Paired(signal = (day % 4).toDouble(), label = (day % 5).toDouble())
        }
        assertEquals(
            Anticipation.forToday(holding(), tied, todaysSignal = 2.0),
            Anticipation.forToday(holding(), tied, todaysSignal = 2.0),
        )
    }

    @Test
    fun `both directions are reported`() {
        val worseWhenHigh = (0 until 40).map { day ->
            val signal = (day % 20).toDouble()
            Paired(signal = signal, label = if (signal > 15) 1.0 else 4.0)
        }
        val high = Anticipation.forToday(holding(), worseWhenHigh, todaysSignal = 19.0)
        assertTrue(high!!.lower)

        val low = Anticipation.forToday(holding(), worseWhenHigh, todaysSignal = 0.0)
        assertTrue("the ordinary side must read as not-lower", low == null || !low.lower)
    }

    @Test
    fun `the overall median is the whole record, not the neighbourhood`() {
        // The comparison is "days like today" against "an ordinary day".
        // Comparing the neighbourhood against itself would always find
        // nothing, and comparing it against the rest would overstate.
        val found = Anticipation.forToday(holding(), record(), todaysSignal = 0.0)
        assertEquals(Anticipation.median(record().map { it.label })!!, found!!.medianOverall, 1e-9)
    }

    @Test
    fun `median handles both parities and refuses an empty list`() {
        assertEquals(2.0, Anticipation.median(listOf(1.0, 2.0, 3.0))!!, 1e-9)
        assertEquals(2.5, Anticipation.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(Anticipation.median(emptyList()))
    }

    @Test
    fun `one extreme day cannot move the usual`() {
        val withOutlier = record() + Paired(signal = 100.0, label = 5.0)
        val found = Anticipation.forToday(holding(), withOutlier, todaysSignal = 0.0)
        assertEquals(2.0, found!!.medianWhenLikeToday, 1e-9)
    }
}
