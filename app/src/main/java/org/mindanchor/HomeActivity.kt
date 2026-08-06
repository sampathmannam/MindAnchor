package org.mindanchor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.mindanchor.friction.SessionManager
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.onboarding.OnboardingPrefs
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.MindAnchorTheme

/**
 * The single HOME activity. As the default launcher this activity is the
 * anchor of the whole experience: calm by default, nothing animated,
 * nothing urgent. First launch shows goal-elicitation onboarding.
 */
class HomeActivity : ComponentActivity() {

    /**
     * Incremented on every home-button press. The activity is singleTask, so
     * pressing home while the launcher is already foreground delivers a new
     * intent rather than recreating anything — without this the launcher
     * would stay wherever the user left it (typically settings).
     */
    private val goHomeSignal = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingPrefs = OnboardingPrefs(applicationContext)
        setContent {
            MindAnchorTheme {
                val done by onboardingPrefs.done.collectAsState(initial = null)
                val goHome by goHomeSignal.collectAsState()
                val scope = rememberCoroutineScope()
                when (done) {
                    // Preferences are still loading. Draw the sky rather than
                    // nothing at all: an empty frame here let the window
                    // background flash through on every cold start.
                    null -> CalmBackground { }

                    false -> OnboardingScreen(
                        onDone = { goals ->
                            scope.launch { onboardingPrefs.complete(goals) }
                        },
                    )

                    true -> LauncherRoot(goHomeSignal = goHome)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        goHomeSignal.value += 1
    }

    /**
     * Being back at the launcher means any timed app has been left, so its
     * session stops counting. Someone who got what they came for and left
     * should not be chimed at later about an app they already closed.
     */
    override fun onResume() {
        super.onResume()
        SessionManager.onReturnedHome(applicationContext)
    }
}
