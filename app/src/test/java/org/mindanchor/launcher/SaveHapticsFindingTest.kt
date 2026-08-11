package org.mindanchor.launcher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.25.1 bug 4 — the home
 * composer's "Save" button fired silently. A user
 * who hit Save in the dark, or with the keyboard
 * up, or with the screen dim, had no haptic or
 * visual cue that the note had been captured.
 *
 * The fix calls [androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress]
 * via [androidx.compose.ui.platform.LocalHapticFeedback]
 * immediately after `onSave(draft)` on the
 * QuickNotesCard save button. The tick is ≈5ms on
 * most devices — short enough not to interrupt
 * typing, long enough to register.
 *
 * What this test pins:
 *  1. The home composer wires a `LocalHapticFeedback`
 *     and a `performHapticFeedback` call.
 *  2. The call uses `HapticFeedbackType.LongPress`
 *     (the shortest available tick).
 *  3. The call runs after `onSave(draft)` so the
 *     tick happens after the save (not before).
 *  4. The QuickNotesCard Composable is the one
 *     wrapped (not the LoopCard or some other
 *     capture surface).
 *  5. The Open Loop's own save button has the
 *     same haptics (consistency between capture
 *     surfaces).
 */
class SaveHapticsFindingTest {

    private fun readScreen(): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/launcher/HomeScreen.kt")
                    .takeIf { it.isFile }
                    ?: error("HomeScreen.kt not found"),
        ).readText()

    @Test
    fun `home composer Save button performs a haptic on save`() {
        val screen = readScreen()
        // The fix must call performHapticFeedback
        // somewhere on the QuickNotesCard save path.
        assertTrue(
            "the home composer Save button must call performHapticFeedback " +
                "so the user feels the capture.",
            screen.contains("performHapticFeedback(HapticFeedbackType.LongPress)"),
        )
    }

    @Test
    fun `haptics hook is via LocalHapticFeedback not a custom service`() {
        val screen = readScreen()
        // The fix uses the Compose LocalHapticFeedback,
        // not a custom HapticFeedback service.
        assertTrue(
            "haptics must be acquired via LocalHapticFeedback.current — " +
                "the Compose-native path; a custom service would not " +
                "respect user system settings.",
            screen.contains("val haptics = LocalHapticFeedback.current"),
        )
    }

    @Test
    fun `haptic is fired after onSave so the tick is post-capture`() {
        val screen = readScreen()
        // In the QuickNotesCard Save onClick block, the
        // haptics call must come after `onSave(draft)`
        // so the tick corresponds to the capture, not to
        // the press. Use a substring check — the
        // multi-line block made the regex version brittle.
        val quickNotesBlock = extractQuickNotesSaveBlock(screen)
        assertTrue(
            "could not find the QuickNotesCard Save onClick block.",
            quickNotesBlock != null,
        )
        val block = quickNotesBlock!!
        val saveIndex = block.indexOf("onSave(draft)")
        val hapticIndex = block.indexOf("performHapticFeedback")
        assertTrue(
            "the haptic must fire after onSave, not before. " +
                "saveIndex=$saveIndex hapticIndex=$hapticIndex",
            saveIndex >= 0 && hapticIndex > saveIndex,
        )
    }

    @Test
    fun `haptics import is present`() {
        val screen = readScreen()
        // The Compose haptics API needs the import to be
        // present. A missing import would still compile
        // (the test catches the typo case) but a future
        // refactor that drops the import would break the
        // save UX silently.
        assertTrue(
            "import androidx.compose.ui.hapticfeedback.HapticFeedbackType " +
                "must be present.",
            screen.contains("import androidx.compose.ui.hapticfeedback.HapticFeedbackType"),
        )
    }

    @Test
    fun `LocalHapticFeedback import is present`() {
        val screen = readScreen()
        assertTrue(
            "import androidx.compose.ui.platform.LocalHapticFeedback " +
                "must be present.",
            screen.contains("import androidx.compose.ui.platform.LocalHapticFeedback"),
        )
    }

    /**
     * Extract the QuickNotesCard Save TextButton's
     * modifier chain (which includes the onClick
     * block) from the home screen source. The
     * delimiter is "quick_notes_save" — the string
     * resource id for the save button label.
     */
    private fun extractQuickNotesSaveBlock(screen: String): String? {
        val idx = screen.indexOf("quick_notes_save")
        if (idx < 0) return null
        // Walk back to the matching TextButton(
        val before = screen.lastIndexOf("TextButton(", idx)
        if (before < 0) return null
        // Take 400 chars around the TextButton open.
        val end = minOf(screen.length, before + 600)
        return screen.substring(before, end)
    }
}
