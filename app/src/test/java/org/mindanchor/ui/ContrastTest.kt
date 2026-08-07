package org.mindanchor.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text-on-ground pair in the palette, held to WCAG AA arithmetic.
 *
 * ## Why this is a unit test and not a design review
 *
 * The palette is a set of constants and relative luminance is a formula,
 * so "is this text readable" is checkable in CI forever rather than
 * re-judged by eye at every change. The day this test was written it
 * failed three times: light-mode primary sat at 3.98:1 against the
 * background — after primary had just been made the colour of every
 * tappable title — light onSurfaceVariant inside a surfaceVariant
 * container at 4.25:1, and light error at 4.27:1. All three were darkened
 * by the minimum that clears 4.5:1 with margin, and this keeps them
 * there.
 *
 * The floor is AA for normal text (4.5:1), not large-text AA (3:1),
 * because these roles are used at body and label sizes throughout. For an
 * app whose stated audience includes people in distress, readable is not
 * a nice-to-have.
 */
class ContrastTest {

    /** WCAG 2.x relative luminance of an sRGB colour. */
    private fun luminance(color: Color): Double {
        fun channel(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(color.red.toDouble()) +
            0.7152 * channel(color.green.toDouble()) +
            0.0722 * channel(color.blue.toDouble())
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        val brighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (brighter + 0.05) / (darker + 0.05)
    }

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        val r = ratio(foreground, background)
        assertTrue(
            "$name is %.2f:1, below the 4.5:1 AA floor for normal text".format(r),
            r >= 4.5,
        )
    }

    @Test
    fun `every light pair clears AA for normal text`() {
        val c = LightColors
        assertReadable("onBackground/background", c.onBackground, c.background)
        assertReadable("onSurface/surface", c.onSurface, c.surface)
        assertReadable("onSurfaceVariant/background", c.onSurfaceVariant, c.background)
        assertReadable("onSurfaceVariant/surfaceVariant", c.onSurfaceVariant, c.surfaceVariant)
        assertReadable("primary/background", c.primary, c.background)
        assertReadable("onPrimary/primary", c.onPrimary, c.primary)
        assertReadable("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer)
        assertReadable("error/background", c.error, c.background)
    }

    @Test
    fun `every dark pair clears AA for normal text`() {
        val c = DarkColors
        assertReadable("onBackground/background", c.onBackground, c.background)
        assertReadable("onSurface/surface", c.onSurface, c.surface)
        assertReadable("onSurfaceVariant/background", c.onSurfaceVariant, c.background)
        assertReadable("onSurfaceVariant/surfaceVariant", c.onSurfaceVariant, c.surfaceVariant)
        assertReadable("primary/background", c.primary, c.background)
        assertReadable("onPrimary/primary", c.onPrimary, c.primary)
        assertReadable("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer)
        assertReadable("error/background", c.error, c.background)
    }
}
