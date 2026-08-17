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
import org.mindanchor.reader.ReaderPrefs
import org.mindanchor.reader.ReadingSize
import org.mindanchor.sleep.BedtimeList
import org.mindanchor.sleep.BedtimePhase
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
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

@Suppress("TooManyFunctions")
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)
    private val frictionPrefs = FrictionPrefs(application)
    private val wellnessRepository = org.mindanchor.vitals.WellnessRepository(application)
    private val readerPrefs = ReaderPrefs(application)
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
     * v0.25.7+ WP-3: the id generator moved to
     * [NotesPrefs.nextNoteId]. The home card and the
     * full [org.mindanchor.model.NoteActivity] now
     * share a single process-singleton counter — the
     * previous design had two separate
     * `AtomicLong` fields (one per view model), and
     * a note written from the home card and a note
     * written in the full activity could share an
     * id (both seeded from the same `max(existing,
     * currentTimeMillis)` on first use).
     */
    private fun nextNoteId(): Long = notesPrefs.nextNoteId()

    /**
     * v0.22.0 (WP-10 step 2): the "what makes this different"
     * callout. Shown strictly fewer than
     * [org.mindanchor.data.LauncherPrefs.INTRO_CALLOUT_LAUNCHES]
     * times; hidden forever after.
     *
     * The home surface records a launch on every display
     * (see [recordHomeLaunch]); the callout is the one
     * affordance the home surface renders conditionally
     * on this flag.
     */
    val showIntroCallout: StateFlow<Boolean> = prefs.showIntroCallout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Increments the home-launch counter. Called from the
     * home surface on every display (not on every home button
     * press — the surface is the unit, because the callout
     * lives on the surface and one display = one chance to
     * see it).
     */
    fun recordHomeLaunch() {
        viewModelScope.launch { prefs.recordHomeLaunch() }
    }
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
     * Whether to take an unfinished thing, hand one back, say nothing, or
     * keep silent because the user has scheduled a specific revisit time.
     *
     * The tuple is (phase, note, postponedAt) — the postponedAt is the
     * user's worry-postponement time (v0.25.5, Borkovec 1994 + Watkins
     * 2008), passed through to the home screen so the card can show
     * "Back at 3pm" while the launcher is silent.
     *
     * Kept apart from [uiState] rather than folded into it: combine() has
     * typed overloads up to five sources, uiState already uses all five,
     * and the vararg form costs a set of unchecked casts that would turn
     * a future reordering into a ClassCastException on the home screen.
     */
    val openLoop: StateFlow<Triple<LoopPhase, String?, Instant?>> = combine(
        frictionPrefs.openLoopNote,
        frictionPrefs.openLoopDay,
        frictionPrefs.openLoopPostponedAt,
        minuteTick,
    ) { note, day, postponedAt, _ ->
        val phase = OpenLoop.phase(
            quietHours = sunsetPrefs.isQuietHour(),
            note = note,
            notedDay = day,
            today = LocalDate.now(),
            postponedAt = postponedAt,
        )
        Triple(phase, note, postponedAt)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Triple(LoopPhase.NONE, null, null),
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

    /**
     * Sets a specific revisit time for the current open loop. The note
     * itself is unchanged — the user is saying "I will deal with this
     * then", not writing a new worry. Used by the v0.25.5 worry-
     * postponement affordance on the RETURN state of the home card.
     */
    fun postponeOpenLoop(at: Instant) {
        viewModelScope.launch { frictionPrefs.setOpenLoopPostponedAt(at) }
    }

    /** Drops the postponement and falls back to the hand-it-back flow. */
    fun cancelOpenLoopPostponement() {
        viewModelScope.launch { frictionPrefs.setOpenLoopPostponedAt(null) }
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
        val note = Note(
            id = nextNoteId(),
            body = trimmed,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            notesPrefs.add(note)
            // v0.25.7+ WP-3: enqueue classification so
            // the home-card note gets the same type chip
            // as a note written in the full activity.
            // Before this fix the home-card note was
            // saved with type=null and stayed that way
            // — the v0.25.0 auto-classify promise was
            // broken on the most common capture path.
            // The classifier is fail-soft (returns
            // GENERAL on any error); a future Phi-4
            // unavailable state degrades to the
            // default, not an untyped note.
            org.mindanchor.note.ClassifierEnqueuer(getApplication()).enqueue(note)
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

    // --- Letter reading size (v0.25.2-B Task 15) --------------------
    //
    // Mirrors [org.mindanchor.settings.SettingsViewModel.letterSize]:
    // both VMs read from the same DataStore-backed [ReaderPrefs] source,
    // so the launcher's letter reader and the Settings → Reading
    // sub-section see the same value. `.stateIn(Eagerly, MEDIUM)` keeps
    // the initial emission off the data path (no value jump for a
    // freshly-launched reader).
    val letterSize: StateFlow<ReadingSize> = readerPrefs.size
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingSize.MEDIUM)

    fun setLetterSize(size: ReadingSize) {
        viewModelScope.launch { readerPrefs.setSize(size) }
    }

    // --- modelFits (v0.25.16 BUG-017, v0.30.1 BUG-046) -----------------
    //
    // The letter inbox's "Generate now" affordance is gated on whether
    // the on-device Phi-4 model is present in the right place and
    // small enough to load. The pre-v0.25.16 HomeScreen held this as
    // a Composable-level `remember { mutableStateOf(false) }` stub —
    // the value was always `false`, so the Generate-now button was
    // always disabled. The wire to the actual disk state belongs in
    // the ViewModel, not in the Composable, so the right primitive
    // is a `StateFlow<Boolean>` that the screen collects with
    // `collectAsStateWithLifecycle` (BUG-004) and reflects into the
    // `LetterScreen` argument.
    //
    // v0.30.1 (BUG-046): the previous `init { ... }` block checked a
    // hard-coded `phi-4-mini-q4.gguf` path that v0.23.0 had renamed
    // to `model.gguf` in [ModelStore.MODEL_FILE_NAME]. The launcher
    // was always reading "model not on file" and the Letters "Use AI"
    // and "Generate now" buttons were always greyed, even after a
    // successful import. Backing this flow with [ModelStore.fitFlow]
    // — the same singleton flow [SettingsViewModel] publishes into
    // after every import/clear — means the launcher and the settings
    // screen agree without either having to know the other exists.
    val modelFits: StateFlow<Boolean> = org.mindanchor.narrate.ModelStore.fitFlow()

    init {
        // Make sure the singleton fitState reflects the
        // current file on disk the first time the launcher
        // is composed. The Settings VM also calls this on
        // every entry, so this is just the "cold start
        // from the launcher" path.
        viewModelScope.launch(Dispatchers.IO) {
            org.mindanchor.narrate.ModelStore.refreshFit(application)
        }
    }

    // --- v0.35.0: three StateFlows for the data-sources card -----------
    //
    // The "Where it comes from" home card surfaces the last sync /
    // last reading for each source the user has opted in to.
    // Each flow degrades to "no source" on any failure (a missing
    // Health Connect install, an unwired Coros bridge, an empty
    // PPG log) — the card paints a single line "Nothing paired
    // yet" in that case. The flow shape is the same
    // WhileSubscribed-5s idiom the rest of this VM uses, so a
    // backgrounded home screen does not pay the DataStore read
    // cost.
    //
    // Why a Coros + PPG + HC trio and not a single SmartwatchRegistry
    // state: the registry is for live BLE/wearable data. The data-
    // sources card is for "when did I last have a reading", which
    // is a cache-warmth question answered by each source's own
    // DataStore. The two read paths are deliberately independent —
    // a user with only a PPG camera and a Coros app (no BLE
    // watch) should still see two rows on the card.

    /** Where the Health Connect side of the data is right now. */
    sealed interface HealthConnectStatus {
        /** HC is not on this device at all. */
        data object Unavailable : HealthConnectStatus
        /** HC is installed but no permissions have been granted. */
        data object NotGranted : HealthConnectStatus
        /** At least one HC permission is granted — readings can flow. */
        data object Granted : HealthConnectStatus
    }

    /** Where the Coros side-channel side of the data is right now. */
    sealed interface CorosDataStatus {
        /** No credentials on file. The user has not opted in. */
        data object NotConnected : CorosDataStatus
        /** Connected; the worker has never synced (or wiped the cache). */
        data class ConnectedNoData(val email: String) : CorosDataStatus
        /** Connected, with a fresh (or stale) last-sync stamp. */
        data class Connected(val email: String, val lastSyncEpochMs: Long) : CorosDataStatus
    }

    /** One PPG measurement, on the home card's "last reading" line. */
    data class PpgLastMeasurement(
        val startEpochMs: Long,
        val endEpochMs: Long,
        val meanHr: Double?,
    )

    val healthConnectStatus: StateFlow<HealthConnectStatus> = flow {
        // Re-emit whenever the user returns to the home surface;
        // the call is a single HC SDK-status check + a permissions
        // read, both cheap.
        val ctx = getApplication<Application>()
        val status = when {
            !org.mindanchor.vitals.HealthConnectSource.isAvailable(ctx) ->
                HealthConnectStatus.Unavailable
            !org.mindanchor.vitals.HealthConnectSource.hasAnyPermissions(ctx) ->
                HealthConnectStatus.NotGranted
            else -> HealthConnectStatus.Granted
        }
        emit(status)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HealthConnectStatus.NotGranted,
    )

    private val corosVitalSource = org.mindanchor.vitals.coros.CorosVitalSource(application)
    private val corosAuth = org.mindanchor.vitals.coros.CorosAuth(application)
    private val ppgSessionStore = org.mindanchor.vitals.PpgSessionStore(application)

    val corosDataStatus: StateFlow<CorosDataStatus> = flow {
        val corosState = corosAuth.connectionState(lastSyncEpochMs = null)
        if (corosState !is org.mindanchor.vitals.coros.CorosConnectionState.Connected) {
            emit(CorosDataStatus.NotConnected)
        } else {
            val email = corosState.email
            val lastSync = corosVitalSource.lastSyncEpochMs.first()
            if (lastSync == null) {
                emit(CorosDataStatus.ConnectedNoData(email))
            } else {
                emit(CorosDataStatus.Connected(email, lastSync))
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CorosDataStatus.NotConnected,
    )

    val ppgLastMeasurement: StateFlow<PpgLastMeasurement?> = ppgSessionStore.lastSession()
        .map { session ->
            session?.let {
                PpgLastMeasurement(
                    startEpochMs = it.start.toEpochMilli(),
                    endEpochMs = it.end.toEpochMilli(),
                    meanHr = it.meanHr,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    // --- Today's one thing (v0.25.5 WP-F) -------------------------------
    //
    // A single, narrow, today's-action text on the home corner.
    // Martell 2013: a single named action outperforms a list of
    // goals on follow-through. The card is silent when the field
    // is null (the default); setting it shows the card; clearing
    // it (Done button) hides it. The flow is exposed as a
    // StateFlow so the home screen can collectAsState it the
    // same way it does the open loop and the bedtime list.

    val oneThing: StateFlow<String?> = prefs.oneThing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOneThing(text: String?) {
        viewModelScope.launch { prefs.setOneThing(text) }
    }
}
