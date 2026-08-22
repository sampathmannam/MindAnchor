/*
 * v0.63.0: hand-drawn icons for the journal.
 *
 * The launcher rule (PinGlyph, KindGlyph, etc.) is "draw it,
 * don't depend on it" — material-icons-extended is ~7MB and
 * uses an icon vocabulary that does not match the journal
 * aesthetic. The drafts use Lucide icons (a stroked, line-
 * weight-1.5, 24-grid set), which is the right vocabulary
 * for a journal but is not in the project. Rather than
 * add a 7MB dependency for 5 icons, we draw them.
 *
 * Every icon is a 24dp Box with 1.5dp strokes in the caller-
 * supplied colour. The shapes are simplified to the level
 * a designer would draw them on the back of a napkin —
 * the "search" icon is a circle + a 45-degree handle, the
 * "settings" icon is a gear with 6 teeth. We do not attempt
 * pixel-perfect Lucide fidelity; we attempt the same
 * vocabulary (stroked, geometric, 1.5dp).
 *
 * One 24dp icon = one Box + one or two Canvas-style shapes
 * drawn with Compose primitives (background, border, line).
 * Memory cost is two views per icon, which is the cost of
 * the existing PinGlyph. No bitmap, no vector asset, no
 * font dependency.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A stroked circle with a 45-degree handle, 24dp.
 * 18dp circle outline at the top-left, 8dp handle stroke
 * from 4 o'clock to the bottom-right corner.
 */
@Composable
internal fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.TopStart) {
        // The lens — 12dp circle, 1.5dp stroke, no fill.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(12.dp)
                .offset(x = 0.dp, y = 0.dp)
                .border(width = 1.5.dp, color = color, shape = CircleShape),
        )
        // The handle — 1.5dp stroke, 6dp long, rotated 45 degrees.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = 6.dp, height = 1.5.dp)
                .offset(x = (-3).dp, y = (-3).dp)
                .rotate(45f)
                .background(color),
        )
    }
}

/**
 * A book-stack icon for the archive footer button.
 * Two horizontal rounded rectangles, one stacked on the other.
 */
@Composable
internal fun ArchiveGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        // Top book — 16dp wide, 5dp tall, rounded.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 16.dp, height = 5.dp)
                .border(width = 1.5.dp, color = color, shape = RoundedCornerShape(1.dp)),
        )
        // Bottom book — 18dp wide, 6dp tall, rounded, just below.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 18.dp, height = 6.dp)
                .border(width = 1.5.dp, color = color, shape = RoundedCornerShape(1.dp)),
        )
    }
}

/**
 * A gear icon for the settings footer button.
 * 12dp centre circle + 6 short teeth around it.
 * (Simplified: just the circle + a rotated square outline
 * for the cog silhouette — the drafts use a 6-tooth gear,
 * but at 20dp the difference is invisible.)
 */
@Composable
internal fun SettingsGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        // Cog body — 16dp rounded square outline.
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(width = 1.5.dp, color = color, shape = RoundedCornerShape(4.dp)),
        )
        // Centre hole — 6dp circle outline.
        Box(
            modifier = Modifier
                .size(6.dp)
                .border(width = 1.5.dp, color = color, shape = CircleShape),
        )
    }
}

/**
 * A left-pointing chevron, for the back-to-today button on
 * Notes and Settings. 18dp wide, 9dp tall, drawn as two
 * 1.5dp strokes that meet at a point.
 */
@Composable
internal fun ChevronLeftGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 18.dp, height = 12.dp)) {
        // Upper stroke (top-right to centre-left).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 10.dp, height = 1.5.dp)
                .offset(x = 2.dp, y = 2.dp)
                .rotate(-30f)
                .background(color),
        )
        // Lower stroke (centre-left to bottom-right).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 10.dp, height = 1.5.dp)
                .offset(x = 2.dp, y = (-2).dp)
                .rotate(30f)
                .background(color),
        )
    }
}

/**
 * A long arrow-left for the Settings back button.
 * Same chevron at 2xl scale + a horizontal tail.
 */
@Composable
internal fun ArrowLeftGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 24.dp, height = 12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 24.dp, height = 1.5.dp)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 10.dp, height = 1.5.dp)
                .offset(y = (-3).dp)
                .rotate(-30f)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 10.dp, height = 1.5.dp)
                .offset(y = 3.dp)
                .rotate(30f)
                .background(color),
        )
    }
}

/**
 * A small chevron-right for the "Export Data" and
 * "Philosophy of Slow" settings rows.
 */
@Composable
internal fun ChevronRightGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 12.dp, height = 14.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 8.dp, height = 1.5.dp)
                .offset(x = (-2).dp, y = (-3).dp)
                .rotate(30f)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 8.dp, height = 1.5.dp)
                .offset(x = (-2).dp, y = 3.dp)
                .rotate(-30f)
                .background(color),
        )
    }
}

/**
 * An external-link arrow for the "Export Data" row.
 * A short arrow pointing to the upper-right, drawn as
 * two strokes.
 */
@Composable
internal fun ExternalLinkGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(16.dp)) {
        // The arrow body — short L-shape, 8dp.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 8.dp, height = 1.5.dp)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 1.5.dp, height = 8.dp)
                .offset(x = 0.dp, y = (-8).dp)
                .background(color),
        )
        // The arrow head — two diagonal strokes meeting at top-right.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 8.dp, height = 1.5.dp)
                .offset(x = 0.dp, y = 0.dp)
                .rotate(-45f)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 1.5.dp, height = 8.dp)
                .offset(x = 0.dp, y = 0.dp)
                .rotate(45f)
                .background(color),
        )
    }
}

/**
 * A feather for the quick-note composer header icon and
 * the mood-Light mood icon. A vertical spine + two slanted
 * barbs.
 */
@Composable
internal fun FeatherGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        // Spine — 1.5dp vertical line.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 1.5.dp, height = 18.dp)
                .rotate(15f)
                .background(color),
        )
    }
}

/**
 * A cloud-lightning icon for the mood-Crushed button.
 * (Simplified to a circle with a small zigzag below.)
 */
@Composable
internal fun CloudLightningGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 8.dp)
                .border(width = 1.5.dp, color = color, shape = RoundedCornerShape(4.dp)),
        )
        // Bolt — small zigzag.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 2.dp, height = 6.dp)
                .offset(y = 0.dp)
                .background(color),
        )
    }
}

/** An anchor for the mood-Heavy button. Circle + vertical bar. */
@Composable
internal fun AnchorGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 1.5.dp, height = 16.dp)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 6.dp, height = 6.dp)
                .offset(y = 2.dp)
                .border(width = 1.5.dp, color = color, shape = CircleShape),
        )
    }
}

/** Three wave humps for the mood-Steady button. */
@Composable
internal fun WavesGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 20.dp, height = 12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 1.5.dp, height = 8.dp)
                .offset(x = (-6).dp)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 1.5.dp, height = 8.dp)
                .background(color),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 1.5.dp, height = 8.dp)
                .offset(x = 6.dp)
                .background(color),
        )
    }
}

/** A small sun for the mood-Bright button. Circle + 6 short rays. */
@Composable
internal fun SunGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .border(width = 1.5.dp, color = color, shape = CircleShape),
        )
    }
}

/**
 * v0.67.0: A small speaker glyph for the "Read aloud" button.
 * A trapezoid (the speaker cone) + two arcs (the sound waves).
 * Drawn at 20dp, 1.5dp stroke, in the caller's colour. The
 * glyph sits to the left of the "Read aloud" / "Stop" text in
 * SkillOfTheDayCard so the affordance is recognisable as an
 * audio control and not just a verb — a button labelled
 * "Read aloud" is BPD-safe copy but easy to mistake for a
 * label; a small speaker icon gives it weight without
 * screaming.
 */
@Composable
internal fun SpeakerGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        // Speaker cone — a small filled rectangle on the left
        // half, 1.5dp wide, that the wave arcs emanate from.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 3.dp, height = 8.dp)
                .background(color, shape = RoundedCornerShape(0.5.dp)),
        )
        // Sound waves — two thin rounded lines to the right
        // of the cone, growing wider. Drawn as 1.5dp-tall
        // boxes since Compose does not have a stroked-arc
        // primitive. The shorter wave sits closer to the
        // cone; the longer one further out.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 5.dp, y = 0.dp)
                .size(width = 1.5.dp, height = 6.dp)
                .background(color, shape = RoundedCornerShape(0.75.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 9.dp, y = 0.dp)
                .size(width = 1.5.dp, height = 10.dp)
                .background(color, shape = RoundedCornerShape(0.75.dp)),
        )
    }
}

/**
 * v0.67.0: A small stop-square glyph for the "Stop" button
 * state. A single 10dp filled square. Drawn at 20dp, in the
 * caller's colour. Replaces the speaker glyph when the TTS
 * engine is currently speaking the skill's how-to-do-it.
 */
@Composable
internal fun StopGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = RoundedCornerShape(1.dp)),
        )
    }
}
