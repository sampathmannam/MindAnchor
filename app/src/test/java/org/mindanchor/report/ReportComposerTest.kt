package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.corpus.Passage

class ReportComposerTest {

    private val corpus = listOf(
        Passage("hrv", "Shaffer & Ginsberg 2017", "RMSSD reflects vagal parasympathetic activity in heart rate variability."),
        Passage("sri", "Windred et al. 2024", "Sleep regularity and bedtime timing predict outcomes beyond sleep duration circadian."),
        Passage("act", "Linardon 2024", "Physical activity steps movement relate to mood in behavioural activation."),
    )

    /** Twenty steady days, so a baseline exists. */
    private fun steady(value: Double, n: Int = 20) = List(n) { value + (it % 3) }

    @Test
    fun `a steady day produces nothing, and that is the common case`() {
        val report = ReportComposer.compose(
            day = "2026-08-07",
            today = mapOf(Signal.HRV to 41.0),
            history = mapOf(Signal.HRV to steady(40.0)),
            corpus = corpus,
        )
        assertTrue("a steady day must render as silence", report.isEmpty)
    }

    @Test
    fun `a signal well outside a person's own usual is reported`() {
        val report = ReportComposer.compose(
            day = "2026-08-07",
            today = mapOf(Signal.HRV to 12.0),
            history = mapOf(Signal.HRV to steady(40.0)),
            corpus = corpus,
        )
        assertEquals(1, report.sections.size)
        val section = report.sections.first()
        assertEquals(Signal.HRV, section.observation.signal)
        assertEquals(Direction.BELOW, section.observation.direction)
    }

    @Test
    fun `direction is reported both ways`() {
        val high = ReportComposer.compose(
            "d", mapOf(Signal.STEPS to 30_000.0), mapOf(Signal.STEPS to steady(6_000.0)), corpus,
        )
        assertEquals(Direction.ABOVE, high.sections.first().observation.direction)
    }

    @Test
    fun `too little history is admitted rather than guessed at`() {
        val report = ReportComposer.compose(
            day = "d",
            today = mapOf(Signal.HRV to 12.0),
            history = mapOf(Signal.HRV to listOf(40.0, 41.0, 39.0)),
            corpus = corpus,
        )
        assertTrue(report.isEmpty)
        assertEquals(listOf(Signal.HRV), report.notYetKnown)
    }

    @Test
    fun `a signal that was not measured is skipped, not treated as normal`() {
        val report = ReportComposer.compose(
            day = "d",
            today = emptyMap(),
            history = mapOf(Signal.HRV to steady(40.0)),
            corpus = corpus,
        )
        assertTrue(report.isEmpty)
        assertTrue(report.notYetKnown.isEmpty())
    }

    @Test
    fun `a bad measurement day cannot produce a wall of alarms`() {
        // Seven simultaneous abnormalities read as an emergency whatever
        // the wording, and the likeliest cause is one bad day of data.
        val signals = Signal.entries.associateWith { 999_999.0 }
        val history = Signal.entries.associateWith { steady(10.0) }
        val report = ReportComposer.compose("d", signals, history, corpus)
        assertTrue(
            "expected at most ${ReportComposer.MAX_SECTIONS}, got ${report.sections.size}",
            report.sections.size <= ReportComposer.MAX_SECTIONS,
        )
    }

    @Test
    fun `the most unusual signal comes first`() {
        val report = ReportComposer.compose(
            day = "d",
            today = mapOf(Signal.HRV to 20.0, Signal.STEPS to 1.0),
            history = mapOf(Signal.HRV to steady(40.0), Signal.STEPS to steady(6_000.0)),
            corpus = corpus,
        )
        assertEquals(Signal.STEPS, report.sections.first().observation.signal)
    }

    @Test
    fun `identical input produces an identical report`() {
        // A report that reshuffles between runs on the same data looks
        // like it found something new when it did not.
        val today = mapOf(Signal.HRV to 12.0, Signal.STEPS to 30_000.0)
        val history = mapOf(Signal.HRV to steady(40.0), Signal.STEPS to steady(6_000.0))
        val a = ReportComposer.compose("d", today, history, corpus)
        val b = ReportComposer.compose("d", today, history, corpus)
        assertEquals(a, b)
    }

    @Test
    fun `every section can name where its research came from`() {
        val report = ReportComposer.compose(
            "d", mapOf(Signal.SLEEP_ONSET to 900.0), mapOf(Signal.SLEEP_ONSET to steady(240.0)), corpus,
        )
        report.sections.forEach { section ->
            assertTrue(
                "a section with unnamed sources makes an unfalsifiable claim",
                section.sources.isNotEmpty() && section.sources.all { it.isNotBlank() },
            )
        }
    }

    @Test
    fun `a section survives an empty corpus, minus its research`() {
        // The observation is a fact about the person and stands alone.
        // Losing the corpus must not lose the count.
        val report = ReportComposer.compose(
            "d", mapOf(Signal.HRV to 12.0), mapOf(Signal.HRV to steady(40.0)), emptyList(),
        )
        assertEquals(1, report.sections.size)
        assertTrue(report.sections.first().passages.isEmpty())
    }

    @Test
    fun `no corpus query asks the research what a change means`() {
        // The whole design rests on this: research explains what a signal
        // is, the personal baseline says it moved, and the join is left to
        // the person. A query containing an interpretation would smuggle
        // population inference back in.
        val forbidden = listOf(
            "depress", "anxi", "risk", "relapse", "episode", "disorder",
            "warning", "concern", "abnormal", "unhealthy",
        )
        Signal.entries.forEach { signal ->
            val query = signal.corpusQuery.lowercase()
            forbidden.forEach { word ->
                assertTrue("$signal query smuggles in '$word': $query", word !in query)
            }
        }
    }
}
