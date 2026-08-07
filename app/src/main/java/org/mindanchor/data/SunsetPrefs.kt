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

    /**
     * Whether the screen also goes grey through the quiet hours. Separate
     * from [enabled] on purpose: someone may want a quiet phone without a
     * colourless one, or the reverse, and neither should imply the other.
     */
    private val grayscaleKey = booleanPreferencesKey("sunset_grayscale")

    val enabled: Flow<Boolean> = context.dataStore.data.map { it[enabledKey] ?: false }

    suspend fun isEnabled(): Boolean = context.dataStore.data.first()[enabledKey] ?: false

    val grayscaleAtNight: Flow<Boolean> =
        context.dataStore.data.map { it[grayscaleKey] ?: false }

    suspend fun isGrayscaleAtNight(): Boolean =
        context.dataStore.data.first()[grayscaleKey] ?: false

    suspend fun setGrayscaleAtNight(value: Boolean) {
        context.dataStore.edit { it[grayscaleKey] = value }
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[enabledKey] = value }
    }

    companion object {
        val START: LocalTime = LocalTime.of(22, 0)
        val END: LocalTime = LocalTime.of(7, 0)

        /**
         * Whether [now] falls inside a window running [start] → [end],
         * which may cross midnight.
         *
         * This lives next to the times themselves because the naive
         * `now >= start || now < end` is only correct while the window does
         * cross midnight. Three copies of that naive form had appeared; the
         * day these times become editable, every copy that is not this one
         * silently starts answering a different question.
         */
        fun isInWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
            if (start <= end) now >= start && now < end else now >= start || now < end

        /** Whether [now] falls inside the configured quiet hours. */
        fun isQuietHour(now: LocalTime = LocalTime.now()): Boolean =
            isInWindow(now, START, END)
    }
}
