package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.llm.LetterContext
import org.mindanchor.llm.LlmMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LetterContextFactsTest {

    private val fact = DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))

    @Test
    fun `steady with facts composes bullet lines without verdict words`() {
        val state = AnchorState.Steady(listOf(fact), weekFlagged = true, computedAtEpochMillis = 0L)
        val section = LetterFactsSection.compose(state)!!
        assertTrue(section.contains("- "))
        assertTrue(section.contains("3 nights"))
        assertFalse(section.contains("good", ignoreCase = true))
        assertFalse(section.contains("bad", ignoreCase = true))
    }

    @Test
    fun `warming up or factless steady composes nothing`() {
        assertNull(LetterFactsSection.compose(AnchorState.WarmingUp(3)))
        assertNull(LetterFactsSection.compose(AnchorState.Steady(emptyList(), false, 0L)))
    }

    @Test
    fun `an empty facts section leaves the prompt byte-identical`() {
        val now = Instant.parse("2026-08-26T09:00:00Z")
        val without = LetterContext.build(LocalDate.of(2026, 8, 26), emptyList(), emptyList(), now, ZoneOffset.UTC)
        val with = LetterContext.build(LocalDate.of(2026, 8, 26), emptyList(), emptyList(), now, ZoneOffset.UTC, factsSection = "")
        assertEquals(userText(without), userText(with))
    }

    @Test
    fun `a facts section lands before the closing instruction`() {
        val now = Instant.parse("2026-08-26T09:00:00Z")
        val section = "- 3 nights this week ran well past your usual bedtime."
        val prompt = userText(
            LetterContext.build(
                LocalDate.of(2026, 8, 26), emptyList(), emptyList(), now, ZoneOffset.UTC,
                factsSection = section,
            ),
        )
        val factsAt = prompt.indexOf(section)
        val instructionAt = prompt.indexOf("Write today's letter")
        assertTrue(factsAt in 1 until instructionAt)
    }

    private fun userText(request: org.mindanchor.llm.LlmRequest): String =
        request.messages.filterIsInstance<LlmMessage.User>().single().content
}
