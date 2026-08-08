package org.mindanchor.pulse

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cadence of the WHO-5 pulse reminder is the design decision
 * `docs/research/11` reviewed and recommended changing. The previous
 * single constant — `INTERVAL_DAYS = 14L` — was wrong on the ceiling
 * (Lally 2010: median habit-formation 66 days; Wood 2019: the "double
 * law of habit" bakes habituation into a fixed schedule).
 *
 * These tests are the audit log for the replacement: every boundary
 * the brief specifies is pinned here, so a future edit cannot change
 * when the cadence advances or resets without the tests noticing.
 */
class PulseCadenceTest {

    @Test
    fun `no completions yet means the most-frequent end of the taper`() {
        assertEquals(
            PulseCadence.EARLY_DAYS,
            PulseCadence.cadenceDays(
                completedCount = 0,
                recentOutcomes = emptyList(),
                consecutiveMisses = 0,
            ),
        )
    }

    @Test
    fun `first two completions stay at 7 days, building the response habit`() {
        // Lally 2010: habit curve builds across the first ~6 weeks of
        // repetition. A short cadence at the start is what the brief
        // calls for, not a 14-day jump.
        for (count in 1..2) {
            assertEquals(
                "count $count",
                PulseCadence.EARLY_DAYS,
                PulseCadence.cadenceDays(count, listOf(true, true), 0),
            )
        }
    }

    @Test
    fun `three to five completions advance to the 10-day mid cadence`() {
        // The 7 → 10 transition is on the third completed pulse.
        // Below that the new habit has not stabilised; above it the
        // taper begins, per the brief's taper schedule.
        for (count in 3..5) {
            val outcomes = List(count) { true }
            assertEquals(
                "count $count",
                PulseCadence.MID_DAYS,
                PulseCadence.cadenceDays(count, outcomes, 0),
            )
        }
    }

    @Test
    fun `six or more completions with a full window of 4 or 5 completions reach the late cadence`() {
        // The user-specific signal: 4 of last 5 completed. The
        // lifetime count alone is not enough; a recent lapse must
        // hold the cadence at MID. This is the Lally/Gardner 2023
        // open-question-4 point: shape the cadence to the user's
        // *actual* trajectory, not to a calendar.
        val outcomes = listOf(true, true, true, true, true) // 5 of 5
        assertEquals(PulseCadence.LATE_DAYS, PulseCadence.cadenceDays(6, outcomes, 0))
        val outcomes2 = listOf(true, true, true, true, false) // 4 of 5
        assertEquals(PulseCadence.LATE_DAYS, PulseCadence.cadenceDays(6, outcomes2, 0))
    }

    @Test
    fun `six completions with 3 or fewer in the recent window stay at the mid cadence`() {
        // The lifetime count is 6+, but the *recent* run is poor.
        // The brief is explicit: lifetime count alone is not the
        // signal; a held cadence at MID is the right response.
        val outcomes = listOf(true, true, true, false, false) // 3 of 5
        assertEquals(PulseCadence.MID_DAYS, PulseCadence.cadenceDays(6, outcomes, 0))
        val outcomes2 = listOf(true, true, false, false, false) // 2 of 5
        assertEquals(PulseCadence.MID_DAYS, PulseCadence.cadenceDays(6, outcomes2, 0))
    }

    @Test
    fun `a recent run of two misses drops the cadence back to early regardless of count`() {
        // The EMA "missed opportunity recovery" (Lally 2010, "missing
        // one opportunity to perform the behaviour did not
        // materially affect the curve"; two misses compound). The
        // bounce-back is the same rule that protects against a
        // calendar-fixed habituation cycle.
        val outcomes = listOf(true, true, true, true, false, false)
        assertEquals(
            PulseCadence.EARLY_DAYS,
            PulseCadence.cadenceDays(completedCount = 10, recentOutcomes = outcomes, consecutiveMisses = 2),
        )
    }

    @Test
    fun `a single miss does not trigger the bounce-back`() {
        // Lally 2010: a single miss is not material. The bounce-back
        // requires two.
        val outcomes = listOf(true, true, true, true, true, false)
        assertEquals(
            PulseCadence.LATE_DAYS,
            PulseCadence.cadenceDays(completedCount = 10, recentOutcomes = outcomes, consecutiveMisses = 1),
        )
    }

    @Test
    fun `consecutive misses at the end is counted from the rightmost entries only`() {
        // The list is oldest-first, so the run at the end is the
        // most recent. A list like [true, true, true, true, false,
        // false] has a trailing run of two; a list like [true, true,
        // false, true, false] has a trailing run of one.
        assertEquals(
            2,
            PulseCadence.consecutiveMissesAtEnd(listOf(true, true, true, true, false, false)),
        )
        assertEquals(
            1,
            PulseCadence.consecutiveMissesAtEnd(listOf(true, true, false, true, false)),
        )
        assertEquals(
            0,
            PulseCadence.consecutiveMissesAtEnd(listOf(true, true, true, true, true)),
        )
        assertEquals(0, PulseCadence.consecutiveMissesAtEnd(emptyList()))
    }

    @Test
    fun `a fresh install with no recent window falls back to mid cadence at the right count`() {
        // Until the recent window fills (5 entries), the function
        // holds at MID, not LATE. The lifetime count is enough to
        // enter MID; the recent signal is what LATE requires.
        val outcomes = listOf(true, true) // window not yet full
        assertEquals(PulseCadence.MID_DAYS, PulseCadence.cadenceDays(6, outcomes, 0))
    }
}
