package org.mindanchor.ui

import androidx.compose.ui.unit.dp

/**
 * The vertical rhythm every screen shares.
 *
 * ## Why this exists
 *
 * Counted across the app before this file was written: fifteen distinct
 * `dp` values in a hundred and fifty padding calls. Six of them —
 * 4, 8, 12, 16, 24, 32 — accounted for nine in ten uses, and the rest
 * (2, 10, 20, 28, 36, 40) were each used once or twice. Those one-offs
 * are not decisions, they are drift: somebody needed a gap slightly
 * larger than the last one and typed a number.
 *
 * The cost is not ugliness so much as incoherence. A screen whose gaps
 * are 16, 20 and 24 reads as three unrelated relationships rather than
 * one hierarchy, and the eye has to work out the grouping that the
 * spacing should simply have told it.
 *
 * ## The scale
 *
 * Steps roughly double. That is deliberate: adjacent steps in a doubling
 * scale are obviously different, so a gap always says something, and
 * there is never a choice between two values that look nearly the same.
 *
 * These are not a style guide to be applied retroactively across every
 * screen in one sweep — that would be a large diff with no visible
 * benefit and real risk. New and reworked screens use them; the rest
 * converge as they are touched.
 */
object Spacing {

    /** Between a label and the thing it labels. */
    val Hair = 4.dp

    /** Between lines of one thought. */
    val Tight = 8.dp

    /** Between a heading and its body. */
    val Snug = 12.dp

    /** Between related blocks. */
    val Comfortable = 16.dp

    /** Between one idea and the next. */
    val Loose = 24.dp

    /** Between sections that are genuinely separate concerns. */
    val Section = 32.dp

    /** The margin from the edge of the screen. */
    val Edge = 24.dp

    /**
     * The smallest a tappable thing may be.
     *
     * Android's documented minimum, and the floor here rather than the
     * aspiration. A person with a tremor, or in distress, or holding a
     * phone one-handed on a bus is the person this app is for, and a
     * target the width of a word is one they will miss.
     */
    val Touch = 48.dp
}
