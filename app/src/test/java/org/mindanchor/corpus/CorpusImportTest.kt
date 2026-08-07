package org.mindanchor.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusImportTest {

    private val seed = listOf(
        Passage("hrv-rmssd", "Shaffer & Ginsberg 2017", "RMSSD reflects vagal tone."),
        Passage("sleep-reg", "Windred et al. 2024", "Sleep regularity predicts outcomes."),
    )

    private fun row(id: String, source: String = "Source", text: String = "Text.") =
        "$id\t$source\t$text"

    @Test
    fun `new passages are added on top of the seed, not instead of it`() {
        val outcome = CorpusImport.merge(seed, listOf(row("new-one"), row("new-two")).joinToString("\n"))
        assertEquals(2, outcome.added)
        assertEquals(0, outcome.replaced)
        assertEquals(4, outcome.corpus.size)
        assertTrue(
            "importing must never cost a seed passage",
            outcome.corpus.map { it.id }.containsAll(seed.map { it.id }),
        )
    }

    @Test
    fun `an imported passage may correct a seed one`() {
        // The escape hatch: being able to fix built-in research you think
        // is wrong is a large part of why importing exists at all.
        val outcome = CorpusImport.merge(seed, row("hrv-rmssd", "Better Source", "Corrected text."))
        assertEquals(0, outcome.added)
        assertEquals(1, outcome.replaced)
        assertEquals(2, outcome.corpus.size)
        assertEquals("Corrected text.", outcome.corpus.first { it.id == "hrv-rmssd" }.text)
    }

    @Test
    fun `re-importing an identical passage is not reported as a change`() {
        val outcome = CorpusImport.merge(seed, row("hrv-rmssd", "Shaffer & Ginsberg 2017", "RMSSD reflects vagal tone."))
        assertEquals(0, outcome.added)
        assertEquals(0, outcome.replaced)
        assertTrue(outcome.isEmpty)
    }

    @Test
    fun `a replacement keeps the seed passage's place in the corpus`() {
        val outcome = CorpusImport.merge(seed, row("hrv-rmssd", "Better", "Corrected."))
        assertEquals(listOf("hrv-rmssd", "sleep-reg"), outcome.corpus.map { it.id })
    }

    @Test
    fun `the same id twice in one file resolves to the last one`() {
        val raw = listOf(row("dup", "First", "Older."), row("dup", "Second", "Newer.")).joinToString("\n")
        val outcome = CorpusImport.merge(seed, raw)
        assertEquals(1, outcome.added)
        assertEquals("Newer.", outcome.corpus.first { it.id == "dup" }.text)
    }

    @Test
    fun `rows that could not be read are counted, not silently dropped`() {
        // "Imported 1 passage" and "imported 1 passage, ignored 3 rows"
        // are different pieces of news, and only the second one tells
        // somebody their file is in the wrong format.
        val raw = listOf(
            "# a comment, which is not a skipped row",
            "",
            "just-an-id",
            "id\tsource-but-no-text",
            "\tblank-id\tText.",
            row("good-one"),
        ).joinToString("\n")
        val outcome = CorpusImport.merge(seed, raw)
        assertEquals(1, outcome.added)
        assertEquals(3, outcome.skippedRows)
    }

    @Test
    fun `a file of nothing usable reports itself as such`() {
        val outcome = CorpusImport.merge(seed, "not a corpus\nat all\n")
        assertTrue(outcome.isEmpty)
        assertEquals(2, outcome.skippedRows)
        assertEquals(seed, outcome.corpus)
    }

    @Test
    fun `an empty file changes nothing`() {
        val outcome = CorpusImport.merge(seed, "")
        assertTrue(outcome.isEmpty)
        assertEquals(0, outcome.skippedRows)
        assertEquals(seed, outcome.corpus)
        assertFalse(outcome.truncated)
    }

    @Test
    fun `more passages than the corpus holds are truncated, and it says so`() {
        val raw = (1..CorpusImport.MAX_PASSAGES + 50).joinToString("\n") { row("p$it") }
        val outcome = CorpusImport.merge(seed, raw)
        assertTrue("silently dropping the tail would read as a full import", outcome.truncated)
        assertEquals(CorpusImport.MAX_PASSAGES, outcome.corpus.size)
    }

    @Test
    fun `a corpus at capacity can still be corrected`() {
        // Capacity stops the corpus growing. It must not stop somebody
        // fixing a passage already in it, which costs no space at all.
        val full = (1..CorpusImport.MAX_PASSAGES).map { Passage("p$it", "Source", "Text.") }
        val outcome = CorpusImport.merge(full, row("p1", "Better", "Corrected."))
        assertEquals(1, outcome.replaced)
        assertFalse(outcome.truncated)
        assertEquals("Corrected.", outcome.corpus.first { it.id == "p1" }.text)
    }

    @Test
    fun `an oversized file is cut at a line boundary, never mid-passage`() {
        // Half a sentence presented as research is worse than one passage
        // fewer, because nothing downstream can tell it is half.
        val one = row("filler", "Source", "x".repeat(200))
        val raw = (1..(CorpusImport.MAX_CHARACTERS / one.length + 20)).joinToString("\n") {
            row("p$it", "Source", "x".repeat(200))
        }
        val outcome = CorpusImport.merge(emptyList(), raw)
        assertTrue(outcome.truncated)
        assertTrue(
            "every surviving passage must be whole",
            outcome.corpus.all { it.text.length == 200 && it.source == "Source" },
        )
    }

    @Test
    fun `what is rendered can be read back unchanged`() {
        val outcome = CorpusImport.merge(seed, listOf(row("new-one"), row("new-two")).joinToString("\n"))
        val reread = CorpusStore.parse(CorpusImport.render(outcome.corpus))
        assertEquals(outcome.corpus, reread)
    }

    @Test
    fun `an app update to an untouched seed passage reaches somebody who has imported`() {
        // CorpusStore stores the difference against the seed rather than
        // the merged whole, precisely so this works. Simulated here
        // because the storage half needs a device and this rule is the
        // reason the storage half is shaped the way it is.
        val added = CorpusImport.merge(seed, row("new-one")).corpus
        val stored = added.filterNot { it in seed.toSet() }
        assertEquals(listOf("new-one"), stored.map { it.id })

        val updatedSeed = listOf(
            Passage("hrv-rmssd", "Shaffer & Ginsberg 2017", "Reworded in a later release."),
            seed[1],
        )
        val reloaded = CorpusImport.merge(updatedSeed, CorpusImport.render(stored)).corpus
        assertEquals(
            "an untouched seed passage must track the app, not the day of the import",
            "Reworded in a later release.",
            reloaded.first { it.id == "hrv-rmssd" }.text,
        )
        assertEquals(3, reloaded.size)
    }

    @Test
    fun `a deliberate correction outlives an app update to the same passage`() {
        val corrected = CorpusImport.merge(seed, row("hrv-rmssd", "Better", "My correction.")).corpus
        val stored = corrected.filterNot { it in seed.toSet() }
        assertEquals(listOf("hrv-rmssd"), stored.map { it.id })

        val updatedSeed = listOf(
            Passage("hrv-rmssd", "Shaffer & Ginsberg 2017", "Reworded in a later release."),
            seed[1],
        )
        val reloaded = CorpusImport.merge(updatedSeed, CorpusImport.render(stored)).corpus
        assertEquals(
            "a correction was chosen; the seed's wording was not",
            "My correction.",
            reloaded.first { it.id == "hrv-rmssd" }.text,
        )
    }

    @Test
    fun `a passage that cannot survive the format is dropped rather than written out broken`() {
        // A newline inside a passage would be torn in two on the next
        // read, and the halves would look like two ordinary passages.
        val corpus = listOf(
            Passage("fine", "Source", "One line."),
            Passage("broken", "Source", "Two\nlines."),
            Passage("bad\tid", "Source", "Text."),
        )
        val rendered = CorpusImport.render(corpus)
        assertEquals(listOf(Passage("fine", "Source", "One line.")), CorpusStore.parse(rendered))
    }
}
