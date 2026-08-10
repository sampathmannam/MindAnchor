package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CheckIn], [CheckInStore], and [CheckInEngine].
 *
 * The engine is pure (no Android types), so these tests run on the JVM
 * directly. Python-mirrored as `python3 -c "..."` during the v0.20.1
 * round 5 ship; the source of truth for the algorithm is in the
 * companion Python verification block in the
 * `work/going-light-vpn` branch (see commit log).
 */
class CheckInTest {

    // === CheckIn validation ===

    @Test fun `rating below MIN_RATING rejected`() {
        try {
            CheckIn(rating = 0, atMillis = 1000L)
            assertFalse("expected exception for rating=0", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test fun `rating above MAX_RATING rejected`() {
        try {
            CheckIn(rating = 6, atMillis = 1000L)
            assertFalse("expected exception for rating=6", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test fun `rating at boundary accepted`() {
        CheckIn(rating = 1, atMillis = 1000L)
        CheckIn(rating = 5, atMillis = 1000L)
    }

    @Test fun `reflection over MAX_REFLECTION rejected`() {
        try {
            CheckIn(
                rating = 3,
                reflection = "x".repeat(CheckIn.MAX_REFLECTION + 1),
                atMillis = 1000L,
            )
            assertFalse("expected exception for over-long reflection", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test fun `empty reflection is a legal check-in, not a missing one`() {
        // Pennebaker 1997 is opt-in: a check-in without a reflection
        // is a real check-in, not an incomplete one. The launcher
        // stores the empty string and the history view shows the
        // row with the rating, never prompting the user to "fill in
        // a reflection later". Without this contract, a
        // field that interprets the empty string as "missing" would
        // be a quiet judgement on the user's choice to skip.
        val c = CheckIn(rating = 3, reflection = "", atMillis = 1000L)
        val decoded = CheckInStore.decode(CheckInStore.encode(listOf(c)))
        assertEquals(1, decoded.size)
        assertEquals("", decoded[0].reflection)
        assertEquals(3, decoded[0].rating)
    }

    @Test fun `the reflection is stored verbatim, not summarised`() {
        // The launcher never derives a mood tag or a category from
        // the reflection text — Pennebaker 1997 is the act of
        // writing, not the launcher reading it back as a feature.
        // If a future refactor starts computing a "mood" or
        // "summary" from the reflection, this test fails: the
        // round-trip preserves every byte, including punctuation,
        // whitespace, and unicode. The history view is the only
        // place the words appear, and they appear as the user
        // wrote them.
        val text = "Today was hard.\nHarder than yesterday.\nI don't know why.\n"
        val c = CheckIn(rating = 2, reflection = text, atMillis = 1000L)
        val decoded = CheckInStore.decode(CheckInStore.encode(listOf(c)))
        assertEquals(text, decoded[0].reflection)
    }

    @Test fun `atMillis zero rejected`() {
        try {
            CheckIn(rating = 3, atMillis = 0L)
            assertFalse("expected exception for atMillis=0", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    // === CheckInStore round-trip ===

    @Test fun `single check-in round-trip`() {
        val c = CheckIn(rating = 3, reflection = "hello", atMillis = 1000L)
        val line = CheckInStore.encode(listOf(c))
        val decoded = CheckInStore.decode(line)
        assertEquals(1, decoded.size)
        assertEquals(c, decoded[0])
    }

    @Test fun `multiple check-ins round-trip`() {
        val cs = listOf(
            CheckIn(rating = 1, reflection = "", atMillis = 1000L),
            CheckIn(rating = 5, reflection = "great", atMillis = 2000L),
            CheckIn(rating = 3, reflection = "ok", atMillis = 3000L),
        )
        val raw = CheckInStore.encode(cs)
        val decoded = CheckInStore.decode(raw)
        assertEquals(cs, decoded)
    }

    @Test fun `reflection with newlines and tabs round-trips`() {
        val c = CheckIn(
            rating = 3,
            reflection = "first line\nsecond\tline\n\nthird",
            atMillis = 1000L,
        )
        val line = CheckInStore.encode(listOf(c))
        val decoded = CheckInStore.decode(line)
        assertEquals(1, decoded.size)
        assertEquals(c.reflection, decoded[0].reflection)
    }

    @Test fun `reflection with unicode round-trips`() {
        val c = CheckIn(
            rating = 3,
            reflection = "हिंदी 🎉 \u200B",
            atMillis = 1000L,
        )
        val decoded = CheckInStore.decode(CheckInStore.encode(listOf(c)))
        assertEquals(1, decoded.size)
        assertEquals(c.reflection, decoded[0].reflection)
    }

    @Test fun `empty reflection round-trips`() {
        val c = CheckIn(rating = 3, reflection = "", atMillis = 1000L)
        val decoded = CheckInStore.decode(CheckInStore.encode(listOf(c)))
        assertEquals(c, decoded[0])
    }

    @Test fun `malformed line skipped silently`() {
        val raw = "garbage line here\n1000\t3\tYm9keQ=="
        val decoded = CheckInStore.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(3, decoded[0].rating)
        assertEquals("body", decoded[0].reflection)
    }

    @Test fun `out-of-range rating in line skipped`() {
        val raw = "1000\t7\tYm9keQ==\n1000\t3\tYm9keQ=="
        val decoded = CheckInStore.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(3, decoded[0].rating)
    }

    @Test fun `non-numeric atMillis in line skipped`() {
        val raw = "abc\t3\tYm9keQ==\n1000\t3\tYm9keQ=="
        val decoded = CheckInStore.decode(raw)
        assertEquals(1, decoded.size)
    }

    @Test fun `decodeLine returns null on empty input`() {
        assertNull(CheckInStore.decodeLine(""))
    }

    @Test fun `decodeLine returns null on too few fields`() {
        assertNull(CheckInStore.decodeLine("1000\t3"))
    }

    @Test fun `decodeLine returns null on invalid base64`() {
        assertNull(CheckInStore.decodeLine("1000\t3\t!@#$"))
    }

    // === CheckInState ===

    @Test fun `add appends check-in`() {
        val state = CheckInState()
        val c = CheckIn(rating = 3, atMillis = 1000L)
        val newState = state.add(c)
        assertEquals(1, newState.checkIns.size)
        assertEquals(c, newState.checkIns[0])
        // Original state unchanged
        assertEquals(0, state.checkIns.size)
    }

    @Test fun `acceptedInDay filters by day range`() {
        val dayStart = 0L
        val dayEnd = 24L * 60 * 60 * 1000
        val c1 = CheckIn(rating = 1, atMillis = 1000L)
        val c2 = CheckIn(rating = 3, atMillis = dayEnd - 1)
        val c3 = CheckIn(rating = 5, atMillis = dayEnd)
        val state = CheckInState(listOf(c1, c2, c3))
        val today = state.acceptedInDay(dayStart, dayEnd)
        assertEquals(2, today.size)
    }

    // === CheckInEngine.shouldFire ===

    @Test fun `shouldFire on fresh state`() {
        val state = CheckInState()
        val rl = CheckInRateLimit()
        val now = 24L * 60 * 60 * 1000  // any non-zero
        assertTrue(CheckInEngine.shouldFire(rl, state, now))
    }

    @Test fun `shouldFire rate-limited within 90 min`() {
        val now = 1000L
        val rl = CheckInRateLimit(
            lastAcceptedMillis = now,
            dayStartMillis = 0L,
        )
        val state = CheckInState()
        assertFalse(CheckInEngine.shouldFire(rl, state, now))
        assertFalse(CheckInEngine.shouldFire(rl, state, now + 30L * 60 * 1000))
        assertFalse(
            CheckInEngine.shouldFire(rl, state, now + 90L * 60 * 1000 - 1000),
        )
    }

    @Test fun `shouldFire allowed after 90 min`() {
        val now = 1000L
        val rl = CheckInRateLimit(
            lastAcceptedMillis = now,
            dayStartMillis = 0L,
        )
        val state = CheckInState()
        assertTrue(
            CheckInEngine.shouldFire(rl, state, now + 90L * 60 * 1000),
        )
    }

    @Test fun `shouldFire blocked at daily cap`() {
        val now = 1000L
        val rl = CheckInRateLimit(
            acceptedToday = CheckInRateLimit.DAILY_CAP,
            dayStartMillis = 0L,
        )
        val state = CheckInState()
        assertFalse(CheckInEngine.shouldFire(rl, state, now))
    }

    @Test fun `shouldFire blocked at daily cap from disk state even after process restart`() {
        // v0.20.1 round 5 follow-up: the cap must
        // survive a process death. The in-memory
        // `acceptedToday` counter resets to 0 on
        // every process restart; the on-disk
        // check-ins do not. Without the
        // max(acceptedToday, onDiskToday) fix,
        // a fresh process on a day with 4 accepted
        // check-ins would re-enable the prompt for
        // a 5th, in direct violation of the
        // "no over-prompting" promise.
        val now = 7L * 60 * 60 * 1000  // 7am
        val dayStart = now  // Use 7am as the day boundary
        val checkIns = (0 until CheckInRateLimit.DAILY_CAP).map { i ->
            CheckIn(rating = 3, atMillis = dayStart + (i + 1) * 60 * 60 * 1000)
        }
        val state = CheckInState(checkIns)
        // Fresh holder (process restart): acceptedToday=0.
        val freshHolder = CheckInRateLimit()
        // Disk has 4 check-ins today; should NOT fire.
        assertFalse(
            "shouldFire must be blocked when disk has 4 check-ins " +
                "even if in-memory count is 0",
            CheckInEngine.shouldFire(freshHolder, state, dayStart + 5 * 60 * 60 * 1000),
        )
    }

    @Test fun `shouldFire allowed below daily cap from disk state`() {
        // v0.20.1 round 5 follow-up: 3 disk
        // check-ins (under the cap of 4) should
        // still allow the prompt to fire even
        // after a process restart.
        val now = 7L * 60 * 60 * 1000
        val dayStart = now
        val checkIns = (0 until 3).map { i ->
            CheckIn(rating = 3, atMillis = dayStart + (i + 1) * 60 * 60 * 1000)
        }
        val state = CheckInState(checkIns)
        val freshHolder = CheckInRateLimit()
        assertTrue(
            "shouldFire must allow prompts when disk has <4 check-ins",
            CheckInEngine.shouldFire(
                freshHolder, state, dayStart + 5 * 60 * 60 * 1000,
            ),
        )
    }

    @Test fun `shouldFire allowed below daily cap`() {
        val now = 1000L
        val rl = CheckInRateLimit(
            acceptedToday = CheckInRateLimit.DAILY_CAP - 1,
            dayStartMillis = 0L,
        )
        val state = CheckInState()
        assertTrue(CheckInEngine.shouldFire(rl, state, now))
    }

    @Test fun `shouldFire blocked when auto-paused same day`() {
        val now = 1000L
        val rl = CheckInRateLimit(
            autoPaused = true,
            dayStartMillis = 0L,
        )
        val state = CheckInState()
        assertFalse(CheckInEngine.shouldFire(rl, state, now))
    }

    @Test fun `shouldFire allowed on new day after auto-pause yesterday`() {
        // The rate-limit was set on a previous
        // calendar day; today is a new calendar day.
        // The day rollover must clear the
        // auto-paused flag so a new day's first
        // check-in is allowed.
        val now = System.currentTimeMillis()
        val yesterday = now - 24L * 60 * 60 * 1000
        val yesterdayDayStart = CheckInEngine.computeDayStart(yesterday)
        val rl = CheckInRateLimit(
            autoPaused = true,
            acceptedToday = 4,
            consecutiveRejections = 3,
            dayStartMillis = yesterdayDayStart,  // yesterday's start, NOT today's
            lastAcceptedMillis = yesterday,
        )
        val state = CheckInState()
        // 24h since last accepted, so > 90 min — should fire
        assertTrue(CheckInEngine.shouldFire(rl, state, now))
    }

    // === recordAcceptance ===

    @Test fun `recordAcceptance bumps daily count and resets consecutive`() {
        val now = 1000L
        val rl = CheckInRateLimit(dayStartMillis = 0L)
        val c = CheckIn(rating = 3, atMillis = now)
        val (newRl, newState) = CheckInEngine.recordAcceptance(rl, CheckInState(), c, now)
        assertEquals(1, newRl.acceptedToday)
        assertEquals(now, newRl.lastAcceptedMillis)
        assertEquals(0, newRl.consecutiveRejections)
        assertEquals(1, newState.checkIns.size)
    }

    // === recordRejection ===

    @Test fun `recordRejection increments counter`() {
        val now = 1000L
        val rl = CheckInRateLimit(dayStartMillis = 0L)
        val r1 = CheckInEngine.recordRejection(rl, now)
        assertEquals(1, r1.consecutiveRejections)
        assertFalse(r1.autoPaused)
        val r2 = CheckInEngine.recordRejection(r1, now)
        assertEquals(2, r2.consecutiveRejections)
        assertFalse(r2.autoPaused)
        val r3 = CheckInEngine.recordRejection(r2, now)
        assertEquals(3, r3.consecutiveRejections)
        assertTrue(r3.autoPaused)
    }

    @Test fun `recordAcceptance after rejections resets counter`() {
        val now = 1000L
        var rl = CheckInRateLimit(dayStartMillis = 0L)
        rl = CheckInEngine.recordRejection(rl, now)
        rl = CheckInEngine.recordRejection(rl, now)
        val c = CheckIn(rating = 3, atMillis = now)
        val (newRl, _) = CheckInEngine.recordAcceptance(rl, CheckInState(), c, now)
        assertEquals(0, newRl.consecutiveRejections)
        assertFalse(newRl.autoPaused)
    }

    // === reset ===

    @Test fun `reset clears all transient state`() {
        val now = 1000L
        val rl = CheckInRateLimit(
            lastAcceptedMillis = now,
            acceptedToday = 2,
            dayStartMillis = 0L,
            consecutiveRejections = 2,
            autoPaused = true,
        )
        val newRl = CheckInEngine.reset(rl, now)
        assertEquals(0L, newRl.lastAcceptedMillis)
        assertEquals(0, newRl.acceptedToday)
        assertEquals(0, newRl.consecutiveRejections)
        assertFalse(newRl.autoPaused)
        // dayStartMillis is now the start of `now`'s day
        assertNotNull(newRl.dayStartMillis)
    }

    // === rollover ===

    @Test fun `rollover resets daily counts on new day`() {
        val day1 = 1000L
        val day2 = day1 + 24L * 60 * 60 * 1000 + 1000L  // next day
        val rl = CheckInRateLimit(
            lastAcceptedMillis = day1,
            acceptedToday = 4,
            dayStartMillis = 0L,
            consecutiveRejections = 3,
            autoPaused = true,
        )
        val (newRl, rolled) = CheckInEngine.rolloverIfNeeded(rl, day2)
        assertTrue(rolled)
        assertEquals(0, newRl.acceptedToday)
        assertEquals(0, newRl.consecutiveRejections)
        assertFalse(newRl.autoPaused)
        // last-accepted preserved (2h is "since last accepted")
        assertEquals(day1, newRl.lastAcceptedMillis)
    }

    @Test fun `rollover returns input unchanged within same day`() {
        val day1 = 1000L
        val rl = CheckInRateLimit(
            acceptedToday = 2,
            dayStartMillis = 0L,
        )
        val (newRl, rolled) = CheckInEngine.rolloverIfNeeded(rl, day1 + 1000L)
        assertFalse(rolled)
        assertEquals(2, newRl.acceptedToday)
    }

    @Test fun `rollover initialises day boundary on first call`() {
        val rl = CheckInRateLimit()
        val now = 1000L
        val (newRl, rolled) = CheckInEngine.rolloverIfNeeded(rl, now)
        assertTrue(rolled)
        assertEquals(0, newRl.acceptedToday)
        assertFalse(newRl.autoPaused)
        // dayStartMillis is now non-sentinel
        assertTrue(newRl.dayStartMillis != CheckInRateLimit.UNINITIALISED_DAY)
    }
}
