package org.mindanchor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Calm design language (docs/research/06).
 *
 * Deliberately NOT using Material You dynamic color: wallpaper-derived
 * palettes are frequently high-chroma, and arousal tracks saturation far
 * more than hue (Valdez & Mehrabian 1994). We keep every colour at low
 * saturation and mid-to-high lightness, and never use pure black or pure
 * white. Shapes are rounded throughout (curvature preference — Bar & Neta
 * 2006; Chuquichambi et al. 2022 meta-analysis).
 */
// Internal rather than private so ContrastTest can hold every pair to
// WCAG AA arithmetic — measured, not assumed; three light pairs were
// failing before that test existed.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF57726A), // muted sage
    onPrimary = Color(0xFFF4F1E9),
    primaryContainer = Color(0xFFD9E3DC),
    onPrimaryContainer = Color(0xFF25332E),
    secondary = Color(0xFF7A8B9E), // dusty blue
    onSecondary = Color(0xFFF4F1E9),
    background = Color(0xFFF4F1E9), // warm off-white, not #FFF
    onBackground = Color(0xFF2E3B39),
    surface = Color(0xFFF4F1E9),
    onSurface = Color(0xFF2E3B39),
    surfaceVariant = Color(0xFFE4E0D6),
    onSurfaceVariant = Color(0xFF576560),
    outline = Color(0xFFA9B2AC),
    error = Color(0xFF86655F), // muted, never alarm-red
    onError = Color(0xFFF4F1E9),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C5BC),
    onPrimary = Color(0xFF1C2523),
    primaryContainer = Color(0xFF33433E),
    onPrimaryContainer = Color(0xFFD9E3DC),
    secondary = Color(0xFF9BAABA),
    onSecondary = Color(0xFF1C2523),
    background = Color(0xFF14191B), // near-black, never #000
    onBackground = Color(0xFFE3DFD5),
    surface = Color(0xFF14191B),
    onSurface = Color(0xFFE3DFD5),
    surfaceVariant = Color(0xFF262D2F),
    onSurfaceVariant = Color(0xFFA8ADA6),
    outline = Color(0xFF5A625F),
    error = Color(0xFFC79E95),
    onError = Color(0xFF1C2523),
)

/** No sharp corners anywhere. */
private val CalmShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Light weights and generous line height. Reading ease is linked to
 * positive affect via processing fluency (Reber et al. 2004); we cannot
 * bundle a downloadable rounded face without a proprietary dependency, so
 * we soften the system face with weight, spacing and leading instead.
 */
private val CalmTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
        ),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Light),
        headlineSmall = headlineSmall.copy(
            fontWeight = FontWeight.Normal,
            lineHeight = 36.sp,
        ),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Normal),
        titleMedium = titleMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.4.sp,
        ),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 24.sp),
        bodySmall = bodySmall.copy(lineHeight = 20.sp),
        labelMedium = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.6.sp,
        ),
    )
}

/**
 * The whole app follows the sky, not the system switch.
 *
 * The home screen has always been time-driven: at half past nine it draws
 * a night sky whatever the phone's light/dark setting says. Every other
 * screen followed the system instead, so on a phone in light mode, opening
 * support at night meant going from a near-black sky to a near-white sheet
 * in one tap. The first screenshots showed exactly that — a dark home and
 * a pale support screen, a jolt on the one journey this app most needs to
 * feel gentle.
 *
 * So the sky decides. [SkyMath] already works out whether this moment
 * wants light text on dark ground or the reverse, purely to keep text
 * readable; that same answer now chooses the colour scheme everywhere. The
 * system setting still feeds in, because it shifts the palette itself, but
 * it no longer contradicts what the sky is doing.
 */
/**
 * The same theme with the palette chosen by the caller instead of the
 * sky.
 *
 * Exists for the screenshot tests alone. The real theme follows the
 * clock, which means a CI emulator photographs whichever palette its
 * clock happens to land on — and the three affordance faults this
 * project has shipped were all found in screenshots, so the palette not
 * pictured is precisely where the next one hides. Same shapes, same
 * typography; only the choice of scheme is forced.
 */
@Composable
internal fun MindAnchorThemeForced(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = CalmShapes,
        typography = CalmTypography,
        content = content,
    )
}

@Composable
fun MindAnchorTheme(content: @Composable () -> Unit) {
    val now = rememberMinuteTick()
    val palette = SkyMath.palette(now.hour * 60 + now.minute, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (palette.lightText) DarkColors else LightColors,
        shapes = CalmShapes,
        typography = CalmTypography,
        content = content,
    )
}
