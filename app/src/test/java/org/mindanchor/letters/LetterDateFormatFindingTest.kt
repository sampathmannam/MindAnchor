package org.mindanchor.letters

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LetterDateFormatFindingTest {

    private val today = LocalDate.of(2026, 8, 11)  // a Tuesday

    @Test fun `friendlyLetterDate for today is Today`() {
        assertEquals("Today", friendlyLetterDate(today, today))
    }

    @Test fun `friendlyLetterDate for yesterday is Yesterday`() {
        assertEquals("Yesterday", friendlyLetterDate(today.minusDays(1), today))
    }

    @Test fun `friendlyLetterDate for 3 days ago is weekday name`() {
        // today = Tue 2026-08-11; -3 days = Sat 2026-08-08
        assertEquals("Saturday", friendlyLetterDate(today.minusDays(3), today))
    }

    @Test fun `friendlyLetterDate for 14 days ago is MMM d`() {
        val out = friendlyLetterDate(today.minusDays(14), today)
        val expected = today.minusDays(14).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        assertEquals(expected, out)
    }

    @Test fun `friendlyLetterDate for 14 days ago last year is MMM d yyyy`() {
        val otherYear = LocalDate.of(2025, 7, 28)
        val out = friendlyLetterDate(otherYear, today)
        val expected = otherYear.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
        assertEquals(expected, out)
    }
}
