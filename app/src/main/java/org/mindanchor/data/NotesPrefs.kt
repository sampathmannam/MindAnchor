package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.model.Note
import org.mindanchor.model.NoteStore
import org.mindanchor.model.NotesState

/**
 * The on-device notes DataStore. v0.20.1 round 5
 * (docs/research/26-notes-and-check-in.md).
 *
 * The DataStore is *separate* from the friction
 * DataStore: notes are user-authored text, not
 * friction configuration. Mixing them would
 * conflate "did the user write a note" with
 * "did the user change a friction setting",
 * and the sealed-codecs HMAC layer would
 * invalidate the friction data on any note edit.
 *
 * The data is plain text on disk; the sealed-codecs
 * wrapper on work/codec-hmac adds the HMAC layer
 * (item D threat model). The plaintext DataStore
 * is harmless on its own — the SealedCodecs
 * wrapper is the integrity boundary.
 */
private val Context.notesDataStore by preferencesDataStore(name = "notes")

/**
 * The notes prefs. Thin DataStore layer over
 * [NoteStore], which carries all the format
 * knowledge. Same pattern as
 * [org.mindanchor.model.MomentStore] / [MomentLedger].
 */
class NotesPrefs(private val context: Context) {

    private val notesKey = stringPreferencesKey("notes")

    /**
     * The user's notes, in storage order. The list
     * view sorts for display via
     * [NoteStore.sortedForList].
     */
    val notes: Flow<NotesState> =
        context.notesDataStore.data.map {
            NotesState(NoteStore.decode(it[notesKey].orEmpty()))
        }

    /**
     * Add a new note. The note is appended to the
     * end of the list; the list view will sort it
     * to the right place on display.
     */
    suspend fun add(note: Note) {
        context.notesDataStore.edit { prefs ->
            val current = NoteStore.decode(prefs[notesKey].orEmpty())
            prefs[notesKey] = NoteStore.encode(current + note)
        }
    }

    /**
     * Edit an existing note. The note with the
     * matching id is replaced; updatedAt is
     * bumped to [editTimestamp] (default: now).
     */
    suspend fun edit(id: Long, body: String, editTimestamp: Long = System.currentTimeMillis()) {
        context.notesDataStore.edit { prefs ->
            val current = NoteStore.decode(prefs[notesKey].orEmpty())
            val next = current.map {
                if (it.id == id) it.copy(body = body, updatedAt = editTimestamp) else it
            }
            prefs[notesKey] = NoteStore.encode(next)
        }
    }

    /**
     * Toggle the pinned state of a note.
     */
    suspend fun togglePinned(id: Long) {
        context.notesDataStore.edit { prefs ->
            val current = NoteStore.decode(prefs[notesKey].orEmpty())
            val next = current.map {
                if (it.id == id) it.copy(pinned = !it.pinned) else it
            }
            prefs[notesKey] = NoteStore.encode(next)
        }
    }

    /**
     * Delete a note. A no-op if the id is not in
     * the store.
     */
    suspend fun delete(id: Long) {
        context.notesDataStore.edit { prefs ->
            val current = NoteStore.decode(prefs[notesKey].orEmpty())
            prefs[notesKey] = NoteStore.encode(current.filter { it.id != id })
        }
    }
}
