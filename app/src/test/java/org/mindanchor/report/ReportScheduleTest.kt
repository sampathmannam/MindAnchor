package org.mindanchor.report

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two conditions WorkManager used to enforce, now enforced here so
 * that this app can keep declaring no network permission at all. See
 * [ReportSchedule]'s own KDoc for why that trade was made; these are the
 * tests that make the replacement worth having, because a scheduler's
 * constraints could not be tested and this can.
 *
 * v0.25.5: [ReportSchedule.nextRun] now takes the [Instant] and
 * [ZoneId] as parameters and returns the [Instant] the caller hands to
 * AlarmManager. The tests below use UTC so the wall-clock is a direct
 * function of the input — the DST-safety behaviour is pinned separately
 * in [ReportScheduleDstSafetyFindingTest].
 */
class ReportScheduleTest {

    private val today = "2026-08-07"
    private val utc: ZoneId = ZoneOffset.UTC

    private fun decide(
        charging: Boolean = true,
        interactive: Boolean = false,
        hourOfDay: Int = ReportSchedule.RUN_HOUR,
        lastRunDay: String? = null,
    ) = ReportSchedule.decide(charging, interactive, hourOfDay, lastRunDay, today)

    @Test
    fun `charging and screen off is the one state that runs`() {
        assertEquals(ReportSchedule.Decision.RUN, decide())
    }

    @Test
    fun `on battery it waits, however dark the screen`() {
        assertEquals(ReportSchedule.Decision.RETRY, decide(charging = false))
    }

    @Test
    fun `screen on it waits, however plugged in`() {
        // Either constraint alone would let this run while somebody is
        // mid-task on their own phone, which is the whole reason both
        // are required.
        assertEquals(ReportSchedule.Decision.RETRY, decide(interactive = true))
    }

    @Test
    fun `a report already written today is never rebuilt`() {
        // Otherwise every hourly retry through the window would rebuild
        // it, and the retry exists to catch a phone that was not
        // charging at three, not to run the same night five times.
        assertEquals(
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
            decide(lastRunDay = today),
        )
    }

    @Test
    fun `a report written yesterday does not block tonight's`() {
        assertEquals(ReportSchedule.Decision.RUN, decide(lastRunDay = "2026-08-06"))
    }

    @Test
    fun `it keeps trying through the night window and gives up after it`() {
        val lastRetryHour = ReportSchedule.RUN_HOUR + ReportSchedule.RETRY_HOURS - 1
        for (hour in ReportSchedule.RUN_HOUR..lastRetryHour) {
            assertEquals(
                "hour $hour is inside the window",
                ReportSchedule.Decision.RETRY,
                decide(charging = false, hourOfDay = hour),
            )
        }
        assertEquals(
            "past the window somebody is plausibly awake, and last night's news has gone stale",
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
            decide(charging = false, hourOfDay = lastRetryHour + 1),
        )
    }

    @Test
    fun `the retry window never crosses midnight`() {
        // decide() compares a plain hour against a plain range, which
        // would silently stop working if the window wrapped.
        assertTrue(ReportSchedule.RUN_HOUR + ReportSchedule.RETRY_HOURS <= 24)
    }

    @Test
    fun `a retry is an hour out`() {
        val now = LocalDateTime.of(2026, 8, 7, 4, 30).toInstant(ZoneOffset.UTC)
        assertEquals(
            LocalDateTime.of(2026, 8, 7, 5, 30).toInstant(ZoneOffset.UTC),
            ReportSchedule.nextRun(now, utc, ReportSchedule.Decision.RETRY),
        )
    }

    @Test
    fun `after running, the next alarm is tomorrow night rather than nothing`() {
        // An alarm that arms nothing ends the feature silently, which is
        // the worst way for it to end.
        val now = LocalDateTime.of(2026, 8, 7, 3, 0).toInstant(ZoneOffset.UTC)
        assertEquals(
            LocalDateTime.of(2026, 8, 8, ReportSchedule.RUN_HOUR, 0).toInstant(ZoneOffset.UTC),
            ReportSchedule.nextRun(now, utc, ReportSchedule.Decision.RUN),
        )
    }

    @Test
    fun `switched on during the day, the first attempt is tonight`() {
        // ensureScheduled arms WAIT_FOR_TOMORROW from the settings
        // switch, and at 2pm "tomorrow" must mean the coming night, not
        // the one after it.
        val afternoon = LocalDateTime.of(2026, 8, 7, 14, 0).toInstant(ZoneOffset.UTC)
        assertEquals(
            LocalDateTime.of(2026, 8, 8, ReportSchedule.RUN_HOUR, 0).toInstant(ZoneOffset.UTC),
            ReportSchedule.nextRun(afternoon, utc, ReportSchedule.Decision.WAIT_FOR_TOMORROW),
        )
    }

    @Test
    fun `switched on after midnight, the first attempt is hours away not a day`() {
        val smallHours = LocalDateTime.of(2026, 8, 7, 1, 0).toInstant(ZoneOffset.UTC)
        assertEquals(
            LocalDateTime.of(2026, 8, 7, ReportSchedule.RUN_HOUR, 0).toInstant(ZoneOffset.UTC),
            ReportSchedule.nextRun(smallHours, utc, ReportSchedule.Decision.WAIT_FOR_TOMORROW),
        )
    }

    @Test
    fun `every next alarm is in the future`() {
        // The one property that matters for all of them together: an
        // alarm set in the past fires immediately, and an immediate
        // refire is a loop rather than a schedule.
        val moments = listOf(0, 1, 3, 4, 7, 8, 12, 18, 23).map {
            LocalDateTime.of(2026, 8, 7, it, 0).toInstant(ZoneOffset.UTC)
        }
        moments.forEach { now ->
            ReportSchedule.Decision.entries.forEach { decision ->
                assertTrue(
                    "$decision at $now armed an alarm in the past",
                    ReportSchedule.nextRun(now, utc, decision).isAfter(now),
                )
            }
        }
    }
}
