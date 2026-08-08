package org.mindanchor.model

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = NotesPrefs(applicationContext)

        setContent {
            MindAnchorTheme {
                val state by prefs.notes.collectAsState(initial = NotesState())
                NoteScreen(
                    notes = state,
                    onAdd = { body ->
                        // Auto-save on add. The id is
                        // wall-clock millis; the
                        // collision risk for a single
                        // human typing in one second
                        // is effectively zero.
                        val now = System.currentTimeMillis()
                        lifecycleScope.launch {
                            prefs.add(
                                Note(
                                    id = now,
                                    body = body,
                                    createdAt = now,
                                    updatedAt = now,
                                    pinned = false,
                                ),
                            )
                        }
                    },
                    onEdit = { id, body ->
                        lifecycleScope.launch { prefs.edit(id, body) }
                    },
                    onTogglePinned = { id ->
                        lifecycleScope.launch { prefs.togglePinned(id) }
                    },
                    onDelete = { id ->
                        lifecycleScope.launch { prefs.delete(id) }
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}
