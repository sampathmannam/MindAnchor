package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the per-app session-length data layer.
 *
 * The data layer is the v0.20.1 round 4 implementation of
 * item M (per-app session-length UI). The UI surface is
 * deferred until the data layer is in production and a
 * clinical-review-approved wording exists.
 */
class PerAppSessionLengthTest {

    @Test
    fun `empty state returns fallback`() {
        val state = PerAppSessionLength()
        assertEquals(
            PerAppSessionLength.FALLBACK_MINUTES,
            state.defaultMinutes("com.instagram.android"),
        )
    }

    @Test
    fun `blank package returns fallback`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf("com.example.app" to 15L),
        )
        assertEquals(
            PerAppSessionLength.FALLBACK_MINUTES,
            state.defaultMinutes(""),
        )
    }

    @Test
    fun `single-app state returns the stored value`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf("com.example.app" to 15L),
        )
        assertEquals(15L, state.defaultMinutes("com.example.app"))
    }

    @Test
    fun `record adds a new entry`() {
        val state = PerAppSessionLength()
        val next = state.record("com.example.app", 7L)
        assertEquals(7L, next.defaultMinutes("com.example.app"))
        // The original is immutable.
        assertEquals(
            PerAppSessionLength.FALLBACK_MINUTES,
            state.defaultMinutes("com.example.app"),
        )
    }

    @Test
    fun `record overwrites an existing entry`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf("com.example.app" to 7L),
        )
        val next = state.record("com.example.app", 25L)
        assertEquals(25L, next.defaultMinutes("com.example.app"))
    }

    @Test
    fun `record preserves other entries`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf(
                "com.example.app" to 7L,
                "com.other.app" to 20L,
            ),
        )
        val next = state.record("com.example.app", 30L)
        assertEquals(30L, next.defaultMinutes("com.example.app"))
        assertEquals(20L, next.defaultMinutes("com.other.app"))
    }

    @Test
    fun `record clamps minutes to MIN_MINUTES`() {
        val state = PerAppSessionLength()
        val next = state.record("com.example.app", 0L)
        assertEquals(PerAppSessionLength.MIN_MINUTES, next.defaultMinutes("com.example.app"))
        val next2 = state.record("com.example.app", -100L)
        assertEquals(PerAppSessionLength.MIN_MINUTES, next2.defaultMinutes("com.example.app"))
    }

    @Test
    fun `record clamps minutes to MAX_MINUTES`() {
        val state = PerAppSessionLength()
        val next = state.record("com.example.app", 999L)
        assertEquals(PerAppSessionLength.MAX_MINUTES, next.defaultMinutes("com.example.app"))
    }

    @Test
    fun `record with blank package is a no-op`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf("com.example.app" to 7L),
        )
        val next = state.record("", 30L)
        assertEquals(state, next)
    }

    @Test
    fun `forget removes an entry`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf("com.example.app" to 7L),
        )
        val next = state.forget("com.example.app")
        assertEquals(
            PerAppSessionLength.FALLBACK_MINUTES,
            next.defaultMinutes("com.example.app"),
        )
    }

    @Test
    fun `forget with non-existent package is a no-op`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf("com.example.app" to 7L),
        )
        val next = state.forget("com.other.app")
        assertEquals(state, next)
    }

    @Test
    fun `forget preserves other entries`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf(
                "com.example.app" to 7L,
                "com.other.app" to 20L,
            ),
        )
        val next = state.forget("com.example.app")
        assertEquals(20L, next.defaultMinutes("com.other.app"))
    }

    @Test
    fun `encode produces empty string for empty state`() {
        assertEquals("", PerAppSessionLengthStore.encode(PerAppSessionLength()))
    }

    @Test
    fun `encode produces tab-separated pkg minutes per line`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf(
                "com.example.app" to 7L,
                "com.other.app" to 20L,
            ),
        )
        val encoded = PerAppSessionLengthStore.encode(state)
        // Sorted by package for diff stability.
        val lines = encoded.split("\n")
        assertEquals(2, lines.size)
        assertEquals("com.example.app\t7", lines[0])
        assertEquals("com.other.app\t20", lines[1])
    }

    @Test
    fun `decode round-trips an empty state`() {
        val decoded = PerAppSessionLengthStore.decode("")
        assertEquals(PerAppSessionLength(), decoded)
    }

    @Test
    fun `decode round-trips a multi-entry state`() {
        val original = PerAppSessionLength(
            perAppMinutes = mapOf(
                "com.example.app" to 7L,
                "com.other.app" to 20L,
                "com.third.app" to 30L,
            ),
        )
        val encoded = PerAppSessionLengthStore.encode(original)
        val decoded = PerAppSessionLengthStore.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `decode skips blank lines`() {
        val raw = """
            com.example.app	7

            com.other.app	20

        """.trimIndent()
        val decoded = PerAppSessionLengthStore.decode(raw)
        assertEquals(2, decoded.perAppMinutes.size)
        assertEquals(7L, decoded.defaultMinutes("com.example.app"))
        assertEquals(20L, decoded.defaultMinutes("com.other.app"))
    }

    @Test
    fun `decode skips malformed lines`() {
        val raw = """
            com.example.app	7
            this is not a valid line
            com.other.app	20
            com.third.app	not-a-number
            com.fourth.app
        """.trimIndent()
        val decoded = PerAppSessionLengthStore.decode(raw)
        assertEquals(2, decoded.perAppMinutes.size)
        assertEquals(7L, decoded.defaultMinutes("com.example.app"))
        assertEquals(20L, decoded.defaultMinutes("com.other.app"))
    }

    @Test
    fun `decode skips lines with blank package name`() {
        val raw = """
            	7
            com.example.app	7
        """.trimIndent()
        val decoded = PerAppSessionLengthStore.decode(raw)
        assertEquals(1, decoded.perAppMinutes.size)
        assertEquals(7L, decoded.defaultMinutes("com.example.app"))
    }

    @Test
    fun `decode clamps out-of-range minutes`() {
        val raw = """
            com.example.app	0
            com.other.app	999
            com.third.app	15
        """.trimIndent()
        val decoded = PerAppSessionLengthStore.decode(raw)
        assertEquals(
            PerAppSessionLength.MIN_MINUTES,
            decoded.defaultMinutes("com.example.app"),
        )
        assertEquals(
            PerAppSessionLength.MAX_MINUTES,
            decoded.defaultMinutes("com.other.app"),
        )
        assertEquals(15L, decoded.defaultMinutes("com.third.app"))
    }

    @Test
    fun `encode filters blank package names`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf(
                "" to 30L,
                "com.example.app" to 7L,
            ),
        )
        val encoded = PerAppSessionLengthStore.encode(state)
        assertTrue("encoded should not contain blank package: $encoded",
            !encoded.startsWith("\t"))
        assertTrue(encoded.contains("com.example.app\t7"))
    }

    @Test
    fun `encode clamps out-of-range minutes defensively`() {
        val state = PerAppSessionLength(
            perAppMinutes = mapOf(
                "com.example.app" to 0L,
                "com.other.app" to 999L,
            ),
        )
        val encoded = PerAppSessionLengthStore.encode(state)
        // Even though the data class already clamps, the
        // codec is defensive — it re-clamps on encode in
        // case the data layer is bypassed.
        assertTrue(encoded.contains("com.example.app\t${PerAppSessionLength.MIN_MINUTES}"))
        assertTrue(encoded.contains("com.other.app\t${PerAppSessionLength.MAX_MINUTES}"))
    }

    @Test
    fun `FALLBACK_MINUTES is 10`() {
        // The brief specifies 10 minutes as the fallback
        // (middle of the 5/10/20 row, the most-tapped
        // research time-box).
        assertEquals(10L, PerAppSessionLength.FALLBACK_MINUTES)
    }

    @Test
    fun `MIN_MINUTES and MAX_MINUTES are within the design range`() {
        assertEquals(1L, PerAppSessionLength.MIN_MINUTES)
        assertEquals(120L, PerAppSessionLength.MAX_MINUTES)
    }
}
