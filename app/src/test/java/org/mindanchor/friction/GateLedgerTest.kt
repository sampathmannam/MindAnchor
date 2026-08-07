package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GateLedgerTest {

    private val start = LocalDate.of(2026, 1, 1)
    private val threeWeeksOn: LocalDate = start.plusDays(21)

    private fun tally(shown: Int, abandoned: Int, since: LocalDate? = start) =
        GateTally(shown = shown, abandoned = abandoned, sinceDay = since?.toString())

    @Test
    fun `a pause nobody ever backs out of, over real volume and real time, is worth mentioning`() {
        assertTrue(GateLedger.worthMentioning(tally(40, 0), threeWeeksOn))
    }

    @Test
    fun `a pause people do back out of is left alone`() {
        // Grüning found 36% abandonment. This one is working.
        assertFalse(GateLedger.worthMentioning(tally(40, 14), threeWeeksOn))
    }

    @Test
    fun `one mis-tap in three weeks does not hide a ceremony`() {
        assertTrue(GateLedger.worthMentioning(tally(40, 1), threeWeeksOn))
    }

    @Test
    fun `too few appearances says nothing, however lopsided`() {
        assertFalse(GateLedger.worthMentioning(tally(19, 0), threeWeeksOn))
        assertTrue(GateLedger.worthMentioning(tally(20, 0), threeWeeksOn))
    }

    @Test
    fun `volume without elapsed time is one bad evening, not a habit`() {
        // Forty gates in a week is exactly the burst the tone system
        // already softens for. Reaching a verdict on it would be reaching
        // a verdict on somebody's worst night.
        assertFalse(GateLedger.worthMentioning(tally(40, 0), start.plusDays(13)))
        assertTrue(GateLedger.worthMentioning(tally(40, 0), start.plusDays(14)))
    }

    @Test
    fun `a tally that never started counting reaches no verdict`() {
        assertFalse(GateLedger.worthMentioning(tally(40, 0, since = null), threeWeeksOn))
    }

    @Test
    fun `an unparseable stored date is treated as no verdict rather than crashing`() {
        val corrupt = GateTally(shown = 40, abandoned = 0, sinceDay = "not-a-date")
        assertFalse(GateLedger.worthMentioning(corrupt, threeWeeksOn))
    }

    @Test
    fun `an empty tally has no rate at all`() {
        assertNull(GateLedger.abandonRate(GateTally()))
        assertFalse(GateLedger.worthMentioning(GateTally(), threeWeeksOn))
    }

    @Test
    fun `the clock starts on the first appearance and does not move after`() {
        val first = GateLedger.recordShown(GateTally(), start)
        assertEquals(start.toString(), first.sinceDay)
        val later = GateLedger.recordShown(first, start.plusDays(5))
        assertEquals(start.toString(), later.sinceDay)
        assertEquals(2, later.shown)
    }

    @Test
    fun `backing out counts as both an appearance and an abandonment`() {
        // It was shown, and it worked. Counting it only as an abandonment
        // would make the rate exceed 1 and the check meaningless.
        val out = GateLedger.recordAbandoned(GateTally(), start)
        assertEquals(1, out.shown)
        assertEquals(1, out.abandoned)
        assertEquals(1.0, GateLedger.abandonRate(out)!!, 0.0001)
    }

    @Test
    fun `tallies survive a round trip through storage`() {
        val original = mapOf(
            "com.example.social" to GateTally(40, 1, start.toString()),
            "com.example.video" to GateTally(3, 2, null),
        )
        assertEquals(original, GateLedger.decode(GateLedger.encode(original)))
    }

    @Test
    fun `a corrupt line costs a statistic, not the home screen`() {
        val raw = "com.good\t5\t1\t2026-01-01\ngarbage\ncom.bad\tnope\t1\t2026-01-01\n\t9\t9\tx"
        val out = GateLedger.decode(raw)
        assertEquals(setOf("com.good"), out.keys)
        assertEquals(5, out.getValue("com.good").shown)
    }

    @Test
    fun `an empty store decodes to nothing rather than to a phantom app`() {
        assertTrue(GateLedger.decode("").isEmpty())
        assertEquals("", GateLedger.encode(emptyMap()))
    }

    @Test
    fun `keeping a pause restarts the count so the question is not asked again tomorrow`() {
        val noisy = tally(40, 0)
        assertTrue(GateLedger.worthMentioning(noisy, threeWeeksOn))
        val kept = GateLedger.reset(threeWeeksOn)
        assertFalse(GateLedger.worthMentioning(kept, threeWeeksOn))
        // And it can come back around, if it really is still ceremony.
        var later = kept
        repeat(GateLedger.MIN_SHOWN) { later = GateLedger.recordShown(later, threeWeeksOn) }
        assertFalse(GateLedger.worthMentioning(later, threeWeeksOn.plusDays(13)))
        assertTrue(GateLedger.worthMentioning(later, threeWeeksOn.plusDays(14)))
    }
}
