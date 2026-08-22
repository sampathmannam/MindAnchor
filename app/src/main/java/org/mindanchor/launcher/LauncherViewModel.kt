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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    // v0.58.0: the long-press mood → annotate
    // path stores a [CheckIn] alongside the
    // mood Note. The two writes are
    // independent (the Note for the home /
    // Notes tab, the CheckIn for the
    // history view) so the launcher keeps
    // them in their own DataStore.
    private val checkInPrefs = org.mindanchor.data.CheckInPrefs(application)

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
            .map { state ->
                // v0.50.0: the home card surfaces
                // ACTIVE notes only. A TASK that the
                // user has marked done is a closed
                // loop — the launcher does not need
                // to keep showing it on the home
                // surface (the Notes tab still does).
                // The cap is unchanged at
                // [QUICK_NOTES_RECENT_CAP] = 3.
                NoteStore.sortedForList(
                    state.notes.filter { !it.done }
                ).take(QUICK_NOTES_RECENT_CAP)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

    /**
     * v0.48.0: the uncapped notes flow for the
     * Notes tab. The home card uses the
     * `notes` flow above, which is capped at
     * QUICK_NOTES_RECENT_CAP = 3 to keep the
     * "what you just wrote" list short. The
     * Notes tab is the "all notes" surface
     * and must NOT be capped — a user with
     * 100 notes (e.g. after running the
     * SeedNotes fixture, or after a week of
     * daily gratitude + tasks) must see all
     * 100 rows.
     *
     * Phase 1 (systematic-debug) root cause
     * investigation: the previous Notes tab
     * read from `notes` and was silently
     * capped to 3 — the user opened the tab
     * expecting "everything I wrote" and saw
     * only the 3 most recent. The fix is
     * structural: a separate uncapped flow.
     * Sorting uses the same [NoteStore.sortedForList]
     * contract as the home card so the order
     * (pinned-first, then updatedAt desc) is
     * consistent across both surfaces.
     *
     * The cap on `notes` is unchanged — the
     * home card's "3 most recent" behaviour
     * is the v0.43.0 design and stays.
     */
    val allNotes: StateFlow<List<Note>> =
        notesPrefs.notes
            .map { state -> NoteStore.sortedForList(state.notes) }
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
     * v0.47.0: the drawer-search bang command.
     *
     * Bangs are `!ground`, `!note`, `!task`,
     * `!settings`, `!mood`. Typing a bang at the
     * start of the query emits the corresponding
     * [BangCommand] on [bangCommand] (and the
     * DrawerSurface consumes it, navigates, and
     * clears the search). Anything that does NOT
     * start with a bang stays on the null branch
     * and the drawer shows the usual app list.
     *
     * The AIO Launcher "bangs in search" pattern is
     * the most distinctive drawer affordance in
     * the minimalist launcher category. It is
     * cheap to ship — one regex on the query,
     * one StateFlow — and the user can navigate
     * to a non-app surface in the same text box
     * they use to launch apps. The drawer does
     * not need a separate "go to settings" button.
     *
     * The bang is detected on a complete word, not
     * a substring. Typing `!grounded` does not
     * fire the bang (the user is probably
     * searching for an app named "grounded").
     * The match is the entire query equal to
     * "!ground" or the entire query starting with
     * "!ground " (the rest is a parameter, not
     * used by v0.47.0).
     */
    val bangCommand: kotlinx.coroutines.flow.Flow<BangCommand?> =
        query.map { parseBang(it) }
            .distinctUntilChanged()

    /**
     * v0.47.0: the bang parser. The regex is
     * intentionally tight: the bang is the WHOLE
     * query or the bang followed by a space
     * (followed by anything the surface ignores).
     * A substring match would over-fire on app
     * names that start with a `!` (rare but real).
     */
    private fun parseBang(text: String): BangCommand? {
        val t = text.trim()
        return when {
            t == "!ground" || t.startsWith("!ground ") -> BangCommand.GroundMe
            // v0.60.0: clinical-variant bangs. !panic
            // opens the Distress Thermometer (a
            // 0-100 self-rating of how acute the
            // feeling is), !breathe opens the
            // paced-breathing screen. They mirror
            // !ground but skip the picker — a user
            // who is mid-panic does not want to
            // choose between "breathe", "cold
            // water", and "name 5 things" first;
            // they want the right next action
            // right now.
            t == "!panic" || t.startsWith("!panic ") -> BangCommand.Panic
            t == "!breathe" || t.startsWith("!breathe ") -> BangCommand.Breathing
            t == "!note" || t.startsWith("!note ") -> BangCommand.Notes
            t == "!task" || t.startsWith("!task ") -> BangCommand.Tasks
            t == "!settings" || t.startsWith("!settings ") -> BangCommand.Settings
            t == "!mood" || t.startsWith("!mood ") -> BangCommand.Mood
            else -> null
        }
    }

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

    /**
     * v0.47.0: the drawer-bang command. Emitted
     * by the bang parser; consumed by
     * [DrawerSurface] which navigates to the
     * matching surface and clears the search.
     *
     * The enum is kept in the ViewModel (not the
     * Composable) so the DrawerSurface can
     * dispatch via the surface-state enum without
     * coupling to the Launcher's internal
     * navigation. The Drawer maps each bang to
     * the corresponding LauncherSurface; the
     * ViewModel does not know about the launcher's
     * navigation graph.
     */
    enum class BangCommand {
        /** v0.26.0 §3.5: the GroundMe sub-surface. */
        GroundMe,
        /** v0.60.0: the Distress Thermometer (acute
         *  self-rating). Skips the GroundMe picker
         *  for a user in mid-panic. */
        Panic,
        /** v0.60.0: the paced-breathing screen.
         *  Skips the GroundMe picker. */
        Breathing,
        /** v0.45.0: the Notes tab (all-notes view). */
        Notes,
        /** v0.44.0: the Tasks chip + reminder picker. */
        Tasks,
        /** v0.25.15: the Settings sub-surface. */
        Settings,
        /** v0.46.0: the Mood Card on the home surface. */
        Mood,
    }

    /**
     * v0.47.0: acknowledge a bang has been
     * consumed. The DrawerSurface calls this
     * after navigating, so the search field
     * clears and the same bang does not re-fire
     * on the next composition.
     */
    fun consumeBang() {
        query.value = ""
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
    fun addQuickNote(body: String, pinned: Boolean = false) {
        val trimmed = body.trim().take(Note.MAX_BODY)
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val note = Note(
            id = nextNoteId(),
            body = trimmed,
            createdAt = now,
            updatedAt = now,
            pinned = pinned,
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

    /**
     * v0.43.0: delete a note from the home card.
     * Wired to the × affordance on each row of the
     * recent-notes list. The same delete path the full
     * NoteActivity uses, so the note is removed from
     * disk, not just from the in-memory list. Blank
     * input and missing notes are no-ops; the row
     * is removed optimistically from the [notes] flow
     * and the prefs write happens in [viewModelScope].
     */
    fun deleteNote(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch {
            notesPrefs.delete(id)
            // v0.44.0: cancelling a note also
            // cancels any pending reminder alarm.
            // The note is gone; the alarm is
            // dangling. The scheduler's
            // `PendingIntent.FLAG_UPDATE_CURRENT`
            // means a future schedule for the same
            // id would re-create the alarm — there
            // isn't a future schedule because the
            // note is deleted, so the cancel is the
            // last step.
            org.mindanchor.note.ReminderScheduler.cancel(getApplication(), id)
        }
    }

    /**
     * v0.54.0: re-insert a deleted note by
     * id. Powers the "Undo" affordance on the
     * Notes tab swipe-to-delete snackbar. The
     * caller passes the full [Note] (snapshot
     * taken at swipe time) so the restored note
     * preserves its original id, body, createdAt,
     * updatedAt, type, dueAt, reminderAt, done,
     * and pinned values — the user gets back
     * exactly what they deleted, in the right
     * day group, with the right pin state.
     *
     * The same path is used for pin-toggle Undo
     * (pass the snapshot, then [pinNote] restores
     * the flag). For pin Undo the caller uses
     * [pinNote] directly with the snapshot's
     * pinned value; for delete Undo the caller
     * uses this function.
     *
     * If the id already exists in the store (a
     * race with another writer), the call is a
     * no-op — the store does not duplicate ids.
     */
    fun restoreNote(note: org.mindanchor.model.Note) {
        if (note.id <= 0L) return
        viewModelScope.launch { notesPrefs.add(note) }
    }

    /**
     * v0.44.0: add a TASK note. A task is a note
     * with `type = NoteType.TASK` and an optional
     * `dueAt` epoch millis. A task with `done =
     * false` shows the body, the type chip, and a
     * checkbox; toggling the checkbox calls
     * [markNoteDone] with `done = true`.
     *
     * Blank input is a no-op (the same
     * trim-and-discard rule as [addQuickNote]).
     * The reminder scheduler is NOT called for a
     * task — tasks do not fire alarms. The user
     * sees the due time on the row.
     */
    fun addTaskNote(body: String, dueAt: Long?, pinned: Boolean = false) {
        val trimmed = body.trim().take(Note.MAX_BODY)
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val note = Note(
            id = nextNoteId(),
            body = trimmed,
            createdAt = now,
            updatedAt = now,
            type = org.mindanchor.model.NoteType.TASK,
            dueAt = dueAt,
            done = false,
            pinned = pinned,
        )
        viewModelScope.launch { notesPrefs.add(note) }
    }

    /**
     * v0.44.0: add a REMINDER note. A reminder is a
     * note with `type = NoteType.REMINDER` and a
     * non-null `reminderAt` epoch millis. The
     * ReminderScheduler is told to schedule the
     * alarm at the same time. The note write and
     * the alarm schedule are both best-effort:
     * a scheduler SecurityException (the user has
     * revoked SCHEDULE_EXACT_ALARM via Settings) is
     * logged but does not roll back the note. The
     * note's `reminderAt` is set; the row label
     * reads "reminder, may be late" if the exact
     * alarm was rejected.
     *
     * Blank input OR a null `reminderAt` is a
     * no-op. A reminder without a time is not a
     * reminder.
     */
    fun addReminderNote(body: String, reminderAt: Long?, pinned: Boolean = false) {
        val trimmed = body.trim().take(Note.MAX_BODY)
        if (trimmed.isEmpty()) return
        val at = reminderAt ?: return
        val now = System.currentTimeMillis()
        val note = Note(
            id = nextNoteId(),
            body = trimmed,
            createdAt = now,
            updatedAt = now,
            type = org.mindanchor.model.NoteType.REMINDER,
            reminderAt = at,
            pinned = pinned,
        )
        viewModelScope.launch {
            notesPrefs.add(note)
            try {
                org.mindanchor.note.ReminderScheduler.schedule(getApplication(), note.id, at)
            } catch (e: SecurityException) {
                android.util.Log.w(
                    "LauncherViewModel",
                    "addReminderNote: alarm not scheduled for noteId=${note.id}",
                    e,
                )
            }
        }
    }

    /**
     * v0.46.0: log a mood with one tap. The body is the
     * emoji itself; the type is [NoteType.GENERAL]
     * (a mood is not a Task or a Reminder). The mood
     * log shows on the home card and the Notes tab as a
     * one-row note with the emoji as the body — the
     * emoji is the entry, no text required.
     *
     * A mood log is what the 56-app competitor survey
     * identified as the single most-replicated
     * interaction in the mental-health category (Daylio
     * 2-tap, Bearable emoji grid, Moodflow gesture+haptic,
     * Wysa penguin). The 1-tap version is the floor the
     * category has converged on.
     *
     * A blank body is a no-op. The classifier is
     * NOT enqueued — a one-tap mood log is a deliberate,
     * user-typed-into-emoji gesture, and the classifier
     * would either mark it as GENERAL anyway or
     * interpret the emoji as something the user did not
     * mean. The user-owned `type = GENERAL` is the
     * honest signal.
     */
    fun addMoodLog(emoji: String) {
        if (emoji.isEmpty()) return
        val now = System.currentTimeMillis()
        val note = Note(
            id = nextNoteId(),
            body = emoji,
            createdAt = now,
            updatedAt = now,
            type = org.mindanchor.model.NoteType.GENERAL,
        )
        viewModelScope.launch { notesPrefs.add(note) }
    }

    /**
     * v0.58.0: long-press mood → annotate. The
     * v0.46.0 1-tap mood log is the floor — it
     * writes a one-row note with the emoji as the
     * body. The 56-app competitor survey
     * (Daylio, Bearable, Moodflow, Wysa, How We
     * Feel) showed the 1-tap emoji is the most-
     * replicated interaction in the mental-health
     * category, but the same survey showed that
     * the 5-emoji scale *with* an optional
     * reflection is the version that the serious
     * mental-health journals (Linehan DBT diary
     * card, Pennebaker expressive-writing protocol,
     * Wrzus & Neubauer EMA methodology) cite as
     * the data that drives outcome improvement.
     *
     * The launcher splits the difference: the
     * default 1-tap path is the v0.46.0 design
     * (fast, no friction), and the long-press path
     * adds an *optional* 1-3 sentence reflection.
     * The mood emoji maps to a WHO-5-style
     * 1-5 rating (😞→1, 😕→2, 😐→3, 🙂→4, 😊→5);
     * the reflection is stored on the [CheckIn]
     * and is the *only* piece of free text the
     * launcher asks for. The launcher never
     * summarises, never stores a mood tag, never
     * feeds the text into a model.
     *
     * A blank emoji is a no-op. A blank
     * reflection is allowed (the user may want
     * the check-in's *timestamp* without words —
     * the EMA methodology still accepts the
     * rating on its own). The reflection is
     * truncated to [CheckIn.MAX_REFLECTION]
     * (1000 chars) before save.
     */
    fun addMoodLogWithReflection(emoji: String, reflection: String) {
        if (emoji.isEmpty()) return
        val now = System.currentTimeMillis()
        val note = Note(
            id = nextNoteId(),
            body = emoji,
            createdAt = now,
            updatedAt = now,
            type = org.mindanchor.model.NoteType.GENERAL,
        )
        val rating = when (emoji) {
            "😞" -> 1
            "😕" -> 2
            "😐" -> 3
            "🙂" -> 4
            "😊" -> 5
            else -> 3
        }
        val safeReflection = reflection.take(org.mindanchor.model.CheckIn.MAX_REFLECTION)
        val checkIn = org.mindanchor.model.CheckIn(
            rating = rating,
            reflection = safeReflection,
            atMillis = now,
        )
        viewModelScope.launch {
            notesPrefs.add(note)
            checkInPrefs.add(checkIn)
        }
    }

    /**
     * v0.44.0: toggle a note's `done` flag.
     * Wired to the checkbox on a TASK row. A no-op
     * if the id is not in the store. The toggle
     * is one-way: there is no separate `markUndone`
     * because the same checkbox toggles the value.
     */
    fun markNoteDone(id: Long, done: Boolean) {
        if (id <= 0L) return
        viewModelScope.launch { notesPrefs.setDone(id, done) }
    }

    /**
     * v0.45.0: pin a note to the home card. The
     * home card shows only pinned notes (max 3).
     * The Notes tab shows every note regardless
     * of pin state. Wired to the "Pin to home"
     * toggle on the QuickNotesCard input and on
     * each row of the Notes tab. The toggle is
     * bidirectional — pinning an unpinned note
     * surfaces it on the home; unpinning a pinned
     * note removes it from the home (the note is
     * still in the Notes tab). A no-op if the id
     * is not in the store.
     */
    fun pinNote(id: Long, pinned: Boolean) {
        if (id <= 0L) return
        viewModelScope.launch { notesPrefs.setPinned(id, pinned) }
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
