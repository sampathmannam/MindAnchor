/*
 * v0.63.0: the Archive screen — the journal's notes list.
 *
 * Locked from superdesign draft b446ae65. The drafts
 * render four entries on a single scrollable card, each
 * with the date in a left-margin label and the entry
 * body in serif italic. The list has a header (back
 * chevron, "ARCHIVE / ALL ENTRIES" breadcrumb, "Search
 * Notes" right-side hint) and the persistent 3-icon
 * footer.
 *
 * v0.63.0 keeps the four fixture entries from the draft
 * (the same Monday-24 / Sunday-23 / Friday-21 /
 * Tuesday-18 prose the draft renders) so the visual
 * lands exactly. v0.64.0 will swap in the last 4
 * notes from [org.mindanchor.model.NoteStore].
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun JournalArchive(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onBangGround: () -> Unit,
    onBangBreathe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // v0.63.0 fixtures — the four entries the draft renders.
    val entries = remember {
        listOf(
            "Monday, Aug 24" to "The light through the window is different today. It feels quieter. I haven't said it out loud yet, but there is a strange sort of peace in just noticing the way the shadows stretch across the floor. No expectations for the next hour. Just this.",
            "Sunday, Aug 23" to "Tried the breathing exercise today. Four counts in, hold, four counts out. The air felt colder than usual. It's funny how we forget we are breathing until we decide to watch it happen.",
            "Friday, Aug 21" to "The city feels loud today. I am staying inside for a while. There is a specific kind of bravery in choosing to do nothing when the world demands everything. I'm keeping the phone in the other room.",
            "Tuesday, Aug 18" to "Found a stone in my pocket today from the walk last week. It's smooth and gray. Holding it helps when the thoughts get too fast. A small anchor for a heavy day.",
        )
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
                .height(800.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — back chevron + breadcrumb + search hint.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 32.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = "ARCHIVE / ",
                            style = JournalSmallCaps,
                            color = Ink.copy(alpha = 0.40f),
                        )
                        Text(
                            text = "ALL ENTRIES",
                            style = JournalSmallCaps,
                            color = Terracotta.copy(alpha = 0.60f),
                        )
                    }
                    Text(
                        text = "Search Notes",
                        style = JournalSmallCaps,
                        color = Ink.copy(alpha = 0.30f),
                        modifier = Modifier.clickable(onClick = onSearch),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(1.dp)
                        .background(Terracotta.copy(alpha = 0.05f)),
                )

                // Body — the 4 entries.
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    entries.forEachIndexed { index, (date, body) ->
                        // Each entry: a 96dp-wide left-margin date label
                        // (absolute-positioned so it sits in the
                        // card's own margin, not in the column's),
                        // plus a body text that fills the rest of
                        // the row. The drafts use a `position: absolute;
                        // left: -48px` pattern, which we approximate
                        // by giving the date its own narrow Row at
                        // the top of a vertical column.
                        Column {
                            Text(
                                text = date,
                                style = JournalArchiveEntry.copy(
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 11.sp,
                                ),
                                color = Ink.copy(alpha = 0.30f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Text(
                                text = body,
                                style = JournalArchiveEntry,
                                color = Ink.copy(alpha = 0.80f),
                            )
                        }
                        if (index < entries.size - 1) {
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Footer.
                JournalFooter(
                    activeIcon = FooterIcon.Archive,
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
