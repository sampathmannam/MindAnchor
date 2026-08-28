package org.mindanchor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import org.mindanchor.data.AppearancePrefs
import java.time.LocalDate

/** Text colours guaranteed readable against the sky behind them. */
data class SkyContent(
    val textPrimary: Color,
    val textSecondary: Color,
)

/**
 * The "slow sky" (docs/research/06): a time-of-day gradient with two soft
 * horizon shapes and an adaptive haze that keeps text readable.
 *
 * Nothing animates. The palette is recomputed once a minute, so the drift
 * from dawn to day is far below the threshold where motion draws the eye —
 * calm technology asks that ambient change stay in the periphery, and
 * attention research is unambiguous that abrupt or trackable motion does
 * the opposite.
 */
@Composable
fun CalmBackground(content: @Composable (SkyContent) -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val appearance = remember(context) { AppearancePrefs(context) }
    val sceneSetting by appearance.scene.collectAsState(initial = null)
    val now = rememberMinuteTick()
    val minuteOfDay = now.hour * 60 + now.minute
    val scene = sceneSetting?.let {
        NatureScene.resolve(it, LocalDate.now().toEpochDay())
    }

    val palette = SkyMath.palette(minuteOfDay, darkTheme)
    val top = palette.top.toColor()
    val mid = palette.mid.toColor()
    val bottom = palette.bottom.toColor()
    val primary = SkyMath.primaryTextFor(palette).toColor()
    val skyContent = SkyContent(
        textPrimary = primary,
        textSecondary = primary.copy(alpha = SkyMath.SECONDARY_ALPHA.toFloat()),
    )
    // The land is a silhouette, not a wash.
    //
    // These used to be drawn in the haze colour at 8% opacity, on the
    // reasoning that haze can only push the background away from the text
    // and so cannot break the contrast guarantee. The reasoning held; the
    // result did not. At night the sky is #1B263B and the haze is #0B101A
    // — near enough the same colour that 8% of it moved luminance by
    // 0.0018, which is nothing. The first real screenshot of this app
    // showed a flat gradient with no landscape in it at all.
    //
    // So the land now departs from the sky far enough to be seen, in the
    // direction that was always safe: away from the text. Under light text
    // it darkens, under dark text it lightens. Both raise contrast rather
    // than lower it — measured 12.40 to 15.09 at night, 9.24 to 10.25 by
    // day — so the guarantee is not merely preserved, it improves.
    val skyFloor = palette.bottom
    val landColor = if (palette.lightText) {
        Rgb(
            (skyFloor.r * LAND_DARKEN).toInt(),
            (skyFloor.g * LAND_DARKEN).toInt(),
            (skyFloor.b * LAND_DARKEN).toInt(),
        )
    } else {
        SkyMath.blend(skyFloor, Rgb(255, 255, 255), LAND_LIGHTEN)
    }.toColor()
    val hillTint = landColor

    val starOpacity = SkyMath.starOpacity(minuteOfDay)
    val sunOpacity = SkyMath.sunOpacity(minuteOfDay)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(top, mid, bottom)))

            // Stars, drawn onto the bare sky before anything in front of
            // it. starOpacity fades on the exact same dawn/dusk windows
            // the palette anchors above use, so they never fight the sky
            // for which one is "actually" night. TEXT_LIGHT is reused as
            // the tint rather than a new colour — it is already the
            // colour this file chooses for night, so the stars read as
            // part of the same palette instead of a decoration bolted on.
            if (starOpacity > 0f) {
                drawStars(opacity = starOpacity, tint = SkyMath.TEXT_LIGHT.toColor())
            }

            // The sun, drawn the same way as the stars above: a soft,
            // deterministic shape whose only movement is its opacity,
            // fading in and out on the exact same dawn/dusk windows
            // (sunOpacity is the complement of starOpacity), so the two
            // are never both on screen at once. A soft radial glow
            // rather than a hard disc, for the same "distance reads as
            // atmosphere, not as an object demanding attention" reason
            // the hills are filled shapes and not a spotlight.
            if (sunOpacity > 0f) {
                drawSun(opacity = sunOpacity, tint = SUN_TINT.toColor())
            }

            // Two hills, drawn as shapes rather than as glows.
            //
            // These were radial gradients centred below the bottom edge, at
            // 1.05 and 1.12 times the height. Only the outermost rim of each
            // circle was ever on screen, and a radial gradient is at its
            // faintest there — so raising the opacity did nothing, because
            // the opaque part was off the screen entirely. The second set of
            // real screenshots still showed a flat gradient.
            //
            // A horizon needs an edge. These are filled curves with a
            // definite crest, low enough to sit under everything the home
            // screen puts on top of them.
            val crestFar = size.height * 0.74f
            val far = Path().apply {
                moveTo(0f, crestFar + size.height * 0.05f)
                cubicTo(
                    size.width * 0.30f, crestFar - size.height * 0.03f,
                    size.width * 0.55f, crestFar + size.height * 0.04f,
                    size.width, crestFar - size.height * 0.01f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(far, color = hillTint.copy(alpha = LAND_FAR_ALPHA))

            val crestNear = size.height * 0.84f
            val near = Path().apply {
                moveTo(0f, crestNear + size.height * 0.04f)
                cubicTo(
                    size.width * 0.35f, crestNear - size.height * 0.05f,
                    size.width * 0.70f, crestNear + size.height * 0.02f,
                    size.width, crestNear + size.height * 0.01f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(near, color = hillTint.copy(alpha = LAND_NEAR_ALPHA))

            // The landscape, in the same atmospheric material as the hills.
            scene?.let { drawNature(it, hillTint) }

            // Adaptive haze last, so the contrast guarantee holds over
            // everything drawn above it.
            if (palette.hazeAlpha > 0f) {
                drawRect(color = palette.haze.toColor().copy(alpha = palette.hazeAlpha))
            }
        }
        content(skyContent)
    }
}

/** How far the land drops below a dark sky, and rises above a bright one. */
private const val LAND_DARKEN = 0.55
private const val LAND_LIGHTEN = 0.45

/** How many stars to scatter, and how far down the sky they reach. */
private const val STAR_COUNT = 48

/**
 * Stars stay in the upper sky, clear of the hill crests (0.74–0.84) and the
 * nature-scene ridges drawn on top of them — the same "distance reads as
 * atmosphere, not as an object" reasoning the hills themselves use.
 */
private const val STAR_MAX_HEIGHT_FRACTION = 0.6f

/** Jitter salts — arbitrary but distinct, so x, y, size and brightness don't correlate. */
private const val STAR_SALT_X = 11
private const val STAR_SALT_Y = 29
private const val STAR_SALT_SIZE = 47

private const val STAR_MIN_RADIUS_PX = 1.2f
private const val STAR_MAX_RADIUS_PX = 3.2f
private const val STAR_MIN_ALPHA = 0.25f
private const val STAR_MAX_ALPHA = 0.85f

/**
 * A scattered, deterministic field of small circles standing in for stars.
 *
 * Positions and sizes come from [NatureMath.jitter] — the same "hand-placed
 * but identical on every recomposition" trick the landscape ridges use — so
 * the sky does not visibly rearrange itself between one minute's redraw and
 * the next. Nothing here animates or twinkles; this file's whole premise is
 * that ambient change stays far below the threshold where motion draws the
 * eye, and a field of blinking stars would be exactly that threshold.
 */
private fun DrawScope.drawStars(opacity: Float, tint: Color) {
    for (i in 0 until STAR_COUNT) {
        val xFraction = (NatureMath.jitter(i, STAR_SALT_X) + 1.0) / 2.0
        val yFraction = (NatureMath.jitter(i, STAR_SALT_Y) + 1.0) / 2.0 * STAR_MAX_HEIGHT_FRACTION
        val sizeFraction = (NatureMath.jitter(i, STAR_SALT_SIZE) + 1.0) / 2.0
        val radius = STAR_MIN_RADIUS_PX + sizeFraction.toFloat() * (STAR_MAX_RADIUS_PX - STAR_MIN_RADIUS_PX)
        val alpha = STAR_MIN_ALPHA + sizeFraction.toFloat() * (STAR_MAX_ALPHA - STAR_MIN_ALPHA)
        drawCircle(
            color = tint.copy(alpha = (alpha * opacity).coerceIn(0f, 1f)),
            radius = radius,
            center = Offset(
                (xFraction * size.width).toFloat(),
                (yFraction * size.height).toFloat(),
            ),
        )
    }
}

/**
 * Where the sun sits: upper-left, clear of the centred clock and the
 * top-right corner buttons (letter / notes / history). Fixed, like the
 * stars — this file's premise is that nothing here animates, so the sun
 * does not arc across the sky, it only fades in and out with the day.
 */
private const val SUN_X_FRACTION = 0.22f
private const val SUN_Y_FRACTION = 0.15f

/** How far the glow reaches, as a fraction of the sky's width. */
private const val SUN_RADIUS_FRACTION = 0.28f

/** Peak alpha at the glow's centre; it falls to 0 at [SUN_RADIUS_FRACTION]. */
private const val SUN_CORE_ALPHA = 0.5f

/**
 * A soft warm gold — the same low-saturation register as the dawn/dusk
 * anchors above, rather than a bright, attention-grabbing yellow.
 */
private val SUN_TINT = Rgb(0xF2, 0xDC, 0xAD)

/**
 * A soft glow standing in for the sun, the same treatment [drawStars] gives
 * the night sky: a radial gradient rather than a hard-edged disc, so it
 * reads as daylight rather than as a shape sitting on top of the sky.
 */
private fun DrawScope.drawSun(opacity: Float, tint: Color) {
    val center = Offset(size.width * SUN_X_FRACTION, size.height * SUN_Y_FRACTION)
    val radius = size.width * SUN_RADIUS_FRACTION
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                tint.copy(alpha = (SUN_CORE_ALPHA * opacity).coerceIn(0f, 1f)),
                tint.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Opacity of the two horizon shapes. Well above the old 8% and 6%, which
 * were invisible; still low enough that the land reads as distance rather
 * than as an object demanding attention.
 */
private const val LAND_NEAR_ALPHA = 0.92f
private const val LAND_FAR_ALPHA = 0.70f

private fun Rgb.toColor(): Color = Color(r, g, b)
