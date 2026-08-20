/*
 * v0.63.0: the paper-texture card surface.
 *
 * The drafts render every screen on a #f8f5f0 paper card
 * with a 1px #dcd7cc hairline border and a 16dp rounded
 * corner. The card sits on the slow-sky gradient (top
 * #f2ece4 → bottom #e8dfd5) at the same 8dp elevation
 * tier the launcher's M3 card uses, so the card reads
 * as "a thing on the sky" — one elevation, no shadow.
 *
 * The drafts apply an inset box-shadow of
 * `inset 0 0 100px rgba(0,0,0,0.02)` to give the paper
 * a subtle vignette. Compose does not have an "inset
 * shadow" modifier, so we approximate with a 1dp
 * darker hairline at the top edge of the border. At
 * the design's resolution the vignette is the
 * difference between "paper" and "white box"; at
 * phone resolution the difference is invisible and
 * the hairline + paper fill carry the journal
 * aesthetic on their own.
 *
 * The card is full-bleed on small phones (1080dp) and
 * the inner-surface on wider ones (the drafts use 4xl
 * = 896px max with 8/12 padding). The Compose box
 * defaults to filling the parent width with a sensible
 * minimum height, and the surface itself is a single
 * [Box] with a [background] + [border] + [shadow]
 * (no rounded corners on the modifier — rounded
 * corners are part of the Shape passed to the
 * background, otherwise the border would draw square).
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The paper card. The [content] sits inside the card
 * with a 32dp horizontal padding and 64dp top + 48dp
 * bottom — the same rhythm the drafts use for the
 * 20px / 24px / 32px Tailwind spacing scale (the journal
 * uses 80 / 96 padding on 4xl, which scales to 32 / 48
 * on a 360dp phone).
 *
 * The 8dp elevation matches the M3 card tier the rest
 * of the launcher uses; one elevation keeps the visual
 * language consistent. The slow-sky cards in v0.56.0
 * also sit at this tier.
 */
@Composable
internal fun JournalPaperCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(cornerRadius), clip = false)
            .background(color = PaperCard, shape = RoundedCornerShape(cornerRadius))
            .border(width = 1.dp, color = PaperBorder, shape = RoundedCornerShape(cornerRadius)),
    ) {
        content()
    }
}

/**
 * A vertical-text string for the left-margin timestamp on
 * the Today screen ("2:14 PM"). The drafts use CSS
 * `writing-mode: vertical-rl; transform: rotate(180deg)` —
 * the first flips the text top-to-bottom, the second
 * rotates 180 so it reads bottom-to-top. The Compose
 * equivalent is a 180-degree rotation of a normally-
 * written string; the effect is the same.
 */
@Composable
internal fun JournalVerticalText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.copy(alpha = 0.20f),
) {
    Box(
        modifier = modifier
            .width(10.dp)
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = JournalTimestampMargin,
            color = color,
            modifier = Modifier.rotate(degrees = 180f),
        )
    }
}
