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
