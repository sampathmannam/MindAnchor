package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import org.mindanchor.R
import org.mindanchor.data.db.AnchorDatabase

/**
 * The home-screen "notification diet" card.
 *
 * Reports the number of held-notification releases over
 * the trailing 7 days, multiplied by the Mark 2005
 * 23-minute interruption-recovery cost. The wording is
 * factual, not celebratory:
 *
 *  - "released" is the same word the digest UI uses;
 *  - "estimated" frames the Mark 2005 figure as a
 *    heuristic, not a fact;
 *  - the footnote cites the paper so the user can verify
 *    the 23-minute number rather than treat it as gospel.
 *
 * Evidence anchor (in the footnote string):
 *
 *  - Mark, G., Gonzalez, V. M., & Harris, J. (2005). No
 *    task left behind? Examining the nature of fragmented
 *    work. In *Proceedings of the SIGCHI Conference on
 *    Human Factors in Computing Systems* (pp. 321–330).
 *    DOI 10.1145/1054972.1055017. The 23-minute figure is
 *    the average recovery time for knowledge workers
 *    after a notification interruption.
 *
 * v0.26+ (Phase 1 G-20).
 *
 * @param dao the [AnchorDatabase.heldNotifications] DAO; the
 *   `releasedCountSince(since)` query is the only call the
 *   card makes. A new Flow is read on every recomposition
 *   (cheap — the underlying query is a single COUNT).
 * @param showIfEmpty when false, the card returns without
 *   rendering when the count is zero; the home surface
 *   uses `false` so a fresh install does not show a
 *   "0 released" card. The setting is the user's
 *   "I am in control of my attention" signal — never
 *   pre-fill it with zeros.
 */
@Composable
fun HomeDietCard(
    dao: org.mindanchor.data.db.HeldNotificationDao,
    showIfEmpty: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val since = now - TimeUnit.DAYS.toMillis(7)
    val releasedCountFlow = remember(dao, since) { dao.releasedCountSince(since) }
    HomeDietCardContent(
        releasedCountFlow = releasedCountFlow,
        showIfEmpty = showIfEmpty,
        modifier = modifier,
    )
}

/**
 * The composable half of the diet card. Split out so
 * the Flow is read once (in the wrapped function) and
 * the rendering is testable as a pure Composable.
 */
@Composable
private fun HomeDietCardContent(
    releasedCountFlow: Flow<Int>,
    showIfEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    val released by releasedCountFlow.collectAsState(initial = 0)
    if (released <= 0 && !showIfEmpty) return
    val savedHours = released * 23.0 / 60.0
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.diet_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.diet_card_demoted, released),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.diet_card_saved, savedHours),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = stringResource(R.string.diet_card_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
