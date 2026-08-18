package org.mindanchor.note

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.HomeActivity
import org.mindanchor.R
import org.mindanchor.data.NotesPrefs
import org.mindanchor.friction.SealedCodecs

/**
 * v0.44.0: the broadcast receiver that fires when a
 * reminder alarm hits.
 *
 * Flow:
 *  1. [ReminderScheduler] schedules an
 *     AlarmManager alarm at the note's reminderAt.
 *  2. The OS wakes the broadcast receiver at the
 *     scheduled time.
 *  3. The receiver reads the note body from the
 *     DataStore (so the body is always fresh, not
 *     a snapshot from when the alarm was scheduled).
 *  4. The receiver posts a notification with the
 *     body as content.
 *  5. The receiver sets [FlashSignal] so the
 *     foreground [HomeActivity] shows a full-screen
 *     flash.
 *  6. If the activity is not running, the
 *     notification's content intent launches it.
 *
 * ## Why a broadcast and not a service
 *
 * The alarm is the wake-up primitive. A service
 * does not wake the app from Doze on its own; a
 * broadcast does. The receiver is short-lived
 * (a few hundred ms) — long enough to read the
 * note body, post a notification, and write to
 * [FlashSignal]. Anything heavier would be a
 * foreground service, which the launcher does not
 * need.
 *
 * ## Why read the body from disk, not the intent
 *
 * The PendingIntent's extras survive a reboot
 * (the OS restores alarms), but they are a
 * snapshot from when the alarm was scheduled.
 * A user who edited the note between scheduling
 * and firing would see the old body. Reading the
 * body from the DataStore on fire gives the
 * user-current body, which is what the user
 * expects.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE_REMINDER) {
            return
        }
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) {
            Log.w(TAG, "onReceive: missing or invalid noteId; ignoring")
            return
        }
        val app = context.applicationContext

        // 1) Fire the flash signal first, so the
        // foreground HomeActivity picks it up
        // immediately. The signal write is cheap
        // (just a StateFlow assignment).
        FlashSignal.fire(noteId)

        // 2) Read the body and post a notification
        // on a background coroutine. The
        // BroadcastReceiver's onReceive must
        // complete quickly, so the DataStore read
        // is dispatched on the IO scope.
        val pending = goAsync()
        scope.launch {
            try {
                val body = readNoteBody(app, noteId)
                postNotification(app, noteId, body)
            } catch (e: Exception) {
                Log.e(TAG, "onReceive: failed to post notification for noteId=$noteId", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Read the body of the note with [noteId] from
     * the DataStore. Returns the body, or a fallback
     * "Reminder fired" if the note was deleted or
     * the DataStore is corrupt.
     */
    private suspend fun readNoteBody(context: Context, noteId: Long): String {
        val notes = try {
            // The DataStore flow is the source of
            // truth; the receiver reads it once via
            // `first()`. The read is on the IO
            // dispatcher because SealedCodecs may
            // touch the Keystore.
            NotesPrefs(context).notes.first().byId(noteId)?.body
        } catch (e: Exception) {
            Log.e(TAG, "readNoteBody: failed to read for noteId=$noteId", e)
            null
        }
        return notes?.takeIf { it.isNotBlank() } ?: "Reminder fired"
    }

    /**
     * Post a system notification for the reminder.
     * The notification's content intent opens
     * [HomeActivity], which will see [FlashSignal]
     * and show the flash. The notification channel
     * is created on first use; Android 8+ requires
     * a channel for every notification.
     */
    private fun postNotification(context: Context, noteId: Long, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, nm)
        val openIntent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(HomeActivity.EXTRA_FLASH_NOTE_ID, noteId)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_breath)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()
        try {
            nm.notify(noteId.toInt(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS is a runtime
            // permission on Android 13+. If the
            // user has denied it, the notify call
            // throws. The flash is still visible on
            // HomeActivity; the notification is the
            // fallback. Logged but not fatal.
            Log.w(TAG, "postNotification: SecurityException; the user may have denied POST_NOTIFICATIONS")
        }
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "reminders"

        /**
         * The application-singleton scope for
         * DataStore reads from the receiver. The
         * scope lives for the lifetime of the
         * process; the receiver's `goAsync()` block
         * is short enough that the SupervisorJob
         * never accumulates. The scope is `private`
         * to the companion so the receiver file is
         * self-contained.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
