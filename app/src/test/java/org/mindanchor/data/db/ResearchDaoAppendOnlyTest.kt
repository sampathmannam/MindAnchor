package org.mindanchor.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 7 — the research tables are append-only, and this is the
 * first of the three guards that make that true rather than intended.
 *
 * This one is a **source-level** check: Room's `@Query`, `@Update` and
 * `@Delete` annotations are declared with binary retention, so runtime
 * reflection cannot see them. Reading the file is the same technique
 * `ClinicalReviewWordlistTest` already uses on `strings.xml`, and it
 * catches the mistake that actually happens — somebody adding a
 * convenience `@Query("DELETE FROM ...")` or switching an insert to
 * `REPLACE`, which SQLite implements as delete-then-insert and which would
 * therefore trip the database triggers at runtime instead of at review.
 *
 * The other two guards are the `BEFORE UPDATE` / `BEFORE DELETE` triggers
 * (`ResearchImmutabilityTest`) and the ledger's own hash chain.
 */
class ResearchDaoAppendOnlyTest {

    /** The gradle test working directory is the `app` module. */
    private val daoSource = File("src/main/java/org/mindanchor/data/db/ResearchDao.kt")

    private fun source(): String {
        assertTrue("ResearchDao.kt must be readable from the test working directory", daoSource.isFile)
        return daoSource.readText(Charsets.UTF_8)
    }

    /** Everything inside a `@Query("...")`, with the surrounding annotation stripped. */
    private fun queryStrings(text: String): List<String> =
        Regex("""@Query\(\s*"((?:[^"\\]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `the dao declares no mutating annotation`() {
        val text = source()
        assertFalse("ResearchDao must declare no @Update", text.contains("@Update"))
        assertFalse("ResearchDao must declare no @Delete", text.contains("@Delete"))
    }

    @Test
    fun `every insert ignores conflicts rather than replacing`() {
        val text = source()
        val inserts = Regex("""@Insert\(([^)]*)\)""").findAll(text).map { it.groupValues[1] }.toList()
        assertTrue("ResearchDao must declare at least one @Insert", inserts.isNotEmpty())
        inserts.forEach { arguments ->
            assertTrue(
                "an @Insert on an append-only table must use IGNORE, not $arguments — " +
                    "REPLACE is a delete followed by an insert, which the immutability triggers reject",
                arguments.contains("OnConflictStrategy.IGNORE"),
            )
        }
        assertFalse("ResearchDao must declare no bare @Insert", Regex("""@Insert\s*\n""").containsMatchIn(text))
    }

    @Test
    fun `no query mutates a row`() {
        val queries = queryStrings(source())
        assertTrue("ResearchDao must declare queries", queries.isNotEmpty())
        val mutating = Regex("""(?i)\b(update|delete|insert|drop|alter)\b""")
        queries.forEach { query ->
            assertFalse("a research query must only read: $query", mutating.containsMatchIn(query))
        }
    }

    @Test
    fun `the dao reads only the two research tables`() {
        val queries = queryStrings(source())
        val tables = Regex("""(?i)\bFROM\s+([a-z_]+)""").findAll(queries.joinToString(" "))
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(setOf("research_ledger_events", "study_phases"), tables)
    }
}
