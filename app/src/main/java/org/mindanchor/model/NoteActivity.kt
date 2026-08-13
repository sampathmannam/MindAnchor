package org.mindanchor.model

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.NotesPrefs
import org.mindanchor.note.ClassifierEnqueuer
import org.mindanchor.ui.MindAnchorTheme

/**
 * Hosts the v0.20.1 notes feature. v0.20.1 round 5
 * (docs/research/26-notes-and-check-in.md).
 *
 * ## Why a separate activity
 *
 * Notes are a *single concern* that benefits from
 * its own task: a long-form writing surface
 * that is not the home screen, not the launcher
 * drawer, not a notification. The activity is
 * full-screen and standard-launchMode; the
 * launcher can route here from a long-press
 * affordance on the home screen.
 *
 * ## Why no editing affordance on the list
 *
 * The list view is for browsing. Tapping a note
 * opens the editor (a separate screen pushed
 * onto the back stack). This matches the
 * reading-app pattern and keeps the list scannable.
 *
 * ## Why notes are user-authored, NOT clinical-review-gated
 *
 * The user owns the words. No wording in this
 * activity is launcher-authored — every visible
 * string is either a system string ("New note",
 * "Delete", "Pin") or a system placeholder
 * ("Start writing…"). No @wording-reviewed tag
 * is needed; no clinical-review pass is required.
 */
class NoteActivity : ComponentActivity() {

    // v0.25.7+ WP-3: the shared [NotesPrefs] holds
    // the id generator. Both this activity and the
    // home-card view model call [NotesPrefs.nextNoteId];
    // a single process-singleton counter guarantees
    // no duplicate ids across the two capture paths.
    //
    // The counter is seeded on first use (lazy, not
    // at construction) by reading the max existing
    // id, so a process restart with a populated
    // store continues correctly. The seeding is
    // [NotesPrefs.idGenerator]'s job now — this
    // activity just calls [nextId].
    private val notesPrefs by lazy { NotesPrefs(applicationContext) }

    private fun nextId(): Long = notesPrefs.nextNoteId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hook the system back button to close the
        // activity. Compose's TopAppBar has its own
        // back button via the navigationIcon slot,
        // but the system back gesture / button needs
        // an explicit handler to dismiss the
        // activity. Without this the user has to use
        // the in-app back button, which is a
        // discoverability failure.
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        // v0.20.1 round 5 follow-up: notes
        // persistence runs in an application-scoped
        // coroutine, not lifecycleScope. The
        // activity can finish() while a write is
        // in flight (user taps the in-app back
        // button, the system back, or system
        // back-then-rotate); lifecycleScope is
        // cancelled by finish() and the in-flight
        // DataStore write could be lost. The
        // notes are user-authored text; losing a
        // write to a finishing activity is a
        // real UX bug (the user saw the
        // TextButton fire, then nothing).
        val appScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() +
                kotlinx.coroutines.Dispatchers.IO,
        )

        // v0.25.0: the on-device classifier. The
        // enqueuer owns its own scope (see
        // ClassifierEnqueuer KDoc) so a model
        // call cannot interrupt a notes write.
        val classifier = ClassifierEnqueuer(applicationContext)

        // v0.25.0: one-time upgrade pass. On the
        // first run of a build that includes
        // v0.25.0, every pre-existing note has
        // `type = null`. Enqueue them all for
        // classification; the pass is
        // idempotent (a flag in SharedPreferences
        // is set once the queue is drained) and
        // runs on the enqueuer's own scope. No-op
        // on subsequent launches.
        classifier.runUpgradePassIfNeeded()

        setContent {
            MindAnchorTheme {
                val state by notesPrefs.notes.collectAsState(initial = NotesState())
                NoteScreen(
                    notes = state,
                    onAdd = { body ->
                        // Auto-save on add. The id is
                        // a monotonic counter so two
                        // notes saved in the same
                        // millisecond do not collide
                        // (brief §A5).
                        val now = System.currentTimeMillis()
                        val note = Note(
                            id = nextId(),
                            body = body,
                            createdAt = now,
                            updatedAt = now,
                            pinned = false,
                        )
                        appScope.launch {
                            runCatching { notesPrefs.add(note) }
                        }
                        // v0.25.0: enqueue for
                        // classification. Fire-and-
                        // forget; the list view
                        // will recompose when the
                        // type is written back.
                        classifier.enqueue(note)
                    },
                    onEdit = { id, body ->
                        appScope.launch { runCatching { notesPrefs.edit(id, body) } }
                        // v0.25.0: re-classify on
                        // body edit. The classifier
                        // reads the new body and
                        // writes a new type; if the
                        // model isn't available,
                        // the type stays as the
                        // previous value.
                        appScope.launch {
                            runCatching {
                                val all = notesPrefs.notes.first()
                                val edited = all.notes.firstOrNull { it.id == id }
                                if (edited != null) {
                                    classifier.enqueue(edited.copy(body = body))
                                }
                            }
                        }
                    },
                    onTogglePinned = { id ->
                        appScope.launch { runCatching { notesPrefs.togglePinned(id) } }
                    },
                    onDelete = { id ->
                        appScope.launch { runCatching { notesPrefs.delete(id) } }
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}
