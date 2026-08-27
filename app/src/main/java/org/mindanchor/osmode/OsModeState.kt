package org.mindanchor.osmode

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The three postures OS Mode can be in, derived — never stored.
 *
 * The device-owner grant is the system's fact; the opt-in switch is the
 * person's. Neither is authoritative alone:
 *
 * - [NOT_PROVISIONED] — no grant, so nothing to opt into. A stray
 *   opt-in flag from a previous life of this phone is ignored rather
 *   than surfaced: the state machine reads the grant as the truth about
 *   what is possible and lets the Settings screen offer the choice.
 * - [AVAILABLE] — the grant exists, the person has not asked for the
 *   posture. This is the resting state for every provisioned phone;
 *   nothing suspends until it is switched on by hand.
 * - [ACTIVE] — grant + opt-in. Only here can the sunset window suspend
 *   anything.
 */
enum class OsModeStatus { NOT_PROVISIONED, AVAILABLE, ACTIVE }

/**
 * What should happen to package suspension right now.
 *
 * [SUSPEND] and [STAND_DOWN] are both commands; the controller acts on
 * each one every time it derives state. That is what makes the design
 * crash-safe: nothing remembers that it used to be suspended, everything
 * re-reads the window and re-decides. A process that died mid-window
 * simply re-derives the same answer on its next start.
 */
enum class SuspensionDecision { SUSPEND, STAND_DOWN }

object OsModeState {

    /**
     * Pure mapping from (grant, opt-in) to posture. Total function: no
     * combination is invalid, so no caller needs a fallback branch.
     */
    fun statusFor(deviceOwnerGranted: Boolean, optedIn: Boolean): OsModeStatus =
        when {
            !deviceOwnerGranted -> OsModeStatus.NOT_PROVISIONED
            optedIn -> OsModeStatus.ACTIVE
            else -> OsModeStatus.AVAILABLE
        }

    /**
     * Whether the chosen packages should be suspended right now.
     *
     * Every condition is load-bearing:
     *
     * - `granted` — without the grant there is nothing to act with.
     * - `optedIn` — the project's standing law: per-feature toggle,
     *   default OFF. Provisioning the phone never implies handing over
     *   the nightly list.
     * - `inWindow` — the quiet hours are the only time this fires, and
     *   they are re-derived from the clock, not from a memory of having
     *   been inside them.
     * - `releasedForThisWindow` — the escape hatch. Completing the slow
     *   typed dwell records "let me through for tonight"; the record is
     *   compared against the current window instance so it expires the
     *   moment the next window opens.
     */
    fun decide(
        granted: Boolean,
        optedIn: Boolean,
        inWindow: Boolean,
        releasedForThisWindow: Boolean,
    ): SuspensionDecision =
        if (granted && optedIn && inWindow && !releasedForThisWindow) {
            SuspensionDecision.SUSPEND
        } else {
            SuspensionDecision.STAND_DOWN
        }

    /**
     * When the currently-running window began, given when it starts each
     * day — the identity of "this" window, used to expire escape-hatch
     * releases.
     *
     * If the clock has passed the start time, today's start; otherwise
     * yesterday's (the window crossed midnight and is still running).
     * For a same-day window such as 09:00 → 17:00 consulted at 23:00
     * the answer (today 09:00) is harmless: nothing consults the release
     * record outside a window anyway.
     *
     * Pure so the expiry rule is testable without a clock or a store.
     */
    fun currentWindowStartedAt(now: LocalDateTime, windowStart: LocalTime): LocalDateTime {
        val todayStart = now.toLocalDate().atTime(windowStart)
        return if (!now.toLocalTime().isBefore(windowStart)) {
            todayStart
        } else {
            todayStart.minusDays(1)
        }
    }

    /** Epoch milliseconds for a window-start instant, for comparing against the release record. */
    fun epochMillisOf(at: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        at.atZone(zone).toInstant().toEpochMilli()
}
