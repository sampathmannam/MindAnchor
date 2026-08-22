package org.mindanchor.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The label a day section in the notes list shows. The launcher's
 * day labels are deliberately small: "Today" for the user's
 * local today, "Yesterday" for the day before, the day-of-week
 * (e.g. "Wednesday") for the past week, and the absolute date
 * (e.g. "August 6") for anything older.
 *
 * The label is computed from the user's local today — passing
 * `today` explicitly lets the caller pin the comparison (the
 * UI rebuilds the list on a refresh, not on every clock tick,
 * so the label is consistent within one screen render).
 *
 * Pure function.
 */
fun daySectionLabel(day: LocalDate, today: LocalDate): String {
    if (day == today) return "Today"
    if (day == today.minusDays(1)) return "Yesterday"
    if (day.isAfter(today.minusDays(7))) {
        return day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    return ABSOLUTE_DATE_FORMATTER.format(day)
}

private val ABSOLUTE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
