package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.BpdProfile

/**
 * v0.26.0 §3.3 FindingTest: the "Before you send" heuristic
 * interstitial exists, the file is wired into the launcher
 * package, and the heuristic's pure function honours its
 * §3.3 plan:
 *
 *  - Off by default (no flag set → no intervention).
 *  - Long message (>= 280 chars) + longMessagesIRegret → fire.
 *  - Late-night (after 23) + close contact + lateNightImpulses → fire.
 *  - All-caps (>= 50%) + sometimesISplit → fire.
 *  - All-caps + longMessagesIRegret → fire (defensive).
 *  - **False positives trust-burn** (the plan §6). The
 *    heuristic fires conservatively.
 *  - **Not a gate.** The surface has a "Send anyway" button
 *    (the dismiss callback) — the message goes out regardless.
 */
class BeforeYouSendHeuristicFindingTest {

    @Test
    fun `heuristic does not fire without a flag`() {
        val empty = BpdProfile()
        val decision = BeforeYouSendHeuristic.shouldIntervene(
            profile = empty,
            length = 5_000,
            allCapsRatio = 1f,
            after23 = true,
            closeContact = true,
        )
        assertFalse(
            "Heuristic must never fire without a BpdProfile flag set",
            decision,
        )
    }

    @Test
    fun `long message + longMessagesIRegret triggers`() {
        val profile = BpdProfile(longMessagesIRegret = true)
        assertTrue(
            BeforeYouSendHeuristic.shouldIntervene(
                profile = profile,
                length = 1_000,
                allCapsRatio = 0f,
                after23 = false,
                closeContact = false,
            ),
        )
    }

    @Test
    fun `late-night to close contact + lateNightImpulses triggers`() {
        val profile = BpdProfile(lateNightImpulses = true)
        assertTrue(
            BeforeYouSendHeuristic.shouldIntervene(
                profile = profile,
                length = 50,
                allCapsRatio = 0f,
                after23 = true,
                closeContact = true,
            ),
        )
    }

    @Test
    fun `all-caps + sometimesISplit triggers`() {
        val profile = BpdProfile(sometimesISplit = true)
        assertTrue(
            BeforeYouSendHeuristic.shouldIntervene(
                profile = profile,
                length = 50,
                allCapsRatio = 0.8f,
                after23 = false,
                closeContact = false,
            ),
        )
    }

    @Test
    fun `all-caps + longMessagesIRegret triggers (defensive)`() {
        val profile = BpdProfile(longMessagesIRegret = true)
        assertTrue(
            BeforeYouSendHeuristic.shouldIntervene(
                profile = profile,
                length = 50,
                allCapsRatio = 0.8f,
                after23 = false,
                closeContact = false,
            ),
        )
    }

    @Test
    fun `okAtNight alone never triggers the heuristic`() {
        val profile = BpdProfile(okAtNight = true)
        assertFalse(
            "okAtNight is the §3.5 flag, not the §3.3 one",
            BeforeYouSendHeuristic.shouldIntervene(
                profile = profile,
                length = 1_000,
                allCapsRatio = 1f,
                after23 = true,
                closeContact = true,
            ),
        )
    }

    @Test
    fun `short message + lateNightImpulses but NOT close contact does not trigger`() {
        val profile = BpdProfile(lateNightImpulses = true)
        assertFalse(
            "Late-night without close contact should not trigger (conservative by design)",
            BeforeYouSendHeuristic.shouldIntervene(
                profile = profile,
                length = 50,
                allCapsRatio = 0f,
                after23 = true,
                closeContact = false,
            ),
        )
    }

    @Test
    fun `contextFor maps the raw inputs to a typed context`() {
        val ctx = BeforeYouSendHeuristic.contextFor(
            length = 320,
            allCapsRatio = 0.6f,
            after23 = true,
            closeContact = true,
        )
        assertEquals(320, ctx.messageLength)
        assertTrue("All-caps ratio >= 0.5 must set isAllCaps", ctx.isAllCaps)
        assertTrue(ctx.sentAfter23)
        assertTrue(ctx.closeContact)
    }

    @Test
    fun `BeforeYouSendInterstitial composable exists in friction package`() {
        val cls = Class.forName("org.mindanchor.friction.BeforeYouSendInterstitialKt")
        val method = cls.declaredMethods.firstOrNull { it.name == "BeforeYouSendInterstitial" }
        assertTrue(
            "BeforeYouSendInterstitial must be a top-level @Composable fun in friction/BeforeYouSendInterstitial.kt",
            method != null,
        )
    }
}
