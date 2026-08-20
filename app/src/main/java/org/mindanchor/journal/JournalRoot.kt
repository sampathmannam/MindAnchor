/*
 * v0.64.0 (BPD-first): the journal root.
 *
 * State-based navigation across the 5 journal screens
 * (Today, Archive, Settings, Mood, QuickNote). Holds
 * the current screen + a small back-stack so the back
 * gesture / back button returns to the previous screen
 * rather than exiting the journal.
 *
 * v0.64.0 changes:
 *   - The 5 screens are the BPD-first variants. The
 *     Today/QuickNote bodies are connected: a single
 *     `todayEntry` string lives in this root and is
 *     shared by both screens. Typing in QuickNote
 *     updates the same string Today shows.
 *   - The footer is no longer a fixed parameter on
 *     every screen — every surface has its own
 *     [JournalFooter] call with the right active
 *     icon, and the 3 icons are NOT labelled (BPD-first:
 *     no labels in the footer).
 *   - The bang commands still work as typed input
 *     inside the Quick Note composer (the existing
 *     LauncherViewModel bang parser routes them), but
 *     the UI no longer advertises them. The right-side
 *     bang-command row in the footer is gone.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.toMutableStateList

/**
 * The 5 journal screens. The enum order is the
 * canonical navigation order (Today → Archive →
 * Settings → Mood → QuickNote).
 */
enum class JournalRoute { Today, Archive, Settings, Mood, QuickNote }

@Composable
fun JournalRoot(
    onExitRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stack: SnapshotStateList<JournalRoute> = remember { mutableStateListOf(JournalRoute.Today) }
    val current: JournalRoute = stack.last()

    // v0.64.0: the entry text is held in this root, so
    // Today and QuickNote share the same string. v0.65.0
    // will persist this to NoteStore on close. For
    // v0.64.0 the text is in-memory only and resets to
    // the v0.64.0 default fixture on app restart.
    var todayEntry by remember {
        // v0.64.0 fixture: the same prose the draft
        // renders. v0.65.0 will load from NoteStore.
        mutableStateOf(
            "The light through the window is different today. It feels quieter. I haven't said it out loud yet, but there is a strange sort of peace in just noticing the way the shadows stretch across the floor. No expectations for the next hour. Just this."
        )
    }

    // The back gesture pops the stack. If the stack has
    // only Today, the back gesture forwards to
    // HomeActivity (which will move the launcher to the
    // background — standard launcher behaviour).
    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (current) {
            JournalRoute.Today -> JournalToday(
                entryBody = todayEntry,
                onEntryBodyChange = { todayEntry = it },
                onContinueWriting = { stack.add(JournalRoute.QuickNote) },
                onMood = { stack.add(JournalRoute.Mood) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
            )
            JournalRoute.Archive -> JournalArchive(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.QuickNote) },
                onSettings = { stack.add(JournalRoute.Settings) },
            )
            JournalRoute.Settings -> JournalSettings(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
            )
            JournalRoute.Mood -> JournalMood(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
            )
            JournalRoute.QuickNote -> JournalQuickNote(
                text = todayEntry,
                onTextChange = { todayEntry = it },
                onBack = { stack.removeAt(stack.lastIndex) },
            )
        }
    }
}
