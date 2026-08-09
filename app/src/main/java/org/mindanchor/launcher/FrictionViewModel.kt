package org.mindanchor.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.AppWatchService
import org.mindanchor.friction.CompassionStore
import org.mindanchor.friction.FrictionBandit
import org.mindanchor.friction.FrictionContext
import org.mindanchor.friction.FrictionTone
import org.mindanchor.friction.GateContext
import org.mindanchor.friction.SessionManager
import org.mindanchor.friction.SmallThings
import java.time.LocalDate
import java.time.LocalTime

/**
 * The friction-gate concerns, extracted from
 * [LauncherViewModel] as part of the senior-architect
 * review follow-up (item C of the SOTA-IMPROVEMENT-PLAN).
 *
 * The class is constructed by [LauncherViewModel] and
 * owned by it. The split is *behavior-preserving*: the
 * public surface of [LauncherViewModel] continues to
 * expose the same methods, and the implementations now
 * delegate to this class. The split is structural — a
 * future change to the friction flow touches this file
 * and not the 434-line [LauncherViewModel].
 *
 * The launch path is a callback because
 * [FrictionViewModel] does not own the app repository;
 * the [LauncherViewModel] does. The callback is the
 * "launch the actual app" step, which the friction
 * layer does not need to know about.
 *
 * @wording-reviewed — the user-visible wording of the
 * gate is the small-thing offer, the if-then plan, and
 * the compassion moment. The strings live in
 * strings.xml; the data flow is in this class.
 */
class FrictionViewModel(application: Application) : AndroidViewModel(application) {

    private val frictionPrefs = FrictionPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)

    /**
     * Launch callback. The friction layer needs to
     * start the activity, but it does not own the
     * repository. The caller injects a function that
     * returns true on a successful launch and false
     * on a failure (e.g. package not installed). The
     * session timer and the bandit observation are
     * tied to a successful launch.
     */
    fun interface LaunchCallback {
        fun launch(app: DisplayApp): Boolean
    }

    /**
     * The full [GateContext] the friction gate needs to
     * render a single opening: tone (now bandit-adaptive
     * for the first two reaches of a window), one of the
     * user's own small things, the user's if-then plan
     * for this app if one is on file, and a rotated
     * self-compassion moment if any are on file.
     *
     * Records the reach as a side effect, because the
     * count only means anything if every reach is
     * counted. Quiet hours come from the sunset window
     * the app already exposes rather than from sleep
     * estimates: it is a setting the person can see and
     * reason about, and it needs no usage-access
     * permission to read.
     */
    suspend fun gateFor(app: DisplayApp): GateContext {
        val packageName = app.component.substringBefore('/')
        val prior = frictionPrefs.recordReach(
            packageName,
            System.currentTimeMillis(),
            FrictionContext.RECENT_WINDOW_MILLIS,
        )
        frictionPrefs.recordGateShown(packageName)
        val quiet = sunsetPrefs.isQuietHour()
        val adaptive = adaptiveTone(prior, quiet)
        val smallThings = frictionPrefs.smallThings.first()
        val ifThenPlans = frictionPrefs.ifThenPlans.first()
        val compassion = frictionPrefs.compassionMoments.first()
        // v0.20.1 round 4 (item M): the per-app session-length
        // map. The gate looks up
        // `perAppSessionLength.defaultMinutes(packageName)`
        // to decide which button to highlight. Reading the
        // map here keeps the gate signature stable: one
        // [GateContext] in, one decision out.
        val perAppLength = frictionPrefs.perAppSessionLength.first()
        val offer = SmallThings.offer(
            things = smallThings,
            nthReach = prior,
            tone = adaptive.tone,
            quietHours = quiet,
        )
        val plan = ifThenPlans[packageName]?.takeIf { it.isComplete }
        val compassionPhrase = CompassionStore.rotate(compassion, prior)?.phrase
        return GateContext(
            tone = adaptive.tone,
            banditArm = adaptive.arm,
            smallThing = offer,
            ifThenPlan = plan,
            compassionMoment = compassionPhrase,
            packageName = packageName,
            perAppSessionLength = perAppLength,
        )
    }

    private suspend fun adaptiveTone(prior: Int, quiet: Boolean): AdaptiveTone {
        val deterministic = FrictionContext.toneFor(prior, insideSleepWindow = quiet)
        if (deterministic != FrictionTone.FULL) return AdaptiveTone(deterministic, null)
        val state = frictionPrefs.banditState.first()
        val tallies = frictionPrefs.gateTallies.first()
        val context = banditContext(prior, quiet, tallies, state)
        val choice = FrictionBandit.choose(state, context)
        val tone = when (choice) {
            FrictionBandit.ArmChoice.FULL -> FrictionTone.FULL
            FrictionBandit.ArmChoice.BRIEF -> FrictionTone.BRIEF
        }
        return AdaptiveTone(tone, choice)
    }

    private data class AdaptiveTone(
        val tone: FrictionTone,
        val arm: FrictionBandit.ArmChoice?,
    )

    private fun banditContext(
        prior: Int,
        quiet: Boolean,
        tallies: Map<String, org.mindanchor.friction.GateTally>,
        state: FrictionBandit.BanditState,
    ): FrictionBandit.Context {
        val hour = LocalTime.now().hour
        val tod = when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..20 -> 2
            else -> 3
        }
        val abandonRate = if (tallies.isEmpty()) 0.0
            else tallies.values.sumOf { it.abandoned }.toDouble() /
                tallies.values.sumOf { it.shown }.toDouble().coerceAtLeast(1.0)
        val abandonBucket = when {
            abandonRate < 0.25 -> 0
            abandonRate < 0.5 -> 1
            abandonRate < 0.75 -> 2
            else -> 3
        }
        return FrictionBandit.Context(
            recentAbandonRateBucket = abandonBucket,
            timeOfDayBucket = tod,
            insideSleepWindow = if (quiet) 1 else 0,
        )
    }

    /**
     * The person met the pause and chose not to go in.
     * The [banditArm] is the arm the bandit played for
     * this gate, when one was.
     */
    fun recordNeverMind(app: DisplayApp, banditArm: FrictionBandit.ArmChoice? = null) {
        viewModelScope.launch {
            val pkg = app.component.substringBefore('/')
            frictionPrefs.recordGateAbandoned(pkg)
            if (banditArm != null) {
                val state = frictionPrefs.banditState.first()
                val updated = FrictionBandit.observe(state, banditArm, reward = false)
                frictionPrefs.saveBanditState(updated)
            }
        }
    }

    /**
     * Launch after the friction gate; [minutes] null
     * means no session timer. The launch itself is
     * delegated to the [launcher] callback (the
     * LauncherViewModel owns the repository). The
     * session timer and the bandit observation are
     * tied to a successful launch.
     */
    fun launchTimed(
        app: DisplayApp,
        minutes: Long?,
        banditArm: FrictionBandit.ArmChoice? = null,
        launcher: LaunchCallback,
    ) {
        val launched = launcher.launch(app)
        if (!launched) return
        val packageName = app.component.substringBefore('/')
        AppWatchService.allow(packageName, minutes)
        if (minutes != null) {
            SessionManager.startSession(getApplication(), packageName, app.label, minutes)
        }
        if (banditArm != null) {
            viewModelScope.launch {
                val state = frictionPrefs.banditState.first()
                val updated = FrictionBandit.observe(state, banditArm, reward = true)
                frictionPrefs.saveBanditState(updated)
            }
        }
    }
}
