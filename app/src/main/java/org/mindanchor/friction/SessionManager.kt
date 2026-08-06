package org.mindanchor.friction

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.data.FrictionPrefs
import java.time.LocalDate

/**
 * Time-boxed sessions (Gollwitzer & Sheeran 2006: pre-committed if-then
 * plans beat in-the-moment willpower). When the chosen time is up we send
 * ONE gentle notification with a graceful exit and an honest, counted
 * "+5 minutes" door — never a hard cutoff, never shame.
 */
object SessionManager {

    private const val CHANNEL_ID = "sessions"
    const val ACTION_EXPIRED = "org.mindanchor.SESSION_EXPIRED"
    const val ACTION_EXTEND = "org.mindanchor.SESSION_EXTEND"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_LABEL = "label"
    private const val EXTEND_MINUTES = 5L

    fun startSession(context: Context, packageName: String, label: String, minutes: Long) {
        scheduleExpiry(context, packageName, label, minutes)
    }

    fun extendSession(context: Context, packageName: String, label: String) {
        scheduleExpiry(context, packageName, label, EXTEND_MINUTES)
    }

    private fun scheduleExpiry(
        context: Context,
        packageName: String,
        label: String,
        minutes: Long,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SessionExpiryReceiver::class.java)
            .setAction(ACTION_EXPIRED)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_LABEL, label)
        val pending = PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000
        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun postExpiryNotification(context: Context, packageName: String, label: String, extensionsToday: Int) {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.session_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        val extendIntent = Intent(context, SessionExpiryReceiver::class.java)
            .setAction(ACTION_EXTEND)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_LABEL, label)
        val extendPending = PendingIntent.getBroadcast(
            context,
            packageName.hashCode() + 1,
            extendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (extensionsToday == 0) {
            context.getString(R.string.session_over_text)
        } else {
            context.resources.getQuantityString(
                R.plurals.session_extensions_today, extensionsToday, extensionsToday,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.session_over_title, label))
            .setContentText(text)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.session_extend_action), extendPending)
            .build()
        manager.notify(packageName.hashCode(), notification)
    }
}

/** Handles session expiry and the "+5 minutes" action. */
class SessionExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(SessionManager.EXTRA_PACKAGE) ?: return
        val label = intent.getStringExtra(SessionManager.EXTRA_LABEL) ?: packageName
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val prefs = FrictionPrefs(appContext)
                val today = LocalDate.now().toString()
                when (intent.action) {
                    SessionManager.ACTION_EXPIRED -> {
                        val extensions = prefs.extensionsToday(packageName, today)
                        SessionManager.postExpiryNotification(
                            appContext, packageName, label, extensions,
                        )
                    }

                    SessionManager.ACTION_EXTEND -> {
                        prefs.recordExtension(packageName, today)
                        appContext.getSystemService(NotificationManager::class.java)
                            ?.cancel(packageName.hashCode())
                        SessionManager.extendSession(appContext, packageName, label)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
