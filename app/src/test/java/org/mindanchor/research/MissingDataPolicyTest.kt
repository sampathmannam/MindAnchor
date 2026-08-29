package org.mindanchor.research

import java.lang.reflect.Modifier
import java.time.LocalDate
import org.junit.Assert.assertNull
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 5 — the missing-data policy is one sentence long and this
 * is the test of it: nothing is imputed, interpolated, carried forward, or
 * filled in, and every absence is listed with a reason.
 *
 * The point is a series that cannot look complete when it is not. A gap
 * that quietly disappears into an interpolated value is the difference
 * between a personal record and a misleading one.
 */
class MissingDataPolicyTest {

    private fun date(day: Int) = LocalDate.of(2026, 8, day)

    private fun measure(day: Int, reason: MissingDataReason) =
        MissingDataRecord(date(day).toString(), MissingDataPolicy.VARIABLE_MORNING_MEASURE, reason)

    private fun context(day: Int) =
        MissingDataRecord(
            date(day).toString(),
            MissingDataPolicy.VARIABLE_JOURNAL_CONTEXT,
            MissingDataReason.CONTEXT_NOT_DERIVED,
        )

    @Test
    fun `the policy states its version and its rule`() {
        assertEquals("missing-data-v2", MissingDataPolicy.VERSION)
        assertTrue(MissingDataPolicy.STATEMENT.contains("Nothing is imputed"))
        // The statement has to describe the window it actually reports.
        // It once promised every absence outright, while the report
        // silently covered a window chosen from the records.
        assertTrue(
            "the statement must say the report is windowed",
            MissingDataPolicy.STATEMENT.contains("in the reported window"),
        )
        // And must not promise a window the policy does not deliver: an
        // earlier wording said the window "spans the recorded dates",
        // which was false of any record older than the reach backwards.
        assertTrue(
            "the statement must say records outside the window are excluded",
            MissingDataPolicy.STATEMENT.contains("excluded from\n" +
                "            the window") ||
                MissingDataPolicy.STATEMENT.contains("excluded from the window"),
        )
    }

    @Test
    fun `a record carries a reason and never a value`() {
        assertEquals(
            listOf("localDate", "variable", "reason"),
            MissingDataRecord::class.java.declaredFields
                .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
                .map { it.name },
        )
    }

    @Test
    fun `nothing recorded means nothing reported`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = null,
                throughDate = date(10),
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `every day without a measure is listed once`() {
        assertEquals(
            listOf(
                measure(1, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(3, MissingDataReason.NOT_RECORDED),
            ),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(3),
                allMeasureDates = setOf(date(2)),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `days before the first measure are not counted as skipped`() {
        assertEquals(
            listOf(
                measure(1, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(2, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(3, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(5, MissingDataReason.NOT_RECORDED),
            ),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(5),
                allMeasureDates = setOf(date(4)),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `an entry with no context says only what is known`() {
        assertEquals(
            listOf(context(1)),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(1),
                allMeasureDates = setOf(date(1)),
                entryDatesWithoutContext = setOf(date(1)),
            ),
        )
    }

    @Test
    fun `the report is sorted by date and variable`() {
        assertEquals(
            listOf(
                measure(1, MissingDataReason.BEFORE_FIRST_RECORD),
                context(2),
                context(3),
                measure(3, MissingDataReason.NOT_RECORDED),
                measure(4, MissingDataReason.NOT_RECORDED),
            ),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(4),
                allMeasureDates = setOf(date(2), date(5)),
                entryDatesWithoutContext = setOf(date(3), date(2)),
            ),
        )
    }

    @Test
    fun `a context gap outside the window is not reported`() {
        assertEquals(
            listOf(context(2)),
            MissingDataPolicy.report(
                firstRecordDate = date(2),
                throughDate = date(2),
                allMeasureDates = setOf(date(2)),
                entryDatesWithoutContext = setOf(date(1), date(2), date(3)),
            ),
        )
    }

    @Test
    fun `a complete record produces an empty report`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(2),
                allMeasureDates = setOf(date(1), date(2)),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `a through date before the first record reports nothing`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = date(5),
                throughDate = date(1),
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `an implausible window fails loudly instead of hanging`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = LocalDate.of(9999, 12, 31),
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            )
        }
        assertTrue(thrown.message.orEmpty().contains("check the clock"))
    }

    @Test
    fun `the boundary of the permitted window still reports`() {
        val through = date(1).plusDays(MissingDataPolicy.MAX_REPORT_DAYS)
        val report = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = through,
            allMeasureDates = emptySet(),
            entryDatesWithoutContext = emptySet(),
        )
        assertEquals((MissingDataPolicy.MAX_REPORT_DAYS + 1).toInt(), report.size)
    }

    @Test
    fun `Program 1 can only ever produce three of the five reasons`() {
        val ranges = listOf(date(1) to date(1), date(1) to date(4), date(3) to date(1))
        val measureSets = listOf(emptySet(), setOf(date(1)), setOf(date(3)), setOf(date(1), date(2), date(3), date(4)))
        val contextSets = listOf(emptySet(), setOf(date(1)), setOf(date(2), date(4)), setOf(date(9)))

        val produced = ranges.flatMap { (from, through) ->
            measureSets.flatMap { measures ->
                contextSets.flatMap { contexts ->
                    MissingDataPolicy.report(from, through, measures, contexts).map { it.reason } +
                        MissingDataPolicy.report(null, through, measures, contexts).map { it.reason }
                }
            }
        }.toSet()

        assertEquals(
            setOf(
                MissingDataReason.NOT_RECORDED,
                MissingDataReason.BEFORE_FIRST_RECORD,
                MissingDataReason.CONTEXT_NOT_DERIVED,
            ),
            produced,
        )
        assertTrue(MissingDataReason.SENSOR_GAP !in produced)
        assertTrue(MissingDataReason.DEVICE_CHANGE_GAP !in produced)
    }

    // --- window selection -------------------------------------------------
    //
    // These exist because a probe found that one corrupt row could define
    // the whole window. The rule under test is that an implausible date is
    // *excluded* from choosing the window, never allowed to set it.

    @Test
    fun `a single far-future row does not push the window past every real date`() {
        // A row stamped 3026-01-01 -- a mis-set clock, or a corrupt import.
        // Letting it set `throughDate` moved the window start to 2925 and
        // produced 36,601 absences, not one of which was about a date the
        // person had lived, in a document still claiming every absence was
        // listed.
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                recordDates = listOf("2026-08-01", "2026-08-27", "2026-08-29", "3026-01-01").map(LocalDate::parse),
                exportDate = LocalDate.parse("2026-08-29"),
            ),
        )
        assertEquals(LocalDate.parse("2026-08-01"), window.start)
        assertEquals(LocalDate.parse("2026-08-29"), window.through)
    }

    @Test
    fun `a single far-past row does not stretch the window back a century`() {
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                recordDates = listOf("1000-01-01", "2026-08-01", "2026-08-29").map(LocalDate::parse),
                exportDate = LocalDate.parse("2026-08-29"),
            ),
        )
        assertEquals(LocalDate.parse("2026-08-01"), window.start)
        assertEquals(LocalDate.parse("2026-08-29"), window.through)
    }

    @Test
    fun `an outlier at each end still produces a bounded report`() {
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                recordDates = listOf("1000-01-01", "2026-08-29", "3026-01-01").map(LocalDate::parse),
                exportDate = LocalDate.parse("2026-08-29"),
            ),
        )
        val report = MissingDataPolicy.report(
            firstRecordDate = window.start,
            throughDate = window.through,
            allMeasureDates = emptySet(),
            entryDatesWithoutContext = emptySet(),
        )
        assertEquals(1, report.size)
    }

    @Test
    fun `a clock behind the newest record ends the window at the clock`() {
        // The deliberate trade-off, stated as a test so it cannot be
        // changed by accident. An earlier version ran the window on to the
        // newest record here, to cover a device whose clock had fallen
        // behind. That produced absences on dates after the export date --
        // days that had not happened. Under-reporting the tail is the
        // smaller wrong, and the policy statement says the window ends on
        // the export date, so the document does not overclaim.
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                recordDates = listOf("2026-08-01", "2026-08-29").map(LocalDate::parse),
                exportDate = LocalDate.parse("2026-08-20"),
            ),
        )
        assertEquals(LocalDate.parse("2026-08-01"), window.start)
        assertEquals(LocalDate.parse("2026-08-20"), window.through)
    }

    @Test
    fun `the window never runs backwards and never exceeds the policy maximum`() {
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                // Far apart, but each within a lifetime of the export date,
                // so both survive the plausibility filter and the span has
                // to be bounded by the clamp rather than by the filter.
                recordDates = listOf("1930-01-01", "2026-08-29").map(LocalDate::parse),
                exportDate = LocalDate.parse("2026-08-29"),
            ),
        )
        assertTrue("the window must not run backwards", !window.through.isBefore(window.start))
        assertTrue(
            "the window must stay inside MAX_REPORT_DAYS so `report` cannot throw",
            ChronoUnit.DAYS.between(window.start, window.through) <= MissingDataPolicy.MAX_REPORT_DAYS,
        )
    }

    @Test
    fun `no records at all means no window rather than an invented one`() {
        assertNull(MissingDataPolicy.windowFor(emptyList(), LocalDate.parse("2026-08-29")))
    }

    @Test
    fun `records that are all implausible leave no window`() {
        // Reporting a century of absences around a single corrupt row
        // asserts a history that did not happen.
        assertNull(
            MissingDataPolicy.windowFor(
                listOf(LocalDate.parse("3026-01-01")),
                LocalDate.parse("2026-08-29"),
            ),
        )
    }

    @Test
    fun `a row one digit into the future does not choose the window`() {
        // 2126 from 2026 -- a single mistyped or corrupted digit, and far
        // likelier than the year 3026 the first fix was measured against.
        // A symmetric hundred-year bound called this plausible, let it set
        // `through`, and pushed every real date out of the report.
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                recordDates = listOf("2024-08-29", "2026-08-01", "2026-08-29", "2126-08-29").map(LocalDate::parse),
                exportDate = LocalDate.parse("2026-08-29"),
            ),
        )
        assertEquals(LocalDate.parse("2024-08-29"), window.start)
        assertEquals(LocalDate.parse("2026-08-29"), window.through)
    }

    @Test
    fun `the window never runs past the export date`() {
        // This replaces a test that pinned the opposite. Ending the window
        // at the newest record covered a device whose clock had fallen
        // behind -- and produced absences on five days that had not
        // happened when the file was written. A day that has not occurred
        // cannot be missing.
        val exportDate = LocalDate.parse("2026-08-29")
        val window = requireNotNull(
            MissingDataPolicy.windowFor(
                recordDates = listOf("2026-08-26", "2026-08-28", "2026-09-03").map(LocalDate::parse),
                exportDate = exportDate,
            ),
        )
        assertEquals(exportDate, window.through)
        assertEquals(LocalDate.parse("2026-08-26"), window.start)
    }

    @Test
    fun `no absence is ever reported for a day that has not happened`() {
        // The property, not the example: whatever the records say, the
        // report must not name a date after the export date.
        val exportDate = LocalDate.parse("2026-08-29")
        listOf(1L, 5L, 30L, 400L).forEach { ahead ->
            val window = requireNotNull(
                MissingDataPolicy.windowFor(
                    listOf(exportDate.minusDays(3), exportDate.plusDays(ahead)),
                    exportDate,
                ),
            )
            val report = MissingDataPolicy.report(
                firstRecordDate = window.start,
                throughDate = window.through,
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            )
            assertTrue(
                "a record $ahead days ahead produced absences after the export date",
                report.none { it.localDate > exportDate.toString() },
            )
        }
    }

    @Test
    fun `plausibility is bounded on both sides, and asymmetrically`() {
        // Nothing legitimately records the future, so the forward bound is
        // zero days. Backwards, a real record can be years long. A single
        // symmetric bound was a defect twice over.
        val exportDate = LocalDate.parse("2026-08-29")
        assertTrue(MissingDataPolicy.isPlausible(exportDate, exportDate))
        assertTrue(!MissingDataPolicy.isPlausible(exportDate.plusDays(1), exportDate))
        assertTrue(MissingDataPolicy.isPlausible(exportDate.minusDays(400), exportDate))
        assertTrue(
            !MissingDataPolicy.isPlausible(
                exportDate.minusDays(MissingDataPolicy.MAX_REPORT_DAYS + 1),
                exportDate,
            ),
        )
    }

    @Test
    fun `no window can ask report for more than it allows`() {
        // `report` throws above MAX_REPORT_DAYS, and a throwing export
        // leaves a zero-byte file. Sweep both boundaries rather than trust
        // the arithmetic.
        val exportDate = LocalDate.parse("2026-08-29")
        val offsets = listOf(
            -MissingDataPolicy.MAX_REPORT_DAYS - 1, -MissingDataPolicy.MAX_REPORT_DAYS,
            -MissingDataPolicy.MAX_REPORT_DAYS + 1, -365, -1, 0, 1, 30, 400_000,
        )
        offsets.forEach { a ->
            offsets.forEach { b ->
                val window = MissingDataPolicy.windowFor(
                    listOf(exportDate.plusDays(a), exportDate.plusDays(b)),
                    exportDate,
                ) ?: return@forEach
                assertTrue("window ran backwards for $a/$b", !window.through.isBefore(window.start))
                assertTrue(
                    "window of ${ChronoUnit.DAYS.between(window.start, window.through)} days for $a/$b",
                    ChronoUnit.DAYS.between(window.start, window.through) <= MissingDataPolicy.MAX_REPORT_DAYS,
                )
            }
        }
    }
}
