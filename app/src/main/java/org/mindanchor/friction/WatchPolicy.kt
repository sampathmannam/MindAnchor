package org.mindanchor.friction

/**
 * Permission to be in an app without being asked again.
 *
 * Granted when somebody passes the gate, and purely time-based: a timed
 * session lasts its own length, an untimed one lasts
 * [WatchPolicy.UNTIMED_ALLOWANCE_MILLIS].
 *
 * Deliberately not "until they leave the app". Detecting a leave means
 * treating every brief hop — a share sheet, a photo picker, the camera
 * opening for one shot — as an exit, and then charging a fresh breathing
 * pause to come back. That teaches people to dread the tool, which is the
 * one failure mode [FrictionTone] exists to avoid.
 */
data class Allowance(val packageName: String, val until: Long)

/**
 * v0.70+ (Phase 1 T-1.5) — the morning-protection
 * snapshot passed into [WatchPolicy.shouldGate].
 *
 * The morning protection is a separate, time-bounded
 * concern from the friction flag list. The decision
 * ("is the window active right now?") is computed
 * once at the call site (in [AppWatchService] or a
 * test) and pinned to the [active] Boolean. The
 * [packages] is the doomscroll list minus
 * [WatchPolicy.NEVER_GATE]; the gate is a no-op
 * for the dialer, settings, etc. anyway.
 *
 * Holding the decision in a snapshot rather than
 * re-evaluating it inside `shouldGate` keeps the
 * function pure: the time component is
 * `AppWatchService`'s concern, not the policy's.
 */
data class MorningProtectionContext(
    val active: Boolean,
    val packages: Set<String>,
)

/**
 * Decides whether an app arriving in the foreground should be met with a
 * pause.
 *
 * Until now friction only existed inside the launcher: it ran when someone
 * tapped an app on the home screen and never otherwise. Opening the same
 * app from a notification, from recents, from a link in a message, or from
 * another app's share sheet went straight through. That is most of how
 * phones are actually used, and a pause that only covers the deliberate
 * route mostly misses the compulsive one.
 *
 * Pure and tested apart from Android, like
 * [org.mindanchor.admin.SuspensionGuard], because the way this fails a
 * person is not "annoying" — it is standing between them and something
 * urgent.
 */
object WatchPolicy {

    /**
     * Thirty minutes of not being asked again, for someone who chose to
     * open an app without a timer. Long enough that ordinary use is not
     * interrupted; short enough that coming back an hour later is treated
     * as the new decision it is.
     */
    const val UNTIMED_ALLOWANCE_MILLIS = 30 * 60 * 1000L

    /**
     * Never gated, whatever anybody flags.
     *
     * Reaching another person must never require getting through a
     * breathing exercise first, and neither must reaching the screen where
     * this app can be switched off. Someone can flag their dialer for a
     * pause from the launcher if they really want to; they cannot make the
     * phone itself argue with them at the moment they are dialling.
     */
    val NEVER_GATE = setOf(
        "com.android.dialer",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.emergency",
        "com.android.settings",
        "com.android.systemui",
    )

    /**
     * True when [opened] arriving in the foreground should be paused.
     *
     * [launcher] is the current home app, passed in rather than looked up
     * so this stays pure; a null launcher simply protects one fewer thing.
     *
     * [morningProtection] is the v0.70+ (Phase 1 T-1.5) morning window
     * snapshot. When non-null and `active`, packages in
     * [MorningProtectionContext.packages] are gated even if they are not
     * in [flagged]. A null [morningProtection] is the historical
     * "no morning protection" shape; the function keeps its
     * pre-morning-protection semantics for callers that have not been
     * updated.
     */
    fun shouldGate(
        opened: String,
        flagged: Set<String>,
        self: String,
        launcher: String?,
        allowance: Allowance?,
        now: Long,
        morningProtection: MorningProtectionContext? = null,
    ): Boolean {
        // The negative-gate predicates. Any of these is a
        // "leave it alone, do not pause"; they are written
        // as one OR to keep the early-return count small.
        val notGateable = opened.isBlank() ||
            opened == self ||
            opened == launcher ||
            opened in NEVER_GATE ||
            (allowance != null &&
                allowance.packageName == opened &&
                now < allowance.until)
        if (notGateable) return false
        // The positive-gate predicates. The friction flag
        // list is the historical surface; morning protection
        // is the v0.70+ opt-in add-on for the first-unlock
        // window. Either one is enough.
        val flagHit = opened in flagged
        val morningHit = morningProtection != null &&
            morningProtection.active &&
            opened in morningProtection.packages
        return flagHit || morningHit
    }

    /**
     * The allowance earned by passing the gate. [minutes] null means the
     * person chose to open it untimed.
     */
    fun allowanceFor(packageName: String, minutes: Long?, now: Long): Allowance =
        Allowance(
            packageName = packageName,
            until = now + (minutes?.times(60_000L) ?: UNTIMED_ALLOWANCE_MILLIS),
        )
}
