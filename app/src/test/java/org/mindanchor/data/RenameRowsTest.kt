package org.mindanchor.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The renames map is persisted as newline-delimited `component\tlabel` rows,
 * so the characters that delimit the format must never survive inside a label.
 *
 * The label is whatever the person typed (or pasted) into the rename field, so
 * this is a user-controlled string flowing into a hand-rolled format. The tests
 * that matter are the ones where it carries a delimiter: a newline used to end
 * the row early, which silently truncated the label and — because the tail is
 * read back as its own row — could rename a *different* app than the one the
 * person long-pressed.
 */
class RenameRowsTest {

    private val clock = "com.android.deskclock/.DeskClock"
    private val other = "com.android.chrome/.Main"

    @Test
    fun `round-trips an ordinary label`() {
        val stored = RenameRows.upsert(null, clock, "Alarm")
        assertEquals(mapOf(clock to "Alarm"), RenameRows.decode(stored))
    }

    @Test
    fun `a newline in a label cannot end the row early`() {
        val stored = RenameRows.upsert(null, clock, "Alarm\nclock")
        assertEquals(mapOf(clock to "Alarm clock"), RenameRows.decode(stored))
    }

    @Test
    fun `a newline in a label cannot rename a different app`() {
        // The injection shape: everything after the newline would otherwise be
        // read back as a row of its own, renaming a component the person never
        // touched.
        val stored = RenameRows.upsert(null, clock, "Alarm\n$other\tHijacked")
        assertEquals(mapOf(clock to "Alarm $other Hijacked"), RenameRows.decode(stored))
    }

    @Test
    fun `a carriage return is a row terminator too`() {
        // lineSequence() splits on a lone \r as well, so sanitizing only \n
        // leaves the same hole open.
        val stored = RenameRows.upsert(null, clock, "Alarm\r$other\tHijacked")
        assertEquals(mapOf(clock to "Alarm $other Hijacked"), RenameRows.decode(stored))
    }

    @Test
    fun `a tab in a label cannot shift the key boundary`() {
        val stored = RenameRows.upsert(null, clock, "Alarm\tclock")
        assertEquals(mapOf(clock to "Alarm clock"), RenameRows.decode(stored))
    }

    @Test
    fun `renaming one app leaves the others alone`() {
        val first = RenameRows.upsert(null, clock, "Alarm")
        val both = RenameRows.upsert(first, other, "Browser")
        assertEquals(mapOf(clock to "Alarm", other to "Browser"), RenameRows.decode(both))
    }

    @Test
    fun `re-renaming replaces rather than duplicates`() {
        val first = RenameRows.upsert(null, clock, "Alarm")
        val second = RenameRows.upsert(first, clock, "Wake up")
        assertEquals(mapOf(clock to "Wake up"), RenameRows.decode(second))
    }

    @Test
    fun `a blank label clears the rename`() {
        val first = RenameRows.upsert(null, clock, "Alarm")
        assertEquals(emptyMap<String, String>(), RenameRows.decode(RenameRows.upsert(first, clock, null)))
        assertEquals(emptyMap<String, String>(), RenameRows.decode(RenameRows.upsert(first, clock, "   ")))
    }

    @Test
    fun `a label made only of delimiters clears rather than writes an empty row`() {
        assertEquals(emptyMap<String, String>(), RenameRows.decode(RenameRows.upsert(null, clock, "\n\t")))
    }

    @Test
    fun `encode sanitizes the restore path the same way`() {
        val encoded = RenameRows.encode(mapOf(clock to "Alarm\n$other\tHijacked"))
        assertEquals(mapOf(clock to "Alarm $other Hijacked"), RenameRows.decode(encoded))
    }

    @Test
    fun `decode ignores rows with no key`() {
        assertEquals(emptyMap<String, String>(), RenameRows.decode("\tno-component\nplain-line\n"))
    }
}
