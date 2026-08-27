package org.mindanchor.llm

import java.time.LocalDate

/**
 * v0.72.x: the system prompt moved into [LetterVoice];
 * this object keeps only the user-prompt template the
 * letter pipeline needs. The shape is the same; the
 * system side now varies by voice choice.
 */
object LetterPrompt {

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
