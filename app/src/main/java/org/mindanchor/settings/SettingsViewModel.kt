package org.mindanchor.settings

import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.corpus.CorpusImport
import org.mindanchor.corpus.CorpusStore
import org.mindanchor.data.AppearancePrefs
import org.mindanchor.narrate.ModelSlot
import org.mindanchor.narrate.ModelStore
import org.mindanchor.data.NotificationPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.ui.NatureScene
import org.mindanchor.notifications.BatchAlarms
import org.mindanchor.notifications.BatchSchedule
import org.mindanchor.notifications.BatchReleaser
import org.mindanchor.report.Coverage
import org.mindanchor.report.CoverageLedger
import org.mindanchor.report.ReportStore
import org.mindanchor.report.ReportScheduler
import org.mindanchor.sleep.Deviation
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.sleep.SleepSummary
import org.mindanchor.sunset.SunsetController
import org.mindanchor.vitals.DailyVitals
import org.mindanchor.vitals.HealthConnectSource
import java.time.LocalDate

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = NotificationPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)
    private val frictionPrefs = org.mindanchor.data.FrictionPrefs(application)
    private val sleepRepository = SleepRepository(application)
    private val appearancePrefs = AppearancePrefs(application)
    private val onboardingPrefs = org.mindanchor.onboarding.OnboardingPrefs(application)
    private val reportStore = ReportStore(application)

    /**
     * What the person said they were struggling with, at onboarding or
     * since. Used to mark the parts of this screen they came for — never
     * to switch anything on for them.
     */
    val goals = onboardingPrefs.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setGoals(goals: Set<org.mindanchor.onboarding.Goal>) {
        viewModelScope.launch { onboardingPrefs.setGoals(goals) }
    }

    /**
     * Pauses that have stopped being pauses — see
     * [org.mindanchor.friction.GateLedger].
     *
     * Only ever read here. This never becomes a notification: somebody
     * having a bad month does not need their phone volunteering that their
     * guards look pointless. They have to come and ask.
     */
    val stalePauses = combine(
        frictionPrefs.gateTallies,
        frictionPrefs.flaggedApps,
    ) { tallies, flagged ->
        val today = java.time.LocalDate.now()
        flagged.mapNotNull { pkg ->
            val tally = tallies[pkg] ?: return@mapNotNull null
            if (org.mindanchor.friction.GateLedger.worthMentioning(tally, today)) {
                pkg to tally
            } else {
                null
            }
        }.sortedBy { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The small things the person said help them — their words only, never
     * seeded with suggestions. See
     * [org.mindanchor.friction.SmallThings] for when they are offered and,
     * more importantly, when they are not.
     */
    val smallThings = frictionPrefs.smallThings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSmallThing(thing: String) {
        viewModelScope.launch { frictionPrefs.addSmallThing(thing) }
    }

    fun removeSmallThing(thing: String) {
        viewModelScope.launch { frictionPrefs.removeSmallThing(thing) }
    }

    /**
     * The user's own self-compassion phrases — see
     * [org.mindanchor.friction.CompassionMoment]. Their
     * words only; the launcher never seeds suggestions
     * (Neff 2003, Linardon 2020 meta).
     */
    val compassionMoments = frictionPrefs.compassionMoments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCompassionMoment(phrase: String) {
        viewModelScope.launch { frictionPrefs.addCompassionMoment(phrase) }
    }

    fun removeCompassionMoment(phrase: String) {
        viewModelScope.launch { frictionPrefs.removeCompassionMoment(phrase) }
    }

    /** Somebody looked at the numbers and kept the pause. Start again. */
    fun keepPause(packageName: String) {
        viewModelScope.launch { frictionPrefs.resetTally(packageName) }
    }

    /** Somebody looked at the numbers and let the pause go. */
    fun dropPause(packageName: String) {
        viewModelScope.launch {
            frictionPrefs.setFlagged(packageName, false)
            frictionPrefs.resetTally(packageName)
        }
    }

    val batchingEnabled = prefs.batchingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val batchedApps = prefs.batchedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(getApplication())
            .contains(getApplication<Application>().packageName)

    fun setBatchingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBatchingEnabled(enabled)
            if (enabled) BatchAlarms.ensureScheduled(getApplication())
        }
    }

    /** When the batches arrive. The person's own, defaulting to the studied dosage. */
    val releaseTimes = prefs.releaseTimes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatchSchedule.DEFAULT_TIMES)

    /**
     * Moves one release time by [byMinutes], or does nothing.
     *
     * Nothing is the right answer when the move would land on another
     * release: see BatchSchedule.nudged for why two batches at the same
     * minute is refused rather than stored. A refused nudge simply leaves
     * the times as they were, which is what the button not appearing to
     * do anything already means to somebody pressing it.
     */
    fun nudgeReleaseTime(slot: Int, byMinutes: Long) {
        viewModelScope.launch {
            val moved = BatchSchedule.nudged(prefs.currentReleaseTimes(), slot, byMinutes)
                ?: return@launch
            if (prefs.setReleaseTimes(moved)) BatchAlarms.ensureScheduled(getApplication())
        }
    }

    fun setAppBatched(packageName: String, batched: Boolean) {
        viewModelScope.launch { prefs.setAppBatched(packageName, batched) }
    }

    fun releaseNow() {
        viewModelScope.launch { BatchReleaser.releaseNow(getApplication()) }
    }

    // --- Sunset mode ---

    val sunsetEnabled = sunsetPrefs.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun hasDndAccess(): Boolean =
        getApplication<Application>()
            .getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true

    /**
     * Hands device ownership back, lifting every suspension first.
     *
     * A way out has to exist and has to be here. Ownership cannot be
     * removed by adb once granted, so if this did not exist the only route
     * back would be wiping the phone — and telling someone their way out
     * of a wellbeing app is a factory reset would be its own small
     * cruelty.
     *
     * [onDone] runs once the release has actually happened, so the screen
     * can re-read ownership rather than keep showing the state it had a
     * moment ago. Without it the section still reads "set up as its own
     * guardian" once it no longer is, which is the kind of lie that makes
     * a person tap the button again.
     */
    fun releaseDeviceOwner(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val chosen = frictionPrefs.flaggedApps.first()
            org.mindanchor.admin.DeviceOwner.release(getApplication(), chosen)
            onDone()
        }
    }

    /**
     * Whether the screen also goes grey through the quiet hours. Kept
     * separate from sunset itself: a quiet phone and a colourless one are
     * different wishes and neither should imply the other.
     */
    val grayscaleAtNight = sunsetPrefs.grayscaleAtNight

    fun setGrayscaleAtNight(enabled: Boolean) {
        viewModelScope.launch {
            sunsetPrefs.setGrayscaleAtNight(enabled)
            // Apply immediately if the quiet hours have already begun,
            // rather than leaving the switch looking broken until 22:00.
            val inWindow = sunsetPrefs.isQuietHour()
            if (inWindow || !enabled) {
                org.mindanchor.grayscale.Grayscale.set(getApplication(), enabled && inWindow)
            }
            SunsetController.onToggled(getApplication(), enabled || sunsetPrefs.isEnabled())
        }
    }

    fun setSunsetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sunsetPrefs.setEnabled(enabled)
            SunsetController.onToggled(getApplication(), enabled)
        }
    }

    val sunsetStart = sunsetPrefs.startTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SunsetPrefs.DEFAULT_START)

    val sunsetEnd = sunsetPrefs.endTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SunsetPrefs.DEFAULT_END)

    /**
     * Moves either end of the quiet hours by [startMinutes] / [endMinutes].
     *
     * Steppers rather than a clock dialog: the targets are large, which
     * matters for anyone with tremor or in distress, and nudging is what
     * people actually do to a bedtime — half an hour at a time, not by
     * typing an exact number.
     *
     * The alarms are re-armed afterwards. They are held by AlarmManager at
     * the old times, and nothing else would ever move them — the window
     * would look changed in settings and behave exactly as before.
     */
    fun nudgeSunset(startMinutes: Long, endMinutes: Long) {
        viewModelScope.launch {
            val (start, end) = sunsetPrefs.window()
            val moved = sunsetPrefs.setWindow(
                start.plusMinutes(startMinutes),
                end.plusMinutes(endMinutes),
            )
            if (moved) SunsetController.ensureScheduled(getApplication())
        }
    }

    // --- Sleep rhythm ---

    private val sleepState = MutableStateFlow<SleepSummary?>(null)
    val sleepSummary = sleepState.asStateFlow()

    init {
        refreshSleep()
        // The Health Connect status is read on the first UI
        // composition of the wearable section rather than here:
        // the underlying [MutableStateFlow] is declared further
        // down this class, and Kotlin initialises properties
        // top-to-bottom before running any [init] block, so an
        // init-time call to [refreshHealthConnectStatus] would
        // touch a [StateFlow.setValue] on a still-null field.
        // The settings UI calls [refreshHealthConnectStatus] from
        // a [LaunchedEffect] on first composition, which is
        // strictly after the ViewModel is fully built.
    }

    fun hasUsageAccess(): Boolean = sleepRepository.hasUsageAccess()

    val sleepMirror = sunsetPrefs.sleepMirror
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSleepMirror(enabled: Boolean) {
        viewModelScope.launch { sunsetPrefs.setSleepMirror(enabled) }
    }

    /**
     * How many of the recent nights ran later than this person's own
     * usual, or null when there is nothing honest to say.
     *
     * Null covers three separate cases and deliberately renders as
     * silence in all of them: the mirror is off, there are too few nights
     * to have a usual, or the week was steady. A screen that reported
     * "nothing unusual" every day would have taught somebody to check it.
     */
    val nightsLaterThanUsual: StateFlow<Int?> = combine(
        sunsetPrefs.sleepMirror,
        sleepState,
    ) { on, summary ->
        if (!on || summary == null) return@combine null
        val onsets = summary.windows.map {
            val time = java.time.Instant.ofEpochMilli(it.startMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
            Deviation.minutesAfterSixPm(time.hour * 60 + time.minute)
        }
        if (Deviation.worthShowing(onsets)) Deviation.laterThanUsual(onsets) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshSleep() {
        viewModelScope.launch(Dispatchers.IO) {
            sleepState.value = sleepRepository.estimate()
        }
    }

    // --- Home-screen appearance ---

    val natureScene = appearancePrefs.scene
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NatureScene.ROTATE)

    fun setNatureScene(scene: NatureScene) {
        viewModelScope.launch { appearancePrefs.setScene(scene) }
    }

    // --- Check-ins (EMA) ---

    private val momentStore = org.mindanchor.model.MomentStore(application)

    val emaEnabled = momentStore.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** How many check-ins exist, for a plain count — never a streak. */
    val emaCount = momentStore.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setEmaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            momentStore.setEnabled(enabled)
            // Arms today's prompts when switched on; clears every armed
            // alarm when switched off. Same call either way — see
            // EmaScheduler.ensureScheduled.
            org.mindanchor.model.EmaScheduler.ensureScheduled(getApplication())
        }
    }

    // --- Last night's look (nightly report) ---

    val reportEnabled = reportStore.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setReportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            reportStore.setEnabled(enabled)
            // Arms the nightly alarm when switched on; cancels it when
            // switched off. Calling this again on an already-armed
            // schedule replaces the one alarm rather than stacking a
            // second — see ReportScheduler.ensureScheduled.
            if (enabled) {
                ReportScheduler.ensureScheduled(getApplication())
            } else {
                ReportScheduler.cancel(getApplication())
            }
        }
    }

    // --- Proving the pipeline on this phone ---
    //
    // The nightly build runs unattended, and "it will have worked" is an
    // assumption this project no longer makes anywhere. These exist so
    // the person can run the whole pipeline once, on demand, and read
    // per-signal facts about what is actually arriving — instead of
    // discovering in week three that a source was silent all along.

    val reportGeneratedDay = reportStore.generatedDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _reportRunning = MutableStateFlow(false)
    val reportRunning: StateFlow<Boolean> = _reportRunning.asStateFlow()

    fun runReportNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _reportRunning.value = true
            ReportScheduler.runNow(getApplication())
            _reportRunning.value = false
        }
    }

    /** Per-signal coverage from the last build, or null before any build. */
    val coverage: StateFlow<List<Coverage>?> = reportStore.coverage
        .map { encoded -> encoded?.let(CoverageLedger::decode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _probe = MutableStateFlow<DailyVitals?>(null)

    /** What Health Connect held for yesterday, read on demand. */
    val probe: StateFlow<DailyVitals?> = _probe.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    /**
     * What Health Connect looks like on this device, for the permission
     * grant flow. The launcher never reads a byte of wearable data
     * without the user having first seen the system dialog and tapped
     * allow — this state drives the "Connect to your watch" button in
     * the settings UI.
     */
    sealed interface HealthConnectStatus {
        data object Unknown : HealthConnectStatus
        data object Unavailable : HealthConnectStatus
        data class Available(val granted: Int, val total: Int) : HealthConnectStatus
    }

    private val _healthConnectStatus = MutableStateFlow<HealthConnectStatus>(HealthConnectStatus.Unknown)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    /**
     * Recompute the Health Connect permission state. Called on the
     * settings screen's first composition, after a permission flow
     * returns, and any time the launcher returns to the foreground.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            _healthConnectStatus.value = if (!HealthConnectSource.isAvailable(app)) {
                HealthConnectStatus.Unavailable
            } else {
                val granted = HealthConnectSource.grantedPermissions(app).size
                HealthConnectStatus.Available(
                    granted = granted,
                    total = HealthConnectSource.PERMISSIONS.size,
                )
            }
        }
    }

    /**
     * Reads yesterday straight from Health Connect, right now.
     *
     * The one honest way to learn what a particular watch actually
     * exports is to look — the vendor documentation for this project's
     * own watch turned out to describe less than the signal list hoped
     * for, and the next watch will differ again.
     */
    fun probeYesterday() {
        viewModelScope.launch(Dispatchers.IO) {
            _probing.value = true
            _probe.value = runCatching {
                HealthConnectSource.readDailyVitals(getApplication(), LocalDate.now().minusDays(1))
            }.getOrNull()
            _probing.value = false
        }
    }

    // --- Research on file (the corpus behind every report) ---

    private val _corpusSize = MutableStateFlow(0)

    /** How many passages the report has to draw on. */
    val corpusSize: StateFlow<Int> = _corpusSize.asStateFlow()

    private val _corpusImported = MutableStateFlow(false)

    /** Whether anything has been added on top of the bundled seed. */
    val corpusImported: StateFlow<Boolean> = _corpusImported.asStateFlow()

    private val _lastImport = MutableStateFlow<CorpusImportReport?>(null)

    /**
     * What the last import did, or null before one has happened this
     * session. Deliberately not persisted: it is a reply to a tap, and a
     * reply still sitting there a week later is not news, it is clutter.
     */
    val lastImport: StateFlow<CorpusImportReport?> = _lastImport.asStateFlow()

    fun refreshCorpus() {
        viewModelScope.launch(Dispatchers.IO) {
            _corpusSize.value = CorpusStore.load(getApplication()).size
            _corpusImported.value = CorpusStore.hasImported(getApplication())
        }
    }

    /**
     * Reads a picked file, merges it into what is already on file, and
     * stores the result.
     *
     * All of it on [Dispatchers.IO]: this reads a file of unknown size
     * off storage the app does not own, and doing that on the main thread
     * is how a settings screen freezes on somebody's slow SD card.
     *
     * A file that yields nothing usable is reported and **not** written.
     * Overwriting a working corpus with the result of a mis-tap would be
     * a destructive answer to a harmless mistake.
     */
    fun importCorpus(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val raw = CorpusStore.readPicked(context, uri)
            if (raw == null) {
                _lastImport.value = CorpusImportReport(unreadable = true)
                return@launch
            }
            val outcome = CorpusImport.merge(CorpusStore.load(context), raw)
            val stored = if (outcome.isEmpty) true else CorpusStore.saveImported(context, outcome.corpus)
            _lastImport.value = CorpusImportReport(
                added = outcome.added,
                replaced = outcome.replaced,
                skippedRows = outcome.skippedRows,
                truncated = outcome.truncated,
                unreadable = !stored,
            )
            _corpusSize.value = CorpusStore.load(context).size
            _corpusImported.value = CorpusStore.hasImported(context)
        }
    }

    /** Back to the bundled seed alone. */
    fun clearCorpus() {
        viewModelScope.launch(Dispatchers.IO) {
            CorpusStore.clearImported(getApplication())
            _lastImport.value = null
            _corpusSize.value = CorpusStore.load(getApplication()).size
            _corpusImported.value = CorpusStore.hasImported(getApplication())
        }
    }

    // --- Model (the small model a future writing engine would run) ---
    //
    // Mirrors the corpus section immediately above: a plain file import
    // into app-private storage, with the whole read and copy on
    // Dispatchers.IO because ModelStore is moving a multi-gigabyte file
    // off storage the app does not own. See ModelStore and Narrator for
    // why importing one does not yet make any writing happen.

    private val _modelPresent = MutableStateFlow(false)

    /** Whether a model is on file at all. */
    val modelPresent: StateFlow<Boolean> = _modelPresent.asStateFlow()

    private val _modelFit = MutableStateFlow(ModelSlot.Fit.TOO_LARGE)

    /**
     * Whether the model on file would actually run here. Meaningless
     * while [modelPresent] is false, where it defaults to the same
     * refuse-by-default value [ModelStore.fit] itself falls back to.
     */
    val modelFit: StateFlow<ModelSlot.Fit> = _modelFit.asStateFlow()

    private val _modelImportFailed = MutableStateFlow(false)

    /** Whether the most recent import attempt this session failed. */
    val modelImportFailed: StateFlow<Boolean> = _modelImportFailed.asStateFlow()

    fun refreshModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            _modelPresent.value = ModelStore.hasModel(context)
            _modelFit.value = ModelStore.fit(context)
        }
    }

    /**
     * Reads a picked file into app-private storage, replacing whatever
     * model was there before.
     *
     * A failed import leaves the previous model, if any, untouched — see
     * [ModelStore.importFrom] for why a failed copy never leaves a
     * partial file to be mistaken for a real one.
     */
    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val imported = ModelStore.importFrom(context, uri)
            _modelImportFailed.value = !imported
            _modelPresent.value = ModelStore.hasModel(context)
            _modelFit.value = ModelStore.fit(context)
        }
    }

    /** Removes the model on file, if any. */
    fun clearModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            ModelStore.clear(context)
            _modelImportFailed.value = false
            _modelPresent.value = ModelStore.hasModel(context)
            _modelFit.value = ModelStore.fit(context)
        }
    }

    // --- Wellness signals (N-of-1, from Health Connect) ---
    //
    // The home card and the settings section both read the same flow,
    // so the two surfaces can never disagree about what is being
    // shown. The flow is null until the first refresh completes —
    // a "still loading" state that is not a "no data" state, so the
    // UI can render a quiet placeholder rather than the more loaded
    // "no data" state when the device is mid-startup.
    //
    // The refresh is launched on every ON_RESUME (via the
    // [permissionEpoch] pattern used elsewhere on this screen) so
    // the home card and the settings panel re-read the moment the
    // launcher comes back to the foreground after a Health Connect
    // permission grant.

    private val _wellnessReadings = MutableStateFlow<List<org.mindanchor.vitals.WellnessReading>?>(null)
    val wellnessReadings: StateFlow<List<org.mindanchor.vitals.WellnessReading>?> = _wellnessReadings.asStateFlow()

    /**
     * The in-flight wellness refresh, if any. Held so a fresh
     * [refreshWellness] call (e.g. when the settings screen returns
     * to the foreground, or a Health Connect permission grant
     * lands) cancels the previous run before starting the new one:
     * the readings pipeline reads and writes the wellness DataStore
     * on every call, and the launcher would otherwise run two
     * `readingsFor` operations in parallel and pick the one that
     * finished last, regardless of which one started last.
     */
    private var wellnessJob: kotlinx.coroutines.Job? = null

    fun refreshWellness() {
        wellnessJob?.cancel()
        wellnessJob = viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val readings = runCatching {
                org.mindanchor.vitals.WellnessRepository(app).readingsFor(LocalDate.now())
            }.getOrDefault(emptyList())
            _wellnessReadings.value = readings
        }
    }
}

/** What an import did, in the terms the settings screen reports it. */
data class CorpusImportReport(
    val added: Int = 0,
    val replaced: Int = 0,
    val skippedRows: Int = 0,
    val truncated: Boolean = false,
    val unreadable: Boolean = false,
)
