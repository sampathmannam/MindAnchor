package org.mindanchor.friction

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The v0.26+ compassionate-wrap event bus.
 *
 * ## What this is
 *
 * The compassionate wrap (Phase 1 G-19) is a 1-tap
 * Snackbar that fires when the user closes a doomscroll
 * app after a long session ("You were on Instagram for
 * 32 minutes — note anything?"). The Snackbar is a
 * validate-then-suggest offer, not a judgment, and the
 * user can always tap "Dismiss" without consequence.
 *
 * The notifier is the bus between the trigger
 * ([org.mindanchor.friction.AppWatchService], which
 * observes foreground-app transitions via the
 * [android.accessibilityservice.AccessibilityService]
 * and the [android.app.usage.UsageStatsManager]) and
 * the renderer (a Compose [androidx.compose.material3.SnackbarHost]
 * in [org.mindanchor.launcher.HomeScreen], which
 * collects the flow and shows the message).
 *
 * ## Why a process-wide singleton and not a per-screen
 *   SharedFlow
 *
 * The trigger fires from a long-running
 * [android.accessibilityservice.AccessibilityService] —
 * the renderer may be in a different process or a
 * different Activity. The single source of truth is a
 * [MutableSharedFlow] held in a process-wide object.
 * The replay is zero, the buffer is the latest event,
 * and the overflow strategy is DROP_OLDEST so a long
 * offline period does not build up a queue of
 * 30-minutes-of-Instagram events for the user to
 * dismiss on reconnect.
 *
 * ## Privacy
 *
 * The events hold the *package name* and the *time
 * spent in the app* — not the app's content. The
 * [AppWatchService] does not read the foreground
 * app's screen or notifications, and the package
 * name resolution to an app label is the only
 * `PackageManager` call (cached per [PackageLabelCache]).
 *
 * v0.26+ (Phase 1 G-19).
 */
object CompassionateWrapNotifier {

    /**
     * One compassionate-wrap event. The label is the
     * human-readable app name; the package is the raw
     * package id (for telemetry / debugging only — the
     * UI never displays the raw package name).
     */
    data class Event(
        val packageName: String,
        val label: String,
        val minutesSpent: Long,
    )

    private val _events: MutableSharedFlow<Event> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Read-only flow for the renderer.
     */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Called by the [AppWatchService] when the user
     * closes a doomscroll app after a long session.
     * The [label] should already be resolved from the
     * package name; the [minutesSpent] is the foreground
     * duration in minutes (rounded down to a whole
     * number; the wrap is a 1-tap offer, not a
     * high-precision timing display).
     */
    suspend fun post(event: Event) {
        _events.emit(event)
    }

    /**
     * Try to resolve [packageName] to a user-friendly
     * label via the [PackageManager]. Falls back to the
     * raw package name when the resolution fails (the
     * user uninstalled the app, the label was changed,
     * etc.) — the wrap fires anyway with the package
     * name as the label, which is honest and avoids a
     * crash on a cold Snackbar path.
     */
    fun labelFor(context: Context, packageName: String): String {
        val pm = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }
            .getOrNull() ?: return packageName
        return runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrDefault(packageName)
    }
}
