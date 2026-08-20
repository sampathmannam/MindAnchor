/*
 * v0.64.0 (BPD-first): the Today screen — the journal's
 * home.
 *
 * v0.63.0 had a hard-coded fixture note and three visible
 * pieces of pressure:
 *   1. "Entry No. 412" in the header — a counter, can
 *      shame ("412 entries, are you keeping up?")
 *   2. A broken vertical-timestamp rendered one
 *      character per line in the left margin (the
 *      JournalVerticalText composable constrained its
 *      width to 10dp, which the text overflows by
 *      stacking vertically — visually broken)
 *   3. "Continue writing..." with a feather icon —
 *      pressuring, always-on, classifying
 *
 * v0.64.0 changes:
 *   - No entry number. Just the date.
 *   - No vertical timestamp. Time is NOT on the screen
 *     (BPD-first: no time pressure).
 *   - No "Continue writing..." pressuring. The soft
 *     input asks "Anything here?" (or stays empty if
 *     there's no entry yet).
 *   - No "saved quietly" ticking counter. The body
 *     ends with "Thanks for writing that." — a
 *     validation, not a status.
 *   - The body is a single Column (no fixed-height Box)
 *     so the gap between body and caption follows the
 *     text, not a 300dp box.
 *   - The crisis line sits just above the 3-icon footer
 *     (search · archive · settings).
 *
 * Bang commands still work when typed into the body
 * (e.g. "!ground" routes to GroundMe — wired in v0.64.0
 * via the existing LauncherViewModel bang parser), but
 * the UI does not advertise them (BPD-first: no "!"
 * affordances).
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Today body. v0.64.0 reads/writes the entry body
 * via the JournalRoot's NoteStore binding (wired in
 * v0.64.0). The hard-coded fixture text is gone — the
 * screen is empty by default, which is the BPD-first
 * stance: an empty state is a valid state.
 */
@Composable
internal fun JournalToday(
    entryBody: String,
    onEntryBodyChange: (String) -> Unit,
    onContinueWriting: () -> Unit,
    onMood: () -> Unit,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    // BPD-first: the date is shown, the time is not.
    // v0.64.0 keeps the date because dates help orient
    // ("Monday" vs "Tuesday" matters) but omits the
    // hour/minute (which would be a counter). The format
    // "EEEE  d MMMM" already produces "Thursday 20 August"
    // with proper title-case — we do NOT lowercase + only
    // title-case the first character (that left "august"
    // lowercase in v0.64.0 first build).
    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE  d MMMM", Locale.US))

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
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — TODAY + divider + date.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 32.dp, bottom = 24.dp),
                ) {
                    Text(
                        text = "TODAY",
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
                    Text(
                        text = dateText,
                        style = JournalSmallCaps,
                        color = Ink.copy(alpha = 0.40f),
                    )
                }

                // Body — entry text. v0.64.0:
                //   - Single Column (no fixed-height Box)
                //   - A BasicTextField always present (the
                //     "Anything here?" placeholder is the
                //     default state, which IS the empty
                //     state — no separate "no note" UI)
                //   - When the user types, a "Thanks for
                //     writing that." validation line appears
                //     directly below the body, no extra
                //     spacer, no weight(1f) forcing the
                //     body to fill a fixed-height box.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    BasicTextField(
                        value = entryBody,
                        onValueChange = onEntryBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = JournalSerif,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            lineHeight = 32.sp,
                            color = Ink.copy(alpha = 0.80f),
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Terracotta),
                        decorationBox = { innerTextField ->
                            if (entryBody.isEmpty()) {
                                Text(
                                    text = "Anything here?",
                                    style = TextStyle(
                                        fontFamily = JournalSerif,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 20.sp,
                                        lineHeight = 32.sp,
                                    ),
                                    color = Ink.copy(alpha = 0.20f),
                                )
                            } else {
                                innerTextField()
                            }
                        },
                    )
                    if (entryBody.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Terracotta.copy(alpha = 0.20f)),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Thanks for writing that.",
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                            ),
                            color = AcknowledgeTeal,
                        )
                    }
                }

                // Spacer pushes the crisis line and footer
                // to the bottom of the card. v0.64.0: a
                // fixed 24dp gap (not weight(0.5f) which
                // produced a big white gap in the v0.64.0
                // first build).
                Spacer(modifier = Modifier.height(24.dp))

                // The "open a fuller composer" affordance.
                // v0.64.0: present only when the body is
                // empty, so the card doesn't keep
                // re-suggesting the QuickNote screen while
                // the user is writing inline. The mood
                // affordance sits in the same empty-state
                // block so the home stays quiet when
                // there's already something written.
                if (entryBody.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                            .clickable(onClick = onContinueWriting),
                    ) {
                        Text(
                            text = "Open the note — if you'd like.",
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                            ),
                            color = Ink.copy(alpha = 0.30f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 4.dp)
                            .clickable(onClick = onMood),
                    ) {
                        Text(
                            text = "Name what today feels like.",
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                            ),
                            color = Ink.copy(alpha = 0.30f),
                        )
                    }
                }

                // Crisis line — present on every surface.
                JournalCrisisLine(
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // The persistent footer (3 icons, no bangs).
                JournalFooter(
                    activeIcon = FooterIcon.None,
                    onSearch = onSearch,
                    onArchive = onArchive,
                    onSettings = onSettings,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
