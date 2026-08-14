package org.mindanchor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.backup.BackupScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.SessionManager
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.letters.LetterScheduler
import org.mindanchor.onboarding.OnboardingPrefs
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.sunset.SunsetController
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.HapticFeedbackGateProvider
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

    /**
     * v0.25.2-A (Task 8): the letter-notification side-channel. When the
     * user taps a letter notification, the intent carries a
     * `letter_date` extra (ISO local date string). The activity writes
     * it here, the launcher root collects it, navigates to the letter
     * reader, and signals back via [consumeLetterDate] so the value
     * is cleared. Without the reset, a configuration change would
     * re-trigger the same navigation.
     *
     * Same shape as [goHomeSignal] — an activity-owned flow the
     * launcher root reads on every recomposition. The counter pattern
     * does not work for a value (a tap for the same date twice would
     * not re-emit), so this is a nullable value, not a counter, and
     * the launcher root clears it on consumption.
     */
    private val letterDateSignal = MutableStateFlow<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // v0.25.6+ WP-1: the on-write Drive backup
        // trigger was unwired before this call. The
        // scheduler's startIfNeeded is idempotent —
        // a second call (e.g. after a config change
        // re-creates the activity) is a no-op. The
        // trigger's own collectors are a no-op
        // when both auto-sync toggles are off, so a
        // user who has never opted in pays only the
        // cost of one DataStore read per notes /
        // letters emission.
        BackupScheduler.startIfNeeded(applicationContext)
        // v0.25.7+ WP-2: re-arm the retry worker on
        // cold start if the queue is non-empty. The
        // schedule is event-driven (kicked on every
        // enqueue), so a process death between an
        // enqueue and the schedule call would have
        // left the entry stranded until the user
        // wrote something else. The startup
        // rehydration is the recovery path. Same
        // idempotent semantics as the in-flight
        // enqueueIfNeeded: a second call while the
        // worker is running is a no-op.
        //
        // v0.25.9: also seed the NotesPrefs id
        // generator from the max existing id on
        // disk. The seed is idempotent (the second
        // call is a no-op) and runs in a coroutine,
        // so the first `nextNoteId()` call from the
        // home card or the full activity is fast
        // (no `runBlocking` on the main thread).
        // Without this seed, a note written before
        // the first `nextNoteId()` call would get
        // an id below the on-disk max — the
        // pre-v0.25.9 bug shape.
        lifecycleScope.launch {
            val pending = org.mindanchor.backup.BackupPrefs(applicationContext)
                .pendingBackups.first()
            if (pending.isNotEmpty()) {
                org.mindanchor.backup.BackupRetryWorker.enqueueIfNeeded(applicationContext)
            }
            NotesPrefs.seedFromDiskIfNeeded(applicationContext)
        }
        val onboardingPrefs = OnboardingPrefs(applicationContext)
        val sunsetPrefs = SunsetPrefs(applicationContext)
        // v0.25.2-A (Task 8): if the activity was cold-launched from a
        // letter notification, the letter_date extra is on the launching
        // intent. setIntent(intent) is implicit (the activity does it for
        // itself in onCreate); we just read the extra and push it into
        // the flow before the launcher root composes for the first time.
        handleLetterIntent(intent)
        setContent {
            MindAnchorTheme {
                // v0.25.16 BUG-013: wrap the entire launcher tree
                // in the HapticFeedbackGateProvider so the four
                // haptics call sites (HomeScreen save / clear,
                // NoteScreen delete-confirm, LetterInbox
                // delete-confirm, FrictionGate breath pause)
                // consult the system haptics toggle and the
                // "remove animations" a11y preference before
                // firing. Without the provider, those four
                // sites would call LocalHapticFeedback directly
                // and bypass the system settings.
                HapticFeedbackGateProvider {
                    val done by onboardingPrefs.done.collectAsState(initial = null)
                    val goHome by goHomeSignal.collectAsState()
                    val letterDate by letterDateSignal.collectAsState()
                    val scope = rememberCoroutineScope()
                    when (done) {
                        // Preferences are still loading. Draw the sky rather than
                        // nothing at all: an empty frame here let the window
                        // background flash through on every cold start.
                        null -> CalmBackground { }

                        false -> OnboardingScreen(
                            onDone = { goals, chronotype ->
                                scope.launch {
                                    onboardingPrefs.complete(goals)
                                    // setChronotype only writes the default
                                    // window if the user has not already picked
                                    // one — first run, the window has never
                                    // been touched, so the chronotype's default
                                    // becomes the launcher's default.
                                    sunsetPrefs.setChronotype(chronotype)
                                    if (sunsetPrefs.isEnabled()) {
                                        SunsetController.ensureScheduled(applicationContext)
                                    }
                                }
                            },
                        )

                        true -> LauncherRoot(
                            goHomeSignal = goHome,
                            letterDateSignal = letterDate,
                            onLetterDateConsumed = ::consumeLetterDate,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        goHomeSignal.value += 1
        // v0.25.2-A (Task 8): singleTask means a second tap on the
        // letter notification delivers a new intent while the
        // activity is already foreground. Route it the same way
        // as the cold-launch path; the launcher root will navigate
        // to the reader for the new date.
        handleLetterIntent(intent)
    }

    /**
     * v0.25.2-A (Task 8): read the [LetterScheduler.ACTION_OPEN_LETTER]
     * intent's `letter_date` extra and push it into [letterDateSignal].
     * No-op when the intent is missing, the action is wrong, the
     * extra is missing, or the date string is unparseable — any of
     * those is an intent the launcher should not act on.
     */
    private fun handleLetterIntent(intent: Intent?) {
        if (intent?.action != LetterScheduler.ACTION_OPEN_LETTER) return
        val raw = intent.getStringExtra("letter_date") ?: return
        val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return
        letterDateSignal.value = date
    }

    /**
     * v0.25.2-A (Task 8): clear [letterDateSignal] after the launcher
     * root has navigated. Called from a `LaunchedEffect` in
     * [LauncherRoot] when the new date is applied. Without this,
     * a configuration change that recomposes the launcher would
     * re-trigger the same navigation — same date, same reader.
     */
    private fun consumeLetterDate() {
        letterDateSignal.value = null
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
