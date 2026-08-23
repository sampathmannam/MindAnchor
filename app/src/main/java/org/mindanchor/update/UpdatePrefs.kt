package org.mindanchor.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * v0.25.9: cache the last update-check result so the launcher
 * does not hit the GitHub API on every cold start. A 24h TTL
 * is short enough that the user always sees a current result
 * and long enough that a heavy daily user does not generate
 * one request per open.
 */
private val Context.updatePrefsDataStore by preferencesDataStore(name = "update_check")

class UpdatePrefs(private val context: Context) {

    private val lastCheckedKey = longPreferencesKey("last_checked_millis")
    private val dismissedVersionKey = stringPreferencesKey("dismissed_version")

    suspend fun lastCheckedMillis(): Long = context.updatePrefsDataStore.data
        .first()[lastCheckedKey] ?: 0L

    suspend fun isDismissed(version: String): Boolean = context.updatePrefsDataStore.data
        .first()[dismissedVersionKey] == version

    suspend fun recordChecked() {
        context.updatePrefsDataStore.edit { prefs ->
            prefs[lastCheckedKey] = System.currentTimeMillis()
        }
    }

    suspend fun recordDismissed(version: String) {
        context.updatePrefsDataStore.edit { prefs ->
            prefs[dismissedVersionKey] = version
        }
    }

    /**
     * `true` if the cached check is younger than [TTL_MILLIS].
     * `false` if the check is stale or has never run.
     */
    suspend fun isCacheFresh(): Boolean {
        val last = lastCheckedMillis()
        if (last == 0L) return false
        return System.currentTimeMillis() - last < TTL_MILLIS
    }

    companion object {
        val TTL_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}
