package org.mindanchor.friction

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for [MorningProtectionState] and
 * [isInMorningWindow]. The window decision is the safety
 * surface — it is the function that decides whether a
 * doomscroll app gets gated at 07:14 — so the cases below
 * cover the everyday edge points a rushed implementation
 * would miss.
 */
class MorningProtectionTest {

    private val zone = ZoneId.of("UTC")

    private fun instant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Instant =
        java.time.ZonedDateTime.of(
            year,
            month,
            day,
            hour,
            minute,
            0,
            0,
            zone,
        ).toInstant()

    @Test
    fun `state rejects minutes above the cap`() {
        val ex = runCatching {
            MorningProtectionState(minutes = MorningProtectionState.MAX_MINUTES + 1)
        }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $ex", ex is IllegalArgumentException)
    }

    @Test
    fun `state accepts the cap as the maximum`() {
        // The cap is inclusive. 60 is the maximum the
        // habit-formation literature justifies.
        val state = MorningProtectionState(minutes = MorningProtectionState.MAX_MINUTES)
        assertEquals(60, state.minutes)
    }

    @Test
    fun `window is inactive when the toggle is off`() {
        val now = instant(2026, 8, 26, 7, 30)
        val state = MorningProtectionState(
            enabled = false,
            minutes = 10,
            lastFirstUnlockEpochMillis = now.toEpochMilli() - 5 * 60_000L,
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive when minutes is zero`() {
        val now = instant(2026, 8, 26, 7, 30)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 0,
            lastFirstUnlockEpochMillis = now.toEpochMilli() - 5 * 60_000L,
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive when no first unlock has been recorded`() {
        val now = instant(2026, 8, 26, 7, 30)
        val state = MorningProtectionState(enabled = true, minutes = 10)
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive before the morning window opens`() {
        // 03:30 UTC is before the 04:00 local
        // morning-window start. The function is
        // pinned to the supplied zone; the production
        // receiver uses the device zone.
        val now = instant(2026, 8, 26, 3, 30)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 10,
            lastFirstUnlockEpochMillis = now.toEpochMilli() - 5 * 60_000L,
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive after the morning window closes`() {
        val now = instant(2026, 8, 26, 12, 0)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 60,
            lastFirstUnlockEpochMillis = now.toEpochMilli() - 30 * 60_000L,
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive when the first unlock was yesterday`() {
        val now = instant(2026, 8, 26, 7, 0)
        val yesterday = instant(2026, 8, 25, 7, 0).toEpochMilli()
        val state = MorningProtectionState(
            enabled = true,
            minutes = 60,
            lastFirstUnlockEpochMillis = yesterday,
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is active inside the morning window within N minutes of first unlock`() {
        // 07:00 first unlock, 07:05 now, N=10 — active.
        val firstUnlock = instant(2026, 8, 26, 7, 0)
        val now = instant(2026, 8, 26, 7, 5)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 10,
            lastFirstUnlockEpochMillis = firstUnlock.toEpochMilli(),
        )
        assertTrue(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is active at the exact N-minute mark`() {
        // The window is [firstUnlock, firstUnlock + N min] inclusive.
        val firstUnlock = instant(2026, 8, 26, 7, 0)
        val now = instant(2026, 8, 26, 7, 10)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 10,
            lastFirstUnlockEpochMillis = firstUnlock.toEpochMilli(),
        )
        assertTrue(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive just past the N-minute mark`() {
        val firstUnlock = instant(2026, 8, 26, 7, 0)
        val now = instant(2026, 8, 26, 7, 11)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 10,
            lastFirstUnlockEpochMillis = firstUnlock.toEpochMilli(),
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is active at the exact first-unlock instant`() {
        // Zero elapsed is in [0, N min], inclusive.
        val firstUnlock = instant(2026, 8, 26, 7, 0)
        val now = firstUnlock
        val state = MorningProtectionState(
            enabled = true,
            minutes = 10,
            lastFirstUnlockEpochMillis = firstUnlock.toEpochMilli(),
        )
        assertTrue(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `window is inactive for a first unlock in the future`() {
        // The function defends against a clock-skewed
        // first-unlock timestamp (a future value
        // would open a perpetual window). The
        // elapsed check excludes negatives.
        val firstUnlock = instant(2026, 8, 26, 8, 0)
        val now = instant(2026, 8, 26, 7, 0)
        val state = MorningProtectionState(
            enabled = true,
            minutes = 10,
            lastFirstUnlockEpochMillis = firstUnlock.toEpochMilli(),
        )
        assertFalse(isInMorningWindow(now, state, zone))
    }

    @Test
    fun `morning window starts at 04 00 local and ends at 12 00 local`() {
        // A regression net: if a future change moves
        // the window, the values should be obvious
        // here rather than scattered.
        val (start, end) = MorningProtectionState.MORNING_WINDOW
        assertEquals(LocalTime.of(4, 0), start)
        assertEquals(LocalTime.of(12, 0), end)
    }

    @Test
    fun `morningProtectionGatedPackages removes never-gate packages`() {
        val doomscroll = setOf(
            "com.example.social",
            "com.android.dialer",
            "com.android.settings",
            "com.example.video",
        )
        val gated = morningProtectionGatedPackages(doomscroll)
        assertTrue("com.example.social in gated", "com.example.social" in gated)
        assertTrue("com.example.video in gated", "com.example.video" in gated)
        assertFalse(
            "dialer must not be in the morning-protection set",
            "com.android.dialer" in gated,
        )
        assertFalse(
            "settings must not be in the morning-protection set",
            "com.android.settings" in gated,
        )
    }
}
