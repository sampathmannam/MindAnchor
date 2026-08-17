@file:Suppress("FunctionNaming")
package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.ui.SkyContent

/**
 * v0.33.0 / v0.35.0: the "Get through this" sub-menu. Replaces
 * the v0.32.x "Right now" home section with a half-screen sheet
 * that surfaces three reflective actions, in the order a person
 * mid-dysregulation is most likely to want them:
 *
 *   1. **What just happened?** — five short fields, ~90 seconds.
 *      The IFS unblend work: name what is happening so the
 *      feeling is not the only thing in the room.
 *   2. **Which part is loud?** — the IFS picker chip grid. Pick
 *      the part. No analysis, no save unless you ask.
 *   3. **Export for my therapist** — a one-time JSON the user
 *      can hand over. No upload, no email. The "let me show
 *      this to someone who can help" affordance.
 *
 * The sub-menu is invoked from the needs card's 4th door
 * (Get through this). It is not a separate activity — the
 * launcher renders it as a stacked surface the same way the
 * existing settings section and letter inbox are. The back
 * button on the sub-menu returns to the home, not to the
 * needs card, because the needs card is the entry point the
 * user came from.
 *
 * @wording-reviewed — clinical-review-required. The three
 * sub-actions are the user's only path to the chain-capture
 * surface, the IFS picker, and the export. The wording is
 * deliberately action-shaped ("What just happened?" not
 * "Reflect on what just happened") because the user mid-
 * crisis does not have the attention for the noun phrase.
 */
@Suppress("FunctionNaming", "LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GetThroughSubMenu(
    sky: SkyContent,
    onWhatHappened: () -> Unit,
    onWhichPart: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_get_through_title),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        SubMenuRow(
            sky = sky,
            titleRes = R.string.home_get_through_what_happened,
            captionRes = R.string.home_get_through_what_happened_caption,
            onClick = onWhatHappened,
        )
        SubMenuRow(
            sky = sky,
            titleRes = R.string.home_get_through_which_part,
            captionRes = R.string.home_get_through_which_part_caption,
            onClick = onWhichPart,
        )
        SubMenuRow(
            sky = sky,
            titleRes = R.string.home_get_through_export,
            captionRes = R.string.home_get_through_export_caption,
            onClick = onExport,
        )

        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { role = Role.Button },
        ) {
            Text(
                text = stringResource(R.string.action_back),
                color = sky.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubMenuRow(
    sky: SkyContent,
    titleRes: Int,
    captionRes: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = {})
            .semantics { role = Role.Button }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = sky.textPrimary,
        )
        Text(
            text = stringResource(captionRes),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
