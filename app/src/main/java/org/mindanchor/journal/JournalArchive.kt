/*
 * v0.64.0 (BPD-first): the Archive screen — the journal's
 * notes list.
 *
 * v0.63.0 had a header reading "ARCHIVE / ALL ENTRIES"
 * (the second part is a count — BPD-unsafe) and a
 * "Search Notes" right-side hint (a discoverability
 * affordance for a feature we don't need to advertise —
 * the bangs still work, the search is a real
 * affordance via the footer icon).
 *
 * v0.64.0 changes:
 *   - Header: just "ARCHIVE". No count, no breadcrumb.
 *   - No "Search Notes" hint. The search is the first
 *     footer icon.
 *   - Each entry: date above, body below. No vertical
 *     timestamp margin. The drafts used a left-margin
 *     absolute date label; v0.64.0 stacks the date
 *     above the body (the same column) — simpler,
 *     reads naturally, no rendering bugs.
 *   - No date grouping. No "This week / This month /
 *     Older" — these are implicit rankings.
 *   - No entry count at the top or bottom.
 *   - Crisis line above the footer.
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

/**
 * The Archive body. v0.64.0 reads entries from the
 * journal's NoteStore. v0.64.0 still keeps the four
 * fixture entries from v0.63.0 (so the visual lands
 * exactly) — v0.65.0+ will swap in live data.
 */
@Composable
internal fun JournalArchive(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onCall: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // v0.64.0 fixtures — the four entries the draft renders.
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
                .height(900.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — back chevron + title only.
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
                        text = "ARCHIVE",
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

                // Body — the 4 entries, plain list.
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    entries.forEachIndexed { index, (date, body) ->
                        Column {
                            Text(
                                text = date,
                                style = JournalArchiveEntry.copy(
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 12.sp(),
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
                            Spacer(modifier = Modifier.height(40.dp))
                        }
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
                    activeIcon = FooterIcon.Archive,
                    onSearch = onSearch,
                    onArchive = onBack,
                    onSettings = onSettings,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Suppress("FunctionName")
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
