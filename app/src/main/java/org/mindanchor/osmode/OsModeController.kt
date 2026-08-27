package org.mindanchor.osmode

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.admin.DeviceOwner
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.prehome.DoomscrollList
import java.time.LocalDateTime

/**
 * OS Mode's runtime: derive, decide, act — in that order, every time.
 *
 * The whole posture is one function: [rederiveSuspend] reads the grant,
 * the opt-in, and the sunset window from their owners (the system, the
 * person, and the clock respectively), asks [OsModeState.decide], and
 * applies the answer. Nothing about suspension is persisted as truth;
 * the stored last-applied set exists only to lift precisely what was
 * applied. A process that dies mid-window re-derives the same answer on
 * its next start, which is why callers fire this from boot, from each
 * sunset alarm, and whenever the opt-in flips.
 *
 * The list suspended is the person's own doomscroll list
 * ([DoomscrollList]) — the same list PreHome pauses on — never an
 * inferred set. [SuspensionGuard] inside [DeviceOwner.apply] keeps the
 * dialer, messages and this app reachable whatever the list says, and
 * `alwaysOpen` wins over everything.
 */
object OsModeController {

    /**
     * Re-derives the suspension state from scratch. Suspend because every
     * input is a DataStore or system read; call from a coroutine that
     * outlives the triggering event (`goAsync` in receivers).
     */
    suspend fun rederiveSuspend(context: Context) {
        val appContext = context.applicationContext
        val prefs = OsModePrefs(appContext)
        val sunset = SunsetPrefs(appContext)

        val granted = DeviceOwner.isDeviceOwner(appContext)
        val optedIn = prefs.isOptedIn()
        val enabled = runCatching { sunset.isEnabled() }.getOrDefault(false)
        val inWindow = enabled && runCatching { sunset.isQuietHour() }.getOrDefault(false)

        // "This window" is identified by when it started. A release made
        // during a previous night must not reach into tonight.
        val released = if (inWindow) {
            val start = runCatching { sunset.window() }.getOrNull()?.first ?: return
            val startedAt = OsModeState.currentWindowStartedAt(LocalDateTime.now(), start)
            prefs.releasedAtEpochMs.first() >= OsModeState.epochMillisOf(startedAt)
        } else {
            false
        }

        when (
            OsModeState.decide(
                granted = granted,
                optedIn = optedIn,
                inWindow = inWindow,
                releasedForThisWindow = released,
            )
        ) {
            SuspensionDecision.SUSPEND -> suspendChosen(appContext, prefs)
            SuspensionDecision.STAND_DOWN -> standDown(appContext, prefs)
        }
    }

    private suspend fun suspendChosen(context: Context, prefs: OsModePrefs) {
        val chosen = DoomscrollList(context).packages.first()
        val alwaysOpen = FrictionPrefs(context).alwaysOpen.first()
        val applied = DeviceOwner.apply(context, chosen, alwaysOpen)
        prefs.setLastSuspended(applied.toSet())
    }

    /**
     * Lifts what this feature applied. The hint set is passed so an app
     * removed from the doomscroll list after it was suspended is still
     * released — clearing only the current list would strand it shut.
     */
    private suspend fun standDown(context: Context, prefs: OsModePrefs) {
        val hint = prefs.lastSuspended.first()
        if (hint.isEmpty()) return
        DeviceOwner.clear(context, hint)
        prefs.setLastSuspended(emptySet())
    }

    /**
     * The escape hatch: the person proved intent through the slow typed
     * dwell, so record the release against this window only and lift now.
     * The next window derives fresh and suspends again — a decision made
     * while calm, honoured once, then asked for again.
     */
    fun releaseForCurrentWindowAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            OsModePrefs(appContext).recordReleasedNow(System.currentTimeMillis())
            rederiveSuspend(appContext)
        }
    }

    /** Fire-and-forget variant for UI callers already off the main path. */
    fun rederiveAsync(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            rederiveSuspend(context.applicationContext)
        }
    }
}
