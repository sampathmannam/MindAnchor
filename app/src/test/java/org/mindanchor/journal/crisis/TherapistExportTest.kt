/*
 * v0.66.0 (DBT-grounded journal) — Task 8.
 *
 * Rendering test for [TherapistExport]: the export must produce a
 * real PDF (magic bytes `%PDF` at offset 0, > 1 KB on disk) so the
 * rest of v0.66.0 can build the share intent on top of a non-empty
 * file.
 *
 * Test pattern (project convention, see [BackupPrefsRoundTripFindingTest]):
 *   - Robolectric 4.13 with `@Config(sdk = [34])` (the project's
 *     pinned SDK config).
 *   - `runBlocking { ... }` from `kotlinx.coroutines.runBlocking` —
 *     NOT `runTest` from `kotlinx-coroutines-test` (that dependency
 *     is not in the project, per `gradle/libs.versions.toml`).
 *   - JUnit 4 (org.junit.*, org.junit.Assert.*).
 *
 * BPD-safe defaults: this test only asserts the on-disk shape of
 * the export. It does not assert any "good" / "bad" / "rank" copy
 * because the export carries none.
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

    @Test fun `export produces a non-empty PDF file`() = runBlocking {
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
