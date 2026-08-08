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
}
