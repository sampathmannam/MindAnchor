package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentStoreTest {

    private fun moment(valence: Int, arousal: Int, atMinuteOfDay: Int, day: String) =
        Moment(valence = valence, arousal = arousal, atMinuteOfDay = atMinuteOfDay, day = day)

    @Test
    fun `moments survive a round trip through storage`() {
        val original = listOf(
            moment(1, 5, 480, "2026-08-01"),
            moment(5, 1, 1200, "2026-08-02"),
            moment(3, 3, 0, "2026-08-03"),
        )
        assertEquals(original, MomentLedger.decode(MomentLedger.encode(original)))
    }

    @Test
    fun `an empty store decodes to nothing rather than a phantom check-in`() {
        assertTrue(MomentLedger.decode("").isEmpty())
        assertEquals("", MomentLedger.encode(emptyList()))
    }

    @Test
    fun `a corrupt line costs a day's label, not the home screen`() {
        val raw = listOf(
            "3\t3\t600\t2026-08-01", // good
            "garbage",
            "9\t3\t600\t2026-08-02", // valence out of range
            "3\t3\t9999\t2026-08-03", // minute out of range
            "3\t3\t600\t", // blank day
        ).joinToString("\n")
        val out = MomentLedger.decode(raw)
        assertEquals(1, out.size)
        assertEquals("2026-08-01", out[0].day)
    }

    @Test
    fun `a trailing newline does not create a phantom record`() {
        val out = MomentLedger.decode("3\t3\t600\t2026-08-01\n")
        assertEquals(1, out.size)
    }

    @Test
    fun `scale values outside Moment's own range are rejected`() {
        assertTrue(MomentLedger.decode("0\t3\t600\t2026-08-01").isEmpty())
        assertTrue(MomentLedger.decode("6\t3\t600\t2026-08-01").isEmpty())
        assertTrue(MomentLedger.decode("3\t0\t600\t2026-08-01").isEmpty())
        assertTrue(MomentLedger.decode("3\t6\t600\t2026-08-01").isEmpty())
    }

    @Test
    fun `a minute of day outside a real day is rejected`() {
        assertTrue(MomentLedger.decode("3\t3\t-1\t2026-08-01").isEmpty())
        assertTrue(MomentLedger.decode("3\t3\t1440\t2026-08-01").isEmpty())
        // Boundaries are legal: 0 is the first minute, 1439 the last.
        assertEquals(1, MomentLedger.decode("3\t3\t0\t2026-08-01").size)
        assertEquals(1, MomentLedger.decode("3\t3\t1439\t2026-08-01").size)
    }

    @Test
    fun `non-numeric fields are skipped rather than throwing`() {
        assertTrue(MomentLedger.decode("nope\t3\t600\t2026-08-01").isEmpty())
        assertTrue(MomentLedger.decode("3\tnope\t600\t2026-08-01").isEmpty())
        assertTrue(MomentLedger.decode("3\t3\tnope\t2026-08-01").isEmpty())
    }

    @Test
    fun `appending grows the list rather than replacing it`() {
        val first = listOf(moment(3, 3, 480, "2026-08-01"))
        val second = MomentLedger.decode(MomentLedger.encode(first)) + moment(2, 4, 900, "2026-08-02")
        val stored = MomentLedger.encode(second)
        assertEquals(2, MomentLedger.decode(stored).size)
        assertEquals(first[0], MomentLedger.decode(stored)[0])
    }
}
