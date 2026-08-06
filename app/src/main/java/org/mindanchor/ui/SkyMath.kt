package org.mindanchor.ui

import kotlin.math.pow

/**
 * Pure "slow sky" palette math (docs/research/06).
 *
 * A time-of-day vertical gradient interpolated continuously between
 * low-saturation anchors — night, dawn, day, dusk. Saturation stays low
 * because arousal tracks saturation far more than hue (Valdez & Mehrabian
 * 1994), and the only movement is the clock itself.
 *
 * Because a sweeping gradient passes through mid luminance — where neither
 * light nor dark text is readable — the palette also carries an *adaptive
 * haze*: a translucent warm-white or deep-blue veil whose opacity is the
 * smallest value that pulls the text-bearing bands back into a readable
 * range. It is invisible at night and midday, and reads as atmosphere
 * during dawn and dusk, when it is briefly needed.
 */
data class Rgb(val r: Int, val g: Int, val b: Int)

data class SkyPalette(
    val top: Rgb,
    val mid: Rgb,
    val bottom: Rgb,
    /** Veil drawn over the whole gradient to guarantee text contrast. */
    val haze: Rgb,
    val hazeAlpha: Float,
    /** True when text should be light-on-dark. */
    val lightText: Boolean,
)

object SkyMath {

    // --- Text colours the contrast guarantee is built around. -------------

    val TEXT_LIGHT = Rgb(0xED, 0xE8, 0xDE)
    val TEXT_DARK = Rgb(0x2E, 0x3B, 0x39)

    /**
     * Secondary text is the same colour, only slightly dimmed: supporting
     * text is de-emphasised by size and weight instead of by contrast, so
     * that nothing on the screen is hard to read.
     */
    const val SECONDARY_ALPHA = 0.95

    /** WCAG AA for normal text. */
    const val MIN_CONTRAST = 4.5

    /**
     * Luminance windows in which both primary and dimmed secondary text
     * clear [MIN_CONTRAST]. Solved against the text colours above, then
     * rounded inwards for margin; SkyMathTest sweeps every minute of the
     * day in both themes and fails if a palette edit ever breaks them.
     * (Measured worst case with these values: 4.56 : 1.)
     */
    private const val LIGHT_TEXT_MAX_LUMINANCE = 0.125
    private const val DARK_TEXT_MIN_LUMINANCE = 0.395

    private val HAZE_DARK = Rgb(0x0B, 0x10, 0x1A)
    private val HAZE_LIGHT = Rgb(0xF6, 0xF3, 0xEB)

    const val MAX_HAZE_ALPHA = 0.5

    // --- Palette anchors --------------------------------------------------

    private data class Anchor(val minutes: Int, val top: Rgb, val mid: Rgb, val bottom: Rgb)

    private val NIGHT_TOP = Rgb(0x0D, 0x13, 0x21)
    private val NIGHT_MID = Rgb(0x14, 0x1C, 0x2E)
    private val NIGHT_BOTTOM = Rgb(0x1B, 0x26, 0x3B)

    private val DAY_TOP = Rgb(0xA8, 0xC4, 0xC4)
    private val DAY_MID = Rgb(0xC3, 0xD6, 0xD0)
    private val DAY_BOTTOM = Rgb(0xDD, 0xE8, 0xDB)

    /** Night and day are plateaus; dawn and dusk are the transitions. */
    private val ANCHORS = listOf(
        Anchor(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM),
        Anchor(5 * 60, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM),
        // dawn: slate → mauve → pale peach
        Anchor(6 * 60 + 30, Rgb(0x2E, 0x34, 0x40), Rgb(0x8B, 0x7D, 0x8B), Rgb(0xD8, 0xB4, 0xA0)),
        Anchor(9 * 60, DAY_TOP, DAY_MID, DAY_BOTTOM),
        Anchor(17 * 60, DAY_TOP, DAY_MID, DAY_BOTTOM),
        // dusk: deep slate → lavender → muted terracotta
        Anchor(19 * 60, Rgb(0x3B, 0x42, 0x52), Rgb(0x7A, 0x6A, 0x8A), Rgb(0xC8, 0x9F, 0x8C)),
        Anchor(21 * 60 + 30, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM),
        Anchor(23 * 60, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM),
    )

    private const val DAY_MINUTES = 24 * 60
    private const val DARK_THEME_SCALE = 0.55

    // --- Public API -------------------------------------------------------

    fun palette(minutesOfDay: Int, darkTheme: Boolean): SkyPalette {
        val minutes = ((minutesOfDay % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES
        val (before, after, fraction) = neighbors(minutes)
        var top = lerp(before.top, after.top, fraction)
        var mid = lerp(before.mid, after.mid, fraction)
        var bottom = lerp(before.bottom, after.bottom, fraction)
        if (darkTheme) {
            top = scale(top, DARK_THEME_SCALE)
            mid = scale(mid, DARK_THEME_SCALE)
            bottom = scale(bottom, DARK_THEME_SCALE)
        }

        // Text sits over the middle and bottom bands; the veil is sized for
        // whichever of those is hardest to read against.
        val textBands = listOf(mid, bottom)
        val darkVeil = hazeFor(textBands, HAZE_DARK) { luminance(it) <= LIGHT_TEXT_MAX_LUMINANCE }
        val lightVeil = hazeFor(textBands, HAZE_LIGHT) { luminance(it) >= DARK_TEXT_MIN_LUMINANCE }

        val useLightText = when {
            darkVeil == null -> false
            lightVeil == null -> true
            else -> darkVeil <= lightVeil
        }
        val haze = if (useLightText) HAZE_DARK else HAZE_LIGHT
        val alpha = (if (useLightText) darkVeil else lightVeil) ?: MAX_HAZE_ALPHA

        return SkyPalette(
            top = top,
            mid = mid,
            bottom = bottom,
            haze = haze,
            hazeAlpha = alpha.toFloat(),
            lightText = useLightText,
        )
    }

    /** Colour to draw primary text in for this palette. */
    fun primaryTextFor(palette: SkyPalette): Rgb =
        if (palette.lightText) TEXT_LIGHT else TEXT_DARK

    /** The composited background a caller's text actually sits on. */
    fun withHaze(background: Rgb, palette: SkyPalette): Rgb =
        blend(background, palette.haze, palette.hazeAlpha.toDouble())

    // --- Colour maths -----------------------------------------------------

    /** WCAG 2.x relative luminance (gamma-corrected), 0..1. */
    fun luminance(color: Rgb): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b)
    }

    /** WCAG contrast ratio between two colours, 1..21. */
    fun contrast(a: Rgb, b: Rgb): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Source-over composite of [over] at [alpha] onto [base]. */
    fun blend(base: Rgb, over: Rgb, alpha: Double): Rgb {
        val a = alpha.coerceIn(0.0, 1.0)
        fun mix(b: Int, o: Int) = (b + (o - b) * a).toInt().coerceIn(0, 255)
        return Rgb(mix(base.r, over.r), mix(base.g, over.g), mix(base.b, over.b))
    }

    /**
     * Smallest veil opacity (to 0.01) that makes every band satisfy
     * [readable], or null if even [MAX_HAZE_ALPHA] cannot.
     */
    private fun hazeFor(
        bands: List<Rgb>,
        veil: Rgb,
        readable: (Rgb) -> Boolean,
    ): Double? {
        var alpha = 0.0
        while (alpha <= MAX_HAZE_ALPHA + 1e-9) {
            if (bands.all { readable(blend(it, veil, alpha)) }) return alpha
            alpha += 0.01
        }
        return null
    }

    private fun neighbors(minutes: Int): Triple<Anchor, Anchor, Double> {
        val wrapped = ANCHORS.first().let {
            Anchor(it.minutes + DAY_MINUTES, it.top, it.mid, it.bottom)
        }
        val extended = ANCHORS + wrapped
        for (i in 0 until extended.size - 1) {
            val a = extended[i]
            val b = extended[i + 1]
            if (minutes >= a.minutes && minutes < b.minutes) {
                val span = (b.minutes - a.minutes).toDouble()
                return Triple(a, b, if (span == 0.0) 0.0 else (minutes - a.minutes) / span)
            }
        }
        return Triple(extended.first(), extended.first(), 0.0)
    }

    private fun lerp(a: Rgb, b: Rgb, t: Double): Rgb = Rgb(
        (a.r + (b.r - a.r) * t).toInt().coerceIn(0, 255),
        (a.g + (b.g - a.g) * t).toInt().coerceIn(0, 255),
        (a.b + (b.b - a.b) * t).toInt().coerceIn(0, 255),
    )

    private fun scale(color: Rgb, factor: Double): Rgb = Rgb(
        (color.r * factor).toInt().coerceIn(0, 255),
        (color.g * factor).toInt().coerceIn(0, 255),
        (color.b * factor).toInt().coerceIn(0, 255),
    )
}
