package org.mindanchor.note

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * v0.44.0: a process-singleton signal that carries
 * the most recent reminder-fire event from
 * [ReminderReceiver] to [HomeActivity].
 *
 * The receiver runs as a broadcast — it may fire
 * while HomeActivity is foreground, background, or
 * not running at all. The receiver always posts a
 * notification (so the user gets the reminder even
 * if the app is in the background) AND sets this
 * signal so the foreground HomeActivity can show a
 * full-screen flash.
 *
 * ## Why a singleton flow, not a local broadcast
 *
 * A local broadcast (`LocalBroadcastManager`) is
 * gone in the modern AndroidX world, and a
 * `registerReceiver` on the activity has to be
 * re-registered on every `onResume`. A singleton
 * [StateFlow] is the simplest, most testable shape:
 * the activity collects in [androidx.compose.runtime.Composable]
 * and the receiver writes to it. The flow is
 * process-wide so a receiver in a different
 * process-singleton context can still fire it.
 *
 * ## Why a single-slot signal
 *
 * The launcher does not queue flashes — a reminder
 * fires, the user sees the flash, the next reminder
 * is a fresh signal. A [StateFlow] with a single
 * nullable event is the right shape: writing
 * `null` after a short delay is the "consumed" path.
 *
 * The [eventId] is the note's `id`; the activity
 * uses it to look up the note body in DataStore and
 * surface the body on the flash surface. The id
 * is the join key; the body comes from disk, not
 * from the receiver intent, so the receiver does
 * not need to ship a 4 KB body across the broadcast
 * boundary.
 */
object FlashSignal {

    /**
     * The active flash event. `null` means "no
     * flash". Writing a new event with a different
     * id replaces the current one; writing the
     * same id is a no-op. The activity is
     * expected to call [consume] once the flash
     * has been shown.
     */
    data class FlashEvent(
        val eventId: Long,
        val triggeredAt: Long = System.currentTimeMillis(),
    )

    private val _event = MutableStateFlow<FlashEvent?>(null)
    val event: StateFlow<FlashEvent?> = _event

    /**
     * Fire a new flash for the given note id. If
     * the same id is already active, the call is a
     * no-op (the user already has the flash
     * visible; the receiver is a duplicate, e.g.
     * the alarm fired twice because the user
     * rebooted).
     */
    fun fire(noteId: Long) {
        val current = _event.value
        if (current != null && current.eventId == noteId) return
        _event.value = FlashEvent(noteId)
    }

    /**
     * Mark the current flash as consumed. The
     * activity calls this from a "dismiss" affordance
     * or after a 5-second auto-clear. The next
     * [fire] with a different id still works.
     */
    fun consume() {
        _event.value = null
    }
}
