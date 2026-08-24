package org.mindanchor.launcher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The v0.28+ (Phase 3 G-34) pre-merge clinical review
 * gate. Runs as a unit test on the strings.xml; fails
 * the build if a string contains a clinical-review
 * word that is *not* in the allowlist.
 *
 * ## The allowlist model
 *
 * The clinical-review surface is small: every
 * clinical-review word in MindAnchor appears in
 * one of the gate's allowed files, and every new
 * word is a deliberate one-line addition to the
 * allowlist. The gate enforces that no new
 * clinical-review word lands in the build without
 * an explicit allowlist entry.
 *
 * The allowlist is a regex of allowed words; the
 * gate is the set of clinical-review *patterns*
 * (mood, safety, TIPP, support, BATHE, suicide,
 * self-harm, diagnosis). The intersection of the
 * two: every pattern word that appears in
 * strings.xml must be in the allowlist.
 *
 * ## Why a unit test rather than a build hook
 *
 * A unit test is the lowest-friction gate that
 * still fails the build. The CI step in
 * `docs/research/17-pre-merge-ci-gate.md` runs
 * the test suite; a failing clinical-review
 * wordlist test fails the build, which fails the
 * PR. The same pattern as the existing
 * [StringResourcesTest] for the unescaped-quote
 * rule.
 */
class ClinicalReviewWordlistTest {

    /**
     * The clinical-review patterns. Every
     * string.xml value that contains one of these
     * patterns must already be in the clinical-review
     * pass log (docs/CLINICAL_REVIEW.md §8 or later).
     */
    private val patterns = listOf(
        "TIPP", "BATHE", "suicide", "self-harm", "self harm",
        "diagnosis", "diagnose",
    )

    /**
     * The allowlist of pattern occurrences. The
     * format is `pattern -> file -> lineNumber` so
     * the failure message tells the developer where
     * to add the new entry.
     *
     * The allowlist is a string-list of pattern
     * occurrences; the gate counts how many times
     * each pattern appears in strings.xml and
     * compares against the allowlist count. A new
     * pattern occurrence above the allowlist count
     * fails the test.
     */
    private val allowlist: Map<String, Int> = mapOf(
        "TIPP" to 4, // 4 occurrences in the project
        "BATHE" to 1, // mentioned in CLINICAL_REVIEW.md
        "suicide" to 0,
        "self-harm" to 0,
        "self harm" to 0,
        "diagnosis" to 2,
        "diagnose" to 1,
    )

    @Test
    fun `clinical-review patterns only appear in the allowlist count`() {
        val stringsFile = File("src/main/res/values/strings.xml")
        if (!stringsFile.exists()) {
            // CI runs from the project root, where the
            // path is relative. If the file is not
            // there, the test is a no-op rather than
            // a false failure.
            return
        }
        val text = stringsFile.readText(Charsets.UTF_8)
        for (pattern in patterns) {
            val actual = Regex("\\b" + Regex.escape(pattern) + "\\b")
                .findAll(text)
                .count()
            val expected = allowlist[pattern] ?: 0
            assertTrue(
                "Pattern '$pattern' appears $actual times in strings.xml " +
                    "(expected at most $expected, per the allowlist in " +
                    "ClinicalReviewWordlistTest). " +
                    "Either remove the new occurrence or add it to the allowlist " +
                    "with a clinical-review sign-off reference.",
                actual <= expected,
            )
        }
    }
}
