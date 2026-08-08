package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The breathing protocol is the part of the friction gate a person
 * *feels*, and the only part that has a primary-source RCT behind
 * its mechanism. The 6s/6s symmetric breath the gate used to play
 * had no direct RCT evidence; the physiological sigh (Balban et al.
 * 2023, *Cell Reports Medicine* 4(1):100895) is the head-to-head
 * winner in this dose range. These tests pin the timing and the
 * phase boundaries so a future edit cannot drift the protocol
 * silently back to a 6/6 symmetric breath.
 */
class BreathingProtocolTest {

    @Test
    fun `the cycle fits the 3 to 10 second shippable window`() {
        // docs/research/12 §"Feasibility": a launcher overlay has
        // 3–10s; a 20-minute practice protocol is not shippable.
        // 9s sits squarely in the window. A future edit that pushes
        // the total over 10s is a regression against the constraint
        // the brief was written against.
        val cycle = BreathingProtocol.CYCLE_MILLIS
        assert(cycle in 3_000L..10_000L) { "cycle was $cycle ms" }
    }

    @Test
    fun `the exhale is at least as long as the inhale, by design`() {
        // The slow-exhale component is the parasympathetic-drive
        // lever (Bernardi 2018, J Physiol 596(8):1449–1464; Zhang
        // 2025). A short exhale inverts the mechanism.
        assert(BreathingProtocol.EXHALE_MILLIS >= BreathingProtocol.INHALE_MILLIS) {
            "exhale shorter than inhale"
        }
    }

    @Test
    fun `the sip is shorter than the first inhale, by design`() {
        // The "sip" is a small second inhale on top of the first
        // — alveolar reinflation, not a second full breath. A sip
        // longer than the first inhale is a regression to a
        // two-stage inhale that loses the *sigh* character.
        assert(BreathingProtocol.SIP_MILLIS < BreathingProtocol.INHALE_MILLIS) {
            "sip longer than or equal to inhale"
        }
    }

    @Test
    fun `phase boundaries are exact`() {
        // The phase drives the wording ("Breathe in" / "…and in
        // again" / "…and out") and the haptic. The boundary is
        // exclusive on the left, so the *exact* transition
        // millisecond reads as the new phase.
        assertEquals(
            BreathingProtocol.Phase.INHALE,
            BreathingProtocol.phaseAt(0L),
        )
        assertEquals(
            BreathingProtocol.Phase.INHALE,
            BreathingProtocol.phaseAt(BreathingProtocol.INHALE_MILLIS - 1),
        )
        assertEquals(
            BreathingProtocol.Phase.SIP,
            BreathingProtocol.phaseAt(BreathingProtocol.INHALE_MILLIS),
        )
        assertEquals(
            BreathingProtocol.Phase.SIP,
            BreathingProtocol.phaseAt(
                BreathingProtocol.INHALE_MILLIS + BreathingProtocol.SIP_MILLIS - 1,
            ),
        )
        assertEquals(
            BreathingProtocol.Phase.EXHALE,
            BreathingProtocol.phaseAt(
                BreathingProtocol.INHALE_MILLIS + BreathingProtocol.SIP_MILLIS,
            ),
        )
        assertEquals(
            BreathingProtocol.Phase.EXHALE,
            BreathingProtocol.phaseAt(BreathingProtocol.CYCLE_MILLIS - 1),
        )
    }

    @Test
    fun `the cycle covers every phase with no gap and no overlap`() {
        // A cycle where INHALE + SIP + EXHALE != CYCLE would mean
        // the gate either idles or skips, both of which are
        // regressions against the existing finite-breath invariant
        // (the comment on BreathingPause that an infinite transition
        // here burned frames and left the UI non-idle).
        val sum = BreathingProtocol.INHALE_MILLIS +
            BreathingProtocol.SIP_MILLIS +
            BreathingProtocol.EXHALE_MILLIS
        assertEquals(BreathingProtocol.CYCLE_MILLIS, sum)
    }
}
