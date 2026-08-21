/*
 * v0.66.0 (DBT-grounded journal) — Task 2.
 *
 * The DataStore-backed persistence for the per-day diary card defined
 * in [DiaryCardEntry]. One entry per `LocalDate`, keyed by epoch day
 * under the `"diary_card"` preferences file. The diary list surface
 * (Task 3) reads from it, the skill nudge (Task 5) reads recent
 * cards to pick a suggestion, and the PDF export (Task 8) iterates a
 * date range via [entriesInRange].
 *
 * ## Why a per-date key (`entry:<epochDay>`) rather than one big blob
 *
 * The natural alternative is a single key holding a `List<DiaryCardEntry>`
 * (or a JSON blob). That is what the v0.28.0 `support.DiaryCardPrefs`
 * did, and it has two failure modes this design avoids:
 *
 *  1. **Write contention**: every keystroke on a single day would
 *     re-serialise the entire history, not just the day being edited.
 *     With per-date keys, a write to 2026-08-21 only touches one
 *     preference. The DataStore write is async so it does not block
 *     the main thread either way, but rewriting the whole history
 *     every save makes the write footprint proportional to lifetime
 *     diary usage, not to "what changed today".
 *  2. **Partial corruption**: a torn write to a single-key blob can
 *     leave the whole history unrecoverable. A per-date key makes
 *     the blast radius one day.
 *
 * The cost is the range query in [entriesInRange] — it has to read
 * every key, decode, and filter. For a v0.66.0 user with a year of
 * cards that is 365 strings; for a v0.66.0 user with a month of
 * cards it is 30. The PDF export runs once on demand, not on every
 * keystroke, so the O(n) read is fine.
 *
 * ## Why hand-rolled pipe-delimited rather than `kotlinx.serialization`
 *
 * The project has `kotlinx-serialization-json` on the compile classpath
 * (used by the v0.28.0 support-layer `DiaryCardPrefs` and a few other
 * config payloads), so swapping the encoding is a one-import change.
 * It is deliberately NOT swapped here:
 *
 *  - The fields are 5 flat scalars (epoch day, three urge ints, a
 *    `SkillId` name, a `Mood` list) — nothing nested, nothing
 *    polymorphic, nothing that would benefit from a real schema.
 *  - `kotlinx-serialization-json` would pull a reflection-shaped
 *    `@Serializable` annotation onto the internal `DiaryCardEntry`
 *    data class, which is exactly the kind of "leaks the format into
 *    the data shape" coupling the v0.66.0 plan tries to keep out.
 *  - The encoding is the wire format of one DataStore; no other code
 *    reads it. A bug in `encode` or `decode` is caught by the
 *    round-trip test on the first run.
 *
 * The `kotlinx.serialization` swap is a one-commit change if a later
 * task needs the encoding to be shared with another store.
 */
package org.mindanchor.journal.diary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mindanchor.journal.Mood
import org.mindanchor.journal.skills.SkillId
import java.time.LocalDate

private val Context.diaryCardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "diary_card",
)

/**
 * Per-day diary card persistence. One key per day; one
 * pipe-delimited string per value.
 *
 * `internal` to match the visibility of [DiaryCardEntry] and
 * [Urges] (both `internal` — see the v0.66.0 Task 1 report for why
 * `Mood` is `internal` and the diary classes track it). Production
 * code outside the diary package does not need to read the store
 * directly; the diary list / nudge / export surfaces all live in
 * this package.
 */
internal class DiaryCardPrefs(private val context: Context) {

    private val store get() = context.diaryCardDataStore

    /**
     * Flow of the entry for [date], or null if nothing is
     * recorded. Emits a new value on every write. The list view
     * uses the null to render the `DiaryCardEntry.empty(date)`
     * placeholder, NOT a zero-`Urges` triple — "did not check"
     * must not look like "checked and felt nothing".
     */
    fun entryFor(date: LocalDate): Flow<DiaryCardEntry?> =
        store.data.map { prefs ->
            prefs[keysForDate(date)]?.let { decode(it) }
        }

    /**
     * Persist [entry] under its date's key. Overwrites any prior
     * entry for the same date — the card is per-day by design.
     */
    suspend fun setEntry(entry: DiaryCardEntry) {
        store.edit { prefs ->
            prefs[keysForDate(entry.date)] = encode(entry)
        }
    }

    /**
     * All entries in `[from, to]` (both inclusive), oldest first.
     * Reads the whole store, decodes every value, filters by date.
     * Intended for the PDF export (Task 8) which fires on demand,
     * not for hot paths.
     */
    suspend fun entriesInRange(from: LocalDate, to: LocalDate): List<DiaryCardEntry> {
        val all = store.data.first()
        return all.asMap()
            .mapNotNull { (k, v) ->
                if (k.name.startsWith(KEY_PREFIX)) decode(v as String) else null
            }
            .filter { it.date in from..to }
            .sortedBy { it.date }
    }

    /**
     * Test-only: drop every key. The round-trip test's
     * `@Before` calls this to isolate tests in the same class
     * (DataStore is a process-wide singleton keyed on the
     * preferences name, so two tests share state without an
     * explicit reset). `internal` so the test in the same module
     * can call it; production code never clears the store.
     */
    internal suspend fun reset() {
        store.edit { it.clear() }
    }

    private fun keysForDate(date: LocalDate) =
        stringPreferencesKey("$KEY_PREFIX${date.toEpochDay()}")

    /**
     * Pipe-delimited encoding. Fields are emitted in the order:
     *   - `d=<epochDay>`             always
     *   - `u=<nssi>,<suicidal>,<d>`  when urges is non-null
     *   - `e=<Mood>,<Mood>,...`      when emotions is non-empty
     *   - `s=<SkillId>`              when a skill was used
     *   - `x=1`                      when exportedToTherapist is true
     *
     * The flag is keyed by presence: omitting a field means
     * "use the default", not "the default is the empty string".
     * This makes the encoding resilient to future additions —
     * a v0.67 reader that does not know a v0.68 field just
     * ignores it, instead of failing to decode.
     */
    private fun encode(e: DiaryCardEntry): String = buildString {
        append("d=").append(e.date.toEpochDay())
        e.urges?.let { append("|u=").append("${it.nssi},${it.suicidal},${it.dissociation}") }
        if (e.emotions.isNotEmpty()) {
            append("|e=").append(e.emotions.joinToString(",") { it.name })
        }
        e.skillUsed?.let { append("|s=").append(it.name) }
        if (e.exportedToTherapist) append("|x=1")
    }

    /**
     * Inverse of [encode]. Returns null on any parse error
     * (unknown Mood name, unknown SkillId name, missing `d=`,
     * non-integer urge, out-of-range urge that survived a buggy
     * writer). A corrupt entry is treated as missing — the
     * round-trip test catches a buggy writer before it can ship.
     */
    private fun decode(s: String): DiaryCardEntry? = runCatching {
        val parts = s.split("|").associate {
            it.substringBefore("=") to it.substringAfter("=")
        }
        val date = LocalDate.ofEpochDay(parts["d"]!!.toLong())
        val urges = parts["u"]?.split(",")?.let {
            Urges(
                nssi = it[0].toInt(),
                suicidal = it[1].toInt(),
                dissociation = it[2].toInt(),
            )
        }
        val emotions = parts["e"]
            ?.split(",")
            ?.mapNotNull { name -> runCatching { Mood.valueOf(name) }.getOrNull() }
            ?: emptyList()
        val skill = parts["s"]?.let { runCatching { SkillId.valueOf(it) }.getOrNull() }
        val exported = parts["x"] == "1"
        DiaryCardEntry(date, urges, emotions, skill, exported)
    }.getOrNull()

    private companion object {
        const val KEY_PREFIX = "entry:"
    }
}
