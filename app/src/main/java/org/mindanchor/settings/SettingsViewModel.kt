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
import org.mindanchor.notifications.BatchReleaser
import org.mindanchor.report.ReportStore
import org.mindanchor.report.ReportScheduler
import org.mindanchor.sleep.Deviation
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.sleep.SleepSummary
import org.mindanchor.sunset.SunsetController

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
}

/** What an import did, in the terms the settings screen reports it. */
data class CorpusImportReport(
    val added: Int = 0,
    val replaced: Int = 0,
    val skippedRows: Int = 0,
    val truncated: Boolean = false,
    val unreadable: Boolean = false,
)
