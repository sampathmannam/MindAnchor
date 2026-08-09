package org.mindanchor.model

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.NotesPrefs
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

    /**
     * Monotonic counter for note ids. Two notes
     * saved in the same wall-clock millisecond
     * would otherwise collide (the brief
     * acknowledged the risk; the fix is cheap).
     *
     * v0.20.1 round 5 follow-up: the counter is
     * seeded at onCreate from the largest existing
     * id (or currentTimeMillis, whichever is
     * larger). Without this, a process restart
     * inside the same millisecond as the last save
     * would re-initialise the counter to the
     * current millisecond and the next note would
     * get a *duplicate* id. The old code claimed
     * "the new ids are still higher than the old"
     * but that was wrong: the seed is currentTime
     * at construction, which is not necessarily
     * higher than the last id written in a
     * previous process.
     *
     * Seeding from the max existing id is correct:
     * the on-disk notes are the source of truth,
     * and any new id strictly greater than the
     * existing max is unique.
     */
    private val idCounter: java.util.concurrent.atomic.AtomicLong

    init {
        // runBlocking here is acceptable: this
        // is the activity's onCreate, which is
        // already on the main thread; a single
        // DataStore read is fast (microseconds
        // when the file is cached). The
        // alternative is to defer the seed to
        // the first call to nextId(), which
        // would require prefs to be a field
        // before idCounter, which it cannot be
        // (Context is the activity's). The
        // pragmatic choice is runBlocking once
        // at construction.
        val prefsForSeed = NotesPrefs(this) // 'this' is the Activity (Context) at init time
        val seeded = kotlinx.coroutines.runBlocking {
            val existing = prefsForSeed.notes.first()
            val maxExisting = existing.notes.maxOfOrNull { it.id } ?: 0L
            maxOf(System.currentTimeMillis(), maxExisting)
        }
        idCounter = java.util.concurrent.atomic.AtomicLong(seeded)
    }

    private fun nextId(): Long = idCounter.incrementAndGet()

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

        val prefs = NotesPrefs(applicationContext)

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

        setContent {
            MindAnchorTheme {
                val state by prefs.notes.collectAsState(initial = NotesState())
                NoteScreen(
                    notes = state,
                    onAdd = { body ->
                        // Auto-save on add. The id is
                        // a monotonic counter so two
                        // notes saved in the same
                        // millisecond do not collide
                        // (brief §A5).
                        val now = System.currentTimeMillis()
                        appScope.launch {
                            runCatching {
                                prefs.add(
                                    Note(
                                        id = nextId(),
                                        body = body,
                                        createdAt = now,
                                        updatedAt = now,
                                        pinned = false,
                                    ),
                                )
                            }
                        }
                    },
                    onEdit = { id, body ->
                        appScope.launch { runCatching { prefs.edit(id, body) } }
                    },
                    onTogglePinned = { id ->
                        appScope.launch { runCatching { prefs.togglePinned(id) } }
                    },
                    onDelete = { id ->
                        appScope.launch { runCatching { prefs.delete(id) } }
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}
