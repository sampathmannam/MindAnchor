package org.mindanchor.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalTest {

    private val corpus = listOf(
        Passage(
            id = "hrv-vagal",
            source = "Shaffer & Ginsberg 2017, Front Public Health",
            text = "RMSSD reflects vagally mediated changes in heart rate variability " +
                "and is the primary time-domain measure of parasympathetic activity.",
        ),
        Passage(
            id = "sri-mortality",
            source = "Windred et al. 2024, Sleep",
            text = "Sleep regularity was a stronger predictor of all-cause mortality " +
                "than sleep duration across a large accelerometry cohort.",
        ),
        Passage(
            id = "friction-abandon",
            source = "Grüning et al. 2023, PNAS",
            text = "A six second delay before opening a target application led " +
                "participants to abandon thirty six percent of open attempts.",
        ),
        Passage(
            id = "generalisation",
            source = "Müller et al. 2021, Scientific Reports",
            text = "A mobility model reached an area under the curve of 0.82 in a " +
                "small student sample and 0.57 in a heterogeneous sample of 5262.",
        ),
    )

    @Test
    fun `a query finds the passage about it`() {
        val hits = Retrieval.search("RMSSD parasympathetic", corpus)
        assertEquals("hrv-vagal", hits.first().passage.id)
    }

    @Test
    fun `every hit carries its source, so a claim can be checked`() {
        Retrieval.search("sleep regularity", corpus).forEach {
            assertTrue("a passage with no source is unusable", it.passage.source.isNotBlank())
        }
    }

    @Test
    fun `a query matching nothing returns nothing rather than padding`() {
        // A report that fills itself with irrelevant research to look
        // thorough is worse than one that says less.
        assertTrue(Retrieval.search("quantum chromodynamics", corpus).isEmpty())
        assertTrue(Retrieval.search("", corpus).isEmpty())
        assertTrue(Retrieval.search("the and of", corpus).isEmpty())
    }

    @Test
    fun `an empty corpus is handled rather than divided by`() {
        assertTrue(Retrieval.search("anything", emptyList()).isEmpty())
        assertTrue(Retrieval.search("anything", corpus, limit = 0).isEmpty())
    }

    @Test
    fun `punctuation and case do not stop a match`() {
        val plain = Retrieval.search("hrv", corpus)
        val messy = Retrieval.search("HRV,", corpus)
        assertEquals(plain.map { it.passage.id }, messy.map { it.passage.id })
    }

    @Test
    fun `numbers are searchable because they carry meaning here`() {
        val hits = Retrieval.search("5262", corpus)
        assertEquals("generalisation", hits.first().passage.id)
    }

    @Test
    fun `the limit is respected`() {
        assertTrue(Retrieval.search("sleep heart application sample", corpus, limit = 2).size <= 2)
    }

    @Test
    fun `ranking is stable for equal scores, so reports do not shuffle`() {
        val first = Retrieval.search("sleep", corpus).map { it.passage.id }
        val again = Retrieval.search("sleep", corpus.reversed()).map { it.passage.id }
        assertEquals(first, again)
    }

    @Test
    fun `a term appearing in every passage does not produce a negative score`() {
        // The classic BM25 trap: without the +1 in the idf, a term in
        // every document scores negative and can rank a genuinely
        // matching passage below one that matched nothing.
        val everywhere = corpus.map { it.copy(text = it.text + " sleep") }
        val hits = Retrieval.search("sleep", everywhere)
        assertTrue("all scores must stay positive", hits.all { it.score > 0.0 })
        assertEquals(everywhere.size, hits.size)
    }

    @Test
    fun `tokenise drops noise but keeps the words a person would search`() {
        val tokens = Retrieval.tokenise("The resting HRV was low, at 32 ms.")
        assertTrue("resting" in tokens)
        assertTrue("low" in tokens)
        assertTrue("hrv" in tokens)
        assertTrue("32" in tokens)
        assertTrue("the" !in tokens)
    }
}
