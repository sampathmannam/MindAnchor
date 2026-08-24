package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import org.mindanchor.notifications.BatchSchedule

private val Context.dataStore by preferencesDataStore(name = "notifications")

/**
 * Batching configuration. Batching is opt-in per app ("every feature is a
 * toggle", docs/PLAN.md §2); conversations and calls always bypass it
 * regardless of these settings.
 */
class NotificationPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("batching_enabled")
    private val batchedAppsKey = stringSetPreferencesKey("batched_packages")

    /**
     * The three release times, as minutes of the day.
     *
     * One key per slot rather than one encoded string, so a single
     * unreadable value costs one release time instead of all three —
     * the same reasoning as [SunsetPrefs]'s two separate keys.
     */
    private val releaseKeys = List(BatchSchedule.SLOTS) { intPreferencesKey("release_$it") }

    val batchingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[enabledKey] ?: false
    }

    /**
     * When the batches arrive.
     *
     * Falls back to [BatchSchedule.DEFAULT_TIMES] whenever what is stored
     * cannot be used — a missing key, a value out of range, two slots that
     * have somehow ended up identical. Discarding rather than repairing is
     * deliberate: a corrupt preference should cost somebody their setting,
     * never their notifications.
     */
    val releaseTimes: Flow<List<LocalTime>> = context.dataStore.data.map { prefs ->
        val stored = releaseKeys.mapIndexedNotNull { slot, key ->
            val minute = prefs[key] ?: return@mapIndexedNotNull null
            if (minute in 0 until MINUTES_PER_DAY) {
                LocalTime.of(minute / 60, minute % 60)
            } else {
                null
            }
        }
        if (BatchSchedule.isUsable(stored)) stored else BatchSchedule.DEFAULT_TIMES
    }

    /** The times as they stand right now, for a caller that cannot collect. */
    suspend fun currentReleaseTimes(): List<LocalTime> = releaseTimes.first()

    /**
     * Stores a whole set of release times, or refuses.
     *
     * All three at once rather than one at a time, because the validity
     * rule is about the set: two slots holding the same minute would show
     * the same time twice with no way to tell three batches from two. See
     * [BatchSchedule.nudged], which is where a move is checked before it
     * ever reaches this.
     */
    suspend fun setReleaseTimes(times: List<LocalTime>): Boolean {
        if (!BatchSchedule.isUsable(times)) return false
        context.dataStore.edit { prefs ->
            times.forEachIndexed { slot, time ->
                prefs[releaseKeys[slot]] = time.hour * 60 + time.minute
            }
        }
        return true
    }

    val batchedApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[batchedAppsKey] ?: emptySet()
    }

    // v0.30+ (spec Phase 2) — the active hours
    // window. Notifications are only demoted inside
    // this window; outside it, the
    // [AnchorNotificationListenerService] lets
    // notifications through unchanged. The spec
    // calls for default 21:00 to 07:00 (the typical
    // "I should not see work stuff overnight"
    // window). Stored as minutes-of-day to keep the
    // DataStore key simple; the helper
    // [isWithinActiveHours] handles the
    // midnight-crossing case.
    private val activeHoursStartKey = intPreferencesKey("active_hours_start")
    private val activeHoursEndKey = intPreferencesKey("active_hours_end")
    // v0.30+ (spec Phase 2) — the held-retention
    // window in days. Held notifications older than
    // this are pruned on next service start; the
    // default is the spec's 7 days.
    private val heldRetentionDaysKey = intPreferencesKey("held_retention_days")

    val activeHoursStart: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[activeHoursStartKey] ?: DEFAULT_ACTIVE_START
    }
    val activeHoursEnd: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[activeHoursEndKey] ?: DEFAULT_ACTIVE_END
    }
    val heldRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[heldRetentionDaysKey]?.coerceIn(1, MAX_RETENTION_DAYS)
            ?: DEFAULT_RETENTION_DAYS
    }

    /**
     * Whether the given [now] (minutes-of-day) is inside
     * the active hours window. The window may cross
     * midnight (e.g. 21:00 to 07:00); this helper handles
     * that without forcing the user to express it as
     * two windows. The test for this rule lives at
     * ActiveHoursTest; the rule is exposed via the
     * companion [isWithinActiveHours] so tests can
     * call it without a `NotificationPrefs` instance.
     */
    fun isWithinActiveHours(
        nowMinutes: Int,
        startMinutes: Int,
        endMinutes: Int,
    ): Boolean = isWithinActiveHoursStatic(nowMinutes, startMinutes, endMinutes)

    suspend fun setActiveHours(startMinutes: Int, endMinutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[activeHoursStartKey] = startMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            prefs[activeHoursEndKey] = endMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        }
    }

    suspend fun setHeldRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[heldRetentionDaysKey] = days.coerceIn(1, MAX_RETENTION_DAYS)
        }
    }

    suspend fun current(): Pair<Boolean, Set<String>> {
        val prefs = context.dataStore.data.first()
        return (prefs[enabledKey] ?: false) to (prefs[batchedAppsKey] ?: emptySet())
    }

    suspend fun setBatchingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setAppBatched(packageName: String, batched: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[batchedAppsKey] ?: emptySet()
            prefs[batchedAppsKey] =
                if (batched) current + packageName else current - packageName
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        // v0.30+ (spec Phase 2) — the default
        // active-hours window. 21:00 to 07:00 is the
        // spec's recommendation; the user can change
        // it in Settings.
        const val DEFAULT_ACTIVE_START = 21 * 60
        const val DEFAULT_ACTIVE_END = 7 * 60
        // v0.30+ (spec Phase 2) — the default
        // held-retention in days. The spec's default
        // is 7; the cap is 30 so a user who wants a
        // longer window can have one without burying
        // the table in held notifications.
        const val DEFAULT_RETENTION_DAYS = 7
        const val MAX_RETENTION_DAYS = 30

        /**
         * v0.30+ (spec Phase 2) — whether the given
         * [nowMinutes] is inside the active-hours
         * window. The window may cross midnight (e.g.
         * 21:00 to 07:00); this helper handles that
         * without forcing the user to express it as
         * two windows. Internal so the unit test
         * ([ActiveHoursTest]) can call it without
         * instantiating `NotificationPrefs`.
         */
        @JvmStatic
        fun isWithinActiveHoursStatic(
            nowMinutes: Int,
            startMinutes: Int,
            endMinutes: Int,
        ): Boolean {
            if (startMinutes == endMinutes) return true // window is the full day
            return if (startMinutes < endMinutes) {
                nowMinutes in startMinutes until endMinutes
            } else {
                // Crosses midnight: active from start
                // to end-of-day, then 0 to end.
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            }
        }
    }
}
