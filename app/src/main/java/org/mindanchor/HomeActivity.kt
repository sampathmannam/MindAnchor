package org.mindanchor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.onboarding.OnboardingPrefs
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.ui.MindAnchorTheme

/**
 * The single HOME activity. As the default launcher this activity is the
 * anchor of the whole experience: calm by default, nothing animated,
 * nothing urgent. First launch shows goal-elicitation onboarding.
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingPrefs = OnboardingPrefs(applicationContext)
        setContent {
            MindAnchorTheme {
                val done by onboardingPrefs.done.collectAsState(initial = null)
                val scope = rememberCoroutineScope()
                when (done) {
                    null -> Unit // brief flicker-free wait for DataStore
                    false -> OnboardingScreen(
                        onDone = { goals ->
                            scope.launch { onboardingPrefs.complete(goals) }
                        },
                    )

                    true -> LauncherRoot()
                }
            }
        }
    }
}
