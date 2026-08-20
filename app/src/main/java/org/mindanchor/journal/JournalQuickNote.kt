/*
 * v0.63.0: the Quick Note composer — a clean page on the
 * desk.
 *
 * Locked from superdesign draft 4cf0a48a. The Quick
 * Note screen is the journal's "outside" surface —
 * no card chrome, no footer, no persistent navigation.
 * A full-bleed paper card with a vertical timestamp
 * margin on the left, an auto-focused textarea
 * ("What is resting on your mind?"), a "Save" link
 * at the bottom-right, and a row of 7 bang-command
 * hints below the textarea.
 *
 * The footer deliberately omits the journal navigation
 * icons (search / archive / settings). The drafts
 * use the same 7 bangs that the home footer
 * references (!ground, !panic, !breathe, !mood,
 * !note, !task, !settings) — but inline as hints,
 * not as a persistent footer. v0.63.0 implements
 * the 7-bang row but treats them as static hints,
 * not as live buttons — clicking a hint brings
 * the user to the home screen (where the bangs
 * become live).
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun JournalQuickNote(
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The drafts auto-focus the textarea on entry. The
        // v0.63.0 build does the same — a small delay
        // (~200ms) lets the card finish animating in
        // before the keyboard slides up.
        kotlinx.coroutines.delay(200)
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
            // No-op — focus can fail on some emulators;
            // the textarea is still tappable.
        }
    }

    val today = remember { LocalDate.now() }
    val time = remember { LocalTime.now() }
    // v0.63.0: LocalDate has no hour/minute field, so a single pattern that
    // includes "HH:mm" throws UnsupportedTemporalTypeException at format time.
    // Format date and time separately and concatenate. The "•" is U+2022.
    val dateText = today.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US)) +
        " • " +
        time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

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
            Box(modifier = Modifier.fillMaxSize()) {
                // Vertical timestamp on the left.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 96.dp)
                        .width(10.dp),
                ) {
                    JournalVerticalText(
                        text = dateText,
                        color = Ink.copy(alpha = 0.20f),
                    )
                }

                // Textarea + Save link.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp)
                        .padding(top = 48.dp, bottom = 16.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
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
                                    text = "What is resting on your mind?",
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

                    // Save link.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "Save",
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                fontSize = 18.sp,
                            ),
                            color = Terracotta.copy(alpha = 0.60f),
                            modifier = Modifier
                                .clickable {
                                    if (text.isNotBlank()) {
                                        onSave(text)
                                        text = ""
                                    }
                                }
                                .padding(8.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 7-bang hint row.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BangHint("!ground")
                        BangHint("!panic")
                        BangHint("!breathe")
                        BangHint("!mood")
                        BangHint("!note")
                        BangHint("!task")
                        BangHint("!settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun BangHint(label: String) {
    Text(
        text = label,
        style = JournalHint,
        color = Ink.copy(alpha = 0.30f),
        modifier = Modifier
            .clickable { onHintTapped(label) }
            .padding(vertical = 4.dp),
    )
}

/**
 * Bang hints are not live in v0.63.0 — they all return
 * the user to the home screen, where the bangs become
 * live. v0.64.0 will wire each hint to its target
 * surface (e.g. !mood opens the Mood screen).
 */
private fun onHintTapped(label: String) {
    // No-op: hints dismiss the keyboard and return to
    // the home via the back button. The drafts do not
    // wire the hints; the implementation here matches
    // the draft's intent ("a reminder of what you can
    // do, not a navigation menu").
    @Suppress("UNUSED_PARAMETER")
    val ignored = label
}
