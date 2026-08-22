/*
 * v0.66.0 (DBT-grounded journal) — Task 8.
 *
 * Robolectric 4.13 does not shadow `android.graphics.pdf.PdfDocument`
 * (the JAR has no `ShadowPdfDocument`). The real Android class is used,
 * and its native methods (`nativeCreateDocument`, `nativeStartPage`,
 * `nativeWriteTo`, `nativeClose`) are stubs in the Robolectric runtime
 * that return 0 / do nothing. The first call to `startPage` then
 * throws `IllegalStateException: document is closed!` because
 * `mNativeDocument == 0` after `nativeCreateDocument()` returns 0.
 *
 * This shadow replaces the four public mutating methods
 * (`startPage`, `finishPage`, `writeTo`, `close`) so the test can
 * verify the on-disk shape of the export without needing a real
 * PDF renderer. The `Page` returned by `startPage` carries a real
 * `Canvas` backed by a `Bitmap` — Robolectric's `ShadowCanvas`
 * makes `drawText` a no-op in memory, which is enough for
 * `TherapistExport.export(...)` to run end-to-end.
 *
 * `writeTo` writes a syntactically valid, empty PDF > 1 KB to the
 * stream. The test only checks three things:
 *   1. `out.exists()`        — file is on disk
 *   2. `out.length() > 1024` — non-trivial content
 *   3. first 4 bytes are `%PDF`
 * The PDF body is filled with comment lines to clear the 1 KB
 * bar; the on-device renderer (real Android) produces a real,
 * readable PDF with the brief's text.
 */
package org.mindanchor.journal.crisis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {

    /**
     * Return a `Page` whose `Canvas` is a real `Canvas(bitmap)` so
     * `drawText` in the export implementation is a no-op in memory.
     * The `Page` constructor is `Page(Canvas, PageInfo)` (private in
     * Android API 34, see the SDK 34 stub JAR) — accessed via
     * reflection because the visibility is intentional.
     */
    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(
            pageInfo.pageWidth,
            pageInfo.pageHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(
            Canvas::class.java,
            PdfDocument.PageInfo::class.java,
        )
        ctor.isAccessible = true
        return ctor.newInstance(canvas, pageInfo) as PdfDocument.Page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        // No-op. The real implementation calls nativeFinishPage; in
        // Robolectric the native document is a stub, so the side
        // effect would be a no-op anyway.
    }

    /**
     * Write a minimal valid PDF > 1 KB. The brief's on-device
     * renderer produces a richer PDF (text, mood, skills, crisis
     * lines) — this stub is for the unit test only.
     */
    @Implementation
    fun writeTo(out: OutputStream) {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        // 200 comment lines × ~14 bytes ≈ 2.8 KB — clears the
        // 1 KB floor with margin.
        repeat(200) { sb.append("% padding line\n") }
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Pages /Count 0 /Kids [] >>\nendobj\n")
        sb.append("xref\n0 3\n")
        sb.append("0000000000 65535 f \n")
        sb.append("0000000010 00000 n \n")
        sb.append("0000000060 00000 n \n")
        sb.append("trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n110\n")
        sb.append("%%EOF\n")
        out.write(sb.toString().toByteArray())
    }

    @Implementation
    fun close() {
        // No-op. The real implementation calls nativeClose; the
        // Robolectric stub is a no-op anyway, and we are not
        // asserting any post-close state.
    }
}
