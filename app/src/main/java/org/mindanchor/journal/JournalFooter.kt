/*
 * v0.63.0: the persistent journal footer.
 *
 * The drafts use the same 3-icon + bang-commands footer
 * on every screen except Quick Note (which has no footer
 * at all — the Quick Note composer is meant to feel
 * "outside" the journal, a clean page on the desk).
 *
 * Footer spec:
 *   - 32dp tall, 12px vertical padding
 *   - 3 icon buttons on the left: search, archive, settings
 *     (active = terracotta, inactive = ink/30%)
 *   - 2-3 bang commands on the right: !ground, !breathe,
 *     and !mood (home only)
 *   - Translucent paper fill at 50% alpha + 12dp top
 *     border + 12dp blur backdrop
 *   - Click target for every button is 48dp (a11y minimum)
 *   - Bang commands are serif italic, resting 60% terracotta
 *
 * v0.63.0: the footer is *not* a sticky bar over the
 * content. It sits below the content with 0px overlap,
 * so the body can scroll past it (Notes and Settings
 * have scrollable bodies). On Home the body is the
 * input section, which is short — no scroll needed.
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The persistent footer. 3 icon buttons + 2-3 bang commands.
 * The active icon is passed in (the draft shows terracotta
 * for whichever screen is currently shown).
 */
@Composable
internal fun JournalFooter(
    activeIcon: FooterIcon,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    onBangGround: () -> Unit,
    onBangBreathe: () -> Unit,
    onBangMood: (() -> Unit)? = null,
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: 3 icon buttons.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FooterIconButton(
                    icon = FooterIcon.Search,
                    isActive = activeIcon == FooterIcon.Search,
                    onClick = onSearch,
                    contentDescription = "Search",
                )
                Spacer(modifier = Modifier.width(20.dp))
                FooterIconButton(
                    icon = FooterIcon.Archive,
                    isActive = activeIcon == FooterIcon.Archive,
                    onClick = onArchive,
                    contentDescription = "Archive",
                )
                Spacer(modifier = Modifier.width(20.dp))
                FooterIconButton(
                    icon = FooterIcon.Settings,
                    isActive = activeIcon == FooterIcon.Settings,
                    onClick = onSettings,
                    contentDescription = "Settings",
                )
            }
            // Right: bang commands.
            Row(verticalAlignment = Alignment.CenterVertically) {
                BangButton("!ground", onClick = onBangGround)
                Spacer(modifier = Modifier.width(16.dp))
                BangButton("!breathe", onClick = onBangBreathe)
                if (onBangMood != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    BangButton("!mood", onClick = onBangMood)
                }
            }
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
            .size(40.dp)
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

@Composable
private fun BangButton(label: String, onClick: () -> Unit) {
    val restColor = Terracotta.copy(alpha = 0.60f)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = JournalBang,
            color = restColor,
        )
    }
}
