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

    // --- T-1.3: what is closed right now -------------------------------
    //
    // The applied set was written by every sync and read by nothing, so
    // nobody could see which apps OS Mode had actually closed. These pin
    // the pure half: ordering, and what happens when a package no longer
    // resolves.

    @Test
    fun `nothing applied means nothing to show`() {
        assertEquals(emptyList<String>(), OsMode.suspendedNow(emptySet()) { "unused" })
    }

    @Test
    fun `applied packages are shown by their app label`() {
        val labels = mapOf("com.example.feed" to "Feed", "com.example.news" to "News")
        assertEquals(
            listOf("Feed", "News"),
            OsMode.suspendedNow(labels.keys) { labels.getValue(it) },
        )
    }

    @Test
    fun `the list reads alphabetically regardless of set order`() {
        // A Set has no order worth relying on, and a list that reshuffles
        // between openings reads as churn rather than status.
        val labels = mapOf("p.z" to "Zebra", "p.a" to "Apple", "p.m" to "mango")
        assertEquals(
            listOf("Apple", "mango", "Zebra"),
            OsMode.suspendedNow(labels.keys) { labels.getValue(it) },
        )
    }

    @Test
    fun `a package that no longer resolves falls back to its name rather than vanishing`() {
        // An app can be uninstalled while still in the applied set. Showing
        // the raw package name is honest; silently dropping it would claim
        // the launcher had closed one thing fewer than it did.
        assertEquals(
            listOf("com.example.gone"),
            OsMode.suspendedNow(setOf("com.example.gone")) { it },
        )
    }

    @Test
    fun `blank packages never reach the list`() {
        assertEquals(emptyList<String>(), OsMode.suspendedNow(setOf("", "   ")) { it })
    }

    // --- what was actually closed, not merely asked for ----------------
    //
    // setPackagesSuspended returns the packages it could NOT suspend --
    // most often because they are not installed. The default feed list is
    // seven popular apps, so a phone missing some of them is the normal
    // case, not the edge one. Recording the request rather than the result
    // made the applied set claim more than the system had done; T-1.3 puts
    // that set on screen, so it has to be true.

    @Test
    fun `everything the system accepted counts as suspended`() {
        assertEquals(
            listOf("com.a", "com.b"),
            DeviceOwner.actuallySuspended(listOf("com.a", "com.b"), emptyArray()),
        )
    }

    @Test
    fun `packages the system refused are not reported as closed`() {
        assertEquals(
            listOf("com.a"),
            DeviceOwner.actuallySuspended(listOf("com.a", "com.missing"), arrayOf("com.missing")),
        )
    }

    @Test
    fun `a request the system refused entirely reports nothing closed`() {
        assertEquals(
            emptyList<String>(),
            DeviceOwner.actuallySuspended(listOf("com.a"), arrayOf("com.a")),
        )
    }

    @Test
    fun `a null return is treated as complete success`() {
        // The platform is documented to return an array, but it is an
        // Android API boundary: a null must not erase the whole result.
        assertEquals(
            listOf("com.a"),
            DeviceOwner.actuallySuspended(listOf("com.a"), null),
        )
    }
}
