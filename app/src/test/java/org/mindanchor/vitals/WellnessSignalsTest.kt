package org.mindanchor.vitals

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure N-of-1 math the wellness surface is built on.
 *
 * Every test here is a check on the shape of a number, not on the
 * shape of an Android class — the rule for this part of the
 * launcher is that the math must be wrong loud and early in a JVM
 * test rather than wrong quiet and late in a person's data.
 */
class WellnessSignalsTest {

    private val day = LocalDate.of(2024, 1, 1)

    // --- median ---

    @Test
    fun `median of an empty list is null`() {
        assertNull(WellnessStats.median(emptyList()))
    }

    @Test
    fun `median of a single value is that value`() {
        assertEquals(42.0, WellnessStats.median(listOf(42.0))!!, 0.0001)
    }

    @Test
    fun `median of an odd-length list is the middle value`() {
        assertEquals(3.0, WellnessStats.median(listOf(1.0, 3.0, 5.0))!!, 0.0001)
        assertEquals(30.0, WellnessStats.median(listOf(10.0, 20.0, 30.0, 40.0, 50.0))!!, 0.0001)
    }

    @Test
    fun `median of an even-length list is the mean of the two middle values`() {
        assertEquals(2.0, WellnessStats.median(listOf(1.0, 3.0))!!, 0.0001)
        assertEquals(25.0, WellnessStats.median(listOf(10.0, 20.0, 30.0, 40.0))!!, 0.0001)
    }

    @Test
    fun `median is robust to a single huge outlier`() {
        // The reason for median over mean. A 14-hour sleep day
        // in a sea of 7-hour nights would move the mean to 8 but
        // the median stays at 7 — the "usual" night is the
        // usual night, not the night's neighbour's bad one.
        val values = listOf(7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 14.0)
        assertEquals(7.0, WellnessStats.median(values)!!, 0.0001)
    }

    // --- MAD ---

    @Test
    fun `mad of an empty list is null`() {
        assertNull(WellnessStats.mad(emptyList(), centre = 0.0))
    }

    @Test
    fun `mad of identical values is zero`() {
        // A perfectly repeated week: every day identical, no
        // variation, MAD is zero. This is the case the z-score
        // must refuse rather than divide by zero.
        assertEquals(0.0, WellnessStats.mad(listOf(7.0, 7.0, 7.0, 7.0), centre = 7.0)!!, 0.0001)
    }

    @Test
    fun `mad is robust to a single huge outlier`() {
        // The reason for MAD over SD. The outlier's deviation
        // is large but the median of the deviations is bounded
        // by the typical deviation, not by the outlier's.
        val values = listOf(7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 14.0)
        val centre = 7.0
        // Deviations: 0, 0, 0, 0, 0, 0, 0, 7 — median of those is 0.
        assertEquals(0.0, WellnessStats.mad(values, centre)!!, 0.0001)
    }

    @Test
    fun `mad of a symmetric distribution is the typical deviation`() {
        // A small distribution: values 0, 2, 4. Median is 2.
        // Deviations: 2, 0, 2 — median of those is 2.
        val values = listOf(0.0, 2.0, 4.0)
        assertEquals(2.0, WellnessStats.mad(values, centre = 2.0)!!, 0.0001)
    }

    // --- baseline ---

    @Test
    fun `baseline is not reportable below the minimum day count`() {
        val values = List(WellnessSignal.MIN_HISTORY_DAYS - 1) { 50.0 + it }
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, values)
        assertFalse(baseline.isReportable)
    }

    @Test
    fun `baseline is reportable at the minimum day count`() {
        val values = List(WellnessSignal.MIN_HISTORY_DAYS) { 50.0 + it }
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, values)
        assertTrue(baseline.isReportable)
        assertNotNull(baseline.median)
        assertNotNull(baseline.mad)
        assertEquals(WellnessSignal.MIN_HISTORY_DAYS, baseline.sampleCount)
    }

    @Test
    fun `baseline records the sample count even when not reportable`() {
        val baseline = WellnessStats.baseline(WellnessSignal.STEPS, listOf(1000.0))
        assertEquals(1, baseline.sampleCount)
        assertFalse(baseline.isReportable)
    }

    // --- robust z-score ---

    @Test
    fun `z-score is null when the baseline is not reportable`() {
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, listOf(40.0))
        assertNull(baseline.robustZ(50.0))
    }

    @Test
    fun `z-score is null when the MAD is zero`() {
        // A perfectly repeated week has no spread — the z-score
        // would divide by zero, so it returns null instead.
        // The display shows NO_DATA, not "infinitely above your
        // usual", which is the honest reading.
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, List(20) { 50.0 })
        assertNull(baseline.robustZ(75.0))
    }

    @Test
    fun `z-score is zero for a value at the median`() {
        val values = listOf(40.0, 42.0, 45.0, 48.0, 50.0, 52.0, 55.0, 58.0, 60.0, 62.0)
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, values)
        assertEquals(0.0, baseline.robustZ(baseline.median!!)!!, 0.0001)
    }

    @Test
    fun `z-score is positive for a value above the median`() {
        // Symmetric values: median 50, MAD is computed over
        // |value - 50|. For 40, 45, 50, 55, 60, deviations are
        // 10, 5, 0, 5, 10 — median of those is 5. The
        // z-score for 60 is 0.6745 * (60 - 50) / 5 = 1.349.
        val values = listOf(40.0, 45.0, 50.0, 55.0, 60.0)
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, values)
        val expected = 0.6745 * (60.0 - 50.0) / 5.0
        assertEquals(expected, baseline.robustZ(60.0)!!, 0.0001)
    }

    @Test
    fun `z-score is negative for a value below the median`() {
        val values = listOf(40.0, 45.0, 50.0, 55.0, 60.0)
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, values)
        val expected = 0.6745 * (40.0 - 50.0) / 5.0
        assertEquals(expected, baseline.robustZ(40.0)!!, 0.0001)
    }

    // --- direction bands ---

    @Test
    fun `no data is reported when there is no value today`() {
        assertEquals(
            WellnessDirection.NO_DATA,
            WellnessDirection.bandFor(zScore = 1.0, hasToday = false),
        )
    }

    @Test
    fun `no data is reported when the z-score is null`() {
        assertEquals(
            WellnessDirection.NO_DATA,
            WellnessDirection.bandFor(zScore = null, hasToday = true),
        )
    }

    @Test
    fun `values within one MAD of the median are AT`() {
        assertEquals(WellnessDirection.AT, WellnessDirection.bandFor(0.0, hasToday = true))
        assertEquals(WellnessDirection.AT, WellnessDirection.bandFor(0.5, hasToday = true))
        assertEquals(WellnessDirection.AT, WellnessDirection.bandFor(-0.5, hasToday = true))
        assertEquals(WellnessDirection.AT, WellnessDirection.bandFor(1.0, hasToday = true))
        assertEquals(WellnessDirection.AT, WellnessDirection.bandFor(-1.0, hasToday = true))
    }

    @Test
    fun `values between one and two MADs above the median are ABOVE`() {
        assertEquals(WellnessDirection.ABOVE, WellnessDirection.bandFor(1.01, hasToday = true))
        assertEquals(WellnessDirection.ABOVE, WellnessDirection.bandFor(2.0, hasToday = true))
    }

    @Test
    fun `values more than two MADs above the median are MUCH_ABOVE`() {
        assertEquals(WellnessDirection.MUCH_ABOVE, WellnessDirection.bandFor(2.01, hasToday = true))
        assertEquals(WellnessDirection.MUCH_ABOVE, WellnessDirection.bandFor(5.0, hasToday = true))
    }

    @Test
    fun `values more than one MAD below the median are BELOW`() {
        // v0.58.0: the v0.21.0 design had a
        // single BELOW band (asymmetric: 1
        // BELOW band, 3 ABOVE bands). The
        // v0.58.0 pass adds a MUCH_BELOW
        // band so the "way more than just a
        // little below my usual" case is
        // visually distinct. -1.01 is still
        // BELOW; -2.01 is now MUCH_BELOW.
        assertEquals(WellnessDirection.BELOW, WellnessDirection.bandFor(-1.01, hasToday = true))
    }

    @Test
    fun `values more than two MADs below the median are MUCH_BELOW`() {
        // v0.58.0: the new MUCH_BELOW band.
        // See [WellnessDirection] KDoc for
        // the design rationale.
        assertEquals(WellnessDirection.MUCH_BELOW, WellnessDirection.bandFor(-2.01, hasToday = true))
        assertEquals(WellnessDirection.MUCH_BELOW, WellnessDirection.bandFor(-5.0, hasToday = true))
    }

    // --- end-to-end reading ---

    @Test
    fun `reading carries the signal, today, baseline, and z-score`() {
        val values = listOf(40.0, 45.0, 50.0, 55.0, 60.0)
        val baseline = WellnessStats.baseline(WellnessSignal.SLEEP_MINUTES, values)
        val reading = WellnessStats.reading(WellnessSignal.SLEEP_MINUTES, today = 70.0, baseline)
        assertEquals(WellnessSignal.SLEEP_MINUTES, reading.signal)
        assertEquals(70.0, reading.today!!, 0.0001)
        assertNotNull(reading.zScore)
        assertTrue(reading.zScore!! > 0.0)
        assertEquals(WellnessDirection.MUCH_ABOVE, reading.direction)
    }

    @Test
    fun `reading with no value today reads as no data`() {
        val values = listOf(40.0, 45.0, 50.0, 55.0, 60.0)
        val baseline = WellnessStats.baseline(WellnessSignal.HRV, values)
        val reading = WellnessStats.reading(WellnessSignal.HRV, today = null, baseline)
        assertNull(reading.today)
        assertNull(reading.zScore)
        assertEquals(WellnessDirection.NO_DATA, reading.direction)
    }

    // --- ledger codec ---

    @Test
    fun `ledger round-trips a single entry`() {
        val entries = listOf(WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0))
        val decoded = WellnessLedger.decode(WellnessLedger.encode(entries))
        assertEquals(entries, decoded)
    }

    @Test
    fun `ledger round-trips multiple signals and days`() {
        val entries = listOf(
            WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0),
            WellnessLedger.Entry(WellnessSignal.STEPS, day, 8000.0),
            WellnessLedger.Entry(WellnessSignal.HRV, day.plusDays(1), 50.0),
        )
        assertEquals(entries, WellnessLedger.decode(WellnessLedger.encode(entries)))
    }

    @Test
    fun `ledger drops lines with an unknown signal name`() {
        val entries = listOf(
            WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0),
            WellnessLedger.Entry(WellnessSignal.STEPS, day, 8000.0),
        )
        val raw = WellnessLedger.encode(entries) + "\nFUTURE_SIGNAL\t2024-01-01\t1.0"
        val decoded = WellnessLedger.decode(raw)
        assertEquals(entries, decoded)
    }

    @Test
    fun `ledger drops lines with a bad date`() {
        val entries = listOf(WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0))
        val raw = WellnessLedger.encode(entries) + "\nHRV\tnot-a-date\t1.0"
        val decoded = WellnessLedger.decode(raw)
        assertEquals(entries, decoded)
    }

    @Test
    fun `ledger drops lines with a non-numeric value`() {
        val entries = listOf(WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0))
        val raw = WellnessLedger.encode(entries) + "\nHRV\t2024-01-01\tnot-a-number"
        val decoded = WellnessLedger.decode(raw)
        assertEquals(entries, decoded)
    }

    @Test
    fun `ledger upsert replaces the entry for the same signal and day`() {
        val first = WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0)
        val second = WellnessLedger.Entry(WellnessSignal.HRV, day, 50.0)
        val after = WellnessLedger.upsert(listOf(first), second)
        assertEquals(1, after.size)
        assertEquals(50.0, after.single().value, 0.0001)
    }

    @Test
    fun `ledger upsert keeps entries for other days intact`() {
        val day1 = WellnessLedger.Entry(WellnessSignal.HRV, day, 42.0)
        val day2 = WellnessLedger.Entry(WellnessSignal.HRV, day.plusDays(1), 50.0)
        val day1Retake = WellnessLedger.Entry(WellnessSignal.HRV, day, 60.0)
        val after = WellnessLedger.upsert(listOf(day1, day2), day1Retake)
        assertEquals(2, after.size)
        // day2's value is preserved
        assertEquals(50.0, after.first { it.day == day2.day }.value, 0.0001)
        // day1's value is replaced
        assertEquals(60.0, after.first { it.day == day }.value, 0.0001)
    }

    @Test
    fun `ledger prune keeps only entries on or after the cutoff`() {
        val entries = listOf(
            WellnessLedger.Entry(WellnessSignal.HRV, day, 1.0),
            WellnessLedger.Entry(WellnessSignal.HRV, day.plusDays(10), 2.0),
            WellnessLedger.Entry(WellnessSignal.HRV, day.plusDays(20), 3.0),
        )
        val pruned = WellnessLedger.prune(entries, keepFrom = day.plusDays(10))
        assertEquals(2, pruned.size)
        assertTrue(pruned.all { it.day >= day.plusDays(10) })
    }
}
