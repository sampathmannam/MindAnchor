package org.mindanchor.friction

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.FrictionPrefs

/**
 * Notices when a flagged app comes to the front, however it got there.
 *
 * ## What this fixes
 *
 * Friction used to live entirely inside the launcher. It ran when somebody
 * tapped an app on the home screen, and never otherwise — so opening the
 * same app from a notification, from recents, from a link in a message or
 * from another app's share sheet went straight through untouched. Those
 * routes are most of how a phone is actually used, and they are
 * disproportionately the compulsive ones: nobody deliberately navigates
 * home and searches for an app when a notification already put it one tap
 * away.
 *
 * ## Why an accessibility service and not a polling service
 *
 * The alternative is a foreground service polling `UsageStatsManager`
 * every second or so, which costs battery continuously and plants a
 * permanent notification in the shade. This costs nothing when nothing
 * happens: Android delivers an event only when a window actually changes.
 *
 * ## What it can and cannot see
 *
 * `canRetrieveWindowContent` is **false** in the service configuration,
 * and the event filter is limited to window-state changes. This service is
 * therefore not able to read the screen — not messages, not passwords, not
 * what anybody is looking at. It receives a package name and nothing else.
 * That is the entire capability, it is enforced by the system rather than
 * by our good intentions, and given what enabling an accessibility service
 * normally implies, the settings screen says so plainly before asking.
 *
 * It is entirely optional. Everything else works without it; turning it
 * off leaves the launcher-only behaviour exactly as it was.
 */
class AppWatchService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val flagged = MutableStateFlow<Set<String>>(emptySet())

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        scope.launch {
            FrictionPrefs(applicationContext).flaggedApps.collect { flagged.value = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val opened = event.packageName?.toString() ?: return

        // Window-state changes fire for dialogs, toasts and popups inside an
        // app that is already open. Without this the pause would reappear
        // over somebody mid-use rather than at the moment they arrived.
        if (opened == lastForeground) return

        // v0.26+ (Phase 1 G-19) — compassionate wrap. The user has
        // left the previous foreground app; if it was non-self,
        // non-launcher, and was foreground for 30+ minutes, post
        // the wrap event to the notifier. The notifier hands it
        // to the home-surface Snackbar.
        val previous = lastForeground
        val now = System.currentTimeMillis()
        scope.launch {
            val prefs = FrictionPrefs(applicationContext)
            if (prefs.compassionateWrapEnabled.first() &&
                previous != null &&
                previous != packageName &&
                // v0.26+ fix: defaultLauncher returns
                // String?, so the safe-call form is
                // required. Treating a null launcher as
                // "previous is not the launcher" is the
                // conservative choice — we cannot know it
                // is, so we do not suppress the wrap.
                !(defaultLauncher(this@AppWatchService)?.contains(previous) ?: false)
            ) {
                val startedAt = foregroundStartedAt[previous]
                if (startedAt != null) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(now - startedAt)
                    if (minutes >= WRAP_THRESHOLD_MINUTES) {
                        CompassionateWrapNotifier.post(
                            CompassionateWrapNotifier.Event(
                                packageName = previous,
                                label = CompassionateWrapNotifier.labelFor(applicationContext, previous),
                                minutesSpent = minutes,
                            ),
                        )
                    }
                }
            }
            // Track the new foreground for the next transition.
            if (previous != null) foregroundStartedAt.remove(previous)
            foregroundStartedAt[opened] = now
        }

        lastForeground = opened

        if (!WatchPolicy.shouldGate(
                opened = opened,
                flagged = flagged.value,
                self = packageName,
                launcher = defaultLauncher(this),
                allowance = allowanceFor(opened),
                now = now,
            )
        ) {
            return
        }

        runCatching {
            startActivity(
                // NEW_TASK only. The gate declares its own task affinity, so
                // it lands in a task of its own on top of whatever opened.
                // Clearing a task here would risk tearing down the app the
                // person was heading for.
                Intent(this, GateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(GateActivity.EXTRA_PACKAGE, opened),
            )
        }
    }

    /** Required by the system; nothing here needs to react to it. */
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {

        /**
         * v0.26+ (Phase 1 G-19) — the foreground-time threshold
         * for the compassionate wrap. The 30-minute minimum is
         * the per-person median for "this is not a quick check"
         * — under 30 minutes the user is genuinely checking
         * something (a DM, a calendar entry, a sale email);
         * over 30 minutes the session is a candidate for the
         * "would you like to note anything?" offer. The 30-min
         * floor is the same threshold the bedtime-procrastination
         * literature (Scullin 2018; Mark 2005 23-min
         * interruption-recovery cost) uses for "session has
         * crossed the line".
         */
        private const val WRAP_THRESHOLD_MINUTES = 30L

        /**
         * The package-name to foreground-started-at map for
         * the compassionate-wrap trigger. Cleared on
         * foreground change. Process-lifetime only — a
         * fresh launch has no history and the wrap
         * therefore does not fire on the first session
         * of a fresh install, which is the right
         * behaviour (the user has not yet opted in to
         * the wrap; an opt-in check gates the trigger
         * on the FrictionPrefs read).
         */
        // CodeRabbit review 2026-08-24 (PR #38):
        // the previous `HashMap` with `@Volatile` is
        // not thread-safe — `@Volatile` only makes the
        // reference visible across threads, but the
        // map's put/remove are compound operations and
        // can interleave. Switched to
        // `ConcurrentHashMap`, matching the `allowances`
        // map at line 208. The read-then-write sequence
        // for the 30-min wrap check is still racy
        // across coroutines, so the start time is
        // captured on the event thread before the
        // `scope.launch` below; the launch body uses
        // the captured `now`, not a second read of the
        // map.
        private val foregroundStartedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

        /**
         * Whether the watcher is currently connected.
         *
         * Read by the settings screen so it can show the real state rather
         * than whatever Android's own toggle last reported — the service
         * can be switched off from system settings without this app being
         * told.
         */
        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        private var lastForeground: String? = null

        /**
         * Permission to be in each app without being asked again.
         *
         * One entry per app rather than a single slot, because a single
         * slot loses the allowance for the app you were in the moment you
         * glance at another. Someone on call checks their work messenger,
         * looks at something else for a minute, comes back — and is met
         * with a breathing exercise on the channel their job runs through.
         * The map costs almost nothing and removes that entirely.
         *
         * Held in memory only. If the process is killed the person gets one
         * extra pause, which is the harmless direction for this to fail in
         * — the opposite would be a pause that silently stopped working.
         */
        private val allowances = java.util.concurrent.ConcurrentHashMap<String, Long>()

        private fun allowanceFor(packageName: String): Allowance? =
            allowances[packageName]?.let { Allowance(packageName, it) }

        /** Called when somebody passes the gate, from either entry point. */
        fun allow(packageName: String, minutes: Long?) {
            val now = System.currentTimeMillis()
            // Drop anything already expired, so a phone left on for weeks
            // cannot accumulate an entry per app ever opened.
            allowances.entries.removeAll { it.value <= now }
            allowances[packageName] = WatchPolicy.allowanceFor(packageName, minutes, now).until
            // The launcher hands focus straight to the app it just launched,
            // and that arrival would otherwise read as a fresh reach and be
            // gated a second time.
            lastForeground = packageName
        }

        fun defaultLauncher(context: Context): String? = runCatching {
            context.packageManager
                .resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                )
                ?.activityInfo
                ?.packageName
        }.getOrNull()
    }
}
