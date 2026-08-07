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
     * How hard the pause should push for [app], right now.
     *
     * Records the reach as a side effect, because the count only means
     * anything if every reach is counted. Quiet hours are taken from the
     * sunset window the app already exposes rather than from sleep
     * estimates: it is a setting the person can see and reason about, and
     * it needs no usage-access permission to read.
     */
    suspend fun toneFor(app: DisplayApp): FrictionTone {
        val packageName = app.component.substringBefore('/')
        val prior = frictionPrefs.recordReach(
            packageName,
            System.currentTimeMillis(),
            FrictionContext.RECENT_WINDOW_MILLIS,
        )
        return FrictionContext.toneFor(prior, insideSleepWindow = SunsetPrefs.isQuietHour())
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
