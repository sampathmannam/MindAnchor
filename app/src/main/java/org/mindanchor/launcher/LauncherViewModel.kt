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
import org.mindanchor.data.NotesPrefs
import org.mindanchor.friction.FrictionBandit
import org.mindanchor.friction.GateContext
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.friction.LoopPhase
import org.mindanchor.friction.OpenLoop
import org.mindanchor.friction.PerAppSessionLength
import org.mindanchor.model.Note
import org.mindanchor.model.NoteStore
import org.mindanchor.sleep.BedtimeList
import org.mindanchor.sleep.BedtimePhase
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

data class LauncherUiState(
    val allApps: List<DisplayApp> = emptyList(),
    val favorites: List<DisplayApp> = emptyList(),
    val frictionPackages: Set<String> = emptySet(),
    /** Apps the person has said must never be closed on them. */
    val alwaysOpenPackages: Set<String> = emptySet(),
)

/**
 * The number of recent notes shown in the home-screen
 * quick-capture card. Three is the deliberate floor:
 * one row tells the user the surface works, three
 * rows give the card the feel of a running journal,
 * and beyond three the card would push the favourites
 * off a small screen at default font scale.
 */
internal const val QUICK_NOTES_RECENT_CAP = 3

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)
    private val frictionPrefs = FrictionPrefs(application)
    private val wellnessRepository = org.mindanchor.vitals.WellnessRepository(application)
    /**
     * The notes DataStore. The home screen surfaces
     * a quick-capture card (one input + a list of
     * recent saves with timestamps) so the user can
     * jot something without opening the full
     * NoteActivity. Both surfaces write to the same
     * sealed DataStore — a save from the home card
     * shows up in the list view, and vice versa.
     *
     * v0.20.4 quick-notes: the home card is a thin
     * shim over [org.mindanchor.model.Note] /
     * [NotesPrefs] / [NoteStore]. The data layer is
     * untouched; only the home-screen affordance is
     * new. The idCounter is the same lazy / by
     * applicationContext pattern used in
     * [org.mindanchor.model.NoteActivity] to avoid
     * the applicationContext-is-null-before-onCreate
     * NPE that crashed that activity once.
     */
    private val notesPrefs = NotesPrefs(application)

    /**
     * The most recent notes, newest first, capped
     * at [QUICK_NOTES_RECENT_CAP] for the home card.
     * The full list (all notes, all timestamps) is
     * available in NoteActivity; the home only needs
     * the *recent* set to make the saving feel
     * immediate. Sorted via [NoteStore.sortedForList]
     * so a pinned note floats to the top of the
     * recent set the way it would in the list view.
     */
    val notes: StateFlow<List<Note>> =
        notesPrefs.notes
            .map { state -> NoteStore.sortedForList(state.notes).take(QUICK_NOTES_RECENT_CAP) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

    /**
     * Monotonic counter for note ids created from
     * the home quick-capture card. The same
     * [java.util.concurrent.atomic.AtomicLong] /
     * lazy / applicationContext pattern as
     * [org.mindanchor.model.NoteActivity.idCounter]:
     * the counter is seeded on first use (not at
     * construction) so a [NotesPrefs] read at
     * construction time does not happen before the
     * ViewModel's application context is available.
     *
     * Seeded from `max(currentTimeMillis, maxExistingId)`,
     * so a process restart immediately after a save
     * in the same millisecond does not duplicate an
     * id. Two notes typed and saved in the same
     * millisecond on the home card would otherwise
     * collide (the brief acknowledged the risk; the
     * fix is cheap).
     */
    private val idCounter: AtomicLong by lazy {
        val seeded = kotlinx.coroutines.runBlocking {
            val existing = notesPrefs.notes.first()
            val maxExisting = existing.notes.maxOfOrNull { it.id } ?: 0L
            maxOf(System.currentTimeMillis(), maxExisting)
        }
        AtomicLong(seeded)
    }

    private fun nextNoteId(): Long = idCounter.incrementAndGet()
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
     * Add a note from the home-screen quick-capture
     * card. The body is trimmed and capped to the
     * same [Note.MAX_BODY] the rest of the notes
     * surface uses, then persisted with the current
     * timestamp. The id is the shared [idCounter]
     * (unique across both the home card and the
     * full NoteActivity).
     *
     * Blank input is a no-op. A trimmed body that
     * is empty after trimming is a no-op too —
     * the [Note] constructor would store an empty
     * string and the list view would show a blank
     * row, which is the worst kind of clutter.
     */
    fun addQuickNote(body: String) {
        val trimmed = body.trim().take(Note.MAX_BODY)
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            notesPrefs.add(
                Note(
                    id = nextNoteId(),
                    body = trimmed,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    // --- Wellness signals (N-of-1, from Health Connect) ---
    //
    // The home card surfaces the per-signal reading against the
    // person's own baseline, with no interpretation beyond
    // "above / at / below your usual". Refreshed on every
    // recomposition of the home surface (it is the home
    // surface's job to call refreshWellness, the same way it
    // calls refresh on the bedtime list) so a Health Connect
    // permission grant followed by an immediate home press
    // shows the data without a launcher restart.
    //
    // The null initial state is "still loading", not "no data":
    // a card that rendered "no data" on first composition would
    // be the wrong answer for somebody whose permission grant
    // is still being applied to the system tables.

    private val _wellnessReadings = MutableStateFlow<List<org.mindanchor.vitals.WellnessReading>?>(null)
    val wellnessReadings: StateFlow<List<org.mindanchor.vitals.WellnessReading>?> = _wellnessReadings.asStateFlow()

    fun refreshWellness() {
        viewModelScope.launch(Dispatchers.IO) {
            val readings = runCatching {
                wellnessRepository.readingsFor(LocalDate.now())
            }.getOrDefault(emptyList())
            _wellnessReadings.value = readings
        }
    }

    /**
     * Delegation to [FrictionViewModel.recordNeverMind].
     */
    fun recordNeverMind(app: DisplayApp, banditArm: FrictionBandit.ArmChoice? = null) {
        friction.recordNeverMind(app, banditArm)
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
        friction.launchTimed(app, minutes, banditArm) { launch(it) }
    }
}
