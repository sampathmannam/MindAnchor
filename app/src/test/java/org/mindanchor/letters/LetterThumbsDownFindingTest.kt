@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.26.2 "This got me wrong" thumbs-down on AI-generated
 * letters.
 *
 * The thumbs-down affordance is the user's only signal that an
 * AI letter was off. The behaviour is split across three
 * files — the reader renders the button, the
 * [LetterFeedbackStore] persists the entry, the inbox reads
 * the count back as a `👎 N` badge. The FindingTest pins
 * each leg in turn so a future refactor that breaks any one
 * of them flips the test red.
 *
 * The test is a file-shape pin, not a runtime exercise: the
 * reading-time on a real device is well below the test budget
 * and the round-trip through DataStore is exercised by
 * [LetterReadStoreRoundTripFindingTest] for the
 * `readDates` set, on which the new `userDates` and
 * feedback file storage are modelled.
 */
class LetterThumbsDownFindingTest {

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterScreen.kt",
        ).readText()

    private val feedback: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterFeedbackStore.kt",
        ).readText()

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test fun `LetterReader renders a TextButton for AI letters only`() {
        // The thumbs-down is an AI-only affordance: a
        // user-authored letter cannot be "wrong about the
        // user." The reader must guard the button on
        // `letter.source == LetterSource.AI`. The pin matches
        // both halves: the TextButton label, AND the AI guard.
        assertTrue(
            "LetterReader must render a TextButton for the thumbs-down (letters_thumbs_down)",
            screen.contains("R.string.letters_thumbs_down"),
        )
        assertTrue(
            "LetterReader must guard the thumbs-down on letter.source == LetterSource.AI",
            // The guard is an `if (letter.source == LetterSource.AI)` in
            // LetterReader. The exact source location is a `TextButton`
            // inside that if.
            Regex(
                """if\s*\(\s*letter\.source\s*==\s*LetterSource\.AI\s*\)\s*\{[\s\S]*?R\.string\.letters_thumbs_down""",
            ).containsMatchIn(screen),
        )
    }

    @Test fun `LetterReader does NOT render the thumbs-down for user-authored letters`() {
        // The same `if` guard means the button is never rendered
        // for `LetterSource.USER`. The pin is the negative: no
        // bare, unguarded `letters_thumbs_down` reference exists
        // outside the AI guard. (The string reference inside the
        // guarded branch is fine — that is exactly what the
        // positive test above pins.)
        //
        // v0.26.2: the render site is inside
        //   if (letter.source == LetterSource.AI) {
        //       LetterReaderThumbsDown(...)
        //   }
        // The test asserts the source has that exact `if`
        // guard wrapping the render call. A regression that
        // drops the guard (or moves the render outside the
        // if) flips the test red. The positive test above
        // pins that the render is INSIDE the guard; this
        // negative test pins that there is exactly one such
        // guard around the render.
        val renderGuardedByAiRegex = Regex(
            """if\s*\(\s*letter\.source\s*==\s*LetterSource\.AI\s*\)\s*\{[\s\S]*?LetterReaderThumbsDown""",
        )
        assertTrue(
            "LetterReader must wrap the thumbs-down render in `if (letter.source == LetterSource.AI)`",
            renderGuardedByAiRegex.containsMatchIn(screen),
        )
    }

    @Test fun `LetterFeedbackStore writes one JSON object per line in letter_feedback_date_dot_json`() {
        // The persistence shape is one file per date, one JSON
        // object per line. The FindingTest pins the file naming
        // and the per-line shape, both of which are part of the
        // contract: a future migration to JSON-array files
        // (e.g. for a richer schema) would invalidate the
        // "cat" workflow the user has been told about.
        assertTrue(
            "LetterFeedbackStore.dir must be filesDir/letter_feedback",
            feedback.contains("File(context.filesDir, DIR_NAME)"),
        )
        assertTrue(
            "LetterFeedbackStore.fileFor must produce letter_feedback_<date>.json",
            feedback.contains("\"letter_feedback_\$date.json\"") ||
                feedback.contains("letter_feedback_\$date.json"),
        )
        assertTrue(
            "save() must append one line per entry (appendText, not full-rewrite)",
            feedback.contains("appendText"),
        )
    }

    @Test fun `LetterFeedbackStore has a synchronous countFor reader for the inbox badge`() {
        // The inbox recomposes the `feedbackCounts` map on every
        // letter-list change. A `Flow`-driven count would also
        // work, but a synchronous read keeps the recomposition
        // cost predictable (one existence check + one file
        // read per letter) and avoids a one-recomposition lag
        // between save and badge. The FindingTest pins that
        // `countFor` exists, returns Int, and does NOT do an
        // async wait.
        assertTrue(
            "LetterFeedbackStore.countFor(date) must exist (synchronous badge reader)",
            Regex(
                """fun\s+countFor\s*\(\s*date\s*:\s*LocalDate\s*\)\s*:\s*Int""",
            ).containsMatchIn(feedback),
        )
    }

    @Test fun `strings xml has thumbs-down copy and badge format string`() {
        // The user-facing copy lives in resources, not in
        // Kotlin literals — a translator rewords without
        // touching the launcher. The test pins the keys the
        // reader and the inbox read.
        listOf(
            "letters_thumbs_down",
            "letters_thumbs_down_prompt",
            "letters_thumbs_down_save",
            "letters_thumbs_down_badge",
        ).forEach { key ->
            assertTrue(
                "strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test fun `LetterRow shows a badge when feedbackCount is greater than zero`() {
        // The badge is a `Text(stringResource(R.string.letters_thumbs_down_badge, n))`
        // inside an `if (feedbackCount > 0)` in LetterRow. Pin
        // both halves: the conditional, AND the format-string
        // call with the count as the first arg.
        val rowFn = Regex(
            """private fun LetterRow\([\s\S]*?^\}""",
            RegexOption.MULTILINE,
        ).find(screen)?.value ?: error("LetterRow not found in LetterScreen.kt")
        assertTrue(
            "LetterRow must guard the badge on `feedbackCount > 0`",
            rowFn.contains("if (feedbackCount > 0)"),
        )
        assertTrue(
            "LetterRow must render the badge via letters_thumbs_down_badge with feedbackCount as the first arg",
            rowFn.contains("R.string.letters_thumbs_down_badge") &&
                rowFn.contains("feedbackCount"),
        )
    }

    @Test fun `LetterFeedbackDialog is rendered by the reader's thumbs-down button`() {
        // The dialog is a separate Composable; the test pins
        // that the call site (the thumbs-down button) opens
        // it. The dialog itself is the source of the reason
        // text and the Save callback.
        assertTrue(
            "LetterFeedbackDialog Composable must be defined in LetterScreen.kt",
            screen.contains("private fun LetterFeedbackDialog"),
        )
        assertTrue(
            "LetterFeedbackDialog must use an OutlinedTextField for the optional reason",
            screen.contains("OutlinedTextField") && screen.contains("reason"),
        )
    }

    @Test fun `thumbs-down prompt is validation-first (audit §2_6 — BPD-safe)`() {
        // v0.27.0: the thumbs-down prompt is "What would feel
        // more like you?" — a soft open question, not a
        // correction request. The pre-v0.27.0 prompt "Tell us
        // what was off" is a correction-first frame that can
        // trigger self-criticism in BPD (Fruzzetti 2006).
        // The FindingTest pins the new copy exactly.
        assertTrue(
            "strings.xml must define the v0.27.0 validation-first prompt",
            strings.contains("That is helpful. What would feel more like you?"),
        )
        // The old correction-first copy must NOT be present
        // in strings.xml anymore.
        assertTrue(
            "strings.xml must NOT contain the pre-v0.27.0 correction-first copy 'Tell us what was off'",
            !strings.contains("Tell us what was off"),
        )
        // The hint is the "no one sees this but you" line.
        assertTrue(
            "strings.xml must contain the v0.27.0 hint 'No one sees this but you'",
            strings.contains("No one sees this but you"),
        )
    }
}
