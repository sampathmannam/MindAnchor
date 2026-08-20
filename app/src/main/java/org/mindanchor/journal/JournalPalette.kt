/*
 * v0.63.0: the journal design palette.
 *
 * Locked from the superdesign drafts (b35ee64d, b446ae65, 5088ef9e,
 * c01a4b03, 4cf0a48a — "warmer journal" direction). The drafts use a
 * sepia paper card on a warm sky gradient: a journal on a desk.
 *
 * Tokens are file-private (lowercase) so the rest of the package
 * refers to them by name, not by hex literal. The hex values are
 * verbatim from the superdesign CSS — no rounding, no re-mapping,
 * no "looks close enough" drifts.
 *
 * Five mood tokens, one per named mood state, with the design's
 * 4-8% opacity tint for the mood-button background and the
 * 70-100% opacity for the mood-button text. The mood-button
 * "selected" text colour saturates to 100% (per draft CSS:
 * group-hover lifts the text from /70 to full), the inactive
 * (other) buttons fade to 40% via opacity 0.4.
 *
 * Risk note: this palette is daytime-paper. The launcher
 * still draws a time-of-day sky (CalmBackground) underneath
 * the paper card. The drafts are designed for the day sky
 * (#f2ece4 → #e8dfd5). The card surface (#f8f5f0) has
 * 100% contrast against the day-sky bottom; at night, when
 * the sky is the deep indigo (#0F1830), the card stands out
 * as a parchment lit from within. We accept that contrast
 * cost — the journal is a quiet room, and a quiet room
 * has a lamp on the desk.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.ui.graphics.Color

/** Sky gradient — top of the screen behind the paper card. */
internal val SkyTop = Color(0xFFF2ECE4)
internal val SkyBottom = Color(0xFFE8DFD5)

/** The paper card itself — the surface that holds every screen. */
internal val PaperCard = Color(0xFFF8F5F0)

/** Hairline border on the paper card (1dp at the design scale). */
internal val PaperBorder = Color(0xFFDCD7CC)

/** The ink that writes on the paper. */
internal val Ink = Color(0xFF3A2A1F)

/** The terracotta accent — headings, links, dividers, bang commands. */
internal val Terracotta = Color(0xFF8B5A44)

/** The "saved quietly" caption — used by the Today screen. */
internal val QuietTeal = Color(0xFF0F766E)

/** Mood tints — 4-8% opacity backgrounds, 70-100% text. */
internal val MoodCrushedBg = Color(0xFF8B4A4A).copy(alpha = 0.04f)
internal val MoodCrushedFg = Color(0xFF8B4A4A)
internal val MoodHeavyBg = Color(0xFF3B5278).copy(alpha = 0.04f)
internal val MoodHeavyFg = Color(0xFF3B5278)
internal val MoodSteadyBg = Color(0xFF0F766E).copy(alpha = 0.04f)
internal val MoodSteadyFg = Color(0xFF0F766E)
internal val MoodLightBg = Color(0xFFB4B0F8).copy(alpha = 0.06f)
internal val MoodLightFg = Color(0xFF3730A3)
internal val MoodBrightBg = Color(0xFFD8B49E).copy(alpha = 0.08f)
internal val MoodBrightFg = Color(0xFF8B5A44)

/** Mood tints for the dimmed "other" buttons (40% opacity). */
internal val MoodCrushedFgDim = MoodCrushedFg.copy(alpha = 0.40f)
internal val MoodHeavyFgDim = MoodHeavyFg.copy(alpha = 0.40f)
internal val MoodSteadyFgDim = MoodSteadyFg.copy(alpha = 0.40f)
internal val MoodLightFgDim = MoodLightFg.copy(alpha = 0.40f)
internal val MoodBrightFgDim = MoodBrightFg.copy(alpha = 0.40f)

/** The toggle switch — off is paper-tinted, on is terracotta. */
internal val ToggleOff = Color(0xFFDCD7CC)
internal val ToggleOn = Terracotta

/** Bang commands and the chevrons — terracotta at 60% resting, full on hover. */
internal val TerracottaResting = Terracotta.copy(alpha = 0.60f)
