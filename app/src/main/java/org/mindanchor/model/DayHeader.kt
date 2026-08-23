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
        return day.dayOfWeek.getDisplayName(TextStyle.FULL, currentLocale())
    }
    return absoluteDateFormatter().format(day)
}

/**
 * v0.25.11 (lint ConstantLocale): a top-level
 * `DateTimeFormatter.ofPattern(..., Locale.getDefault())` is
 * pinned to the locale at class-load time. If the user changes
 * locale in system settings after the launcher has warmed up,
 * the formatter keeps the old language — a date that should
 * render as "6 augusti" keeps rendering as "August 6".
 *
 * Building a fresh formatter per call costs a few microseconds
 * and runs only when a day label needs to be drawn, which is
 * rare (the notes list rebuilds on a refresh, not every tick).
 * [currentLocale] uses [Locale.Category.FORMAT] so a system
 * override of just the format locale is honoured.
 */
private fun currentLocale(): Locale = Locale.getDefault(Locale.Category.FORMAT)

private fun absoluteDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d", currentLocale())
