package org.mindanchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import org.mindanchor.R
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.rememberClockFormat
import org.mindanchor.ui.rememberMinuteTick

@Composable
fun NowWhatShell(onWantSleep: () -> Unit, onWantGround: () -> Unit, onWantTalk: () -> Unit) {
    val now = rememberMinuteTick()
    val pattern = rememberClockFormat()
    val clock = DateTimeFormatter.ofPattern(pattern).format(now)
    CalmBackground { sky ->
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)
                .semantics(mergeDescendants = false) { contentDescription = "It's late. What do you need right now?" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(clock, style = MaterialTheme.typography.displayLarge, color = sky.textPrimary)
            Text(stringResource(R.string.now_what_title), style = MaterialTheme.typography.titleLarge, color = sky.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.now_what_subtitle), style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary, textAlign = TextAlign.Center)
            NowWhatRow(stringResource(R.string.now_what_sleep), sky, onWantSleep)
            NowWhatRow(stringResource(R.string.now_what_ground), sky, onWantGround)
            NowWhatRow(stringResource(R.string.now_what_talk), sky, onWantTalk)
        }
    }
}

@Composable private fun NowWhatRow(label: String, sky: SkyContent, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = sky.textPrimary)
            }
        }
    }
}

object NowWhatHeuristic {
    const val QUIET_START_HOUR = 0
    const val QUIET_END_HOUR = 5
    fun shouldShow(currentHour: Int, okAtNight: Boolean): Boolean {
        if (okAtNight) return false
        return currentHour in QUIET_START_HOUR..QUIET_END_HOUR
    }
}
