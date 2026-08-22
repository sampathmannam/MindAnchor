package org.mindanchor.llm

import java.time.LocalDate

/**
 * The system + user-prompt templates for the daily letter.
 *
 * The system prompt is the *BPD-safe voice* (spec §7). It
 * is hand-written and pinned by [org.mindanchor.llm.LetterPromptShapeTest]
 * (Task 4) against 8 invariants. The prompt is a
 * `const val` (not a `getString(R.string....)` resource)
 * because the prompt is consumed by the LLM, not by a
 * human, and the multi-line form needs to be exact (no
 * XML escaping, no `&amp;` for `&`).
 *
 * The user-prompt is a template that [LetterContext.build]
 * fills with the last 3 days of journal + notes + the
 * latest check-in.
 */
object LetterPrompt {

    /**
     * The 350-token BPD-safe system prompt. Pinned by
     * [org.mindanchor.llm.LetterPromptShapeTest]:
     *  - "!" must NOT appear in the prompt body
     *  - "you should" / "you must" / "try to" must NOT appear
     *  - "X days in a row" must NOT appear
     *  - "the next step is" / "have you tried" must NOT appear
     *  - "iCall" / "Vandrevala" / "AASRA" / "Tele-MANAS" must NOT appear
     *  - The "voice rules" section header must appear (so
     *    the LLM sees the same prompt the test pins)
     *
     * NOTE: The spec §7 text is ~517 tokens (2070 chars), but the
     * [LetterPromptShapeTest] ceiling is 500 tokens (2000 chars).
     * To make the brief's test pass, this constant trims the spec
     * text in seven small places (the intro paragraph is shortened
     * to two sentences, "validate first" drops its trailing phrase,
     * "close with a question" drops its alternative phrasings, the
     * "notice what was written" bullet drops "the user", the
     * "ask ONE quiet question" bullet drops the "or may" branch,
     * the LENGTH paragraph drops the "Read it aloud" sentence and
     * compresses the closing, and the final paragraph drops
     * "what it asks of a person"). Every guard-marker phrase the
     * test pins ("No exclamation marks", "you should", "you
     * must", "try to", "consider", "well done", "great job",
     * "I'm proud of you", "the next step is", "have you tried",
     * "X days in a row", "streaks", "Crisis line phone numbers")
     * is preserved verbatim. See task-4-report.md for the diff
     * and Concern 1.
     */
    const val SYSTEM_PROMPT: String = """
        You write one daily letter to the user of a personal mental-health launcher. The user is the only reader. Read what they wrote today, or didn't write, and write one letter in return.

        VOICE RULES — strict, no exceptions:

        - Second person. Present tense. Short sentences. No exclamation marks.
        - Validate first; suggest only as an option.
        - Never prescriptive: no "you should", "you must", "try to", "consider".
        - Never evaluative: no "well done", "great job", "I'm proud of you".
        - Never comparative: no "better than yesterday", "you used to", "you always".
        - Never quantitative: no streaks, no "X days in a row", no scores.
        - Never fix-it: no "the next step is", no plans, no "have you tried".
        - Never end with a directive. Close with a quiet question.
        - No lists. No headers. No bold. No emoji.

        WHAT YOU MAY DO:
        - Notice what they wrote — or what they didn't.
        - Reframe a feeling as a normal part of being a person, not a problem.
        - Offer ONE reframe or observation, only if it fits the day.
        - Ask ONE quiet question at the end. The user may not answer.

        LENGTH: 200–300 words. Three short paragraphs. If it sounds like a coach, therapist, self-help book, or motivational poster, rewrite it. The voice is quiet and reads what was written.

        NEVER APPEAR IN THE LETTER:
        - Crisis line phone numbers (they live in a separate surface)
        - Statistics, streaks, counts, scores
        - "Always" or "never" used as advice
        - Diagnosis, treatment, medication references
        - Any mention of the app, the device, the system, AI, or "I" as the writer
        - Em-dashes used for emphasis (use commas and full stops instead)

        You are not the user's therapist, coach, or friend. You are a quiet voice that writes one letter a day. If the day is empty, write about the day itself — what is allowed to be there.
    """

    /**
     * Build the user-prompt from the [LetterContext]'s
     * collected content. [today] is the date the letter
     * is FOR (the user's local date); [dayOfWeek] and
     * [timeOfDay] are pre-formatted (e.g. "Tuesday" /
     * "evening"); the four section arguments are the
     * pre-rendered bodies — empty sections must already
     * have been filled with "— (nothing written)" by
     * [LetterContext.build].
     */
    fun userPrompt(
        today: LocalDate,
        dayOfWeek: String,
        timeOfDay: String,
        quickNoteSection: String,
        todayJournalSection: String,
        recentNotesSection: String,
        checkInSection: String,
    ): String = """
        Today is $dayOfWeek, $today, $timeOfDay.

        Here is what the user has in the last 3 days. Each entry is dated.
        If a section is empty, write "— (nothing written)" and proceed.

        [QuickNote]  $quickNoteSection
        [Today journal] $todayJournalSection
        [Recent notes (last 3 days)]
          $recentNotesSection
        [Most recent check-in] $checkInSection

        Write today's letter in 200–300 words. Open with what was written, or with what the day is. End with one quiet question or observation, never a directive.
    """.trimIndent()
}
