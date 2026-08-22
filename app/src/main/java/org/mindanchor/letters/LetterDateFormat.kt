package org.mindanchor.letters

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The human-friendly date label used by both the inbox row and the
 * group-separator header. Same shape as the existing report's
 * [org.mindanchor.report.ReportScreen.friendlyDay] but exported as
 * `internal` so the inbox and the finding test can share one
 * implementation.
 *
 * Rules (pinned by LetterDateFormatFindingTest):
 *   - same day        -> "Today"
 *   - one day before  -> "Yesterday"
 *   - 2..7 days back  -> weekday name, English (e.g. "Monday")
 *   - 8+ days back, same year -> "MMM d" (e.g. "Jul 28")
 *   - 8+ days back, other year -> "MMM d, yyyy" (e.g. "Jul 28, 2025")
 *
 * `date` is the letter's date; `today` is the caller's idea of
 * "today" (the system clock on the device). The helper does NOT
 * read the clock itself so the test can pin every branch.
 */
internal fun friendlyLetterDate(date: LocalDate, today: LocalDate): String {
    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()
    return when {
        daysAgo == 0 -> "Today"
        daysAgo == 1 -> "Yesterday"
        daysAgo in 2..7 -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }
}
