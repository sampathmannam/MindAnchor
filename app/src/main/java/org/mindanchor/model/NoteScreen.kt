package org.mindanchor.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R

/**
 * The v0.20.1 notes screen. v0.20.1 round 5.
 *
 * ## Why single-screen with mode toggle
 *
 * The list view and the editor are the same
 * screen with an `editingNoteId` state. Tapping
 * a note in the list sets `editingNoteId`; the
 * editor renders above the list. Pressing the
 * back button in the editor returns to the list.
 * This is simpler than routing through the
 * Android Navigation component for a single-
 * file surface.
 *
 * ## Why a FAB for "new note"
 *
 * The list is for *reading your past notes*.
 * Creating a new note is a different action and
 * deserves a different affordance. The FAB is
 * the standard pattern; it is reachable with
 * one hand (bottom-right).
 *
 * ## Why auto-save on every keystroke
 *
 * The brief: "I want to remember this" — the
 * user's pattern is "capture an insight", not
 * "write a draft." Drafts need a Save button;
 * captured insights do not. We save on every
 * edit and on every list change. A note is
 * never "in progress"; it is whatever was
 * last saved.
 *
 * ## Why no "share" / "export" / "reminder"
 *
 * Brief §A5: notes are local-only, no cloud,
 * no share, no export, no reminders. The
 * surface is the data, not a feature.
 */

/**
 * v0.20.9: Modifier extension that auto-scrolls
 * the nearest scrollable ancestor to bring the
 * receiving composable into view when it gains
 * focus. The notes screen has two text fields
 * (the always-on composer at the top and the
 * inline editor that replaces a row when
 * `editingNoteId` is set) and the soft keyboard
 * otherwise covers whichever one the user is
 * typing into. The inline editor is the worse
 * case — it can be many rows down the LazyColumn,
 * and a focus event without a bringIntoView call
 * leaves the field hidden under the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch { requester.bringIntoView() }
            }
        }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    notes: NotesState,
    onAdd: (body: String) -> Unit,
    onEdit: (id: Long, body: String) -> Unit,
    onTogglePinned: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onClose: () -> Unit,
) {
    // v0.20.1 round 5 follow-up (round 5 audit
    // fix): the previous comment claimed the
    // `addInFlight` flag prevents a double-tap.
    // It does not — the flag is set and reset in
    // the same synchronous call frame because
    // `onAdd(...)` returns immediately (the
    // activity's prefs.add is on a separate
    // coroutine). The actual double-tap guard
    // is the synchronous `newNoteDraft = ""`
    // that runs after the onAdd fires: a second
    // tap in the same frame sees the draft as
    // blank, the Save button's `enabled` check
    // returns false, and Compose does not
    // dispatch the click. The flag is kept for
    // future use (a real async guard if the
    // onAdd becomes suspending) and to make the
    // intent explicit in the code.
    //
    // rememberSaveable: the in-flight draft body
    // and the editor body survive a configuration
    // change. The activity is stateNotNeeded in
    // the manifest, but rememberSaveable uses the
    // activity's SavedStateRegistry, which is
    // independent of stateNotNeeded.
    var addInFlight by remember { mutableStateOf(false) }
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorBody by rememberSaveable { mutableStateOf("") }
    var newNoteDraft by rememberSaveable { mutableStateOf("") }
    // v0.20.1 round 5 follow-up: the id of the
    // note whose delete the user just confirmed.
    // Set when the user taps the × IconButton on a
    // note row; cleared when the user confirms or
    // dismisses the dialog. remember (not
    // rememberSaveable) is correct: a config change
    // mid-dialog should not auto-confirm a delete.
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    // The composer's focus requester. The
    // "directly" affordance: the user lands on the
    // notes screen and the keyboard is already up.
    // The capture pattern fails if the user has
    // to tap the field before they can type.
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (notes.notes.isEmpty() && editingNoteId == null) {
            // Only auto-focus on the empty state.
            // If the user is returning to a screen
            // with notes, don't steal focus from
            // the list view.
            composerFocus.requestFocus()
        }
    }

    val sorted = NoteStore.sortedForList(notes.notes)

    // Back handler: if the user is mid-edit on a
    // note, back exits edit mode (and saves the
    // in-flight edit). Otherwise back falls through
    // to the activity's onBackPressedDispatcher
    // callback, which calls finish().
    BackHandler(enabled = editingNoteId != null) {
        val id = editingNoteId
        if (id != null) {
            onEdit(id, editorBody)
            editingNoteId = null
            editorBody = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        // v0.20.9: imePadding on the screen Column so
        // the soft keyboard does not cover the
        // composer or the inline editor when the
        // user is editing an existing note. The
        // TopAppBar below already handles the
        // status-bar inset; imePadding adds the
        // keyboard inset on the bottom of the
        // content area.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.note_new)) },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.semantics {
                            contentDescription = "Back to launcher"
                        },
                    ) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )

            // The new-note composer. Always visible
            // at the top of the screen — captures
            // the "I just thought of something"
            // moment without forcing a modal.
            // v0.20.9: bringIntoViewOnFocus on the
            // composer keeps it visible above the
            // keyboard when the field is focused.
            // (The auto-focus on empty state from
            // composerFocus.requestFocus() also
            // triggers the same requester.)
            OutlinedTextField(
                value = newNoteDraft,
                onValueChange = {
                    if (it.length <= Note.MAX_BODY) {
                        newNoteDraft = it
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 80.dp)
                    .bringIntoViewOnFocus()
                    .focusRequester(composerFocus),
                placeholder = { Text(stringResource(R.string.note_body_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                supportingText = {
                    Text(
                        text = "${newNoteDraft.length} / ${Note.MAX_BODY}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        if (newNoteDraft.isNotBlank() && !addInFlight) {
                            addInFlight = true
                            onAdd(newNoteDraft.trim().take(Note.MAX_BODY))
                            newNoteDraft = ""
                            addInFlight = false
                        }
                    },
                    enabled = newNoteDraft.isNotBlank() && !addInFlight,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }

            if (sorted.isEmpty()) {
                // Empty state. A single line, no
                // illustration — the home screen
                // already provides calm, and the
                // notes screen is a tool, not a
                // mood.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.note_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    items(sorted, key = { it.id }) { note ->
                        val isEditing = editingNoteId == note.id

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isEditing) {
                                            onEdit(note.id, editorBody)
                                            editingNoteId = null
                                            editorBody = ""
                                        } else {
                                            editingNoteId = note.id
                                            editorBody = note.body
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isEditing) {
                                    // v0.20.9: bringIntoViewOnFocus
                                    // on the inline editor so the
                                    // note being edited scrolls into
                                    // view above the keyboard. The
                                    // note is in a LazyColumn and
                                    // the field can be many rows down
                                    // — without the requester the
                                    // field was at the same Y as
                                    // the keyboard and the user
                                    // could not see what they were
                                    // typing.
                                    OutlinedTextField(
                                        value = editorBody,
                                        onValueChange = {
                                            if (it.length <= Note.MAX_BODY) {
                                                editorBody = it
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 80.dp)
                                            .bringIntoViewOnFocus(),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences,
                                        ),
                                    )
                                } else {
                                    Text(
                                        text = note.body.lineSequence().firstOrNull() ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                IconButton(
                                    onClick = { onTogglePinned(note.id) },
                                    modifier = Modifier.semantics {
                                        contentDescription = if (note.pinned) {
                                            "Unpin this note"
                                        } else {
                                            "Pin this note"
                                        }
                                    },
                                ) {
                                    Text(
                                        text = if (note.pinned) "★" else "☆",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = if (note.pinned) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeleteId = note.id },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Delete this note"
                                    },
                                ) {
                                    Text(
                                        text = "×",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        val id = pendingDeleteId!!
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.note_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.note_delete_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.note_delete_no))
                }
            },
        )
    }
}
