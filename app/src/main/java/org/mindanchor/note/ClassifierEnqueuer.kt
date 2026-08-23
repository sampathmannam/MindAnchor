package org.mindanchor.note

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.NotesPrefs
import org.mindanchor.model.Note

/**
 * The fire-and-forget enqueuer for the on-device
 * note classifier. The enqueuer owns its own
 * [CoroutineScope] so a note save that finishes
 * before the model can run still completes the UI
 * write; the classifier's work happens on a
 * different scope and never blocks the launcher.
 *
 * ## Why a separate scope rather than the activity's
 *
 * [org.mindanchor.model.NoteActivity] keeps an
 * application-scoped [CoroutineScope] for note
 * persistence (the v0.20.1 round 5 follow-up that
 * survives `finish()`). The classifier's scope is
 * the same shape but a *different* job: persistence
 * is the source-of-truth write that must land,
 * classification is a derived-data refresh that can
 * be dropped on the floor if the process dies.
 * Sharing a scope couples their failure modes —
 * a single uncaught exception in classification
 * would cancel persistence, which is the wrong
 * direction.
 *
 * ## Why "drop on the floor" is OK
 *
 * A note that is saved but not yet classified has
 * `type = null`. The list view shows no chip. The
 * user can tap "Re-classify all" in Settings, or
 * just edit the note (the edit triggers a fresh
 * classify). A lost classification is recoverable
 * and the user-visible state is "no chip" — the
 * same state as a note saved before v0.25.0, the
 * same state as a user without Phi-4 mini
 * installed. The chip is the only thing the user
 * loses, and the chip is the user's, not the data.
 */
class ClassifierEnqueuer(private val context: Context) {

    /**
     * The classification scope. [SupervisorJob] so a
     * single failed classification does not cancel
     * the rest of the queue. [Dispatchers.IO] because
     * the work is a model call — CPU-bound but not
     * main-thread; the same dispatcher the night
     * report and the letter use.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The notes prefs. Held as a field so every
     * enqueue reads and writes through the same
     * DataStore handle. The handle is cheap; one
     * per enqueuer is fine.
     */
    private val prefs = NotesPrefs(context.applicationContext)

    /**
     * The classifier. A new instance per enqueue is
     * fine — the classifier holds no state beyond
     * the [Context]; the model file path and thread
     * budget are constants.
     */
    private val classifier = NoteClassifier(context.applicationContext)

    /**
     * Enqueue a single note for classification.
     * The classifier runs on [scope]; the
     * write-back (via [NotesPrefs.setType]) is a
     * coroutine inside the launch block, so it
     * also runs off the main thread.
     *
     * Failures (model not loaded, OOM, malformed
     * output) are caught and dropped. The note
     * keeps `type = null`; the list view shows no
     * chip; the user can re-enqueue via Settings.
     */
    fun enqueue(note: Note) {
        scope.launch {
            runCatching {
                val type = classifier.classify(note.body)
                prefs.setType(note.id, type)
            }
        }
    }

    /**
     * Enqueue a batch of notes for classification.
     * Used by the one-time upgrade pass and the
     * "Re-classify all" settings action. Notes
     * are enqueued one at a time on the same
     * scope, so a backlog of 1000 notes produces
     * 1000 sequential model calls — slow, but
     * the scope survives `finish()` and the
     * launcher can be backgrounded while the
     * pass runs.
     *
     * The pass is **idempotent**: notes that
     * already have a type are skipped (the caller
     * filters by `type == null` for the upgrade
     * pass, or resets to null before re-enqueueing
     * for the manual re-classify).
     */
    fun enqueueAll(notes: List<Note>) {
        for (note in notes) {
            enqueue(note)
        }
    }

    /**
     * v0.25.0: the one-time upgrade pass. Reads
     * every note whose `type` is null, enqueues
     * them all, and sets a SharedPreferences flag
     * so the pass does not run again. The flag
     * is the source of truth for "have we done
     * this yet"; the call is safe to make on
     * every activity launch.
     *
     * The function uses [scope] (the same one
     * [enqueue] uses) so the work outlives
     * `finish()`. A user who opens the notes
     * screen, closes it, and re-opens it sees
     * the same in-flight pass — model calls
     * don't restart; they continue.
     *
     * The flag is set only after the read
     * succeeds, so a crash mid-pass causes
     * the pass to retry on the next launch.
     * The enqueue itself is fault-tolerant;
     * a single failed classify is dropped,
     * the rest of the queue continues.
     */
    fun runUpgradePassIfNeeded() {
        scope.launch {
            runCatching {
                val prefsForFlag = context.applicationContext
                    .getSharedPreferences(UPGRADE_PREFS, Context.MODE_PRIVATE)
                if (prefsForFlag.getBoolean(UPGRADE_FLAG_KEY, false)) return@launch
                val state = prefs.notes.first()
                val untyped = state.notes.filter { it.type == null }
                if (untyped.isNotEmpty()) {
                    enqueueAll(untyped)
                }
                prefsForFlag.edit { putBoolean(UPGRADE_FLAG_KEY, true) }
            }
        }
    }

    private companion object {
        const val UPGRADE_PREFS = "notes_upgrade"
        const val UPGRADE_FLAG_KEY = "v0_25_classify_pass_done"
    }
}
