@file:Suppress("MagicNumber")
package org.mindanchor.support

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.diaryCardStore by preferencesDataStore(name = "diary_card")

/**
 * v0.28.0: persistence for the DBT diary card.
 *
 * One entry per day, keyed by ISO date. Stored as a single
 * JSON string per date under [ENTRY_KEY_<date>] in a
 * preferencesDataStore. The "this week" view reads the last 7
 * dates.
 *
 * ## What is and is not stored
 *
 * The card stores the user's own words (urge / emotion /
 * intensity / skill / outcome). It does NOT store a score, a
 * comparison, a chart series, or anything that would imply an
 * interpretation the project is not allowed to make. The
 * intensity is a single int 0–10 (DBT diary card convention).
 */
class DiaryCardPrefs(private val context: Context) {

    private fun keyFor(date: LocalDate): String = "diary_card_${date}"

    suspend fun save(date: LocalDate, entry: DiaryCardEntry) {
        val json = Json.encodeToString(DiaryCardEntry.serializer(), entry)
        context.diaryCardStore.edit { prefs ->
            prefs[stringPreferencesKey(keyFor(date))] = json
        }
    }

    suspend fun load(date: LocalDate): DiaryCardEntry? {
        val prefs = context.diaryCardStore.data.first()
        val raw = prefs[stringPreferencesKey(keyFor(date))] ?: return null
        return runCatching { Json.decodeFromString(DiaryCardEntry.serializer(), raw) }.getOrNull()
    }

    /** The last 7 days, oldest first. Days with no entry are skipped. */
    suspend fun lastWeek(): List<Pair<LocalDate, DiaryCardEntry>> {
        val prefs = context.diaryCardStore.data.first()
        val today = LocalDate.now()
        val dates = (0..6).map { today.minusDays(it.toLong()) }
        return dates.reversed().mapNotNull { date ->
            val raw = prefs[stringPreferencesKey(keyFor(date))] ?: return@mapNotNull null
            val entry = runCatching { Json.decodeFromString(DiaryCardEntry.serializer(), raw) }
                .getOrNull() ?: return@mapNotNull null
            date to entry
        }
    }

    /** Live stream of the last 7 days, for the "this week" surface. */
    fun lastWeekFlow(): Flow<List<Pair<LocalDate, DiaryCardEntry>>> =
        context.diaryCardStore.data.map {
            val today = LocalDate.now()
            val dates = (0..6).map { today.minusDays(it.toLong()) }
            dates.reversed().mapNotNull { date ->
                val raw = it[stringPreferencesKey(keyFor(date))] ?: return@mapNotNull null
                val entry = runCatching { Json.decodeFromString(DiaryCardEntry.serializer(), raw) }
                    .getOrNull() ?: return@mapNotNull null
                date to entry
            }
        }
}

/**
 * v0.28.0: one diary card entry. All fields are nullable
 * because the card is saved on a single Save tap — any field
 * the user has not yet filled is null. The card is per-day;
 * the date is the key, not a field.
 */
@Serializable
data class DiaryCardEntry(
    val urge: String? = null,
    val emotion: String? = null,
    val intensity: Int? = null,
    val skill: String? = null,
    val outcome: String? = null,
)
