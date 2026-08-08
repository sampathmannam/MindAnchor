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
import org.mindanchor.friction.FrictionBandit
import org.mindanchor.friction.GateContext
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.LoopPhase
import org.mindanchor.friction.OpenLoop
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
    /**
     * The friction-gate concerns, extracted into
     * [FrictionViewModel] as part of the senior-architect
     * review follow-up (item C of the SOTA-IMPROVEMENT-PLAN).
     * The [LauncherViewModel] is a thin facade: the public
     * methods [gateFor], [recordNeverMind], [launchTimed]
     * delegate to this instance. A future change to the
     * friction flow touches [FrictionViewModel] and not
     * this 434-line file.
     */
    private val friction = FrictionViewModel(application)

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
     * Delegation to [FrictionViewModel.gateFor]. The full
     * KDoc is on the FrictionViewModel side; this is a
     * thin facade so callers (HomeScreen, GateActivity)
     * continue to call [LauncherViewModel.gateFor] without
     * a callsite change.
     */
    suspend fun gateFor(app: DisplayApp): GateContext =
        friction.gateFor(app)

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
     * Delegation to [FrictionViewModel.recordNeverMind].
     */
    fun recordNeverMind(app: DisplayApp, banditArm: FrictionBandit.ArmChoice? = null) {
        friction.recordNeverMind(app, banditArm)
    }

    /**
     * Delegation to [FrictionViewModel.launchTimed]. The
     * actual app launch is the [FrictionViewModel.LaunchCallback]
     * passed in — the [LauncherViewModel.launch] method,
     * which owns the app repository.
     */
    fun launchTimed(app: DisplayApp, minutes: Long?, banditArm: FrictionBandit.ArmChoice? = null) {
        friction.launchTimed(app, minutes, banditArm) { launch(it) }
    }
}
