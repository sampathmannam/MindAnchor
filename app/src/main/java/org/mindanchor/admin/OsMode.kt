package org.mindanchor.admin

import android.content.Context
import kotlinx.coroutines.flow.first
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.prehome.DoomscrollList
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The states the guided OS Mode surface can be in. Pure, so the mapping
 * is unit-testable without Android.
 */
sealed interface OsModeState {
    /** No device-owner grant: the switch cannot exist yet. */
    data object NotProvisioned : OsModeState

    /** Grant present, switch off: arming is one tap away. */
    data object Available : OsModeState

    /** Grant present, switch on: feed apps close through the window. */
    data object Armed : OsModeState
}

/**
 * OS Mode — the launcher acting as the phone's policy layer
 * (master plan T-1.1/T-1.2).
 *
 * A device-owner-provisioned MindAnchor may suspend packages system-wide,
 * which survives every entry point launcher-level gating cannot reach:
 * notifications, links, share sheets. [DeviceOwner] holds the raw power;
 * this object is the posture around it — opt-in, window-bounded, and
 * reversible from at least three places.
 *
 * ## The one rule: derive, never persist
 *
 * Whether apps are suspended right now is **never** stored. [sync]
 * recomputes it on every call from things that already exist:
 *
 * 1. the device-owner grant (read live),
 * 2. the OS Mode switch ([OsModePrefs]),
 * 3. the sunset window ([SunsetPrefs]) — same single window definition
 *    the rest of the app uses,
 * 4. whether the typed-dwell unlock already fired inside *this* window.
 *
 * A crash between suspend and unsuspend therefore heals itself: the next
 * sync — boot, alarm, or settings change — puts reality back in step with
 * the window, whichever side it drifted to.
 *
 * ## Layering with the existing quiet-hours suspension
 *
 * `SunsetController.handleAlarm` already suspends `FrictionPrefs.flaggedApps`
 * through quiet hours; that behaviour is untouched and remains gated only
 * on sunset + ownership. OS Mode adds a **second** layer over the person's
 * own doomscroll list ([DoomscrollList]), gated additionally on the explicit
 * switch. Turning OS Mode on mid-window applies immediately; turning it off
 * lifts its layer immediately and never touches the flagged-apps layer.
 *
 * ## Escape hatch
 *
 * The 30-second typed dwell on the Sleep Lock card calls [onEarlyUnlock]:
 * the feed layer lifts for the rest of *this* window and stays lifted
 * (a marker, not a re-suspension loop). At the next window start the
 * marker expires by derivation and enforcement returns. Lifting a
 * suspension that was never applied does nothing, so every path here is
 * idempotent.
 */
object OsMode {

    /**
     * Pure decision at the heart of [sync], split out so the truth table
     * is testable without DataStore or DevicePolicyManager.
     */
    fun shouldSuspend(
        osModeEnabled: Boolean,
        sunsetEnabled: Boolean,
        insideWindow: Boolean,
        earlyReleaseActive: Boolean,
    ): Boolean = osModeEnabled && sunsetEnabled && insideWindow && !earlyReleaseActive

    /**
     * The apps OS Mode has closed right now, ready to read.
     *
     * The applied set is written by every sync and, until this existed,
     * read by nothing: the person could be told the feature was armed but
     * never which apps it had actually shut. Pure, so the ordering and the
     * uninstalled-app case are testable without a PackageManager.
     *
     * Sorted case-insensitively because a Set has no order worth relying
     * on, and a list that reshuffles between openings reads as churn
     * rather than as status. A package that no longer resolves keeps its
     * raw name rather than disappearing -- dropping it would claim the
     * launcher had closed one thing fewer than it did.
     */
    fun suspendedNow(applied: Set<String>, label: (String) -> String): List<String> =
        applied.filter { it.isNotBlank() }
            .map(label)
            .sortedBy { it.lowercase() }

    /**
     * The moment the current window opened, given where we are now.
     *
     * Needed because the early-release marker is keyed to "this window":
     * an unlock at 23:30 must suppress tonight's enforcement but must not
     * suppress tomorrow's. Same-day and midnight-crossing windows both
     * handled; pure so boundaries are unit-testable.
     */
    internal fun mostRecentWindowStart(now: LocalDateTime, start: LocalTime): LocalDateTime {
        val todayStart = now.toLocalDate().atTime(start)
        return if (!now.toLocalTime().isBefore(start)) todayStart else todayStart.minusDays(1)
    }

    fun stateFor(isDeviceOwner: Boolean, isEnabled: Boolean): OsModeState =
        when {
            !isDeviceOwner -> OsModeState.NotProvisioned
            isEnabled -> OsModeState.Armed
            else -> OsModeState.Available
        }

    /** Whether OS Mode's feed layer is switched on and the grant exists. */
    suspend fun isActive(context: Context): Boolean =
        runCatching {
            val prefs = OsModePrefs(context)
            prefs.isEnabled() && DeviceOwner.isDeviceOwner(context)
        }.getOrDefault(false)

    /**
     * Re-derives suspension from the window. Never throws: callers include
     * broadcast receivers with seconds to live.
     *
     * Runs on every sunset alarm, on boot (via `Alarms.ensureAll`), and
     * whenever the switch flips — START alarms apply, END alarms lift, and
     * either action heals drift. It deliberately runs even outside the
     * window, because turning something off must also be able to lift what
     * an earlier window closed.
     */
    suspend fun sync(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        runCatching { syncInternal(context.applicationContext, now) }
    }

    private suspend fun syncInternal(context: Context, now: LocalDateTime) {
        if (!DeviceOwner.isDeviceOwner(context)) return

        val prefs = OsModePrefs(context)
        val sunset = SunsetPrefs(context)
        val doomscroll = DoomscrollList(context).packages.first()
        val (start, end) = sunset.window()

        // The early-release marker lives exactly as long as its window:
        // anything recorded before the most recent window start is stale.
        val windowStartMillis = mostRecentWindowStart(now, start)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val earlyReleaseAt = prefs.earlyReleaseAt.first()

        val insideWindow = SunsetPrefs.isInWindow(now.toLocalTime(), start, end)
        val suspendNow = shouldSuspend(
            osModeEnabled = prefs.isEnabled(),
            sunsetEnabled = sunset.isEnabled(),
            insideWindow = insideWindow,
            earlyReleaseActive = earlyReleaseAt != null && earlyReleaseAt >= windowStartMillis,
        )

        if (suspendNow) {
            val alwaysOpen = FrictionPrefs(context).alwaysOpen.first()
            val actuallySuspended = DeviceOwner.apply(context, doomscroll, alwaysOpen)
            prefs.recordApplied(actuallySuspended.toSet())
        } else {
            // Lift everything this feature could have closed — the last
            // applied hint plus the whole current list, because entries
            // removed from the list since still deserve their freedom.
            // Lifting a suspension that never happened does nothing;
            // missing one strands an app.
            DeviceOwner.clear(context, prefs.applied.first() + doomscroll)
            prefs.recordApplied(emptySet())
        }

        // Only wipe the marker once its window has closed. Wiping it
        // mid-window would let the very next sync re-close apps the
        // person had just deliberately opened.
        if (!insideWindow) prefs.clearEarlyRelease()
    }

    /**
     * The typed-dwell unlock: lift the feed layer now and mark the window
     * as released, so later syncs inside the same window do not undo the
     * person's choice. Never throws, idempotent when nothing is suspended.
     */
    suspend fun onEarlyUnlock(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            if (!DeviceOwner.isDeviceOwner(appContext)) return@runCatching
            val prefs = OsModePrefs(appContext)
            prefs.markEarlyRelease()
            val doomscroll = DoomscrollList(appContext).packages.first()
            DeviceOwner.clear(appContext, prefs.applied.first() + doomscroll)
            prefs.recordApplied(emptySet())
        }
    }
}
