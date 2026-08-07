package org.mindanchor.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusStoreTest {

    @Test
    fun `valid rows parse into passages`() {
        val raw = listOf(
            "hrv-rmssd\tShaffer & Ginsberg 2017\tRMSSD reflects vagal tone.",
            "sleep-dur\tWalker 2017\tSleep duration predicts next-day mood.",
        ).joinToString("\n")
        val passages = CorpusStore.parse(raw)
        assertEquals(2, passages.size)
        assertEquals(
            Passage("hrv-rmssd", "Shaffer & Ginsberg 2017", "RMSSD reflects vagal tone."),
            passages[0],
        )
        assertEquals(
            Passage("sleep-dur", "Walker 2017", "Sleep duration predicts next-day mood."),
            passages[1],
        )
    }

    @Test
    fun `comment lines, indented or not, are ignored`() {
        val raw = listOf(
            "# a top-level comment",
            "  # an indented comment",
            "hrv-rmssd\tSource\tText.",
        ).joinToString("\n")
        assertEquals(1, CorpusStore.parse(raw).size)
    }

    @Test
    fun `blank lines, including whitespace-only ones, are ignored`() {
        val raw = "\n\nhrv-rmssd\tSource\tText.\n\n   \n"
        assertEquals(1, CorpusStore.parse(raw).size)
    }

    @Test
    fun `lines with too few fields are skipped rather than throwing`() {
        val raw = listOf(
            "onlyid",
            "id\tsource-no-text",
            "hrv-rmssd\tSource\tText.",
        ).joinToString("\n")
        assertEquals(1, CorpusStore.parse(raw).size)
    }

    @Test
    fun `a blank id, source or text once trimmed is skipped`() {
        val raw = listOf(
            "\tSource\tText.",
            "id\t\tText.",
            "id\tSource\t",
            "   \tSource\tText.",
            "id\tSource\tText.",
        ).joinToString("\n")
        assertEquals(1, CorpusStore.parse(raw).size)
    }

    @Test
    fun `empty input parses to nothing`() {
        assertTrue(CorpusStore.parse("").isEmpty())
    }

    @Test
    fun `whitespace-only input parses to nothing`() {
        assertTrue(CorpusStore.parse("   \n\t\n").isEmpty())
    }

    @Test
    fun `text may contain further tab characters and is preserved whole`() {
        val raw = "id\tSource\tPart one\tpart two"
        val passages = CorpusStore.parse(raw)
        assertEquals(1, passages.size)
        assertEquals("Part one\tpart two", passages[0].text)
    }

    @Test
    fun `fields are trimmed of surrounding whitespace`() {
        val raw = "  id  \t  Source  \t  Text.  "
        val passages = CorpusStore.parse(raw)
        assertEquals(1, passages.size)
        assertEquals(Passage("id", "Source", "Text."), passages[0])
    }
}
