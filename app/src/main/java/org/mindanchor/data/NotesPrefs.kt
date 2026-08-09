package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.friction.SealedCodecs
import org.mindanchor.model.Note
import org.mindanchor.model.NotesState

/**
 * The on-device notes DataStore. v0.20.1 round 5
 * (docs/research/26-notes-and-check-in.md).
 *
 * The DataStore is *separate* from the friction
 * DataStore: notes are user-authored text, not
 * friction configuration. Mixing them would
 * conflate "did the user write a note" with
 * "did the user change a friction setting", and
 * the sealed-codecs HMAC layer would invalidate
 * the friction data on any note edit.
 *
 * The on-disk form is sealed with
 * [SealedCodecs.encodeNotes] / [SealedCodecs.decodeNotes]
 * (HMAC-SHA256 tag, codecId "notes"). The seal
 * is the threat-model boundary: a motivated user
 * with root cannot rewrite the on-disk notes
 * without invalidating the MAC. A v0.20.0
 * plaintext form is *not* migrated; the first
 * write produces a sealed record. This is the
 * same fail-closed migration policy as the
 * other codecs (per-app session-length, gate
 * tallies, etc.).
 */
private val Context.notesDataStore by preferencesDataStore(name = "notes")

/**
 * The notes prefs. Thin DataStore layer over
 * [org.mindanchor.model.NoteStore], which carries
 * all the format knowledge. The integrity layer
 * (the HMAC tag) lives in [SealedCodecs].
 */
class NotesPrefs(private val context: Context) {

    private val notesKey = stringPreferencesKey("notes")

    /**
     * The user's notes, in storage order. The list
     * view sorts for display via
     * [org.mindanchor.model.NoteStore.sortedForList].
     */
    val notes: Flow<NotesState> =
        context.notesDataStore.data.map {
            SealedCodecs.decodeNotes(it[notesKey].orEmpty())
        }

    /**
     * Add a new note. The note is appended to the
     * end of the list; the list view will sort it
     * to the right place on display.
     */
    suspend fun add(note: Note) {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.add(note)
            prefs[notesKey] = SealedCodecs.encodeNotes(next)
        }
    }

    /**
     * Edit an existing note. The note with the
     * matching id is replaced; updatedAt is
     * bumped to [editTimestamp] (default: now).
     */
    suspend fun edit(id: Long, body: String, editTimestamp: Long = System.currentTimeMillis()) {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.edit(id, body, editTimestamp)
            prefs[notesKey] = SealedCodecs.encodeNotes(next)
        }
    }

    /**
     * Toggle the pinned state of a note.
     */
    suspend fun togglePinned(id: Long) {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.togglePinned(id)
            prefs[notesKey] = SealedCodecs.encodeNotes(next)
        }
    }

    /**
     * Delete a note. A no-op if the id is not in
     * the store.
     */
    suspend fun delete(id: Long) {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.delete(id)
            prefs[notesKey] = SealedCodecs.encodeNotes(next)
        }
    }
}
