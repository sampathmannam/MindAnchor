package org.mindanchor.model

/**
 * The check-in rate-limit holder. v0.20.1 round 5
 * follow-up (after the brief review).
 *
 * ## Why a holder
 *
 * The [CheckInEngine] is a pure-function object.
 * Its state lives in [CheckInRateLimit] and the
 * on-disk [CheckInState]. The rate-limit, however,
 * is the *gate* on whether a check-in should fire
 * at all, and the gate must persist across
 * BroadcastReceiver events within the same
 * process. The trigger fires on
 * `ACTION_USER_PRESENT` (every phone unlock), and
 * the activity runs in the same process as the
 * receiver; without a process-scoped holder, every
 * unlock would create a fresh
 * [CheckInRateLimit] and the daily cap + auto-
 * pause would never trigger.
 *
 * ## Why an in-memory holder (not a DataStore)
 *
 * The brief is explicit: "the launcher prefers a
 * missed check-in over a permanent 'user said no
 * 47 times' record." The rate-limit is
 * intentionally transient — an app restart resets
 * it. The holder is a Kotlin `object` (singleton
 * in the process); on process death the rate-limit
 * is gone, and the next check-in starts fresh.
 *
 * This is the *deliberate* trade-off documented in
 * brief §B6: the 3-consecutive-rejection auto-
 * pause protects the user from over-prompting
 * *within a session*, not across sessions. A user
 * who keeps rejecting today and reboots tomorrow
 * gets a clean slate tomorrow.
 *
 * ## Thread safety
 *
 * The holder is read and written by both
 * [CheckInActivity] (UI thread) and
 * [CheckInTrigger] (IO thread). The `var` is
 * `Volatile`-style — `@Volatile` annotation
 * guarantees the read/write is visible across
 * threads. The state transitions
 * ([CheckInEngine.recordAcceptance] etc.) are
 * pure functions that produce a new [CheckInRateLimit]
 * via `.copy(...)`; the read-modify-write is not
 * atomic, but the engine is *eventually consistent*
 * — a lost update is a missed check-in, not a
 * wrong check-in.
 */
object CheckInRateLimitHolder {
    /**
     * The current rate-limit state. The trigger
     * reads this to decide whether to fire; the
     * activity updates it on accept/reject. Volatile
     * so a write on the IO thread is visible to a
     * read on the UI thread without needing
     * explicit synchronisation.
     */
    @Volatile
    var state: CheckInRateLimit = CheckInRateLimit()
}
