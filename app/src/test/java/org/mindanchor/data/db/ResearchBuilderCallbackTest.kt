package org.mindanchor.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 7 — every [AnchorDatabase] builder in this repository
 * must install the research-immutability callback.
 *
 * The hole this closes is quiet and permanent. Room's generated
 * `createAllTables` carries no triggers, so a builder that forgets the
 * callback produces `research_ledger_events` and `study_phases` that are
 * not append-only — and nothing complains. On the production path that
 * would be every fresh install, forever, because `onCreate` never runs
 * twice and `MIGRATION_6_7` never runs on a database already at version 7.
 * In a test it is worse in a different way: a restore or merge path that
 * rewrote the ledger would pass its own tests.
 *
 * Source text is the only place this is checkable. The builders are spread
 * across a dozen test files and one production call site, and no runtime
 * assertion in one of them can speak for the others.
 */
class ResearchBuilderCallbackTest {

    /** The gradle test working directory is the `app` module. */
    private val sourceRoots = listOf(File("src/main/java"), File("src/test/java"), File("src/androidTest/java"))

    private val builderPattern = Regex(
        """Room\.(inMemory)?[dD]atabaseBuilder\s*\([^)]*AnchorDatabase::class\.java[^)]*\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private fun kotlinFiles(): List<File> = sourceRoots
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun `every AnchorDatabase builder installs the immutability callback`() {
        val files = kotlinFiles()
        assertTrue("the source roots must be readable from the test working directory", files.isNotEmpty())

        val offenders = mutableListOf<String>()
        var builders = 0
        files.forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            builderPattern.findAll(text).forEach { match ->
                builders += 1
                // The chained call has to appear before this builder is
                // built. Look at the text from the builder to the next
                // `.build()`, which is where every call site puts it.
                val tail = text.substring(match.range.last)
                val chain = tail.substringBefore(".build()", tail.take(CHAIN_SEARCH_LIMIT))
                if (!chain.contains("withResearchImmutability")) {
                    offenders += "${file.path} at offset ${match.range.first}"
                }
            }
        }
        assertTrue("this repository must contain AnchorDatabase builders", builders > 0)
        assertEquals(
            "every AnchorDatabase builder must chain .withResearchImmutability() before .build(); " +
                "without it the research tables are not append-only",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the callback installs triggers on open as well as on create`() {
        val source = File("src/main/java/org/mindanchor/data/db/AnchorDatabase.kt").readText(Charsets.UTF_8)
        val callback = source.substringAfter("val researchImmutabilityCallback").substringBefore("@Volatile")
        assertTrue("onCreate must install the triggers", callback.contains("override fun onCreate"))
        assertTrue(
            "onOpen must install them too, so a database created without the callback self-heals",
            callback.contains("override fun onOpen"),
        )
        assertTrue(
            "recursive triggers must be on, or INSERT OR REPLACE deletes a row without firing the trigger",
            callback.contains("PRAGMA recursive_triggers = ON"),
        )
    }

    private companion object {
        /** Enough text to contain any realistic builder chain. */
        private const val CHAIN_SEARCH_LIMIT = 400
    }
}
