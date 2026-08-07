package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "sunset")

/**
 * Sunset (wind-down) configuration.
 *
 * The window was hardcoded to 22:00 → 07:00, with "editable times come
 * later" written next to it. 22:00 is somebody else's bedtime: it is wrong
 * for shift workers, wrong for anyone on call, wrong for night staff, and
 * a wind-down that begins three hours after you have gone to bed is not a
 * wind-down. It is stored now, and still defaults to 22:00 → 07:00.
 */
class SunsetPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("sunset_enabled")

    /**
     * Whether the screen also goes grey through the quiet hours. Separate
     * from [enabled] on purpose: someone may want a quiet phone without a
     * colourless one, or the reverse, and neither should imply the other.
     */
    private val grayscaleKey = booleanPreferencesKey("sunset_grayscale")

    private val mirrorKey = booleanPreferencesKey("sleep_mirror")

    /**
     * Whether to count nights that ran later than usual and show the
     * count — see [org.mindanchor.sleep.Deviation].
     *
     * Off until asked for. It states a fact about somebody's own screen
     * and never interprets it, but "you usually aren't" can still read as
     * reproach, and it lands hardest on the person already keeping score
     * against themselves.
     */
    val sleepMirror: Flow<Boolean> = context.dataStore.data.map { it[mirrorKey] ?: false }

    suspend fun setSleepMirror(value: Boolean) {
        context.dataStore.edit { it[mirrorKey] = value }
    }

    private val startKey = intPreferencesKey("sunset_start_minute")
    private val endKey = intPreferencesKey("sunset_end_minute")

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

    val startTime: Flow<LocalTime> =
        context.dataStore.data.map { timeOf(it[startKey], DEFAULT_START) }

    val endTime: Flow<LocalTime> =
        context.dataStore.data.map { timeOf(it[endKey], DEFAULT_END) }

    /** Both ends at once, so a caller cannot read a half-updated window. */
    suspend fun window(): Pair<LocalTime, LocalTime> {
        val prefs = context.dataStore.data.first()
        return timeOf(prefs[startKey], DEFAULT_START) to timeOf(prefs[endKey], DEFAULT_END)
    }

    /** Whether the quiet hours are running right now. */
    suspend fun isQuietHour(now: LocalTime = LocalTime.now()): Boolean {
        val (start, end) = window()
        return isInWindow(now, start, end)
    }

    /**
     * Stores a new window. A window whose ends are equal is refused rather
     * than stored, because it reads as "all day" and behaves as "never",
     * and the person would have no way to tell which they had asked for.
     */
    suspend fun setWindow(start: LocalTime, end: LocalTime): Boolean {
        if (!isValidWindow(start, end)) return false
        context.dataStore.edit {
            it[startKey] = start.hour * 60 + start.minute
            it[endKey] = end.hour * 60 + end.minute
        }
        return true
    }

    companion object {
        val DEFAULT_START: LocalTime = LocalTime.of(22, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(7, 0)

        /**
         * Reads a stored minute-of-day back into a time, falling back to
         * [fallback] when it is missing or out of range. A corrupt value
         * must cost somebody their preference, not their launcher.
         */
        fun timeOf(minuteOfDay: Int?, fallback: LocalTime): LocalTime =
            if (minuteOfDay == null || minuteOfDay !in 0..1439) {
                fallback
            } else {
                LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            }

        fun isValidWindow(start: LocalTime, end: LocalTime): Boolean = start != end

        /**
         * Whether [now] falls inside a window running [start] → [end],
         * which may cross midnight.
         *
         * This lives next to the times themselves because the naive
         * `now >= start || now < end` is only correct while the window does
         * cross midnight. Three copies of that naive form had appeared, and
         * now that the times really are editable, a same-day window is no
         * longer hypothetical — someone on nights will set 09:00 → 17:00
         * and every naive copy would have answered true at every hour.
         */
        fun isInWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
            if (start <= end) now >= start && now < end else now >= start || now < end
    }
}
