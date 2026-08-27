package org.mindanchor.llm

import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Builds the user-prompt for one LLM letter generation.
 *
 * The 3-day window is deliberate: today's note (if any),
 * today's journal entry (if any), the last 3 days of
 * notes, and the most recent check-in. The total prompt
 * size is ~800 tokens typical, ~1500 worst case — well
 * below Groq's 8000-token context window for the 70B
 * model.
 *
 * Pure function: no DataStore, no OkHttp, no side effects.
 * The caller (LetterViewModel) collects the notes +
 * check-ins once and passes them in; the unit test
 * exercises the function with synthetic data.
 */
object LetterContext {

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Build the [LlmRequest] for [today]'s letter. The
     * [notes] list is the user's full notes (the function
     * filters to the last 3 days); [checkIns] is the full
     * check-in list (the function takes the most recent).
     * [now] is the current instant — the test passes a
     * fixed value so the output is deterministic.
     */
    fun build(
        today: LocalDate,
        notes: List<Note>,
        checkIns: List<CheckIn>,
        voice: LetterVoice = LetterVoice.DEFAULT,
        model: String = LlmProvider.GOOGLE_AI_STUDIO.defaultModel,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LlmRequest {
        val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val timeOfDay = timeOfDayFor(now, zone)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val threeDaysAgo = today.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()

        // Today's QuickNote: the most recent note written today.
        val todaysNotes = notes
            .filter { it.createdAt in todayStart..(todayStart + 24 * 60 * 60 * 1000L - 1) }
            .sortedByDescending { it.createdAt }
        val quickNote = todaysNotes.firstOrNull()?.let { note ->
            "${DATE_FMT.format(Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate())} — \"${truncate(note.body, 200)}\""
        } ?: "— (nothing written)"

        // Today's journal: not currently in the data model
        // (the journal feature is v0.66.x, not v0.25.5).
        // For v0.25.7 the journal section is always empty.
        // The LLM still gets the marker so the prompt
        // structure is stable when the journal ships.
        val todayJournal = "— (nothing written)"

        // Recent notes: notes from the last 3 days, oldest first.
        val recent = notes
            .filter { it.createdAt in threeDaysAgo..(todayStart - 1) }
            .sortedBy { it.createdAt }
            .take(20) // hard cap; 20 notes × ~40 tokens = 800 tokens
        val recentSection = if (recent.isEmpty()) {
            "— (nothing written)"
        } else {
            recent.joinToString("\n  ") { note ->
                val noteDate = DATE_FMT.format(Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate())
                "${noteDate} — \"${truncate(note.body, 80)}\""
            }
        }

        // Most recent check-in: the latest by atMillis.
        val latestCheckIn = checkIns.maxByOrNull { it.atMillis }
        val checkInSection = if (latestCheckIn == null) {
            "— (no check-in yet)"
        } else {
            val ciDate = DATE_FMT.format(Instant.ofEpochMilli(latestCheckIn.atMillis).atZone(zone).toLocalDate())
            val reflection = latestCheckIn.reflection.takeIf { it.isNotBlank() }?.let { " — \"${truncate(it, 100)}\"" } ?: ""
            "${ciDate} — mood: ${latestCheckIn.rating}/5$reflection"
        }

        val userPrompt = LetterPrompt.userPrompt(
            today = today,
            dayOfWeek = dayOfWeek,
            timeOfDay = timeOfDay,
            quickNoteSection = quickNote,
            todayJournalSection = todayJournal,
            recentNotesSection = recentSection,
            checkInSection = checkInSection,
        )

        return LlmRequest(
            model = model,
            messages = listOf(
                LlmMessage.System(voice.systemPrompt),
                LlmMessage.User(userPrompt),
            ),
        )
    }

    private fun timeOfDayFor(now: Instant, zone: ZoneId): String {
        val hour = now.atZone(zone).hour
        return when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "day"
            in 17..21 -> "evening"
            else -> "night"
        }
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"
}
