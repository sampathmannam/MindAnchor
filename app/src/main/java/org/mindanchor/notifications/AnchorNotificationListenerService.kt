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
import org.mindanchor.support.CrisisContactRef

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

    /**
     * Chosen people who must never be delayed. Held in memory so the hot
     * path stays synchronous — a notification arrives before any suspend
     * function could finish loading them.
     */
    @Volatile
    private var crisisContacts: List<CrisisContactRef> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val prefs = NotificationPrefs(applicationContext)
        config = combine(prefs.batchingEnabled, prefs.batchedApps) { enabled, apps ->
            enabled to apps
        }.stateIn(scope, SharingStarted.Eagerly, false to emptySet())

        scope.launch {
            AnchorDatabase.get(applicationContext).safety().contacts().collect { contacts ->
                crisisContacts = contacts.map { CrisisContactRef(it.name, it.phone) }
                crisisContactsLoaded = true
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        // On the service's own scope now: arming a release reads the
        // person's chosen times off DataStore, and a suspend call cannot
        // happen on the connection callback directly.
        scope.launch { BatchAlarms.ensureScheduled(applicationContext) }
    }

    /**
     * True once the chosen people have been read from disk at least once.
     *
     * Until then we hold nothing. The alternative was to batch with an
     * empty bypass list, which in the seconds after the service connects
     * would delay a message from exactly the person the bypass exists to
     * protect. Letting a few notifications through early is the harmless
     * side of that trade.
     */
    @Volatile
    private var crisisContactsLoaded: Boolean = false

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!crisisContactsLoaded) return
        val (enabled, batchedApps) = config.value
        val meta = sbn.toMeta()
        val hold = NotificationClassifier.shouldHold(
            meta = meta,
            batchingEnabled = enabled,
            batchedApps = batchedApps,
            ownPackage = packageName,
            crisisContacts = crisisContacts,
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

    /** Every identifier the notification offers for who it is from. */
    private fun peopleIn(extras: android.os.Bundle): List<String> = runCatching {
        val people = mutableListOf<String>()
        @Suppress("DEPRECATION")
        extras.getStringArray(Notification.EXTRA_PEOPLE)?.let { people += it }
        @Suppress("DEPRECATION")
        val persons = extras.getParcelableArrayList<android.app.Person>(
            Notification.EXTRA_PEOPLE_LIST,
        )
        persons?.forEach { person ->
            person.uri?.let { people += it }
            person.name?.let { people += it.toString() }
        }
        extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.let { people += it.toString() }
        people.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun StatusBarNotification.toMeta(): NotificationMeta {
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val isConversation =
            template.endsWith("MessagingStyle") ||
                notification.category == Notification.CATEGORY_MESSAGE ||
                notification.shortcutId != null
        // Spelled out as this.packageName on purpose. A bare packageName
        // here reads identically to the Service's own package, and if it
        // ever resolved that way every notification would look like our
        // own and batching would silently stop holding anything at all.
        return NotificationMeta(
            packageName = this.packageName,
            category = notification.category,
            isOngoing = isOngoing,
            isClearable = isClearable,
            isGroupSummary =
                (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isConversation = isConversation,
            people = peopleIn(extras),
        )
    }
}
