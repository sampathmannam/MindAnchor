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
import org.mindanchor.friction.FrictionContext
import org.mindanchor.friction.FrictionTone
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.SessionManager
import org.mindanchor.friction.SmallThings
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
     * The tone, and one of the person's own small things if this is a
     * moment to offer one.
     *
     * Records the reach as a side effect, because the count only means
     * anything if every reach is counted. Quiet hours come from the sunset
     * window the app already exposes rather than from sleep estimates: it
     * is a setting the person can see and reason about, and it needs no
     * usage-access permission to read. Resolved together because both depend on the
     * same reach count and the same clock, and reading the clock twice
     * could straddle the start of the quiet hours.
     */
    suspend fun gateFor(app: DisplayApp): Pair<FrictionTone, String?> {
        val packageName = app.component.substringBefore('/')
        val prior = frictionPrefs.recordReach(
            packageName,
            System.currentTimeMillis(),
            FrictionContext.RECENT_WINDOW_MILLIS,
        )
        frictionPrefs.recordGateShown(packageName)
        val quiet = sunsetPrefs.isQuietHour()
        val tone = FrictionContext.toneFor(prior, insideSleepWindow = quiet)
        val offer = SmallThings.offer(
            things = frictionPrefs.smallThings.first(),
            nthReach = prior,
            tone = tone,
            quietHours = quiet,
        )
        return tone to offer
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
    fun recordNeverMind(app: DisplayApp) {
        viewModelScope.launch {
            frictionPrefs.recordGateAbandoned(app.component.substringBefore('/'))
        }
    }

    /**
     * Launch after the friction gate; [minutes] null means no session timer.
     * The timer is armed only if the app actually started — otherwise a
     * failed launch would leave a phantom session ringing later.
     */
    fun launchTimed(app: DisplayApp, minutes: Long?) {
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
    }
}
