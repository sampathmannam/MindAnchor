package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "sunset")

/**
 * Sunset (wind-down) configuration. Fixed default window for v1:
 * 22:00 → 07:00; editable times come later.
 */
class SunsetPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("sunset_enabled")

    val enabled: Flow<Boolean> = context.dataStore.data.map { it[enabledKey] ?: false }

    suspend fun isEnabled(): Boolean = context.dataStore.data.first()[enabledKey] ?: false

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[enabledKey] = value }
    }

    companion object {
        val START: LocalTime = LocalTime.of(22, 0)
        val END: LocalTime = LocalTime.of(7, 0)
    }
}
