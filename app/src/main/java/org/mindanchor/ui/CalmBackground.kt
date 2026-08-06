package org.mindanchor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.mindanchor.data.AppearancePrefs
import java.time.LocalDate
import java.time.LocalTime

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
    var minuteOfDay by remember {
        mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute })
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
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
    // The hills are made of the same atmospheric material as the haze, so
    // they can only ever push the background further from the text colour —
    // never closer. That keeps SkyMath's contrast guarantee sound even
    // though it reasons about the bare gradient.
    val hillTint = palette.haze.toColor()

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(top, mid, bottom)))

            // Two soft overlapping "hills": curvature-preference shapes at
            // whisper opacity, suggesting a horizon without depicting one.
            val hillCenterA = Offset(size.width * 0.15f, size.height * 1.05f)
            val hillRadiusA = size.width * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hillTint.copy(alpha = 0.08f), Color.Transparent),
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
                    colors = listOf(hillTint.copy(alpha = 0.06f), Color.Transparent),
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

private fun Rgb.toColor(): Color = Color(r, g, b)
