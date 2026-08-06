package org.mindanchor.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.data.NotificationPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.HeldNotification

/**
 * The batcher's intake. Hold-then-journal design (docs/research/05 §1):
 * we journal the notification content FIRST, then cancel the original —
 * so a crash between the two duplicates rather than loses. Released
 * batches surface as one digest notification plus the in-app journal;
 * we deliberately do not re-post copies (fragile, loses reply actions).
 */
class AnchorNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var config: kotlinx.coroutines.flow.StateFlow<Pair<Boolean, Set<String>>>

    override fun onCreate() {
        super.onCreate()
        val prefs = NotificationPrefs(applicationContext)
        config = combine(prefs.batchingEnabled, prefs.batchedApps) { enabled, apps ->
            enabled to apps
        }.stateIn(scope, SharingStarted.Eagerly, false to emptySet())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        BatchAlarms.ensureScheduled(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val (enabled, batchedApps) = config.value
        val meta = sbn.toMeta()
        val hold = NotificationClassifier.shouldHold(
            meta = meta,
            batchingEnabled = enabled,
            batchedApps = batchedApps,
            ownPackage = packageName,
        )
        if (!hold) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)
        val key = sbn.key

        scope.launch {
            AnchorDatabase.get(applicationContext).heldNotifications().insert(
                HeldNotification(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime,
                ),
            )
            // Journaled — now it is safe to take it off the shade.
            runCatching { cancelNotification(key) }
        }
    }

    private fun StatusBarNotification.toMeta(): NotificationMeta {
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val isConversation =
            template.endsWith("MessagingStyle") ||
                notification.category == Notification.CATEGORY_MESSAGE ||
                notification.shortcutId != null
        return NotificationMeta(
            packageName = packageName,
            category = notification.category,
            isOngoing = isOngoing,
            isClearable = isClearable,
            isGroupSummary =
                (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isConversation = isConversation,
        )
    }
}
