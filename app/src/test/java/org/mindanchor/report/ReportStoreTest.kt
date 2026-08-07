package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mindanchor.corpus.Passage

class ReportStoreTest {

    private fun sampleReport(): Report = Report(
        day = "2026-08-06",
        sections = listOf(
            ReportSection(
                observation = Observation(Signal.HRV, Direction.BELOW, today = 32.5, usual = 48.0),
                passages = listOf(
                    Passage("hrv-rmssd", "Shaffer & Ginsberg 2017", "RMSSD reflects vagal tone."),
                    Passage("hrv-two", "Another Source", "Second passage, with\ttab inside."),
                ),
            ),
            ReportSection(
                observation = Observation(Signal.STEPS, Direction.ABOVE, today = 15000.0, usual = 6000.0),
                passages = emptyList(),
            ),
        ),
        notYetKnown = listOf(Signal.VALENCE, Signal.AROUSAL),
    )

    @Test
    fun `a report survives a round trip through storage`() {
        val original = sampleReport()
        assertEquals(original, ReportLedger.decode(ReportLedger.encode(original)))
    }

    @Test
    fun `an empty report — the common outcome — round trips too`() {
        val quiet = Report(day = "2026-08-06", sections = emptyList(), notYetKnown = emptyList())
        assertEquals(quiet, ReportLedger.decode(ReportLedger.encode(quiet)))
    }

    @Test
    fun `empty storage decodes to no report rather than a phantom one`() {
        assertNull(ReportLedger.decode(""))
    }

    @Test
    fun `passage text carrying its own tab characters survives intact`() {
        val decoded = ReportLedger.decode(ReportLedger.encode(sampleReport()))
        assertEquals("Second passage, with\ttab inside.", decoded?.sections?.get(0)?.passages?.get(1)?.text)
    }

    @Test
    fun `garbage that is not a report at all decodes to null rather than throwing`() {
        assertNull(ReportLedger.decode("not a report\nrandom junk"))
    }

    @Test
    fun `a header with no day is rejected`() {
        assertNull(ReportLedger.decode("REPORT\t\t"))
    }

    @Test
    fun `a corrupt section is dropped without losing the sections around it`() {
        val raw = listOf(
            "REPORT\t2026-08-06\t",
            "SECTION\tHRV\tBELOW\t32.5\t48.0",
            "PASSAGE\thrv-rmssd\tShaffer\tRMSSD reflects vagal tone.",
            "SECTION\tNOT_A_SIGNAL\tBELOW\t1.0\t2.0",
            "PASSAGE\tghost\tGhost\tShould not attach to anything.",
            "SECTION\tSTEPS\tABOVE\t15000.0\t6000.0",
        ).joinToString("\n")
        val decoded = ReportLedger.decode(raw)
        assertEquals(2, decoded?.sections?.size)
        assertEquals(Signal.HRV, decoded?.sections?.get(0)?.observation?.signal)
        assertEquals(1, decoded?.sections?.get(0)?.passages?.size)
        assertEquals(Signal.STEPS, decoded?.sections?.get(1)?.observation?.signal)
        assertEquals(0, decoded?.sections?.get(1)?.passages?.size)
    }

    @Test
    fun `an unparseable number in a section drops that section, not the whole report`() {
        val raw = listOf(
            "REPORT\t2026-08-06\t",
            "SECTION\tHRV\tBELOW\tnotanumber\t48.0",
            "SECTION\tSTEPS\tABOVE\t15000.0\t6000.0",
        ).joinToString("\n")
        val decoded = ReportLedger.decode(raw)
        assertEquals(1, decoded?.sections?.size)
        assertEquals(Signal.STEPS, decoded?.sections?.get(0)?.observation?.signal)
    }

    @Test
    fun `a passage line before any section is dropped, not misattached`() {
        val raw = listOf(
            "REPORT\t2026-08-06\t",
            "PASSAGE\torphan\tSource\tText.",
            "SECTION\tSTEPS\tABOVE\t15000.0\t6000.0",
        ).joinToString("\n")
        val decoded = ReportLedger.decode(raw)
        assertEquals(1, decoded?.sections?.size)
        assertEquals(0, decoded?.sections?.get(0)?.passages?.size)
    }

    @Test
    fun `notYetKnown names that no longer exist are dropped, not crashed on`() {
        val decoded = ReportLedger.decode("REPORT\t2026-08-06\tHRV,NOT_A_SIGNAL,STEPS")
        assertEquals(listOf(Signal.HRV, Signal.STEPS), decoded?.notYetKnown)
    }
}
