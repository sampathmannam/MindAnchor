/*
 * v0.66.0 (DBT-grounded journal) — Task 8.
 *
 * Two tests for [TherapistExport]:
 *
 *  1. **Content-level test** (the load-bearing one). Asserts the
 *     brief's stated contract: the export carries the disclosure
 *     copy, the 4 crisis line numbers, the mood label, the
 *     "MindAnchor" header, and the own-median value passed in. These
 *     assertions run against [TherapistExport.buildContent], which
 *     returns the exact `List<String>` the renderer consumes. A
 *     future refactor that accidentally drops, say, the Tele-MANAS
 *     line will fail this test.
 *
 *  2. **Smoke test** (file-shape only). Verifies the PDF is written
 *     to disk, is > 1 KB, and starts with `%PDF`. The on-disk bytes
 *     come from the Robolectric `ShadowPdfDocument` (Robolectric
 *     4.13 does not shadow `PdfDocument`), so this test cannot
 *     verify content — that is what test 1 is for.
 *
 * Test pattern (project convention, see [BackupPrefsRoundTripFindingTest]):
 *   - Robolectric 4.13 with `@Config(sdk = [34])` (the project's
 *     pinned SDK config).
 *   - `runBlocking { ... }` from `kotlinx.coroutines.runBlocking` —
 *     NOT `runTest` from `kotlinx-coroutines-test` (that dependency
 *     is not in the project, per `gradle/libs.versions.toml`).
 *   - JUnit 4 (org.junit.*, org.junit.Assert.*).
 */
package org.mindanchor.journal.crisis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPdfDocument::class])
class TherapistExportTest {

    @Test fun `content builder includes disclosure, crisis numbers, and mood line`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val exporter = TherapistExport(ctx)
        val lines = exporter.buildContent(
            from = LocalDate.of(2026, 8, 14),
            to = LocalDate.of(2026, 8, 21),
            diaryEntries = emptyList(),
            skillEntries = emptyList(),
            moodOwnMedian = 3,
            moodMad = 0,
        )

        assertTrue("content list is non-empty", lines.isNotEmpty())

        // Disclosure copy — bridge-to-therapist framing.
        assertTrue(
            "content includes disclosure about therapy substitute",
            lines.any { it.contains("not a substitute for therapy") },
        )

        // 4 crisis line numbers (hard-coded strings, per the brief).
        val crisisNumbers = listOf("9152987821", "18602662362", "9820466726", "14416")
        for (number in crisisNumbers) {
            assertTrue(
                "content includes crisis number $number",
                lines.any { it.contains(number) },
            )
        }

        // Header + section labels.
        assertTrue(
            "content includes MindAnchor header",
            lines.any { it.contains("MindAnchor") },
        )
        assertTrue(
            "content includes mood section label",
            lines.any { it == "Mood (N-of-1, 14-day)" },
        )

        // Mood value passed in is reflected.
        assertTrue(
            "content includes own median value 3",
            lines.any { it.contains("Own median: 3") },
        )
    }

    @Test fun `export produces a non-empty PDF file (smoke test)`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val out = TherapistExport(ctx).export(
            from = LocalDate.of(2026, 8, 14),
            to = LocalDate.of(2026, 8, 21),
            diaryEntries = emptyList(),
            skillEntries = emptyList(),
            moodOwnMedian = 3,
            moodMad = 0,
        )
        assertTrue("PDF should exist", out.exists())
        assertTrue("PDF should be > 1KB", out.length() > 1024)
        val bytes = out.readBytes()
        // PDF magic bytes
        assertTrue(
            "PDF starts with %PDF",
            bytes.copyOfRange(0, 4).contentEquals("%PDF".toByteArray()),
        )
    }
}
