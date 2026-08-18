@file:Suppress("MaxLineLength", "FunctionNaming", "LongMethod", "MagicNumber")
package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mindanchor.R
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.theme.SoftContent

/**
 * v0.33.0 / v0.35.0: the home-card that replaces the v0.32.x
 * "How is it right now?" Distress Thermometer with a needs-first
 * 2×2 grid. The four doors are need-language ("I need X"), not
 * action-language ("do X") — the home asks what is needed first
 * and then offers one well-shaped path.
 *
 * Research basis:
 *  - DBT Distress Tolerance (Linehan 1993, ch. 8):
 *    validate-then-suggest. The card validates the moment
 *    (the caption: "Pick the one that is closest to what you
 *    need. There is no wrong door.") before offering any
 *    action. There is no "score your distress" step before
 *    the doors open.
 *  - IFS (Schwartz 1995): the four doors map loosely to
 *    "Be heard" = Self-led witness, "A moment" = unblend,
 *    "Check in" = notice (somatic + data), "Get through this"
 *    = protector work. The mapping is loose on purpose — the
 *    user does not have to know IFS to use the doors.
 *  - Lindsay 2024 JMIR: home-screen surfaces that ask
 *    "what do you need" outperform "what do you want to do"
 *    in N-of-1 wellness engagement studies.
 *
 * Why a 2×2 grid and not a row of 4 buttons: four buttons in
 * a row on a 1080-wide phone would each be ~250dp — enough
 * for the word but not for the caption. The 2×2 lets each door
 * show its caption, and BPD-strict design (per the v0.26.6
 * audit) says the caption is what tells the user the door is
 * the right one. Without the caption, a user mid-dysregulation
 * picks the first one they see, not the one they need.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming")
@Composable
fun NeedsCard(
    sky: SkyContent,
    onBeHeard: () -> Unit,
    onMoment: () -> Unit,
    onCheckIn: () -> Unit,
    onGetThrough: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_needs_title),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_needs_caption),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        // v0.37.1: each cell's caption is now capped at
        // `maxLines = 3, overflow = Ellipsis` (see NeedsCardCell
        // below) so the four doors land at a uniform 3-line
        // height. v0.35.0 left the captions uncapped, which
        // gave "A moment" four lines and "Get through this"
        // two — a 2×2 with mismatched row heights.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NeedsCardCell(
                modifier = Modifier.weight(1f),
                sky = sky,
                titleRes = R.string.home_needs_be_heard,
                captionRes = R.string.home_needs_be_heard_caption,
                onClick = onBeHeard,
            )
            NeedsCardCell(
                modifier = Modifier.weight(1f),
                sky = sky,
                titleRes = R.string.home_needs_moment,
                captionRes = R.string.home_needs_moment_caption,
                onClick = onMoment,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NeedsCardCell(
                modifier = Modifier.weight(1f),
                sky = sky,
                titleRes = R.string.home_needs_check_in,
                captionRes = R.string.home_needs_check_in_caption,
                onClick = onCheckIn,
            )
            NeedsCardCell(
                modifier = Modifier.weight(1f),
                sky = sky,
                titleRes = R.string.home_needs_get_through,
                captionRes = R.string.home_needs_get_through_caption,
                onClick = onGetThrough,
            )
        }
    }
}

/**
 * One cell in the 2×2 needs grid. A plain surface (no border,
 * no shadow — the rest of the home avoids both) with the title
 * one line of titleMedium and the caption one or two lines of
 * bodySmall. Combined-clickable so long-press in a future
 * release can surface context; the v0.35.0 release only wires
 * the short-tap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NeedsCardCell(
    modifier: Modifier,
    sky: SkyContent,
    titleRes: Int,
    captionRes: Int,
    onClick: () -> Unit,
) {
    // A plain bordered cell — no fill, no shadow. The launcher
    // is BPD-strict (per the v0.26.6 audit) and the home
    // surface avoids surfaces-with-fill because a filled card
    // on a sky background reads as a "thing to look at", not
    // a "thing to tap". A 1dp border with the secondary
    // text colour is enough to give the cell a hit target
    // without making it the loudest thing on the screen.
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .border(
                width = 1.dp,
                color = sky.textSecondary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {},
            )
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            // v0.40.1: the home doors now use the same Lora
            // voice as the support DBT skill titles. The home
            // needs cards ("Be heard", "A moment", "Check in",
            // "Get through this") are the soft content of the
            // home surface — they describe what the user
            // needs, not what the system is doing — so the
            // serif treatment tells the reader "this is for
            // you, the rest of the screen is the chrome."
            // v0.39.0 left the doors in the sans system voice,
            // which read as "options" rather than "invitations."
            // 16sp Medium is the right weight for a 2×2 cell:
            // 18sp (the support DBT skill size) was visually
            // heavier than the cell could hold.
            style = SoftContent.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            ),
            color = sky.textPrimary,
        )
        Text(
            text = stringResource(captionRes),
            // v0.37.1: cap the caption at 3 lines with
            // ellipsis so the four cells land at a uniform
            // height. v0.35.0 had uncapped captions that
            // produced 2/3/4-line cells and a 2×2 with
            // mismatched row heights. Truncation is safer
            // than widening the cells: the door label is the
            // first line ("Be heard", etc.), and the
            // second/third lines are the only thing the user
            // loses — they can always long-press or read
            // more in the support hub.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
