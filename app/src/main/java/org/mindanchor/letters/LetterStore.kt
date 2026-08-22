package org.mindanchor.letters

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.letterDataStore by preferencesDataStore(name = "letters")

/**
 * Where a letter came from. The inbox and the reader both
 * branch on this: AI letters get the "This got me wrong"
 * thumbs-down affordance, user-authored letters do not.
 *
 * Default [AI] so the existing test fixture in
 * [LetterLedgerTest] keeps building `Letter` literals with
 * the two-argument shape; a letter constructed without
 * an explicit source is treated as AI, which is also what
 * the v0.26.2 backward-compat rule says about every letter
 * the old version of the app wrote to disk.
 */
enum class LetterSource { AI, USER }

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
 * @property source whether the letter was written by the
 * on-device model (AI) or by the user themselves (USER). v0.26.2
 * makes the composer the default; AI generation is opt-in via a
 * "Use AI" affordance. Defaults to [LetterSource.AI] so a letter
 * constructed without an explicit source reads as "the model wrote
 * this" — which is what every v0.25.x letter actually was.
 */
data class Letter(
    val date: LocalDate,
    val body: String,
    val source: LetterSource = LetterSource.AI,
)

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
    // v0.25.3-WP-C: the set of letter dates the user has opened
    // (read). Stored as a separate key in the same DataStore so the
    // [LetterLedger] wire format (date + body) is unchanged — a
    // letter can be deleted without losing its read state, and vice
    // versa. v0.25.2's `unreadLetterCount` was a stand-in (count of
    // letters dated after install); this is the real per-letter flag.
    private val readDatesKey = stringSetPreferencesKey("letters_read_dates")
    // v0.26.2: the set of letter dates the user wrote themselves
    // (rather than the model). Stored as a separate key in the same
    // DataStore so the [LetterLedger] wire format is unchanged and
    // a v0.25.x install's letters all read as AI (the empty set
    // default — every prior letter was written by the model).
    private val userDatesKey = stringSetPreferencesKey("letters_user_dates")

    /** Off until asked for, like everything else in this app. */
    val enabled: Flow<Boolean> = context.letterDataStore.data
        .map { it[enabledKey] ?: false }

    /**
     * The hour-of-day the user chose to receive the daily letter.
     * Stored as "HH:MM" so the WorkManager job and the
     * notification can both read it without re-parsing. Defaults
     * to 07:00 (v0.26.2: was 08:00 in v0.25.x).
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
            val decoded = LetterLedger.decode(raw)
            val userDates = prefs[userDatesKey]
                ?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
            // Wire format is unchanged (date + body); the source is
            // read from a parallel key. A v0.25.x install has the
            // set empty, so every pre-existing letter reads as AI —
            // which is what it actually was.
            decoded.map { l ->
                if (l.date in userDates) l.copy(source = LetterSource.USER)
                else l
            }
        }

    /**
     * The set of letter dates the user has opened. v0.25.3-WP-C:
     * replaces the v0.25.2 install-date stand-in for the "unread"
     * badge. Stored as ISO local-date strings because DataStore's
     * `stringSetPreferencesKey` is the only set-shaped preference,
     * and the [LocalDate.parse] round-trip is what the [setRead]
     * writer uses. Corrupt entries (a date that fails to parse) are
     * dropped on read, so a manually-edited prefs file degrades to
     * "unread" rather than crashing.
     */
    val readDates: Flow<Set<LocalDate>> = context.letterDataStore.data
        .map { prefs ->
            prefs[readDatesKey]
                ?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
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
     * Marks [date] as read (`true`) or unread (`false`). Idempotent:
     * setting an already-set value is a no-op. The set is a
     * [Set]<[String]>, so the date is stored as its ISO local-date
     * representation; corrupt strings (e.g. a typo in the prefs
     * file) silently degrade to "not in the set" on read rather than
     * crashing the flow.
     */
    suspend fun setRead(date: LocalDate, read: Boolean) {
        context.letterDataStore.edit { prefs ->
            val current = prefs[readDatesKey] ?: emptySet()
            prefs[readDatesKey] = if (read) current + date.toString() else current - date.toString()
        }
    }

    /**
     * Clears every key in the underlying DataStore. Test-only — used
     * by [LetterReadStoreRoundTripFindingTest]'s `@Before` to isolate
     * tests in the same class (DataStore is a process-wide singleton
     * keyed on the preferences name, so two tests in the same class
     * share state without an explicit reset). Mirrors the
     * `internal suspend fun reset()` shape added to
     * [org.mindanchor.reader.ReaderPrefs] in v0.25.2 (Task 13).
     *
     * `internal` so the test (same module) can call it, but the rest
     * of the app and any third-party callers cannot. Production code
     * does not need to clear the store; a fresh install has no
     * letters and no read state.
     */
    internal suspend fun reset() {
        context.letterDataStore.edit { it.clear() }
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
            // Keep the user-dates set in sync with the letter
            // being saved. A USER letter adds its date; an AI
            // letter is not in the set (the default) and the set
            // entry for it, if any, is dropped — an AI save is the
            // "the user changed their mind" path.
            val currentUser = prefs[userDatesKey] ?: emptySet()
            prefs[userDatesKey] = if (letter.source == LetterSource.USER) {
                currentUser + letter.date.toString()
            } else {
                currentUser - letter.date.toString()
            }
        }
    }

    /**
     * v0.26.2: save a user-authored letter. Body is whatever
     * the user wrote in the composer; date defaults to today.
     * The letter is filed as [LetterSource.USER] so the inbox
     * does not show the "This got me wrong" thumbs-down on it
     * (a user-authored letter cannot be "wrong about the user").
     */
    suspend fun saveUserLetter(date: LocalDate, body: String) {
        if (body.isBlank()) return
        save(Letter(date = date, body = body, source = LetterSource.USER))
    }

    suspend fun delete(date: LocalDate) {
        context.letterDataStore.edit { prefs ->
            val current = LetterLedger.decode(prefs[lettersKey].orEmpty())
            val kept = current.filter { it.date != date }
            prefs[lettersKey] = LetterLedger.encode(kept)
            // A delete also drops the user-dates entry, so a
            // re-save for the same date is not silently treated
            // as user-authored because the old set entry was
            // still there.
            val currentUser = prefs[userDatesKey] ?: emptySet()
            prefs[userDatesKey] = currentUser - date.toString()
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
     * re-hardcoding "7" in a second place.
     */
    companion object {
        /** 07:00 local — v0.26.2 default (was 08:00 in v0.25.x). */
        const val DEFAULT_TIME = "07:00"
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        const val HOUR_MIN = 0
        const val HOUR_MAX = 23
        const val MINUTE_MIN = 0
        const val MINUTE_MAX = 59
    }
}
