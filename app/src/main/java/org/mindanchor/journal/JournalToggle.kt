/*
 * v0.63.0: the journal toggle switch.
 *
 * The drafts use a 32x18dp pill switch:
 *   - off: #dcd7cc track, white thumb at the left
 *   - on:  #8b5a44 track, white thumb at the right
 *   - thumb is 14dp diameter (so 2dp of track is visible
 *     on either side), 4dp animation easing over .4s
 *     (matching the draft's `transition: .4s`).
 *
 * The Compose Material 3 [Switch] is too tall (32dp vs
 * 18dp) and uses the wrong thumb shape. The launcher
 * already builds custom toggles for the SettingsScreen
 * (the v0.50.0 6-tile tile + the v0.55.0 wearable
 * roster use Material Switches in a different visual
 * language). For the journal, we draw the switch.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A 32x18dp pill switch. Clicking the row OR the switch
 * toggles — the entire row is the tap target, but the
 * switch itself is the visual.
 */
@Composable
internal fun JournalToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (checked) ToggleOn else ToggleOff
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 400),
        label = "JournalToggleThumb",
    )
    Box(
        modifier = modifier
            .size(width = 32.dp, height = 18.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(trackColor)
            .semantics { role = Role.Switch }
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset + 2.dp, y = 0.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White),
        )
    }
}
