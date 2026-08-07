package org.mindanchor.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tests for artifact rejection and the time-domain metrics.
 *
 * The arithmetic is checked against values worked out by hand rather than
 * against whatever the code happens to produce, because the whole point of
 * this file is that a wrong HRV number is worse than none.
 */
class HrvTest {

    /** A steady series, long enough to clear the sixty-second floor. */
    private fun steadySeries(): List<Double> {
        val pattern = listOf(850.0, 848.0, 852.0, 849.0, 851.0)
        return List(90) { pattern[it % pattern.size] }
    }

    // --- Hand-worked arithmetic ------------------------------------------

    @Test
    fun `rmssd and sdnn match values worked out by hand`() {
        val rr = listOf(800.0, 850.0, 820.0, 830.0, 810.0)
        val m = Hrv.timeDomain(rr)!!

        // mean = 4110 / 5 = 822
        assertEquals(822.0, m.meanRrMillis, 1e-9)
        assertEquals(60_000.0 / 822.0, m.meanHeartRate, 1e-9)

        // Deviations -22, +28, -2, +8, -12; squares 484 + 784 + 4 + 64 + 144
        // = 1480; divided by n-1 = 4 gives 370.
        assertEquals(sqrt(370.0), m.sdnnMillis, 1e-9)

        // Successive differences +50, -30, +10, -20; squares 2500 + 900 +
        // 100 + 400 = 3900; divided by the 4 differences gives 975.
        assertEquals(sqrt(975.0), m.rmssdMillis, 1e-9)

        assertEquals(5, m.intervalCount)
        assertEquals(4, m.successivePairCount)
    }

    @Test
    fun `pnn50 counts only differences strictly greater than fifty`() {
        // The largest difference here is exactly 50 ms, which the Task Force
        // definition does not count.
        val onTheLine = listOf(800.0, 850.0, 820.0, 830.0, 810.0)
        assertEquals(0.0, Hrv.timeDomain(onTheLine)!!.pnn50Percent, 1e-9)

        // Widen that first step to 60 ms and exactly one of the four
        // differences now counts.
        val over = listOf(800.0, 860.0, 820.0, 830.0, 810.0)
        val m = Hrv.timeDomain(over)!!
        assertEquals(25.0, m.pnn50Percent, 1e-9)
        // Differences +60, -40, +10, -20: 3600 + 1600 + 100 + 400 = 5700, / 4.
        assertEquals(sqrt(1425.0), m.rmssdMillis, 1e-9)
    }

    @Test
    fun `a series shorter than two intervals has no metrics`() {
        assertNull(Hrv.timeDomain(listOf(800.0)))
        assertNull(Hrv.timeDomain(emptyList()))
    }

    // --- Artifact rejection ----------------------------------------------

    @Test
    fun `ectopic missed and spurious beats are all rejected`() {
        val dirty = steadySeries().toMutableList()
        dirty[20] = 470.0    // premature ectopic beat
        dirty[21] = 1230.0   // the compensatory pause after it
        dirty[45] = 1700.0   // a missed beat: two intervals merged into one
        dirty[60] = 300.0    // a spurious extra detection
        dirty[61] = 1400.0   // and its partner

        val cleaned = Hrv.clean(dirty)
        val removed = dirty.indices.filterNot { it in cleaned.acceptedIndices.toSet() }

        // Exactly the injected beats, and nothing else. Removing more would
        // mean an artifact had cascaded into its healthy neighbours.
        assertEquals(listOf(20, 21, 45, 60, 61), removed)
        assertEquals(5, cleaned.rejectedCount)
        assertEquals(85, cleaned.acceptedMillis.size)
    }

    @Test
    fun `cleaning restores the variability the artifacts destroyed`() {
        val clean = steadySeries()
        val dirty = clean.toMutableList()
        dirty[20] = 470.0
        dirty[21] = 1230.0
        dirty[45] = 1700.0
        dirty[60] = 300.0
        dirty[61] = 1400.0

        val truth = Hrv.analyse(clean).metrics!!
        val recovered = Hrv.analyse(dirty).metrics!!
        val naive = Hrv.timeDomain(dirty)!!

        // Five bad beats in ninety - under six percent of the recording -
        // inflate naive RMSSD by a factor of eighty.
        assertTrue(
            "naive RMSSD was ${naive.rmssdMillis}, truth ${truth.rmssdMillis}",
            naive.rmssdMillis > 50.0 * truth.rmssdMillis,
        )
        // After rejection it lands back on the true value.
        assertEquals(truth.rmssdMillis, recovered.rmssdMillis, 0.1)
        assertEquals(truth.sdnnMillis, recovered.sdnnMillis, 0.1)
        assertEquals(truth.meanRrMillis, recovered.meanRrMillis, 1.0)
    }

    @Test
    fun `successive differences never straddle a rejected beat`() {
        // Beat 2 was rejected, so intervals 1 and 3 are not neighbours. The
        // step between them spans the artifact that was just removed; using
        // it would undo the rejection entirely.
        val indices = listOf(0, 1, 3, 4)
        val values = listOf(800.0, 810.0, 1000.0, 1010.0)
        val m = Hrv.timeDomain(indices, values)!!

        assertEquals("only genuinely adjacent pairs count", 2, m.successivePairCount)
        assertEquals(sqrt((100.0 + 100.0) / 2.0), m.rmssdMillis, 1e-9)

        // What it would have been had the gap been ignored.
        val ignoringTheGap = Hrv.timeDomain(values)!!
        assertEquals(3, ignoringTheGap.successivePairCount)
        assertTrue(ignoringTheGap.rmssdMillis > 100.0)
    }

    @Test
    fun `genuine high variability is not mistaken for artifact`() {
        // A strong respiratory swing: a fit person at rest. Every successive
        // step stays under fifteen percent, so nothing should be rejected -
        // otherwise the rule would be shaving off the signal it is meant to
        // be protecting.
        val rr = List(90) { 1000.0 + 100.0 * sin(it * 1.5) }
        val result = Hrv.analyse(rr)

        assertEquals(0, result.cleaned.rejectedCount)
        assertNull(result.refusal)
        assertEquals(96.4, result.metrics!!.rmssdMillis, 0.5)
    }

    @Test
    fun `the local median alone misses bigeminy and the combined rule does not`() {
        // Every other beat premature. Three of any five neighbours share the
        // value under test, so the local median always agrees with it.
        //
        // The series is long enough that what survives still clears the beat
        // count and the sixty-second floor, so the refusal below is the
        // rejection-fraction gate firing and not one of the earlier ones.
        val alternating = List(180) { if (it % 2 == 0) 700.0 else 1000.0 }

        val medianOnly = Hrv.clean(alternating, ArtifactRule.KARLSSON_MEDIAN)
        assertEquals("the median filter is blind to this", 0, medianOnly.rejectedCount)

        val combined = Hrv.clean(alternating, ArtifactRule.COMBINED)
        assertEquals(90, combined.rejectedCount)
        assertEquals(90, combined.acceptedMillis.size)
        assertTrue("the survivors alone would have passed the other gates",
            combined.acceptedDurationMillis > Hrv.MIN_CLEAN_DURATION_MILLIS)

        // Having rejected half the series, the result is refused rather than
        // reported from what survived.
        val result = Hrv.analyse(alternating)
        assertNull(result.metrics)
        assertEquals("more than 20% of beats rejected", result.refusal)
    }

    @Test
    fun `an interval outside human range is always rejected`() {
        val rr = steadySeries().toMutableList()
        rr[10] = 2500.0   // 24 bpm
        rr[30] = 150.0    // 400 bpm
        val cleaned = Hrv.clean(rr)
        assertTrue(10 !in cleaned.acceptedIndices)
        assertTrue(30 !in cleaned.acceptedIndices)
    }

    // --- Refusal ---------------------------------------------------------

    @Test
    fun `too few clean beats is refused`() {
        val result = Hrv.analyse(List(49) { 850.0 })
        assertNull(result.metrics)
        assertEquals("fewer than 50 clean beats", result.refusal)
    }

    @Test
    fun `enough beats but under a minute of them is refused`() {
        // Fifty intervals of 850 ms is only 42.5 seconds. The count passes
        // and the duration does not; both have to hold.
        val result = Hrv.analyse(List(50) { 850.0 })
        assertNull(result.metrics)
        assertEquals("under 60 s of clean beats", result.refusal)
    }

    @Test
    fun `just over a minute of clean beats is accepted`() {
        // Seventy intervals is 59.5 s and refused; seventy-one is 60.4 s.
        assertNull(Hrv.analyse(List(70) { 850.0 }).metrics)
        val accepted = Hrv.analyse(List(71) { 850.0 })
        assertNotNull(accepted.metrics)
        assertNull(accepted.refusal)
        assertEquals(850.0, accepted.metrics!!.meanRrMillis, 1e-9)
    }

    @Test
    fun `a series needing heavy correction is refused rather than reported`() {
        // Every fourth beat is wrong: too much of the result would be an
        // artifact of the cleaning rather than a measurement.
        val rr = List(100) { if (it % 4 == 0) 400.0 else 900.0 }
        val result = Hrv.analyse(rr)
        assertNull(result.metrics)
        assertEquals("more than 20% of beats rejected", result.refusal)
        assertTrue(result.cleaned.rejectedFraction > Hrv.MAX_REJECTED_FRACTION)
    }

    @Test
    fun `an empty series is refused without throwing`() {
        val result = Hrv.analyse(emptyList())
        assertNull(result.metrics)
        assertNotNull(result.refusal)
        assertEquals(0, result.cleaned.totalCount)
        assertEquals(0.0, result.cleaned.rejectedFraction, 1e-12)
    }

    @Test
    fun `the number of rejected beats is always reported`() {
        val dirty = steadySeries().toMutableList()
        dirty[20] = 470.0
        dirty[21] = 1230.0
        val result = Hrv.analyse(dirty)
        assertEquals(2, result.cleaned.rejectedCount)
        assertEquals(90, result.cleaned.totalCount)
        assertEquals(2.0 / 90.0, result.cleaned.rejectedFraction, 1e-12)
        assertNotNull(result.metrics)
    }

    @Test
    fun `malik anchors on the last accepted beat so one artifact cannot cascade`() {
        // A single long interval in an otherwise steady series. Anchoring on
        // the previous *observed* value would reject the healthy beat after
        // it too, because it differs from the artifact.
        val rr = steadySeries().toMutableList()
        rr[40] = 1300.0
        val cleaned = Hrv.clean(rr, ArtifactRule.MALIK)
        val removed = rr.indices.filterNot { it in cleaned.acceptedIndices.toSet() }
        assertEquals(listOf(40), removed)
    }
}
