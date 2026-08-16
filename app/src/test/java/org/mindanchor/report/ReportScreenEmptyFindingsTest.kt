package org.mindanchor.report

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * Finding test for the v0.22.2 P3 fix in [ReportScreen].
 *
 * v0.22.0 and v0.22.1 rendered the report screen with a "Nothing
 * stood out" line whenever the report was empty, regardless of
 * whether the pattern search had actually run. On a fresh install
 * the screen showed the line next to the facts-empty line
 * "Nothing arrived that day — no reading, no wearable data, no
 * check-in", and a user reading the two together could not tell
 * whether the app had looked for patterns or not.
 *
 * The fix gates the "Nothing stood out" line on the facts being
 * non-empty: if the day has no measurements either, the search
 * had no signal data to look for, and the only honest answer the
 * screen can give is the facts-empty line. The report_quiet line
 * only earns its place when the search actually ran.
 *
 * What this test checks:
 *  1. The `current.isEmpty` branch in [ReportScreen] gates the
 *     `R.string.report_quiet` Text on `facts.isNotEmpty()`.
 *  2. The gate is the exact form `facts != null && facts.isNotEmpty()`
 *     so a refresh that flips facts to empty (rather than null)
 *     still hides the line.
 *  3. The original `R.string.report_quiet` is still rendered for
 *     the genuine-search-ran-and-found-nothing case.
 *  4. The unchanged R.string.facts_empty is still in place — the
 *     fix is additive, not a copy.
 */
class ReportScreenEmptyFindingsTest {

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/report/ReportScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `the empty-report branch gates report_quiet on facts being non-empty`() {
        // The pre-fix shape was a flat `current.isEmpty -> Text(report_quiet)`.
        // The post-fix shape adds a facts-not-empty guard. The exact
        // form matters: a refresh that leaves facts non-null but empty
        // (rather than null) must still hide the line, so the gate
        // must be `facts != null && facts.isNotEmpty()`.
        val emptyBranch = Regex(
            "current\\.isEmpty\\s*->\\s*\\{[^}]*report_quiet",
            RegexOption.DOT_MATCHES_ALL,
        ).find(screen)?.value ?: error(
            "Could not locate the `current.isEmpty -> { ... report_quiet ... }` " +
                "branch in ReportScreen.kt",
        )
        assertTrue(
            "The empty-report branch must gate `R.string.report_quiet` on " +
                "`facts != null && facts.isNotEmpty()`. Without this guard, " +
                "the screen says 'Nothing stood out' next to 'Nothing arrived " +
                "that day' on a fresh install — a lie of omission, since the " +
                "search had no data to look for. " +
                "Branch: $emptyBranch",
            emptyBranch.contains("facts != null") &&
                emptyBranch.contains("facts.isNotEmpty()"),
        )
    }

    @Test
    fun `the line is still rendered when facts are present and the report is empty`() {
        // The fix must not over-correct: when the day has measurements
        // and the search genuinely found nothing, "Nothing stood out"
        // is the honest message. The branch must still call
        // Text(stringResource(R.string.report_quiet)) inside the
        // `if (facts != null && facts.isNotEmpty())` block — not
        // suppress the line unconditionally.
        val branchStart = screen.indexOf("current.isEmpty ->")
        if (branchStart < 0) error("Could not locate `current.isEmpty ->` in ReportScreen.kt")
        val slice = screen.substring(
            branchStart,
            minOf(branchStart + 2000, screen.length),
        )
        assertTrue(
            "The empty-report branch must still render " +
                "`R.string.report_quiet` when facts are non-empty. " +
                "Suppressing it unconditionally would hide the legitimate " +
                "'search ran, found nothing' message. " +
                "Slice: $slice",
            slice.contains("stringResource(R.string.report_quiet)"),
        )
    }

    @Test
    fun `the report_quiet string still says Nothing stood out for the legitimate case`() {
        // String-level pin: the wording still exists for the case
        // where the search genuinely ran and found nothing. A future
        // rewrite that rewords the string without the finding test
        // catching it is also caught here.
        assertTrue(
            "R.string.report_quiet must still exist with the original " +
                "wording — it is rendered when the search ran over " +
                "the user's history and found nothing, which is a " +
                "genuine and common outcome. Do not delete or reword it.",
            strings.contains("report_quiet") &&
                strings.contains("Nothing stood out"),
        )
    }

    @Test
    fun `the facts_empty string is unchanged so the 'no data' message still carries`() {
        // The fix does not duplicate the "nothing arrived" message
        // into the patterns slot — it relies on the existing
        // facts_empty string being rendered below. That string must
        // still exist.
        assertTrue(
            "R.string.facts_empty must still exist. The P3 fix relies " +
                "on the facts-empty line below being the only honest " +
                "message the screen can show when both the report and " +
                "today's facts are empty. Do not delete or reword it.",
            strings.contains("facts_empty") &&
                strings.contains("Nothing arrived that day"),
        )
    }
}
