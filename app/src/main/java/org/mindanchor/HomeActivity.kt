package org.mindanchor

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.continuity.RestoreCoordinator
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.SessionManager
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.letters.LetterScheduler
import org.mindanchor.onboarding.OnboardingPrefs
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.sunset.SunsetController
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.MindAnchorTheme
import org.mindanchor.update.UpdateChecker
// UpdateInfo removed in v0.30+ (security audit
// 2026-08-24); the auto-update flow is gone.

import org.mindanchor.update.UpdatePrefs

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
     * v0.30+ (security audit 2026-08-24) — the
     * GitHub Releases check was removed; the
     * `availableUpdate` flow is preserved as a
     * no-op `StateFlow` (always null) so the
     * home-surface plumbing compiles. The
     * "Check for updates" affordance now opens
     * the releases page in the browser via
     * [UpdateChecker.openReleasesPage].
     */
    private val availableUpdate: kotlinx.coroutines.flow.MutableStateFlow<Any?> =
        kotlinx.coroutines.flow.MutableStateFlow<Any?>(null).also {
            // v0.30+: never emits a non-null value.
            // The previous `check()` call site is
            // removed; the StateFlow is kept as a
            // no-op so the home surface can compile
            // without further changes.
        }

    /**
     * v0.25.9 (deployability §8.3): whether the user has
     * set MindAnchor as the default home. The Flow starts
     * at a safe default and is populated in onCreate (and
     * refreshed on resume). The home surface shows a callout
     * while this is false.
     *
     * v0.25.20 (Android 17 crash fix): cannot call
     * getSystemService() from a property initializer
     * because System services are not available to
     * Activities before onCreate(). The pre-fix line
     * `MutableStateFlow(computeIsDefaultHome())` ran during
     * <init>, which is too early. We now seed with `true`
     * and overwrite inside onCreate. The first frame may
     * briefly show the "not default" callout on a clean
     * install (worst case); onCreate runs before the first
     * composition in the Compose lifecycle, so the user
     * does not see a flash.
     */
    private val isDefaultHome = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDefaultHome.value = computeIsDefaultHome()
        enableEdgeToEdge()
        val onboardingPrefs = OnboardingPrefs(applicationContext)
        val sunsetPrefs = SunsetPrefs(applicationContext)
        // v0.25.2-A (Task 8): if the activity was cold-launched from a
        // letter notification, the letter_date extra is on the launching
        // intent. setIntent(intent) is implicit (the activity does it for
        // itself in onCreate); we just read the extra and push it into
        // the flow before the launcher root composes for the first time.
        handleLetterIntent(intent)
        // v0.25.9 (auto-update): kick off a silent GitHub releases
        // check. Best-effort, never blocks the launcher. The cached
        // result is consulted first; the network is only hit when
        // the cache is older than 24h.
        maybeRunUpdateCheck()
        // Task 10: re-arm the nightly continuity snapshot on every cold
        // start. Idempotent (unique work, REPLACE policy) and purely
        // local WorkManager scheduling — no network call is made here;
        // the worker itself gates on ContinuityPrefs.backupEnabled and
        // only runs once WorkManager's own CONNECTED constraint is met.
        // This is also the self-repair path for a process death between
        // a successful nightly upload and it rescheduling itself.
        ContinuityWorkScheduler.ensureNightlyScheduled(applicationContext)
        // Task 11: resume a staged-but-interrupted restore, local phase
        // only. Fire-and-forget, its own coroutine — see
        // RestoreCoordinator.resumeIfPending's KDoc: it makes zero
        // network/Drive calls (a no-op unless a restore was already
        // staged on THIS device), so this stays consistent with the
        // "startup never touches the network" boundary above.
        RestoreCoordinator.resumeIfPending(applicationContext)
        setContent {
            MindAnchorTheme {
                val done by onboardingPrefs.done.collectAsState(initial = null)
                val goHome by goHomeSignal.collectAsState()
                val letterDate by letterDateSignal.collectAsState()
                val update by availableUpdate.collectAsState()
                val defaultHome by isDefaultHome.collectAsState()
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
                        availableUpdate = update as? org.mindanchor.update.UpdateInfo,
                        isDefaultHome = defaultHome,
                        onUpdateAction = { openReleasesPage() },
                        onUpdateDismiss = { availableUpdate.value = null },
                        onSetDefaultHome = { openDefaultHomeSettings() },
                    )
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
        // v0.25.9 (deployability §8.3): refresh the default-home state.
        // The user may have toggled MindAnchor as the default home from
        // system Settings while the launcher was in the background.
        isDefaultHome.value = computeIsDefaultHome()
    }

    /**
     * v0.30+ (security audit 2026-08-24) — the
     * silent GitHub Releases check that shipped in
     * v0.25.9 was a privacy contract violation. The
     * [UpdateChecker.check] path was removed; the
     * "Check for updates" button in Settings → About
     * now opens the releases page in the browser
     * (see [UpdateChecker.openReleasesPage]).
     * [maybeRunUpdateCheck] is preserved as a
     * no-op so the existing call site
     * ([onCreate]) does not need to change.
     */
    private fun maybeRunUpdateCheck() {
        // v0.30+ no-op: the launcher no longer
        // checks for updates. The "Check for
        // updates" affordance in Settings → About
        // opens the releases page in the browser
        // (see [UpdateChecker.openReleasesPage]).
    }

    /**
     * v0.30+ (security audit 2026-08-24) — open the
     * release page in the system browser. The user
     * installs manually because this build is not
     * on Play Store yet — the sideload pattern is
     * the project's documented distribution for an
     * alpha cohort. Replaces the previous
     * [openUpdate(UpdateInfo)] method, which took a
     * GitHub response and opened the URL from there;
     * the new version opens the canonical
     * [UpdateChecker.RELEASES_URL] (no network call
     * by the launcher).
     */
    private fun openReleasesPage() {
        UpdateChecker(applicationContext).openReleasesPage()
    }

    /**
     * v0.25.9: open the system Default-Apps settings so the
     * user can switch the home to MindAnchor. The launcher
     * has no API to set itself as home; the user must do it
     * via the system settings.
     *
     * v0.25.11: the SDK_INT >= N guard is unnecessary at
     * minSdk=33; ACTION_HOME_SETTINGS has been around since
     * API 21. Inlined the action directly.
     */
    private fun openDefaultHomeSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        runCatching { startActivity(intent) }
    }

    /**
     * v0.25.11: the SDK_INT < Q guard was the pre-Q
     * `ROLE_HOME` workaround (RoleManager landed at Q).
     * With minSdk=33, every install runs on Q or later, so
     * the guard always falls through to the real check.
     * The deprecated fallback has been removed.
     */
    private fun computeIsDefaultHome(): Boolean {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            ?: return true
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }
}
