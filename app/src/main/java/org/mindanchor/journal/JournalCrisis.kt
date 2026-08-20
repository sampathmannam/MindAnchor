/*
 * v0.64.0 (BPD-first): the crisis-line composable.
 *
 * The same line appears on every journal surface, just
 * above the persistent footer (or just above the
 * bottom edge of the card on Quick Note, which has no
 * footer). The line is NOT a banner, NOT highlighted, NOT
 * a call-to-action. It sits at the same alpha as the
 * journal's other secondary text, so the numbers are
 * equal citizens to the rest of the page.
 *
 * Numbers (verified):
 *   iCall        9152987821          TISS Mumbai
 *   Vandrevala   1860-2662-362       24/7 multilingual
 *   AASRA        9820466726          24/7 suicide prevention
 *
 * The line is broken across two lines at narrower widths
 * (the Plus Jakarta Sans 11sp + 360dp card body wraps
 * naturally on 393dp phones). The numbers themselves are
 * NOT tappable in v0.64.0 — the journal is a quiet room,
 * not a dialer. v0.65.0+ may wire the numbers to
 * ACTION_DIAL intents behind a long-press, but never
 * behind a single tap (BPD-first: a single tap is too
 * impulsive an action for a crisis line).
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The crisis line. Two short lines:
 *   "Need to talk?  iCall 9152987821"
 *   "Vandrevala 1860-2662-362  ·  AASRA 9820466726"
 *
 * The 11sp Plus Jakarta Sans at the same alpha as other
 * secondary text keeps the line equal-weight. The numbers
 * are not links, not buttons, not highlighted. They are
 * just there.
 */
@Composable
internal fun JournalCrisisLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Need to talk?  iCall 9152987821",
                style = JournalCrisisLineStyle(),
                color = CrisisLine,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Vandrevala 1860-2662-362  ·  AASRA 9820466726",
                style = JournalCrisisLineStyle(),
                color = CrisisLine,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The crisis-line text style. 11sp Plus Jakarta Sans,
 * normal weight, 0.5 letter-spacing. Plus Jakarta Sans
 * is the journal's sans face (the serif is Crimson Pro
 * for body text only).
 */
internal fun JournalCrisisLineStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
)
