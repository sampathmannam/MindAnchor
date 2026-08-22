/*
 * v0.35.1 — setup wizard host activity.
 *
 * A single Compose surface that hosts the 5 setup steps + welcome
 * + done. The Activity is non-exported — no other app can launch
 * it. Cold-start of HomeActivity reads
 * `SetupPrefs.wizardCompleted` and `SetupPrefs.userDismissedWizard`
 * to decide whether to launch this activity.
 *
 * Back behaviour is owned by the Activity, not by a `BackHandler`
 * inside a Composable, because the back behaviour for the wizard
 * differs from screen to screen:
 *
 *   * From WELCOME: back-press sets `userDismissedWizard = true`
 *     and finishes. Equivalent to "Skip setup".
 *   * From any other step: back-press goes to the previous step.
 *   * From DONE: back-press is equivalent to the system-finishes-
 *     the-activity behaviour (DONE is the last screen).
 */
package org.mindanchor.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mindanchor.onboarding.steps.DoneStep
import org.mindanchor.onboarding.steps.HealthConnectStep
import org.mindanchor.onboarding.steps.PairWatchStep
import org.mindanchor.onboarding.steps.PolarStep
import org.mindanchor.onboarding.steps.PpgStep
import org.mindanchor.onboarding.steps.WelcomeStep
import org.mindanchor.ui.MindAnchorTheme

class SetupWizardActivity : ComponentActivity() {

    private val viewModel: SetupWizardViewModel by viewModels {
        SetupWizardViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindAnchorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WizardRoot(viewModel = viewModel, onFinished = { finish() })
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardRoot(
    viewModel: SetupWizardViewModel,
    onFinished: () -> Unit,
) {
    val step by viewModel.currentStep.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    when (step) {
        SetupStep.WELCOME -> WelcomeStep(
            onContinue = { viewModel.advance() },
            // v0.35.1: system back from the welcome step
            // dismisses the wizard. Same shape as `complete` —
            // we await the DataStore write before finishing so
            // HomeActivity's read on the next composition sees
            // the dismissed flag and does not re-launch the
            // wizard.
            onBack = {
                scope.launch {
                    viewModel.dismiss()
                    onFinished()
                }
            },
        )

        SetupStep.HEALTH_CONNECT -> HealthConnectStep(
            onSkip = { viewModel.skip(SetupStep.HEALTH_CONNECT) },
            onDone = { viewModel.advance() },
        )

        SetupStep.PAIR_WATCH -> PairWatchStep(
            onSkip = { viewModel.skip(SetupStep.PAIR_WATCH) },
            onDone = { viewModel.advance() },
        )

        SetupStep.POLAR -> PolarStep(
            onSkip = { viewModel.skip(SetupStep.POLAR) },
            onDone = { viewModel.advance() },
        )

        SetupStep.PPG -> PpgStep(
            onSkip = { viewModel.skip(SetupStep.PPG) },
            onDone = { viewModel.advance() },
        )

        SetupStep.DONE -> DoneStep(
            onFinish = {
                // Await the DataStore write before finishing. The
                // underlying HomeActivity re-collects
                // `wizardCompleted` from the same DataStore on
                // its next composition, and a fire-and-forget
                // coroutine here would race with that read —
                // HomeActivity would see `wizardCompleted = false`
                // and re-launch the wizard. Awaiting makes the
                // ordering explicit.
                scope.launch {
                    viewModel.complete()
                    onFinished()
                }
            },
        )
    }
}
