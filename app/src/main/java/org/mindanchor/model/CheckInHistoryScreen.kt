package org.mindanchor.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The v0.20.1 round 5 follow-up check-in history
 * view.
 *
 * ## Why a list view, not a chart
 *
 * The brief is explicit: the user owns the words.
 * A chart implies an interpretation (trend,
 * average, "concerning pattern") that the project
 * is not allowed to make. A list view lets the
 * user scroll back through their own check-ins,
 * see the rating and reflection at each moment,
 * and form their own impression.
 *
 * The list is in *append-order* (newest at the
 * bottom). The launcher does *not* sort by rating
 * or by reflection length. The user sees the
 * chronological record, the same way a diary does.
 *
 * ## Why a single line per check-in
 *
 * The screen is a glance-back surface, not an
 * analytics dashboard. One line per check-in:
 * date, time, rating, reflection (if any). The
 * user can re-read at their own pace.
 *
 * ## Why no "edit" or "delete" affordance
 *
 * Check-ins are append-only. The launcher does
 * not let the user rewrite their past â€” that is
 * the same engagement-analytics trap as a
 * "mood log." Past ratings are past. New ratings
 * are new.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CheckInHistoryScreen(
    checkIns: CheckInState,
    onClose: () -> Unit,
) {
    val sorted = checkIns.checkIns.sortedBy { it.atMillis }
    val dateFormat = remember {
        // Locale.getDefault is fine here: the
        // launcher is single-user, single-locale.
        // A multilingual user would see their
        // device's date format, which is what
        // they expect.
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.check_in_history_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.semantics {
                            contentDescription = "Close"
                        },
                    ) {
                        Text(
                            text = "â†",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )

            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.check_in_history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // v0.20.1 round 5 follow-up: scroll
                // to the newest check-in on first
                // open. The list is oldest-at-top
                // (chronological diary pattern) but
                // the user opens the screen to see
                // what they just did, not the oldest
                // entry from weeks ago.
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.runtime.LaunchedEffect(sorted.size) {
                    if (sorted.isNotEmpty()) {
                        listState.scrollToItem(sorted.size - 1)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    // The key must be unique across the
                    // list. atMillis is not â€” two
                    // accepted check-ins in the same
                    // millisecond (rare but possible
                    // across two CheckInActivity
                    // launches that land on the same
                    // System.currentTimeMillis()) would
                    // collide and trigger a
                    // IllegalArgumentException from
                    // LazyColumn. The list index, paired
                    // with the atMillis, is a strong-enough
                    // key: re-orderings in the source list
                    // would change the index, but this
                    // screen is append-only (oldest first,
                    // never re-sorted), so the (atMillis,
                    // index) tuple is stable.
                    itemsIndexed(
                        sorted,
                        key = { index, ci -> "${ci.atMillis}-$index" },
                    ) { _, checkIn ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dateFormat.format(Date(checkIn.atMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // The rating is the single
                                // 1-5 number, with the
                                // user-language anchor in
                                // parentheses. The launcher
                                // does *not* colour-code
                                // ratings â€” colour is a
                                // judgement the project is
                                // not allowed to make.
                                Text(
                                    text = "${checkIn.rating}/5",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (checkIn.reflection.isNotBlank()) {
                                Text(
                                    text = checkIn.reflection,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
