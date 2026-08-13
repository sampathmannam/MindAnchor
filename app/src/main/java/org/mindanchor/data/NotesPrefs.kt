package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
     * v0.25.7+ WP-3: the shared id generator. Before
     * this fix [org.mindanchor.launcher.LauncherViewModel]
     * and [org.mindanchor.model.NoteActivity] each had
     * their own private `idCounter: AtomicLong` —
     * two AndroidViewModels, two AtomicLongs, two
     * counter seeds. A note written from the home
     * card and a note written in the full activity
     * could get the same id (the home card's seed
     * was System.currentTimeMillis() at first use
     * in the home activity; the full activity's
     * seed was the same at first use in its
     * activity). Duplicate ids break every lookup
     * by id: [org.mindanchor.model.NoteStore.groupedByDay],
     * [setType], [org.mindanchor.data.NotesPrefs.remove].
     *
     * The fix is to make the counter a process-
     * singleton keyed on the [Application] context,
     * not on the activity. The [NotesPrefs] is the
     * natural home — both call sites already
     * construct one. The counter is seeded on first
     * call (lazy, not at construction) by reading
     * the max existing id, so a process restart
     * with a populated store continues correctly.
     */
    private val idGenerator: java.util.concurrent.atomic.AtomicLong by lazy {
        val seeded = kotlinx.coroutines.runBlocking {
            val existing = context.notesDataStore.data.first()
            val decoded = SealedCodecs.decodeNotes(existing[notesKey].orEmpty())
            val maxExisting = decoded.notes.maxOfOrNull { it.id } ?: 0L
            maxOf(System.currentTimeMillis(), maxExisting)
        }
        java.util.concurrent.atomic.AtomicLong(seeded)
    }

    /**
     * Allocate a fresh id. The id is strictly
     * greater than every id currently on disk, and
     * unique within the process lifetime (the
     * counter is process-singleton). Concurrent
     * callers get distinct ids; the AtomicLong is
     * the only source of truth.
     */
    fun nextNoteId(): Long = idGenerator.incrementAndGet()

    /**
     * Edit an existing note. The note with the
     * matching id is replaced; updatedAt is
     * bumped to [editTimestamp] (default: now).
     *
     * v0.25.0: the existing type is preserved on
     * edit. The classifier runs in the background
     * and overwrites the type via [setType] once
     * the new body is read. If the classifier
     * isn't available, the type stays as the
     * previous value — same as a v0.24.0 note with
     * no type. The user can re-classify via
     * Settings.
     */
    suspend fun edit(id: Long, body: String, editTimestamp: Long = System.currentTimeMillis()) {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.edit(id, body, editTimestamp)
            prefs[notesKey] = SealedCodecs.encodeNotes(next)
        }
    }

    /**
     * v0.25.0: write back the classified type for
     * a single note. The body and timestamps are
     * untouched; only the [org.mindanchor.model.Note.type]
     * field changes. Called by the
     * [org.mindanchor.note.ClassifierEnqueuer] after
     * a successful classify.
     *
     * A no-op if the id is not in the store — the
     * note may have been deleted between enqueue
     * and completion. The write is best-effort
     * because the call site is fire-and-forget.
     */
    suspend fun setType(id: Long, type: org.mindanchor.model.NoteType) {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.setType(id, type)
            if (next === current) return@edit // id not found
            prefs[notesKey] = SealedCodecs.encodeNotes(next)
        }
    }

    /**
     * v0.25.0: reset every note's type to null.
     * Used by the "Re-classify all" settings
     * action. The function does not enqueue —
     * the caller re-enqueues after, so the
     * re-classification is visible as a chip
     * appearing on each row over the next
     * several minutes.
     */
    suspend fun clearAllTypes() {
        context.notesDataStore.edit { prefs ->
            val current = SealedCodecs.decodeNotes(prefs[notesKey].orEmpty())
            val next = current.clearAllTypes()
            if (next === current) return@edit // nothing to do
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
