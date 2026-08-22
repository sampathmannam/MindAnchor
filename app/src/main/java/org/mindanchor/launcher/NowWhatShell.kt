@file:Suppress("MaxLineLength", "FunctionNaming", "WildcardImport", "MagicNumber")
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
fun NowWhatShell(
    onWantSleep: () -> Unit,
    onWantGround: () -> Unit,
    onWantTalk: () -> Unit,
    // v0.26.5: opt-in escape hatch. The 3 main options above are
    // for "right now" — the user picks one and the shell
    // disappears. "I'm up late tonight" is different: it changes
    // the heuristic itself (sets `okAtNight = true` in BpdProfile
    // via DataStore) so the 2am shell stops showing on subsequent
    // compositions for the rest of the night and on future nights.
    // To revert, the user goes to Settings → PAUSES → BPD
    // profile → uncheck "I'm OK at night".
    onStayUp: () -> Unit,
) {
    val now = rememberMinuteTick()
    val pattern = rememberClockFormat()
    val clock = DateTimeFormatter.ofPattern(pattern).format(now)
    val a11y = stringResource(R.string.now_what_a11y)
    CalmBackground { sky ->
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)
                .semantics(mergeDescendants = false) { contentDescription = a11y },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(clock, style = MaterialTheme.typography.displayLarge, color = sky.textPrimary)
            Text(stringResource(R.string.now_what_title), style = MaterialTheme.typography.titleLarge, color = sky.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.now_what_subtitle), style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary, textAlign = TextAlign.Center)
            NowWhatRow(stringResource(R.string.now_what_sleep), sky, onWantSleep)
            NowWhatRow(stringResource(R.string.now_what_ground), sky, onWantGround)
            NowWhatRow(stringResource(R.string.now_what_talk), sky, onWantTalk)
            // v0.26.5: 4th option — visually distinct (plain
            // TextButton, smaller weight) so it doesn't compete
            // with the 3 main "what do I need" options. The
            // semantics role is Button (default for TextButton)
            // and the heightIn(min = 48.dp) keeps the tap target
            // a11y-compliant.
            TextButton(
                onClick = onStayUp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.Button },
            ) {
                Text(
                    stringResource(R.string.now_what_stay_up),
                    style = MaterialTheme.typography.bodyLarge,
                    color = sky.textSecondary,
                )
            }
        }
    }
}

@Composable private fun NowWhatRow(label: String, sky: SkyContent, onClick: () -> Unit) {
    // v0.25.12 fix: removed the wrapping `Box(modifier = Modifier.fillMaxSize(), …)`.
    // In a Column with `fillMaxSize`, a child Box with `fillMaxSize` requests
    // the full remaining column height, which collapses the next two NowWhatRow
    // siblings to 0 height and pushes them off-screen. The TextButton already
    // has Alignment.CenterStart for its own content, so the Box was redundant.
    // The two onClicks (Surface + TextButton) are not a double-fire bug: the
    // TextButton consumes the click event before the Surface's onClick can fire,
    // because the TextButton covers the entire Surface area.
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = sky.textPrimary)
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
