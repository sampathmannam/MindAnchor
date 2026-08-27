package org.mindanchor.anchorcore

import java.time.Instant

/**
 * Whether the quiet one-card proposal may appear: only when the loop is
 * on, steady, carrying a live late-night cluster, and the person has not
 * recently declined it. Never auto-applies — the autonomy law holds.
 */
object SunsetProposal {

    enum class Reason { DISABLED, WARMING, NO_CLUSTER, SUPPRESSED, SHOW }

    data class Decision(val show: Boolean, val reason: Reason)

    val HIDDEN = Decision(false, Reason.DISABLED)

    const val OVERRIDE_DAYS = 7L
    const val EARLIER_BY_MINUTES = 30L

    fun decide(
        enabled: Boolean,
        state: AnchorState,
        suppressedUntil: Instant?,
        nowMillis: Long,
    ): Decision = when {
        !enabled -> Decision(false, Reason.DISABLED)
        state !is AnchorState.Steady -> Decision(false, Reason.WARMING)
        state.lateNightCluster == null -> Decision(false, Reason.NO_CLUSTER)
        suppressedUntil != null && suppressedUntil.toEpochMilli() > nowMillis ->
            Decision(false, Reason.SUPPRESSED)
        else -> Decision(true, Reason.SHOW)
    }
}
