package org.mindanchor.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * v0.25.5 WP-D: the local-only PPG session telemetry shape.
 *
 * The session log is what makes the *act* of measuring visible to the
 * user — "you sat down with this 4 times yesterday, three of them
 * were 45-50 seconds" is the useful answer to a question the user is
 * allowed to ask. Nothing here leaves the device; the file is the
 * same shape as every other DataStore in this app (newline-separated
 * plain text).
 *
 * The five tests below pin the surface: the data class shape, the
 * ledger round-trip, the store's recording call site in PpgCapture,
 * the data class file existence, and the screen-side history row.
 */
class PpgSessionFindingTest {

    @Test
    fun `PpgSession has start, end, meanHr and a durationSeconds computed from them`() {
        // The four fields are the contract. A regression that dropped
        // durationSeconds (and forced callers to compute it from
        // start/end) would couple the read side to a clock; a
        // regression that made durationSeconds a stored value would
        // mean a corrupt file could disagree with the real duration.
        val start = Instant.parse("2026-03-10T08:14:00Z")
        val end = Instant.parse("2026-03-10T08:15:30Z")
        val session = PpgSession(start = start, end = end, meanHr = 72.0)
        assertEquals(start, session.start)
        assertEquals(end, session.end)
        assertEquals(72.0, session.meanHr!!, 0.001)
        assertEquals(90L, session.durationSeconds)
    }

    @Test
    fun `PpgSessionLog round-trips a session through encode then decode`() {
        // The wire format is the on-disk format. A regression that
        // changed the field order (or used a different separator) would
        // silently orphan every previously-recorded session.
        val start = Instant.parse("2026-03-10T08:14:00Z")
        val end = Instant.parse("2026-03-10T08:15:30Z")
        val original = listOf(
            PpgSession(start, end, 72.0),
            PpgSession(start.plusSeconds(3600), end.plusSeconds(3600), null),
        )
        val encoded = PpgSessionLog.encode(original)
        val decoded = PpgSessionLog.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `PpgSessionLog decode drops corrupt lines without throwing`() {
        // Same fail-closed rule as MeasuredLedger: a corrupted line
        // costs one entry, never the file. A regression that threw
        // would turn a single bad write into a crash on the next
        // read.
        val mixed = """
            2026-03-10T08:14:00Z	2026-03-10T08:15:30Z	72.0	90
            not-a-date	2026-03-10T08:15:30Z	72.0	90
            2026-03-10T08:14:00Z	2026-03-10T08:15:30Z	72.0	90
            2026-03-10T08:14:00Z	2026-03-10T08:15:30Z	not-a-number	90
            2026-03-10T08:14:00Z	2026-03-10T08:15:30Z	72.0	-1
            2026-03-10T08:14:00Z	2026-03-10T08:15:30Z	72.0	90
        """.trimIndent()
        val decoded = PpgSessionLog.decode(mixed)
        // Three of the six lines are valid; the corrupt ones are
        // dropped, never thrown.
        assertEquals(3, decoded.size)
        decoded.forEach { assertTrue(it.durationSeconds >= 0L) }
    }

    @Test
    fun `PpgCapture records the session in a finally block, not just on success`() {
        // A failed session is still a session. A regression that
        // recorded only on success would erase every reading the
        // gate refused, and the user would lose the right answer
        // to "how many times did I try yesterday?". The file-shape
        // pin is the cheapest way to keep the recording in the
        // finally block.
        val source = readSource("PpgCapture.kt")
        assertNotNull("PpgCapture.kt must be readable for the file-shape pin", source)
        assertTrue(
            "PpgCapture.start must have a finally block that records the session",
            source!!.contains("finally {") &&
                source.contains("recordSession(sessionStart, result)"),
        )
    }

    @Test
    fun `PpgScreen renders a PpgHistoryRow sub-composable for each recent session`() {
        // The history line is the user-visible payoff for the whole
        // feature. A regression that lost it would still log the
        // session but the user would never see it. The file-shape
        // pin is the cheapest way to keep the row.
        val source = readSource("PpgScreen.kt")
        assertNotNull("PpgScreen.kt must be readable for the file-shape pin", source)
        assertTrue(
            "PpgHistoryRow is the sub-Composable that renders one row",
            source!!.contains("fun PpgHistoryRow("),
        )
        // The screen also reads from the store — without the
        // collectAsState, the row would never repaint.
        assertTrue(
            "PpgScreen must collect sessionStore.recent() and render the list",
            source.contains("sessionStore.recent(3)") &&
                source.contains("forEach { session ->"),
        )
        // meanHr is allowed to be null on a failed session.
        val session = PpgSession(
            start = Instant.parse("2026-03-10T08:14:00Z"),
            end = Instant.parse("2026-03-10T08:15:30Z"),
            meanHr = null,
        )
        assertNull(session.meanHr)
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/vitals/$filename",
            "../app/src/main/java/org/mindanchor/vitals/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
