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
 * [CheckInTrigger] (IO thread).
 *
 * v0.20.1 round 5 follow-up: the read-modify-
 * write pattern is now wrapped in a `synchronized`
 * block on the holder. The previous design used
 * only `@Volatile` and the docstring claimed
 * "a lost update is a missed check-in, not a wrong
 * check-in" — that was wrong. `acceptedToday` lost
 * updates are *not* missed check-ins: they are
 * *over-prompts*, a user can receive a 5th check-in
 * on a day that already had 4 (the daily cap is
 * the only thing enforcing the cap, and the cap
 * is in `acceptedToday`).
 *
 * The fix is a monitor on the holder: every
 * read-modify-write goes through [update], which
 * takes the lock, reads the current state, calls
 * the pure-function engine method, and writes back.
 * The `synchronized` block is the price of correct
 * concurrent behaviour on a single JVM object. A
 * `compareAndSet` loop on an `AtomicReference` would
 * also work but would require the engine's pure
 * functions to be re-entrant; the monitor is
 * simpler and is in the brief's spirit of "no
 * cleverness."
 */
object CheckInRateLimitHolder {
    /**
     * The current rate-limit state. Reads and
     * writes happen inside [update] so the
     * read-modify-write is atomic. Direct
     * access is not part of the public API;
     * the activity and the trigger must use
     * [update] for any read-modify-write. The
     * bare [state] field is provided for
     * read-only access from the trigger's
     * "should I fire?" check, which does not
     * mutate.
     *
     * `@Volatile` is kept for the read-side
     * guarantee: a thread that reads [state]
     * outside of [update] sees the most recent
     * write without a happens-before edge. The
     * write-side discipline is the [update]
     * function.
     */
    @Volatile
    private var _state: CheckInRateLimit = CheckInRateLimit()

    /**
     * Read the current state. A pure read; no
     * side effect. Visible across threads via
     * `@Volatile`.
     */
    val state: CheckInRateLimit
        get() = _state

    /**
     * Atomically transform the state. The
     * `transform` lambda is called while
     * holding the monitor on this object; the
     * returned value is the new state. The
     * lambda is expected to be a pure-function
     * call to a [CheckInEngine] method
     * (`recordAcceptance`, `recordRejection`,
     * `reset`).
     *
     * Returns the new state. If the transform
     * throws, the state is not changed.
     */
    fun update(
        transform: (CheckInRateLimit) -> CheckInRateLimit,
    ): CheckInRateLimit = synchronized(this) {
        val next = transform(_state)
        _state = next
        next
    }

    /**
     * Test-only: reset the holder to a fresh
     * state. Production code should never need
     * this; it is here for unit-test isolation.
     * Marked `@VisibleForTesting` so the lint
     * passes; the `internal` visibility on the
     * function prevents accidental production
     * use.
     */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting(
        newState: CheckInRateLimit = CheckInRateLimit(),
    ) {
        synchronized(this) { _state = newState }
    }
}
