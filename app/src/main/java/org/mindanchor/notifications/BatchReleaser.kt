package org.mindanchor.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.mindanchor.R
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.digest.DigestActivity

/**
 * Turns the pending batch into a single calm digest notification and marks
 * the journal entries released. One gentle notification per batch — never a
 * flood of re-posts.
 */
object BatchReleaser {

    private const val CHANNEL_ID = "digest"
    private const val NOTIFICATION_ID = 7

    suspend fun releaseNow(context: Context) {
        val dao = AnchorDatabase.get(context).heldNotifications()
        val pending = dao.pending()
        if (pending.isEmpty()) return

        dao.markAllReleased(System.currentTimeMillis())
        postDigest(context, pending.size, pending.map { it.appLabel }.distinct())
    }

    private fun postDigest(context: Context, count: Int, appLabels: List<String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // Journal still has everything; digest UI shows it on next open.
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.digest_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.digest_channel_description)
            },
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, DigestActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val apps = appLabels.take(4).joinToString(", ") +
            if (appLabels.size > 4) "…" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.digest_title, count, count,
                ),
            )
            .setContentText(context.getString(R.string.digest_text, apps))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
