/*
 * TestFileUtil.kt — shared path-resolution helper for FindingTest
 * suites.
 *
 * v0.29.0: 28 FindingTest classes had the same 4-line helper:
 *
 *   private fun fileAt(relative: String): File {
 *       val candidates = listOf(relative, "../$relative", "../../$relative")
 *       return candidates.map(::File).firstOrNull { it.isFile }
 *           ?: error("$relative not found from working directory ${File(".").absolutePath}.")
 *   }
 *
 * The candidates walk lets the test run from any working tree
 * position (gradle's project root, the module root, the JVM test
 * cwd) and still find the same file. The error message names
 * the working directory so a failure is debuggable without
 * checking the test runner.
 *
 * Why this is OK to share:
 *   1. The helper has no state — pure function over a String.
 *   2. The FindingTest "pin file shape" property still holds:
 *      each test reads a file from disk, asserts on a literal
 *      substring or regex, and is hermetic. The shared helper
 *      is just path resolution; the assertion logic stays in
 *      each test.
 *   3. ~140 lines of pure duplication removed across 28 files.
 *
 * Used by:
 *   - support/AcceptsFindingTest
 *   - support/DiaryCardFindingTest
 *   - support/DistressThermometerFindingTest
 *   - support/InterpersonalFindingTest
 *   - support/LetterToPartFindingTest
 *   - support/OppositeActionFindingTest
 *   - support/RadicalAcceptanceFindingTest
 *   - support/SelfCompassionFindingTest
 *   - support/SupportOrderFindingTest
 *   - (and 19 other FindingTests across launcher/, settings/,
 *     letters/, accessibility/, i18n/, report/, ci/)
 */
package org.mindanchor.testing

import java.io.File

object TestFileUtil {
    /**
     * Resolve a path relative to the test working tree.
     *
     * The candidates list walks up at most two directories,
     * which covers:
     *   - gradle's project root  (`./app/src/main/...`)
     *   - the module root        (`../app/src/main/...` from `app/`)
     *   - a deeper cwd            (`../../app/src/main/...`)
     *
     * The function returns the first existing file match. If
     * no candidate resolves, it raises an error naming the
     * working directory so a test failure is debuggable
     * without checking the runner.
     */
    fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }
}
