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

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(top, mid, bottom)))

            // Two soft overlapping "hills": curvature-preference shapes at
            // whisper opacity, suggesting a horizon without depicting one.
            val hillCenterA = Offset(size.width * 0.15f, size.height * 1.05f)
            val hillRadiusA = size.width * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hillTint.copy(alpha = LAND_NEAR_ALPHA), Color.Transparent),
                    center = hillCenterA,
                    radius = hillRadiusA,
                ),
                center = hillCenterA,
                radius = hillRadiusA,
            )
            val hillCenterB = Offset(size.width * 0.95f, size.height * 1.12f)
            val hillRadiusB = size.width
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hillTint.copy(alpha = LAND_FAR_ALPHA), Color.Transparent),
                    center = hillCenterB,
                    radius = hillRadiusB,
                ),
                center = hillCenterB,
                radius = hillRadiusB,
            )

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
private const val LAND_NEAR_ALPHA = 0.75f
private const val LAND_FAR_ALPHA = 0.55f

private fun Rgb.toColor(): Color = Color(r, g, b)
