/*
 * v0.64.0 (BPD-first): the Quick Note composer.
 *
 * v0.63.0 had a Save link and a 7-bang hint row at the
 * bottom. v0.64.0 removes both:
 *   - No Save button. Auto-save on close.
 *   - No "!" affordance. Bang commands still work when
 *     typed (the existing LauncherViewModel bang parser
 *     routes "!ground" to GroundMe, "!breathe" to
 *     Breathing, etc.), but the UI does not advertise
 *     them. The hint row was 7 little text buttons that
 *     competed for attention.
 *   - No vertical timestamp. The drafts put "20 Aug •
 *     06:19" in the left margin, rotated 90 degrees
 *     (which the Compose render was breaking — the
 *     constraint width of 10dp was too narrow, so the
 *     text stacked character-by-character). v0.64.0
 *     drops the vertical margin entirely.
 *
 * v0.64.0 changes:
 *   - Title: just "NOTE" (not "Quick Note" — "Quick" is
 *     hurry language).
 *   - Subtitle: "When you're ready, or not." (soft
 *     permission framing).
 *   - Single text input, full-bleed within the card.
 *   - "This saves when you leave." (auto-save disclosure).
 *   - Crisis line at the bottom (the Quick Note has no
 *     footer; the crisis line is the only chrome).
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Quick Note composer. v0.64.0: a single text input,
 * auto-saves on close. The [text] is wired in v0.64.0
 * via the JournalRoot's NoteStore binding.
 */
@Composable
internal fun JournalQuickNote(
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The drafts auto-focus the textarea on entry.
        // v0.64.0 keeps that — a small delay (~200ms)
        // lets the card finish rendering before the
        // keyboard slides up. Note: the user can always
        // tap to focus; the auto-focus is just a soft
        // default, not a pressure.
        kotlinx.coroutines.delay(200)
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
            // No-op — focus can fail on some emulators;
            // the textarea is still tappable.
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        JournalPaperCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
            ) {
                // Title — NOTE (no "Quick").
                Text(
                    text = "NOTE",
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 32.sp,
                        letterSpacing = 5.sp,
                    ),
                    color = Terracotta,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Terracotta.copy(alpha = 0.30f)),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle — soft permission framing.
                Text(
                    text = "When you're ready, or not.",
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light,
                        fontSize = 16.sp,
                    ),
                    color = Ink.copy(alpha = 0.50f),
                )

                // The textarea.
                Spacer(modifier = Modifier.height(24.dp))
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontFamily = JournalSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 20.sp,
                        lineHeight = 36.sp,
                        color = Ink.copy(alpha = 0.90f),
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Terracotta),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = "What's here.",
                                style = TextStyle(
                                    fontFamily = JournalSerif,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 20.sp,
                                    lineHeight = 36.sp,
                                ),
                                color = Ink.copy(alpha = 0.20f),
                            )
                        } else {
                            innerTextField()
                        }
                    },
                )

                // Auto-save disclosure.
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This saves when you leave.",
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp,
                    ),
                    color = Ink.copy(alpha = 0.40f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Crisis line — the only chrome at the
                // bottom of the Quick Note. The composer
                // has no footer (the drafts removed the
                // 3-icon footer for this surface, and
                // v0.64.0 keeps that).
                JournalCrisisLine()
            }
        }
    }
}
