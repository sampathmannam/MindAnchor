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

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
