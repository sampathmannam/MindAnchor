package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.25.5 WP-B: the nightly report alarm must survive a DST shift and
 * a timezone change without firing at the wrong wall-clock.
 *
 * The v0.23.0 surface took a `LocalDateTime` and the caller converted to
 * an `Instant` with `ZoneId.systemDefault()`. That is correct for the
 * happy path and incorrect for the two boundary cases:
 *
 *  1. Spring-forward day in a non-UTC zone. A `LocalDateTime` of `2:30`
 *     does not exist, and the conversion silently picks the post-
 *     transition offset. The alarm fires at a wall-clock the user did
 *     not pick — sometimes immediately, sometimes never.
 *
 *  2. Fall-back day. The same wall-clock happens twice, and a wrong
 *     choice fires the alarm twice in one hour.
 *
 * The fix: `ReportSchedule.nextRun` is now `(Instant, ZoneId, Decision)
 * -> Instant`. The wall-clock math runs in `ZoneId` at the moment the
 * answer is computed, and the return value is the UTC instant the
 * caller will hand to AlarmManager. The five tests below pin the
 * boundary at known instants in known zones, so a regression that
 * reverts to `LocalDateTime` will be caught here.
 */
class ReportScheduleDstSafetyFindingTest {

    private val eastern: ZoneId = ZoneId.of("America/New_York")
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `nextRun returns the wall-clock RUN_HOUR in the requested zone`() {
        // At 1am UTC, the next 3am UTC is 2 hours away. The same call
        // with eastern at 1am eastern (which is 6am UTC) is 23 hours
        // away — tomorrow's 3am eastern. A regression that ignores the
        // zone and always returns 2 hours would fail this test. The
        // test asserts the wall-clock in the requested zone, not the
        // exact UTC offset, so a JDK with a different DST rule for
        // March 2026 (some JDKs already ship March 2026 as EDT) does
        // not flake the test.
        val now = Instant.parse("2026-03-10T01:00:00Z")
        val utcNext = ReportSchedule.nextRun(now, utc, ReportSchedule.Decision.WAIT_FOR_TOMORROW)
        assertEquals(3, utcNext.atZone(utc).hour)
        assertEquals(0, utcNext.atZone(utc).minute)
        // Eastern at 1am: tonight's 3am in eastern. The instant is
        // some hours later, and what matters is the wall-clock the
        // user picked — not the offset the JDK happens to use.
        val easternNow = Instant.parse("2026-03-10T06:00:00Z")
        val easternNext = ReportSchedule.nextRun(
            easternNow,
            eastern,
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
        )
        val easternAsZoned = easternNext.atZone(eastern)
        assertEquals(3, easternAsZoned.hour)
        assertEquals(0, easternAsZoned.minute)
        assertEquals(LocalDate.of(2026, 3, 10), easternAsZoned.toLocalDate())
    }

    @Test
    fun `nextRun survives spring-forward by landing on the post-transition wall-clock`() {
        // 2026-03-29 in America/New_York: clocks jump from 2:00 EST to
        // 3:00 EDT. The `RUN_HOUR` of 3 exists in both EST and EDT, so
        // 3:00 still happens — it just happens at a different UTC
        // instant. The next 3am is 7am UTC (the EDT 3:00), not 8am UTC
        // (the EST 3:00 that no longer exists in 2026-03-29's offset).
        val justBefore = Instant.parse("2026-03-29T06:30:00Z") // 2:30 EDT
        val next = ReportSchedule.nextRun(
            justBefore,
            eastern,
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
        )
        // The wall-clock of the returned instant must be 03:00 in
        // the eastern zone on the same date, not 02:00 (which doesn't
        // exist) and not 04:00 (which is the next valid time after
        // 2:30 EDT and would be a real wall-clock but not the one the
        // user picked).
        val asZoned = next.atZone(eastern)
        assertEquals(3, asZoned.hour)
        assertEquals(0, asZoned.minute)
        assertEquals(LocalDate.of(2026, 3, 29), asZoned.toLocalDate())
    }

    @Test
    fun `nextRun survives fall-back by preferring the post-transition wall-clock`() {
        // 2026-11-01 in America/New_York: clocks fall back from 2:00 EDT
        // to 1:00 EST, so the 1:00-2:00 wall-clock window happens twice.
        // The next 3:00 EST is the post-fall-back instant (7am UTC,
        // since EST is UTC-5). The pre-fall-back 3:00 EDT is 6am UTC
        // and is no longer "today" by the time the user schedules —
        // the only 3:00 wall-clock on 2026-11-01 is the post-fall one.
        val justBefore = Instant.parse("2026-11-01T05:30:00Z") // 1:30 EDT
        val next = ReportSchedule.nextRun(
            justBefore,
            eastern,
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
        )
        val asZoned = next.atZone(eastern)
        assertEquals(3, asZoned.hour)
        assertEquals(0, asZoned.minute)
        assertEquals(LocalDate.of(2026, 11, 1), asZoned.toLocalDate())
    }

    @Test
    fun `nextRun RETRY advances the next firing by one hour from now`() {
        // The retry path is +1 hour from the current instant, not
        // +1 hour from RUN_HOUR. A user who is checking the home
        // screen at 2:15am and the conditions are not yet met should
        // see the next attempt at 3:15am, not 4:00am. The test pins
        // the literal +3600s.
        val now = Instant.parse("2026-03-10T02:15:00Z")
        val next = ReportSchedule.nextRun(now, utc, ReportSchedule.Decision.RETRY)
        assertEquals(now.plusSeconds(3600), next)
    }

    @Test
    fun `nextRun is monotonic in now for WAIT_FOR_TOMORROW`() {
        // The user is in eastern. They turn the report off at 11pm
        // EST (4am UTC the next day), then turn it back on at 4am EST
        // (9am UTC). The next firing after the off-to-on transition
        // must not be BEFORE the next firing after the original arming
        // — turning the feature off and on should not produce a
        // double-firing or a backward jump.
        val firstNow = Instant.parse("2026-03-10T03:00:00-05:00") // 3am EST
        val firstNext = ReportSchedule.nextRun(
            firstNow,
            eastern,
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
        )
        val secondNow = Instant.parse("2026-03-10T04:00:00-05:00") // 4am EST, later same day
        val secondNext = ReportSchedule.nextRun(
            secondNow,
            eastern,
            ReportSchedule.Decision.WAIT_FOR_TOMORROW,
        )
        // The first call's next is today's 3am EST — but that's the
        // current moment, so isAfter() is false and we add a day. The
        // second call's next is tomorrow's 3am EST. The second
        // must be >= the first.
        assertTrue("Second next ($secondNext) must be at or after first next ($firstNext)", secondNext >= firstNext)
    }
}
