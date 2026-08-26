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

    // --- v0.70+ (Phase 1 T-1.5) morning protection ---

    @Test
    fun `morning protection with an empty context does not gate anything`() {
        // A null or empty morning protection is the
        // pre-T-1.5 shape; the function must keep its
        // pre-morning-protection semantics for callers
        // that have not been updated.
        val morning = MorningProtectionContext(active = false, packages = emptySet())
        assertFalse(
            WatchPolicy.shouldGate(
                opened = "com.example.feed",
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = null,
                now = now,
                morningProtection = morning,
            ),
        )
    }

    @Test
    fun `morning protection gates doomscroll packages even when not flagged`() {
        // The whole point of the morning window: a
        // doomscroll app the user has not flagged for
        // full-day friction still meets the gate
        // during the morning window. The morning
        // protection set is the doomscroll list
        // already filtered against NEVER_GATE.
        val morning = MorningProtectionContext(
            active = true,
            packages = setOf("com.example.feed"),
        )
        assertTrue(
            WatchPolicy.shouldGate(
                opened = "com.example.feed",
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = null,
                now = now,
                morningProtection = morning,
            ),
        )
    }

    @Test
    fun `morning protection does not gate packages outside the morning set`() {
        // The morning protection set is the doomscroll
        // list. A non-doomscroll package (e.g. the
        // banking app) is not in the morning set, so
        // it is not gated even with the morning window
        // active.
        val morning = MorningProtectionContext(
            active = true,
            packages = setOf("com.example.feed"),
        )
        assertFalse(
            WatchPolicy.shouldGate(
                opened = "com.example.banking",
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = null,
                now = now,
                morningProtection = morning,
            ),
        )
    }

    @Test
    fun `morning protection does not override an existing live allowance`() {
        // Someone who passed the gate for a package
        // is not re-paused on the next foreground
        // transition inside the session, even if the
        // morning window is still active. The
        // allowance check happens before the morning
        // gate check, so the existing semantic holds.
        val allowance = WatchPolicy.allowanceFor(
            packageName = "com.example.feed",
            minutes = 5,
            now = now,
        )
        val morning = MorningProtectionContext(
            active = true,
            packages = setOf("com.example.feed"),
        )
        assertFalse(
            WatchPolicy.shouldGate(
                opened = "com.example.feed",
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = allowance,
                now = now + 60_000,
                morningProtection = morning,
            ),
        )
    }

    @Test
    fun `morning protection never gates the dialer even if the doomscroll list contained it`() {
        // The buildMorningProtectionContext helper
        // removes WatchPolicy.NEVER_GATE from the
        // doomscroll set before the policy sees it.
        // If a caller passes a set that *does*
        // contain a never-gate package, the policy
        // still refuses. The NEVER_GATE check is the
        // last line, not the first.
        val morning = MorningProtectionContext(
            active = true,
            packages = setOf("com.android.dialer"),
        )
        assertFalse(
            WatchPolicy.shouldGate(
                opened = "com.android.dialer",
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = null,
                now = now,
                morningProtection = morning,
            ),
        )
    }

    @Test
    fun `morning protection does not gate this app or the launcher`() {
        // The self-and-launcher exclusions apply
        // even when the morning protection is
        // active. A buggy doomscroll list that
        // contained the launcher's own package
        // would still be a no-op.
        val morning = MorningProtectionContext(
            active = true,
            packages = setOf(self, launcher),
        )
        assertFalse(
            WatchPolicy.shouldGate(
                opened = self,
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = null,
                now = now,
                morningProtection = morning,
            ),
        )
        assertFalse(
            WatchPolicy.shouldGate(
                opened = launcher,
                flagged = emptySet(),
                self = self,
                launcher = launcher,
                allowance = null,
                now = now,
                morningProtection = morning,
            ),
        )
    }

    @Test
    fun `flagged apps still gate when morning protection is null`() {
        // The default-value shape: callers that
        // have not been updated to thread the
        // morning context still see the historical
        // behaviour, which is what the migration
        // is meant to preserve.
        assertTrue(gate("com.example.social"))
    }
}
