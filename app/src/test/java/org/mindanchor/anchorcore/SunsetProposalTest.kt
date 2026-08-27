package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SunsetProposalTest {

    private val steadyClustered = AnchorState.Steady(
        facts = listOf(DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))),
        weekFlagged = true,
        computedAtEpochMillis = 0L,
    )

    @Test
    fun `shows only when enabled steady clustered and not suppressed`() {
        val d = SunsetProposal.decide(true, steadyClustered, suppressedUntil = null, nowMillis = 1000L)
        assertEquals(SunsetProposal.Reason.SHOW, d.reason)
        assertEquals(true, d.show)
    }

    @Test
    fun `disabled never shows`() {
        assertEquals(
            SunsetProposal.Reason.DISABLED,
            SunsetProposal.decide(false, steadyClustered, null, 1000L).reason,
        )
    }

    @Test
    fun `warming never shows`() {
        assertEquals(
            SunsetProposal.Reason.WARMING,
            SunsetProposal.decide(true, AnchorState.WarmingUp(9), null, 1000L).reason,
        )
    }

    @Test
    fun `no cluster no card even on a flagged week`() {
        val flaggedNoCluster = AnchorState.Steady(
            facts = listOf(DayFact(FactKind.MOVEMENT_LOW, "-2.4", LocalDate.of(2026, 8, 26))),
            weekFlagged = true,
            computedAtEpochMillis = 0L,
        )
        assertEquals(
            SunsetProposal.Reason.NO_CLUSTER,
            SunsetProposal.decide(true, flaggedNoCluster, null, 1000L).reason,
        )
    }

    @Test
    fun `suppressed hides until the window passes`() {
        assertEquals(
            SunsetProposal.Reason.SUPPRESSED,
            SunsetProposal.decide(true, steadyClustered, Instant.ofEpochMilli(2000L), 1000L).reason,
        )
        assertEquals(
            SunsetProposal.Reason.SHOW,
            SunsetProposal.decide(true, steadyClustered, Instant.ofEpochMilli(500L), 1000L).reason,
        )
    }
}
