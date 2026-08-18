package org.mindanchor.note

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * v0.44.0: schedule and cancel per-note reminder
 * alarms.
 *
 * The launcher uses [AlarmManager.setExactAndAllowWhileIdle]
 * to fire a [ReminderReceiver] at the note's
 * [org.mindanchor.model.Note.reminderAt] time. The
 * alarm is the only thing that wakes the app — the
 * note is saved with `reminderAt` set, the scheduler
 * is told to schedule, and from that point on the
 * OS owns the alarm until the receiver fires.
 *
 * ## Why AlarmManager and not a worker / coroutine
 *
 * The note is a user-authored artifact; the reminder
 * is the user's promise to themselves that "this
 * thought will come back to me at <time>". The
 * launcher cannot be the device owner (the typical
 * user has a stock phone), so the only system-level
 * wake-up primitive is AlarmManager. WorkManager
 * would not fire at a specific wall-clock time, and a
 * foreground coroutine dies the moment the user
 * backgrounds the app. AlarmManager is the right
 * tool.
 *
 * ## Why exact
 *
 * The user's note is a "remind me at 7pm" — they
 * do not want a 15-minute window. `setExactAndAllowWhileIdle`
 * is the only [AlarmManager] method that fires on
 * the dot in Doze mode. The SCHEDULE_EXACT_ALARM
 * permission is not required on Android 13+ when
 * the app does not target SDK 34+, but the launcher
 * declares it anyway (declared in the manifest,
 * granted on install) so the user does not see a
 * "this reminder may be late" dialog.
 *
 * ## The PendingIntent identity
 *
 * The PendingIntent that fires the receiver is
 * keyed on the note's `id`, so scheduling a new
 * reminder for an existing note replaces the
 * old alarm (same identity, updated extras) and
 * scheduling a reminder for a brand-new note
 * creates a fresh alarm. Cancelling uses the
 * same key.
 *
 * ## Failure mode
 *
 * If the AlarmManager call throws
 * (security exception on a restricted device, OOM
 * on an over-allocated phone), the call site in
 * [org.mindanchor.launcher.LauncherViewModel] is
 * expected to log and move on. The note is still
 * saved with `reminderAt` set; the UI will surface
 * "reminder set" but the alarm may not fire. The
 * user can re-trigger by editing and re-saving the
 * note, or by toggling the reminder off and on.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    /**
     * The Intent action the receiver listens for. A
     * custom action (not the default `BOOT_COMPLETED`
     * or `ACTION_SCREEN_ON`) so the manifest filter
     * is tight and the receiver cannot be fired by
     * an unrelated broadcast.
     */
    const val ACTION_FIRE_REMINDER = "org.mindanchor.action.FIRE_REMINDER"

    /**
     * The Intent extra that carries the note id from
     * the alarm to the receiver. The receiver uses
     * it to look up the note body in the DataStore
     * and to forward the id to HomeActivity for the
     * flash.
     */
    const val EXTRA_NOTE_ID = "org.mindanchor.extra.NOTE_ID"

    /**
     * Schedule an alarm at [atMillis] (epoch
     * milliseconds) that fires the receiver for the
     * note with the given [noteId]. Calling this
     * twice with the same [noteId] REPLACES the old
     * alarm — the PendingIntent identity is keyed
     * on the note id, so the new extras overwrite
     * the old ones.
     *
     * If [atMillis] is in the past, the call is
     * silently ignored — the note may have been
     * edited after the reminder was supposed to
     * fire, and the right behaviour is to surface
     * "reminder time passed" on the row rather than
     * firing immediately.
     */
    fun schedule(context: Context, noteId: Long, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "schedule: atMillis is in the past for noteId=$noteId; ignoring")
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, noteId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // canScheduleExactAlarms() is true on a
                // stock phone for the install-time
                // permission. If the user has revoked
                // the permission via Settings, the call
                // throws SecurityException; the
                // runCatching at the call site catches
                // and the user gets a soft "set
                // reminder" confirmation that the
                // reminder may be late.
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        atMillis,
                        pi,
                    )
                } else {
                    // Fall back to an inexact alarm.
                    // The reminder will fire within a
                    // few minutes of the requested time
                    // — the launcher documents the
                    // behaviour in the row label
                    // ("reminder, may be late").
                    am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        atMillis,
                        pi,
                    )
                }
            } else {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    atMillis,
                    pi,
                )
            }
            Log.i(TAG, "scheduled reminder for noteId=$noteId at $atMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "schedule: SecurityException for noteId=$noteId", e)
            // Re-throw so the caller can decide. The
            // caller in LauncherViewModel.saveReminder
            // catches and surfaces a soft "reminder
            // may be late" UI; the data layer is
            // unaffected.
            throw e
        }
    }

    /**
     * Cancel the alarm for the note with the given
     * [noteId]. A no-op if the alarm was never
     * scheduled. Called when the user clears a
     * reminder or deletes the note.
     */
    fun cancel(context: Context, noteId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, noteId)
        am.cancel(pi)
        Log.i(TAG, "cancelled reminder for noteId=$noteId")
    }

    /**
     * Build the PendingIntent that the alarm fires.
     * The request code is the noteId (cast to Int)
     * so the identity is unique per note and a
     * second [schedule] for the same id updates the
     * extras rather than creating a parallel alarm.
     */
    private fun pendingIntentFor(context: Context, noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, noteId.toInt(), intent, flags)
    }
}
