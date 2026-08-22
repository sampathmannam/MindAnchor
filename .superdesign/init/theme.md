# MindAnchor Design System (theme.md)

> **Status:** v0.62.7 (2026-08-20) | **Stack:** Kotlin + Jetpack Compose + Material 3
> **Tagline:** "A quiet mental-health-first launcher for the moments before you pick up your phone."
> **Voice:** Slow, warm, research-backed. No streaks, no urgency, no social proof. Encourages grounded, embodied action.

---

## Part 1 — Compact token summary (the budget-friendly digest)

### Brand voice & visual identity
- **App name:** MindAnchor
- **Tamil name:** காவலன் (Kāvaḷaṉ) = "guardian"
- **App icon (adaptive foreground, 432×432):** Indigo `#1F2A44` shield silhouette + cream `#F2EBDC` background + a subtle "W" / shieldmark cutout
- **Launcher:** MindAnchor is the user's home screen. Replaces the stock Android launcher.

### The "slow sky" (signature background)
The launcher background is a **time-of-day-driven sky gradient** (see `SkyMath.kt`).
NOT a hardcoded light/dark mode. Walls, cards, and typography all derive from this sky.

| Time of day | Day sky (top → bottom) | Night sky (top → bottom) |
|-------------|------------------------|---------------------------|
| 06:00–11:59 | dawn: `#F5D5BD` → `#E6C4A7` | (still day) |
| 12:00–16:59 | day:  `#9DBCC9` → `#DCE0DF` | (still day) |
| 17:00–20:59 | dusk: `#8AA1C0` → `#3B5278` | (dusk → night crossover) |
| 21:00–05:59 | (night) | night: `#1B2845` → `#0F1830` |

The `darkTheme` boolean is computed from clock + sunset/sunrise hours. Cards and text are derived from the same sky.

### Color tokens (file-private to HomeScreen.kt)
| Token | Hex | Use |
|-------|-----|-----|
| `KindTealBg` | `#B2DFD8` (teal-200) | Soft fill on Task picker / row chip (selected) |
| `KindTealFg` | `#115E59` (teal-800) | Selected label, high-contrast on teal fill |
| `KindIndigoBg` | `#C7D2FE` (indigo-200) | Soft fill on Reminder picker / row chip (selected) |
| `KindIndigoFg` | `#3730A3` (indigo-800) | Selected label, high-contrast on indigo fill |
| `ActionAccentFg` | `#0F766E` (teal-700) | Navigation accents ("!" in bang hint, chevrons, swipe affordances) |
| `ActionAccentBg` | `#99F6E4` (teal-200) | Soft fill for accent backgrounds |
| `sky.textPrimary` | varies | Default text (high contrast on sky) |
| `sky.textSecondary` | varies | Muted text (0.945 alpha) |
| `NotesSwipeDeleteBg` | `#FCA5A5` (red-300) | Swipe-to-delete background hint |

### M3 ColorScheme (skyAwareColorScheme in SkyColorScheme.kt)
- `primary` = teal-700 / teal-300 (dark)
- `primaryContainer` = teal-800 / teal-200
- `secondary` = indigo-300 / indigo-300
- `tertiary` = warm dawn accent `#D8B49E` (low-priority; rare)
- `error` = deep rust `#8B4A4A` (NotesSwipeDeleteFg)
- `background` = **transparent** (sky shows through)
- `surface` / `surfaceContainer` = translucent layers derived from sky bottom

### Typography
- **Font:** system default (Roboto on Android, no custom fonts)
- **Type scale (Material 3):** `displayLarge/Medium/Small`, `headlineLarge/Medium/Small`, `titleLarge/Medium/Small`, `bodyLarge/Medium/Small`, `labelLarge/Medium/Small`
- **Two-tier visual hierarchy (v0.53.0):**
  - **Layer 1 (background):** clock + mood
  - **Layer 2 (LayerSecondary, 8% white tint, 4dp elev):** mood + notes / letter / reminder content
  - **Layer 3 (LayerTertiary, 12% white tint, 8dp elev):** input + picker + save (where the user touches)
- **`SECONDARY_ALPHA = 0.945`** (SkyMath) — secondary text sits just below primary on the WCAG ladder.

### Spacing & shape
- **8dp baseline grid** (Material 3 default)
- **Corners:** small = 4dp, medium = 8dp, large = 16dp
- **Touch targets:** minimum 48dp (Material 3 default)
- **Haptics:** `TextHandleMove` (subtle) for taps; `LongPress` for swipe / completion

### Iconography
- **No material-icons-extended** imports (project rule). Custom-drawn icons via `androidx.compose.foundation.Canvas` + `drawLine()` / `drawCircle()`.
- Examples in HomeScreen.kt: chevron (line 4597), checkmark (v0.62.6 added for "Saved Xs ago").
- Bang hint uses a literal "!" character styled with `ActionAccentFg`.

### Motion
- **No third-party animation libs** (no Lottie, no Accompanist).
- `rememberInfiniteTransition(label = "flash-pulse")` for the soft pulse on the gentle prompt.
- `androidx.compose.animation.core.Animatable` for one-shot transitions.
- All animations are short (≤ 500ms), no parallax, no spring-back.

---

## Part 2 — Raw source dumps

> Below is the full source for the key design-token files. Trim from here on the
> `superdesign` design command will use the compact Part 1 first.

### `app/src/main/java/org/mindanchor/ui/SkyColorScheme.kt`

```kotlin
package org.mindanchor.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
            // ... (truncated for init file budget; full file in repo)
            error = errorColor,
            onError = Color(0xFF1F1717),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0F766E),            // teal-700
            onPrimary = Color(0xFFE6FFFA),
            primaryContainer = Color(0xFFB2DFD8),  // teal-200
            onPrimaryContainer = Color(0xFF115E59), // teal-800
            secondary = Color(0xFF3730A3),          // indigo-800
            onSecondary = Color(0xFFE0E7FF),
            tertiary = Color(0xFF8B6F47),          // warm tan (dawn accent)
            onTertiary = Color(0xFFFFF7E6),
            background = Color.Transparent,
            // ... (truncated for init file budget; full file in repo)
            error = errorColor,
            onError = Color(0xFFFFEDED),
        )
    }
}
```

### `app/src/main/java/org/mindanchor/ui/SkyMath.kt` (key clock → theme bits)

```kotlin
// Time-of-day → darkTheme boolean
// Also drives SECONDARY_ALPHA = 0.945 (v0.55.0 WCAG-AA calibration)
package org.mindanchor.ui

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

// v0.55.0: SECONDARY_ALPHA tuned for WCAG-AA at every minute of every theme.
const val SECONDARY_ALPHA = 0.945f

data class SkyContent(
    val timeOfDay: Float,         // 0.0 = midnight, 0.5 = noon
    val textPrimary: Color,       // default text on sky
    val textSecondary: Color,     // muted text (alpha 0.945)
    val skyTop: Color,
    val skyBottom: Color,
    val sunGlow: Color,
    val darkTheme: Boolean,
)
```

> **For full source of `SkyColorScheme.kt`, `SkyMath.kt`, `Theme.kt`, `Spacing.kt`,
> `CalmBackground.kt`, and the nature scene composables — see the matching files
> in `app/src/main/java/org/mindanchor/ui/`. The above is the design-DNA digest
> the superdesign tool will use first.
