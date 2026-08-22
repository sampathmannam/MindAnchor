@file:Suppress("MaxLineLength")
package org.mindanchor.watch

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.mindanchor.R
import org.mindanchor.friction.BeforeYouSendHostActivity

/**
 * v0.26.1 §3.3: the SMS tone-check foreground service.
 *
 * When an incoming SMS arrives, the [SmsInterceptor] receiver
 * captures the sender + body excerpt, writes a record to
 * [SmsToneCheckPrefs], then starts this service as a foreground
 * service so the post-broadcast work is not killed under
 * Android 12+ background-start restrictions.
 *
 * The service's own foreground notification is the *tone-check
 * prompt*: "Tone check before sending. [Open]". Tapping the
 * notification opens
 * [org.mindanchor.friction.BeforeYouSendInterstitial] (or rather,
 * an activity that hosts it) with the SMS context carried as
 * intent extras.
 *
 * `foregroundServiceType="dataSync"` is the load-bearing pair
 * with the `FOREGROUND_SERVICE_DATA_SYNC` permission declared
 * in the manifest. The Android 14 service-type rules require
 * both, and the AppWatchServiceManifestFindingTest pins the
 * pair.
 *
 * The service does no real-time processing beyond the
 * notification post: a real backend hook would be a much
 * larger commitment (default-SMS-app status, plus the platform
 * permission grant flow) and is explicitly out of scope for
 * v0.26.1. The service exists so the work the receiver has
 * already done does not get reaped.
 */
class AppWatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is always started with the same intent shape:
        // sender + body excerpt from the intercepted SMS. The
        // notification it posts carries the same payload to the
        // interstitial.
        val sender = intent?.getStringExtra(EXTRA_SENDER).orEmpty()
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification(sender, body))
        // Nothing more to do — the prompt is the foreground
        // notification, and the record is already in the
        // store by the time the receiver called us. Stop
        // self so the foreground service does not stay
        // promoted forever.
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun buildNotification(sender: String, body: String): Notification {
        ensureChannel(this)
        val openIntent = BeforeYouSendHostActivity.intent(
            this,
            sender = sender,
            body = body,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(getString(R.string.tone_check_title))
            .setContentText(getString(R.string.tone_check_text, sender))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        // v0.26.3: the channel id is now sourced from Channels.TONE_CHECK
        // (centralised). Callers should use Channels.TONE_CHECK
        // directly; this constant is kept for backward-compat.
        const val CHANNEL_ID = org.mindanchor.notifications.Channels.TONE_CHECK
        const val NOTIFICATION_ID = 0x7101

        const val EXTRA_SENDER = "tone_check_sender"
        const val EXTRA_BODY = "tone_check_body"

        /**
         * The "Tone check" channel is created in
         * [org.mindanchor.notifications.Channels.ensureAll] at process
         * start (centralised v0.25.19). This stub is kept for
         * backward-compat with any caller that still invokes
         * `ensureChannel(...)`; it is a no-op because the channel
         * is already created. The
         * [org.mindanchor.permissions.NotificationChannelCreationFindingTest]
         * pins that `createNotificationChannel` only appears in
         * Channels.kt.
         */
        fun ensureChannel(@Suppress("UnusedParameter") context: Context) {
            // no-op; the channel is created at process start.
        }

        /**
         * The launcher-side permission gate for the post.
         *
         * Mirrors
         * [org.mindanchor.model.EmaScheduler.postPrompt]: no
         * `POST_NOTIFICATIONS` permission, no notification.
         * The tone-check is opt-in, like every other
         * notification this app posts.
         */
        fun hasPostNotificationsPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
