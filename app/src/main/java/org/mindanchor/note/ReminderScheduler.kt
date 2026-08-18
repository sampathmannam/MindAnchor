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
 * The launcher uses [AlarmManager.setAlarmClock] to
 * fire a [ReminderReceiver] at the note's
 * [org.mindanchor.model.Note.reminderAt] time. The
 * alarm is the only thing that wakes the app — the
 * note is saved with `reminderAt` set, the scheduler
 * is told to schedule, and from that point on the
 * OS owns the alarm until the receiver fires.
 *
 * ## v0.45.1: migrated from `setExactAndAllowWhileIdle`
 * to `setAlarmClock`
 *
 * The previous implementation used
 * [AlarmManager.setExactAndAllowWhileIdle], which on
 * Android 12+ is throttled by the system's
 * `app_standby` bucket even when the `allow-while-idle`
 * flag is set. An empirical repro on the v0.45.0
 * FireTest reminder showed the alarm sitting
 * `app_standby=-1h10m34s` past due with no sign of
 * firing. `setAlarmClock` is the only API
 * guaranteed to fire on the dot on Android 12+
 * regardless of `app_standby` bucket or Doze mode.
 *
 * The cost is a small clock icon in the status bar
 * for the duration of the pending alarm. For a
 * mental-health launcher, "your reminder is pending"
 * is a *positive* affordance — the user has made a
 * promise to themselves, and the system is visibly
 * holding them to it.
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
 * do not want a 15-minute window. `setAlarmClock` is
 * the only [AlarmManager] method that fires on
 * the dot in Doze mode on Android 12+ without being
 * subject to the `app_standby` bucket. The
 * SCHEDULE_EXACT_ALARM permission is not required on
 * Android 13+ when the app does not target SDK 34+,
 * but the launcher declares it anyway (declared in
 * the manifest, granted on install) so the user
 * does not see a "this reminder may be late"
 * dialog.
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
     *
     * v0.45.1: uses [AlarmManager.setAlarmClock]
     * instead of `setExactAndAllowWhileIdle` so
     * the alarm is not throttled by the Android 12+
     * `app_standby` bucket. The trade-off is a
     * small clock icon in the status bar while the
     * alarm is pending — accepted as a positive
     * affordance for a mental-health surface.
     */
    fun schedule(context: Context, noteId: Long, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "schedule: atMillis is in the past for noteId=$noteId; ignoring")
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, noteId)
        val showIntent = Intent(context, org.mindanchor.HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPi = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // v0.45.1: setAlarmClock is the only API
            // that fires on the dot on Android 12+
            // regardless of app_standby bucket.
            // Unlike setExactAndAllowWhileIdle, it
            // is NOT subject to the temporary
            // allowlist restriction.
            //
            // v0.48.0: gate on canScheduleExactAlarms().
            // setAlarmClock() throws SecurityException
            // when the app does not hold
            // SCHEDULE_EXACT_ALARM (or USE_EXACT_ALARM
            // on Android 13+). A user who has revoked
            // the permission via Settings, or an
            // emulator that has not granted it, would
            // otherwise see every reminder fail. The
            // fallback to setAndAllowWhileIdle (an
            // inexact alarm) preserves the reminder
            // for late delivery, the same v0.44.0
            // contract.
            //
            // The first argument is an
            // [AlarmClockInfo] wrapping the trigger
            // time and a "show" PendingIntent. The
            // system uses the show-intent as the
            // tap target for the clock icon it
            // displays in the status bar while the
            // alarm is pending. We point it at
            // HomeActivity so tapping the clock
            // returns the user to the launcher.
            //
            // The second argument is the operation
            // PendingIntent — what the alarm
            // actually fires when the time hits.
            // That is the ReminderReceiver.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                Log.w(TAG, "schedule: cannot schedule exact alarms; falling back to inexact for noteId=$noteId")
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    atMillis,
                    pi,
                )
                return
            }
            val info = AlarmManager.AlarmClockInfo(atMillis, showPi)
            am.setAlarmClock(info, pi)
            Log.i(TAG, "scheduled reminder (setAlarmClock) for noteId=$noteId at $atMillis")
        } catch (e: SecurityException) {
            // On a restricted device the user has
            // revoked SCHEDULE_EXACT_ALARM. The
            // v0.44.0 fallback was
            // `setAndAllowWhileIdle` (inexact);
            // v0.45.1 keeps the same fallback so
            // an over-restrictive Settings
            // configuration still surfaces a
            // reminder, even if late.
            Log.e(TAG, "schedule: SecurityException for noteId=$noteId; falling back to inexact", e)
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    atMillis,
                    pi,
                )
                Log.w(TAG, "schedule: fell back to inexact for noteId=$noteId")
            } catch (e2: SecurityException) {
                Log.e(TAG, "schedule: even inexact failed for noteId=$noteId", e2)
                throw e2
            }
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
        // v0.45.1: setAlarmClock uses a separate
        // show-intent PendingIntent (the clock-
        // icon tap target). Cancel it too, or
        // the status bar clock icon lingers
        // after the reminder has been deleted.
        val showIntent = Intent(context, org.mindanchor.HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPi = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(showPi)
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
