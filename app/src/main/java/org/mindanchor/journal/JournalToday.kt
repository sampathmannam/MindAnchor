/*
 * v0.63.0: the Today screen — the journal's home.
 *
 * Locked from superdesign draft b35ee64d ("MindAnchor Journal -
 * Day One Chapter"). The drafts render a 4-icon footer
 * (search, archive, notes, settings) plus 3 bang commands
 * (!ground, !breathe, !mood). v0.63.0 sticks to the launcher's
 * 3-icon rule (search, archive, settings — no notes tile;
 * notes are reachable via the "Continue writing..." input
 * or the !note bang). The 3 bangs (`!ground`, `!breathe`,
 * `!mood`) are home-specific.
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Today body. v0.63.0 uses a single hard-coded fixture
 * note (the same prose the superdesign draft renders) so
 * the visual lands exactly. v0.64.0 will swap in the most-
 * recent note from [org.mindanchor.model.NoteStore].
 *
 * The "Entry No. 412" is a journal ritual, not a count —
 * the v0.63.0 fixture is 412; v0.64.0 will read from
 * NoteStore.count() but the rendering stays "Entry No. N"
 * rather than "412 notes". The drafts render the
 * hard-coded 412 because the journal aesthetic reads
 * "412th day of practice", not "412 entries in DB".
 */
@Composable
internal fun JournalToday(
    onContinueWriting: () -> Unit,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    onBangGround: () -> Unit,
    onBangBreathe: () -> Unit,
    onBangMood: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteText by remember { mutableStateOf(
        "The light through the window is different today. It feels quieter. I haven't said it out loud yet, but there is a strange sort of peace in just noticing the way the shadows stretch across the floor. No expectations for the next hour. Just this."
    ) }
    val today = remember { LocalDate.now() }
    val time = remember { LocalTime.now() }
    val dateText = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
    val timeText = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    val entryNumber = 412

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
                .height(720.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — TODAY + divider + date / entry no.
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = dateText.uppercase(),
                            style = JournalSmallCaps,
                            color = Ink.copy(alpha = 0.40f),
                        )
                        Text(
                            text = "Entry No. $entryNumber",
                            style = JournalEntryNumber,
                            color = Ink.copy(alpha = 0.30f),
                        )
                    }
                }

                // Body — vertical timestamp in the left margin + note.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(300.dp),
                ) {
                    JournalVerticalText(
                        text = timeText,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 0.dp),
                    )
                    Text(
                        text = noteText,
                        style = JournalNoteBody,
                        color = Ink.copy(alpha = 0.80f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 28.dp)
                            .fillMaxWidth(),
                    )
                    Text(
                        text = "saved quietly",
                        style = JournalBang,
                        color = QuietTeal.copy(alpha = 0.50f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 28.dp, bottom = 4.dp),
                    )
                }

                // Input section — "Continue writing..." with feather icon.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .clickable(onClick = onContinueWriting)
                        .padding(vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatherGlyph(
                            color = Ink.copy(alpha = 0.30f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue writing...",
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontWeight = FontWeight.Light,
                                fontSize = 18.sp,
                            ),
                            color = Ink.copy(alpha = 0.20f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // The persistent footer.
                JournalFooter(
                    activeIcon = FooterIcon.None,
                    onSearch = onSearch,
                    onArchive = onArchive,
                    onSettings = onSettings,
                    onBangGround = onBangGround,
                    onBangBreathe = onBangBreathe,
                    onBangMood = onBangMood,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
