package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind the units the report prints.
 *
 * `Time asleep: 435` shipped for eighteen versions — the app's own
 * headline metric handing the reader a division problem. These cover the
 * split that fixes it, and in particular the order of the two operations:
 * rounding after dividing disagrees with the total the same number would
 * produce anywhere else.
 */
class ValueFormatTest {

    @Test
    fun `a night's sleep splits into hours and minutes`() {
        assertEquals(7 to 15, hoursAndMinutes(435.0))
        assertEquals(8 to 0, hoursAndMinutes(480.0))
        assertEquals(5 to 1, hoursAndMinutes(301.0))
    }

    @Test
    fun `under an hour keeps its minutes`() {
        assertEquals(0 to 40, hoursAndMinutes(40.0))
        assertEquals(0 to 0, hoursAndMinutes(0.0))
    }

    @Test
    fun `rounding happens before the split, not after`() {
        // 59.6 minutes is an hour once rounded. Dividing first would
        // produce 0h and a 59.6-minute remainder that rounds to 60, and
        // the screen would read "0h 60m".
        assertEquals(1 to 0, hoursAndMinutes(59.6))
        assertEquals(0 to 59, hoursAndMinutes(59.4))
        assertEquals(2 to 0, hoursAndMinutes(119.5))
    }

    @Test
    fun `a negative duration cannot happen but must not print as one`() {
        // Nothing upstream produces this. If a corrupt ledger line ever
        // did, "-1h -30m" is a worse answer than zero.
        assertEquals(0 to 0, hoursAndMinutes(-90.0))
    }

    @Test
    fun `an iso day becomes something a person would say`() {
        // Locale-dependent by design, so this asserts what must be true
        // everywhere rather than one locale's exact string: the day and
        // month are named, and the year — always noise for "last night" —
        // is gone.
        val friendly = friendlyDay("2026-08-07")
        assertEquals(false, friendly.contains("2026"))
        assertEquals(false, friendly.contains("-"))
        assertEquals(true, friendly.contains("7"))
    }

    @Test
    fun `an unparseable day falls through rather than throwing`() {
        // A heading is never worth a crash on the one screen somebody
        // opens to read about themselves.
        assertEquals("not a date", friendlyDay("not a date"))
        assertEquals("", friendlyDay(""))
    }
}
