package org.mindanchor.friction

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The "Going Light" v1.1 module — Castelo, Kushlev, Ward,
 * Esterman, Reiner, *PNAS Nexus* 2025;4(2):pgaf017,
 * doi:10.1093/pnasnexus/pgaf017.
 *
 * The brief: a scheduled time window (e.g. 8pm–10pm, or a
 * weekly 24-hour block) during which the launcher and a
 * lightweight Android companion module cut the *mobile-internet*
 * connection (browser, social, YouTube) while leaving SMS,
 * voice, and offline apps untouched. Castelo's RCT (N=467, 2
 * weeks) found sustained-attention gains equivalent to ~10
 * years of age-related decline reversal, depression-symptom
 * reductions *larger than the average effect of pharmaceutical
 * antidepressants*, and gains persisted at 4-week follow-up
 * even after the internet was restored.
 *
 * `docs/research/15` §1 named this as the single biggest
 * open whitespace in the category: no Android app ships
 * scheduled whole-browser-window disconnection. The
 * implementation requires either a `VpnService` (Android
 * permission) or `AccessibilityService` to gate the actual
 * content; that permission-level work is out of scope for
 * one commit. This file ships the *pure-function design
 * layer* — schedule, day-of-week selection, data model —
 * so the implementation of the actual blocking mechanism is
 * one file with a `VpnService` and a `BroadcastReceiver` and
 * a one-line wire into the schedule the data layer holds.
 *
 * ## The two schedule shapes
 *
 * The brief is explicit that the active ingredient is
 * *mobile-internet content*, not communication — a launcher
 * that preserves calls/messaging while gating feeds mimics
 * the trial's mechanism. Two schedule shapes:
 *
 *  - **Daily window** — e.g. 20:00 to 22:00 every day. The
 *    simpler shape; what Castelo's mechanism is. Default
 *    recommendation in `docs/research/15` §1.
 *  - **Weekly block** — e.g. Saturday 06:00 to Sunday 06:00.
 *    The 24-hour block half of the Castelo mechanism. Higher
 *    effect per the trial; higher cost per the same trial
 *    (~25% fully complied).
 *
 * ## Active window
 *
 * [isActive] is the *one* function the implementation will
 * call on every gate event to decide whether the gate should
 * fire. The window is per-day; for the weekly block, the
 * [activeDays] set is the day-of-week set on which the window
 * is *active* (Saturday + Sunday for the 24-hour block
 * case). The [startTime] / [endTime] are the local clock
 * boundaries within each active day.
 *
 * A schedule with no active days is "off"; the launcher
 * behaves as it does today. A schedule with active days
 * but `endTime` equal to or before `startTime` is treated
 * as an *overnight* window (active from startTime on day
 * N until endTime on day N+1) — the same overnight-handling
 * the SunsetPrefs already does for the bedtime hours.
 */
data class GoingLightSchedule(
    val enabled: Boolean = false,
    val activeDays: Set<DayOfWeek> = emptySet(),
    val startTime: LocalTime = LocalTime.of(20, 0),
    val endTime: LocalTime = LocalTime.of(22, 0),
) {
    /**
     * Whether the window is active at the given instant.
     *
     * The same-day case is the simple one: now is between
     * start and end on an active day. The overnight case is
     * the one where the window starts in the evening and ends
     * in the morning of the next day — handled by checking
     * the *previous* day's schedule against the morning hours.
     */
    fun isActiveAt(now: LocalDateTime): Boolean {
        if (!enabled) return false
        val today = now.date
        val tod = now.time
        val todayActive = today.dayOfWeek in activeDays
        val yesterdayActive = today.minusDays(1).dayOfWeek in activeDays
        val sameDay = startTime.isBefore(endTime) &&
            todayActive &&
            !tod.isBefore(startTime) &&
            tod.isBefore(endTime)
        // Overnight: yesterday was active, we are in the
        // window's tail (after midnight, before endTime).
        val overnight = !startTime.isBefore(endTime) &&
            ((todayActive && !tod.isBefore(startTime)) ||
                (yesterdayActive && tod.isBefore(endTime)))
        return sameDay || overnight
    }

    /**
     * The next active transition after [now]. Used by the
     * scheduler to arm the next broadcast.
     *
     * Returns the instant at which the window will next
     * *start* (when transitioning from inactive to active),
     * or null when the schedule is permanently off (no
     * active days). When the window is currently active,
     * returns the next *end* time — the scheduler arms the
     * re-entry to inactive state.
     */
    fun nextTransition(now: LocalDateTime): LocalDateTime? {
        if (!enabled || activeDays.isEmpty()) return null
        if (isActiveAt(now)) {
            // We are inside the window; the next transition
            // is the end of this window.
            return now.date.atTime(endTime)
        }
        // Search forward up to 7 days for the next active start.
        for (offset in 0L..7L) {
            val candidate = now.date.plusDays(offset)
            if (candidate.dayOfWeek in activeDays) {
                val start = candidate.atTime(startTime)
                if (start.isAfter(now)) return start
            }
        }
        return null
    }
}

/**
 * A `LocalDate + LocalTime` so the file does not have to
 * import `java.time.LocalDateTime` everywhere. The
 * implementation will hold a `Clock` and read `LocalDateTime.now()`
 * at every gate event.
 */
data class LocalDateTime(val date: LocalDate, val time: LocalTime)
