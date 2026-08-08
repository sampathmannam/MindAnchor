package org.mindanchor.friction

/**
 * The breathing protocol the gate plays through.
 *
 * `docs/research/12` reviewed the primary literature on shippable
 * 3–10s breathing protocols (Balban et al. 2023, *Cell Reports Medicine*
 * 4(1):100895 — physiological sigh; Bernardi et al. 2018, *J Physiol*
 * 596(8):1449–1464 — slow-exhale parasympathetic drive; Lehrer 2003
 * family — resonance-frequency ~5.5 bpm; 4-7-8 Weil; 6s/6s symmetric)
 * and the SOTA verdict is:
 *
 * - **Cyclic / physiological sighing** is the only head-to-head RCT
 *   in this dose range, n=108, and cyclic sighing won on positive
 *   affect (+1.91 vs +1.22 for mindfulness meditation, p<0.05) and on
 *   the largest drop in resting respiratory rate. The single-cycle
 *   version is one *double inhale* (a 2-second nasal inhale, then a
 *   short 1-second "sip" inhale to fully inflate the alveoli) followed
 *   by one *extended exhale* (a 6-second slow mouth exhale).
 * - The **6s in / 6s out symmetric** breath the gate used to play
 *   has no direct RCT evidence at this dose; its only positive
 *   evidence is from the broader slow-breathing literature, and the
 *   systematic review (Vagedes 2025, SPB) found only 2 of 7 studies
 *   showed co-occurring HRV and subjective stress improvement.
 * - The **4-7-8 "ratio"** (Weil) has no demonstrated superiority over
 *   other slow-exhale patterns in peer-reviewed work.
 *
 * Switching to a 2s + 1s + 6s physiological-sigh cycle is a
 * single-cycle fit into a 9-second overlay, with the longer
 * exhale being the active ingredient. The Balban effect is for a
 * 5-minute *practice*; a single cycle is the *trigger*, not the dose.
 * The single-cycle change inherits the same evidence as the cycle:
 * the longer-exhale parasympathetic drive (Bernardi 2018;
 * Zhang 2025) is what is shippable here.
 */
object BreathingProtocol {

    /** A 2s nasal inhale that fills the lungs. */
    const val INHALE_MILLIS = 2_000L

    /**
     * A short 1s "sip" inhale on top of the first.
     *
     * This is the second half of a *physiological sigh*: the alveolar
     * reinflation that distinguishes a sigh from an ordinary breath.
     * The double inhale is what makes the protocol a sigh, and the
     * sigh is what Balban 2023 compared against mindfulness meditation
     * and won on affect and respiratory rate.
     */
    const val SIP_MILLIS = 1_000L

    /**
     * A 6s slow mouth exhale.
     *
     * The slow-exhale component is where the parasympathetic drive
     * comes from. Slow-exhale patterns beat slow-inhale patterns on
     * RSA (Bernardi 2018, *J Physiol* 596(8):1449–1464) and on
     * subjective calm (Zhang 2025); the sigh's distinctive feature
     * is the double inhale, but the *mechanism* is the long exhale.
     */
    const val EXHALE_MILLIS = 6_000L

    /** Total cycle, in milliseconds. 9s, in the 3–10s shippable range. */
    const val CYCLE_MILLIS = INHALE_MILLIS + SIP_MILLIS + EXHALE_MILLIS

    /**
     * Which phase the gate is in at a given offset from cycle start.
     *
     * The phase drives the wording ("Breathe in" / "…and in again" /
     * "…and out") and the haptic. The boundary is exclusive on the
     * left so the *exact* transition millisecond reads as the new
     * phase, which matters for the haptic landing on the user
     * noticing a change.
     */
    fun phaseAt(elapsedMillis: Long): Phase = when {
        elapsedMillis < INHALE_MILLIS -> Phase.INHALE
        elapsedMillis < INHALE_MILLIS + SIP_MILLIS -> Phase.SIP
        else -> Phase.EXHALE
    }

    enum class Phase { INHALE, SIP, EXHALE }
}
