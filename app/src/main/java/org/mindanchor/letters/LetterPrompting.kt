package org.mindanchor.letters

import java.time.LocalDate

/**
 * The inputs to one letter: the user's own data from the past
 * 7 days, condensed into a small, plain-text prompt the
 * on-device model can read.
 *
 * The shape mirrors the spec's example (Section 1 of the v0.24.0
 * design doc) closely enough that the model's job is "notice the
 * pattern, write 2-3 paragraphs", not "decode this format". A
 * different shape would change the model's behaviour, not the
 * user's data.
 *
 * @property start inclusive first day of the 7-day window
 * @property end inclusive last day of the 7-day window
 * @property who5Summary the existing report's WHO-5 section,
 * or empty if the user has not scored this week
 * @property emaSummary the existing check-in average and
 * trajectory, or empty
 * @property notesSummary a small digest of the week's notes
 * (count, top themes, the most recent note's first line)
 * @property sleepSummary bedtime / wake variability, late-night
 * count, or empty
 * @property patternsLines the 0-3 lines the [org.mindanchor.report.PatternFinder]
 * emitted for the period — already in the user's own words
 * @property smallThings the user's own pre-emptive small things
 * that have helped, or empty
 */
data class WeekData(
    val start: LocalDate,
    val end: LocalDate,
    val who5Summary: String,
    val emaSummary: String,
    val notesSummary: String,
    val sleepSummary: String,
    val patternsLines: List<String>,
    val smallThings: List<String>,
)

/**
 * The system prompt and prompt builder for the daily letter.
 *
 * Different from [org.mindanchor.narrate.Prompting], which is the
 * night report paragraph. The night report writes about a single
 * day, cites research passages the launcher already has, and runs
 * on the schedule. The letter is conversational, observational,
 * and grounded in 7 days of the user's own data — a mirror, not
 * a research summary.
 *
 * The system prompt forbids the same categories the night report
 * does (clinical terms, citations, year-or-author names) and adds
 * tone rules that match the launcher's "observe, don't evaluate"
 * voice. Output is filtered by the existing
 * [org.mindanchor.narrate.NarrationGuard]; the only letter-specific
 * guard is the [WARMTH_LINE_LIMIT] and the observation-first
 * rule (the model is told to start with the pattern, not with
 * "I" or a greeting).
 */
object LetterPrompting {

    /**
     * The system prompt the model is given. Same privacy posture
     * as the night report: no name, no study, no clinical
     * category, no evaluation, no motivation.
     */
    const val SYSTEM = """You are writing a short, gentle letter to a person about their own past week.
You are not a therapist, doctor, or coach. You are a mirror that notices patterns in their own data.

Rules:
- Be observational, not evaluative. Notice, don't judge.
- Never congratulate ("great job!"). Never scold ("you should...").
- Never name a study, an author, or a year.
- Never mention a condition, a diagnosis, a symptom, or a risk.
- Pick 1-2 patterns from the data below and reflect them back in plain language.
- If the data shows something concerning, name it gently and suggest one small concrete action the person can take this week.
- 2-3 short paragraphs. Plain words. Write to the person, not about them.
- Do not begin with "I". Begin with the observation.
"""

    /**
     * The maximum number of paragraphs the model is asked for.
     * The output is filtered by [org.mindanchor.narrate.NarrationGuard]
     * which has its own length cap; this constant is the
     * instruction-budget, not the post-filter cap.
     */
    const val WARMTH_LINE_LIMIT = 3

    /**
     * Build the user-side prompt from [week]. Returns null when
     * the week is too sparse to be worth a letter — fewer than
     * two of the five "this is the user's week" surfaces have
     * anything in them. A letter written from nothing is
     * dishonest in the same way a paragraph written from an
     * empty report is, and the caller folds the null into the
     * same success outcome as "no letter today" (see
     * [org.mindanchor.report.ReportScheduler]'s shape for the
     * same reasoning).
     */
    fun build(week: WeekData): String? {
        val nonEmpty = listOf(
            week.who5Summary,
            week.emaSummary,
            week.notesSummary,
            week.sleepSummary,
        ).count { it.isNotBlank() } + week.patternsLines.size
        if (nonEmpty < 2) return null
        val sb = StringBuilder()
        sb.append("This person's past 7 days (").append(week.start).append(" to ")
            .append(week.end).append("):\n\n")
        appendIfPresent(sb, "WHO-5 pulse", week.who5Summary)
        appendIfPresent(sb, "EMA check-ins", week.emaSummary)
        appendIfPresent(sb, "Notes", week.notesSummary)
        appendIfPresent(sb, "Sleep rhythm", week.sleepSummary)
        if (week.patternsLines.isNotEmpty()) {
            sb.append("Patterns from the past 14 days:\n")
            week.patternsLines.forEach { sb.append("- ").append(it).append('\n') }
            sb.append('\n')
        }
        if (week.smallThings.isNotEmpty()) {
            sb.append("Small things you wrote down help: ")
            sb.append(week.smallThings.joinToString(", "))
            sb.append("\n\n")
        }
        sb.append("Letter (2-").append(WARMTH_LINE_LIMIT).append(" short paragraphs):\n")
        return sb.toString()
    }

    private fun appendIfPresent(sb: StringBuilder, label: String, value: String) {
        if (value.isBlank()) return
        sb.append(label).append(": ").append(value.trim()).append("\n\n")
    }
}
