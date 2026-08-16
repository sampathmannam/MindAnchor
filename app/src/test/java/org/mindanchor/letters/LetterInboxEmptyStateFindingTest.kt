@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.26.2: the inbox's empty state.
 *
 * v0.25.x shipped a single line of `Text` reading "No letters
 * yet. The first arrives at 8 AM…". v0.26.2 replaces that
 * with a friendlier three-piece layout — an envelope icon, a
 * one-line title, a one-line body — with a primary "Write a
 * letter now" button that opens the user-authored composer.
 * The AI generation is opt-in via a secondary "Use AI" button
 * below the primary one.
 *
 * The FindingTest pins the icon, the title, the body, the
 * button, and the AI affordance so a future refactor that
 * drops any of them (or, worse, regresses to the v0.25.x
 * single-paragraph shape) flips the test red.
 *
 * The icon is the Unicode envelope `✉️` (U+2709 + U+FE0F).
 * A regression to a hard-coded English paragraph — the
 * shape the v0.25.x empty state had — is the most likely
 * failure mode, so the test asserts the icon-string usage.
 */
class LetterInboxEmptyStateFindingTest {

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test fun `LetterInboxEmptyState Composable exists in LetterScreen_kt`() {
        // The empty-state is a separate Composable so the
        // non-empty path stays under the LongMethod
        // threshold. Pin the function's existence.
        assertTrue(
            "LetterScreen.kt must define a LetterInboxEmptyState Composable",
            screen.contains("private fun LetterInboxEmptyState"),
        )
    }

    @Test fun `LetterInboxEmptyState is rendered when letters is empty`() {
        // The dispatch is in LetterInboxContent: when
        // `letters.isEmpty()`, render LetterInboxEmptyState
        // instead of the rows. Pin the call site. The slice
        // is 1200 chars to cover the long block-comment that
        // sits between the `if` and the call site in v0.26.2
        // — a future refactor that trims the comment would
        // shrink the slice, but the call site is still
        // inside the branch.
        val isEmptyIdx = screen.indexOf("if (letters.isEmpty())")
        assertTrue(
            "LetterInboxContent must branch on letters.isEmpty()",
            isEmptyIdx >= 0,
        )
        val slice = screen.substring(isEmptyIdx, minOf(isEmptyIdx + 1200, screen.length))
        assertTrue(
            "LetterInboxContent must call LetterInboxEmptyState inside the empty branch",
            slice.contains("LetterInboxEmptyState"),
        )
    }

    @Test fun `LetterInboxEmptyState renders the envelope icon, title, body, and a Write a letter now button`() {
        // The Composable body pins all four pieces. The icon
        // is `letters_empty_icon` (the Unicode envelope); the
        // title is `letters_empty_title`; the body is
        // `letters_empty_body` (or the no-model variant);
        // the primary button is `letters_write_now`.
        val emptyFn = Regex(
            """private fun LetterInboxEmptyState\([\s\S]*?^\}""",
            RegexOption.MULTILINE,
        ).find(screen)?.value ?: error("LetterInboxEmptyState not found in LetterScreen.kt")
        listOf(
            "R.string.letters_empty_icon",
            "R.string.letters_empty_title",
            "R.string.letters_empty_body",
            "R.string.letters_write_now",
        ).forEach { key ->
            assertTrue(
                "LetterInboxEmptyState must render $key",
                emptyFn.contains(key),
            )
        }
    }

    @Test fun `Write a letter now button forwards onWriteNow to the inbox`() {
        // v0.31.0: the empty-state button passes the
        // inbox-supplied `onWriteNow` callback straight through.
        // The inbox wires that callback to "open the composer
        // dialog" — pre-v0.31.0 the inbox wired it to
        // `onSaveUserLetter(today, "")`, but that called
        // LetterStore.saveUserLetter with a blank body, which
        // the store silently rejected at line 246 (`if
        // (body.isBlank()) return`). The user's tap was a
        // no-op. v0.31.0: the composer dialog is what they
        // wanted all along — a place to type a body, then
        // save it. The button just opens it.
        //
        // The first `onWriteNow =` in the file is the inbox's
        // own wiring (`onWriteNow = { composerOpen.value = ...}`)
        // — the *second* is the call site in LetterInboxContent
        // that hands the parameter to the empty state. The
        // test pins the second match, which is the one that
        // matters for the empty-state contract.
        val first = screen.indexOf("onWriteNow =")
        val second = screen.indexOf("onWriteNow =", first + 1)
        assertTrue(
            "LetterInboxContent must pass an onWriteNow callback to LetterInboxEmptyState",
            second > 0,
        )
        val slice = screen.substring(second, minOf(second + 300, screen.length))
        assertTrue(
            "LetterInboxEmptyState.onWriteNow must be wired to the inbox's onWriteNow parameter " +
                "(not inlined to onSaveUserLetter(today, ...))",
            slice.contains("onWriteNow = onWriteNow"),
        )
    }

    @Test fun `Use AI is a secondary button gated on modelFits`() {
        // The AI generation is opt-in; the affordance is a
        // secondary `TextButton` (not the primary `Button`).
        // The test pins both: the affordance exists, and it
        // is gated on `enabled = modelFits` so a user without
        // a runnable model does not see a "Use AI" button
        // that does nothing.
        val emptyFn = Regex(
            """private fun LetterInboxEmptyState\([\s\S]*?^\}""",
            RegexOption.MULTILINE,
        ).find(screen)?.value ?: error("LetterInboxEmptyState not found in LetterScreen.kt")
        assertTrue(
            "LetterInboxEmptyState must render the AI affordance (letters_use_ai)",
            emptyFn.contains("R.string.letters_use_ai"),
        )
        // The TextButton block has `enabled = modelFits` as
        // one of its arguments. The regex is permissive
        // (matches a slice that contains both); the test
        // exists to pin the gate is in place, not the exact
        // form.
        val useAiIdx = emptyFn.indexOf("R.string.letters_use_ai")
        val around = emptyFn.substring(
            maxOf(0, useAiIdx - 200),
            minOf(useAiIdx + 200, emptyFn.length),
        )
        assertTrue(
            "Use AI button must be gated on modelFits (enabled = modelFits). Slice: $around",
            around.contains("enabled = modelFits"),
        )
    }

    @Test fun `strings xml has the empty-state keys (icon, title, body, write, use_ai)`() {
        // The user-facing copy lives in resources, not in
        // Kotlin literals. The test pins the keys the
        // empty-state Composable reads.
        listOf(
            "letters_empty_icon",
            "letters_empty_title",
            "letters_empty_body",
            "letters_write_now",
            "letters_use_ai",
        ).forEach { key ->
            assertTrue(
                "strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }
}
