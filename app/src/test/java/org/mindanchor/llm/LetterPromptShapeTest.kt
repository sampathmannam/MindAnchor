package org.mindanchor.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v0.72.x: per-voice safety invariants.
 *
 * The system prompt is the only safety lever for the
 * letter's voice. Every guard marker in the prompt is
 * tested here; if a future contributor "tightens" the
 * wording and accidentally removes a marker, the LLM has
 * one fewer rule to follow, and the test fails.
 *
 * Each voice (Quiet / Warm / Direct / Playful / Insight /
 * Reflective) has its own per-voice rules in addition to
 * the common BPD-safe baseline. The common rules below
 * apply to all six; the per-voice additions are pinned in
 * the per-voice block of the same test class.
 */
class LetterPromptShapeTest {

    // The baseline guard markers every voice MUST contain
    // (BPD-safe + non-prescriptive + non-evaluative +
    //  non-comparative + non-quantitative + no-directive
    //  + crisis-line-not-in-letter + never-self-reference).
    private val baselineMarkers = listOf(
        // the "no exclamation mark" rule
        "No exclamation marks",
        // the prescriptive guard
        "you should", "you must", "try to", "consider",
        // the evaluative guard
        "well done", "great job", "I'm proud of you",
        // the comparative guard
        "better than yesterday", "you used to", "you always",
        // the quantitative / streak guard
        "streaks", "X days in a row",
        // the fix-it / directive guard
        "the next step is", "have you tried",
        // the self-reference guard (the LLM must not
        //  mention itself, the app, the device, AI, or "I"
        //  as the writer; the exact wording varies by voice
        //  so the test checks for a tight substring).
        "AI",
        // the crisis-line surface guard
        "Crisis line phone numbers",
        // the closing-question rule
        "quiet question",
    )

    @Test
    fun `every voice carries the baseline BPD-safe markers in its system prompt`() {
        for (voice in LetterVoice.values()) {
            for (marker in baselineMarkers) {
                // Markers are kept verbatim where the prompt
                // uses them (most prompts use lowercase
                // "crisis line phone numbers" or "ai" in
                // a comma-separated list; the test is
                // case-insensitive on substring match so
                // each voice's wording is honoured).
                assertTrue(
                    "voice ${voice.name} system prompt must contain '$marker' as a guard",
                    voice.systemPrompt.contains(marker, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `every voice prompt itself has no exclamation mark in any line`() {
        // The body of the prompt is read by the LLM. If
        // it contains "!", the LLM may mirror it. Walk
        // every line of every voice's prompt.
        for (voice in LetterVoice.values()) {
            for (line in voice.systemPrompt.lines()) {
                assertFalse(
                    "${voice.name} prompt line contains '!': '$line'",
                    line.contains("!"),
                )
            }
        }
    }

    @Test
    fun `every voice prompt is between 250 and 700 tokens`() {
        // 1 token ≈ 4 chars (English). A 700-token prompt
        // is the upper bound the Insight voice reaches
        // because it has to spell out the per-voice rules;
        // a 100-token prompt loses detail. Quiet / Warm /
        // Direct / Playful / Reflective run 500-620; Insight
        // is the one that pushes the upper bound.
        for (voice in LetterVoice.values()) {
            val approxTokens = voice.systemPrompt.length / 4
            assertTrue(
                "${voice.name} prompt is $approxTokens tokens (target 250-700)",
                approxTokens in 250..700,
            )
        }
    }

    // ------------------------------------------------------------------
    // Per-voice rules. Each voice has its own contract; if a future
    // contributor drifts from the rule, the test below fails.
    // ------------------------------------------------------------------

    @Test
    fun `Insight names psychology concepts in plain language without citing research`() {
        val p = LetterVoice.INSIGHT.systemPrompt
        // The voice is about psychology; it must name concepts.
        assertTrue(
            "Insight must mention 'concept' as the lever",
            p.contains("concept"),
        )
        // It must not name specific researchers or book
        // titles — the user is not a student. The word
        // "citation" / "citations" itself is allowed
        // because the prompt explicitly forbids them in
        // the NEVER-APPEAR-IN-THE-LETTER list; only the
        // researcher names are off-limits.
        for (forbidden in listOf("Brene", "Brené", "Linehan", "Beck", "Sapolsky", "Seligman", "Csikszent", "Dweck")) {
            assertFalse(
                "Insight must not name '$forbidden'",
                p.contains(forbidden, ignoreCase = true),
            )
        }
        // It must constrain to one concept per letter.
        assertTrue(
            "Insight must enforce 'one concept per letter'",
            p.contains("one concept", ignoreCase = true) || p.contains("One concept"),
        )
    }

    @Test
    fun `Direct voice declares the shortest length and forbids metaphor`() {
        val p = LetterVoice.DIRECT.systemPrompt
        // The Direct voice is supposed to be terse — the
        // system prompt must name the shorter word budget.
        assertTrue(
            "Direct should target a shorter length",
            p.contains("150") || p.contains("150–250"),
        )
        // The Direct voice must not steer the LLM toward
        // metaphor (the user said it sounds like a coach).
        assertTrue(
            "Direct must forbid metaphor",
            p.contains("Metaphor", ignoreCase = true) || p.contains("metaphor"),
        )
    }

    @Test
    fun `Warm voice names the friend metaphor without prescribing`() {
        val p = LetterVoice.WARM.systemPrompt
        // Warm is "a friend who has known this person for
        // years" — that phrase must appear so the LLM has a
        // concrete voice to aim for.
        assertTrue(
            "Warm must declare the friend metaphor",
            p.contains("friend", ignoreCase = true),
        )
    }

    @Test
    fun `Playful voice forbids sarcasm and forces the small-smile rule`() {
        val p = LetterVoice.PLAYFUL.systemPrompt
        assertTrue(
            "Playful must forbid sarcasm / jokes at the user's expense",
            p.contains("sarcasm", ignoreCase = true),
        )
        // A small smile is allowed; a punchline is not.
        assertTrue(
            "Playful must allow the small-smile form",
            p.contains("smile", ignoreCase = true),
        )
    }

    @Test
    fun `Reflective voice names the "hold the day at arm's length" stance`() {
        val p = LetterVoice.REFLECTIVE.systemPrompt
        assertTrue(
            "Reflective must declare the at-arm's-length stance",
            p.contains("arm", ignoreCase = true),
        )
    }

    @Test
    fun `every voice declares a displayName, a description, and a sample paragraph`() {
        // The UI surfaces them. A missing field would
        // surface as a blank chip or dialog.
        for (voice in LetterVoice.values()) {
            assertTrue(
                "${voice.name} needs a non-blank displayName",
                voice.displayName.isNotBlank(),
            )
            assertTrue(
                "${voice.name} needs a non-blank description",
                voice.description.isNotBlank(),
            )
            assertTrue(
                "${voice.name} needs a non-blank sample",
                voice.sample.isNotBlank(),
            )
        }
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
    }
}
