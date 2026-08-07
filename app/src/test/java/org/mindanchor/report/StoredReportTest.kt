package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.corpus.Passage

/**
 * [StoredReport] is [Report] plus an optional narration — see
 * [ReportLedger]'s own KDoc for the exact line format. [ReportStoreTest]
 * already covers everything about the underlying [Report] encoding; this
 * covers only what changed: the [StoredReport.narration] half of it, and
 * that an old, narration-less report on disk still reads back exactly as
 * it always did.
 */
class StoredReportTest {

    private fun sampleReport(): Report = Report(
        day = "2026-08-06",
        sections = listOf(
            ReportSection(
                observation = Observation(Signal.HRV, Direction.BELOW, today = 32.5, usual = 48.0),
                passages = listOf(
                    Passage("hrv-rmssd", "Shaffer & Ginsberg 2017", "RMSSD reflects vagal tone."),
                ),
            ),
        ),
        notYetKnown = emptyList(),
    )

    @Test
    fun `a report with a narration round trips whole`() {
        val original = StoredReport(
            report = sampleReport(),
            narration = "Your heart-rate variability was lower than usual today. " +
                "It describes how much time between heartbeats varies from one beat to the next.",
        )
        assertEquals(original, ReportLedger.decode(ReportLedger.encode(original)))
    }

    @Test
    fun `a report with no narration round trips with narration staying null`() {
        val original = StoredReport(report = sampleReport(), narration = null)
        val decoded = ReportLedger.decode(ReportLedger.encode(original))
        assertEquals(original, decoded)
        assertNull(decoded?.narration)
    }

    @Test
    fun `an old-format report with no NARRATION line at all still decodes fine`() {
        // Exactly the shape ReportLedger produced before narration existed
        // — no NARRATION line anywhere. Backward compatibility means this
        // still decodes to the same report, with narration simply null.
        val raw = listOf(
            "REPORT\t2026-08-06\t",
            "SECTION\tHRV\tBELOW\t32.5\t48.0",
            "PASSAGE\thrv-rmssd\tShaffer & Ginsberg 2017\tRMSSD reflects vagal tone.",
        ).joinToString("\n")
        val decoded = ReportLedger.decode(raw)
        assertEquals(sampleReport(), decoded?.report)
        assertNull(decoded?.narration)
    }

    @Test
    fun `narration text containing a tab survives intact`() {
        val original = StoredReport(
            report = sampleReport(),
            narration = "This part\thas a tab\tright in the middle of it, and still five sentences besides.",
        )
        val decoded = ReportLedger.decode(ReportLedger.encode(original))
        assertEquals(
            "This part\thas a tab\tright in the middle of it, and still five sentences besides.",
            decoded?.narration,
        )
    }

    @Test
    fun `the NARRATION line sits immediately after the header, before any section`() {
        val original = StoredReport(report = sampleReport(), narration = "A short paragraph about the day.")
        val lines = ReportLedger.encode(original).lines()
        assertTrue(lines[0].startsWith("REPORT\t"))
        assertEquals("NARRATION\tA short paragraph about the day.", lines[1])
    }

    @Test
    fun `a narration carrying a newline is flattened, not silently truncated`() {
        // The format is line-oriented. Written as two lines, the second
        // would come back as an unrecognised record and be dropped, and
        // nothing downstream could tell half a paragraph from a whole
        // one — which is the worst of the failures available here.
        val original = StoredReport(
            report = sampleReport(),
            narration = "First sentence about the day.\n\nSecond sentence about it,\r\nand a third.",
        )
        val encoded = ReportLedger.encode(original)
        // REPORT, NARRATION, SECTION, PASSAGE — one line each, and the
        // paragraph is the whole of its own.
        assertEquals("one paragraph must occupy one line", 4, encoded.lines().size)
        assertEquals(
            "First sentence about the day. Second sentence about it, and a third.",
            ReportLedger.decode(encoded)?.narration,
        )
    }

    @Test
    fun `a blank narration is preserved rather than silently dropped by the ledger`() {
        // ReportLedger only omits the line when narration is null; it does
        // not judge the text. Deciding blank is not worth showing is
        // ReportScreen's job, not the storage format's.
        val original = StoredReport(report = sampleReport(), narration = "")
        val decoded = ReportLedger.decode(ReportLedger.encode(original))
        assertEquals("", decoded?.narration)
    }
}
