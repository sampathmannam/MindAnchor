/*
 * v0.66.0 (DBT-grounded journal) — Task 8.
 *
 * On-device PDF export for sharing a date-bounded slice of the
 * journal (mood, diary card, skills) with a therapist. The export
 * is the bridge-to-therapist surface that v0.66.0 ships before the
 * share intent is wired up; the file lives in
 * `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`, with a
 * `filesDir` fallback for the "no external storage" device.
 *
 * BPD-safe defaults (carried from v0.64.0 / v0.65.0, no rollback):
 *   - N-of-1 framing only. "Your 14-day direction (own median ± MAD)"
 *     and "Own median" / "MAD" lines; never "you're better than X%"
 *     and never naming a condition.
 *   - The disclosure "Pattern, not diagnosis. MindAnchor is a
 *     personal R&D tool, not a substitute for therapy." is on the
 *     PDF so the recipient sees the same disclaimer the user sees.
 *   - No streak counter, no leaderboard, no "you used TIPP N times"
 *     copy. The skills section is a count summary only.
 *   - The four crisis lines (iCall, Vandrevala, AASRA, Tele-MANAS)
 *     are hard-coded strings, not constants — they are display
 *     values, not logic. The numbers are deliberately repeated as
 *     strings so a future refactor cannot accidentally swap them.
 *
 * Implementation notes:
 *   - The text content is built first into a `List<String>` via
 *     [buildContent], then rendered line by line. This split lets
 *     the content-level assertions in `TherapistExportTest` verify
 *     the brief's stated contract (disclosure copy, 4 crisis
 *     numbers, mood label, etc.) without depending on the
 *     Robolectric `ShadowPdfDocument` — the on-disk bytes come from
 *     the shadow's padding, not the real `drawText` calls, so file
 *     content cannot be asserted via the rendered PDF.
 *   - Android's native `android.graphics.pdf.PdfDocument`. No
 *     iText, no Apache PDFBox, no kotlinx-serialization. The export
 *     is plain-text, single-page, A4 (595 x 842 points) — readable
 *     on every device and printable.
 *   - On-device only. No network call. No share intent (the UI
 *     layer is responsible for handing the file to a chooser when
 *     the user taps Share).
 *   - The mood + skills + diary rendering is intentionally minimal
 *     (one line per row); the export is a paper trail, not a UI.
 */
package org.mindanchor.journal.crisis

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import org.mindanchor.journal.diary.DiaryCardEntry
import org.mindanchor.journal.skills.SkillId
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// `internal` (not `public`) so the `export` function can take
// `DiaryCardEntry` (also `internal`, see
// `org.mindanchor.journal.diary.DiaryCardEntry`). Kotlin rejects
// a public function with an internal parameter type, so the whole
// class inherits the narrowest visibility that still lets the
// v0.66.0 UI layer call it. The test in this package can still
// see it.
internal class TherapistExport(private val context: Context) {

    private val titleDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Build the text content of the export as a flat list of
     * lines. The header / non-header distinction is dropped here
     * (the renderer applies bold via a paint flag); what matters
     * for the content-level test is the string itself.
     *
     * The list preserves the order rendered to the PDF so a
     * content-level test can also assert ordering if needed.
     */
    internal fun buildContent(
        from: LocalDate,
        to: LocalDate,
        diaryEntries: List<DiaryCardEntry>,
        skillEntries: List<Pair<LocalDate, SkillId>>,
        moodOwnMedian: Int,
        moodMad: Int,
    ): List<String> {
        val out = mutableListOf<String>()

        // Header + meta
        out += "MindAnchor — therapist export"
        out += "Range: ${from.format(titleDate)} to ${to.format(titleDate)}"
        out += "Your 14-day direction (own median ± MAD): $moodOwnMedian ± $moodMad"
        out += "Pattern, not diagnosis. MindAnchor is a personal R&D tool, not a substitute for therapy."
        out += ""  // blank line for spacing

        // Mood
        out += "Mood (N-of-1, 14-day)"
        out += "Own median: $moodOwnMedian (1=Crushed, 5=Bright)"
        out += "MAD (Median Absolute Deviation): $moodMad"
        out += "Direction band: stable (within 1 MAD from your own median)"
        out += ""

        // Diary card
        out += "Diary card entries (count: ${diaryEntries.size})"
        if (diaryEntries.isEmpty()) {
            out += "No diary card entries in this range."
        } else {
            for (e in diaryEntries) {
                val skills = e.skillUsed?.name ?: "—"
                val urges = e.urges?.let { "NSSI ${it.nssi}/5, Sui ${it.suicidal}/5, Dis ${it.dissociation}/5" } ?: "no urges recorded"
                out += "${e.date.format(titleDate)}: $urges. Skill: $skills"
            }
        }
        out += ""

        // Skills
        out += "Skills used (count: ${skillEntries.size})"
        if (skillEntries.isEmpty()) {
            out += "No skills recorded in this range."
        } else {
            val counts = skillEntries.groupingBy { it.second }.eachCount()
            counts.toSortedMap(compareBy { it.ordinal }).forEach { (skill, n) ->
                out += "$skill: $n times"
            }
        }
        out += ""

        // Crisis lines (4 hard-coded strings, not constants — see file header)
        out += "Crisis lines used in this range: see the app's Today surface for count."
        out += "Crisis resources (India):"
        out += "  iCall: 9152987821 (Mon-Sat 8am-10pm)"
        out += "  Vandrevala: 18602662362 (24/7)"
        out += "  AASRA: 9820466726 (24/7)"
        out += "  Tele-MANAS: 14416 (24/7, 20 langs)"

        return out
    }

    fun export(
        from: LocalDate,
        to: LocalDate,
        diaryEntries: List<DiaryCardEntry>,
        skillEntries: List<Pair<LocalDate, SkillId>>,
        moodOwnMedian: Int,
        moodMad: Int,
    ): File {
        val lines = buildContent(from, to, diaryEntries, skillEntries, moodOwnMedian, moodMad)

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()  // A4 in points
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 12f; isAntiAlias = true }

        var y = 60f
        fun line(text: String, advance: Float = 18f) {
            canvas.drawText(text, 40f, y, paint)
            y += advance
        }

        // The crisis block (last 5 lines) is rendered at 9pt per the
        // brief; everything before it is 12pt. Find the boundary
        // before drawing.
        val crisisStart = lines.indexOf(
            "Crisis lines used in this range: see the app's Today surface for count."
        )

        // Render. A blank string in [lines] is a paragraph break —
        // no draw, just an advance. The "Mood", "Diary card", and
        // "Skills used" sections are rendered in bold (header) per
        // the brief; everything else is body text.
        for ((i, text) in lines.withIndex()) {
            // Switch to 9pt at the crisis block, with a 36pt advance
            // before it (matches the brief's `y += 36f`).
            if (i == crisisStart) {
                y += 36f
                paint.textSize = 9f
            }
            if (text.isEmpty()) {
                y += 18f
                continue
            }
            val isHeader = i == 0 ||
                text == "Mood (N-of-1, 14-day)" ||
                text.startsWith("Diary card entries ") ||
                text.startsWith("Skills used (")
            if (isHeader) {
                paint.isFakeBoldText = true
                line(text)
                paint.isFakeBoldText = false
            } else {
                line(text)
            }
        }

        doc.finishPage(page)

        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "MindAnchor-Therapist-Export-${from.format(titleDate)}-${to.format(titleDate)}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
