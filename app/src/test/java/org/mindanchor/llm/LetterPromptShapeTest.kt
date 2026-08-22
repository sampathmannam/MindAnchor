package org.mindanchor.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The system prompt is the only safety lever for the
 * letter's voice. Every guard marker in the prompt is
 * tested here; if a future contributor "tightens" the
 * wording and accidentally removes a marker, the LLM has
 * one fewer rule to follow, and the test fails.
 */
class LetterPromptShapeTest {

    @Test
    fun `system prompt forbids exclamation marks in the LETTER body guidance`() {
        // The prompt says "No exclamation marks" — the
        // test confirms the rule is in the prompt itself
        // (so the LLM can read it) AND that the prompt
        // doesn't accidentally include any "!" in its own
        // text.
        assertTrue(
            "system prompt must include the no-! rule",
            LetterPrompt.SYSTEM_PROMPT.contains("No exclamation marks"),
        )
        // The prompt's own body must not contain "!" — the
        // exception is the dash in the "—" character (not
        // "!"). We check for the ASCII "!" specifically.
        for (line in LetterPrompt.SYSTEM_PROMPT.lines()) {
            assertFalse(
                "system prompt line contains '!': '$line'",
                line.contains("!"),
            )
        }
    }

    @Test
    fun `system prompt includes the prescriptive-words guard`() {
        for (phrase in listOf("you should", "you must", "try to", "consider")) {
            assertTrue(
                "system prompt must mention '$phrase' as forbidden",
                LetterPrompt.SYSTEM_PROMPT.contains(phrase),
            )
        }
    }

    @Test
    fun `system prompt includes the streak and score guard`() {
        assertTrue(
            LetterPrompt.SYSTEM_PROMPT.contains("streaks"),
        )
        assertTrue(
            LetterPrompt.SYSTEM_PROMPT.contains("X days in a row"),
        )
    }

    @Test
    fun `system prompt includes the fix-it guard`() {
        for (phrase in listOf("the next step is", "have you tried")) {
            assertTrue(
                "system prompt must mention '$phrase' as forbidden",
                LetterPrompt.SYSTEM_PROMPT.contains(phrase),
            )
        }
    }

    @Test
    fun `system prompt includes the crisis-line guard`() {
        // The prompt must explicitly tell the LLM not to
        // include crisis-line numbers — they live in a
        // separate surface (the journal's sticky bar).
        assertTrue(
            "system prompt must include the 'crisis line' guard",
            LetterPrompt.SYSTEM_PROMPT.contains("Crisis line phone numbers"),
        )
    }

    @Test
    fun `system prompt forbids evaluative phrases`() {
        for (phrase in listOf("well done", "great job", "I'm proud of you")) {
            assertTrue(
                "system prompt must mention '$phrase' as forbidden",
                LetterPrompt.SYSTEM_PROMPT.contains(phrase),
            )
        }
    }

    @Test
    fun `system prompt is between 250 and 500 tokens`() {
        // 350 tokens is the spec target; a 600-token
        // prompt burns Groq's free-tier cap for no
        // benefit, a 100-token prompt loses detail.
        // We approximate: 1 token ≈ 4 chars (English).
        val approxTokens = LetterPrompt.SYSTEM_PROMPT.length / 4
        assertTrue(
            "system prompt is $approxTokens tokens (target 250-500)",
            approxTokens in 250..500,
        )
    }

    @Test
    fun `userPrompt template includes the 4 section markers and a today line`() {
        val prompt = LetterPrompt.userPrompt(
            today = LocalDate.of(2026, 8, 22),
            dayOfWeek = "Saturday",
            timeOfDay = "evening",
            quickNoteSection = "— (nothing written)",
            todayJournalSection = "Today I went for a walk.",
            recentNotesSection = "— (nothing written)",
            checkInSection = "— (no check-in yet)",
        )
        assertTrue(prompt.startsWith("Today is Saturday, 2026-08-22, evening."))
        assertTrue(prompt.contains("[QuickNote]"))
        assertTrue(prompt.contains("[Today journal]"))
        assertTrue(prompt.contains("[Recent notes (last 3 days)]"))
        assertTrue(prompt.contains("[Most recent check-in]"))
        assertTrue(prompt.contains("200–300 words"))
    }
}
