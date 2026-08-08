package org.mindanchor.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.data.AppRepository
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.friction.AppWatchService
import org.mindanchor.friction.CompassionStore
import org.mindanchor.friction.FrictionBandit
import org.mindanchor.friction.FrictionContext
import org.mindanchor.friction.FrictionTone
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.GateContext
import org.mindanchor.friction.GateTally
import org.mindanchor.friction.SessionManager
import org.mindanchor.friction.SmallThings
import org.mindanchor.friction.LoopPhase
import org.mindanchor.friction.OpenLoop
import org.mindanchor.friction.PerAppSessionLength
import org.mindanchor.sleep.BedtimeList
import org.mindanchor.sleep.BedtimePhase
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlinx.coroutines.flow.first

data class LauncherUiState(
    val allApps: List<DisplayApp> = emptyList(),
    val favorites: List<DisplayApp> = emptyList(),
    val frictionPackages: Set<String> = emptySet(),
    /** Apps the person has said must never be closed on them. */
    val alwaysOpenPackages: Set<String> = emptySet(),
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)
    private val frictionPrefs = FrictionPrefs(application)

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    /**
     * The two friction lists as one flow. combine() only has typed
     * overloads up to five sources, and this is the sixth.
     */
    private val frictionLists = combine(
        frictionPrefs.flaggedApps,
        frictionPrefs.alwaysOpen,
    ) { flagged, alwaysOpen -> flagged to alwaysOpen }

    /**
     * A pulse every minute.
     *
     * The unfinished-thing state turns on the clock crossing into or out
     * of the quiet hours, and nothing writes to preferences at that
     * moment — without a tick the prompt would appear only when something
     * else happened to change.
     */
    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    /**
     * Whether to take an unfinished thing, hand one back, or say nothing.
     *
     * Kept apart from [uiState] rather than folded into it: combine() has
     * typed overloads up to five sources, uiState already uses all five,
     * and the vararg form costs a set of unchecked casts that would turn
     * a future reordering into a ClassCastException on the home screen.
     */
    val openLoop: StateFlow<Pair<LoopPhase, String?>> = combine(
        frictionPrefs.openLoopNote,
        frictionPrefs.openLoopDay,
        minuteTick,
    ) { note, day, _ ->
        OpenLoop.phase(
            quietHours = sunsetPrefs.isQuietHour(),
            note = note,
            notedDay = day,
            today = LocalDate.now(),
        ) to note
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LoopPhase.NONE to null,
    )

    /**
     * The bedtime to-do list — see [org.mindanchor.sleep.BedtimeList].
     *
     * Same shape as [openLoop] above: a tuple of (phase, items) so
     * the home screen can render either prompt with one card. The
     * minute-tick is joined to the same source the openLoop uses
     * so both cards re-evaluate at the same minute boundary —
     * important when crossing into or out of the quiet hours, when
     * one card may need to appear and the other to disappear
     * simultaneously.
     */
    val bedtimeList: StateFlow<Triple<BedtimePhase, List<String>, String?>> = combine(
        frictionPrefs.bedtimeList,
        frictionPrefs.bedtimeListDay,
        minuteTick,
    ) { items, day, _ ->
        Triple(
            BedtimeList.phase(
                quietHours = sunsetPrefs.isQuietHour(),
                items = items,
                writtenDay = day,
                today = LocalDate.now(),
            ),
            items,
            day,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Triple(BedtimePhase.NONE, emptyList(), null),
    )

    val uiState: StateFlow<LauncherUiState> =
        combine(
            repository.apps,
            prefs.favorites,
            prefs.hidden,
            prefs.renames,
            frictionLists,
        ) { apps, favorites, hidden, renames, friction ->
            val display = AppFiltering.toDisplayApps(apps, favorites, hidden, renames)
            LauncherUiState(
                allApps = display,
                favorites = AppFiltering.favorites(display, favorites),
                frictionPackages = friction.first,
                alwaysOpenPackages = friction.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun searchResults(state: LauncherUiState): List<DisplayApp> =
        AppFiltering.search(state.allApps, query.value)

    fun launch(app: DisplayApp): Boolean {
        query.value = ""
        return repository.launch(app.component)
    }

    fun toggleFavorite(app: DisplayApp) {
        viewModelScope.launch { prefs.toggleFavorite(app.component) }
    }

    fun setHidden(app: DisplayApp, hide: Boolean) {
        viewModelScope.launch { prefs.setHidden(app.component, hide) }
    }

    fun rename(app: DisplayApp, label: String?) {
        viewModelScope.launch { prefs.rename(app.component, label) }
    }

    fun toggleFriction(app: DisplayApp) {
        val packageName = app.component.substringBefore('/')
        viewModelScope.launch {
            frictionPrefs.setFlagged(
                packageName,
                packageName !in uiState.value.frictionPackages,
            )
        }
    }

    fun toggleAlwaysOpen(app: DisplayApp) {
        val packageName = app.component.substringBefore('/')
        viewModelScope.launch {
            frictionPrefs.setAlwaysOpen(
                packageName,
                packageName !in uiState.value.alwaysOpenPackages,
            )
        }
    }

    /**
     * The full [GateContext] the friction gate needs to render
     * a single opening: tone (now bandit-adaptive for the first
     * two reaches of a window), one of the user's own small
     * things, the user's if-then plan for this app if one is
     * on file, and a rotated self-compassion moment if any are
     * on file.
     *
     * Records the reach as a side effect, because the count
     * only means anything if every reach is counted. Quiet
     * hours come from the sunset window the app already exposes
     * rather than from sleep estimates: it is a setting the
     * person can see and reason about, and it needs no
     * usage-access permission to read. Resolved together because
     * both depend on the same reach count and the same clock,
     * and reading the clock twice could straddle the start of
     * the quiet hours.
     *
     * The bandit decision runs *before* the deterministic
     * FrictionContext.toneFor: when the bandit says BRIEF, that
     * is what the gate plays (within the first two reaches
     * anyway). When the bandit says FULL, the deterministic
     * toneFor is what plays, with the same recentOpens and
     * insideSleepWindow inputs the gate already uses. FEATHER
     * (third reach onward) is still reached deterministically.
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
            // v0.20.1 round 4 (item M): the per-app
            // session-length map. The gate looks up
            // `perAppSessionLength.defaultMinutes(packageName)`
            // to decide which button to highlight.
            // Reading the map here keeps the gate
            // signature stable: one [GateContext] in,
            // one decision out.
            packageName = packageName,
            perAppSessionLength = perAppLength,
        )
    }

    /**
     * The bandit-adapted tone decision. When the deterministic
     * tone is FULL (the first two reaches of a window), the
     * bandit has a vote between FULL and BRIEF based on the
     * user's own history. When the deterministic tone is
     * already BRIEF or FEATHER (third reach onward, or quiet
     * hours), the bandit does not intervene — the deterministic
     * policy is right for those cases.
     *
     * The sleep-window bypass is inside [FrictionBandit.choose]:
     * even when the bandit would say BRIEF, the gate plays
     * FULL inside the sleep window. The OS-level sleep lever
     * is too important to leave to a posterior sample.
     *
     * Returns the chosen tone *and* the arm the bandit played
     * (when one was played). The arm is recorded in the
     * returned [GateContext] so the outcome of the gate —
     * the user proceeded past, or backed out — can update
     * exactly that arm's posterior, not whichever arm happens
     * to be the deterministic winner an hour later.
     */
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

    /**
     * Tiny carrier for the (tone, arm) pair returned by
     * [adaptiveTone]. A data class is overkill for two
     * fields and the constructor is private to the file —
     * only [adaptiveTone] constructs it.
     */
    private data class AdaptiveTone(
        val tone: FrictionTone,
        val arm: FrictionBandit.ArmChoice?,
    )

    /**
     * The 3-feature context the bandit reads. The features
     * are the ones the brief in [docs/research/16] identifies
     * as the dominant predictors of JITAI engagement: time of
     * day, day-of-week (folded into "is it the sleep window"),
     * and recent abandon rate. HRV is deliberately *not* a
     * feature — the brief flags wearable HRV as a weak signal
     * for within-person mental-health prediction.
     */
    private fun banditContext(
        prior: Int,
        quiet: Boolean,
        tallies: Map<String, GateTally>,
        state: FrictionBandit.BanditState,
    ): FrictionBandit.Context {
        val hour = java.time.LocalTime.now().hour
        val tod = when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..20 -> 2
            else -> 3
        }
        // The brief recommends a "recent abandon rate over
        // the last 24h" feature. The gate ledger already
        // records shown/abandoned, but the per-app tally is
        // a lifetime figure, not a 24h figure. For v1.2 we
        // approximate with the global abandon rate from the
        // current state — the data plumbing for a true
        // 24h window is a follow-up. The 4-bucket mapping
        // (0..3) is the same one [FrictionBandit.Context]
        // expects.
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

    fun saveOpenLoop(note: String) {
        viewModelScope.launch { frictionPrefs.setOpenLoop(note) }
    }

    fun clearOpenLoop() {
        viewModelScope.launch { frictionPrefs.clearOpenLoop() }
    }

    /**
     * Persist the bedtime list for tonight. Each non-blank item is
     * stored on its own line, capped at [BedtimeList.MAX_ITEMS]
     * (the cap is on the *decode* side, so a corrupted file cannot
     * produce an overflowing list). The day is stamped so the
     * morning "return" prompt fires only for last night.
     */
    fun saveBedtimeList(items: List<String>) {
        viewModelScope.launch { frictionPrefs.setBedtimeList(items) }
    }

    /** Hands the list back, then clears it — the Scullin loop. */
    fun clearBedtimeList() {
        viewModelScope.launch { frictionPrefs.clearBedtimeList() }
    }

    /**
     * The person met the pause and chose not to go in.
     *
     * This is the outcome the whole thing exists to make possible, and
     * until now nothing counted it — so there was no way to tell a pause
     * that was working from one that had become a formality. See
     * [org.mindanchor.friction.GateLedger].
     */
    /**
     * The person met the pause and chose not to go in.
     *
     * This is the outcome the whole thing exists to make possible, and
     * until now nothing counted it — so there was no way to tell a pause
     * that was working from one that had become a formality. See
     * [org.mindanchor.friction.GateLedger].
     *
     * The [banditArm] is the arm the bandit played for this gate, when
     * one was. A "did not go in" outcome is the reward signal the bandit
     * is learning against — it tells the bandit this tone (FULL or BRIEF)
     * was not the one the user proceeded through. The update is a
     * no-op when [banditArm] is null (the bandit did not play for this
     * gate, e.g. tone was already BRIEF or FEATHER deterministically).
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
     * Record a per-app time-box choice from the friction
     * gate. v0.20.1 round 4 (item M, docs/research/22).
     * The launcher invokes this when the user picks a
     * 5/10/20 button *and* the "Learn this for next time"
     * toggle is on. The choice is stored in
     * [FrictionPrefs.perAppSessionLength] and the gate
     * highlights the matching button on subsequent
     * reaches.
     *
     * The DataStore key is `per_app_session_length`; the
     * sealed-codecs equivalent lives on the codec-hmac
     * branch. A blank package name is a no-op (the gate
     * never passes a blank package; this is defensive).
     */
    fun recordPerAppSessionLength(packageName: String, minutes: Long) {
        if (packageName.isBlank()) return
        viewModelScope.launch {
            frictionPrefs.recordPerAppSessionLength(packageName, minutes)
        }
    }

    /**
     * v0.20.1 round 5 follow-up: forget the per-app
     * default. The user reached the gate, saw "Like
     * last time — N min", and decided that default
     * is no longer what they want. The launcher
     * clears the map entry; the next reach shows
     * the "Learn this for next time" toggle again
     * as if the user had never picked.
     *
     * Defensive: blank package name is a no-op. A
     * blank package would otherwise clear the entire
     * map (PerAppSessionLength.forget on an empty
     * string is a no-op too, but the gate never
     * passes a blank package; this is a belt-and-
     * braces check).
     */
    fun clearPerAppSessionLength(packageName: String) {
        if (packageName.isBlank()) return
        viewModelScope.launch {
            frictionPrefs.clearPerAppSessionLength(packageName)
        }
    }

    /**
     * Launch after the friction gate; [minutes] null means no session timer.
     * The timer is armed only if the app actually started — otherwise a
     * failed launch would leave a phantom session ringing later.
     *
     * The [banditArm] is the arm the bandit played for this gate, when
     * one was. A "did go in" outcome is the *reward* signal the bandit
     * learns against — it tells the bandit this tone (FULL or BRIEF) was
     * the one the user proceeded through. The update is a no-op when
     * [banditArm] is null.
     */
    fun launchTimed(app: DisplayApp, minutes: Long?, banditArm: FrictionBandit.ArmChoice? = null) {
        val launched = launch(app)
        if (!launched) return
        val packageName = app.component.substringBefore('/')
        // Tell the watcher this one is already settled. Without it the app
        // arriving in the foreground reads as a fresh reach and the person
        // gets the same pause twice for a single decision — once here and
        // once a heartbeat later, on top of the app they just opened.
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
