@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.onboarding.OnboardingPrefs
import org.mindanchor.onboarding.inRecapWindowPure
import java.time.LocalDate

/**
 * v0.25.11 (B-recap-ui-surface): the public UI surface for
 * the 14-day onboarding recap.
 *
 * v0.25.5 WP-E shipped the data layer
 * ([org.mindanchor.onboarding.OnboardingPrefs.installDay],
 * [org.mindanchor.onboarding.OnboardingPrefs.recapSeenDay],
 * [org.mindanchor.onboarding.inRecapWindowPure],
 * [org.mindanchor.onboarding.OnboardingPrefs.markRecapSeen])
 * and the pure-function test surface
 * ([org.mindanchor.onboarding.OnboardingRecapWindowFindingTest]),
 * but no Composable ever called any of them. The user never
 * saw the recap. The fix is this Composable: a banner that
 * reads the install day + recap-seen day from
 * [OnboardingPrefs], checks whether the user is in a 14-day
 * recap window via the pure
 * [org.mindanchor.onboarding.inRecapWindowPure] function, and
 * calls [OnboardingPrefs.markRecapSeen] when the user
 * dismisses the banner.
 *
 * The Composable is a *banner*, not a *screen* — the WP-E
 * brief is "the user never sees the recap", and a banner on
 * the home surface is the lowest-friction way to make the
 * data layer reachable. A future WP can promote the banner
 * to a full recap screen without changing the data layer.
 *
 * The Composable is self-contained: it takes no parameters
 * because the [LocalContext] gives it the [OnboardingPrefs]
 * it needs. HomeActivity / SettingsActivity can drop the
 * banner anywhere on their existing surface; the banner
 * shows nothing when the user is not in a window.
 */
@Suppress("FunctionNaming")
@Composable
fun RecapBanner(
    onDismissed: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember(context) { OnboardingPrefs(context.applicationContext) }
    val installDay by prefs.installDay.collectAsState(initial = null)
    val recapSeenDay by prefs.recapSeenDay.collectAsState(initial = null)
    val today = remember { mutableStateOf(LocalDate.now()) }
    val scope = rememberCoroutineScope()

    // Re-evaluate the window predicate on every recomposition.
    // The pure function is a cheap `LocalDate -> Boolean`; the
    // recomposition cost is the cost of a 3-line predicate.
    val inWindow = inRecapWindowPure(
        installDay = installDay,
        recapSeenDay = recapSeenDay,
        today = today.value,
    )

    if (!inWindow) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.recap_banner_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.recap_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = {
                    scope.launch {
                        prefs.markRecapSeen(today.value)
                        onDismissed()
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.recap_banner_dismiss))
            }
        }
    }

    // Refresh `today` at composition start so a banner that
    // was rendered yesterday and survives a config change
    // re-evaluates against the new wall-clock day. The
    // [LaunchedEffect] key is the LocalDate itself, so the
    // effect re-runs only on day boundaries, not on every
    // recomposition.
    LaunchedEffect(today.value) {
        val now = LocalDate.now()
        if (now != today.value) {
            today.value = now
        }
    }
}
