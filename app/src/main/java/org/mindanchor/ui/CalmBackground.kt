package org.mindanchor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    // v0.53.0 (Red Dot review fix, Issue 1):
    // the launcher's dark/light mode is now
    // a function of the wall clock, not the
    // system theme. A user who opens the
    // launcher at 7am in a dark system theme
    // would previously have seen the deep
    // indigo "night" sky; v0.53.0 shows the
    // dawn/cream sky because 7am IS dawn.
    // The launcher follows the user's day,
    // not the device's appearance setting.
    //
    // The previous v0.50.0 behaviour (use
    // [isSystemInDarkTheme]) was the right
    // default for a 2018 Android app. For a
    // 2026 mental-health-first launcher, the
    // wall clock is the right signal — a
    // user in a dark room at noon should see
    // noon colours, not 3am colours.
    //
    // The threshold is the same as
    // [SkyMath]'s "evening" bucket: 18:00.
    // Before 18:00 the sky is the light
    // palette; after, the dark palette. The
    // [SkyMath.palette] function takes a
    // [darkTheme: Boolean] parameter and
    // blends accordingly; the v0.53.0
    // change is in *which* signal feeds the
    // boolean, not in the palette math
    // itself.
    val now = rememberMinuteTick()
    val darkTheme = now.hour >= 18 || now.hour < 6
    val context = LocalContext.current
    val appearance = remember(context) { AppearancePrefs(context) }
    // v0.25.17 BUG-004: lifecycle-aware collect. The
    // background-scene setting is read on every
    // composition; pre-v0.25.17 a STOPPED
    // CalmBackground would keep recomposing the sky
    // on every appearance emission.
    val sceneSetting by appearance.scene.collectAsStateWithLifecycle(initialValue = null)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(top, mid, bottom)))

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

/**
 * Opacity of the two horizon shapes. Well above the old 8% and 6%, which
 * were invisible; still low enough that the land reads as distance rather
 * than as an object demanding attention.
 */
private const val LAND_NEAR_ALPHA = 0.92f
private const val LAND_FAR_ALPHA = 0.70f

private fun Rgb.toColor(): Color = Color(r, g, b)
