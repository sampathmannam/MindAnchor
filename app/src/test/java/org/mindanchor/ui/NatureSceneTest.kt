package org.mindanchor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NatureSceneTest {

    @Test
    fun `off draws nothing`() {
        assertNull(NatureScene.resolve(NatureScene.OFF, epochDay = 20_000))
    }

    @Test
    fun `a fixed choice is honoured every day`() {
        for (day in 0L..10L) {
            assertEquals(NatureScene.WATER, NatureScene.resolve(NatureScene.WATER, day))
        }
    }

    @Test
    fun `rotation cycles through the drawable scenes`() {
        val seen = (0L until 3L).map { NatureScene.resolve(NatureScene.ROTATE, it) }
        assertEquals(NatureScene.DRAWABLE.toSet(), seen.toSet())
    }

    @Test
    fun `rotation is stable within a day and changes the next`() {
        val today = NatureScene.resolve(NatureScene.ROTATE, 20_000)
        assertEquals(today, NatureScene.resolve(NatureScene.ROTATE, 20_000))
        assertNotEquals(today, NatureScene.resolve(NatureScene.ROTATE, 20_001))
    }

    @Test
    fun `rotation never yields a non-drawable scene, including before the epoch`() {
        for (day in -800L..800L) {
            val scene = NatureScene.resolve(NatureScene.ROTATE, day)
            assertTrue("day $day gave $scene", scene in NatureScene.DRAWABLE)
        }
    }

    @Test
    fun `unknown or missing settings fall back to rotation`() {
        assertEquals(NatureScene.ROTATE, NatureScene.fromKey(null))
        assertEquals(NatureScene.ROTATE, NatureScene.fromKey("nonsense"))
        assertEquals(NatureScene.FOREST, NatureScene.fromKey("FOREST"))
        assertEquals(NatureScene.MEADOW, NatureScene.fromKey("meadow"))
    }

    @Test
    fun `ridge stays inside its band and is deterministic`() {
        for (layer in 0..2) {
            var previous: Double? = null
            var x = 0.0
            while (x <= 1.0) {
                val offset = NatureMath.ridgeOffset(x, layer)
                assertTrue("offset $offset out of range", offset in 0.0..1.0)
                assertEquals(offset, NatureMath.ridgeOffset(x, layer), 1e-12)
                // Terrain should undulate, not sit flat.
                previous?.let { assertTrue(kotlin.math.abs(offset - it) < 0.35) }
                previous = offset
                x += 0.02
            }
        }
    }

    @Test
    fun `ridge layers differ so the ridges do not overlap exactly`() {
        val a = (0..20).map { NatureMath.ridgeOffset(it / 20.0, 0) }
        val b = (0..20).map { NatureMath.ridgeOffset(it / 20.0, 1) }
        assertNotEquals(a, b)
    }

    @Test
    fun `spread positions are ordered and inside the screen`() {
        val positions = NatureMath.spread(7)
        assertEquals(7, positions.size)
        assertEquals(positions.sorted(), positions)
        positions.forEach { assertTrue(it in 0.0..1.0) }
        assertEquals(listOf(0.5), NatureMath.spread(1))
        assertTrue(NatureMath.spread(0).isEmpty())
    }

    @Test
    fun `jitter is bounded and deterministic but varies by index`() {
        val values = (0..30).map { NatureMath.jitter(it, salt = 5) }
        values.forEach { assertTrue("jitter $it out of range", it in -1.0..1.0) }
        assertEquals(values, (0..30).map { NatureMath.jitter(it, salt = 5) })
        assertTrue("jitter should vary", values.distinct().size > 20)
    }
}
