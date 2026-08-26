package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SriWeekLedgerTest {

    private val d0: LocalDate = LocalDate.of(2026, 8, 1)

    @Test
    fun `first run seeds the current slot and reports no last week`() {
        val r = SriWeekLedger.roll(prev = null, cur = null, today = d0, liveScore = 70)
        assertEquals(SriWeekLedger.Slot(d0, 70), r.cur)
        assertNull(r.prev)
        assertNull(r.lastWeekSri)
    }

    @Test
    fun `inside the week the anchor date holds and the score refreshes`() {
        val r = SriWeekLedger.roll(null, SriWeekLedger.Slot(d0, 70), d0.plusDays(3), liveScore = 66)
        assertEquals(SriWeekLedger.Slot(d0, 66), r.cur)
        assertNull(r.lastWeekSri)
    }

    @Test
    fun `after seven days the slot rolls and last week appears`() {
        val r = SriWeekLedger.roll(null, SriWeekLedger.Slot(d0, 68), d0.plusDays(7), liveScore = 60)
        assertEquals(SriWeekLedger.Slot(d0, 68), r.prev)
        assertEquals(SriWeekLedger.Slot(d0.plusDays(7), 60), r.cur)
        assertEquals(68, r.lastWeekSri)
    }

    @Test
    fun `a prev slot older than thirteen days is stale not last week`() {
        val r = SriWeekLedger.roll(
            SriWeekLedger.Slot(d0, 68),
            SriWeekLedger.Slot(d0.plusDays(30), 60),
            d0.plusDays(30),
            liveScore = 60,
        )
        assertNull(r.lastWeekSri)
    }

    @Test
    fun `a null live score changes nothing`() {
        val cur = SriWeekLedger.Slot(d0, 70)
        val r = SriWeekLedger.roll(null, cur, d0.plusDays(9), liveScore = null)
        assertEquals(cur, r.cur)
        assertNull(r.prev)
    }
}
