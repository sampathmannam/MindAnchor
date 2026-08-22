/*
 * v0.66.0 (DBT-grounded journal) — Task 5.
 *
 * The DataStore-backed persistence for the "which skill did the user
 * use on which date" tracking that powers the skill-nudge reflection
 * (Task 9) and the PDF export (Task 8). One entry per `LocalDate`,
 * keyed by epoch day under the `"skills"` preferences file. The value
 * is the `SkillId.name` — a plain string, the canonical enum-name
 * round-trip, no `kotlinx.serialization` shape to leak.
 *
 * ## Why a per-date key (`used:<epochDay>`) rather than one big blob
 *
 * The natural alternative is a single key holding a `Map<LocalDate,
 * SkillId>` (or a JSON blob, the way v0.28.0 `support.DiaryCardPrefs`
 * did for a similar shape). That is rejected for two reasons that
 * mirror [DiaryCardPrefs]'s rationale:
 *
 *  1. **Write contention**: every `markUsed` would re-serialise the
 *     whole history, not just the day being recorded. The DataStore
 *     write is async so it does not block the main thread either way,
 *     but rewriting the whole history every save makes the write
 *     footprint proportional to lifetime skill usage, not to "what
 *     changed today". A per-date key keeps the write to one
 *     preference.
 *  2. **Partial corruption blast radius**: a torn write to a
 *     single-key blob can leave the whole history unrecoverable. A
 *     per-date key makes the blast radius one day — the next day
 *     still records correctly.
 *
 * The cost is the range query in [entriesInRange] — it has to read
 * every key, decode, and filter. For a v0.66.0 user with a year of
 * skill use that is 365 strings; for a v0.66.0 user with a month it
 * is 30. The PDF export runs once on demand, not on every keystroke,
 * so the O(n) read is fine. The same trade-off [DiaryCardPrefs]
 * makes, and the same justification holds.
 *
 * ## Why hand-rolled `SkillId.name` rather than an index or hash
 *
 * The five `SkillId` values are stable (Task 6, `SkillsLibrary`) and
 * adding a sixth is a spec change. Storing the enum name as a string
 * means a future addition of, say, `GIVE`, just means a new enum
 * case — old writes decode fine because unknown names are treated
 * as missing. Storing an `Int` ordinal would couple the on-disk
 * format to the enum declaration order and break backward
 * compatibility on the first reorder.
 *
 * ## Why no separate "time of day" key
 *
 * Time-of-day patterns (e.g. "TIPP is used at 3pm more often than
 * at 3am") are inferred at read time by the skill-nudge surface
 * (Task 9) from the entries in range, not stored. The reason is the
 * same one the v0.66.0 plan uses for the v0.28.0 `DiaryCardEntry`:
 * inference is cheap (we already have the date), the alternative is a
 * second `Map<LocalTime, SkillId>` key that doubles the write
 * footprint and the decode cost, and the user-facing surface does
 * not need second-level resolution for the "what did you use"
 * reflection. The Task 9 surface filters by hour-of-day on read.
 *
 * ## Why `internal` on the top-level extension, no `reset()` on the class
 *
 * The top-level `Context.skillsDataStore` is `internal` (not
 * `private`) so the unit test in
 * `app/src/test/java/org/mindanchor/journal/skills/SkillsPrefsTest.kt`
 * can call `context.skillsDataStore.edit { it.clear() }` to isolate
 * tests in the same class. The test source set is the same Gradle
 * module as `main`, so `internal` is in scope. The production class
 * has no `reset()` method on purpose — `internal` on a production
 * method is module-wide callable, which lets any same-module code
 * wipe the user's skill history.
 *
 * ## DataStore name collision check
 *
 * Verified at Task 5 implementation time: the only existing
 * `preferencesDataStore(name = "...")` in `app/src/main` matching
 * `skills` is this new file. The v0.28.0 support layer has no
 * `Skills` store. The delegate is process-wide per file name — two
 * `private val` extensions in different packages with the same name
 * would throw `IllegalStateException: There are multiple DataStores
 * active for the same file` the moment the live app touched both
 * stores.
 *
 * ## The KEY prefix `used:`
 *
 * The prefix separates skill-used keys from any future keys the
 * same store might need (e.g. a future `pref:show_nudge=true` for a
 * UI preference). [entriesInRange] filters on `k.name.startsWith(
 * "used:")` so a future non-date key in the same store would be
 * silently ignored by the range query, not mis-decoded as a date.
 */
package org.mindanchor.journal.skills

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

// The DataStore name MUST NOT collide with any other
// `preferencesDataStore(name = "...")` in the project. Verified at
// Task 5 implementation time: the only existing names are
// `chain_store`, `sms_tone_check`, `backup_prefs`,
// `smartwatch_connectors`, `appearance`, `checkins`, `bpd_profile`,
// `friction`, `notes`, `sunset`, `notifications`, `launcher`,
// `letters`, `measured`, `ifs_store`, `coros_bridge_data`,
// `wellness`, `ppg_sessions`, `journal_draft`, `diary_card_v66`,
// `reader_prefs`, `safety_plan`, `polar_bridge_data`, `report`,
// `ema`, `onboarding`, `inferred`, `setup_wizard`, `diary_card`,
// `receipts`, `act_values`, plus the v0.28.0 support layer's
// `support.*` set. No `skills`.
// The delegate is process-wide per file name — two `private val`
// extensions in different packages with the same name would throw
// `IllegalStateException: There are multiple DataStores active for
// the same file` the moment the live app touched both stores.
//
// `internal` (not `private`) so the unit test can call
// `context.skillsDataStore.edit { it.clear() }` to isolate tests
// in the same class. See the file header for the full rationale.
internal val Context.skillsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "skills",
)

/**
 * Per-date skill-used persistence. One key per day; the value is
 * the `SkillId.name` of the skill the user recorded for that day.
 *
 * `public` to match [SkillId]'s visibility. [SkillId] is
 * deliberately public (it is the public enum the picker UI surfaces
 * in the v0.66.0 plan), so the prefs class that reads / writes it
 * must be reachable across the package boundary. Only the top-level
 * [Context.skillsDataStore] extension is `internal` (see the file
 * header for the test-only-extensions rationale).
 *
 * The DataStore is process-wide per file name; the per-test reset
 * in [SkillsPrefsTest] wipes via the `internal` extension directly,
 * not via a production `reset()` method.
 */
class SkillsPrefs(private val context: Context) {

    private val store get() = context.skillsDataStore

    /**
     * Flow of the skill used on [date], or null if no skill was
     * recorded. Emits a new value on every write. The skill-nudge
     * surface (Task 9) uses the null to render the
     * "no skill recorded for this day" placeholder, NOT a default
     * `TIPP` — picking a default would silently lie to the user
     * about which skill they used.
     */
    fun usedOn(date: LocalDate): Flow<SkillId?> =
        store.data.map { prefs ->
            // `SkillId.valueOf` throws on an unknown name (a
            // future-added enum case is fine; a typo or a value
            // written by a buggy v0.28.0 layer is not). `runCatching`
            // keeps the read resilient — a corrupt entry is treated
            // as missing, the same trade-off [DiaryCardPrefs] makes.
            prefs[keyForDate(date)]?.let {
                runCatching { SkillId.valueOf(it) }.getOrNull()
            }
        }

    /**
     * Persist that [skill] was used on [date]. Overwrites any prior
     * entry for the same date — the tracking is per-day, and a user
     * can re-record a different skill for the same day (a real
     * use case: a person tries TIPP, then DEAR MAN, then
     * 3-Minute Breathing Space, and the journal records the most
     * recent at the time the entry was saved).
     */
    suspend fun markUsed(skill: SkillId, date: LocalDate) {
        store.edit { prefs ->
            prefs[keyForDate(date)] = skill.name
        }
    }

    /**
     * All (date, skill) pairs in `[from, to]` (both inclusive),
     * oldest first. Reads the whole store, decodes every value
     * whose key starts with `used:`, filters by date. Intended
     * for the PDF export (Task 8), which fires on demand, not for
     * hot paths.
     *
     * A key that does not parse (unknown `SkillId` name, unparseable
     * epoch day) is silently dropped — the same "corrupt entry is
     * missing" trade-off [DiaryCardPrefs.entriesInRange] makes.
     */
    suspend fun entriesInRange(from: LocalDate, to: LocalDate): List<Pair<LocalDate, SkillId>> {
        val all = store.data.first()
        return all.asMap()
            .mapNotNull { (k, v) ->
                if (k.name.startsWith(KEY_PREFIX)) {
                    val date = runCatching {
                        LocalDate.ofEpochDay(k.name.removePrefix(KEY_PREFIX).toLong())
                    }.getOrNull()
                    val skill = runCatching { SkillId.valueOf(v as String) }.getOrNull()
                    if (date != null && skill != null && date in from..to) date to skill else null
                } else null
            }
            .sortedBy { it.first }
    }

    /**
     * NOTE: there is deliberately no `reset()` on this class. The
     * round-trip test reaches the [Context.skillsDataStore]
     * extension directly to clear the store between tests, so the
     * production surface stays minimal. A `reset()` here would be
     * `internal` (module-wide) which lets any same-module code
     * wipe the user's skill history — a real risk, not a
     * theoretical one, given the project's `internal` test helpers
     * and the `backup` package's `internal` reset methods.
     */

    private fun keyForDate(date: LocalDate) =
        stringPreferencesKey("$KEY_PREFIX${date.toEpochDay()}")

    private companion object {
        const val KEY_PREFIX = "used:"
    }
}
