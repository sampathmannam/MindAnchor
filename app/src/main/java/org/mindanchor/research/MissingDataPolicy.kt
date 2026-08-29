package org.mindanchor.research

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * Why a value is absent.
 *
 * There is deliberately **no** case distinguishing "structural context
 * extraction was switched off" from "it ran and produced nothing". The
 * kill switch is a live user-toggleable flag and nothing records when it
 * was toggled, so labelling a six-week-old absence with today's flag state
 * would assert a cause nobody knows — a fabrication of exactly the kind
 * this policy exists to prevent, and a carry-forward of a reason rather
 * than a value. [CONTEXT_NOT_DERIVED] says only what is actually known.
 *
 * [SENSOR_GAP] and [DEVICE_CHANGE_GAP] are capability without a detector,
 * the same discipline `LedgerEventKind.SENSOR_GAP` follows: Program 1 owns
 * no sensors, so no code path here produces them. They exist so Program 2
 * can report a real gap without changing the export shape or the frozen
 * data dictionary, and a test exhausts this function's reachable inputs to
 * prove Program 1 never emits one.
 */
@Serializable
enum class MissingDataReason {
    /** The person did not record it that day. */
    NOT_RECORDED,

    /** The date precedes the first record of that variable — not a skipped day. */
    BEFORE_FIRST_RECORD,

    /** A Journal entry on this date has no structural context rows. Why is not recorded. */
    CONTEXT_NOT_DERIVED,

    /** Program 2: a signal source was unavailable. */
    SENSOR_GAP,

    /** Program 2: history moved to another device and a window has no coverage. */
    DEVICE_CHANGE_GAP,
}

/** One absent value, named and explained. Deliberately has no value field. */
@Serializable
data class MissingDataRecord(val localDate: String, val variable: String, val reason: MissingDataReason)

/**
 * The explicit missing-data policy, stated once and enforced by one pure
 * function.
 *
 * **Nothing is imputed, interpolated, carried forward, or filled in. Every
 * absence is listed with a reason.**
 *
 * A series that quietly looks complete is worse than one with visible
 * holes, because only the second lets a reader judge how much of a pattern
 * is real. So [report] enumerates absences rather than repairing them, and
 * [MissingDataRecord] has nowhere to put a value even if a later author
 * wanted to.
 *
 * The distinction between [MissingDataReason.BEFORE_FIRST_RECORD] and
 * [MissingDataReason.NOT_RECORDED] is the one that matters most: a person
 * who journaled for two weeks before ever completing a morning measure did
 * not skip fourteen measures.
 */
object MissingDataPolicy {

    /**
     * v2: the report's window is now chosen by [windowFor], which excludes
     * implausibly dated records instead of letting one define the span.
     * A new version rather than an edit, because the provenance vector
     * carries this string: devices that recorded under the old rule open a
     * new study phase rather than having their history reinterpreted.
     */
    const val VERSION = "missing-data-v2"

    const val STATEMENT =
        "Nothing is imputed, interpolated, carried forward, or filled in. " +
            "Every absence in the reported window is listed with a reason. " +
            "The window ends at the export date and reaches back at most ten " +
            "years. Records outside it are excluded from the window and from " +
            "the reasons given inside it, and still appear in the data itself."

    /** The morning research measure, one per local date. */
    const val VARIABLE_MORNING_MEASURE = "morning_measure"

    /** Structural context rows derived from a Journal entry. */
    const val VARIABLE_JOURNAL_CONTEXT = "journal_context"

    /**
     * Ten years. How far back a report may reach, and so also how old a
     * record may be and still choose the window.
     *
     * Was a century, which bounded nothing that mattered: a single row
     * dated a century back still produced a 36,600-row, ~3.3 MB report of
     * absences on dates nobody was alive for. Ten years is longer than any
     * record this app can hold — it did not exist ten years ago — while
     * keeping the worst case a few thousand rows.
     */
    const val MAX_REPORT_DAYS = 3_653L

    /**
     * How far *ahead* of the export date a record may be dated and still
     * be treated as an observation.
     *
     * Asymmetric with [MAX_REPORT_DAYS], and that asymmetry is the point.
     * A symmetric bound was the whole defect: a row dated 2126 instead of
     * 2026 — one digit — sat comfortably inside a century-wide window,
     * dragged the window forward with it, and pushed every real date out
     * of the report. Nothing legitimately records the future; the only
     * reason to tolerate any of it is a device clock that is behind, or a
     * timezone, and neither runs to years.
     */
    const val MAX_FUTURE_DAYS = 30L

    /**
     * Whether [date] could be an observation made by someone using this
     * app, as opposed to a wrong clock or a corrupt row.
     *
     * Public because the window is not the only thing an implausible date
     * can wreck: it also decides *reasons*. One row stamped in the year
     * 1000 made `allMeasureDates.min()` the year 1000, which turned every
     * "you had not started this measure yet" into "you skipped it" — the
     * distinction this policy exists to keep.
     */
    fun isPlausible(date: LocalDate, exportDate: LocalDate): Boolean =
        !date.isAfter(exportDate.plusDays(MAX_FUTURE_DAYS)) &&
            !date.isBefore(exportDate.minusDays(MAX_REPORT_DAYS))

    /** The span a missing-data report covers: [start] to [through], inclusive. */
    data class ReportWindow(val start: LocalDate, val through: LocalDate)

    /**
     * Chooses the window a report should cover, from the dates actually
     * recorded and the date the export was taken.
     *
     * The rule is that an implausible date is *excluded* from choosing the
     * window, never allowed to define it. That distinction is the whole
     * point of this function. Taking the window from the outermost records
     * and clamping afterwards looks equivalent and is not: a single row
     * stamped a thousand years in the future moved the window with it, so
     * the report listed thirty-six thousand absences in the thirtieth
     * century and not one about a date the person had lived - in a
     * document whose own policy statement says every absence is listed.
     * A confidently wrong report is worse than the crash the clamp was
     * added to prevent.
     *
     * "Implausible" is measured against the export date, in either
     * direction, at [MAX_REPORT_DAYS]. It is deliberately generous: a
     * record a few days ahead of a slow device clock is ordinary and must
     * still be reported through, and only something like a corrupt row or
     * a clock set to the wrong millennium falls outside it.
     *
     * Returns null when no record is plausible, because reporting a
     * century of absences around one corrupt row asserts a history that
     * did not happen.
     */
    fun windowFor(recordDates: List<LocalDate>, exportDate: LocalDate): ReportWindow? {
        val plausible = recordDates.filter { isPlausible(it, exportDate) }
        val through = maxOf(exportDate, plausible.maxOrNull() ?: return null)
        // Clamped against `through`, not against the export date: a record
        // just ahead of a slow clock pushes `through` past it, and the span
        // is what `report` refuses above MAX_REPORT_DAYS. `start` is still
        // a real record at or after the boundary, never the boundary
        // itself -- that distinction is what keeps one old row from
        // generating thousands of absences nobody recorded.
        val start = plausible.filter { !it.isBefore(through.minusDays(MAX_REPORT_DAYS)) }.minOrNull()
            ?: return null
        return ReportWindow(start = start, through = through)
    }

    /**
     * Enumerates every absence between [firstRecordDate] and [throughDate]
     * inclusive. Dates outside that window are never reported, in either
     * direction.
     *
     * @param firstRecordDate the earliest local date on which anything was
     *   recorded, or null when nothing ever was — in which case the report
     *   is empty, because inventing absences for a person who has not
     *   started is not information.
     * @param allMeasureDates **every** local date that has a morning
     *   measure, not a recent window of them. The name is the contract: a
     *   windowed set would make the earliest window boundary look like the
     *   first measure ever taken, and turn months of genuinely skipped
     *   days into "hadn't started yet".
     * @param entryDatesWithoutContext local dates carrying a Journal entry
     *   with no structural context rows. Keyed by date, so several entries
     *   on one date report as one absence.
     * @throws IllegalArgumentException if the window is longer than
     *   [MAX_REPORT_DAYS].
     */
    fun report(
        firstRecordDate: LocalDate?,
        throughDate: LocalDate,
        allMeasureDates: Set<LocalDate>,
        entryDatesWithoutContext: Set<LocalDate>,
    ): List<MissingDataRecord> {
        if (firstRecordDate == null || throughDate.isBefore(firstRecordDate)) return emptyList()
        val span = ChronoUnit.DAYS.between(firstRecordDate, throughDate)
        require(span <= MAX_REPORT_DAYS) {
            "missing-data window of $span days exceeds $MAX_REPORT_DAYS; check the clock, not the policy"
        }

        val firstMeasureDate = allMeasureDates.minOrNull()
        val measureGaps = generateSequence(firstRecordDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(throughDate) }
            .filterNot { it in allMeasureDates }
            .map { day ->
                val reason = if (firstMeasureDate == null || day.isBefore(firstMeasureDate)) {
                    MissingDataReason.BEFORE_FIRST_RECORD
                } else {
                    MissingDataReason.NOT_RECORDED
                }
                MissingDataRecord(day.toString(), VARIABLE_MORNING_MEASURE, reason)
            }

        val contextGaps = entryDatesWithoutContext
            .filter { !it.isBefore(firstRecordDate) && !it.isAfter(throughDate) }
            .map {
                MissingDataRecord(it.toString(), VARIABLE_JOURNAL_CONTEXT, MissingDataReason.CONTEXT_NOT_DERIVED)
            }

        // No de-duplication pass: measure gaps are unique by construction,
        // entryDatesWithoutContext is a set, and the two groups always
        // differ in `variable`.
        return (measureGaps.toList() + contextGaps).sortedWith(compareBy({ it.localDate }, { it.variable }))
    }
}
