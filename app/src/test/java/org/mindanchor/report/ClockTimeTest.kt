package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.model.Baseline
import org.mindanchor.sleep.Deviation

/**
 * The sleep-onset frame, both directions.
 *
 * Sleep onset is the one signal in [Signal] that is a clock reading rather
 * than a quantity, and clock readings wrap. Everything here exists because
 * that wrap, left alone, breaks the baseline and then prints a bedtime
 * nobody had.
 */
class ClockTimeTest {

    private fun onset(hour: Int, minute: Int) = Deviation.minutesAfterSixPm(hour * 60 + minute)

    @Test
    fun `the clock survives a round trip through the eighteen-hundred frame`() {
        for (minuteOfDay in 0 until 1440) {
            val framed = Deviation.minutesAfterSixPm(minuteOfDay)
            val expected = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
            assertEquals(expected, clockTime(framed.toDouble()))
        }
    }

    @Test
    fun `twenty to midnight and ten past it are twenty minutes apart, not most of a day`() {
        // The whole reason for the frame. As raw minutes-of-day these two
        // bedtimes are 1430 and 10 — a 1420-minute gap in a series whose
        // real spread is twenty minutes.
        val before = onset(23, 50)
        val after = onset(0, 10)
        assertEquals(20, kotlin.math.abs(after - before))
    }

    @Test
    fun `a genuinely late night still reads as unusual against midnight-straddling nights`() {
        // Fourteen ordinary nights scattered either side of midnight, then
        // one at half past three. On raw minutes-of-day the dispersion of
        // the ordinary nights is so wide that nothing could ever clear it;
        // in this frame the late night is visible.
        val ordinary = listOf(
            onset(23, 40), onset(0, 5), onset(23, 55), onset(0, 15), onset(23, 50),
            onset(0, 0), onset(23, 45), onset(0, 20), onset(23, 35), onset(0, 10),
            onset(23, 58), onset(0, 2), onset(23, 48), onset(0, 12),
        ).map { it.toDouble() }
        val late = onset(3, 30).toDouble()

        val z = Baseline.robustZ(late, ordinary)
        assertTrue("a 3:30am night must clear a cluster around midnight", z != null && Baseline.isNotable(z))
    }

    @Test
    fun `an ordinary night says nothing`() {
        val ordinary = listOf(
            onset(23, 40), onset(0, 5), onset(23, 55), onset(0, 15), onset(23, 50),
            onset(0, 0), onset(23, 45), onset(0, 20), onset(23, 35), onset(0, 10),
            onset(23, 58), onset(0, 2), onset(23, 48), onset(0, 12),
        ).map { it.toDouble() }

        val z = Baseline.robustZ(onset(23, 52).toDouble(), ordinary)
        assertTrue("a bedtime inside the usual cluster must render as silence", z == null || !Baseline.isNotable(z))
    }

    @Test
    fun `the report frames sleep onset the way the screen unframes it`() {
        // Guards the pair rather than either half: if one side ever moves
        // to a different origin hour, this fails rather than the person
        // being shown a bedtime six hours off.
        assertEquals("23:50", clockTime(onset(23, 50).toDouble()))
        assertEquals("00:10", clockTime(onset(0, 10).toDouble()))
        assertEquals("18:00", clockTime(onset(18, 0).toDouble()))
        assertEquals("17:59", clockTime(onset(17, 59).toDouble()))
    }
}
