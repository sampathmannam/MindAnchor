package org.mindanchor.grayscale

/**
 * The colour-correction settings as Android holds them.
 *
 * [mode] is the daltonizer mode: 0 is monochromacy, and the other values
 * are the real colour-vision corrections (deuteranomaly, protanomaly,
 * tritanomaly) that some people depend on to read their phone.
 */
data class ColourState(val enabled: Boolean, val mode: Int)

/**
 * Decides what to write to the colour-correction settings, and — the part
 * that matters — what to put back.
 *
 * Grayscale borrows the daltonizer, because Android offers no other way to
 * desaturate the display. That borrowing has a victim if done carelessly:
 * someone using colour correction for colour vision deficiency has a mode
 * configured, and turning grayscale on overwrites it. Turning grayscale off
 * again by simply clearing the enabled flag then leaves them with their
 * correction switched off *and* their mode replaced by monochromacy — an
 * accessibility setting quietly destroyed by a wellbeing feature.
 *
 * So the prior state is remembered before the first change and put back
 * afterwards. Kept pure and apart from Android for the same reason
 * [org.mindanchor.admin.SuspensionGuard] is: the cost of getting it wrong
 * falls on somebody who cannot simply look harder at the screen.
 */
object GrayscalePolicy {

    const val MONOCHROMACY = 0

    /**
     * What to record before turning grayscale on, or null to record
     * nothing.
     *
     * The first record wins. If grayscale gets switched on at 22:00 and on
     * again at 22:05, re-recording would capture our own monochromacy as
     * though it were the person's setting, and the real one would be lost
     * at the second write rather than the first.
     */
    fun stateToRemember(current: ColourState, alreadyRemembered: Boolean): ColourState? = when {
        alreadyRemembered -> null
        // Already fully grey — whether we did that or they did, there is
        // no colour-correction preference underneath left to preserve.
        current.enabled && current.mode == MONOCHROMACY -> null
        else -> current
    }

    /**
     * What to write when turning grayscale off.
     *
     * With nothing remembered, the safe assumption is that colour
     * correction was off, which is the Android default and true for almost
     * everyone. Never leaves the mode sitting on monochromacy with the
     * flag merely cleared, so a later trip to Android's own accessibility
     * settings shows what the person actually chose.
     */
    fun stateToRestore(remembered: ColourState?): ColourState =
        remembered ?: ColourState(enabled = false, mode = MONOCHROMACY)
}
