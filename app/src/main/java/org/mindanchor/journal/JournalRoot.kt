/*
 * v0.63.0: the journal root.
 *
 * State-based navigation across the 5 journal screens
 * (Today, Archive, Settings, Mood, QuickNote). Holds
 * the current screen + a small back-stack so the back
 * gesture / back button returns to the previous screen
 * rather than exiting the journal.
 *
 * The 5 screens correspond exactly to the 5 superdesign
 * drafts (b35ee64d, b446ae65, 5088ef9e, c01a4b03,
 * 4cf0a48a). The state is a simple enum; the bang
 * commands and the footer icons each route to a
 * destination, and the journal returns to Today when
 * the user dismisses the QuickNote composer.
 *
 * v0.63.0 routes the journal as the primary launcher
 * surface — HomeActivity calls [JournalRoot] directly,
 * replacing the v0.62.7 [LauncherRoot]. The old
 * LauncherRoot is preserved in HomeScreen.kt for
 * rollback but is not wired in v0.63.0.
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

    // v0.63.0: the back gesture pops the stack. If
    // the stack has only Today, the back gesture
    // forwards to HomeActivity (which will move
    // the launcher to the background — standard
    // launcher behaviour).
    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    val onBangGround: () -> Unit = { /* TODO v0.64.0: open GroundMe */ }
    val onBangBreathe: () -> Unit = { /* TODO v0.64.0: open Breathing */ }
    val onBangMood: () -> Unit = { stack.add(JournalRoute.Mood) }

    Box(modifier = modifier.fillMaxSize()) {
        when (current) {
            JournalRoute.Today -> JournalToday(
                onContinueWriting = { stack.add(JournalRoute.QuickNote) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onBangGround = onBangGround,
                onBangBreathe = onBangBreathe,
                onBangMood = onBangMood,
            )
            JournalRoute.Archive -> JournalArchive(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.QuickNote) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onBangGround = onBangGround,
                onBangBreathe = onBangBreathe,
            )
            JournalRoute.Settings -> JournalSettings(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
                onBangGround = onBangGround,
                onBangBreathe = onBangBreathe,
            )
            JournalRoute.Mood -> JournalMood(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onBangGround = onBangGround,
                onBangBreathe = onBangBreathe,
            )
            JournalRoute.QuickNote -> JournalQuickNote(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSave = { /* TODO v0.64.0: write to NoteStore */ stack.removeAt(stack.lastIndex) },
            )
        }
    }
}
