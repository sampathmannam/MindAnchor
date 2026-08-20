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
 * v0.65.0: each number is a long-press target that
 * fires ACTION_DIAL. NOT a single tap (BPD-first: a
 * single tap is too impulsive an action for a crisis
 * line). Long-press is a deliberate, slow gesture —
 * exactly the BPD-first shape. The numbers stay at the
 * same alpha as the rest of the secondary text, so the
 * line is not visually a button bar.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
 * secondary text keeps the line equal-weight.
 *
 * v0.65.0: each number is a long-press target.
 *   onCall("9152987821")         → iCall
 *   onCall("18602662362")        → Vandrevala
 *   onCall("9820466726")         → AASRA
 *
 * [onCall] is invoked with the phone number to dial, no
 * prefix. The caller (JournalRoot) is responsible for
 * translating that to an ACTION_DIAL intent. The line
 * itself does no I/O — keep the journal composable
 * unit-testable without a Context.
 */
@Composable
internal fun JournalCrisisLine(
    modifier: Modifier = Modifier,
    onCall: (String) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // First line: "Need to talk?  iCall 9152987821"
            // Only the iCall number is tappable here, not the
            // "Need to talk?" prefix. The whole-line tap is
            // reserved for the iCall number.
            Text(
                text = "Need to talk?  iCall 9152987821",
                style = JournalCrisisLineStyle(),
                color = CrisisLine,
                textAlign = TextAlign.Center,
                modifier = Modifier.pointerInput("iCall") {
                    detectTapGestures(onLongPress = { onCall("9152987821") })
                },
            )
            // Second line: Vandrevala ... · AASRA ...
            // Two independent long-press targets.
            RowCrisisSecondLine(onCall = onCall)
        }
    }
}

/**
 * The second crisis line, with two distinct long-press
 * targets (Vandrevala, AASRA). Layout-wise they sit on
 * one Row with a "·" separator; for the gesture layer
 * each number is its own Box so the long-press hit
 * areas don't overlap.
 */
@Composable
private fun RowCrisisSecondLine(onCall: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Vandrevala 1860-2662-362",
            style = JournalCrisisLineStyle(),
            color = CrisisLine,
            textAlign = TextAlign.Center,
            modifier = Modifier.pointerInput("Vandrevala") {
                detectTapGestures(onLongPress = { onCall("18602662362") })
            },
        )
        Text(
            text = "  ·  ",
            style = JournalCrisisLineStyle(),
            color = CrisisLine,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "AASRA 9820466726",
            style = JournalCrisisLineStyle(),
            color = CrisisLine,
            textAlign = TextAlign.Center,
            modifier = Modifier.pointerInput("AASRA") {
                detectTapGestures(onLongPress = { onCall("9820466726") })
            },
        )
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
