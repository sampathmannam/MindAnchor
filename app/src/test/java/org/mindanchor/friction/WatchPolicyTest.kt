package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This decides when the phone interrupts somebody. The cases that matter
 * are the ones where it must not.
 */
class WatchPolicyTest {

    private val self = "org.mindanchor"
    private val launcher = "org.mindanchor"
    private val flagged = setOf("com.example.social", "com.example.video")
    private val now = 1_000_000L

    private fun gate(
        opened: String,
        allowance: Allowance? = null,
        at: Long = now,
        flaggedSet: Set<String> = flagged,
    ) = WatchPolicy.shouldGate(opened, flaggedSet, self, launcher, allowance, at)

    @Test
    fun `a flagged app arriving from anywhere is paused`() {
        // The whole point: this fires for a notification tap and a recents
        // swipe, not only for a tap on the home screen.
        assertTrue(gate("com.example.social"))
    }

    @Test
    fun `an app nobody flagged is left alone`() {
        assertFalse(gate("com.example.banking"))
    }

    @Test
    fun `the dialer is never paused, even when flagged`() {
        // Somebody trying to phone another person does not get a breathing
        // exercise first. This holds even if they flagged it themselves.
        WatchPolicy.NEVER_GATE.forEach { protectedPackage ->
            assertFalse(
                "gated $protectedPackage",
                gate(protectedPackage, flaggedSet = flagged + protectedPackage),
            )
        }
    }

    @Test
    fun `settings is never paused, so this can always be switched off`() {
        assertFalse(gate("com.android.settings", flaggedSet = flagged + "com.android.settings"))
    }

    @Test
    fun `this app never pauses itself`() {
        assertFalse(gate(self, flaggedSet = flagged + self))
    }

    @Test
    fun `the launcher is never paused`() {
        assertFalse(
            WatchPolicy.shouldGate(
                "com.other.launcher", flagged + "com.other.launcher", self,
                launcher = "com.other.launcher", allowance = null, now = now,
            ),
        )
    }

    @Test
    fun `a device with no known launcher still protects everything else`() {
        assertFalse(
            WatchPolicy.shouldGate(
                self, flagged + self, self, launcher = null, allowance = null, now = now,
            ),
        )
    }

    @Test
    fun `blank package names are ignored rather than acted on`() {
        assertFalse(gate(""))
        assertFalse(gate("   "))
    }

    @Test
    fun `a live allowance means no second pause`() {
        val allowance = WatchPolicy.allowanceFor("com.example.social", minutes = 5, now = now)
        assertFalse(gate("com.example.social", allowance, at = now + 60_000))
    }

    @Test
    fun `an allowance for one app does not cover another`() {
        val allowance = WatchPolicy.allowanceFor("com.example.social", minutes = 20, now = now)
        assertTrue(gate("com.example.video", allowance, at = now + 60_000))
    }

    @Test
    fun `an expired allowance pauses again`() {
        val allowance = WatchPolicy.allowanceFor("com.example.social", minutes = 5, now = now)
        assertFalse(gate("com.example.social", allowance, at = now + 5 * 60_000 - 1))
        assertTrue(gate("com.example.social", allowance, at = now + 5 * 60_000))
    }

    @Test
    fun `an untimed open buys half an hour, not forever and not one moment`() {
        // Forever would make the pause a formality. One moment would
        // re-pause on the first hop out to a share sheet and back, which
        // is how a tool teaches people to hate it.
        val allowance = WatchPolicy.allowanceFor("com.example.social", minutes = null, now = now)
        assertEquals(now + WatchPolicy.UNTIMED_ALLOWANCE_MILLIS, allowance.until)
        assertFalse(gate("com.example.social", allowance, at = now + 29 * 60_000))
        assertTrue(gate("com.example.social", allowance, at = now + 31 * 60_000))
    }

    @Test
    fun `flagging nothing pauses nothing`() {
        assertFalse(gate("com.example.social", flaggedSet = emptySet()))
    }
}
