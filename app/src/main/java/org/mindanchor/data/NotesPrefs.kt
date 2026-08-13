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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

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
     * Allocate a fresh id. The id is strictly
     * greater than every id currently on disk, and
     * unique within the process lifetime (the
     * counter is process-singleton — see
     * [Companion.idGenerator] below). Concurrent
     * callers get distinct ids; the AtomicLong is
     * the only source of truth.
     *
     * v0.25.9: the generator is a true
     * process-singleton on the [Companion] (a
     * class-level `by lazy` resolves separately per
     * class instance, which silently broke the
     * v0.25.8 "process-singleton" claim — two
     * `NotesPrefs` instances in the same process
     * had two counters seeded to the same
     * `System.currentTimeMillis()` and produced
     * duplicate ids on a fast device). The seed is
     * performed asynchronously by
     * [Companion.seedFromDiskIfNeeded] from
     * `HomeActivity.onCreate`, so the first
     * `nextNoteId()` call is fast (no `runBlocking`
     * on the main thread, no DataStore read in the
     * hot path).
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

    companion object {
        /**
         * The DataStore key. Held on the companion so
         * the singleton [seedFromDiskIfNeeded] can
         * read the same key the instance methods
         * write to.
         */
        @JvmStatic
        private val notesKey = stringPreferencesKey("notes")

        /**
         * v0.25.9: process-singleton id generator.
         *
         * The v0.25.8 fix was a class-level `by lazy`
         * on the `idGenerator` field, which silently
         * produced one `AtomicLong` per `NotesPrefs`
         * instance. Two views (the home card and the
         * full activity) construct their own
         * `NotesPrefs` → two counters → two seeds →
         * potential duplicate ids (the exact failure
         * mode the v0.25.8 release notes claimed to
         * fix).
         *
         * The fix is to put the generator on the
         * [Companion] object (a true per-class-loader
         * singleton) and seed it asynchronously from
         * [seedFromDiskIfNeeded] in
         * `HomeActivity.onCreate`. The seed runs in a
         * background coroutine, so the first
         * `nextNoteId()` call is fast (no
         * `runBlocking` on the main thread, no
         * DataStore read in the hot path).
         *
         * The counter starts at
         * `System.currentTimeMillis()` so that any
         * note created *before* the seed completes
         * still has an id well above zero; the seed
         * raises the counter to at least the max
         * existing id via an atomic
         * `updateAndGet { maxOf(it, seed) }`, so
         * post-seed ids are always > any historical
         * id even if the seed races with a
         * concurrent `nextNoteId()` call.
         */
        @JvmStatic
        private val idGenerator = AtomicLong(System.currentTimeMillis())

        @JvmStatic
        private val seeded = AtomicBoolean(false)

        @JvmStatic
        private val seedLock = Any()

        /**
         * Seed the id generator from the max
         * existing id on disk. Called from
         * `HomeActivity.onCreate` (or any custom
         * `Application.onCreate` if added later).
         *
         * The function is idempotent — only the
         * first call does work, subsequent calls
         * are a no-op. Safe to call from any
         * coroutine context; never blocks the
         * caller.
         *
         * The DataStore read (`data.first()`) is
         * a suspend point and must be outside the
         * `synchronized` block (the Kotlin
         * compiler forbids suspending inside a
         * `synchronized` block). The pattern is
         * "suspend, then lock" rather than "lock,
         * then suspend" — the double-check on
         * `seeded` after the lock re-acquires
         * handles the case where another caller
         * raced to the lock first.
         */
        suspend fun seedFromDiskIfNeeded(context: Context) {
            if (seeded.get()) return
            val app = context.applicationContext
            val existing = app.notesDataStore.data.first()
            val decoded = SealedCodecs.decodeNotes(existing[notesKey].orEmpty())
            val maxExisting = decoded.notes.maxOfOrNull { it.id } ?: 0L
            val seed = maxOf(System.currentTimeMillis(), maxExisting)
            synchronized(seedLock) {
                if (seeded.get()) return@synchronized
                // Raise the counter atomically. The
                // maxOf(current, seed) means: if a
                // nextNoteId() call already advanced
                // past our seed (e.g. before this
                // coroutine ran), we do not roll back.
                idGenerator.updateAndGet { current -> maxOf(current, seed) }
                seeded.set(true)
            }
        }
    }
}
