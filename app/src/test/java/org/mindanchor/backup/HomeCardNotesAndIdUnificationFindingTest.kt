package org.mindanchor.backup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.8+ WP-3: the home-card note path and the
 * id-generator were the two highest-severity
 * findings from the v0.25.7 bug-hunt campaign
 * (NOTES-#2 + NOTES-#3).
 *
 * Two file-shape pins:
 *  1. [org.mindanchor.launcher.LauncherViewModel.addQuickNote]
 *     enqueues classification — the home-card note
 *     gets the same type chip as a note written in
 *     the full activity.
 *  2. The id generator is process-singleton
 *     ([org.mindanchor.data.NotesPrefs.nextNoteId])
 *     and both call sites (the home card and the
 *     full activity) read from it. There is no
 *     longer a per-view-model [AtomicLong] field
 *     that could produce duplicate ids across the
 *     two paths.
 *
 * A regression on either axis would re-introduce
 * a silent failure mode: notes written from the
 * home card would have no type chip (NOTES-#2) and
 * the two capture paths would race for ids
 * (NOTES-#3, data integrity).
 */
class HomeCardNotesAndIdUnificationFindingTest {

    @Test
    fun `LauncherViewModel addQuickNote enqueues classification`() {
        val source = readSource("LauncherViewModel.kt")
        assertNotNull(source)
        // The home-card add path must call into the
        // ClassifierEnqueuer, just like the full
        // activity's onAdd. The shape is the
        // enqueue call + an explicit comment that
        // the chip is part of the v0.25.0 contract.
        val enqueues = source!!.contains("ClassifierEnqueuer(") &&
            source.contains(".enqueue(note)")
        assertTrue(
            "LauncherViewModel.addQuickNote must enqueue classification " +
                "via ClassifierEnqueuer. Without this, a note written " +
                "from the home-card stays type=null even after the " +
                "classifier runs — the v0.25.0 chip promise was " +
                "broken on the most common capture path. enqueues=" +
                "$enqueues.",
            enqueues,
        )
    }

    @Test
    fun `NoteActivity and LauncherViewModel share the NotesPrefs id generator`() {
        // v0.25.7: two AndroidViewModels, two AtomicLong
        // fields, two seeds. A note written from the
        // home card and a note written in the full
        // activity could share an id.
        //
        // v0.25.8+WP-3 fix: a single process-singleton
        // id generator in [NotesPrefs] (seeded on
        // first use from the max existing id). Both
        // call sites call [NotesPrefs.nextNoteId] —
        // not their own private AtomicLong.
        val notesPrefs = readSource("NotesPrefs.kt").orEmpty()
        val activity = readSource("NoteActivity.kt", "..").orEmpty()
        val launcher = readSource("LauncherViewModel.kt", "..").orEmpty()

        val sharedGeneratorDefined = notesPrefs.contains("fun nextNoteId():") &&
            notesPrefs.contains("idGenerator") &&
            notesPrefs.contains("AtomicLong")

        // The NoteActivity must call notesPrefs.nextNoteId,
        // not its own AtomicLong. The launcher must
        // do the same.
        val activityUsesShared = activity.contains("notesPrefs.nextNoteId()") &&
            !activity.contains("private val idCounter:")
        val launcherUsesShared = launcher.contains("notesPrefs.nextNoteId()") &&
            !launcher.contains("private val idCounter:")

        assertTrue(
            "NotesPrefs must define a single process-singleton id " +
                "generator. sharedGeneratorDefined=" +
                "$sharedGeneratorDefined.",
            sharedGeneratorDefined,
        )
        assertTrue(
            "NoteActivity must use NotesPrefs.nextNoteId, not its own " +
                "private idCounter. activityUsesShared=" +
                "$activityUsesShared.",
            activityUsesShared,
        )
        assertTrue(
            "LauncherViewModel must use NotesPrefs.nextNoteId, not its " +
                "own private idCounter. launcherUsesShared=" +
                "$launcherUsesShared.",
            launcherUsesShared,
        )
    }

    private fun readSource(filename: String, pkg: String = ""): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/$pkg/$filename",
            "app/src/main/java/org/mindanchor/data/$filename",
            "app/src/main/java/org/mindanchor/launcher/$filename",
            "app/src/main/java/org/mindanchor/model/$filename",
            "app/src/main/java/org/mindanchor/$filename",
            "../app/src/main/java/org/mindanchor/$pkg/$filename",
            "../app/src/main/java/org/mindanchor/data/$filename",
            "../app/src/main/java/org/mindanchor/launcher/$filename",
            "../app/src/main/java/org/mindanchor/model/$filename",
            "../app/src/main/java/org/mindanchor/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
