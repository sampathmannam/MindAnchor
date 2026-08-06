package org.mindanchor.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Calm design language (CONCEPT.md §3.8): warm, low-arousal, muted palette.
 * No red accents outside genuine emergencies. Dynamic color is used when the
 * wallpaper-derived palette is available so the launcher feels native.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B6B5D),
    onPrimary = Color(0xFFF6F4EE),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF3A3A36),
    surface = Color(0xFFF6F4EE),
    onSurface = Color(0xFF3A3A36),
    surfaceVariant = Color(0xFFE8E5DC),
    onSurfaceVariant = Color(0xFF5C5B54),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9B8AA),
    onPrimary = Color(0xFF20241F),
    background = Color(0xFF191A17),
    onBackground = Color(0xFFD9D7CE),
    surface = Color(0xFF191A17),
    onSurface = Color(0xFFD9D7CE),
    surfaceVariant = Color(0xFF2A2B26),
    onSurfaceVariant = Color(0xFFA5A399),
)

@Composable
fun MindAnchorTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
