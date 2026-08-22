package org.mindanchor.friction

/**
 * The breathing protocol the gate plays through.
 *
 * The launcher's research index (`docs/research/22-research-index.md`)
 * lists the verified primary literature. The short version:
 *
 * - **Cyclic / physiological sighing** is the only head-to-head RCT
 *   of a 3-10s breathing protocol at this dose. Balban et al. 2023,
 *   *Cell Reports Medicine* 4(1):100895 (DOI 10.1016/j.xcrm.2022.100895),
 *   n=108, 28 days of 5-min daily practice. Cyclic sighing (a
 *   double nasal inhale followed by a long oral exhale) beat
 *   mindfulness meditation on positive affect and on resting
 *   respiratory rate. The single-cycle version is one *double
 *   inhale* (a 2-second nasal inhale, then a short 1-second "sip"
 *   inhale to fully reinflate the alveoli) followed by one
 *   *extended exhale* (a 6-second slow mouth exhale).
 * - The **mechanism** for the long-exhale phase is the slow-breathing
 *   baroreflex effect. Bernardi et al. 2001, *J. Hypertens.*
 *   19(12):2221-2229 (DOI 10.1097/00004872-200112000-00016), showed
 *   that slow breathing at 6 breaths/min depressed chemoreflex
 *   responses and increased baroreflex sensitivity — the closest
 *   primary reference for the parasympathetic-drive claim that
 *   grounds the 6s exhale.
 * - The single-cycle version is a *trigger*, not a dose. The Balban
 *   effect is for a 5-min *practice*; one cycle is what fits inside
 *   a friction gate without becoming a meditation. A launcher that
 *   asks for 5 minutes at every reach would be the wrong product.
 *
 * Out-of-scope for the verified index (kept out of the KDoc until a
 * primary source is found):
 *  - The old "6s in / 6s out symmetric" pattern that this code
 *    replaced: no RCT at this dose; the slow-paced-breathing
 *    literature is mixed (a cited systematic review reportedly
 *    found co-occurring HRV and subjective-stress improvement in
 *    only 2 of 7 studies, but that specific review is unverified
 *    and is not cited here). Replaced rather than kept.
 *  - "Bernardi 2018, *J. Physiol.* 596(8):1449-1464" — searched, no
 *    paper matches. The 2001 *J. Hypertens.* paper is the verified
 *    reference for the same mechanism.
 *  - "Zhang 2025" slow-exhale review — searched, not verified.
 *  - The Weil "4-7-8" pattern: not from peer-reviewed work, and
 *    has no demonstrated superiority over other slow-exhale patterns.
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
     * sigh is what Balban et al. 2023 (Cell Reports Medicine 4(1):100895)
     * compared against mindfulness meditation and won on affect and
     * respiratory rate.
     */
    const val SIP_MILLIS = 1_000L

    /**
     * A 6s slow mouth exhale.
     *
     * The slow-exhale component is where the parasympathetic drive
     * comes from. The primary reference is Bernardi et al. 2001,
     * *J. Hypertens.* 19(12):2221-2229: slow breathing at 6
     * breaths/min depressed chemoreflex response and increased
     * baroreflex sensitivity. The sigh's distinctive feature is the
     * double inhale, but the *mechanism* is the long exhale.
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
