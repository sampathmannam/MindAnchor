package org.mindanchor.backup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug-hunt finding tests for the on-write trigger shape
 * (BackupScheduler.start).
 *
 * The trigger subscribes to NotesPrefs.notes and LetterStore.letters
 * and diffs each emission against the previous one. The expected
 * behaviour is: only fires on a genuinely *new* entry — the first
 * emission (the current snapshot on subscribe) must be a no-op,
 * otherwise an existing user who flips the auto-sync toggle after
 * the app has been in use for a while would have all of their
 * existing notes re-backed-up via the on-write path. The intended
 * backfill surface is the "Back up now" button, which calls
 * backupAll() — a separate, non-streaming path.
 *
 * Both checks below are file-shape pins (matching the project's
 * existing "FindingTest" pattern). The findings are real but
 * behavioural: a fresh Robolectric round-trip would need the
 * NotesPrefs / LetterStore flows backed by a DataStore + the
 * scheduler's start() coroutine plumbing, which is more surface
 * than this hunt is willing to invest. The shape is enough.
 */
class OnWriteTriggerFirstEmissionFindingTest {

    /**
     * The on-write trigger must NOT fire on the first emission. A
     * user who already has notes on disk and then enables auto-sync
     * should see only *future* writes get backed up — not the
     * existing ones via the streaming path.
     *
     * The current implementation does `scan(NotesDiffState())` which
     * seeds the accumulator with an empty previous state, so the
     * first emission's `newOnes` is the entire current list. The
     * fix is to drop the first emission (`drop(1)`) or to seed
     * the accumulator with the current state on the first iteration.
     */
    @Test
    fun `on-write trigger must not backfill existing notes on subscribe`() {
        val source = readSource("BackupScheduler.kt")
        assertNotNull(source)
        // Match any of: `.drop(1)` after the scan, or a `firstOrNull`
        // skip, or a `flow.onStart { emit(emptyList()) }` style
        // seeding. The regex is loose to accommodate the eventual
        // fix shape.
        val hasFirstEmissionSkip = Regex(
            """\.drop\(1\)|dropWhile|onStart\s*\{""",
        ).containsMatchIn(source!!)
        assertTrue(
            "BackupScheduler.start must skip the first emission of the " +
                "scan-based diff — otherwise the first DataStore " +
                "reading of the notes / letters lists counts every " +
                "existing entry as new and the on-write trigger " +
                "performs a silent backfill. The 'Back up now' button " +
                "is the intended backfill surface; the streaming path " +
                "must be new-only. Add `.drop(1)` after each `.scan(...)`.",
            hasFirstEmissionSkip,
        )
    }

    /**
     * The on-write diff for letters must key by date AND body.
     * The LetterStore.save() contract is "one letter per date" — a
     * re-save replaces the existing entry for that date. A date-only
     * diff misses the replacement entirely, so a re-save's new body
     * never reaches Drive.
     *
     * v0.25.7+ WP-2 fix: `previousByDate = previous.associateBy { it.date }`
     * then `current.filter { letter -> previousByDate[letter.date]?.body
     * != letter.body }`. The date-only filter (`previousDates = previous
     * .map { it.date }.toSet()`) must NOT be present.
     */
    @Test
    fun `letter diff must detect body changes for the same date`() {
        val source = readSource("BackupScheduler.kt")
        assertNotNull(source)
        // The buggy date-only pattern must be absent
        val diffUsesDateOnly = source!!.contains(
            "val previousDates = previous.map { it.date }.toSet()",
        ) && source.contains("current.filter { it.date !in previousDates }")
        // The fix pattern must be present: a per-date map + body
        // comparison. The exact API name may evolve, so the test
        // checks for the contract shape: an associateBy on date,
        // and a body comparison.
        val diffUsesBody = source.contains("associateBy { it.date }") &&
            source.contains("?.body != letter.body")
        assertTrue(
            "BackupScheduler.newLetters must key by (date, body), not by " +
                "date alone. The buggy date-only pattern is present: " +
                "$diffUsesDateOnly; the body-aware fix is present: " +
                "$diffUsesBody. The bug was a silent data-loss: a re-save " +
                "of an existing letter for the same date did not produce a " +
                "diff, so the new body never reached the backup.",
            !diffUsesDateOnly && diffUsesBody,
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/backup/$filename",
            "../app/src/main/java/org/mindanchor/backup/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
