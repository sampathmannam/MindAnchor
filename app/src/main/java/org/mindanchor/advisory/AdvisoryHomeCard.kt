package org.mindanchor.advisory

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import org.mindanchor.R
import org.mindanchor.data.db.AdvisoryOpportunityEntity

/**
 * Program 3 Task 5 — an ordinary, dismissible card. Never a dialog,
 * overlay, or full-screen takeover, and never opened by the app itself.
 *
 * @wording-reviewed — clinical-review-required, see docs/CLINICAL_REVIEW.md.
 * Every string here reports a finalized historical fact and its date;
 * none may claim anything about the person's current state.
 */
@Suppress("FunctionNaming")
@Composable
fun AdvisoryHomeCard(
    opportunity: AdvisoryOpportunityEntity,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation()) {
        Text(
            text = stringResource(R.string.advisory_historical_title),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Text(
            text = opportunity.sourceExplanation,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.advisory_recorded_date, opportunity.sourceLocalDate),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.advisory_finalized_as_of, formatAdvisoryInstant(opportunity.sourceAsOfTime)),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.advisory_open)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.advisory_dismiss)) }
        }
    }
}

/** Same locale-aware shape as [org.mindanchor.launcher.noteTimeText]: same-day time, else a short date. */
internal fun formatAdvisoryInstant(epochMillis: Long): String {
    val date = Date(epochMillis)
    val now = Date()
    val calendar = java.util.Calendar.getInstance().apply { time = date }
    val nowCalendar = java.util.Calendar.getInstance().apply { time = now }
    val sameDay = calendar.get(java.util.Calendar.YEAR) == nowCalendar.get(java.util.Calendar.YEAR) &&
        calendar.get(java.util.Calendar.DAY_OF_YEAR) == nowCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(date) +
            " " + DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    }
}
