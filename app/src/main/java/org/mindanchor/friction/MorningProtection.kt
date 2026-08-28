@file:Suppress("MatchingDeclarationName", "ReturnCount", "MagicNumber")

package org.mindanchor.friction

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Phase 1 T-1.5 — "morning protection" toggle.
 *
 * The brief: feeds are gated for a user-set N minutes after first
 * unlock of the day. The doomscroll apps a person has identified
 * (the existing [org.mindanchor.prehome.DoomscrollList] set) are
 * met with the friction gate for the first N minutes after the
 * phone is unlocked for the first time in the morning, regardless
 * of whether they were added to the friction list. The window
 * resets the next day.
 *
 * ## Design choices
 *
 *  - **N is a minutes count, not an absolute clock time.** The
 *    user picks "10" and gets 10 minutes from first unlock.
 *    Picking a clock time would force a "before 9 am" or "until
 *    sunrise" choice that is wrong for shift work; minutes from
 *    first unlock is the brief's framing.
 *
 *  - **"First unlock" is the first ACTION_USER_PRESENT of the
 *    local calendar day.** Subsequent unlocks do not extend the
 *    window. A person who unlocks at 7:02, locks, unlocks again
 *    at 7:08, still has until 7:12 of protection.
 *
 *  - **The window is the same N minutes whether N is 5 or 60.**
 *    The brief is explicit that this is about giving the morning
 *    a runway, not about blanket blocking. N=0 means off; the
 *    upper bound (60) is the most the data on habit-formation
 *    literature justifies.
 *
 *  - **Pure data + pure function.** The state is one record; the
 *    window test is one function with no Android dependency. The
 *    receiver and the prefs sit outside this file, so the
 *    computation is testable the same way [WatchPolicy] is.
 *
 * ## Why this is not a [GoingLightSchedule]
 *
 * GoingLight is a *daily* window, scoped to the schedule the
 * user picked. Morning protection is a *first-unlock-of-the-day*
 * window, scoped to "minutes since the phone came alive". They
 * share the `lastFirstUnlockEpochMillis` concept only by
 * accident; the policy is different.
 */
data class MorningProtectionState(
    val enabled: Boolean = false,
    val minutes: Int = 0,
    val lastFirstUnlockEpochMillis: Long = 0L,
) {
    init {
        require(minutes in 0..MAX_MINUTES) {
            "minutes must be in 0..$MAX_MINUTES, was $minutes"
        }
    }

    companion object {
        /**
         * The upper bound on N. 60 minutes is the most the
         * habit-formation literature justifies (Lally 2010,
         * median 66 days to automaticity, range 18-254 days).
         * Going beyond 60 minutes converts the protection
         * into blanket morning blocking, which the brief
         * explicitly rejects.
         */
        const val MAX_MINUTES = 60

        /**
         * The "morning" window in local clock time. Outside
         * this window, the first-unlock check is moot — a
         * 10-minute protection that fires at 23:00 would be
         * a surprise. The window is 04:00–12:00 local,
         * which covers an early-shift 04:30 start, a
         * 09:00 office start, and a 11:30 late riser. The
         * window is hard-coded because making it a setting
         * is a Settings-screen-emptier, not a feature
         * anyone has asked for.
         */
        val MORNING_WINDOW: Pair<LocalTime, LocalTime> = LocalTime.of(4, 0) to LocalTime.of(12, 0)
    }
}

/**
 * True when [now] is inside the morning-protection window for
 * the user with [state].
 *
 * The test is a four-clause AND:
 *
 *  1. [MorningProtectionState.enabled] is true (the toggle is on).
 *  2. [MorningProtectionState.minutes] is at least 1 (an N of 0
 *     is the same as off; checking it twice is defensive).
 *  3. The current local clock time is in the morning window
 *     (04:00–12:00 local). Outside the morning window, the
 *     first-unlock check is moot — a 10-minute protection that
 *     fires at 23:00 would be a surprise.
 *  4. The current instant is within N minutes of the recorded
 *     first-unlock timestamp of *today* (not yesterday's). A
 *     timestamp from yesterday is not a "first unlock today".
 *
 * The function is pure: it reads [now] and the [state] only,
 * and produces a Boolean. The wall-clock comparison is in the
 * supplied [zone] (default: system default), so tests can pin
 * the local clock.
 */
fun isInMorningWindow(
    now: Instant,
    state: MorningProtectionState,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (!isEligibleForWindow(state)) return false
    if (!isInLocalMorningWindow(now, state, zone)) return false
    if (!isFirstUnlockToday(now, state, zone)) return false
    return isWithinMinutesOfFirstUnlock(now, state)
}

/**
 * Guard: the toggle must be on and the minute count must be
 * at least 1. The minute check is defensive — the prefs
 * store is clamped at write time, but a hand-edited file
 * could land a negative in here.
 */
private fun isEligibleForWindow(state: MorningProtectionState): Boolean =
    state.enabled && state.minutes > 0 && state.lastFirstUnlockEpochMillis != 0L

/**
 * The local-clock part: 04:00–12:00 in the supplied zone.
 * Outside this window, a 10-minute protection that fires
 * at 23:00 would be a surprise.
 */
private fun isInLocalMorningWindow(
    now: Instant,
    @Suppress("UNUSED_PARAMETER") state: MorningProtectionState,
    zone: ZoneId,
): Boolean {
    val (morningStart, morningEnd) = MorningProtectionState.MORNING_WINDOW
    val localTime = now.atZone(zone).toLocalTime()
    return !localTime.isBefore(morningStart) && localTime.isBefore(morningEnd)
}

/**
 * Yesterday's first unlock is not a "first unlock today".
 * The receiver sets a fresh value on the first
 * ACTION_USER_PRESENT of the local day; if it has not
 * fired yet today, the window is closed.
 */
private fun isFirstUnlockToday(
    now: Instant,
    state: MorningProtectionState,
    zone: ZoneId,
): Boolean {
    val today = now.atZone(zone).toLocalDate()
    val lastUnlockLocal = Instant.ofEpochMilli(state.lastFirstUnlockEpochMillis)
        .atZone(zone)
        .toLocalDate()
    return lastUnlockLocal == today
}

/**
 * The minutes-of-first-unlock test. The window is
 * `[firstUnlock, firstUnlock + N min]`, so zero elapsed
 * is in (the gate fires at the moment of unlock) and
 * `N + 1` minutes is out. A clock-skewed first-unlock
 * timestamp in the future opens a window the user did
 * not ask for; the elapsed check excludes negatives.
 */
private fun isWithinMinutesOfFirstUnlock(
    now: Instant,
    state: MorningProtectionState,
): Boolean {
    val windowMillis = state.minutes * TimeUnit.MINUTES.toMillis(1)
    val elapsedMillis = now.toEpochMilli() - state.lastFirstUnlockEpochMillis
    return elapsedMillis in 0..windowMillis
}

/**
 * The packages the morning protection forces through the gate,
 * derived from the user's doomscroll list. The set is
 * intersected with [WatchPolicy.NEVER_GATE] removed — dialler,
 * settings, systemui, etc. — so the morning protection cannot
 * stand between the user and a phone call.
 */
fun morningProtectionGatedPackages(
    doomscroll: Set<String>,
): Set<String> = doomscroll - WatchPolicy.NEVER_GATE
