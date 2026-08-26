package org.mindanchor.anchorcore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.anchorDataStore by preferencesDataStore(name = "anchorcore")

/**
 * The loop's switches and counters. One DataStore, the SunsetPrefs
 * discipline: typed keys, defaults that match the opt-out-by-silence
 * rule, nothing interpreted.
 *
 * The clean-streak default is 7 (= unflagged): a person whose loop has
 * never flagged anything must not start life inside a flagged week.
 */
class AnchorPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("anchor_enabled")
    private val letterKey = booleanPreferencesKey("hook_letter_facts")
    private val frictionKey = booleanPreferencesKey("hook_friction_hold")
    private val proposalKey = booleanPreferencesKey("hook_sunset_proposal")
    private val cleanStreakKey = intPreferencesKey("week_clean_streak")
    private val weekFlaggedKey = booleanPreferencesKey("week_flagged")
    private val lastReducedDayKey = stringPreferencesKey("streak_last_reduced_day")
    private val suppressedUntilKey = longPreferencesKey("proposal_suppressed_until_epoch_millis")
    private val sriPrevDayKey = stringPreferencesKey("sri_prev_day")
    private val sriPrevScoreKey = intPreferencesKey("sri_prev_score")
    private val sriCurDayKey = stringPreferencesKey("sri_cur_day")
    private val sriCurScoreKey = intPreferencesKey("sri_cur_score")

    val enabled: Flow<Boolean> = context.anchorDataStore.data.map { it[enabledKey] ?: false }
    val letterFactsEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[letterKey] ?: false }
    val frictionHoldEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[frictionKey] ?: false }
    val sunsetProposalEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[proposalKey] ?: false }

    suspend fun isEnabled(): Boolean = enabled.first()

    /**
     * The first off->on transition flips every hook on (the person asked
     * for the loop); afterwards each hook toggles independently and a
     * hand-set value is never overwritten.
     */
    suspend fun setEnabled(v: Boolean) {
        context.anchorDataStore.edit {
            val was = it[enabledKey] ?: false
            it[enabledKey] = v
            if (v && !was && it[letterKey] == null) it[letterKey] = true
            if (v && !was && it[frictionKey] == null) it[frictionKey] = true
            if (v && !was && it[proposalKey] == null) it[proposalKey] = true
        }
    }

    suspend fun setLetterFactsEnabled(v: Boolean) { context.anchorDataStore.edit { it[letterKey] = v } }
    suspend fun setFrictionHoldEnabled(v: Boolean) { context.anchorDataStore.edit { it[frictionKey] = v } }
    suspend fun setSunsetProposalEnabled(v: Boolean) { context.anchorDataStore.edit { it[proposalKey] = v } }

    suspend fun cleanStreak(): Int =
        context.anchorDataStore.data.first()[cleanStreakKey] ?: WeekPicture.CLEAN_DAYS_TO_UNFLAG

    suspend fun setCleanStreak(v: Int) {
        context.anchorDataStore.edit { it[cleanStreakKey] = v.coerceIn(0, WeekPicture.CLEAN_DAYS_TO_UNFLAG) }
    }

    suspend fun weekFlagged(): Boolean = context.anchorDataStore.data.first()[weekFlaggedKey] ?: false
    suspend fun setWeekFlagged(v: Boolean) { context.anchorDataStore.edit { it[weekFlaggedKey] = v } }

    suspend fun lastReducedDay(): LocalDate? =
        context.anchorDataStore.data.first()[lastReducedDayKey]
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    suspend fun setLastReducedDay(day: LocalDate) {
        context.anchorDataStore.edit { it[lastReducedDayKey] = day.toString() }
    }

    suspend fun recordProposalDismissed(now: Instant = Instant.now()) {
        context.anchorDataStore.edit {
            it[suppressedUntilKey] = now.plusSeconds(SUPPRESS_DAYS * SECONDS_PER_DAY).toEpochMilli()
        }
    }

    suspend fun proposalSuppressedUntil(now: Instant = Instant.now()): Instant? =
        context.anchorDataStore.data.first()[suppressedUntilKey]
            ?.takeIf { it > now.toEpochMilli() }
            ?.let { Instant.ofEpochMilli(it) }

    fun suppressedUntilFlow(): Flow<Instant?> =
        context.anchorDataStore.data.map { prefs ->
            prefs[suppressedUntilKey]?.let { Instant.ofEpochMilli(it) }
        }

    suspend fun sriSlots(): Pair<SriWeekLedger.Slot?, SriWeekLedger.Slot?> {
        val p = context.anchorDataStore.data.first()
        fun slot(
            dayKey: androidx.datastore.preferences.core.Preferences.Key<String>,
            scoreKey: androidx.datastore.preferences.core.Preferences.Key<Int>,
        ): SriWeekLedger.Slot? {
            val day = p[dayKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val score = p[scoreKey] ?: return null
            return SriWeekLedger.Slot(day, score)
        }
        return slot(sriPrevDayKey, sriPrevScoreKey) to slot(sriCurDayKey, sriCurScoreKey)
    }

    suspend fun setSriSlots(prev: SriWeekLedger.Slot?, cur: SriWeekLedger.Slot?) {
        context.anchorDataStore.edit {
            if (prev == null) { it.remove(sriPrevDayKey); it.remove(sriPrevScoreKey) } else {
                it[sriPrevDayKey] = prev.day.toString(); it[sriPrevScoreKey] = prev.score
            }
            if (cur == null) { it.remove(sriCurDayKey); it.remove(sriCurScoreKey) } else {
                it[sriCurDayKey] = cur.day.toString(); it[sriCurScoreKey] = cur.score
            }
        }
    }

    /**
     * Test-only: clears the DataStore. Mirrors the BackupPrefs harness
     * (test/.../backup/BackupPrefsRoundTripFindingTest.kt) so each
     * Robolectric test starts from a fresh install. Not on a hot path.
     */
    suspend fun reset() {
        context.anchorDataStore.edit { it.clear() }
    }

    companion object {
        const val SUPPRESS_DAYS = 14L
        private const val SECONDS_PER_DAY = 24L * 60L * 60L
    }
}
