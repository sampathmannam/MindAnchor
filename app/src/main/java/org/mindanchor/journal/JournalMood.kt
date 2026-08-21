/*
 * v0.64.0 (BPD-first): the Mood screen.
 *
 * v0.63.0 had:
 *   - A "CHAPTER ONE / How does it feel right now?"
 *     header — the "chapter" framing implies progress
 *     and chapters-to-come (a counter narrative).
 *   - A $dateText • $timeText subtitle — date and time
 *     together. The time is a counter.
 *   - 5 mood buttons stacked vertically. Tapping a
 *     mood dimmed the other 4 to 40% opacity (the
 *     "selected state"). The response line below
 *     validated the choice ("It is okay to sit with
 *     the weight for a moment.").
 *
 * v0.64.0 changes:
 *   - Header: just "MOOD" + a single line of soft
 *     framing text: "If you'd like to name it,
 *     here's where." No chapter, no date, no time.
 *   - 5 mood states in a single horizontal row, all
 *     equal-weight. NO dimming of unselected states
 *     (BPD-first: don't rank the states; "Steady" is
 *     not less than "Bright").
 *   - On selection, the response line VALIDATES:
 *     "Steady it is. Not every day has to be Bright."
 *     — DBT-grounded validate-then-suggest language
 *     (Linehan 1993).
 *   - Crisis line above the footer.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// v0.66.0: promoted from `private` to `internal` so the diary package
// (Task 1: DiaryCardEntry) can reference the 5-state mood enum without
// re-declaring it. The enum stays file-local to the mood surface's
// UI concerns — no other file mutates it; the diary package only reads
// the values.
internal enum class Mood(
    val displayName: String,
    val bg: Color,
    val fg: Color,
    val responseText: String,
) {
    CRUSHED(
        "Crushed",
        MoodCrushedBg,
        MoodCrushedFg,
        "Crushed is okay. You don't have to fix it.",
    ),
    HEAVY(
        "Heavy",
        MoodHeavyBg,
        MoodHeavyFg,
        "Heavy is allowed. Move slowly if you can.",
    ),
    STEADY(
        "Steady",
        MoodSteadyBg,
        MoodSteadyFg,
        "Steady it is. Not every day has to be Bright.",
    ),
    LIGHT(
        "Light",
        MoodLightBg,
        MoodLightFg,
        "Light. Carry it gently.",
    ),
    BRIGHT(
        "Bright",
        MoodBrightBg,
        MoodBrightFg,
        "Bright. You don't have to earn it again tomorrow.",
    ),
}

@Composable
internal fun JournalMood(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onCall: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Mood?>(null) }
    var showResponse by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected != null) {
            // v0.64.0: 200ms delay before the validate
            // line appears, so the visual settles (DBT
            // validate-then-suggest — the suggestion
            // follows the validation, never the reverse).
            kotlinx.coroutines.delay(200)
            showResponse = true
        } else {
            showResponse = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        JournalPaperCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(640.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — back link + MOOD title.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 32.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChevronLeftGlyph(color = Ink.copy(alpha = 0.30f))
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = "MOOD",
                        style = JournalSmallCaps,
                        color = Terracotta,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(1.dp)
                        .background(Terracotta.copy(alpha = 0.30f)),
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Soft framing — "if you'd like to name
                // it, here's where." v0.64.0: no time,
                // no date, no "track your mood" framing.
                Text(
                    text = "If you'd like to name it, here's where.",
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light,
                        fontSize = 18.sp,
                    ),
                    color = Ink.copy(alpha = 0.60f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                // Body — 5 mood chips in a horizontal row.
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Mood.values().forEach { mood ->
                        MoodChip(
                            mood = mood,
                            isSelected = selected == mood,
                            onClick = {
                                selected = if (selected == mood) null else mood
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Validate-then-suggest response.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                        .height(80.dp),
                ) {
                    if (showResponse && selected != null) {
                        Text(
                            text = selected?.responseText.orEmpty(),
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light,
                                fontSize = 16.sp,
                            ),
                            color = Ink.copy(alpha = 0.70f),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Crisis line.
                JournalCrisisLine(
                    modifier = Modifier.padding(vertical = 8.dp),
                    onCall = onCall,
                )

                // Footer.
                JournalFooter(
                    activeIcon = FooterIcon.None,
                    onSearch = onSearch,
                    onArchive = onBack,
                    onSettings = onSettings,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MoodChip(
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(96.dp)
            .background(mood.bg, shape = RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Terracotta else mood.fg.copy(alpha = 0.20f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoodGlyph(mood = mood)
        Text(
            text = mood.displayName,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Light,
                fontSize = 14.sp,
            ),
            color = mood.fg,
        )
    }
}

@Composable
private fun MoodGlyph(mood: Mood) {
    when (mood) {
        Mood.CRUSHED -> CloudLightningGlyph(color = mood.fg, modifier = Modifier.size(20.dp))
        Mood.HEAVY -> AnchorGlyph(color = mood.fg, modifier = Modifier.size(20.dp))
        Mood.STEADY -> WavesGlyph(color = mood.fg, modifier = Modifier.size(20.dp))
        Mood.LIGHT -> FeatherGlyph(color = mood.fg, modifier = Modifier.size(20.dp))
        Mood.BRIGHT -> SunGlyph(color = mood.fg, modifier = Modifier.size(20.dp))
    }
}
