package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.5 WP-G: haptic-rich captures (Brewster CHI 2007).
 *
 * The v0.25.1 senior-tester audit flagged that a single haptic for
 * "save" is information-poor. Brewster's CHI 2007 paper on rich
 * tactile feedback: a single haptic for "save" is information-poor;
 * distinct feedback types (long-press for save, soft "whoosh" for
 * clear, etc.) let the user navigate by feel. The fix widens the
 * surface to multiple call sites and at least two distinct
 * HapticFeedbackType values, so the user can tell the actions
 * apart without looking at the screen.
 *
 * v0.26.6: BedtimeListCard removed from the home surface, so
 * the bedtime-save haptic site is gone. The surface is now
 * three call sites (NoteScreen delete, LetterInbox delete,
 * QuickNotesCard clear), still spanning at least two distinct
 * HapticFeedbackType values.
 *
 * The tests below pin the surface: one per call site, plus
 * the type-variety assertion. A regression that copied the "save"
 * haptic to all the call sites would pass the per-site tests
 * and fail the type-variety test — the load-bearing one.
 */
class HapticRichCapturesFindingTest {

    @Test
    fun `NoteScreen row delete fires a haptic on confirm`() {
        val source = readSource("NoteScreen.kt")
        assertNotNull(source)
        // v0.25.16 fix: NoteScreen no longer imports
        // `LocalHapticFeedback` directly; the
        // [org.mindanchor.ui.HapticFeedbackGate]
        // CompositionLocal is the new source. The token
        // `org.mindanchor.ui.LocalHapticFeedbackGate` is the
        // post-fix shape.
        assertTrue(
            "NoteScreen.kt uses the HapticFeedbackGate CompositionLocal (v0.25.16 fix).",
            source!!.contains("org.mindanchor.ui.LocalHapticFeedbackGate"),
        )
        assertTrue(
            "NoteScreen.kt fires a haptic in the delete confirm onClick",
            source.contains("haptics.performHapticFeedback(HapticFeedbackType.LongPress)") &&
                source.contains("onDelete(id)"),
        )
    }

    @Test
    fun `LetterInbox delete fires a haptic on confirm`() {
        val source = readSource("LetterScreen.kt")
        assertNotNull(source)
        // v0.25.16: LetterScreen uses the gate. The
        // HapticFeedbackType parameter and the call-site
        // shape are unchanged.
        assertTrue(
            "LetterScreen.kt uses the HapticFeedbackGate CompositionLocal (v0.25.16 fix).",
            source!!.contains("org.mindanchor.ui.LocalHapticFeedbackGate"),
        )
        assertTrue(
            "LetterScreen.kt fires a haptic in the inbox delete confirm",
            source.contains("haptics.performHapticFeedback(") &&
                source.contains("HapticFeedbackType.LongPress") &&
                source.contains("onDelete(pendingDeleteDate)"),
        )
    }

    @Test
    fun `QuickNotesCard clear fires a distinct TextHandleMove haptic`() {
        val source = readSource("HomeScreen.kt")
        assertNotNull(source)
        // TextHandleMove is the soft "whoosh" of moving text out
        // of the way. The QuickNotesCard's clear button is the
        // one place it is used; everywhere else is LongPress.
        // v0.25.16: the haptic is routed through the
        // [HapticFeedbackGate].
        assertTrue(
            "HomeScreen.kt has a clear button with TextHandleMove haptic (via HapticFeedbackGate, v0.25.16 fix)",
            source!!.contains("HapticFeedbackType.TextHandleMove") &&
                source.contains("draft = \"\"") &&
                source.contains("haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)") &&
                source.contains("org.mindanchor.ui.LocalHapticFeedbackGate"),
        )
    }

    @Test
    fun `the four call sites are not all the same HapticFeedbackType`() {
        // The load-bearing assertion. Brewster's whole point is
        // that distinct feedback types are information: a user
        // who can feel the difference between save and clear
        // will not wonder which they pressed. A regression that
        // copy-pasted the LongPress call to every save / delete
        // / clear site would erase that. The test counts the
        // distinct types in use and asserts there is more than
        // one. v0.25.16: the gate is the new route; the
        // HapticFeedbackType values at the call sites are
        // unchanged.
        val sources = listOf(
            "HomeScreen.kt" to readSource("HomeScreen.kt").orEmpty(),
            "NoteScreen.kt" to readSource("NoteScreen.kt").orEmpty(),
            "LetterScreen.kt" to readSource("LetterScreen.kt").orEmpty(),
        )
        val allText = sources.joinToString("\n") { it.second }
        val types = setOf(
            "HapticFeedbackType.LongPress",
            "HapticFeedbackType.TextHandleMove",
            "HapticFeedbackType.LongRelease",
            "HapticFeedbackType.SegmentFrequentTick",
        )
        val used = types.filter { allText.contains(it) }.toSet()
        assertTrue(
            "At least two HapticFeedbackType values are in use across the launcher; saw $used",
            used.size >= 2,
        )
        // The QuickNotesCard clear is the one place TextHandleMove
        // is used. A regression that dropped it would re-collapse
        // the four call sites into a single type.
        assertTrue(
            "TextHandleMove is used at least once (the QuickNotesCard clear)",
            used.contains("HapticFeedbackType.TextHandleMove"),
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/launcher/$filename",
            "app/src/main/java/org/mindanchor/model/$filename",
            "app/src/main/java/org/mindanchor/letters/$filename",
            "../app/src/main/java/org/mindanchor/launcher/$filename",
            "../app/src/main/java/org/mindanchor/model/$filename",
            "../app/src/main/java/org/mindanchor/letters/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
