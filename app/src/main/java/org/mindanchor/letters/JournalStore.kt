package org.mindanchor.letters

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.journalDataStore by preferencesDataStore(name = "journal")

/**
 * v0.28+ (Phase 3 G-22 / G-23 / G-29 / G-8) — the
 * journal store for the protective-layer entries
 * (BA / DEAR MAN / gratitude / expressive writing).
 *
 * These are not letters — they are journal entries
 * the user types into the launcher. Mixing them with
 * the daily letter's `Letter` data class would couple
 * the LLM context to a UI kind (the existing
 * `LetterLedger` codec has no `type` field, by
 * design), and would cause same-day collisions: a BA
 * entry on Monday would overwrite Monday's generated
 * daily letter, and a gratitude entry on Monday
 * would overwrite the BA entry. CodeRabbit review
 * 2026-08-24 of PR #38 surfaced this.
 *
 * The fix: each entry type gets its own key in a
 * separate DataStore. The key is `<type>:<iso-date>`;
 * the value is the entry body. The store is a flat
 * string map, deliberately append-capable: an entry
 * on a given date never overwrites another entry on
 * the same date, and the daily letter is unaffected.
 *
 * ## Why a separate DataStore (not a new column on
 *   `letters`)
 *
 * The `letters` DataStore is the daily letter's home;
 * the codec has been the wire format since v0.25.0.
 * Mixing journal entries in would force a codec bump
 * and a migration. The journal is small, append-only,
 * and never read by the letter inbox. A separate
 * store is the right shape.
 */
class JournalStore(private val context: Context) {

    /**
     * The kinds of entries this store holds. The
     * `kind` is the prefix in the [stringPreferencesKey];
     * a same-date entry under a different kind does not
     * collide with this one.
     */
    enum class Kind(val tag: String) {
        BA("ba"),
        DEAR_MAN("dear-man"),
        GRATITUDE("gratitude"),
        EXPRESSIVE_WRITING("expressive-writing"),
    }

    private fun key(kind: Kind, date: LocalDate): String = "${kind.tag}:$date"

    /**
     * Saves the entry's body for (kind, date). The same
     * (kind, date) is replaced (you do not "amend" a BA
     * entry — you replace it). Different kinds on the
     * same date are independent.
     */
    suspend fun save(kind: Kind, date: LocalDate, body: String) {
        val clean = body.trim()
        if (clean.isEmpty()) return
        context.journalDataStore.edit { prefs ->
            prefs[stringPreferencesKey(key(kind, date))] = clean
        }
    }

    /**
     * All entries of the given kind, oldest first.
     * Used by the [org.mindanchor.launcher.JournalScreen]
     * (a future follow-up). For now, callers use
     * [readOne] for the specific (kind, date) they need.
     */
    val entries: Flow<List<Entry>> = context.journalDataStore.data.map { prefs ->
        Kind.entries.flatMap { kind ->
            (0L..365L).mapNotNull { daysAgo ->
                val date = LocalDate.now().minusDays(daysAgo)
                val raw = prefs[stringPreferencesKey(key(kind, date))] ?: return@mapNotNull null
                Entry(kind = kind, date = date, body = raw)
            }
        }
    }

    /**
     * Every entry ever saved to this store, regardless of age — unlike
     * [entries], which only probes the latest 365 days for the launcher's
     * recent-entries view. This is a one-shot read: it exists for the Task
     * 4 legacy importer, which consumes the whole store exactly once.
     *
     * Keys are `<kind-tag>:<ISO-date>`; the split is on the *last* colon,
     * since tags like `dear-man`/`expressive-writing` contain hyphens but
     * no colons. A key that doesn't match a known tag, or whose date half
     * doesn't parse, is skipped rather than thrown — the DataStore is a
     * flat string map and could in principle hold something unrelated.
     * Results are sorted by date, then kind.
     */
    suspend fun allEntries(): List<Entry> {
        val prefs = context.journalDataStore.data.first()
        return prefs.asMap().mapNotNull { (prefKey, rawValue) ->
            val key = prefKey.name
            val separator = key.lastIndexOf(':')
            if (separator <= 0) return@mapNotNull null
            val tag = key.substring(0, separator)
            val dateText = key.substring(separator + 1)
            val kind = Kind.entries.find { it.tag == tag } ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@mapNotNull null
            val body = rawValue as? String ?: return@mapNotNull null
            Entry(kind = kind, date = date, body = body)
        }.sortedWith(compareBy({ it.date }, { it.kind }))
    }

    /**
     * The body for a specific (kind, date), or null when
     * nothing was saved on that date.
     */
    suspend fun readOne(kind: Kind, date: LocalDate): String? {
        // .first() suspends to the current snapshot;
        // this is a one-shot read for the home surface,
        // not a long-lived collector. The DataStore
        // guarantees the snapshot is consistent.
        val prefs = context.journalDataStore.data.first()
        return prefs[stringPreferencesKey(key(kind, date))]
    }

    data class Entry(
        val kind: Kind,
        val date: LocalDate,
        val body: String,
    )
}
