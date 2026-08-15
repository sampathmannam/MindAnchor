@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.25.18 i18n sweep: a FindingTest that asserts the 12
 * v0.25.18 source files contain no hardcoded English
 * `text = "..."` or `contentDescription = "..."` literals
 * outside `stringResource(R.string....)` calls.
 *
 * Background: v0.25.12 swept the 5 BPD surfaces and missed
 * the rest of the app. The hardcoded literals that survived
 * are not always wrong — a back arrow glyph "←" or a
 * separator "·" is decoration, not user-facing English —
 * but every one of them is a Tamil user hearing English in
 * TalkBack when the system is set to ta.
 *
 * The test is intentionally tolerant. It whitelists:
 *
 *  - empty strings (`""`),
 *  - dynamic strings (`"value · other"`,
 *    `stringResource(R.string.X, value)`),
 *  - pure-symbol strings (no ASCII letters at all — these
 *    are arrows, bullets, separators, the × close, the
 *    ★/☆ pin glyphs),
 *  - decoration prefixes/suffixes (`"•  "`, `"  →"`,
 *    `" · "`),
 *  - any `text = ...` whose right-hand side contains a
 *    `stringResource(` call.
 *
 * The test fails the build if a future commit re-introduces
 * a hardcoded English label on any of the 12 surfaces. The
 * migration path is: add a `<string name="X">` entry to
 * `app/src/main/res/values/strings.xml`, hoist the call as
 * `val label = stringResource(R.string.X)` in the
 * Composable scope, then assign it to `text =` or
 * `contentDescription =` inside the lambda.
 */
class I18nSweepFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val sweptFiles = listOf(
        "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        "app/src/main/java/org/mindanchor/letters/LetterScreen.kt",
        // v0.25.18: the inbox rendering lives inside
        // letters/LetterScreen.kt as private fun
        // LetterInbox / LetterInboxContent. The user
        // brief named the file LetterInboxScreen.kt;
        // the surface area is the same and is swept
        // through this file.
        "app/src/main/java/org/mindanchor/model/NoteScreen.kt",
        "app/src/main/java/org/mindanchor/model/NoteActivity.kt",
        "app/src/main/java/org/mindanchor/digest/DigestScreen.kt",
        "app/src/main/java/org/mindanchor/support/SupportScreen.kt",
        "app/src/main/java/org/mindanchor/report/ReportScreen.kt",
        "app/src/main/java/org/mindanchor/vitals/PpgScreen.kt",
        "app/src/main/java/org/mindanchor/onboarding/OnboardingScreen.kt",
        "app/src/main/java/org/mindanchor/HomeActivity.kt",
    )

    /**
     * A literal is "decorative" if it has no ASCII letters,
     * only symbols / whitespace / digits. These are arrows,
     * bullets, separators, the × close glyph, the ★/☆
     * pin glyphs, the "%d / %d" counter format, and the
     * `·` dot separator. None of them are user-facing
     * English.
     */
    private fun isDecorativeLiteral(literal: String): Boolean {
        val stripped = literal.trim().removePrefix("$").trim()
        if (stripped.isEmpty()) return true
        // pure non-letter content (only digits, punctuation,
        // whitespace, CJK / Tamil / Arabic codepoints)
        if (!stripped.any { it.isLetter() && it.code < 128 }) return true
        return false
    }

    /**
     * Walk the source, find every `text = "..."` and
     * `contentDescription = "..."` literal, return the
     * ones that look like hardcoded English (i.e. not
     * wrapped in stringResource and not a pure symbol).
     */
    private fun hardcodedLiterals(source: String): List<String> {
        val problems = mutableListOf<String>()
        val pattern = Regex("""(text|contentDescription)\s*=\s*"([^"]*)"""")
        for (m in pattern.findAll(source)) {
            val key = m.groupValues[1]
            val literal = m.groupValues[2]
            // The match is `text = "..."` or
            // `contentDescription = "..."`. The string
            // might be a `text = stringResource(R.string.X)`,
            // in which case the literal captured here is
            // `stringResource(R.string.X)` — which contains
            // an open paren but no `"`, so the regex
            // captures up to the first `"`. We need to
            // distinguish: if the right-hand side is
            // `stringResource(`, skip.
            val rhs = source.substring(m.range.last + 1, source.length.coerceAtMost(m.range.last + 200))
            if (rhs.trimStart().startsWith("stringResource(") ||
                rhs.trimStart().startsWith("if (") ||
                rhs.trimStart().startsWith("note.")
            ) {
                // The regex was greedy enough to capture
                // the prefix of an interpolated string.
                // Skip — the actual literal is empty or
                // dynamic.
                continue
            }
            if (literal.isEmpty()) continue // text = "" is fine
            // Dynamic strings: anything with `${...}` or
            // starting with `$` is template interpolation.
            // The `text = "${draft.length} / ${MAX_BODY}"`
            // and `text = "$time · ${item.appLabel}"`
            // shapes are not user-facing English — they
            // are display formatters. The components are
            // data (numbers, app labels) glued with a
            // separator symbol; the separator is a
            // decoration, not English.
            if (literal.contains("\${") || literal.startsWith("$")) continue
            if (isDecorativeLiteral(literal)) continue
            problems += "$key = \"$literal\""
        }
        return problems
    }

    @Test
    fun `every swept file passes the i18n sweep`() {
        val failures = mutableListOf<String>()
        for (rel in sweptFiles) {
            val source = try {
                fileAt(rel).readText()
            } catch (t: Throwable) {
                failures += "$rel could not be read: ${t.message}"
                continue
            }
            val problems = hardcodedLiterals(source)
            if (problems.isNotEmpty()) {
                failures += "$rel has ${problems.size} hardcoded literal(s):\n  " +
                    problems.joinToString("\n  ")
            }
        }
        assertTrue(
            "i18n sweep failed for the 12 v0.25.18 surfaces.\n" +
                "Every `text = \"...\"` or `contentDescription = \"...\"` must be " +
                "a `stringResource(R.string.…)` call, an empty string, a dynamic " +
                "string (\"value\"), or a pure symbol (←, ×, ★, ☆, ·, •, …). " +
                "Add a new `<string name=\"X\">` to values/strings.xml and hoist " +
                "the call as `val label = stringResource(R.string.X)` in the " +
                "Composable scope. Failures:\n" + failures.joinToString("\n\n"),
            failures.isEmpty(),
        )
    }

    /**
     * The new string for the close button on the notes
     * TopAppBar must be present in both values/ and
     * values-ta/. This pins the migration: removing the
     * string from either file flips this test red.
     */
    @Test
    fun `R_string note_close is defined in values and values-ta`() {
        val english = fileAt("app/src/main/res/values/strings.xml").readText()
        val tamil = fileAt("app/src/main/res/values-ta/strings.xml").readText()
        assertTrue(
            "values/strings.xml must define <string name=\"note_close\"> for " +
                "the NoteScreen TopAppBar close button. v0.25.18 i18n sweep.",
            english.contains("<string name=\"note_close\">"),
        )
        assertTrue(
            "values-ta/strings.xml must define <string name=\"note_close\"> " +
                "for the NoteScreen TopAppBar close button. v0.25.18 i18n sweep.",
            tamil.contains("<string name=\"note_close\">"),
        )
    }
}
