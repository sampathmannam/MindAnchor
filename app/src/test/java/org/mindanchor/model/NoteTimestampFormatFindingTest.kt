package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Finding tests for v0.25.1 bug 2 — the same note
 * showed two different timestamps on the home card
 * ("yesterday 7:15 PM") and in the notes list
 * ("Aug 10, 10:02 PM").
 *
 * The home card used [Note.createdAt]; the list
 * view used [Note.updatedAt]. The KDoc on the list
 * view claimed consistency with the home preview,
 * but the field was the wrong one. The fix routes
 * both surfaces through [formatNoteTimestamp],
 * which pins the list view to [Note.createdAt] —
 * the moment of capture is the meaningful anchor
 * for a wellness app.
 *
 * What this test pins:
 *  1. `formatNoteTimestamp` uses `createdAt` —
 *     the rendered string follows `createdAt`,
 *     not `updatedAt`, when the two differ.
 *  2. Editing a note (touching `updatedAt`)
 *     does NOT change the list-view timestamp.
 *  3. The list-view Composable delegates to the
 *     helper, so a future refactor that inlines a
 *     fresh `note.updatedAt` is caught here.
 *  4. The helper's zone parameter is respected
 *     (UTC vs IST gives different calendar days
 *     near midnight).
 *  5. The helper's formatter parameter is
 *     respected (a custom pattern works).
 */
class NoteTimestampFormatFindingTest {

    private val zoneUtc: ZoneId = ZoneId.of("UTC")
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

    @Test
    fun `formatNoteTimestamp uses createdAt not updatedAt`() {
        val note = Note(
            id = 1L,
            body = "x",
            createdAt = 1_000L, // some moment
            updatedAt = 99_999L, // far later
            type = null,
        )
        val rendered = formatNoteTimestamp(note, zoneUtc, formatter)
        // Must NOT use the updatedAt value. A epoch of 99_999
        // is 1970-01-01T00:01:39Z, which would render as
        // "Jan 1, 12:01 AM" if we accidentally used updatedAt.
        val expected = formatter.format(
            java.time.Instant.ofEpochMilli(1_000L)
                .atZone(zoneUtc)
                .toLocalDateTime(),
        )
        assertEquals(expected, rendered)
        // The updatedAt path would produce a different string
        // because 1_000L (epoch second 1) is a different
        // local time than 99_999L.
        val updatedAtRendered = formatter.format(
            java.time.Instant.ofEpochMilli(99_999L)
                .atZone(zoneUtc)
                .toLocalDateTime(),
        )
        assertNotEquals(updatedAtRendered, rendered)
    }

    @Test
    fun `editing a note does not change the list-view timestamp`() {
        // The whole point of the fix: the list view
        // should not move the timestamp every time
        // the user touches the note.
        val original = Note(
            id = 1L,
            body = "x",
            createdAt = 5_000L,
            updatedAt = 5_000L,
            type = null,
        )
        val before = formatNoteTimestamp(original, zoneUtc, formatter)
        val edited = original.copy(updatedAt = 999_999L)
        val after = formatNoteTimestamp(edited, zoneUtc, formatter)
        assertEquals(
            "editing must not move the list-view timestamp",
            before,
            after,
        )
    }

    @Test
    fun `NoteScreen list view delegates to formatNoteTimestamp helper`() {
        // If a future refactor inlines a fresh
        // `note.updatedAt` we want to catch it at
        // build time, not when a user notices two
        // different timestamps on the same note.
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/model/NoteScreen.kt",
            "../app/src/main/java/org/mindanchor/model/NoteScreen.kt",
        )
        val file = candidates
            .map { java.io.File(it) }
            .firstOrNull { it.isFile }
            ?: error("NoteScreen.kt not found")
        val text = file.readText()
        // The list-view row's `Text(` for the timestamp
        // must call the helper, not a fresh `.updatedAt`.
        assertTrue(
            "list view must call formatNoteTimestamp(note, zone, formatter); " +
                "a fresh note.updatedAt here would re-introduce the bug.",
            text.contains("formatNoteTimestamp(note, zone, noteTimestampFormatter)"),
        )
    }

    @Test
    fun `zone parameter is honoured`() {
        // 1_000L epoch is 1970-01-01 00:00:01 UTC,
        // which is 1970-01-01 05:30 in Asia/Kolkata.
        val note = Note(
            id = 1L,
            body = "x",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            type = null,
        )
        val utc = formatNoteTimestamp(note, ZoneId.of("UTC"), formatter)
        val kolkata = formatNoteTimestamp(note, ZoneId.of("Asia/Kolkata"), formatter)
        assertNotEquals(
            "the helper must respect the zone argument; otherwise " +
                "the same note would render two different times on two " +
                "phones in different time zones.",
            utc,
            kolkata,
        )
    }

    @Test
    fun `formatter parameter is honoured`() {
        val note = Note(
            id = 1L,
            body = "x",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            type = null,
        )
        val pattern1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        val pattern2 = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
        val rendered1 = formatNoteTimestamp(note, zoneUtc, pattern1)
        val rendered2 = formatNoteTimestamp(note, zoneUtc, pattern2)
        assertNotEquals(rendered1, rendered2)
    }
}
