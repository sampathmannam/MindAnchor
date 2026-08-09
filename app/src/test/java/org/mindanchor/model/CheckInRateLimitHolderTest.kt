package org.mindanchor.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [CheckInRateLimitHolder] + the
 * cross-unlock scenario that was the headline
 * fix in the v0.20.1 round 5 follow-up.
 *
 * The engine is pure (no Android types), so
 * these tests run on the JVM directly.
 * Python-mirrored as `python3 -c "..."` during
 * the v0.20.1 round 5 ship; the source of truth
 * for the algorithm is the companion Python
 * block in commit `d10753d`.
 *
 * v0.20.1 round 5 follow-up: tests now exercise
 * the monitor-based [CheckInRateLimitHolder.update]
 * function and a concurrent-writer scenario that
 * the previous `@Volatile`-only design would
 * fail (the test `concurrent updates are
 * serialised` catches the lost-update race).
 */
class CheckInRateLimitHolderTest {

    @After
    fun resetHolder() {
        // Each test starts from a clean holder.
        // The holder is a process-wide singleton;
        // tests can run in any order, and one
        // test's residue would corrupt the next.
        CheckInRateLimitHolder.resetForTesting()
    }

    private fun dayStartAt(millis: Long): Long =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test fun `holder starts fresh`() {
        assertEquals(0L, CheckInRateLimitHolder.state.lastAcceptedMillis)
        assertEquals(0, CheckInRateLimitHolder.state.acceptedToday)
        assertEquals(0, CheckInRateLimitHolder.state.consecutiveRejections)
        assertFalse(CheckInRateLimitHolder.state.autoPaused)
        assertEquals(
            CheckInRateLimit.UNINITIALISED_DAY,
            CheckInRateLimitHolder.state.dayStartMillis,
        )
    }

    @Test fun `update writes are visible to subsequent reads`() {
        val now = System.currentTimeMillis()
        val newState = CheckInRateLimit(
            lastAcceptedMillis = now,
            acceptedToday = 1,
            dayStartMillis = dayStartAt(now),
        )
        CheckInRateLimitHolder.update { newState }
        assertEquals(now, CheckInRateLimitHolder.state.lastAcceptedMillis)
        assertEquals(1, CheckInRateLimitHolder.state.acceptedToday)
    }

    @Test fun `update returns the new state`() {
        val now = System.currentTimeMillis()
        val newState = CheckInRateLimit(
            lastAcceptedMillis = now,
            acceptedToday = 7,
            dayStartMillis = dayStartAt(now),
        )
        val returned = CheckInRateLimitHolder.update { newState }
        assertEquals(7, returned.acceptedToday)
        assertEquals(now, returned.lastAcceptedMillis)
    }

    @Test fun `concurrent updates are serialised`() {
        // The headline test for v0.20.1 round 5
        // follow-up: 100 concurrent threads each
        // record a rejection via the monitor-based
        // update. With the previous `@Volatile`-only
        // design, the read-modify-write race could
        // lose some of the 100 increments, leaving
        // `consecutiveRejections` at some value
        // *less than* 100. The monitor guarantees
        // that every increment is observed.
        val now = System.currentTimeMillis()
        CheckInRateLimitHolder.update { rl ->
            rl.copy(dayStartMillis = dayStartAt(now))
        }

        val threads = 100
        val latch = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        val errors = AtomicInteger(0)

        try {
            repeat(threads) {
                pool.submit {
                    try {
                        latch.await()
                        CheckInRateLimitHolder.update { rl ->
                            CheckInEngine.recordRejection(rl, now)
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            // Release all threads at once.
            latch.countDown()
            pool.shutdown()
            assertTrue(
                "concurrent update pool did not finish in 30s",
                pool.awaitTermination(30, TimeUnit.SECONDS),
            )
        } finally {
            if (!pool.isTerminated) {
                pool.shutdownNow()
            }
        }

        assertEquals(0, errors.get())
        // Every increment must be visible.
        // The previous @Volatile design would
        // have lost some; the monitor preserves
        // them.
        assertEquals(
            threads,
            CheckInRateLimitHolder.state.consecutiveRejections,
        )
    }

    @Test fun `cross-unlock rejection counter persists`() {
        // The headline scenario: 3 phone unlocks,
        // 3 rejections, auto-pause on the third.
        // Without the holder, each unlock would
        // create a fresh rate-limit and the
        // auto-pause would never trigger.
        val t = 8L * 60 * 60 * 1000

        // Unlock 1
        val (afterInit, _) = CheckInEngine.rolloverIfNeeded(
            CheckInRateLimitHolder.state, t,
        )
        CheckInRateLimitHolder.update { afterInit }
        assertTrue(
            CheckInEngine.shouldFire(
                CheckInRateLimitHolder.state, CheckInState(), t,
            ),
        )
        CheckInRateLimitHolder.update { rl ->
            CheckInEngine.recordRejection(rl, t)
        }
        assertEquals(1, CheckInRateLimitHolder.state.consecutiveRejections)
        assertFalse(CheckInRateLimitHolder.state.autoPaused)

        // Unlock 2
        val t2 = t + 60L * 60 * 1000
        assertTrue(
            CheckInEngine.shouldFire(
                CheckInRateLimitHolder.state, CheckInState(), t2,
            ),
        )
        CheckInRateLimitHolder.update { rl ->
            CheckInEngine.recordRejection(rl, t2)
        }
        assertEquals(2, CheckInRateLimitHolder.state.consecutiveRejections)
        assertFalse(CheckInRateLimitHolder.state.autoPaused)

        // Unlock 3 — should auto-pause
        val t3 = t2 + 60L * 60 * 1000
        assertTrue(
            CheckInEngine.shouldFire(
                CheckInRateLimitHolder.state, CheckInState(), t3,
            ),
        )
        CheckInRateLimitHolder.update { rl ->
            CheckInEngine.recordRejection(rl, t3)
        }
        assertEquals(3, CheckInRateLimitHolder.state.consecutiveRejections)
        assertTrue(CheckInRateLimitHolder.state.autoPaused)

        // Subsequent unlock should NOT fire
        val t4 = t3 + 60L * 60 * 1000
        assertFalse(
            CheckInEngine.shouldFire(
                CheckInRateLimitHolder.state, CheckInState(), t4,
            ),
        )
    }

    @Test fun `acceptance resets rejection counter`() {
        val now = System.currentTimeMillis()
        val t = 8L * 60 * 60 * 1000
        // Start with 2 rejections
        val rl = CheckInRateLimit(
            lastAcceptedMillis = 0,
            acceptedToday = 0,
            dayStartMillis = dayStartAt(t),
            consecutiveRejections = 2,
            autoPaused = false,
        )
        CheckInRateLimitHolder.update { rl }

        // Accept
        val checkIn = CheckIn(rating = 3, atMillis = now)
        val (newRl, _) = CheckInEngine.recordAcceptance(
            rateLimit = CheckInRateLimitHolder.state,
            state = CheckInState(),
            checkIn = checkIn,
            nowMillis = now,
        )
        CheckInRateLimitHolder.update { newRl }

        assertEquals(0, CheckInRateLimitHolder.state.consecutiveRejections)
        assertFalse(CheckInRateLimitHolder.state.autoPaused)
        assertEquals(1, CheckInRateLimitHolder.state.acceptedToday)
    }

    @Test fun `day rollover clears auto-pause and counts`() {
        val t = 8L * 60 * 60 * 1000
        // State: 3 rejections, auto-paused, 4 accepted, day 1
        CheckInRateLimitHolder.update {
            CheckInRateLimit(
                lastAcceptedMillis = t,
                acceptedToday = 4,
                dayStartMillis = dayStartAt(t),
                consecutiveRejections = 3,
                autoPaused = true,
            )
        }

        // Day 2 8am
        val t2 = t + 24L * 60 * 60 * 1000
        val shouldFire = CheckInEngine.shouldFire(
            CheckInRateLimitHolder.state, CheckInState(), t2,
        )
        // The post-rollover state has auto_paused=false,
        // acceptedToday=0, so shouldFire is true (assuming
        // the 90-min interval has passed — it has, 24h
        // since last accepted).
        assertTrue("day rollover should reset auto-pause", shouldFire)
    }

    @Test fun `reset clears all transient state`() {
        val now = System.currentTimeMillis()
        CheckInRateLimitHolder.update {
            CheckInRateLimit(
                lastAcceptedMillis = now,
                acceptedToday = 2,
                dayStartMillis = dayStartAt(now),
                consecutiveRejections = 2,
                autoPaused = true,
            )
        }
        CheckInRateLimitHolder.update { rl ->
            CheckInEngine.reset(rl, now)
        }
        assertEquals(0L, CheckInRateLimitHolder.state.lastAcceptedMillis)
        assertEquals(0, CheckInRateLimitHolder.state.acceptedToday)
        assertEquals(0, CheckInRateLimitHolder.state.consecutiveRejections)
        assertFalse(CheckInRateLimitHolder.state.autoPaused)
        // The new dayStartMillis is set by reset to
        // the start of `now`'s day, not the
        // UNINITIALISED_DAY sentinel.
        assertTrue(
            CheckInRateLimitHolder.state.dayStartMillis !=
                CheckInRateLimit.UNINITIALISED_DAY,
        )
    }
}
