package org.mindanchor.letters

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.letterDataStore by preferencesDataStore(name = "letters")

/**
 * One letter, stored as a flat string. Same shape as
 * [org.mindanchor.model.MomentLedger]: text rather than JSON
 * because a letter is a single readable blob, and a corrupt
 * letter must cost one day's letter, never the inbox.
 *
 * @property date the day the letter was written FOR. A letter
 * dated 2026-08-10 was the morning of 2026-08-11, but the
 * date the letter is filed under is the day it talks about.
 * @property body the letter's text, 2-3 paragraphs
 */
data class Letter(val date: LocalDate, val body: String)

/**
 * Encodes and decodes the list of letters. The shape is one
 * line per letter, with the date tab-separated from the body
 * — a tab because local dates never contain a tab, and a
 * newline because a letter is always a paragraph of plain
 * text.
 *
 * The wire format is `date\tbody\n` per letter, terminated
 * with a final newline. Empty body means "this line was a
 * placeholder" and is rejected on read.
 */
object LetterLedger {

    fun encode(letters: List<Letter>): String =
        letters.joinToString(separator = "\n", postfix = "\n") {
            "${it.date}\t${it.body.replace("\n", " ")}"
        }

    fun decode(raw: String): List<Letter> = raw.lineSequence()
        .mapNotNull(::decodeLine)
        .sortedBy { it.date }
        .toList()

    private fun decodeLine(line: String): Letter? {
        if (line.isBlank()) return null
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        val date = runCatching { LocalDate.parse(line.substring(0, tab)) }.getOrNull() ?: return null
        val body = line.substring(tab + 1).trim()
        return if (body.isEmpty()) null else Letter(date = date, body = body)
    }
}

/**
 * Letters written for the user, the toggle for the feature, and
 * the time of day the user chose to receive them.
 *
 * Mirrors the shape of [org.mindanchor.model.MomentStore]: a
 * thin DataStore layer over [LetterLedger], which owns the
 * format. The toggle defaults to OFF (the spec is explicit:
 * "Off by default; opt-in"); the time defaults to 08:00 local
 * (the spec's default).
 */
class LetterStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("letters_enabled")
    private val timeKey = stringPreferencesKey("letters_time") // "HH:MM" local
    private val lettersKey = stringPreferencesKey("letters")

    /** Off until asked for, like everything else in this app. */
    val enabled: Flow<Boolean> = context.letterDataStore.data
        .map { it[enabledKey] ?: false }

    /**
     * The hour-of-day the user chose to receive the daily letter.
     * Stored as "HH:MM" so the WorkManager job and the
     * notification can both read it without re-parsing. Defaults
     * to 08:00.
     */
    val time: Flow<Pair<Int, Int>> = context.letterDataStore.data
        .map { prefs ->
            val raw = prefs[timeKey] ?: DEFAULT_TIME
            val parts = raw.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(HOUR_MIN, HOUR_MAX) ?: DEFAULT_HOUR
            val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(MINUTE_MIN, MINUTE_MAX) ?: DEFAULT_MINUTE
            h to m
        }

    /** All letters, oldest first. Empty for a fresh install. */
    val letters: Flow<List<Letter>> = context.letterDataStore.data
        .map { prefs ->
            val raw = prefs[lettersKey].orEmpty()
            LetterLedger.decode(raw)
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.letterDataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        context.letterDataStore.edit {
            it[timeKey] = "%02d:%02d".format(hour.coerceIn(HOUR_MIN, HOUR_MAX), minute.coerceIn(MINUTE_MIN, MINUTE_MAX))
        }
    }

    /**
     * Adds a letter to the inbox. Replaces any existing letter
     * for the same date — a letter is dated, and a week has
     * one letter per day, not several regenerations of the
     * same one.
     */
    suspend fun save(letter: Letter) {
        context.letterDataStore.edit { prefs ->
            val current = LetterLedger.decode(prefs[lettersKey].orEmpty())
            val deduped = current.filter { it.date != letter.date } + letter
            prefs[lettersKey] = LetterLedger.encode(deduped)
        }
    }

    suspend fun delete(date: LocalDate) {
        context.letterDataStore.edit { prefs ->
            val current = LetterLedger.decode(prefs[lettersKey].orEmpty())
            val kept = current.filter { it.date != date }
            prefs[lettersKey] = LetterLedger.encode(kept)
        }
    }

    /**
     * Defaults and bounds for the time-of-day field.
     *
     * [DEFAULT_TIME] is the wire format the DataStore stores;
     * [DEFAULT_HOUR] / [DEFAULT_MINUTE] are the same default in
     * numeric form, exposed so callers building their own
     * [kotlinx.coroutines.flow.StateFlow] over [time] can use the
     * same initial value [LetterStore] falls back to, without
     * re-hardcoding "8" in a second place.
     */
    companion object {
        /** 08:00 local — the spec's default. */
        const val DEFAULT_TIME = "08:00"
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 0
        const val HOUR_MIN = 0
        const val HOUR_MAX = 23
        const val MINUTE_MIN = 0
        const val MINUTE_MAX = 59
    }
}
