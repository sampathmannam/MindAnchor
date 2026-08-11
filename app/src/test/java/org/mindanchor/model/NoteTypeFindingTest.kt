package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Finding tests for the v0.25.0 note type shape.
 *
 * These tests pin:
 *  - the four [NoteType] values, in the order the
 *    filter chip row and the typed-summary line
 *    render them,
 *  - the on-disk v0.24.0 → v0.25.0 codec migration
 *    (5-field lines still decode, with `type = null`),
 *  - the rejection of unknown type names (a line
 *    that says `BOGUS` in the type slot is "this
 *    line is corrupt, skip it" — the same fail-closed
 *    pattern as the rest of the codec),
 *  - the round-trip of a typed note,
 *  - the [NotesState.setType] and
 *    [NotesState.clearAllTypes] semantics (no-op
 *    when the id is missing or the value is
 *    unchanged, returns the same instance so the
 *    caller can detect the no-op).
 */
class NoteTypeFindingTest {

    @Test
    fun `NoteType has exactly four values, in render order`() {
        val values = NoteType.values()
        assertEquals(4, values.size)
        assertEquals(NoteType.GENERAL, values[0])
        assertEquals(NoteType.TASK, values[1])
        assertEquals(NoteType.REMINDER, values[2])
        assertEquals(NoteType.JOURNAL, values[3])
    }

    @Test
    fun `NoteType names are upper-case, no abbreviations`() {
        assertEquals("GENERAL", NoteType.GENERAL.name)
        assertEquals("TASK", NoteType.TASK.name)
        assertEquals("REMINDER", NoteType.REMINDER.name)
        assertEquals("JOURNAL", NoteType.JOURNAL.name)
    }

    @Test
    fun `Note default type is null`() {
        val note = Note(id = 1L, body = "x", createdAt = 0L, updatedAt = 0L)
        assertNull(note.type)
    }

    @Test
    fun `encode with null type produces an empty type token (5 fields separated by tabs at the prefix, then body)`() {
        val note = Note(id = 1L, body = "x", createdAt = 1000L, updatedAt = 1000L, pinned = false, type = null)
        val encoded = note.encode()
        // The line is id\tpinned\tcreatedAt\tupdatedAt\ttype\tbase64(body).
        // An empty type token is two consecutive tabs.
        val parts = encoded.split('\t')
        assertEquals(6, parts.size)
        assertEquals("", parts[4]) // empty type token
    }

    @Test
    fun `encode with a type produces the enum name in the type token`() {
        val note = Note(id = 1L, body = "x", createdAt = 1000L, updatedAt = 1000L, type = NoteType.TASK)
        val encoded = note.encode()
        val parts = encoded.split('\t')
        assertEquals(6, parts.size)
        assertEquals("TASK", parts[4])
    }

    @Test
    fun `decodeLine accepts the v0_24_0 5-field format with type null`() {
        // id=1, pinned=0, createdAt=1000, updatedAt=1000, base64("hello")
        val v240 = "1\t0\t1000\t1000\taGVsbG8="
        val decoded = Note.decodeLine(v240)
        assertNotNull(decoded)
        assertEquals(1L, decoded!!.id)
        assertNull(decoded.type)
        assertEquals("hello", decoded.body)
    }

    @Test
    fun `decodeLine accepts the v0_25_0 6-field format with a type`() {
        // id=1, pinned=0, createdAt=1000, updatedAt=1000, type=TASK, base64("hello")
        val v250 = "1\t0\t1000\t1000\tTASK\taGVsbG8="
        val decoded = Note.decodeLine(v250)
        assertNotNull(decoded)
        assertEquals(1L, decoded!!.id)
        assertEquals(NoteType.TASK, decoded.type)
        assertEquals("hello", decoded.body)
    }

    @Test
    fun `decodeLine accepts the v0_25_0 6-field format with an empty type token`() {
        val v250NoType = "1\t0\t1000\t1000\t\taGVsbG8="
        val decoded = Note.decodeLine(v250NoType)
        assertNotNull(decoded)
        assertNull(decoded!!.type)
    }

    @Test
    fun `decodeLine rejects unknown type names as corrupt`() {
        val v250Bogus = "1\t0\t1000\t1000\tBOGUS\taGVsbG8="
        val decoded = Note.decodeLine(v250Bogus)
        assertNull(decoded)
    }

    @Test
    fun `decodeLine rejects lines with the wrong number of fields`() {
        assertNull(Note.decodeLine("1\t0\t1000")) // 3 fields
        assertNull(Note.decodeLine("1\t0\t1000\t1000")) // 4 fields
        // 5 fields, valid v0.24.0 shape — "TASK" is
        // base64-decodable (4 chars, 3 bytes), so this
        // is treated as a v0.24.0 line with a binary
        // body. Not null. The body-validation rule
        // (body is allowed to be empty but not
        // undecodable) does not extend to "looks like
        // text", so a v0.24.0 line that decodes to
        // binary is accepted. The decoder is *dumb* —
        // a binary body is the previous file's
        // contract, and a v0.25.0 reader inherits it.
        // 7 fields — beyond the v0.25.0 max — is
        // always rejected.
        assertNull(Note.decodeLine("1\t0\t1000\t1000\tTASK\taGVsbG8\textra"))
    }

    @Test
    fun `round-trip preserves the type through NoteStore`() {
        val original = Note(
            id = 42L,
            body = "buy milk",
            createdAt = 1000L,
            updatedAt = 1000L,
            type = NoteType.TASK,
        )
        val encoded = NoteStore.encode(listOf(original))
        val decoded = NoteStore.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(NoteType.TASK, decoded[0].type)
    }

    @Test
    fun `mixed-format list decodes — v0_24_0 and v0_25_0 notes together`() {
        val lines = listOf(
            "1\t0\t1000\t1000\taGVsbG8=", // v0.24.0, no type
            "2\t0\t2000\t2000\tJOURNAL\tZ29vZA==", // v0.25.0, journal
        ).joinToString("\n")
        val decoded = NoteStore.decode(lines)
        assertEquals(2, decoded.size)
        assertNull(decoded[0].type)
        assertEquals(NoteType.JOURNAL, decoded[1].type)
    }

    @Test
    fun `NotesState setType updates the type for the matching id`() {
        val state = NotesState(notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, type = null),
        ))
        val next = state.setType(1L, NoteType.TASK)
        assertEquals(NoteType.TASK, next.byId(1L)?.type)
    }

    @Test
    fun `NotesState setType returns the same instance for an unknown id`() {
        val state = NotesState(notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, type = null),
        ))
        val same = state.setType(99L, NoteType.TASK)
        assertSame(state, same)
    }

    @Test
    fun `NotesState setType returns the same instance when the value is unchanged`() {
        val state = NotesState(notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, type = NoteType.TASK),
        ))
        val same = state.setType(1L, NoteType.TASK)
        assertSame(state, same)
    }

    @Test
    fun `NotesState clearAllTypes resets every type to null`() {
        val state = NotesState(notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, type = NoteType.TASK),
            Note(id = 2L, body = "b", createdAt = 2000L, updatedAt = 2000L, type = NoteType.JOURNAL),
        ))
        val cleared = state.clearAllTypes()
        assertNull(cleared.byId(1L)?.type)
        assertNull(cleared.byId(2L)?.type)
    }

    @Test
    fun `NotesState clearAllTypes returns the same instance when all are already null`() {
        val state = NotesState(notes = listOf(
            Note(id = 1L, body = "a", createdAt = 1000L, updatedAt = 1000L, type = null),
        ))
        val same = state.clearAllTypes()
        assertSame(state, same)
    }
}
