package org.mindanchor.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * v0.56.0: a Material 3 [ColorScheme] that follows the
 * launcher's "slow sky" instead of the system theme.
 *
 * The default M3 color scheme is hard-coded to the
 * system's light or dark mode. The launcher's theme
 * follows the wall clock (light at noon, dark at
 * midnight, see [CalmBackground] / [SkyMath]), so the
 * M3 default is wrong for every surface in the app.
 * Before v0.56.0, the Settings surface used the M3
 * default: a hard-coded white card on a system dark or
 * light theme, neither of which matches the slow-sky
 * gradient the rest of the app uses. The result was a
 * hospital-form-on-a-beach.
 *
 * [skyAwareColorScheme] returns a [ColorScheme] whose
 * surface, onSurface, primary, error, and outline are
 * all derived from the [SkyContent] and the
 * clock-derived [darkTheme] flag the rest of the
 * launcher already uses. The sky is drawn through
 * [CalmBackground] (the M3 `background` and
 * `onBackground` are kept transparent / sky-driven so
 * the gradient never gets covered up by a card).
 *
 * The card surface is a translucent layer that sits
 * one step further along the haze direction than the
 * sky — slightly lighter than the day sky bottom
 * (#DCE0DF → #F2EFE8) and slightly lighter than the
 * night sky bottom (#3B5278 → #4A6088) — so a card
 * reads as "a thing on the sky" rather than as "a
 * different app's window."
 *
 * The accent colours (primary = teal-700, secondary =
 * indigo-800, error = deep rust) are the same tokens
 * the home / notes / letter surfaces already use, so
 * a SegmentedButton, a Switch, a Checkbox, or an
 * AlertDialog inside Settings picks the same colour
 * language as the same control on the home card.
 */
@Composable
fun skyAwareColorScheme(sky: SkyContent, darkTheme: Boolean): ColorScheme {
    val cardSurface = if (darkTheme) {
        Color(0xFF4A6088) // one step lighter than night sky bottom (#3B5278)
    } else {
        Color(0xFFF2EFE8) // one step lighter than day sky bottom (#DCE0DF)
    }
    val onCard = sky.textPrimary
    val cardContainer = if (darkTheme) {
        Color(0xFF243A65) // deeper than cardSurface, for chips / list items
    } else {
        Color(0xFFE6E2D8) // deeper than cardSurface, for chips / list items
    }
    val onCardContainer = if (darkTheme) {
        Color(0xFFB8C5DC) // soft sky-tinted foreground for the container
    } else {
        Color(0xFF445566) // deep navy foreground for the container
    }
    val outline = sky.textPrimary.copy(alpha = 0.20f)
    val outlineVariant = sky.textPrimary.copy(alpha = 0.10f)
    val errorColor = Color(0xFF8B4A4A) // deep rust (matches NotesSwipeDeleteFg)

    return if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF7DD3C0),            // teal-300 (lighter for dark)
            onPrimary = Color(0xFF003733),
            primaryContainer = Color(0xFF115E59),  // teal-800
            onPrimaryContainer = Color(0xFFB2DFD8), // teal-200
            secondary = Color(0xFFB4B0F8),          // indigo-300
            onSecondary = Color(0xFF1A1670),
            tertiary = Color(0xFFD8B49E),           // warm dawn accent
            onTertiary = Color(0xFF3A2A1F),
            background = Color.Transparent,
            onBackground = sky.textPrimary,
            surface = cardSurface,
            onSurface = onCard,
            surfaceVariant = cardContainer,
            onSurfaceVariant = onCardContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            error = errorColor,
            onError = Color(0xFFEDE8DE),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0F766E),            // teal-700
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB2DFD8),  // teal-200
            onPrimaryContainer = Color(0xFF115E59), // teal-800
            secondary = Color(0xFF3730A3),          // indigo-800
            onSecondary = Color.White,
            tertiary = Color(0xFF8B4A2A),           // deep terracotta
            onTertiary = Color.White,
            background = Color.Transparent,
            onBackground = sky.textPrimary,
            surface = cardSurface,
            onSurface = onCard,
            surfaceVariant = cardContainer,
            onSurfaceVariant = onCardContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            error = errorColor,
            onError = Color.White,
        )
    }
}
