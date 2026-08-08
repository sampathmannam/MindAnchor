package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Neff 2003 self-compassion break — see [CompassionMoment]
 * and `docs/research/15` §3. Small, scripted, opt-in, the
 * user's own words. Linardon 2020 (*J Clin Psychol* meta,
 * PMID 32586436) reports small-to-moderate effects across 27
 * RCTs of smartphone-delivered self-compassion / acceptance
 * apps; Liu 2023 (*Psicologia: Reflexão e Crítica* 36:32,
 * doi:10.1186/s41155-023-00276-w) reports a decrease in SI
 * after 4 weeks of app-guided loving-kindness meditation.
 */
class CompassionMomentTest {

    @Test
    fun `a moment is live only when the phrase is not blank`() {
        assertTrue(CompassionMoment("may I be kind to myself").isLive)
        assertFalse(CompassionMoment("").isLive)
        assertFalse(CompassionMoment("   ").isLive)
    }

    @Test
    fun `encode and decode round trip preserves phrases`() {
        val original = listOf(
            CompassionMoment("may I be kind to myself"),
            CompassionMoment("this is hard, and that is OK"),
            CompassionMoment("others feel this too"),
        )
        assertEquals(original, CompassionStore.decode( CompassionStore.encode(original)))
    }

    @Test
    fun `decode drops blank lines`() {
        val raw = "may I be kind to myself\n\n\nthis is hard, and that is OK\n   \n"
        val out = CompassionStore.decode(raw)
        assertEquals(2, out.size)
        assertEquals("may I be kind to myself", out[0].phrase)
        assertEquals("this is hard, and that is OK", out[1].phrase)
    }

    @Test
    fun `decode trims surrounding whitespace`() {
        val raw = "  may I be kind to myself  "
        val out = CompassionStore.decode(raw)
        assertEquals(1, out.size)
        assertEquals("may I be kind to myself", out[0].phrase)
    }

    @Test
    fun `rotate returns null for an empty list`() {
        assertNull(CompassionStore.rotate(emptyList(), reach = 0))
        assertNull(CompassionStore.rotate(listOf(CompassionMoment()), reach = 0))
    }

    @Test
    fun `rotate cycles through the user's live moments`() {
        // The anti-habituation rule from FrictionTone: the same
        // phrase must not become wallpaper. Round-robin rotation
        // is the simplest version of that rule.
        val moments = listOf(
            CompassionMoment("a"),
            CompassionMoment("b"),
            CompassionMoment("c"),
        )
        assertEquals("a", CompassionStore.rotate(moments, reach = 0)?.phrase)
        assertEquals("b", CompassionStore.rotate(moments, reach = 1)?.phrase)
        assertEquals("c", CompassionStore.rotate(moments, reach = 2)?.phrase)
        assertEquals("a", CompassionStore.rotate(moments, reach = 3)?.phrase)
    }

    @Test
    fun `rotate skips blank moments even when present in the list`() {
        // A list with a mix of live and blank entries rotates
        // only over the live ones. A blank entry is a
        // deleted/cleared slot, not a phantom rotation.
        val moments = listOf(
            CompassionMoment("a"),
            CompassionMoment(""),
            CompassionMoment("b"),
        )
        assertEquals("a", CompassionStore.rotate(moments, reach = 0)?.phrase)
        assertEquals("b", CompassionStore.rotate(moments, reach = 1)?.phrase)
        assertEquals("a", CompassionStore.rotate(moments, reach = 2)?.phrase)
    }

    @Test
    fun `rotate guards against a negative reach`() {
        val moments = listOf(CompassionMoment("a"), CompassionMoment("b"))
        // Without the guard, modulo on a negative would throw
        // ArithmeticException on somebody's home screen. The
        // brief is the same as FrictionTone's: a bad value is
        // treated as a first reach, not a crash.
        assertEquals("a", CompassionStore.rotate(moments, reach = -1)?.phrase)
        assertEquals("a", CompassionStore.rotate(moments, reach = -100)?.phrase)
    }
}
