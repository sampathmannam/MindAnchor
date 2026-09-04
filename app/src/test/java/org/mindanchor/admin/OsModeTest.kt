package org.mindanchor.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure-logic tests for [OsMode] (master plan T-1.1/T-1.2).
 *
 * The whole suspension decision is derived from the sunset window and
 * never persisted, so the truth table here is the actual contract —
 * not a mirror of stored state.
 */
class OsModeTest {

    // --- shouldSuspend: the truth table ---

    @Test
    fun `all four conditions together suspend`() {
        assertTrue(
            OsMode.shouldSuspend(
                osModeEnabled = true,
                sunsetEnabled = true,
                insideWindow = true,
                earlyReleaseActive = false,
            ),
        )
    }

    @Test
    fun `os mode off means never, even mid-window`() {
        assertFalse(
            OsMode.shouldSuspend(
                osModeEnabled = false,
                sunsetEnabled = true,
                insideWindow = true,
                earlyReleaseActive = false,
            ),
        )
    }

    @Test
    fun `sunset off means never, even with os mode armed`() {
        assertFalse(
            OsMode.shouldSuspend(
                osModeEnabled = true,
                sunsetEnabled = false,
                insideWindow = true,
                earlyReleaseActive = false,
            ),
        )
    }

    @Test
    fun `outside the window means never`() {
        assertFalse(
            OsMode.shouldSuspend(
                osModeEnabled = true,
                sunsetEnabled = true,
                insideWindow = false,
                earlyReleaseActive = false,
            ),
        )
    }

    @Test
    fun `an active early release suppresses this window only via its flag`() {
        assertFalse(
            OsMode.shouldSuspend(
                osModeEnabled = true,
                sunsetEnabled = true,
                insideWindow = true,
                earlyReleaseActive = true,
            ),
        )
    }

    // --- mostRecentWindowStart: window identity for the early-release marker ---

    @Test
    fun `same-day window - after start belongs to today's window`() {
        val now = LocalDateTime.of(2026, 8, 26, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 26, 9, 0),
            OsMode.mostRecentWindowStart(now, LocalTime.of(9, 0)),
        )
    }

    @Test
    fun `same-day window - before start belongs to yesterday's window`() {
        val now = LocalDateTime.of(2026, 8, 26, 8, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 25, 9, 0),
            OsMode.mostRecentWindowStart(now, LocalTime.of(9, 0)),
        )
    }

    @Test
    fun `same-day window - exactly at start counts as today's window`() {
        val now = LocalDateTime.of(2026, 8, 26, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 26, 9, 0),
            OsMode.mostRecentWindowStart(now, LocalTime.of(9, 0)),
        )
    }

    @Test
    fun `midnight-crossing window - late evening belongs to tonight`() {
        // 22:00 → 07:00 default-shaped window; 23:30 is after tonight's start.
        val now = LocalDateTime.of(2026, 8, 26, 23, 30)
        assertEquals(
            LocalDateTime.of(2026, 8, 26, 22, 0),
            OsMode.mostRecentWindowStart(now, LocalTime.of(22, 0)),
        )
    }

    @Test
    fun `midnight-crossing window - small hours belong to last night`() {
        // 03:00 on the 27th is inside the window that opened 22:00 on the 26th.
        val now = LocalDateTime.of(2026, 8, 27, 3, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 26, 22, 0),
            OsMode.mostRecentWindowStart(now, LocalTime.of(22, 0)),
        )
    }

    @Test
    fun `midnight-crossing window - just before start still belongs to last night`() {
        val now = LocalDateTime.of(2026, 8, 26, 21, 59)
        assertEquals(
            LocalDateTime.of(2026, 8, 25, 22, 0),
            OsMode.mostRecentWindowStart(now, LocalTime.of(22, 0)),
        )
    }

    // --- stateFor: what the guided surface renders ---

    @Test
    fun `no grant means not provisioned regardless of switch`() {
        assertEquals(OsModeState.NotProvisioned, OsMode.stateFor(false, false))
        assertEquals(OsModeState.NotProvisioned, OsMode.stateFor(false, true))
    }

    @Test
    fun `grant without switch is available`() {
        assertEquals(OsModeState.Available, OsMode.stateFor(true, false))
    }

    @Test
    fun `grant plus switch is armed`() {
        assertEquals(OsModeState.Armed, OsMode.stateFor(true, true))
    }
}
