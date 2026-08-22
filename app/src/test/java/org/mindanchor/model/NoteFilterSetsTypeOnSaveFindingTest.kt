package org.mindanchor.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.10: pin the fix for smoke-v2 P0 finding #1 —
 * "Note classification ignores user-selected category".
 *
 * The repro:
 *  1. Home → type a note → Save → "New note" screen.
 *  2. Type a body.
 *  3. Tap the "Task" pill (it highlights purple).
 *  4. Tap Save.
 *  5. Tap the "All" pill to view the saved note.
 *  Expected: the note is persisted with type `Task`.
 *  Observed (v0.25.8, v0.25.9): the note is persisted
 *  with type `General`, because the classifier writes
 *  its own guess after `onAdd` returns. The user's
 *  filter selection is silently dropped.
 *
 * The fix (Shape A, the minimum that ships v0.25.10):
 *  - `NoteScreen.onAdd` now takes a `(body, type)`
 *    callback. The Save button passes the active
 *    filter as the second arg.
 *  - `NoteActivity.onAdd` writes the user-supplied
 *    type onto the new `Note` and skips the
 *    `classifier.enqueue` call when the user has
 *    already picked a type. The existing
 *    "no filter selected" path is preserved: when
 *    the user is on "All" (filter is null), the
 *    classifier still runs.
 *
 * File-shape pins:
 *  1. `NoteScreen.onAdd` is `(body: String, type: NoteType?) -> Unit`
 *     (not the v0.25.8 `(body: String) -> Unit`).
 *  2. The Save TextButton's `onClick` calls
 *     `onAdd(<draft>, filter)` — the active filter
 *     is the second arg, so the activity can use it
 *     as the new note's type.
 *  3. `NoteActivity.onAdd` writes the second arg
 *     onto `Note.type` (so the user-selected type
 *     lands on the saved note, not `null`).
 *  4. `NoteActivity.onAdd` calls
 *     `classifier.enqueue(note)` only when the
 *     second arg is null — the classifier is skipped
 *     when the user has already picked a type,
 *     which is the v0.25.10 fix.
 *
 * A regression that reverts to the v0.25.8 shape
 * (1-arg `onAdd`, `type = null`, unconditional
 * `classifier.enqueue`) flips all four pins red.
 */
class NoteFilterSetsTypeOnSaveFindingTest {

    @Test
    fun `NoteScreen onAdd accepts a type argument (v0_25_10 fix shape)`() {
        val source = readSource("NoteScreen.kt")
        assertNotNull(source)
        // Pin #1: the parameter type is
        // `(body: String, type: NoteType?) -> Unit`,
        // not the v0.25.8 `(body: String) -> Unit`.
        val newShape = source!!.contains("onAdd: (body: String, type: NoteType?) -> Unit")
        val oldShape = Regex(
            "onAdd: \\(body: String\\)\\s*->\\s*Unit",
        ).containsMatchIn(source)
        assertTrue(
            "NoteScreen's onAdd must accept a second `type: NoteType?` arg " +
                "so the active filter can be passed as the new note's type. " +
                "newShape=$newShape oldShape=$oldShape.",
            newShape && !oldShape,
        )
    }

    @Test
    fun `NoteScreen Save button passes the active filter as the type argument`() {
        val source = readSource("NoteScreen.kt")
        assertNotNull(source)
        // Pin #2: the Save TextButton's onClick calls
        // onAdd(<draft>, filter) — the active filter
        // is the second arg.
        val passesFilter = Regex(
            "onAdd\\(\\s*newNoteDraft\\.trim\\(\\)\\.take\\(Note\\.MAX_BODY\\)\\s*,\\s*filter\\s*\\)",
        ).containsMatchIn(source!!)
        // Negative regression guard: the v0.25.8 shape
        // was a 1-arg onAdd call.
        val oneArgCall = Regex(
            "onAdd\\(\\s*newNoteDraft\\.trim\\(\\)\\.take\\(Note\\.MAX_BODY\\)\\s*\\)",
        ).containsMatchIn(source)
        assertTrue(
            "NoteScreen's Save TextButton must call `onAdd(<draft>, filter)` " +
                "so the active filter is passed as the new note's type. " +
                "passesFilter=$passesFilter oneArgCall=$oneArgCall.",
            passesFilter && !oneArgCall,
        )
    }

    @Test
    fun `NoteActivity onAdd writes the type argument onto Note_type`() {
        val source = readSource("NoteActivity.kt")
        assertNotNull(source)
        // Pin #3: NoteActivity's onAdd lambda
        // signature accepts `type` as the second
        // arg, and the new Note is constructed with
        // `type = type` (not `type = null`, which was
        // the v0.25.8 shape).
        val lambdaShape = Regex(
            "onAdd = \\{ body, type ->",
        ).containsMatchIn(source!!)
        val writesType = source.contains("type = type,") ||
            source.contains("type = type ")
        assertTrue(
            "NoteActivity's onAdd must destructure the type arg and write it onto " +
                "the new Note (so the user-selected type is preserved, not dropped " +
                "to null). lambdaShape=$lambdaShape writesType=$writesType.",
            lambdaShape && writesType,
        )
    }

    @Test
    fun `NoteActivity onAdd skips the classifier when the user picked a type`() {
        val source = readSource("NoteActivity.kt")
        assertNotNull(source)
        // Pin #4: the classifier is enqueued only
        // when the user-supplied type is null. The
        // v0.25.8 shape was an unconditional
        // `classifier.enqueue(note)` after every
        // add — the model would overwrite the
        // user's choice. The v0.25.10 fix wraps
        // the enqueue in an `if (type == null)`
        // guard.
        val hasGuard = source!!.contains("if (type == null) {")
        val hasEnqueue = source.contains("classifier.enqueue(note)")
        // The guard must appear *before* the enqueue
        // (chronological order in the lambda body).
        val guardBeforeEnqueue = source.indexOf("if (type == null) {") <
            source.indexOf("classifier.enqueue(note)")
        assertTrue(
            "NoteActivity's onAdd must skip classifier.enqueue when the user has " +
                "picked a type (the v0.25.10 fix). The classifier is the path " +
                "that overwrote the user's 'Task' selection with 'General' in " +
                "the v0.25.8 build. hasGuard=$hasGuard hasEnqueue=$hasEnqueue " +
                "guardBeforeEnqueue=$guardBeforeEnqueue.",
            hasGuard && hasEnqueue && guardBeforeEnqueue,
        )
    }

    @Test
    fun `filter chip still controls the list filter (regression guard for the v0_25_0 list view)`() {
        // The fix must NOT break the v0.25.0 list
        // filter: tapping a pill still narrows the
        // visible list. The pre-fix file already had
        // `filter = noteType` in the chip onClick;
        // we pin the v0.25.10 file to still set the
        // filter, not just the next-note type.
        val source = readSource("NoteScreen.kt")
        assertNotNull(source)
        val stillSetsFilter = source!!.contains("filter = if (filter == noteType) null else noteType") ||
            source.contains("filter = noteType")
        assertTrue(
            "NoteScreen must still let the filter pill set the list filter. The " +
                "v0.25.10 fix adds a second effect to the same tap (next-note " +
                "type), it does not replace the first (list filter). " +
                "stillSetsFilter=$stillSetsFilter.",
            stillSetsFilter,
        )
    }

    private fun assertNotNull(source: String?) {
        org.junit.Assert.assertNotNull(
            "could not read source file from the working dir",
            source,
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/model/$filename",
            "../app/src/main/java/org/mindanchor/model/$filename",
            "src/main/java/org/mindanchor/model/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
