package org.mindanchor.osmode

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The pure half of OS Mode: posture mapping, the suspension decision,
 * and window-instance identity. No Android types — the whole point of
 * keeping [OsModeState] free of Context is that these rules are the
 * safety-relevant ones and must be exhaustively testable on the JVM.
 */
class OsModeStateTest {

    // --- statusFor -------------------------------------------------------

    @Test
    fun `no grant means not provisioned even with a stale opt-in`() {
        assertEquals(
            OsModeStatus.NOT_PROVISIONED,
            OsModeState.statusFor(deviceOwnerGranted = false, optedIn = false),
        )
        assertEquals(
            OsModeStatus.NOT_PROVISIONED,
            OsModeState.statusFor(deviceOwnerGranted = false, optedIn = true),
        )
    }

    @Test
    fun `grant without opt-in is available and never active by implication`() {
        assertEquals(
            OsModeStatus.AVAILABLE,
            OsModeState.statusFor(deviceOwnerGranted = true, optedIn = false),
        )
    }

    @Test
    fun `grant plus explicit opt-in is active`() {
        assertEquals(
            OsModeStatus.ACTIVE,
            OsModeState.statusFor(deviceOwnerGranted = true, optedIn = true),
        )
    }

    // --- decide ----------------------------------------------------------

    @Test
    fun `suspend requires every condition at once`() {
        assertEquals(
            SuspensionDecision.SUSPEND,
            OsModeState.decide(
                granted = true, optedIn = true, inWindow = true,
                releasedForThisWindow = false,
            ),
        )
    }

    @Test
    fun `each missing condition alone forces stand-down`() {
        val cases = listOf(
            // no grant
            Triple(false, true, true),
            // not opted in — provisioning alone never implies handing over the night
            Triple(true, false, true),
            // outside the window
            Triple(true, true, false),
        )
        for ((granted, optedIn, inWindow) in cases) {
            assertEquals(
                SuspensionDecision.STAND_DOWN,
                OsModeState.decide(granted, optedIn, inWindow, releasedForThisWindow = false),
            )
        }
    }

    @Test
    fun `an escape-hatch release stands down the current window only`() {
        assertEquals(
            SuspensionDecision.STAND_DOWN,
            OsModeState.decide(
                granted = true, optedIn = true, inWindow = true,
                releasedForThisWindow = true,
            ),
        )
        // …and the next night derives fresh again.
        assertEquals(
            SuspensionDecision.SUSPEND,
            OsModeState.decide(
                granted = true, optedIn = true, inWindow = true,
                releasedForThisWindow = false,
            ),
        )
    }

    // --- window-instance identity -----------------------------------------

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun epochOf(at: LocalDateTime): Long = OsModeState.epochMillisOf(at, zone)

    @Test
    fun `window that crosses midnight is still last night's instance before dawn`() {
        val start = LocalTime.of(22, 0)
        val now = LocalDateTime.of(2026, 8, 26, 3, 0)
        val expected = epochOf(LocalDateTime.of(2026, 8, 25, 22, 0))
        assertEquals(expected, epochOf(OsModeState.currentWindowStartedAt(now, start)))
    }

    @Test
    fun `window after its start belongs to today's instance`() {
        val start = LocalTime.of(22, 0)
        val now = LocalDateTime.of(2026, 8, 26, 23, 30)
        val expected = epochOf(LocalDateTime.of(2026, 8, 26, 22, 0))
        assertEquals(expected, epochOf(OsModeState.currentWindowStartedAt(now, start)))
    }

    @Test
    fun `same-day window consulted late at night still names today's start`() {
        // Outside such a window nothing consults this value; it merely
        // must not crash or name a future instant.
        val start = LocalTime.of(9, 0)
        val now = LocalDateTime.of(2026, 8, 26, 23, 0)
        val expected = epochOf(LocalDateTime.of(2026, 8, 26, 9, 0))
        assertEquals(expected, epochOf(OsModeState.currentWindowStartedAt(now, start)))
    }

    @Test
    fun `a release from a previous night does not reach into tonight`() {
        val start = LocalTime.of(22, 0)
        val yesterdayStart = LocalDateTime.of(2026, 8, 25, 22, 0)
        val now = LocalDateTime.of(2026, 8, 26, 23, 0)

        val releaseDuringYesterdayWindow =
            epochOf(yesterdayStart.plusHours(2)) // 00:00, the night-side of the old window
        val currentInstance = epochOf(OsModeState.currentWindowStartedAt(now, start))

        // The old release predates tonight's window start, so tonight suspends again.
        assert(releaseDuringYesterdayWindow < currentInstance)
    }
}
