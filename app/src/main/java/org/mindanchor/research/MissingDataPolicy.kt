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
            "The window ends on the export date — never after it, because a " +
            "day that has not happened cannot be missing — and reaches back " +
            "at most ten years. Records dated outside it are excluded from " +
            "the window and from the reasons given inside it, and still " +
            "appear in the data itself."

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
     * Whether [date] could be an observation this report may describe.
     *
     * Two ways to fail. A date **after** the export date cannot be an
     * absence: the day had not happened when the file was written, and
     * saying somebody failed to record on it asserts something that did
     * not occur. A date more than [MAX_REPORT_DAYS] **before** it is
     * outside what this report reaches back to.
     *
     * Public because the window is not the only thing an implausible date
     * can wreck: it also decides *reasons*. One row stamped in the year
     * 1000 made `allMeasureDates.min()` the year 1000, which turned every
     * "you had not started this measure yet" into "you skipped it" — the
     * distinction this policy exists to keep.
     */
    fun isPlausible(date: LocalDate, exportDate: LocalDate): Boolean =
        !date.isAfter(exportDate) && !date.isBefore(exportDate.minusDays(MAX_REPORT_DAYS))

    /** The span a missing-data report covers: [start] to [through], inclusive. */
    data class ReportWindow(val start: LocalDate, val through: LocalDate)

    /**
     * Chooses the window a report should cover, from the dates actually
     * recorded and the date the export was taken.
     *
     * Two rules, and the history behind each is worth keeping.
     *
     * **The window ends at the export date, always.** An earlier version
     * ended it at the newest record when that record was ahead of the
     * clock, to cover a device whose clock had fallen behind. The cost was
     * a report asserting that somebody had failed to record a morning
     * measure on five days that had not happened yet — in a document whose
     * whole purpose is to never claim what did not occur. Under-reporting
     * a few days at the end is a smaller wrong than inventing them, and
     * the statement says which one this is.
     *
     * **An implausible date is excluded from choosing the window, never
     * allowed to define it.** Taking the window from the outermost records
     * and clamping afterwards looks equivalent and is not: a single
     * corrupt row moved the window with it, and the report covered a
     * century nobody lived through while dropping every real date. Twice —
     * the second time because the bound was symmetric, so a row dated 2126
     * instead of 2026, one digit, was still "plausible".
     *
     * Returns null when no record is plausible, because a report about a
     * window nothing was recorded in asserts a history that did not
     * happen. The records themselves are still exported verbatim; only
     * this derived report ignores them.
     */
    fun windowFor(recordDates: List<LocalDate>, exportDate: LocalDate): ReportWindow? {
        val start = recordDates.filter { isPlausible(it, exportDate) }.minOrNull() ?: return null
        return ReportWindow(start = start, through = exportDate)
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
