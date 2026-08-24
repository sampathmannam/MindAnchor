package org.mindanchor.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.model.MomentStore

/**
 * The "What your check-ins show" surface.
 *
 * A single [Composable] that:
 *  1. Reads the user's [org.mindanchor.model.Moment]s
 *     from [MomentStore].
 *  2. Calls [CheckInPatterns.compute] to derive the
 *     four [Insight]s.
 *  3. Maps each insight to a string-resource
 *     (the wording is the clinical-review surface;
 *     the engine is not).
 *
 * The section is hidden when the "Ask me how I am"
 * toggle is off — the data isn't being collected,
 * so patterns would be misleading. The empty
 * state is a single line, never a "you have no
 * data" wall.
 *
 * The wording follows the project's N-of-1,
 * validate-then-suggest framing: descriptive,
 * never directive, never compared to a norm. The
 * copy is in strings.xml so a wording pass that
 * drifts toward judgement is caught at review
 * time.
 */
@Composable
fun CheckInInsightsSection(
    momentStore: MomentStore,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isEnabled) return

    val moments by momentStore.moments.collectAsState(initial = emptyList())
    val insights = remember(moments) {
        CheckInPatterns.compute(moments)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (insights.isEmpty()) {
            Text(
                text = stringResource(R.string.check_in_insights_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        for (insight in insights) {
            Text(
                text = insight.toWording(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Maps an [Insight] to its user-facing string.
 *
 * The wording is in strings.xml; this helper is
 * the only place that knows which string belongs
 * to which insight. A finding test (see
 * [org.mindanchor.insights.CheckInPatternsInsightStringsTest])
 * pins the mapping so a future insight that
 * forgets to register a string is caught at
 * review time.
 */
@Composable
private fun Insight.toWording(): String = when (this) {
    is Insight.RecentTrend -> when (direction) {
        TrendDirection.BRIGHTER -> stringResource(R.string.check_in_insights_trend_brighter)
        TrendDirection.ROUGHER -> stringResource(R.string.check_in_insights_trend_rougher)
        TrendDirection.SAME -> stringResource(R.string.check_in_insights_trend_same)
    }
    is Insight.BestHours -> stringResource(
        R.string.check_in_insights_best_hours,
        best.toLabel(),
        worst.toLabel(),
    )
    is Insight.Coverage -> stringResource(
        R.string.check_in_insights_coverage,
        answered,
        expected,
    )
    is Insight.VsBaseline -> when (direction) {
        TrendDirection.BRIGHTER -> stringResource(R.string.check_in_insights_baseline_brighter)
        TrendDirection.ROUGHER -> stringResource(R.string.check_in_insights_baseline_rougher)
        TrendDirection.SAME -> stringResource(R.string.check_in_insights_baseline_same)
    }
}

private fun PartOfDay.toLabel(): String = when (this) {
    PartOfDay.MORNING -> "morning"
    PartOfDay.AFTERNOON -> "afternoon"
    PartOfDay.EVENING -> "evening"
    PartOfDay.LATE_EVENING -> "late evening"
}
