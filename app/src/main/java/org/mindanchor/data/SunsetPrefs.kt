package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mindanchor.sunset.Chronotype
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "sunset")

/**
 * Sunset (wind-down) configuration.
 *
 * The window was hardcoded to 22:00 → 07:00, with "editable times come
 * later" written next to it. 22:00 is somebody else's bedtime: it is
 * wrong for shift workers, wrong for anyone on call, wrong for night
 * staff, and a wind-down that begins three hours after you have gone
 * to bed is not a wind-down. It is stored now, and still defaults to
 * 22:00 → 07:00.
 *
 * The "22:00 is somebody else's bedtime" framing rests on two
 * verified findings, listed in the launcher's research index
 * (`docs/research/22-research-index.md`):
 *
 * - **Roenneberg et al. 2007, *Sleep Med. Rev.* 11(6):429-438,
 *   DOI 10.1016/j.smrv.2007.07.005.** The Munich Chronotype
 *   Questionnaire paper — chronotype is age- and sex-dependent, and
 *   the population distribution of "preferred bedtime" is wide
 *   enough that no single 22:00 default is the right one for most
 *   people. The launcher's default of 22:00 → 07:00 is a
 *   *placeholder*, not a recommendation: the right window is
 *   whatever the user actually sleeps on.
 * - **Åkerstedt 2003, *Occup. Med.* 53(2):89-94, DOI
 *   10.1093/occmed/kqg046.** Difficulty initiating sleep, shortened
 *   sleep, and somnolence during work hours are the principal acute
 *   symptoms of shift work. A 22:00 → 07:00 window applied to a
 *   shift worker is the wrong window by hours, and the launcher
 *   making the window editable is the only correct response.
 *
 * Out of scope for the verified index: the launcher's notion of a
 * "wind-down" itself is a design choice, not a research finding.
 * The two citations above justify making the window editable; they
 * do not justify a specific default length. The default length is
 * the launcher's choice.
 *
 * ## The chronotype → window contract
 *
 * Setting a [Chronotype] (from onboarding or settings) writes the
 * chronotype *and* — if and only if the user has not already picked
 * their own window — overwrites the default window to that
 * chronotype's [Chronotype.defaultWindow]. The guard exists so
 * picking a chronotype six months in does not silently undo a
 * 21:30 the user once set by hand. [setWindow] flips a "user has
 * customised" flag, and [setChronotype] only writes the window when
 * the flag is still off.
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
    private val overrideStartKey = intPreferencesKey("sunset_override_start_minute")
    private val overrideEndKey = intPreferencesKey("sunset_override_end_minute")
    private val overrideExpiryKey = stringPreferencesKey("sunset_override_expiry_day")

    /**
     * Flipped the first time the user changes a window by hand. Lets
     * [setChronotype] tell the difference between "no window yet, so
     * use the chronotype's default" and "user picked 21:30 themselves
     * last March, do not stomp on it."
     */
    private val windowCustomizedKey = booleanPreferencesKey("sunset_window_customized")

    private val chronotypeKey = stringPreferencesKey("sunset_chronotype")

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

    /**
     * The user's chronotype, or [Chronotype.UNKNOWN] before the
     * onboarding question has been answered.
     *
     * Unknown names (a future version adding a case, a corrupted
     * preference) are dropped to [Chronotype.UNKNOWN] rather than
     * throwing, so a stored value the launcher no longer recognises
     * degrades to "ask again" instead of crashing the home screen.
     */
    val chronotype: Flow<Chronotype> = context.dataStore.data.map { prefs ->
        prefs[chronotypeKey]?.let { name ->
            runCatching { Chronotype.valueOf(name) }.getOrNull()
        } ?: Chronotype.UNKNOWN
    }

    suspend fun currentChronotype(): Chronotype = chronotype.first()

    /**
     * Whether the user has ever written their own window. False on
     * first run, true from the first call to [setWindow]. Used by
     * [setChronotype] to decide whether the chronotype should also
     * overwrite the window.
     */
    val isWindowCustomized: Flow<Boolean> =
        context.dataStore.data.map { it[windowCustomizedKey] ?: false }

    /**
     * Sets the chronotype, and — only if the user has not already
     * picked a window — overwrites the window to the chronotype's
     * [Chronotype.defaultWindow]. The guard matters: a user who set
     * 21:30 six months ago, and is now picking a chronotype to
     * answer a different question, would not expect their bedtime
     * to move.
     */
    suspend fun setChronotype(chronotype: Chronotype) {
        context.dataStore.edit { prefs ->
            prefs[chronotypeKey] = chronotype.name
            val customized = prefs[windowCustomizedKey] ?: false
            val newWindow = newWindowFor(chronotype, customized)
            if (newWindow != null) {
                val (start, end) = newWindow
                prefs[startKey] = start.hour * 60 + start.minute
                prefs[endKey] = end.hour * 60 + end.minute
            }
        }
    }

    val startTime: Flow<LocalTime> =
        context.dataStore.data.map { timeOf(it[startKey], DEFAULT_START) }

    val endTime: Flow<LocalTime> =
        context.dataStore.data.map { timeOf(it[endKey], DEFAULT_END) }

    /** Both ends at once, so a caller cannot read a half-updated window. */
    suspend fun window(): Pair<LocalTime, LocalTime> =
        activeWindowOverride() ?: run {
            val prefs = context.dataStore.data.first()
            timeOf(prefs[startKey], DEFAULT_START) to timeOf(prefs[endKey], DEFAULT_END)
        }

    /**
     * The AnchorCore temporary window (Hook C accept), or null when unset
     * or expired. Deliberately separate keys: the person's own window and
     * the customised flag are never touched, so removing the override —
     * or just letting it lapse — restores exactly what was there before.
     */
    suspend fun activeWindowOverride(): Pair<LocalTime, LocalTime>? {
        val prefs = context.dataStore.data.first()
        val expiry = prefs[overrideExpiryKey]
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val s = prefs[overrideStartKey]
        val e = prefs[overrideEndKey]
        val valid = expiry != null &&
            !expiry!!.isBefore(java.time.LocalDate.now()) &&
            s != null && e != null &&
            s in 0..MINUTES_IN_DAY_MINUS_ONE &&
            e in 0..MINUTES_IN_DAY_MINUS_ONE &&
            s != e
        return if (!valid) null else timeOf(s, LocalTime.MIDNIGHT) to timeOf(e, LocalTime.MIDNIGHT)
    }

    suspend fun setTemporaryWindow(start: LocalTime, end: LocalTime, until: java.time.LocalDate) {
        context.dataStore.edit {
            it[overrideStartKey] = start.hour * 60 + start.minute
            it[overrideEndKey] = end.hour * 60 + end.minute
            it[overrideExpiryKey] = until.toString()
        }
    }

    suspend fun clearTemporaryWindow() {
        context.dataStore.edit {
            it.remove(overrideStartKey)
            it.remove(overrideEndKey)
            it.remove(overrideExpiryKey)
        }
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
     *
     * Sets the customised flag so a later chronotype change does not
     * overwrite the user's choice. The flag is sticky on purpose: there
     * is no scenario where the user wants their hand-set window to be
     * silently replaced by a chronotype default.
     */
    suspend fun setWindow(start: LocalTime, end: LocalTime): Boolean {
        if (!isValidWindow(start, end)) return false
        context.dataStore.edit {
            it[startKey] = start.hour * 60 + start.minute
            it[endKey] = end.hour * 60 + end.minute
            it[windowCustomizedKey] = true
        }
        return true
    }

    companion object {
        val DEFAULT_START: LocalTime = LocalTime.of(22, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(7, 0)
        private const val MINUTES_IN_DAY_MINUS_ONE = 1439

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
         * The window [setChronotype] should write, or null when the
         * existing window is the user's own and must be left alone.
         *
         * Pure function so the customised-window guard is testable
         * without a DataStore: the DataStore side-effect in
         * [setChronotype] is the only thing that needs Android.
         */
        fun newWindowFor(
            chronotype: Chronotype,
            isWindowCustomized: Boolean,
        ): Pair<LocalTime, LocalTime>? =
            if (isWindowCustomized) null else chronotype.defaultWindow()

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
