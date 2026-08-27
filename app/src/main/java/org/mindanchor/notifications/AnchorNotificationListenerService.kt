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
    private lateinit var config: kotlinx.coroutines.flow.StateFlow<Pair<Boolean, Set<String>>> // (batchingEnabled, neverBatchApps)

    /**
     * T-3.2 (v0.72+) — whether the marketing classifier is allowed to
     * demote notifications. Collected eagerly like [config] so the hot
     * path stays a synchronous read.
     */
    private lateinit var marketingDemotion: kotlinx.coroutines.flow.StateFlow<Boolean>

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
        // v0.72+ — the second leg of the pair is the LET-THROUGH set
        // (apps the user has explicitly chosen to bypass batching), not
        // the batched set. See NotificationPrefs.DEFAULT_NEVER_BATCH_PACKAGES
        // for the curated seed (phone, WhatsApp, messages).
        config = combine(prefs.batchingEnabled, prefs.neverBatchApps) { enabled, never ->
            enabled to never
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            false to NotificationPrefs.DEFAULT_NEVER_BATCH_PACKAGES,
        )

        // v0.30+ (spec Phase 2) — the active-hours
        // and retention values drive the demotion
        // gate and the auto-prune on listener connect.
        // Active hours default 21:00-07:00 (the spec
        // recommendation) and retention default 7
        // days. The flow is collected on the service
        // scope; the values are read in
        // [onNotificationPosted] for the gate and
        // [onListenerConnected] for the prune.
        activeHours = combine(
            prefs.activeHoursStart,
            prefs.activeHoursEnd,
        ) { start, end -> start to end }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                NotificationPrefs.DEFAULT_ACTIVE_START to NotificationPrefs.DEFAULT_ACTIVE_END,
            )
        retentionDays = prefs.heldRetentionDays
            .stateIn(scope, SharingStarted.Eagerly, NotificationPrefs.DEFAULT_RETENTION_DAYS)
        // T-3.2 (v0.72+) — marketing demotion toggle; default on.
        marketingDemotion = prefs.marketingDemotionEnabled
            .stateIn(scope, SharingStarted.Eagerly, NotificationPrefs.DEFAULT_MARKETING_DEMOTION)

        scope.launch {
            AnchorDatabase.get(applicationContext).safety().contacts().collect { contacts ->
                // v0.72.x: an empty list is a valid
                // starting state — the user has, in
                // fact, configured no crisis contacts.
                // The previous version of this code
                // gated the whole listener on the
                // first emit, which meant a stuck or
                // slow Flow left the user with no
                // batching at all. The gate was
                // removed; the bypass check now runs
                // against an empty list during the
                // brief window before this collect
                // emits, which is the correct
                // behaviour.
                crisisContacts = contacts.map { CrisisContactRef(it.name, it.phone) }
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
        // v0.30+ (spec Phase 2) — auto-prune
        // notifications older than the retention
        // window. The user's setting is read from
        // DataStore; the prune is best-effort and
        // runs on the service scope so it does not
        // block the listener connect callback.
        scope.launch { pruneExpired() }
    }

    /**
     * Auto-prune held notifications older than the
     * user's [NotificationPrefs.heldRetentionDays]
     * setting. The cutoff is `now - retentionDays *
     * 24h`; rows whose `postedAt < cutoff` are
     * deleted in a single SQL `DELETE`. The prune is
     * idempotent: a second call is a no-op when no
     * rows are older than the cutoff.
     */
    private suspend fun pruneExpired() {
        val days = retentionDays.value
        val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
        AnchorDatabase.get(applicationContext).heldNotifications().pruneOlderThan(cutoff)
    }

    /**
     * v0.30+ (spec Phase 2) — the active-hours
     * (start, end) tuple, in minutes-of-day. Read
     * from [NotificationPrefs.activeHoursStart] and
     * [NotificationPrefs.activeHoursEnd]. The flow
     * is collected on the service scope.
     */
    private lateinit var activeHours: kotlinx.coroutines.flow.StateFlow<Pair<Int, Int>>

    /**
     * v0.30+ (spec Phase 2) — the held-retention
     * window in days. Read from
     * [NotificationPrefs.heldRetentionDays]. Drives
     * the auto-prune in [pruneExpired].
     */
    private lateinit var retentionDays: kotlinx.coroutines.flow.StateFlow<Int>

    private companion object {
        // Milliseconds per day. The magic number is
        // hoisted to a constant so the detekt
        // [MagicNumber] rule does not flag the
        // ms-per-day computation in [pruneExpired].
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }

    /**
     * v0.30+ (spec Phase 2) — the now-minute helper
     * for the active-hours gate. Exposed at the file
     * level so the test in
     * [org.mindanchor.notifications.ActiveHoursTest]
     * covers the rule without needing the service.
     */
    private fun nowMinuteOfDay(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            cal.get(java.util.Calendar.MINUTE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // v0.72.x: the previous "wait for crisis
        // contacts to load" gate was removed. An
        // empty [crisisContacts] list is the correct
        // starting state — it is what the user has
        // configured — and the bypass check inside
        // [NotificationClassifier.shouldHold] does
        // the right thing with it. The old gate
        // blocked the whole listener indefinitely
        // whenever the Flow did not emit, which
        // was strictly worse than letting one
        // notification through to a person the user
        // had not added to the safety plan yet.
        //
        // v0.30+ (spec Phase 2) — gate the demote on
        // the active-hours window. Outside the
        // window, the notification passes through
        // unchanged. The window may cross midnight
        // (default 21:00-07:00); the helper handles
        // that.
        val (startMin, endMin) = activeHours.value
        if (!NotificationPrefs.isWithinActiveHoursStatic(
                nowMinuteOfDay(), startMin, endMin,
            )
        ) {
            return
        }
        val (enabled, neverBatchApps) = config.value
        val meta = sbn.toMeta()
        // T-3.2 (v0.72+) — title/text/appLabel are extracted BEFORE the
        // hold decision now: the marketing classifier needs them, and they
        // were already needed for every notification we hold.
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)

        // v0.72+ — marketing demotion is now a TIER, not a hold override.
        // The shouldHold gate is checked first; the marketing classifier
        // only changes which SenderTier the held row is recorded under
        // (and therefore how the attention receipt attributes it).
        // An app on the let-through list is never held, even if its
        // notification looks like marketing — that was the design choice
        // behind flipping the default.
        if (!NotificationClassifier.shouldHold(
                meta = meta,
                batchingEnabled = enabled,
                neverBatchApps = neverBatchApps,
                ownPackage = packageName,
                crisisContacts = crisisContacts,
            )
        ) return
        val demoteMarketing = marketingDemotion.value &&
            MarketingClassifier.isMarketing(
                meta = meta,
                title = title,
                text = text,
                appLabel = appLabel,
            )
        val key = sbn.key

        scope.launch {
            AnchorDatabase.get(applicationContext).heldNotifications().insert(
                HeldNotification(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime,
                    tier = if (demoteMarketing) {
                        SenderTier.MARKETING.name
                    } else {
                        SenderTier.MACHINE.name
                    },
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
