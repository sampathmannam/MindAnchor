package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notifications")

/**
 * Batching configuration. Batching is opt-in per app ("every feature is a
 * toggle", docs/PLAN.md §2); conversations and calls always bypass it
 * regardless of these settings.
 */
class NotificationPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("batching_enabled")
    private val batchedAppsKey = stringSetPreferencesKey("batched_packages")

    val batchingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[enabledKey] ?: false
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
}
