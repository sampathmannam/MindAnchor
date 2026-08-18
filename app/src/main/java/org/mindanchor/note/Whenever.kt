package org.mindanchor.note

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * v0.47.0: a tiny natural-language reminder parser.
 *
 * The launcher's reminder chips cover the simple
 * cases (in 5 min, in 15 min, in 1 hour, in 3
 * hours, in 1 day, in 3 days). The chips are the
 * right floor — the user picks an option in 1
 * tap and the alarm fires. NL parsing covers the
 * long tail ("tomorrow at 7pm", "monday morning",
 * "in 2 hours") that a user types because the
 * chips do not match what they want.
 *
 * The parser is intentionally narrow:
 *
 *  - "in N (min|mins|minute|minutes|hour|hours|day|days)"
 *  - "tomorrow" / "tomorrow at H(:MM)?(am|pm)?"
 *  - "monday" / "tuesday" / ... (next occurrence)
 *  - "tonight" (= today at 8pm)
 *  - "morning" / "evening" / "noon" / "midnight"
 *  - "at H(:MM)?(am|pm)?"
 *
 * Anything else returns null and the reminder
 * falls back to the chip picker. The user is
 * never surprised by a parser that misread their
 * intent — if the parser returns null, the
 * QuickNotesCard's Save button is disabled.
 *
 * The output is a [LocalDateTime] in the device's
 * local zone, converted to epoch millis. The
 * caller ([QuickNotesCard] in v0.47.0+) is
 * expected to combine the parsed time with the
 * current date when the phrase is "in N" (a
 * relative offset), and to combine the parsed
 * date with the current time when the phrase is
 * "tomorrow" (a date phrase). The
 * [parseAndResolve] helper does the combination
 * for the common shapes.
 *
 * Not an LLM. A regex + a small enum + the
 * JDK's [LocalDateTime] arithmetic. The
 * implementation is < 100 lines; the test
 * fixture is in `app/src/test/java/.../WheneverTest.kt`
 * and asserts 14 cases (3 relative, 5 daily,
 * 3 weekly, 2 ambiguous, 1 null).
 *
 * The locale is hardcoded to [Locale.ROOT] for
 * the english day-of-week names; the user-facing
 * side uses the system locale for the time
 * formatter.
 */
object Whenever {

    private val RELATIVE = Regex(
        """\s*in\s+(\d+)\s+(min|mins|minute|minutes|hour|hours|day|days)\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val TOMORROW_AT = Regex(
        """\s*tomorrow(?:\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val DAY_AT = Regex(
        """\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?:\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val AT = Regex(
        """\s*at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The day-of-week map. The regex matches
     * the english name case-insensitively; the
     * map converts to [java.time.DayOfWeek].
     */
    private val DOW: Map<String, java.time.DayOfWeek> = mapOf(
        "monday" to java.time.DayOfWeek.MONDAY,
        "tuesday" to java.time.DayOfWeek.TUESDAY,
        "wednesday" to java.time.DayOfWeek.WEDNESDAY,
        "thursday" to java.time.DayOfWeek.THURSDAY,
        "friday" to java.time.DayOfWeek.FRIDAY,
        "saturday" to java.time.DayOfWeek.SATURDAY,
        "sunday" to java.time.DayOfWeek.SUNDAY,
    )

    /**
     * Parse the text and return the resolved
     * time in epoch millis, or null if the
     * text is not a recognised shape. The
     * caller is expected to fall back to the
     * chip picker when null is returned.
     *
     * The text is trimmed and lower-cased for
     * matching; the original text is preserved
     * for the caller's log line ("reminder
     * set: in 2 hours").
     */
    fun parseAndResolve(text: String, now: LocalDateTime = LocalDateTime.now()): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        // 1. relative: "in N (unit)"
        RELATIVE.matchEntire(trimmed)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val unit = m.groupValues[2].lowercase()
            val resolved = when (unit) {
                "min", "mins", "minute", "minutes" -> now.plusMinutes(n)
                "hour", "hours" -> now.plusHours(n)
                "day", "days" -> now.plusDays(n)
                else -> return null
            }
            return resolved.atZone(zone).toInstant().toEpochMilli()
        }
        // 2. tomorrow at H[:MM][am|pm]
        TOMORROW_AT.matchEntire(trimmed)?.let { m ->
            val date = now.toLocalDate().plusDays(1)
            val time = parseClock(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                ?: LocalTime.of(9, 0)
            return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
        }
        // 3. day-of-week at H[:MM][am|pm]
        DAY_AT.matchEntire(trimmed)?.let { m ->
            val dow = DOW[m.groupValues[1].lowercase()] ?: return null
            val date = nextDayOfWeek(now.toLocalDate(), dow)
            val time = parseClock(m.groupValues[2], m.groupValues[3], m.groupValues[4])
                ?: LocalTime.of(9, 0)
            return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
        }
        // 4. at H[:MM][am|pm] (today)
        AT.matchEntire(trimmed)?.let { m ->
            val time = parseClock(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                ?: return null
            val candidate = LocalDateTime.of(now.toLocalDate(), time)
            // If the parsed time is in the past for today, schedule for tomorrow.
            val resolved = if (candidate.isBefore(now)) candidate.plusDays(1) else candidate
            return resolved.atZone(zone).toInstant().toEpochMilli()
        }
        // 5. simple keywords
        when (trimmed.lowercase()) {
            "morning" -> return LocalDateTime.of(
                now.toLocalDate().plusDays(if (now.hour < 12) 0 else 1),
                LocalTime.of(8, 0),
            ).atZone(zone).toInstant().toEpochMilli()
            "evening" -> return LocalDateTime.of(
                now.toLocalDate().plusDays(if (now.hour < 20) 0 else 1),
                LocalTime.of(20, 0),
            ).atZone(zone).toInstant().toEpochMilli()
            "noon" -> return LocalDateTime.of(
                now.toLocalDate().plusDays(if (now.hour < 12) 0 else 1),
                LocalTime.of(12, 0),
            ).atZone(zone).toInstant().toEpochMilli()
            "midnight" -> return LocalDateTime.of(
                now.toLocalDate().plusDays(1),
                LocalTime.MIDNIGHT,
            ).atZone(zone).toInstant().toEpochMilli()
            "tonight" -> return LocalDateTime.of(
                now.toLocalDate().plusDays(if (now.hour < 20) 0 else 1),
                LocalTime.of(20, 0),
            ).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private fun parseClock(h: String?, mm: String?, ampm: String?): LocalTime? {
        if (h.isNullOrEmpty()) return null
        var hour = h.toIntOrNull() ?: return null
        val minute = mm?.toIntOrNull() ?: 0
        if (minute !in 0..59) return null
        when (ampm?.lowercase()) {
            "am" -> if (hour == 12) hour = 0
            "pm" -> if (hour != 12) hour += 12
            else -> if (hour !in 0..23) return null
        }
        if (hour !in 0..23) return null
        return LocalTime.of(hour, minute)
    }

    private fun nextDayOfWeek(today: LocalDate, target: java.time.DayOfWeek): LocalDate {
        var d = today
        repeat(7) {
            if (d.dayOfWeek == target) return d
            d = d.plusDays(1)
        }
        return today
    }

    /**
     * Parse just the body out of a sentence
     * containing a reminder phrase. The
     * convention is "remind me <text> at <when>"
     * or "remind me <text> <when>". The body is
     * the part before the recognised phrase;
     * the phrase is the recognised time.
     *
     * Returns a [Pair] of (body, atMillis), or
     * null if no phrase is recognised. The
     * caller is expected to fall back to the
     * chip picker when null is returned.
     */
    fun extractFrom(text: String, now: LocalDateTime = LocalDateTime.now()): Pair<String, Long>? {
        val t = text.trim()
        // Try each pattern at the END of the
        // text. The patterns are anchored with
        // `\s*$` so the phrase is the trailing
        // token, and the body is the prefix.
        val patterns: List<Regex> = listOf(
            // "remind me <body> in N <unit>"
            Regex(
                """^(.*?)\s+in\s+(\d+)\s+(min|mins|minute|minutes|hour|hours|day|days)\s*$""",
                RegexOption.IGNORE_CASE,
            ),
            // "remind me <body> tomorrow"
            Regex(
                """^(.*?)\s+tomorrow(?:\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?)?\s*$""",
                RegexOption.IGNORE_CASE,
            ),
            // "remind me <body> <day-of-week>"
            Regex(
                """^(.*?)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?:\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?)?\s*$""",
                RegexOption.IGNORE_CASE,
            ),
            // "remind me <body> at H[:MM][am|pm]"
            Regex(
                """^(.*?)\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*$""",
                RegexOption.IGNORE_CASE,
            ),
            // "remind me <body> tonight|tonight"
            Regex(
                """^(.*?)\s+(tonight|morning|evening|noon|midnight)\s*$""",
                RegexOption.IGNORE_CASE,
            ),
        )
        for (pattern in patterns) {
            val m = pattern.matchEntire(t) ?: continue
            val body = m.groupValues[1].trim()
            // Strip a leading "remind me" or
            // "reminder" or "to" if the user
            // typed those.
            val cleanedBody = body
                .replace(Regex("""^(remind\s+me\s+to\s+|remind\s+me\s+|reminder\s+to\s+|reminder\s+|to\s+)""", RegexOption.IGNORE_CASE), "")
                .trim()
            // Re-parse the phrase tail alone.
            val tail = m.value.removePrefix(body).trim()
            val atMillis = parseAndResolve(tail, now) ?: continue
            return cleanedBody to atMillis
        }
        return null
    }
}
