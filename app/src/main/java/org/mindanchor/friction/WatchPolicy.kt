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
     */
    fun shouldGate(
        opened: String,
        flagged: Set<String>,
        self: String,
        launcher: String?,
        allowance: Allowance?,
        now: Long,
    ): Boolean {
        if (opened.isBlank()) return false
        if (opened == self) return false
        if (opened == launcher) return false
        if (opened in NEVER_GATE) return false
        if (opened !in flagged) return false
        if (allowance != null && allowance.packageName == opened && now < allowance.until) {
            return false
        }
        return true
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
