package org.mindanchor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.mindanchor.journal.JournalRoot
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

    /**
     * v0.44.0: the flash signal from a fired
     * reminder. When the [ReminderReceiver] fires, it
     * writes the note id to
     * [org.mindanchor.note.FlashSignal]. The launcher
     * root collects the flow and shows a full-screen
     * flash. We also read the EXTRA_FLASH_NOTE_ID
     * from the launching intent — when the user taps
     * the reminder notification, the activity is
     * started (or resumed) with the extra, and we
     * propagate it into the flash signal so the
     * flash plays.
     */
    private val flashSignal get() = org.mindanchor.note.FlashSignal.event

    companion object {
        /**
         * v0.44.0: the Intent extra that
         * [org.mindanchor.note.ReminderReceiver] puts on
         * the notification's content intent. When the
         * user taps the notification, HomeActivity
         * reads the extra and forwards the note id to
         * the flash signal.
         */
        const val EXTRA_FLASH_NOTE_ID = "org.mindanchor.extra.FLASH_NOTE_ID"
    }

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
        // v0.35.1: the setup wizard for data sources. Runs on
        // cold start if the goal-elicitation onboarding is done
        // and the data-source wizard is not. The wizard is
        // re-runnable from Settings at any time regardless of
        // this flag.
        val setupPrefs = org.mindanchor.onboarding.SetupPrefs(applicationContext)
        // v0.25.2-A (Task 8): if the activity was cold-launched from a
        // letter notification, the letter_date extra is on the launching
        // intent. setIntent(intent) is implicit (the activity does it for
        // itself in onCreate); we just read the extra and push it into
        // the flow before the launcher root composes for the first time.
        handleLetterIntent(intent)
        // v0.44.0: a cold launch from the reminder
        // notification's content intent also carries
        // the note id. Forward to the flash signal
        // so the home surface plays the flash on
        // first composition.
        handleFlashIntent(intent)
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
                    // v0.25.17 BUG-004: lifecycle-aware collect.
                    // The three activity-level flows
                    // (`onboardingPrefs.done`, `goHomeSignal`,
                    // `letterDateSignal`) are read on every
                    // composition; pre-v0.25.17 they kept
                    // collecting on every emission even when
                    // the activity was STOPPED.
                    val done by onboardingPrefs.done.collectAsStateWithLifecycle(initialValue = null)
                    val goHome by goHomeSignal.collectAsStateWithLifecycle()
                    val letterDate by letterDateSignal.collectAsStateWithLifecycle()
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

                        true -> {
                            // v0.35.1: route to the data-source
                            // setup wizard on first cold start
                            // after the goal-elicitation onboarding
                            // is done. The wizard finishes itself,
                            // the user lands on the home, and the
                            // home's DataSourcesCard shows the per-
                            // source state.
                            // initialValue = true: the home
                            // shows on the first composition,
                            // before the DataStore has answered.
                            // The DataStore answer comes a moment
                            // later, and if the wizard has not
                            // been completed or dismissed, the
                            // second composition launches it.
                            // Setting the initial value to false
                            // would launch the wizard on the
                            // first composition AND every
                            // subsequent cold start that happens
                            // to race the DataStore — the wizard
                            // would re-open for a user who has
                            // already completed it.
                            val setupCompleted by setupPrefs.wizardCompleted.collectAsStateWithLifecycle(initialValue = true)
                            val setupDismissed by setupPrefs.userDismissedWizard.collectAsStateWithLifecycle(initialValue = true)
                            if (!setupCompleted && !setupDismissed) {
                                val wizardIntent = android.content.Intent(
                                    this@HomeActivity,
                                    org.mindanchor.onboarding.SetupWizardActivity::class.java,
                                )
                                // No `finish()` here — the wizard
                                // sits on top of HomeActivity in the
                                // back stack, and when the user
                                // finishes the wizard they should
                                // pop back to the home, not to the
                                // launcher.
                                startActivity(wizardIntent)
                            } else {
                                // v0.44.0: collect the flash signal
                                // so the home surface can show a
                                // full-screen flash when a reminder
                                // fires. The flow is read in
                                // collectAsStateWithLifecycle so a
                                // backgrounded activity does not
                                // collect on every emission (BUG-004
                                // pattern). The activity is
                                // foreground when the flash plays.
                                val flashEvent by flashSignal.collectAsStateWithLifecycle()
                                // v0.63.0: the journal home replaces the
                                // v0.62.7 launcher tree. The launcher
                                // (app drawer, favourites, friction
                                // gate, etc.) is preserved in code as
                                // LauncherRoot for rollback, but the
                                // primary home surface is now the
                                // paper-texture journal from the
                                // superdesign "warmer journal"
                                // direction (b35ee64d, b446ae65,
                                // 5088ef9e, c01a4b03, 4cf0a48a).
                                JournalRoot()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * v0.44.0: mark the current flash event as
     * consumed. Called from the home surface when
     * the user taps the flash to dismiss it, or
     * from a `LaunchedEffect` after a 5-second
     * auto-clear. Without this, a configuration
     * change would re-trigger the same flash.
     */
    private fun consumeFlash() {
        org.mindanchor.note.FlashSignal.consume()
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
        // v0.44.0: the notification's content intent
        // carries EXTRA_FLASH_NOTE_ID for the fired
        // reminder. Forward the id to the flash
        // signal so the launcher root shows the
        // flash on the next composition.
        handleFlashIntent(intent)
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
     * v0.44.0: read the [EXTRA_FLASH_NOTE_ID] extra from the
     * launching / `onNewIntent` intent and forward it to
     * [org.mindanchor.note.FlashSignal] so the launcher root shows
     * the flash. Called from both `onCreate` (cold launch from
     * the notification's content intent) and `onNewIntent`
     * (singleTask — second tap on the notification while the
     * activity is foreground). No-op when the extra is missing or
     * unparseable — any of those is an intent the launcher should
     * not act on.
     */
    private fun handleFlashIntent(intent: Intent?) {
        if (intent == null) return
        val noteId = intent.getLongExtra(EXTRA_FLASH_NOTE_ID, -1L)
        if (noteId <= 0L) return
        org.mindanchor.note.FlashSignal.fire(noteId)
        // Clear the extra so a config change does not
        // re-trigger the same flash. The notification's
        // content intent is single-fire; the flash
        // signal is the runtime path.
        intent.removeExtra(EXTRA_FLASH_NOTE_ID)
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
