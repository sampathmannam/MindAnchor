@file:Suppress("MaxLineLength", "FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")
package org.mindanchor.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * The brief: "I want to remember this" â€” the
 * user's pattern is "capture an insight", not
 * "write a draft." Drafts need a Save button;
 * captured insights do not. We save on every
 * edit and on every list change. A note is
 * never "in progress"; it is whatever was
 * last saved.
 *
 * ## Why no "share" / "export" / "reminder"
 *
 * Brief Â§A5: notes are local-only, no cloud,
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
 * case â€” it can be many rows down the LazyColumn,
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
    // v0.25.10: the save handler now receives the
    // user-selected type (from the active filter
    // pill). The activity uses this as the note's
    // type and skips the classifier when it is
    // non-null â€” the user explicitly told us the
    // type, and silently overwriting it was the
    // v0.25.8 / smoke-v2 P0 #1 bug. The v0.25.8
    // shape was the 1-arg `(body: String) -> Unit`
    // lambda; the activity now passes a 2-arg
    // lambda.
    onAdd: (body: String, type: NoteType?) -> Unit,
    onEdit: (id: Long, body: String) -> Unit,
    onTogglePinned: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onClose: () -> Unit,
) {
    // v0.20.1 round 5 follow-up (round 5 audit
    // fix): the previous comment claimed the
    // `addInFlight` flag prevents a double-tap.
    // It does not â€” the flag is set and reset in
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
    // Set when the user taps the Ã— IconButton on a
    // note row; cleared when the user confirms or
    // dismisses the dialog. remember (not
    // rememberSaveable) is correct: a config change
    // mid-dialog should not auto-confirm a delete.
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    // v0.25.0: the active type filter. null = "All"
    // (no filter). Set to a [NoteType] to narrow the
    // list to notes of that type. remember (not
    // rememberSaveable) is correct: a config change
    // resets the filter, same as the search field
    // does not survive in the current build (the
    // search field is local to the screen).
    var filter by remember { mutableStateOf<NoteType?>(null) }

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
    // v0.23.0: notes are grouped by day, latest day on top. The
    // existing [NoteStore.sortedForList] sorts the inner list per
    // day; [NoteStore.groupedByDay] produces the (day, sorted
    // notes for that day) list, ordered by the day's
    // most-recently-touched note. The list view renders one
    // section header per day and the notes underneath. The
    // [today] is captured at composition start so the label
    // ("Today" / "Yesterday" / day-of-week / absolute date) is
    // consistent within one screen render and does not drift if
    // the user has the screen open across midnight.
    val grouped = remember(sorted) { NoteStore.groupedByDay(sorted) }
    // v0.25.0: filter the grouped list by the active
    // type. Days with no matching notes are removed
    // entirely (a day header with no notes is noise).
    // The filter is in-memory; a config change
    // resets it, same as the search field.
    val visible = remember(grouped, filter) {
        if (filter == null) {
            grouped
        } else {
            grouped.mapNotNull { (day, dayNotes) ->
                val matching = dayNotes.filter { it.type == filter }
                if (matching.isEmpty()) null else day to matching
            }
        }
    }
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    val noteTimestampFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    }

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
        // v0.25.18 i18n sweep: hoist the close-button
        // contentDescription into a local val so the
        // IconButton's Modifier.semantics lambda can
        // reference it. stringResource is @Composable
        // and cannot be called inside the semantics
        // lambda; the val is the canonical pattern.
        val closeDesc = stringResource(R.string.note_close)
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
                            // v0.25.18 i18n sweep: was the literal
                            // English string "Close". A Tamil
                            // user running a Tamil-localised build
                            // heard English in TalkBack. The string
                            // now lives in strings.xml as
                            // R.string.note_close so the localiser
                            // can override it.
                            contentDescription = closeDesc
                        },
                    ) {
                        Text(
                            text = "â†",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )

            // The new-note composer. Always visible
            // at the top of the screen â€” captures
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
                            // v0.25.10: pass the active
                            // filter as the new note's
                            // type. The "Task" pill the
                            // user tapped is now also a
                            // type-selector for the note
                            // they are about to save â€”
                            // the v0.25.8 / smoke-v2
                            // P0 #1 bug was that the
                            // filter selection was
                            // silently dropped. null
                            // (the "All" / no-filter
                            // case) keeps the existing
                            // classifier path.
                            onAdd(newNoteDraft.trim().take(Note.MAX_BODY), filter)
                            newNoteDraft = ""
                            addInFlight = false
                        }
                    },
                    enabled = newNoteDraft.isNotBlank() && !addInFlight,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }

            // v0.25.0: the type-filter chip row. A
            // horizontal scroll of [FilterChip]s â€”
            // "All" plus one per [NoteType]. The
            // active chip is visually selected;
            // tapping the active chip clears the
            // filter (back to "All"). The row is
            // hidden when there are no notes yet,
            // since "All" is the only meaningful
            // state and a row of one chip is noise.
            if (sorted.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == null,
                        onClick = { filter = null },
                        label = { Text(stringResource(R.string.note_filter_all)) },
                    )
                    NoteType.values().forEach { noteType ->
                        FilterChip(
                            selected = filter == noteType,
                            onClick = {
                                filter = if (filter == noteType) null else noteType
                            },
                            label = { Text(stringResource(noteTypeLabel(noteType))) },
                        )
                    }
                }
            }

            if (sorted.isEmpty()) {
                // Empty state. A single line, no
                // illustration â€” the home screen
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
            } else if (visible.isEmpty()) {
                // v0.25.0: notes exist, but the
                // current filter has no matches.
                // One short line; the chip row
                // is the affordance to clear the
                // filter.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(filterEmptyLabel(filter)),
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
                    visible.forEach { (day, dayNotes) ->
                        // Day header. A label, not a button. The
                        // padding around it keeps a small visual
                        // gap between the section above and the
                        // first note in this section.
                        item(key = "header-$day") {
                            Text(
                                text = daySectionLabel(day, today),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = 16.dp,
                                    bottom = 4.dp,
                                ),
                            )
                        }
                        items(dayNotes, key = { it.id }) { note ->
                            val isEditing = editingNoteId == note.id

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
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
                                            // v0.25.7+ WP-3: cap the
                                            // body preview at 2 lines
                                            // so a pasted URL or
                                            // long sentence does not
                                            // push the row height to
                                            // fill the screen. The
                                            // full body is in the
                                            // activity; the row
                                            // shows the title (by
                                            // convention the first
                                            // line) plus a hint of
                                            // the second.
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        // v0.25.0: the type chip, or
                                        // a small shimmer placeholder
                                        // while the classifier is
                                        // running. Three states:
                                        //  - typed note: a small
                                        //    colored surface with the
                                        //    type name;
                                        //  - untyped note, recently
                                        //    saved: a thin indeterminate
                                        //    progress bar, signalling
                                        //    "the model is still
                                        //    thinking";
                                        //  - untyped note, save older
                                        //    than the shimmer window:
                                        //    no badge at all (the
                                        //    model isn't on the phone,
                                        //    or it failed silently).
                                        NoteTypeBadge(note = note, now = System.currentTimeMillis())
                                    }
                                    val pinDesc = stringResource(
                                        if (note.pinned) R.string.note_unpin else R.string.note_pin
                                    )
                                    val deleteDesc = stringResource(R.string.note_delete)
                                    IconButton(
                                        onClick = { onTogglePinned(note.id) },
                                        modifier = Modifier.semantics {
                                            contentDescription = pinDesc
                                        },
                                    ) {
                                        Text(
                                            text = if (note.pinned) "â˜…" else "â˜†",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = if (note.pinned) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteId = note.id },
                                        modifier = Modifier.semantics {
                                            contentDescription = deleteDesc
                                        },
                                    ) {
                                        Text(
                                            text = "Ã—",
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    }
                                }
                                // v0.23.0: date+time stamp under the
                                // body, in the launcher's standard
                                // "MMM d, h:mm a" format. The
                                // format is consistent with the
                                // home-screen note preview, so a
                                // user looking at a note on home
                                // and the same note in the list
                                // reads the same string.
                                Text(
                                    text = formatNoteTimestamp(note, zone, noteTimestampFormatter),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        val id = pendingDeleteId!!
        // v0.25.5 WP-G: a confirmation pulse when a note is
        // actually deleted. Brewster CHI 2007: rich tactile
        // feedback for distinct actions. LongPress is the same
        // shape the QuickNotesCard save uses â€” the user is
        // committing a destructive action, the same feedback
        // type fits.
        val haptics = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.note_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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

/**
 * v0.25.0: the string resource for a [NoteType]'s
 * display name. Used by both the chip on the row
 * and the filter chip above the list. Keeping
 * the mapping in one place means the chip and
 * the filter can never drift apart.
 */
private fun noteTypeLabel(type: NoteType): Int = when (type) {
    NoteType.GENERAL -> R.string.note_type_general
    NoteType.TASK -> R.string.note_type_task
    NoteType.REMINDER -> R.string.note_type_reminder
    NoteType.JOURNAL -> R.string.note_type_journal
}

/**
 * v0.25.0: the string resource for the empty-
 * state message when the active filter has no
 * matches. A user on "Tasks" with no tasks sees
 * "No tasks yet." Same shape as the existing
 * [R.string.note_list_empty], narrowed by type.
 */
private fun filterEmptyLabel(filter: NoteType?): Int = when (filter) {
    null -> R.string.note_list_empty
    NoteType.GENERAL -> R.string.note_filter_empty_general
    NoteType.TASK -> R.string.note_filter_empty_task
    NoteType.REMINDER -> R.string.note_filter_empty_reminder
    NoteType.JOURNAL -> R.string.note_filter_empty_journal
}

/**
 * v0.25.0: the per-type chip background colour.
 * Pastel-tinted, drawn from the existing launcher
 * palette (no new colours introduced). The colours
 * are picked to be distinguishable in the list
 * view at the chip's small size without
 * dominating the note body.
 */
private fun noteTypeColor(type: NoteType): Color = when (type) {
    // Neutral grey for general; matches the
    // unclassified default.
    NoteType.GENERAL -> Color(GENERAL_CHIP_COLOR)
    // Blue tint for tasks â€” same family as the
    // EMA "above usual" indicator.
    NoteType.TASK -> Color(TASK_CHIP_COLOR)
    // Orange tint for reminders â€” the time-bound
    // signal. Matches the EMA "below usual".
    NoteType.REMINDER -> Color(REMINDER_CHIP_COLOR)
    // Purple tint for journal â€” reflective, the
    // quietest of the four. The letter's reading-
    // card accent colour.
    NoteType.JOURNAL -> Color(JOURNAL_CHIP_COLOR)
}

private const val GENERAL_CHIP_COLOR: Long = 0xFFE0E0E0
private const val TASK_CHIP_COLOR: Long = 0xFFBBDEFB
private const val REMINDER_CHIP_COLOR: Long = 0xFFFFE0B2
private const val JOURNAL_CHIP_COLOR: Long = 0xFFE1BEE7

/**
 * v0.25.0: the per-row type badge. Three states:
 *  - **typed note** â€” a small colored surface with
 *    the type name.
 *  - **untyped note, recently saved** â€” a thin
 *    indeterminate [LinearProgressIndicator]. The
 *    classifier is running; the chip will appear
 *    once it writes back.
 *  - **untyped note, save older than the shimmer
 *    window** â€” no badge. The classifier isn't on
 *    the phone, or it failed silently; the user
 *    can re-classify via Settings.
 *
 * The shimmer is the "the model is thinking"
 * affordance the spec calls for. Without it, a
 * newly-saved note looks identical to a note saved
 * yesterday that has no type â€” the user cannot
 * tell the model is running. With it, the user
 * sees activity for the [SHIMMER_DURATION_MS]
 * window after save, then either a chip (success)
 * or nothing (failure / no model).
 *
 * The [now] parameter is passed in rather than
 * read from [System.currentTimeMillis] so the
 * helper is testable.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun NoteTypeBadge(note: Note, now: Long) {
    val type = note.type
    if (type != null) {
        Surface(
            color = noteTypeColor(type),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text = stringResource(noteTypeLabel(type)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    } else if (isClassifying(note, now)) {
        // A thin indeterminate progress bar. The
        // animation runs for as long as the bar is
        // composed; when [isClassifying] flips to
        // false (model wrote a type, or the window
        // elapsed), recomposition removes the
        // bar and renders nothing (or a chip, if
        // the type is now set).
        LinearProgressIndicator(
            modifier = Modifier
                .padding(start = 8.dp)
                .width(SHIMMER_WIDTH_DP)
                .height(SHIMMER_HEIGHT_DP),
            color = noteTypeColor(NoteType.GENERAL),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    // else: nothing.
}

/**
 * v0.25.1: the date+time stamp under each note
 * row. The list view used to render `note.updatedAt`,
 * the home card renders `note.createdAt` â€” the
 * same note showed two different timestamps on two
 * screens. This helper pins the list view to
 * `createdAt` so the two surfaces read the same
 * string. `internal` so the JVM unit tests can
 * reach it.
 *
 * Why createdAt, not updatedAt: the moment of
 * capture is the more meaningful anchor for a
 * wellness app â€” "when did I write this?" is a
 * question the user can usefully ask themselves,
 * "when did I last edit it?" rarely is. The home
 * card already uses createdAt (see
 * [org.mindanchor.launcher.HomeScreen.noteTimeText])
 * and the list view now matches.
 */
internal fun formatNoteTimestamp(
    note: Note,
    zone: java.time.ZoneId,
    formatter: java.time.format.DateTimeFormatter,
): String =
    formatter.format(
        java.time.Instant.ofEpochMilli(note.createdAt)
            .atZone(zone)
            .toLocalDateTime()
    )

/**
 * v0.25.0: true when [note] is untyped and was
 * saved within the [SHIMMER_DURATION_MS] window.
 *
 * The check is "within the window, inclusive on
 * both ends". A note saved exactly
 * [SHIMMER_DURATION_MS] ago still shows the
 * shimmer; a note saved one millisecond after
 * the window does not. The inclusive upper bound
 * keeps the shimmer visible for the full duration
 * the user is told to expect.
 *
 * Pure function; the [now] parameter is the test
 * seam. Production callers pass
 * [System.currentTimeMillis].
 *
 * `internal` so the JVM unit tests can reach it
 * without making the helper part of the public
 * API. The visible-to-tests surface is the same
 * as the visible-to-the-screen surface.
 */
internal fun isClassifying(note: Note, now: Long): Boolean {
    if (note.type != null) return false
    val elapsed = now - note.updatedAt
    return elapsed in 0..SHIMMER_DURATION_MS
}

internal const val SHIMMER_DURATION_MS: Long = 10_000L
private val SHIMMER_WIDTH_DP = 32.dp
private val SHIMMER_HEIGHT_DP = 4.dp
