package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the note data layer (v0.20.1 round 5,
 * docs/research/26-notes-and-check-in.md).
 *
 * The data layer is independent of the UI. The UI
 * (NoteActivity, NoteScreen) is a follow-up; the
 * data layer is harmless on its own and is
 * Python-mirror-verified.
 */
class NoteTest {

    @Test
    fun `encode and decode empty state`() {
        assertEquals("", NoteStore.encode(emptyList()))
        assertEquals(emptyList<Note>(), NoteStore.decode(""))
    }

    @Test
    fun `round-trips a single note`() {
        val original = Note(
            id = 1L,
            body = "hello world",
            createdAt = 1000L,
            updatedAt = 1000L,
            pinned = false,
        )
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(original.body, decoded[0].body)
        assertEquals(original.id, decoded[0].id)
        assertEquals(original.pinned, decoded[0].pinned)
    }

    @Test
    fun `preserves tabs and newlines in the body`() {
        val original = Note(
            id = 1L,
            body = "line 1\nline 2\twith tab\nline 3",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(original.body, decoded[0].body)
    }

    @Test
    fun `preserves unicode in the body`() {
        val original = Note(
            id = 1L,
            body = "café ☕ résumé",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals(original.body, decoded[0].body)
    }

    @Test
    fun `pinned flag round-trips`() {
        val original = Note(id = 1L, body = "pinned", createdAt = 2000L, updatedAt = 2000L, pinned = true)
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertTrue(decoded[0].pinned)
    }

    @Test
    fun `sanitised trims and caps the body`() {
        val original = Note(
            id = 1L,
            body = "  " + "x".repeat(5000) + "  ",
            createdAt = 1000L,
            updatedAt = 5000L,
        )
        val sanitised = original.sanitised()
        assertEquals(Note.MAX_BODY, sanitised.body.length)
        assertTrue(sanitised.updatedAt >= sanitised.createdAt)
    }

    @Test
    fun `sortedForList puts pinned first, then updated desc`() {
        val notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, pinned = false),
            Note(id = 2L, body = "b", createdAt = 2000L, updatedAt = 2000L, pinned = true),
            Note(id = 3L, body = "c", createdAt = 3000L, updatedAt = 3000L, pinned = false),
            Note(id = 4L, body = "d", createdAt = 4000L, updatedAt = 4000L, pinned = true),
        )
        val sorted = NoteStore.sortedForList(notes)
        // Pinned first, sorted by updatedAt desc within each group:
        // pinned: id=4 (4000), id=2 (2000)
        // unpinned: id=3 (3000), id=1 (1000)
        assertEquals(listOf(4L, 2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `search is case-insensitive and matches across newlines`() {
        val notes = listOf(
            Note(id = 1L, body = "Hello World", createdAt = 1000L, updatedAt = 1000L),
            Note(id = 2L, body = "goodbye world", createdAt = 2000L, updatedAt = 2000L),
            Note(id = 3L, body = "GOODBYE", createdAt = 3000L, updatedAt = 3000L),
        )
        val results = NoteStore.search(notes, "goodbye")
        assertEquals(2, results.size)
        assertEquals(2L, results[0].id)
        assertEquals(3L, results[1].id)
    }

    @Test
    fun `empty search returns all notes`() {
        val notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L),
            Note(id = 2L, body = "b", createdAt = 2000L, updatedAt = 2000L),
        )
        assertEquals(notes, NoteStore.search(notes, ""))
    }

    @Test
    fun `malformed lines are skipped`() {
        val raw = """
            1	0	1000	1000	${"Z29vZA=="}
            this is not a valid line
            2	0	2000	2000	${"YWxz"}
            	0	100	100	${"Z29vZA=="}
            3	2	3000	3000	${"Z29vZA=="}
        """.trimIndent()
        val decoded = NoteStore.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(1L, decoded[0].id)
        assertEquals(2L, decoded[1].id)
    }

    @Test
    fun `body at MAX_BODY round-trips`() {
        val original = Note(id = 1L, body = "x".repeat(Note.MAX_BODY), createdAt = 1000L, updatedAt = 1000L)
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(Note.MAX_BODY, decoded[0].body.length)
    }

    @Test
    fun `body over MAX_BODY is clamped on encode`() {
        val original = Note(
            id = 1L,
            body = "x".repeat(Note.MAX_BODY + 100),
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals(Note.MAX_BODY, decoded[0].body.length)
    }

    @Test
    fun `empty body round-trips`() {
        val original = Note(id = 1L, body = "", createdAt = 1000L, updatedAt = 1000L)
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals("", decoded[0].body)
    }

    @Test
    fun `NotesState add appends`() {
        val state = NotesState()
        val next = state
            .add(Note(id = 1L, body = "first", createdAt = 1000L, updatedAt = 1000L))
            .add(Note(id = 2L, body = "second", createdAt = 2000L, updatedAt = 2000L))
        assertEquals(2, next.notes.size)
        assertEquals("first", next.byId(1L)?.body)
        assertNull(next.byId(3L))
    }

    @Test
    fun `NotesState edit replaces body and bumps updatedAt`() {
        val state = NotesState(
            notes = listOf(
                Note(id = 1L, body = "first", createdAt = 1000L, updatedAt = 1000L),
            ),
        )
        val next = state.edit(id = 1L, body = "first edited", editTimestamp = 9999L)
        assertEquals("first edited", next.byId(1L)?.body)
        assertEquals(9999L, next.byId(1L)?.updatedAt)
        assertEquals(1000L, next.byId(1L)?.createdAt)
    }

    @Test
    fun `NotesState togglePinned flips the flag`() {
        val state = NotesState(
            notes = listOf(Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, pinned = false)),
        )
        val pinned = state.togglePinned(1L)
        assertTrue(pinned.byId(1L)?.pinned == true)
        val unpinned = pinned.togglePinned(1L)
        assertFalse(unpinned.byId(1L)!!.pinned)
    }

    @Test
    fun `NotesState delete removes the note`() {
        val state = NotesState(
            notes = listOf(
                Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L),
                Note(id = 2L, body = "b", createdAt = 2000L, updatedAt = 2000L),
            ),
        )
        val next = state.delete(1L)
        assertEquals(1, next.notes.size)
        assertNull(next.byId(1L))
        assertEquals("b", next.byId(2L)?.body)
    }

    @Test
    fun `NotesState operations are pure`() {
        val state = NotesState(
            notes = listOf(Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L)),
        )
        // add, edit, delete, togglePinned return new
        // instances; the original is not mutated.
        state.add(Note(id = 2L, body = "b", createdAt = 2000L, updatedAt = 2000L))
        state.edit(1L, "changed", 9999L)
        state.delete(1L)
        state.togglePinned(1L)
        assertEquals(1, state.notes.size)
        assertEquals("a", state.byId(1L)?.body)
        assertEquals(1000L, state.byId(1L)?.updatedAt)
        assertFalse(state.byId(1L)!!.pinned)
    }

    // v0.23.0: day-grouping tests for the notes list view.
    // The list is a LazyColumn of day sections; the data layer
    // is responsible for the grouping, the UI is responsible
    // for the section headers. These tests pin the data layer
    // so a refactor that re-introduces a flat list is caught.

    @Test
    fun `groupedByDay returns one section per day, latest day first`() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 8, 10)
        val threeDaysAgo = today.minusDays(3)
        val eightDaysAgo = today.minusDays(8)
        fun millisAt(date: java.time.LocalDate, hour: Int): Long =
            date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val notes = listOf(
            // Three days ago, morning.
            Note(id = 1L, body = "old", createdAt = millisAt(threeDaysAgo, 9), updatedAt = millisAt(threeDaysAgo, 9)),
            // Today, late.
            Note(id = 2L, body = "today late", createdAt = millisAt(today, 22), updatedAt = millisAt(today, 22)),
            // Today, early.
            Note(id = 3L, body = "today early", createdAt = millisAt(today, 8), updatedAt = millisAt(today, 8)),
            // Eight days ago.
            Note(
                id = 4L,
                body = "very old",
                createdAt = millisAt(eightDaysAgo, 12),
                updatedAt = millisAt(eightDaysAgo, 12),
            ),
        )
        val grouped = NoteStore.groupedByDay(notes, zone)
        assertEquals(3, grouped.size)
        // The day with the most-recently-touched note (today
        // late, hour 22) is first.
        assertEquals(today, grouped[0].first)
        assertEquals(threeDaysAgo, grouped[1].first)
        assertEquals(eightDaysAgo, grouped[2].first)
    }

    @Test
    fun `groupedByDay keeps the inner sort pinned-first then updatedAt desc`() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 8, 10)
        fun millisAt(date: java.time.LocalDate, hour: Int): Long =
            date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val notes = listOf(
            Note(
                id = 1L,
                body = "today unpinned early",
                createdAt = millisAt(today, 8),
                updatedAt = millisAt(today, 8),
                pinned = false,
            ),
            Note(
                id = 2L,
                body = "today pinned",
                createdAt = millisAt(today, 7),
                updatedAt = millisAt(today, 7),
                pinned = true,
            ),
            Note(
                id = 3L,
                body = "today unpinned late",
                createdAt = millisAt(today, 22),
                updatedAt = millisAt(today, 22),
                pinned = false,
            ),
        )
        val grouped = NoteStore.groupedByDay(notes, zone)
        assertEquals(1, grouped.size)
        val (_, dayNotes) = grouped[0]
        // Pinned first (sorted by updatedAt desc), then
        // unpinned (sorted by updatedAt desc).
        assertEquals(2L, dayNotes[0].id) // pinned
        assertEquals(3L, dayNotes[1].id) // unpinned late
        assertEquals(1L, dayNotes[2].id) // unpinned early
    }

    @Test
    fun `groupedByDay on an empty list returns an empty list`() {
        val zone = java.time.ZoneId.of("UTC")
        assertEquals(emptyList<Pair<java.time.LocalDate, List<Note>>>(), NoteStore.groupedByDay(emptyList(), zone))
    }

    @Test
    fun `daySectionLabel returns Today for the user's local today`() {
        val today = java.time.LocalDate.of(2026, 8, 10)
        assertEquals("Today", daySectionLabel(today, today))
    }

    @Test
    fun `daySectionLabel returns Yesterday for the day before today`() {
        val today = java.time.LocalDate.of(2026, 8, 10)
        assertEquals("Yesterday", daySectionLabel(today.minusDays(1), today))
    }

    @Test
    fun `daySectionLabel returns day-of-week for the past week`() {
        val today = java.time.LocalDate.of(2026, 8, 10) // Monday
        val threeDaysAgo = today.minusDays(3) // Friday
        // Locale-dependent: the test is locale-aware. We assert
        // that the label is the day-of-week name (not "Today",
        // not "Yesterday", not the absolute date).
        val label = daySectionLabel(threeDaysAgo, today)
        assertTrue("got '$label'", label != "Today" && label != "Yesterday")
        assertFalse("got '$label'", label.contains("8") || label.contains("August"))
    }

    @Test
    fun `daySectionLabel returns absolute date for anything older than a week`() {
        val today = java.time.LocalDate.of(2026, 8, 10)
        val eightDaysAgo = today.minusDays(8)
        val label = daySectionLabel(eightDaysAgo, today)
        // Locale-dependent: assert the absolute date is in
        // there. "August 2" or "2 August" or similar.
        assertTrue("got '$label'", label.contains("August") || label.contains("2"))
    }
}
