package org.mindanchor.letters

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mindanchor.continuity.ContinuityWorkScheduler

private val Context.letterDataStore by preferencesDataStore(name = "letters")

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
        ContinuityWorkScheduler.requestCheckpoint(context)
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
        }
        ContinuityWorkScheduler.requestCheckpoint(context)
    }

    suspend fun delete(date: LocalDate) {
        context.letterDataStore.edit { prefs ->
            val current = LetterLedger.decode(prefs[lettersKey].orEmpty())
            val kept = current.filter { it.date != date }
            prefs[lettersKey] = LetterLedger.encode(kept)
        }
        ContinuityWorkScheduler.requestCheckpoint(context)
    }

    /**
     * Adds a behavioural-activation weekly-prompt entry for
     * [date]. The entry is stored as a [Letter] whose body
     * starts with the [BA_BODY_PREFIX] marker so the reader
     * UI can render it with the BA-specific affordances
     * (mastery + pleasure label, completion checkbox) rather
     * than the standard letter treatment.
     *
     * ## Wire-format note
     *
     * The codec ([LetterLedger]) has no `type` field — the
     * 5 nullable metadata fields added in v0.25.7 (provider,
     * model, promptTokens, completionTokens, durationMs)
     * are all LLM-specific and repurposing one of them for
     * a UI kind would couple the LLM context to the BA
     * context. A body prefix is the smallest change that
     * keeps the codec stable and lets the reader UI
     * detect BA entries from the [Letter.body] prefix.
     *
     * ## Evidence anchor
     *
     * Dimidjian S, Hollon SD, Dobson KS, et al. (2006) BA
     * RCT, N=241. The mastery/pleasure pair is from
     * Lewinsohn et al. 1976 (the original BA
     * activity-scheduling protocol). v0.26+ (Phase 1
     * G-22).
     */
    suspend fun saveBaEntry(date: LocalDate, mastery: String, pleasure: String) {
        val cleanMastery = mastery.trim().take(BA_MAX_FIELD_LEN)
        val cleanPleasure = pleasure.trim().take(BA_MAX_FIELD_LEN)
        if (cleanMastery.isEmpty() && cleanPleasure.isEmpty()) return
        val body = "$BA_BODY_PREFIX$cleanMastery$BA_FIELD_SEP$cleanPleasure"
        save(Letter(date = date, body = body, provider = BA_PROVIDER_TAG))
    }

    /**
     * Decodes a [Letter] into a BA mastery/pleasure pair,
     * or null when the letter is not a BA entry. The
     * detection is the [BA_BODY_PREFIX] on [Letter.body];
     * the LLM-provider tags never produce that prefix,
     * so the two kinds are disjoint on disk.
     */
    fun readBaEntry(letter: Letter): BaEntry? {
        if (!letter.body.startsWith(BA_BODY_PREFIX)) return null
        val tail = letter.body.removePrefix(BA_BODY_PREFIX)
        val parts = tail.split(BA_FIELD_SEP, limit = 2)
        return BaEntry(
            date = letter.date,
            mastery = parts.getOrNull(0).orEmpty(),
            pleasure = parts.getOrNull(1).orEmpty(),
        )
    }

    data class BaEntry(
        val date: LocalDate,
        val mastery: String,
        val pleasure: String,
    )

    companion object {
        /**
         * The body prefix that marks a letter as a
         * behavioural-activation entry. The literal
         * "BA:" is short enough to read in the letter
         * list and unique enough that no canned or
         * LLM-generated body collides with it.
         */
        const val BA_BODY_PREFIX = "BA:"

        /**
         * The field separator between the mastery
         * and pleasure strings. A pipe character —
         * not a tab — keeps the LLM body pipeline
         * (which uses tab for the metadata columns)
         * untouched.
         */
        const val BA_FIELD_SEP = "|"

        /**
         * The provider tag for BA entries. Lets
         * the reader UI distinguish them from
         * LLM-driven letters in a single
         * [Letter.provider] check, the same way
         * "groq" identifies the Groq cloud LLM.
         * The string is not an LLM provider; the
         * reader uses it as a kind-of-entry
         * marker, not as a remote source.
         */
        const val BA_PROVIDER_TAG = "ba-prompt"

        /**
         * The per-field length cap. 280 chars
         * matches Twitter's old cap and is enough
         * for one or two sentences per field.
         * Trimmed at the call site; longer input
         * is truncated, not rejected.
         */
        const val BA_MAX_FIELD_LEN = 280

        /** 08:00 local — the spec's default. */
        const val DEFAULT_TIME = "08:00"
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 0
        const val HOUR_MIN = 0
        const val HOUR_MAX = 23
        const val MINUTE_MIN = 0
        const val MINUTE_MAX = 59
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
}

/**
 * Task 7 — merges a restored snapshot's letters into the local store.
 * For each incoming letter, the local record wins outright when one
 * already exists for that date (the safer, additive-restore behaviour);
 * only a date with no local letter at all gets the incoming one. Read
 * dates are unioned in, never removed — "read" is monotonic, so restoring
 * an old snapshot can only add read marks, never take one away.
 *
 * Idempotent: a date already covered locally (whether from a prior run of
 * this merge or original local data) is never re-added or overwritten,
 * and [setRead] is itself a no-op when the date is already marked read.
 */
suspend fun LetterStore.mergeRestored(incoming: List<Letter>, incomingReadDates: Set<LocalDate>) {
    val localDates = letters.first().map { it.date }.toSet()
    incoming.filter { it.date !in localDates }.forEach { save(it) }
    incomingReadDates.forEach { date -> setRead(date, read = true) }
}
