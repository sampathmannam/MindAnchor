package org.mindanchor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyMathTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    // --- The contrast guarantee ------------------------------------------

    /**
     * The whole point of the adaptive haze: there must be no minute of the
     * day, in either theme, where the clock or the buttons beneath it fall
     * below WCAG AA against the sky they sit on.
     */
    @Test
    fun `text clears WCAG AA at every minute in both themes`() {
        var worst = Double.MAX_VALUE
        var worstAt = ""
        for (minute in 0 until 24 * 60) {
            for (dark in listOf(false, true)) {
                val palette = SkyMath.palette(minute, dark)
                val primary = SkyMath.primaryTextFor(palette)
                // The clock band matters and used to be missing here, which
                // is how the palette kept a true 3.77:1 while this test
                // reported everything as fine: it swept only the two bands
                // the solver already worked against, so it could never
                // catch the solver's own blind spot. blend() is a lerp, so
                // this reconstructs the stretch the clock actually sits on.
                val clockBand = SkyMath.blend(palette.top, palette.mid, 0.56)
                listOf(
                    "clock" to clockBand,
                    "mid" to palette.mid,
                    "bottom" to palette.bottom,
                ).forEach { (band, color) ->
                    val background = SkyMath.withHaze(color, palette)
                    // Secondary text is the same colour dimmed by alpha, so
                    // it composites against the same background.
                    val secondary =
                        SkyMath.blend(background, primary, SkyMath.SECONDARY_ALPHA)
                    val primaryContrast = SkyMath.contrast(primary, background)
                    val secondaryContrast = SkyMath.contrast(secondary, background)
                    val lowest = minOf(primaryContrast, secondaryContrast)
                    if (lowest < worst) {
                        worst = lowest
                        worstAt = "minute=$minute dark=$dark band=$band"
                    }
                }
            }
        }
        assertTrue(
            "worst contrast $worst at $worstAt is below AA ${SkyMath.MIN_CONTRAST}",
            worst >= SkyMath.MIN_CONTRAST,
        )
    }

    @Test
    fun `the haze stays subtle enough to keep the palette visible`() {
        for (minute in 0 until 24 * 60) {
            for (dark in listOf(false, true)) {
                val alpha = SkyMath.palette(minute, dark).hazeAlpha
                assertTrue(
                    "haze $alpha too strong at minute $minute",
                    alpha <= SkyMath.MAX_HAZE_ALPHA.toFloat() + 1e-4f,
                )
                assertTrue("negative haze at minute $minute", alpha >= 0f)
            }
        }
    }

    @Test
    fun `night and midday need no haze at all`() {
        assertEquals(0f, SkyMath.palette(at(2), darkTheme = false).hazeAlpha, 0.001f)
        assertEquals(0f, SkyMath.palette(at(13), darkTheme = false).hazeAlpha, 0.001f)
    }

    // --- Palette shape ----------------------------------------------------

    @Test
    fun `night is dark and asks for light text`() {
        val night = SkyMath.palette(at(2), darkTheme = false)
        assertTrue(night.lightText)
        assertTrue(SkyMath.luminance(night.top) < 0.05)
    }

    @Test
    fun `midday is bright and asks for dark text`() {
        val day = SkyMath.palette(at(12), darkTheme = false)
        assertTrue("expected a light sky", SkyMath.luminance(day.bottom) > 0.6)
        assertTrue("bright sky needs dark text", !day.lightText)
    }

    @Test
    fun `night holds flat between midnight and five`() {
        val midnight = SkyMath.palette(at(0), darkTheme = false)
        assertEquals(midnight.top, SkyMath.palette(at(3), darkTheme = false).top)
        assertEquals(midnight.top, SkyMath.palette(at(5), darkTheme = false).top)
    }

    @Test
    fun `day holds flat between nine and seventeen`() {
        val nine = SkyMath.palette(at(9), darkTheme = false)
        assertEquals(nine.bottom, SkyMath.palette(at(12), darkTheme = false).bottom)
        assertEquals(nine.bottom, SkyMath.palette(at(17), darkTheme = false).bottom)
    }

    @Test
    fun `dawn brightens monotonically and dusk darkens monotonically`() {
        var previous = SkyMath.luminance(SkyMath.palette(at(5), false).bottom)
        for (hour in 6..9) {
            val current = SkyMath.luminance(SkyMath.palette(at(hour), false).bottom)
            assertTrue("sky darkened during dawn at $hour", current >= previous - 1e-9)
            previous = current
        }
        previous = SkyMath.luminance(SkyMath.palette(at(17), false).bottom)
        for (hour in 18..22) {
            val current = SkyMath.luminance(SkyMath.palette(at(hour), false).bottom)
            assertTrue("sky brightened during dusk at $hour", current <= previous + 1e-9)
            previous = current
        }
    }

    @Test
    fun `the palette is continuous across the midnight wrap`() {
        val before = SkyMath.palette(at(23, 59), darkTheme = false)
        val after = SkyMath.palette(at(0, 0), darkTheme = false)
        assertTrue(kotlin.math.abs(before.top.r - after.top.r) <= 2)
        assertTrue(kotlin.math.abs(before.top.g - after.top.g) <= 2)
        assertTrue(kotlin.math.abs(before.top.b - after.top.b) <= 2)
    }

    @Test
    fun `every minute produces valid colour channels`() {
        for (minute in 0 until 24 * 60) {
            listOf(true, false).forEach { dark ->
                val palette = SkyMath.palette(minute, dark)
                listOf(palette.top, palette.mid, palette.bottom).forEach { color ->
                    assertTrue(color.r in 0..255)
                    assertTrue(color.g in 0..255)
                    assertTrue(color.b in 0..255)
                }
            }
        }
    }

    @Test
    fun `dark theme never brightens the sky`() {
        for (hour in 0..23) {
            val light = SkyMath.luminance(SkyMath.palette(at(hour), darkTheme = false).bottom)
            val dark = SkyMath.luminance(SkyMath.palette(at(hour), darkTheme = true).bottom)
            assertTrue("dark theme brighter at $hour", dark <= light + 1e-9)
        }
    }

    @Test
    fun `out of range minutes wrap instead of crashing`() {
        assertEquals(
            SkyMath.palette(at(3), false).top,
            SkyMath.palette(at(3) + 24 * 60, false).top,
        )
        assertEquals(
            SkyMath.palette(at(3), false).top,
            SkyMath.palette(at(3) - 24 * 60, false).top,
        )
    }

    // --- Stars --------------------------------------------------------

    @Test
    fun `stars are fully out at midday and fully in at midnight`() {
        assertEquals(0f, SkyMath.starOpacity(at(13)), 0.001f)
        assertEquals(1f, SkyMath.starOpacity(at(0)), 0.001f)
    }

    @Test
    fun `stars hold flat through the night and day plateaus`() {
        assertEquals(1f, SkyMath.starOpacity(at(0)))
        assertEquals(1f, SkyMath.starOpacity(at(3)))
        assertEquals(1f, SkyMath.starOpacity(at(5)))
        assertEquals(0f, SkyMath.starOpacity(at(9)))
        assertEquals(0f, SkyMath.starOpacity(at(12)))
        assertEquals(0f, SkyMath.starOpacity(at(17)))
    }

    @Test
    fun `stars fade out monotonically at dawn and in monotonically at dusk`() {
        var previous = SkyMath.starOpacity(at(5))
        for (hour in 6..9) {
            val current = SkyMath.starOpacity(at(hour))
            assertTrue("stars brightened during dawn at $hour", current <= previous + 1e-9)
            previous = current
        }
        previous = SkyMath.starOpacity(at(17))
        for (hour in 18..21) {
            val current = SkyMath.starOpacity(at(hour))
            assertTrue("stars dimmed during dusk at $hour", current >= previous - 1e-9)
            previous = current
        }
    }

    @Test
    fun `star opacity never leaves 0 to 1`() {
        for (minute in 0 until 24 * 60) {
            val alpha = SkyMath.starOpacity(minute)
            assertTrue("star opacity $alpha out of range at minute $minute", alpha in 0f..1f)
        }
    }

    @Test
    fun `star opacity is continuous across the midnight wrap`() {
        val before = SkyMath.starOpacity(at(23, 59))
        val after = SkyMath.starOpacity(at(0, 0))
        assertTrue(kotlin.math.abs(before - after) < 0.01f)
    }

    @Test
    fun `star opacity wraps instead of crashing out of range`() {
        assertEquals(SkyMath.starOpacity(at(3)), SkyMath.starOpacity(at(3) + 24 * 60), 0.001f)
        assertEquals(SkyMath.starOpacity(at(3)), SkyMath.starOpacity(at(3) - 24 * 60), 0.001f)
    }

    // --- Sun ------------------------------------------------------------

    @Test
    fun `sun is fully out at midnight and fully in at midday`() {
        assertEquals(0f, SkyMath.sunOpacity(at(0)), 0.001f)
        assertEquals(1f, SkyMath.sunOpacity(at(13)), 0.001f)
    }

    @Test
    fun `sun opacity is the exact complement of star opacity at every minute`() {
        for (minute in 0 until 24 * 60) {
            assertEquals(
                1f - SkyMath.starOpacity(minute),
                SkyMath.sunOpacity(minute),
                0.0001f,
            )
        }
    }

    @Test
    fun `sun and stars are never both bright at once`() {
        for (minute in 0 until 24 * 60) {
            val total = SkyMath.sunOpacity(minute) + SkyMath.starOpacity(minute)
            assertEquals("sun+star should sum to 1 at minute $minute", 1f, total, 0.0001f)
        }
    }

    // --- Sun arc ----------------------------------------------------------

    @Test
    fun `sun sits low on the left at sunrise and low on the right at sunset`() {
        assertEquals(0.12f, SkyMath.sunXFraction(at(5)), 0.001f)
        assertEquals(0.58f, SkyMath.sunYFraction(at(5)), 0.001f)
        assertEquals(0.88f, SkyMath.sunXFraction(at(21, 30)), 0.001f)
        assertEquals(0.58f, SkyMath.sunYFraction(at(21, 30)), 0.001f)
    }

    @Test
    fun `sun is centred and highest at solar noon`() {
        val solarNoon = at(13, 15)
        assertEquals(0.5f, SkyMath.sunXFraction(solarNoon), 0.001f)
        assertEquals(0.12f, SkyMath.sunYFraction(solarNoon), 0.001f)
    }

    @Test
    fun `sun moves left to right monotonically across the day`() {
        var previous = SkyMath.sunXFraction(at(5))
        for (hour in 6..21) {
            val current = SkyMath.sunXFraction(at(hour))
            assertTrue("sun moved backwards at $hour", current >= previous - 1e-6f)
            previous = current
        }
    }

    @Test
    fun `sun's height is symmetric around solar noon`() {
        val solarNoon = at(13, 15)
        for (offsetMinutes in 0..400 step 20) {
            val before = SkyMath.sunYFraction(solarNoon - offsetMinutes)
            val after = SkyMath.sunYFraction(solarNoon + offsetMinutes)
            assertEquals(
                "height should mirror around solar noon at +/-$offsetMinutes minutes",
                before,
                after,
                0.001f,
            )
        }
    }

    @Test
    fun `sun position never leaves its designed range`() {
        for (minute in 0 until 24 * 60) {
            val x = SkyMath.sunXFraction(minute)
            val y = SkyMath.sunYFraction(minute)
            assertTrue("sun x $x out of range at minute $minute", x in 0.12f..0.88f)
            assertTrue("sun y $y out of range at minute $minute", y in 0.12f..0.58f)
        }
    }

    @Test
    fun `sun position wraps instead of crashing out of range`() {
        assertEquals(
            SkyMath.sunXFraction(at(13)),
            SkyMath.sunXFraction(at(13) + 24 * 60),
            0.001f,
        )
        assertEquals(
            SkyMath.sunYFraction(at(13)),
            SkyMath.sunYFraction(at(13) + 24 * 60),
            0.001f,
        )
    }

    // --- Colour maths sanity ---------------------------------------------

    @Test
    fun `contrast matches known WCAG values`() {
        val white = Rgb(255, 255, 255)
        val black = Rgb(0, 0, 0)
        assertEquals(21.0, SkyMath.contrast(white, black), 0.05)
        assertEquals(1.0, SkyMath.contrast(white, white), 0.001)
    }

    @Test
    fun `blend endpoints are exact`() {
        val a = Rgb(10, 20, 30)
        val b = Rgb(200, 210, 220)
        assertEquals(a, SkyMath.blend(a, b, 0.0))
        assertEquals(b, SkyMath.blend(a, b, 1.0))
    }
}
