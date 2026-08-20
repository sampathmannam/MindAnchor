/*
 * v0.64.0: the persistent journal footer (BPD-first).
 *
 * The footer is the journal's persistent anchor. It is
 * identical on every screen except Quick Note (which has
 * no footer — the Quick Note composer is the journal's
 * "outside" surface, a clean page on the desk).
 *
 * v0.64.0 footer spec:
 *   - 32dp tall, 12px vertical padding
 *   - 3 icon buttons, evenly spaced, NO labels, NO bang
 *     commands. The drafts render !ground / !breathe /
 *     !mood as small text buttons on the right; v0.64.0
 *     drops the "!" affordance entirely (BPD-first: the
 *     bang commands still work when typed into the Quick
 *     Note composer, but the UI does not advertise them).
 *   - Active icon = terracotta. Inactive = ink at 30% alpha.
 *   - Translucent paper fill at 50% alpha + 1dp top hairline
 *   - Every icon button is 48dp (a11y minimum)
 *   - The crisis line above the footer carries the iCall /
 *     Vandrevala / AASRA numbers — see JournalCrisisLine
 *     in each screen. The footer is the journal's
 *     navigation, the crisis line is the journal's
 *     promise.
 *
 * v0.63.0 had a sticky footer with 2-3 bang commands on
 * the right ("!ground", "!breathe", "!mood"). v0.64.0
 * removes the bang commands. The bangs are still typed
 * into the Quick Note composer as power-user shortcuts;
 * they just aren't part of the visible chrome.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The persistent footer. 3 icon buttons, no labels, no
 * bang commands. The active icon is passed in.
 */
@Composable
internal fun JournalFooter(
    activeIcon: FooterIcon,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paperAlpha = PaperCard.copy(alpha = 0.50f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(paperAlpha)
            .border(width = 1.dp, color = PaperBorder.copy(alpha = 0.30f), shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FooterIconButton(
                icon = FooterIcon.Search,
                isActive = activeIcon == FooterIcon.Search,
                onClick = onSearch,
                contentDescription = "Search",
            )
            FooterIconButton(
                icon = FooterIcon.Archive,
                isActive = activeIcon == FooterIcon.Archive,
                onClick = onArchive,
                contentDescription = "Archive",
            )
            FooterIconButton(
                icon = FooterIcon.Settings,
                isActive = activeIcon == FooterIcon.Settings,
                onClick = onSettings,
                contentDescription = "Settings",
            )
        }
    }
}

internal enum class FooterIcon { Search, Archive, Settings, None }

@Composable
private fun FooterIconButton(
    icon: FooterIcon,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    val color = if (isActive) Terracotta else Ink.copy(alpha = 0.30f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        when (icon) {
            FooterIcon.Search -> SearchGlyph(color = color, modifier = Modifier.size(20.dp))
            FooterIcon.Archive -> ArchiveGlyph(color = color, modifier = Modifier.size(20.dp))
            FooterIcon.Settings -> SettingsGlyph(color = color, modifier = Modifier.size(20.dp))
            FooterIcon.None -> {}
        }
    }
}
