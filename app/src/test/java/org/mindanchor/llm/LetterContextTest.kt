package org.mindanchor.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [LetterContext.build] is a pure function: given the
 * same (today, notes, checkIns, now) it returns the same
 * LlmRequest. The test pins the prompt's structure, the
 * empty-section rendering, and the 3-day window.
 */
class LetterContextTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 22)
    private val zone: ZoneId = ZoneOffset.UTC
    private val noonUtc: Instant = today.atStartOfDay(zone).toInstant().plusSeconds(12 * 3600)

    @Test
    fun `build returns a request with system and user messages`() {
        val req = LetterContext.build(
            today = today,
            notes = emptyList(),
            checkIns = emptyList(),
            now = noonUtc,
            zone = zone,
        )
        assertEquals(2, req.messages.size)
        assertTrue(req.messages[0] is LlmMessage.System)
        assertTrue(req.messages[1] is LlmMessage.User)
        assertEquals(LetterPrompt.SYSTEM_PROMPT, (req.messages[0] as LlmMessage.System).content)
    }

    @Test
    fun `build renders empty sections as the spec-mandated placeholders`() {
        val req = LetterContext.build(
            today = today,
            notes = emptyList(),
            checkIns = emptyList(),
            now = noonUtc,
            zone = zone,
        )
        val userPrompt = (req.messages[1] as LlmMessage.User).content
        assertTrue(userPrompt.contains("[QuickNote]  — (nothing written)"))
        assertTrue(userPrompt.contains("[Today journal] — (nothing written)"))
        assertTrue(userPrompt.contains("— (nothing written)")) // recent notes
        assertTrue(userPrompt.contains("[Most recent check-in] — (no check-in yet)"))
    }

    @Test
    fun `build includes today's QuickNote when one exists`() {
        val note = Note(
            id = 1L,
            body = "Coffee with M. The light was good.",
            createdAt = today.atStartOfDay(zone).toInstant().toEpochMilli() + 9 * 3600_000L,
        )
        val req = LetterContext.build(
            today = today,
            notes = listOf(note),
            checkIns = emptyList(),
            now = noonUtc,
            zone = zone,
        )
        val userPrompt = (req.messages[1] as LlmMessage.User).content
        assertTrue(userPrompt.contains("Coffee with M."))
    }

    @Test
    fun `build uses the most recent check-in`() {
        val older = CheckIn(rating = 3, reflection = "yesterday", atMillis = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 18 * 3600_000L)
        val newer = CheckIn(rating = 4, reflection = "today felt lighter", atMillis = today.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3600_000L)
        val req = LetterContext.build(
            today = today,
            notes = emptyList(),
            checkIns = listOf(older, newer),
            now = noonUtc,
            zone = zone,
        )
        val userPrompt = (req.messages[1] as LlmMessage.User).content
        assertTrue(userPrompt.contains("mood: 4/5"))
        assertTrue(userPrompt.contains("today felt lighter"))
        assertTrue(!userPrompt.contains("yesterday"))
    }

    @Test
    fun `build limits recent notes to the last 3 days`() {
        val oldNote = Note(id = 1L, body = "from 4 days ago", createdAt = today.minusDays(4).atStartOfDay(zone).toInstant().toEpochMilli())
        val inWindow = Note(id = 2L, body = "from 2 days ago", createdAt = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() + 3 * 3600_000L)
        val req = LetterContext.build(
            today = today,
            notes = listOf(oldNote, inWindow),
            checkIns = emptyList(),
            now = noonUtc,
            zone = zone,
        )
        val userPrompt = (req.messages[1] as LlmMessage.User).content
        assertTrue(userPrompt.contains("from 2 days ago"))
        assertTrue(!userPrompt.contains("from 4 days ago"))
    }

    @Test
    fun `build caps recent notes at 20 entries`() {
        val notes = (1..25).map { i ->
            Note(
                id = i.toLong(),
                body = "note $i",
                createdAt = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() + i * 60_000L,
            )
        }
        val req = LetterContext.build(
            today = today,
            notes = notes,
            checkIns = emptyList(),
            now = noonUtc,
            zone = zone,
        )
        val userPrompt = (req.messages[1] as LlmMessage.User).content
        assertTrue(userPrompt.contains("note 20"))
        assertTrue(!userPrompt.contains("note 21"))
        assertTrue(!userPrompt.contains("note 25"))
    }
}
