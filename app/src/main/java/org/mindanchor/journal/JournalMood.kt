/*
 * v0.63.0: the Mood screen — check-in with one of 5
 * named states.
 *
 * Locked from superdesign draft c01a4b03 ("How does it
 * feel right now?"). The drafts render 5 mood buttons
 * stacked vertically, each with its own subtle background
 * tint (4-8% opacity) and a Lucide-style icon on the
 * right. Clicking a mood fades the other four to 40%
 * opacity and shows a single italic response line
 * below the buttons:
 *
 *   Crushed  → "It is okay to sit with the weight for a moment."
 *   Heavy    → "Let yourself move slowly today. There is no rush."
 *   Steady   → "There is a quiet strength in simply being steady."
 *   Light    → "Savor the air. It feels a little easier to breathe."
 *   Bright   → "Carry this warmth with you, gently."
 *
 * The response line uses an opacity + translateY
 * transition (200ms delay, 800ms ease-out) so the
 * words appear to settle into place, not snap.
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Mood(
    val displayName: String,
    val bg: Color,
    val fg: Color,
    val fgDim: Color,
    val responseText: String,
) {
    CRUSHED(
        "Crushed",
        MoodCrushedBg,
        MoodCrushedFg,
        MoodCrushedFgDim,
        "It is okay to sit with the weight for a moment.",
    ),
    HEAVY(
        "Heavy",
        MoodHeavyBg,
        MoodHeavyFg,
        MoodHeavyFgDim,
        "Let yourself move slowly today. There is no rush.",
    ),
    STEADY(
        "Steady",
        MoodSteadyBg,
        MoodSteadyFg,
        MoodSteadyFgDim,
        "There is a quiet strength in simply being steady.",
    ),
    LIGHT(
        "Light",
        MoodLightBg,
        MoodLightFg,
        MoodLightFgDim,
        "Savor the air. It feels a little easier to breathe.",
    ),
    BRIGHT(
        "Bright",
        MoodBrightBg,
        MoodBrightFg,
        MoodBrightFgDim,
        "Carry this warmth with you, gently.",
    ),
}

@Composable
internal fun JournalMood(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onBangGround: () -> Unit,
    onBangBreathe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Mood?>(null) }
    // The fade-in / slide-in for the response line.
    var showResponse by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected != null) {
            // The drafts use a 200ms delay before the
            // response text fades in. We honour the
            // delay so the visual lands exactly.
            kotlinx.coroutines.delay(200)
            showResponse = true
        } else {
            showResponse = false
        }
    }

    val now = remember { LocalDate.now() }
    val time = remember { LocalTime.now() }
    val dateText = now.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
    val timeText = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

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
                .height(760.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — back link + title + date subtitle.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 32.dp, bottom = 16.dp),
                ) {
                    Row(
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
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "CHAPTER ONE",
                            style = JournalSmallCaps,
                            color = Ink.copy(alpha = 0.30f),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "How does it feel right now?",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = JournalSerif,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                            fontSize = 24.sp,
                        ),
                        color = Terracotta,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$dateText • $timeText",
                        style = JournalDate,
                        color = Ink.copy(alpha = 0.40f),
                    )
                }

                // Body — 5 mood buttons + response line.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Mood.values().forEach { mood ->
                        MoodButton(
                            mood = mood,
                            isSelected = selected == mood,
                            isDimmed = selected != null && selected != mood,
                            onClick = {
                                selected = if (selected == mood) null else mood
                            },
                        )
                    }
                }

                // Response line — fade in when a mood is selected.
                // v0.63.0: simple if-else instead of AnimatedVisibility
                // (the AnimatedVisibility ColumnScope receiver clashes
                // with the enclosing Box inside a Column). The drafts
                // use an 800ms fade + 8px slide; v0.64.0 will restore
                // the animation when the layout is split into a
                // separate sub-composable.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .height(60.dp),
                ) {
                    if (showResponse && selected != null) {
                        Text(
                            text = selected?.responseText.orEmpty(),
                            style = JournalMoodResponse,
                            color = Ink.copy(alpha = 0.60f),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Footer — settings active (mood is reachable from
                // anywhere via the !mood bang; the home footer is
                // the canonical entry, but settings also keeps
                // the 3-icon bar for consistency).
                JournalFooter(
                    activeIcon = FooterIcon.None,
                    onSearch = onSearch,
                    onArchive = onBack,
                    onSettings = onSettings,
                    onBangGround = onBangGround,
                    onBangBreathe = onBangBreathe,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MoodButton(
    mood: Mood,
    isSelected: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (isDimmed) 0.40f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(mood.bg, shape = RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = mood.fg.copy(alpha = if (isSelected) 0.20f else 0.05f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = mood.displayName,
            style = JournalMoodLabel,
            color = mood.fg,
        )
        MoodGlyph(mood = mood)
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

@Suppress("FunctionName")
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
