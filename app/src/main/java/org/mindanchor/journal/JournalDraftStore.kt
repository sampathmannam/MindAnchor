package org.mindanchor.journal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.journalDraftDataStore by preferencesDataStore(name = "journal_draft")

/**
 * A single in-progress Journal entry draft — the crash/kill recovery net
 * between "the person started typing" and "the person hit save". Title and
 * body are held in separate keys (not one combined blob) so a caller can
 * inspect either independently.
 *
 * **Caller contract** (this class does not enforce or wire any of this
 * itself — that is Task 6's job, building the actual Journal UI):
 * - Call [save] on every accepted change to the in-progress entry (e.g. on
 *   each text-field edit, or debounced).
 * - Call [clear] *only* after [JournalRepository.create] has returned
 *   successfully for this draft. Never clear speculatively before that
 *   call succeeds — a failed `create()` must leave the draft intact so
 *   nothing the person wrote is lost.
 */
class JournalDraftStore(private val context: Context) {

    private val titleKey = stringPreferencesKey("title")
    private val bodyKey = stringPreferencesKey("body")
    private val updatedAtKey = longPreferencesKey("updated_at")

    /**
     * Saves the draft. Title and body are trimmed; the body is capped at
     * [JournalEntry.MAX_BODY_LENGTH] — the same limit [JournalEntry.create]
     * enforces — by truncating rather than rejecting, since a draft is
     * provisional and must never throw on the person's own typing.
     */
    suspend fun save(title: String, body: String, now: Long) {
        context.journalDraftDataStore.edit { prefs ->
            prefs[titleKey] = title.trim()
            prefs[bodyKey] = body.trim().take(JournalEntry.MAX_BODY_LENGTH)
            prefs[updatedAtKey] = now
        }
    }

    /** The saved draft, or null when nothing has been saved (or it was [clear]ed). */
    suspend fun read(): Draft? {
        val prefs = context.journalDraftDataStore.data.first()
        val title = prefs[titleKey]
        val body = prefs[bodyKey]
        val updatedAt = prefs[updatedAtKey]
        if (title == null && body == null && updatedAt == null) return null
        return Draft(title = title.orEmpty(), body = body.orEmpty(), updatedAt = updatedAt ?: 0L)
    }

    /** Removes the draft. See the caller contract on the class doc. */
    suspend fun clear() {
        context.journalDraftDataStore.edit { prefs ->
            prefs.remove(titleKey)
            prefs.remove(bodyKey)
            prefs.remove(updatedAtKey)
        }
    }

    data class Draft(val title: String, val body: String, val updatedAt: Long)
}
