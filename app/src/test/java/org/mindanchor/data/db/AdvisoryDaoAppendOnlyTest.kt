package org.mindanchor.data.db

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 3 Task 2 — the DAO may not learn to mutate.
 *
 * The database triggers already refuse an UPDATE or DELETE at runtime.
 * This reads the DAO's own source so the refusal is also a build
 * failure: a mutation method added here would otherwise compile, ship,
 * and only fail on a device, in the one code path whose whole purpose is
 * that evidence cannot be rewritten.
 */
class AdvisoryDaoAppendOnlyTest {

    /** The gradle test working directory is the `app` module. */
    private val source = File("src/main/java/org/mindanchor/data/db/AdvisoryDao.kt")

    private fun statements(): List<String> {
        assertTrue("the DAO source must be readable from the test working directory", source.isFile)
        return source.readLines()
            .map { it.substringBefore("//").trim() }
            .filterNot { it.isEmpty() || it.startsWith("*") || it.startsWith("/*") }
    }

    @Test
    fun `the advisory dao declares no mutation annotation`() {
        statements().forEach { line ->
            assertTrue("AdvisoryDao must not declare @Update: $line", !line.startsWith("@Update"))
            assertTrue("AdvisoryDao must not declare @Delete: $line", !line.startsWith("@Delete"))
            assertTrue("AdvisoryDao must not declare @Upsert: $line", !line.startsWith("@Upsert"))
        }
    }

    @Test
    fun `the advisory dao issues no mutating sql`() {
        val forbidden = listOf("UPDATE ", "DELETE ", "INSERT OR REPLACE", "REPLACE INTO", "DROP ")
        statements().forEach { line ->
            val upper = line.uppercase()
            forbidden.forEach { fragment ->
                assertTrue("AdvisoryDao must not issue `$fragment`: $line", !upper.contains(fragment))
            }
        }
    }

    @Test
    fun `every advisory insert ignores conflicts rather than replacing`() {
        val inserts = statements().filter { it.startsWith("@Insert") }
        assertTrue("the DAO must declare at least one insert", inserts.isNotEmpty())
        inserts.forEach { line ->
            assertTrue(
                "an advisory insert must be IGNORE, never REPLACE or ABORT: $line",
                line.contains("OnConflictStrategy.IGNORE"),
            )
        }
    }
}
