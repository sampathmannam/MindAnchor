@file:Suppress("MaxLineLength", "FunctionNaming", "LongMethod", "MagicNumber")
package org.mindanchor.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.mindanchor.R
import org.mindanchor.digest.DigestActivity
import org.mindanchor.friction.FrictionGate
import org.mindanchor.friction.FrictionTone
import org.mindanchor.friction.GateContext
import org.mindanchor.friction.LoopPhase
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterScreen
import org.mindanchor.letters.LetterStore
import org.mindanchor.model.Note
import org.mindanchor.model.NoteActivity
import org.mindanchor.reader.ReadingSize
import org.mindanchor.report.ReportScreen
import org.mindanchor.report.ReportStore
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.vitals.PpgScreen
import org.mindanchor.vitals.WellnessDirection
import org.mindanchor.vitals.WellnessReading
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.support.SupportActivity
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.rememberClockFormat
import org.mindanchor.ui.rememberMinuteTick
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver

private enum class LauncherSurface {
    Home,
    Drawer,
    Settings,
    Ppg,
    Report,
    Letter,
    // v0.26.0
    GroundMe,
    BeforeYouSend,
    // v0.35.0: the "Get through this" sub-menu. A
    // sibling of the home, the settings, and the
    // drawer; not a separate activity because the
    // three reflective actions it surfaces are
    // existing activities, and a sub-menu is
    // cheaper to navigate between than a fresh
    // Intent trip.
    GetThrough,
}

/**
 * v0.25.15: the custom Saver for [DisplayApp?] that lets
 * [rememberSaveable] hold the "actions for" / "gate for"
 * launcher state across config change and process death.
 *
 * The default autoSaver for [DisplayApp] would not work
 * (the data class has 4 fields and Bundle has its own
 * parcelable contract). A [mapSaver] keyed on the
 * component-name is the documented Compose pattern for
 * "I have a small data class, give me a Saver": the
 * save side returns a `Map<String, Any?>` of the four
 * fields, the restore side walks the map back into the
 * data class. `null` is encoded as an empty map — the
 * mapSaver contract is "non-null map means there was a
 * state; empty map means the state was null".
 *
 * Why save the label and the favourite/hidden flags
 * rather than just the component name: the renamed
 * label and the favourite/hidden state are exactly
 * what the long-press dialog is editing, and losing
 * them on a config change would silently revert the
 * user's edit. The ComponentName itself is the join
 * key; the other three fields ride along.
 */
private val DisplayAppNullableSaver: Saver<DisplayApp?, Any> = mapSaver(
    save = { app ->
        if (app == null) {
            emptyMap<String, Any>()
        } else {
            mapOf(
                "component" to app.component,
                "label" to app.label,
                "isFavorite" to app.isFavorite,
                "isHidden" to app.isHidden,
            )
        }
    },
    restore = { raw ->
        @Suppress("UNCHECKED_CAST")
        val map = raw as? Map<String, Any?> ?: return@mapSaver null
        val component = map["component"] as? String ?: return@mapSaver null
        val label = map["label"] as? String ?: return@mapSaver null
        val isFavorite = map["isFavorite"] as? Boolean ?: false
        val isHidden = map["isHidden"] as? Boolean ?: false
        DisplayApp(
            component = component,
            label = label,
            isFavorite = isFavorite,
            isHidden = isHidden,
        )
    },
)

/**
 * v0.25.15: the custom Saver for [LocalDate?] that lets
 * [rememberSaveable] hold the letter reader's selected
 * date across config change and process death. Encoded
 * as the ISO-8601 local date string
 * (`DateTimeFormatter.ISO_LOCAL_DATE` →
 * `"2026-08-14"`) and restored via `LocalDate.parse`.
 * `null` round-trips as the empty string, again so the
 * autoSaver has a non-null value to Bundle.
 */
private val LocalDateNullableSaver: Saver<LocalDate?, Any> = Saver(
    save = { date -> date?.format(DateTimeFormatter.ISO_LOCAL_DATE).orEmpty() },
    restore = { raw ->
        val str = raw as? String ?: return@Saver null
        if (str.isEmpty()) null else runCatching { LocalDate.parse(str) }.getOrNull()
    },
)

/**
 * v0.20.9: Modifier extension that auto-scrolls the nearest
 * scrollable ancestor to bring the receiving composable into
 * view when it gains focus. The home surface has three input
 * surfaces (the open-loop capture line, the bedtime-list lines,
 * the quick-notes input) and the soft keyboard would otherwise
 * cover whichever line is focused — the user could not see
 * what they were typing. The bedtime list in particular has
 * up to five lines, and the one being typed into could be the
 * bottom one, well below the visible scroll area once the
 * keyboard is up.
 *
 * The pattern is the standard Compose one: a
 * [BringIntoViewRequester] registered with the input's
 * modifier, called from a coroutine when the input gains
 * focus. The scroll container picks up the request and
 * scrolls the minimum needed to expose the focused field.
 *
 * Returns a [Modifier] so the caller can chain further
 * modifiers (e.g. fillMaxWidth, padding) before applying.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewOnFocus(): Modifier {
    // The factory is `BringIntoViewRequester()` (top-
    // level function in the relocation package);
    // there is no `rememberBringIntoViewRequester` in
    // Compose Foundation 1.7.x. Wrapping it in
    // remember gives one instance per field per
    // composition.
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch { requester.bringIntoView() }
            }
        }
}

/**
 * Root of the launcher UI. Three surfaces: the calm home (clock, greeting,
 * favorites), the search-first app drawer, and settings. No grid, no icons,
 * no badges — text only (CONCEPT.md §3.2).
 */
@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel = viewModel(),
    /** Bumped whenever the home button is pressed; see HomeActivity. */
    goHomeSignal: Int = 0,
    /**
     * v0.25.2-A (Task 8): when the user taps a letter notification,
     * HomeActivity writes the letter's date here. The launcher navigates
     * to the reader for that date, then signals back via
     * [onLetterDateConsumed] so the activity clears the value. The
     * reset is what makes a re-tap for the same date work — without
     * it, the flow would not re-emit and the second tap would be a
     * silent no-op.
     */
    letterDateSignal: LocalDate? = null,
    /**
     * v0.25.2-A (Task 8): invoked after the launcher has applied a
     * [letterDateSignal]. HomeActivity uses this to clear its
     * `MutableStateFlow` so a configuration change does not re-trigger
     * the same navigation.
     */
    onLetterDateConsumed: () -> Unit = {},
) {
    // v0.25.14: collectAsStateWithLifecycle on all 7 LauncherRoot flows so the
    // collector stops when the screen is STOPPED. With collectAsState, a
    // backgrounded home screen would keep recomposing on every preference
    // change, every notes write, every wellness tick — the StateFlow is
    // never paused. collectAsStateWithLifecycle ties the collector to the
    // Compose tree's lifecycle, which is what BackgroundedState in the
    // BUG-004 finding test was probing.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openLoop by viewModel.openLoop.collectAsStateWithLifecycle()
    val oneThing by viewModel.oneThing.collectAsStateWithLifecycle()
    val recentNotes by viewModel.notes.collectAsStateWithLifecycle()
    val wellnessReadings by viewModel.wellnessReadings.collectAsStateWithLifecycle()
    // v0.35.0: the data-sources card reads three StateFlows.
    // Each is a cold read of a per-source DataStore; the
    // WhileSubscribed(5_000) in the VM keeps a backgrounded
    // home from paying the read cost. The collectAsStateWithLifecycle
    // is the BUG-004 primitive: no collection while STOPPED.
    val healthConnectStatus by viewModel.healthConnectStatus.collectAsStateWithLifecycle()
    val corosDataStatus by viewModel.corosDataStatus.collectAsStateWithLifecycle()
    val ppgLastMeasurement by viewModel.ppgLastMeasurement.collectAsStateWithLifecycle()
    // v0.26.0 §3.5
    val ctx = LocalContext.current
    val bpdProfilePrefs = remember { org.mindanchor.data.BpdProfilePrefs(ctx.applicationContext) }
    val bpdProfile by bpdProfilePrefs.profile.collectAsStateWithLifecycle(initialValue = org.mindanchor.data.BpdProfile())
    // v0.42.0: the 2x2 needs grid on the home surface is gated
    // by a preference (Settings → Home screen → Show needs
    // grid). Default `true` so first-launch users see the same
    // home they had in v0.41.0; existing users who already
    // configured the setting get the value they wrote.
    val appearancePrefs = remember { org.mindanchor.data.AppearancePrefs(ctx.applicationContext) }
    val needsGridVisible by appearancePrefs.needsGridVisible
        .collectAsStateWithLifecycle(initialValue = true)
    // v0.26.5: the onStayUp callback writes `okAtNight = true`
    // to the BpdProfile DataStore; the flow re-emits, isTwoAmWindow
    // recomputes to false, and the shell disappears on the next
    // composition. rememberCoroutineScope is the right scope for a
    // one-shot DataStore write from a tap callback (lives as long
    // as the composition, not the activity).
    val bpdProfileScope = rememberCoroutineScope()
    val nowTick = rememberMinuteTick()
    val isTwoAmWindow = NowWhatHeuristic.shouldShow(
        currentHour = nowTick.hour,
        okAtNight = bpdProfile.okAtNight,
    )
    // v0.25.15: the 3 deferred LauncherRoot state fields are now
    // rememberSaveable too. `actionsFor` and `gateFor` hold
    // `DisplayApp?` and use the file-level `DisplayAppNullableSaver`
    // (mapSaver, component-name key) so the value survives a config
    // change or process death. `letterSelectedDate` holds
    // `LocalDate?` and uses `LocalDateNullableSaver` (ISO-8601
    // string round-trip). See the KDoc on the Savers for why a
    // generic `Saver<Any, _>` over the standard autoSaver is the
    // right shape here.
    var surface by rememberSaveable { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by rememberSaveable(stateSaver = DisplayAppNullableSaver) {
        mutableStateOf<DisplayApp?>(null)
    }
    var gateFor by rememberSaveable(stateSaver = DisplayAppNullableSaver) {
        mutableStateOf<DisplayApp?>(null)
    }

    // Where the report was opened from, so back returns there. Two ways
    // in now — the settings section and a line on the home screen — and
    // sending somebody who came from home into settings would be a small
    // daily disorientation.
    var reportCameFrom by rememberSaveable { mutableStateOf(LauncherSurface.Settings) }
    // v0.25.2-A (Task 6): the letter inbox + reader. Same shape as
    // reportCameFrom — selected date is null on the inbox, non-null
    // in the reader; cameFrom remembers where the user came from so
    // the inbox's back button returns there. Two entry points: the
    // new "letters" TopEnd corner on the home surface, the (later)
    // Reading sub-section in Settings, and the letter notification
    // (Task 8), which writes letterDateSignal from HomeActivity.
    // v0.25.15: `letterSelectedDate` is rememberSaveable via the
    // ISO-string `LocalDateNullableSaver` so a config change while
    // the user is reading a letter preserves the open reader.
    var letterSelectedDate by rememberSaveable(stateSaver = LocalDateNullableSaver) {
        mutableStateOf<LocalDate?>(null)
    }
    var letterCameFrom by rememberSaveable { mutableStateOf(LauncherSurface.Home) }
    val context = LocalContext.current
    val reportStore = remember(context) { ReportStore(context.applicationContext) }
    // v0.25.17 BUG-004: lifecycle-aware collect. The
    // report-store flow emits when a fresh nightly
    // report is composed; pre-v0.25.17 the launcher
    // kept listening to the flow even when the home
    // surface was STOPPED (Settings, Onboarding,
    // etc.).
    val storedReport by reportStore.stored.collectAsStateWithLifecycle(initialValue = null)
    // Only when there is genuinely something to read. An empty report is
    // ReportComposer's ordinary, good outcome, and offering a way in to
    // read nothing teaches somebody to stop looking.
    val hasReport = storedReport?.let {
        it.patterns.isNotEmpty() || !it.narration.isNullOrBlank() || !it.report.isEmpty
    } == true

    // Pressing home while deep in the drawer or settings must land on the
    // home surface — otherwise the launcher "sticks" wherever you left it.
    LaunchedEffect(goHomeSignal) {
        if (goHomeSignal > 0) {
            gateFor = null
            actionsFor = null
            surface = LauncherSurface.Home
            viewModel.onQueryChange("")
        }
    }

    // v0.20.5: refresh wellness on every transition into the
    // home surface. The readings are cached in the ViewModel
    // for 5s by WhileSubscribed, but the first composition
    // after a Health Connect permission grant — the most
    // likely moment the user opens the launcher — is exactly
    // the moment the data is freshest. The same goHomeSignal
    // path that handles "press home from deep in the app"
    // also handles "press home from settings", which is the
    // path the user takes after granting permission.
    LaunchedEffect(goHomeSignal) {
        if (goHomeSignal >= 0) viewModel.refreshWellness()
    }

    // v0.25.2-A (Task 8): letter notification side-channel. When the
    // user taps a letter notification, HomeActivity writes the letter's
    // date into letterDateSignal. We navigate to the letter reader
    // for that date, then clear the signal so a configuration change
    // does not re-trigger the same navigation and so a re-tap of the
    // same date emits a fresh value. Same shape as the goHomeSignal
    // LaunchedEffect above — an activity-owned flow the launcher
    // reacts to on every recomposition.
    LaunchedEffect(letterDateSignal) {
        val date = letterDateSignal ?: return@LaunchedEffect
        letterSelectedDate = date
        letterCameFrom = LauncherSurface.Home
        surface = LauncherSurface.Letter
        onLetterDateConsumed()
    }

    // Settings has its own [BackHandler] now: when a
    // group is open, the first back closes the group
    // and the second leaves Settings for Home. Leaving
    // surface==Settings in the predicate means our
    // global back does not steal the press from the
    // settings screen — every prior version did, which
    // is why the section index used to disappear on
    // the way out.
    BackHandler(enabled = (surface != LauncherSurface.Home && surface != LauncherSurface.Settings) || gateFor != null) {
        gateFor = null
        surface = LauncherSurface.Home
        viewModel.onQueryChange("")
    }

    fun attemptLaunch(app: DisplayApp) {
        val packageName = app.component.substringBefore('/')
        if (packageName in state.frictionPackages) {
            gateFor = app
        } else {
            viewModel.launch(app)
            surface = LauncherSurface.Home
        }
    }

    gateFor?.let { app ->
        // The tone and the optional extras (small thing, if-then
        // plan, compassion moment) all depend on disk reads. Nothing
        // is drawn until they resolve — showing the full breath and
        // then swapping it for a lighter prompt would be worse than
        // the brief blank the sky already covers.
        var gate by remember(app) { mutableStateOf<GateContext?>(null) }
        LaunchedEffect(app) { gate = viewModel.gateFor(app) }
        val resolved = gate
        if (resolved == null) {
            // Hold the sky. Falling through here would draw the home screen
            // for a frame between tapping an app and the pause appearing,
            // which is the flash this launcher has already been fixed for
            // once.
            CalmBackground { }
        } else {
            FrictionGate(
                tone = resolved.tone,
                appLabel = app.label,
                smallThing = resolved.smallThing,
                ifThenPlan = resolved.ifThenPlan,
                compassionMoment = resolved.compassionMoment,
                perAppSessionLength = resolved.perAppSessionLength,
                packageName = resolved.packageName,
                // v0.20.1 round 4 (item M): the per-app
                // session-length "Learn this for next time"
                // toggle. The gate invokes this callback
                // only when the toggle is on at the moment
                // of the tap. The launcher records the
                // choice via FrictionPrefs and the change
                // is picked up on the next reach.
                onTimeBoxPicked = { pkg, minutes ->
                    viewModel.recordPerAppSessionLength(pkg, minutes)
                },
                // v0.20.1 round 5 follow-up: forget the
                // per-app default. The launcher clears the
                // map entry and the next reach will show
                // the "Learn this for next time" toggle
                // again, as if the user had never picked.
                onForgetDefault = { pkg ->
                    viewModel.clearPerAppSessionLength(pkg)
                },
                // Taking the small thing is leaving, not entering. It
                // counts as backing out for the same reason "never mind"
                // does: the person met the pause and did not go in.
                onSmallThingTaken = {
                    viewModel.recordNeverMind(app, resolved.banditArm)
                    gateFor = null
                    surface = LauncherSurface.Home
                },
                onOpen = { minutes ->
                    viewModel.launchTimed(app, minutes, resolved.banditArm)
                    gateFor = null
                    surface = LauncherSurface.Home
                },
                onNeverMind = {
                    viewModel.recordNeverMind(app, resolved.banditArm)
                    gateFor = null
                    surface = LauncherSurface.Home
                },
            )
        }
        return
    }

    when (surface) {
        // v0.26.0 §3.5
        LauncherSurface.Home ->
            if (isTwoAmWindow) {
                NowWhatShell(
                    onWantSleep = { surface = LauncherSurface.Home },
                    onWantGround = { surface = LauncherSurface.GroundMe },
                    onWantTalk = {
                        runCatching {
                            val supportIntent = android.content.Intent(context, SupportActivity::class.java)
                            context.startActivity(supportIntent)
                        }
                    },
                    // v0.26.5: 4th option. Toggle okAtNight in
                    // BpdProfile (DataStore `bpd_ok_at_night`
                    // pref) — the next composition reads the new
                    // value via collectAsStateWithLifecycle and
                    // isTwoAmWindow flips false, so the shell
                    // disappears. The same Settings checkbox
                    // (BpdProfileCheckbox) is the way to revert.
                    onStayUp = {
                        bpdProfileScope.launch {
                            bpdProfilePrefs.update(bpdProfile.copy(okAtNight = true))
                        }
                    },
                )
            } else {
                CalmBackground { sky ->
                    // v0.25.17 BUG-004: lifecycle-aware collect.
                    // Same rationale as the report-store flow
                    // above. The intro-callout flag is read
                    // only when the home surface is foreground.
                    val showIntroCallout by viewModel.showIntroCallout.collectAsStateWithLifecycle()
            HomeSurface(
                sky = sky,
                favorites = state.favorites,
                // v0.42.0: hide the 2x2 needs grid when the user
                // has turned it off in Settings. The four doors
                // collapse to nothing; the time, greeting, and
                // quick-notes card remain. Support is still
                // reachable from the top-left "Open Support"
                // button and from Settings.
                needsGridVisible = needsGridVisible,
                onOpenDrawer = { surface = LauncherSurface.Drawer },
                onOpenSettings = { surface = LauncherSurface.Settings },
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
                loopPhase = openLoop.first,
                loopNote = openLoop.second,
                loopPostponedAt = openLoop.third,
                onLoopSave = viewModel::saveOpenLoop,
                onLoopClear = viewModel::clearOpenLoop,
                onLoopPostpone = viewModel::postponeOpenLoop,
                onLoopCancelPostpone = viewModel::cancelOpenLoopPostponement,
                // v0.28.0: open the Distress Thermometer activity.
                // The home card's "Ground me here" button routes here.
                // The activity is non-exported; a misconfigured manifest
                // would silently fail without the runCatching wrapper.
                onOpenDistressThermometer = {
                    runCatching {
                        val distressIntent = android.content.Intent(
                            context,
                            org.mindanchor.support.DistressThermometerActivity::class.java,
                        )
                        context.startActivity(distressIntent)
                    }
                },
                onOpenGroundMe = { surface = LauncherSurface.GroundMe },
                recentNotes = recentNotes,
                onAddQuickNote = viewModel::addQuickNote,
                hasReport = hasReport,
                onOpenReport = {
                    reportCameFrom = LauncherSurface.Home
                    surface = LauncherSurface.Report
                },
                onOpenNotes = {
                    // v0.20.1 round 5: route to the
                    // notes activity. runCatching
                    // because a misconfigured
                    // manifest is the easiest way to
                    // ship a broken entry point, and
                    // the cost of catching is one
                    // try-frame, not a UX failure.
                    runCatching {
                        val notesIntent = android.content.Intent(
                            context, org.mindanchor.model.NoteActivity::class.java,
                        )
                        context.startActivity(notesIntent)
                    }
                },
                onOpenCheckInHistory = {
                    // v0.20.1 round 5 follow-up:
                    // route to the check-in history.
                    // Same runCatching pattern as the
                    // notes entry — defensive against
                    // a misconfigured manifest.
                    runCatching {
                        val historyIntent = android.content.Intent(
                            context, org.mindanchor.model.CheckInHistoryActivity::class.java,
                        )
                        context.startActivity(historyIntent)
                    }
                },
                // v0.25.2-A (Task 6): the "letters" TopEnd
                // corner. Wired here so the lambda body has
                // access to the letter state (selectedDate,
                // cameFrom) and the surface dispatcher. The
                // Settings entry will pass a sibling lambda
                // with cameFrom = LauncherSurface.Settings.
                onOpenLetters = {
                    letterSelectedDate = null
                    letterCameFrom = LauncherSurface.Home
                    surface = LauncherSurface.Letter
                },
                // v0.26.4 §3.4: the 3 BPD entry points. Each
                // is a runCatching because the activity is
                // not-exported; an unconfigured manifest is
                // the easiest way to ship a broken entry
                // point, and a single try-frame is not a
                // UX failure. Same defensive pattern as
                // onOpenNotes + onOpenCheckInHistory.
                onOpenChainCapture = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.chain.ChainCaptureActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenIfsPicker = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.ifs.IfsPickerActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenExport = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.export.ExportActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                // v0.35.0: the four needs-card doors.
                onOpenSupport = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.support.SupportActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenAccepts = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.support.AcceptsActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenDiaryCard = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.support.DiaryCardActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenGetThrough = {
                    surface = LauncherSurface.GetThrough
                },
                // v0.35.0: the data-sources card reads these
                // three StateFlows. The card is hidden entirely
                // when no source has data; the empty-state
                // visibility rule lives in DataSourcesCard.
                healthConnectStatus = healthConnectStatus,
                corosDataStatus = corosDataStatus,
                ppgLastMeasurement = ppgLastMeasurement,
                wellnessReadings = wellnessReadings,
                showIntroCallout = showIntroCallout,
                onRecordLaunch = viewModel::recordHomeLaunch,
            )
        }
            }

        LauncherSurface.Drawer -> Surface(modifier = Modifier.fillMaxSize()) {
            DrawerSurface(
                viewModel = viewModel,
                state = state,
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
            )
        }

        LauncherSurface.Settings -> Surface(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                allApps = state.allApps,
                hiddenApps = state.allApps.filter { it.isHidden },
                onUnhide = { viewModel.setHidden(it, false) },
                onBack = { surface = LauncherSurface.Home },
                onOpenPpg = { surface = LauncherSurface.Ppg },
                onOpenReport = {
                    reportCameFrom = LauncherSurface.Settings
                    surface = LauncherSurface.Report
                },
                // v0.25.2-A (Task 10): the Daily letter
                // sub-section in Settings has an "Open inbox"
                // button. Routing is the same shape as
                // onOpenReport above — flag the cameFrom so
                // the letter surface's back button returns
                // to Settings rather than to the home screen,
                // and let the letter state default to the
                // inbox (no letter is preselected).
                onOpenLetters = {
                    letterSelectedDate = null
                    letterCameFrom = LauncherSurface.Settings
                    surface = LauncherSurface.Letter
                },
                onOpenBeforeYouSend = { surface = LauncherSurface.BeforeYouSend },
            )
        }

        // Its own surface rather than a section inside the settings scroll.
        // The measurement holds the screen awake and runs the torch for a
        // minute and a half; nesting that inside a screen somebody is
        // scrolling through would mean starting it by accident.
        LauncherSurface.Ppg -> Surface(modifier = Modifier.fillMaxSize()) {
            PpgScreen(onBack = { surface = LauncherSurface.Settings })
        }

        LauncherSurface.Report -> Surface(modifier = Modifier.fillMaxSize()) {
            // Back goes wherever this was opened from. Sending somebody
            // who tapped the line on the home screen into settings would
            // be a small, daily disorientation.
            ReportScreen(onBack = { surface = reportCameFrom })
        }

        // v0.25.2-A (Task 6): the letter inbox + reader. Dispatched
        // here because the parent (HomeScreen) holds the
        // letterSelectedDate / letterCameFrom state — the
        // LetterScreen Composable is otherwise stateless on which
        // date is selected. The back button clears the selected
        // date when in the reader (back to inbox) and falls back
        // to letterCameFrom when in the inbox.
        //
        // v0.25.16 BUG-017: `modelFits` is now wired from
        // `viewModel.modelFits` (a `StateFlow<Boolean>` that
        // reflects the on-disk presence of the Phi-4 model).
        // The pre-v0.25.16 stub held a Composable-level
        // `remember { mutableStateOf(false) }` whose value was
        // always `false` — the Generate-now affordance was
        // permanently disabled. Wiring through the VM is the
        // standard `collectAsStateWithLifecycle` pattern and
        // is what the BUG-017 FindingTest asserts.
        LauncherSurface.Letter -> Surface(modifier = Modifier.fillMaxSize()) {
            val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()
            // v0.25.2-B (Task 15): letter size is read from the
            // LauncherViewModel (mirrors the SettingsViewModel.letterSize
            // from Task 9 — both VMs read from the same DataStore source).
            // v0.25.17 BUG-004: lifecycle-aware collect.
            // The letter-size preference is a DataStore
            // value; reading it through the lifecycle-
            // aware primitive keeps the launcher from
            // collecting on every emission while the
            // letter surface is STOPPED.
            val letterSize by viewModel.letterSize.collectAsStateWithLifecycle()
            val letterStore = remember(context.applicationContext) {
                LetterStore(context.applicationContext)
            }
            val feedbackStore = remember(context.applicationContext) {
                org.mindanchor.letters.LetterFeedbackStore(context.applicationContext)
            }
            // The actual letter list. v0.26.2 finally wires
            // this off LetterStore.letters; the v0.25.x stub
            // (`emptyList()`) meant the inbox was permanently
            // empty. `collectAsStateWithLifecycle` is the
            // SOTA-v2 primitive (see HomeScreen.kt's own
            // LauncherRoot for the BUG-004 fix), and matches
            // the v0.25.14 batch.
            val letters by letterStore.letters.collectAsStateWithLifecycle(
                initialValue = emptyList(),
            )
            val letterScope = rememberCoroutineScope()
            // v0.26.2: build the per-date feedback-count map
            // synchronously on every recomposition. The store
            // is a plain-file read, no IO pump, no Flow; the
            // counts are small (one per letter date on file);
            // the recomposition cost is O(letter count). A
            // user with 30 letters on file pays 30 file
            // existence checks — measured at sub-millisecond
            // on a real device. The cost is fine until
            // somebody reports it isn't.
            val feedbackCounts: Map<LocalDate, Int> = remember(letters) {
                letters.associate { it.date to feedbackStore.countFor(it.date) }
            }
            LetterScreen(
                letters = letters,
                modelFits = modelFits,
                date = letterSelectedDate,
                size = letterSize,
                feedbackCounts = feedbackCounts,
                // v0.25.3-WP-C: a row tap marks the letter as read so
                // the Settings "Open inbox (N)" badge decrements.
                // The mark is idempotent (Set semantics) and the write
                // is on Dispatchers.IO via DataStore.
                onSelect = { date ->
                    letterSelectedDate = date
                    letterScope.launch { letterStore.setRead(date, true) }
                },
                onBack = {
                    if (letterSelectedDate != null) {
                        letterSelectedDate = null
                    } else {
                        surface = letterCameFrom
                    }
                },
                onDelete = { date -> letterScope.launch { letterStore.delete(date) } },
                onSetSize = { size -> viewModel.setLetterSize(size) },
                // v0.26.2: persist a user-authored letter from
                // the empty-state composer. The body comes in
                // from the composer's text field; the date is
                // today. The DataStore write is on the IO
                // dispatcher via the store.
                onSaveUserLetter = { date, body ->
                    letterScope.launch { letterStore.saveUserLetter(date, body) }
                },
                // v0.31.0: the inbox's "Generate now" / "Use
                // AI" affordance now actually runs. The
                // pipeline: collect this week's data via
                // [WeekDataCollector], call [LetterWriter] on
                // the IO dispatcher, save the result as
                // today's letter if the model produced
                // anything safe. The whole call is wrapped
                // in runCatching so a model load failure, a
                // generation timeout, or a [NarrationGuard]
                // rejection never crashes the launcher — the
                // user sees an empty inbox, exactly as they
                // did before v0.31.0.
                //
                // v0.32.1: the work is now hosted by a
                // [org.mindanchor.letters.LettersGenerationService]
                // foreground service. Pre-v0.32.1 the
                // coroutine was tied to this Composable's
                // `letterScope = rememberCoroutineScope()`
                // and died when the user navigated away, or
                // (more often on a 1.8 GB MemAvailable
                // phone) when the OS reaped the process
                // mid-decode. The service holds a partial
                // wake lock, posts an ongoing notification
                // for visibility, and runs until the letter
                // is saved or the run fails. The same
                // pipeline ([WeekDataCollector] →
                // [LetterWriter] → [LetterStore.saveUserLetter])
                // — just hosted in a place that survives
                // the Composable.
                onGenerateNow = {
                    // The Toast is the immediate user-side
                    // confirmation: "yes, the button worked;
                    // the system has the work." The
                    // notification will appear in the
                    // status bar a moment later; that is
                    // the "this is still running" signal.
                    // The letter itself appears in the
                    // inbox when the generation finishes
                    // (and the user gets a one-shot
                    // "Tonight's letter is ready"
                    // notification at that point).
                    //
                    // v0.37.0 (BPD-safety WARN remediation):
                    // the previous copy spelled out the
                    // 30–60 min window, which read as
                    // latency pressure for a person in
                    // distress. The new copy names the
                    // outcome ("in your inbox by morning")
                    // without quantifying the wait, and
                    // keeps "tonight's" as the only time
                    // reference so the user doesn't have
                    // to do the arithmetic.
                    android.widget.Toast.makeText(
                        context.applicationContext,
                        "Started. Tonight's letter will be in your inbox by morning.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    val appContext = context.applicationContext
                    appContext.startForegroundService(
                        org.mindanchor.letters.LettersGenerationService.intent(appContext),
                    )
                },
                // v0.26.2: persist a thumbs-down. The body
                // comes from the feedback dialog's optional
                // text field; the date is the letter's date.
                // The file write is on the IO dispatcher
                // because [LetterFeedbackStore.save] is a
                // blocking `appendText` call.
                onSaveFeedback = { date, reason ->
                    letterScope.launch(Dispatchers.IO) {
                        feedbackStore.save(date, reason)
                    }
                },
            )
        }

        // v0.26.0 §3.2
        LauncherSurface.GroundMe -> GroundMeScreen(
            onClose = { surface = LauncherSurface.Home },
        )
        // v0.26.0 §3.3
        LauncherSurface.BeforeYouSend -> BeforeYouSendDemo(
            onDismiss = { surface = LauncherSurface.Home },
        )
        // v0.35.0: the "Get through this" sub-menu. A
        // stacked surface rather than a fresh activity
        // because the three reflective actions it surfaces
        // are existing activities and a sub-menu is cheaper
        // to navigate between than a fresh Intent trip.
        // The sub-menu routes to the same activities the
        // v0.32.0 "Right now" section did (chain capture,
        // IFS picker, export); the entry point moves from
        // "a section of the home" to "the 4th door of the
        // needs card". The back button on the sub-menu
        // returns to the home, not to the needs card,
        // because the needs card is the surface the user
        // came from.
        LauncherSurface.GetThrough -> Surface(modifier = Modifier.fillMaxSize()) {
            CalmBackground { sky ->
                GetThroughSubMenu(
                    sky = sky,
                    onWhatHappened = {
                        runCatching {
                            val intent = android.content.Intent(
                                context, org.mindanchor.chain.ChainCaptureActivity::class.java,
                            )
                            context.startActivity(intent)
                        }
                    },
                    onWhichPart = {
                        runCatching {
                            val intent = android.content.Intent(
                                context, org.mindanchor.ifs.IfsPickerActivity::class.java,
                            )
                            context.startActivity(intent)
                        }
                    },
                    onExport = {
                        runCatching {
                            val intent = android.content.Intent(
                                context, org.mindanchor.export.ExportActivity::class.java,
                            )
                            context.startActivity(intent)
                        }
                    },
                    onBack = { surface = LauncherSurface.Home },
                )
            }
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isFrictioned = app.component.substringBefore('/') in state.frictionPackages,
            isAlwaysOpen = app.component.substringBefore('/') in state.alwaysOpenPackages,
            onDismiss = { actionsFor = null },
            onToggleFavorite = { viewModel.toggleFavorite(app); actionsFor = null },
            onToggleHidden = { viewModel.setHidden(app, !app.isHidden); actionsFor = null },
            onToggleFriction = { viewModel.toggleFriction(app); actionsFor = null },
            onToggleAlwaysOpen = { viewModel.toggleAlwaysOpen(app); actionsFor = null },
            onRename = { label -> viewModel.rename(app, label); actionsFor = null },
        )
    }
}

/**
 * The one unfinished thing — see [org.mindanchor.friction.OpenLoop].
 *
 * Deliberately silent most of the time. It appears once in the quiet
 * hours to take a line, and once the next morning to give it back, and
 * otherwise draws nothing at all. A home screen that always has something
 * to say is a home screen people stop reading.
 *
 * v0.25.5: a fourth phase, [LoopPhase.POSTPONED], keeps the launcher
 * silent while the user's worry-postponement clock is in the future
 * (Borkovec 1994 + Watkins 2008). The card surfaces a small
 * "Back at HH:MM" line and a "Back to it now" affordance that drops
 * the postponement and falls back to the hand-it-back flow. A
 * "Postpone" button on the RETURN state opens a small dialog with
 * "Later today" / "Tomorrow morning" — the Borkovec protocol is
 * "schedule a specific time", but the user's two most common times
 * are good defaults and "pick a time" can wait.
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
private fun OpenLoopCard(
    sky: SkyContent,
    phase: LoopPhase,
    note: String?,
    postponedAt: Instant?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onPostpone: (Instant) -> Unit,
    onCancelPostpone: () -> Unit,
) {
    // v0.25.10 (SOTA v2 bug-hunt B9): remember the system date once and
    // pass it to formatWallClock so the formatted time and the
    // "tomorrow" comparison come from the same system instant, not two
    // separate reads that could straddle a midnight or DST boundary.
    val today = remember { LocalDate.now() }
    when (phase) {
        LoopPhase.NONE -> Unit

        LoopPhase.CAPTURE -> {
            // v0.25.10 (SOTA v2 bug-hunt B7): rememberSaveable so a
            // captured draft survives config change / process death.
            var draft by rememberSaveable { mutableStateOf("") }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.loop_capture),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.loop_capture_hint)) },
                    // v0.20.9: bringIntoViewOnFocus so the
                    // open-loop capture line scrolls above the
                    // keyboard when focused. The whole
                    // open-loop card sits between the clock
                    // and the bedtime list and would otherwise
                    // be covered by the IME.
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .padding(top = 8.dp),
                )
                TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { onSave(draft) }) {
                    Text(stringResource(R.string.loop_save), color = sky.textPrimary)
                }
            }
        }

        LoopPhase.POSTPONED -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = note.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.loop_postponed_back_at,
                    formatWallClock(postponedAt, today),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = sky.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onCancelPostpone) {
                Text(stringResource(R.string.loop_postponed_cancel), color = sky.textSecondary)
            }
        }

        LoopPhase.RETURN -> {
            // v0.25.10 (SOTA v2 bug-hunt B8): rememberSaveable so a
            // Postpone dialog stays open across a config change.
            var showDialog by rememberSaveable { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.loop_return),
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = note.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = sky.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { showDialog = true }) {
                        Text(stringResource(R.string.loop_postpone), color = sky.textSecondary)
                    }
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onClear) {
                        Text(stringResource(R.string.loop_clear), color = sky.textSecondary)
                    }
                }
            }
            if (showDialog) {
                PostponeDialog(
                    onDismiss = { showDialog = false },
                    onPick = { at ->
                        onPostpone(at)
                        showDialog = false
                    },
                )
            }
        }
    }
}

/**
 * v0.28.0: the home-surface Distress Thermometer card. The first
 * question the home surface asks — validation-first, before any
 * task-capture or note-taking. A single Surface with the title,
 * the caption, and a "Ground me here" button that opens
 * [org.mindanchor.support.DistressThermometerActivity].
 *
 * The full 0-100 slider lives in the activity; the home card is
 * the launcher. The card is BPD-safe by design: no directive
 * language, no all-or-nothing framing, no comparative
 * day-rating language. The caption is validate-then-suggest
 * ("slide to where it is, not where you want it to be").
 *
 * Research: Linehan 1993 (DBT Distress Tolerance, ch. 8) +
 * Gross 1998 (emotion regulation). The home card is the
 * "check in with where you are" affordance that the rest of
 * the launcher's surfaces assume has already happened.
 *
 * v0.25.5-v0.27.0 used to render a OneThingCard ("today's one
 * thing" — Martell 2013) as a sibling to OpenLoopCard +
 * QuickNotesCard. v0.28.0 removes the OneThingCard from the
 * home surface (BPD-strict: the first question is "how is it
 * right now?", not "what's the one thing today?"). The
 * OneThing data model is preserved in
 * [org.mindanchor.launcher.LauncherViewModel.oneThing] for
 * the export payload and any future re-introduction.
 */
@Suppress("FunctionNaming")
@Composable
private fun HomeDistressCard(
    sky: SkyContent,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_distress_card_title),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_distress_card_caption),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(
            onClick = onOpen,
            modifier = Modifier
                .semantics { role = Role.Button }
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.home_ground_me_button),
                color = sky.textPrimary,
            )
        }
    }
}

/**
 * The two-option worry-postponement dialog. Returns an [Instant] picked
 * from the user's choice. Borkovec's worry-postponement protocol says
 * the user picks the time, not the algorithm — but the two most common
 * times ("later today", "tomorrow morning") are the right defaults and
 * the explicit time-picker is a follow-up.
 */
@Suppress("FunctionNaming")
@Composable
private fun PostponeDialog(onDismiss: () -> Unit, onPick: (Instant) -> Unit) {
    // v0.25.10 (SOTA v2 bug-hunt B6): the zone is captured here once,
    // and "now" is read at the moment the user picks, not at the moment
    // the dialog composes. A dialog that stays open across a clock
    // change, an NTP correction, a zone change, or simply a long pause
    // used to schedule the postponed-at time from a stale instant; the
    // pick is now a fresh system read in the same zone as the rest of
    // the app.
    val zone = ZoneId.systemDefault()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loop_postpone_dialog_title)) },
        text = {
            Column {
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        onPick(
                            LocalDateTime.now(zone)
                                .plusHours(2)
                                .atZone(zone)
                                .toInstant(),
                        )
                    },
                ) {
                    Text(stringResource(R.string.loop_postpone_later_today))
                }
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        onPick(
                            LocalDate.now(zone)
                                .plusDays(1)
                                .atTime(9, 0)
                                .atZone(zone)
                                .toInstant(),
                        )
                    },
                ) {
                    Text(stringResource(R.string.loop_postpone_tomorrow_morning))
                }
            }
        },
        confirmButton = {
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = onDismiss,
                // v0.25.10 (B6): Role.Button

            ) {
                Text(stringResource(R.string.loop_postpone_cancel))
            }
        },
    )
}

/**
 * Formats an [Instant] as a local wall-clock "HH:mm" or "tomorrow HH:mm"
 * for the [LoopPhase.POSTPONED] sub-text. The Intents are UTC; the
 * formatting is in the device's local zone so the user sees what they
 * scheduled in their own clock, not UTC.
 */
private fun formatWallClock(at: Instant?, today: LocalDate): String {
    if (at == null) return ""
    val zoned = at.atZone(ZoneId.systemDefault())
    val time = zoned.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (zoned.toLocalDate() == today) time else "tomorrow $time"
}

/**
 * v0.26.6: BedtimeListCard removed from the home surface
 * (three task-capture cards was one too many). The data
 * model (sleep/BedtimeList.kt), the DataStore
 * (data/LauncherPrefs.kt), the strings, and the bedtimeList
 * state flow are kept — only the home-surface call is gone.
 */
/**
 * The home-screen quick-notes card. v0.20.4.
 *
 * The launcher already routes the user to
 * [org.mindanchor.model.NoteActivity] from the
 * "notes" button in the top-right corner; the full
 * activity is the right place to read, edit, and
 * pin a long note. This card is the *capture*
 * surface — the place to jot one line without
 * opening anything.
 *
 * ## Why always visible
 *
 * The brief: "I want to remember this." The whole
 * notes feature exists for the moment between
 * noticing a thought and losing it. Two taps
 * (notes → new) is two taps too many in that
 * moment. The home card is the launcher-equivalent
 * of the URL bar in a browser: one place, always
 * there, one line, type and save.
 *
 * ## Why it shows the last three notes
 *
 * The save is the moment the user wants to know
 * worked. Showing the just-saved line land at the
 * top of a small list is the cheapest possible
 * "it worked" feedback. Three is the floor that
 * makes the card feel like a journal (one row
 * feels like a typo) and the ceiling before the
 * card would push the favourites off a small
 * screen at default font scale. The full list —
 * every note, every timestamp, edit and pin —
 * is one tap away via "View all".
 *
 * ## Why a button, not auto-save
 *
 * Notes are user-authored text and an
 * accidental keystroke (the keyboard popping
 * up while walking) is a real failure mode.
 * Auto-save on focus loss would silently
 * capture typos. The button makes the save
 * explicit; the placeholder and the disabled-
 * when-blank button tell the user the surface
 * is alive without nagging.
 *
 * ## Tapping a saved note
 *
 * A single tap opens the full [NoteActivity]
 * for editing. The card never edits inline
 * (the editing affordance is a different
 * surface, and inline edit on the home would
 * make the card a second editor — which the
 * brief is explicit that it is not).
 */
@Composable
private fun QuickNotesCard(
    sky: SkyContent,
    recent: List<Note>,
    onSave: (String) -> Unit,
    onOpenAll: () -> Unit,
) {
    // v0.25.14: rememberSaveable so a mid-capture draft
    // (a half-typed note about the email you just saw)
    // survives a config change or process death. The
    // String is auto-Saveable; no custom Saver needed.
    var draft by rememberSaveable { mutableStateOf("") }
    // A small haptic tick on save, so the user feels
    // the capture even if the note disappears under
    // the keyboard or the screen is dim. LongPress is
    // the shortest available tick (≈5ms on most
    // devices) — short enough not to interrupt
    // typing, long enough to register. The user
    // pressed a button; the button is allowed to
    // answer.
    //
    // v0.25.16 BUG-013: gate through
    // [org.mindanchor.ui.HapticFeedbackGate] so the
    // system haptics toggle and the "remove animations"
    // a11y preference are honored. LongPress for save,
    // TextHandleMove for clear — the rich-tactile
    // distinction Brewster CHI 2007 names is preserved
    // by the gate's `type` parameter.
    val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.quick_notes_section),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textSecondary,
        )
        // v0.20.9: bringIntoViewOnFocus so the quick-notes
        // input is not covered by the keyboard. The card is
        // the home-screen capture affordance; "I want to
        // remember this" fails if the user has to dismiss
        // the keyboard to see what they are typing.
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.quick_notes_input_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnFocus()
                .padding(top = 8.dp),
        )
        TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                onSave(draft)
                draft = ""
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            enabled = draft.isNotBlank(),
            // v0.25.10 (B6): Role.Button

        ) {
            Text(stringResource(R.string.quick_notes_save), color = sky.textPrimary)
        }
        // v0.25.5 WP-G: a "Clear" affordance with a distinct
        // tactile shape (TextHandleMove — the soft "whoosh" of
        // moving text out of the way). The four haptic types
        // Brewster CHI 2007 distinguishes are all in use across
        // the launcher now: save and the destructive actions are
        // LongPress (a confirmation pulse); clear is
        // TextHandleMove (a softer, distinct shape). A user
        // who can feel the difference will not wonder which
        // they pressed.
        if (draft.isNotBlank()) {
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                    draft = ""
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            ) {
                Text(stringResource(R.string.quick_notes_clear), color = sky.textSecondary)
            }
        }
        if (recent.isEmpty()) {
            Text(
                text = stringResource(R.string.quick_notes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = sky.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            recent.forEach { note ->
                // The note row is intentionally
                // compact: first line of the body
                // (the title by convention) plus a
                // small timestamp. Full body is in
                // the activity; the home only
                // surfaces the *fact* the user
                // wrote it, and the rough when.
                val title = note.title.ifBlank { note.body.take(60) }
                val whenText = noteTimeText(note)
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Button },
                    onClick = onOpenAll,
                ) {
                    Text(
                        text = stringResource(
                            R.string.quick_notes_saved_at,
                            title,
                            whenText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = sky.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = onOpenAll,
                // v0.25.10 (B6): Role.Button

            ) {
                Text(stringResource(R.string.quick_notes_view_all), color = sky.textSecondary)
            }
        }
    }
}

/**
 * Format a note's timestamp for the home card.
 *
 * The list view uses the absolute date+time; the
 * home card needs something a person can read at
 * a glance (where the line is small). Today vs.
 * yesterday vs. earlier is the right shape: a
 * note from 2pm today reads "14:00", a note from
 * yesterday reads "yesterday 22:13", a note from
 * last week reads the short date. The function
 * is local to this file because the rule is
 * display-only and no other surface needs the
 * same compaction.
 */
private fun noteTimeText(note: Note): String {
    val now = System.currentTimeMillis()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = note.createdAt }
    val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.createdAt))
    }
    val yesterdayCal = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = cal.get(java.util.Calendar.YEAR) == yesterdayCal.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR)
    if (isYesterday) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.createdAt))
        return "yesterday $time"
    }
    return DateFormat.getDateInstance(DateFormat.SHORT).format(Date(note.createdAt))
}

/**
 * The wellness card — per-signal readings for today against the
 * person's own history, in the same N-of-1 framing the rest of
 * the launcher uses. The home is a glance surface: one line per
 * signal, no charts, no diagnosis. A signal the watch had no
 * data for reads as a dash, not a number; a signal the baseline
 * has not yet caught up to reads as a still-building note, not a
 * fake number.
 *
 * ## Why direction only, not raw z-score
 *
 * The home card is for glancing at, not for analysing. A robust
 * z-score of 1.4 is a fraction of a personal distribution; a
 * label of "above your usual" is the same fact in words. The
 * number is preserved in [WellnessReading.zScore] for the
 * settings panel and the nightly report, where it is read in
 * the larger context those surfaces provide.
 *
 * ## Why a card at all on a launcher that says "say less"
 *
 * The launcher is a quiet place by design, and a card that
 * updates itself with five lines a day is the kind of thing
 * that trains a person to look. The compromise: the card is
 * shown only when at least one signal has a value to show AND
 * a baseline to compare it to. A user with no Health Connect
 * source app, or fewer than 14 days of history, sees no card —
 * the home stays the home.
 *
 * ## Why "your usual" rather than "your 30-day average"
 *
 * The signal is the personal median, the language is "usual".
 * "Average" is a population word — it tells a person where
 * they are against a curve that has nothing to say about
 * them. "Usual" is a personal word — it tells a person where
 * they are against themselves. The full machinery is in
 * [org.mindanchor.vitals.WellnessStats]; the home card is
 * deliberately understating it.
 */
@Composable
private fun WellnessCard(
    sky: SkyContent,
    readings: List<WellnessReading>,
) {
    // Hide the card entirely when there is nothing to say.
    // The home is a glance surface; an empty card is a
    // standing invitation to look. Two cases:
    //  - no readings yet (the ViewModel has not refreshed
    //    — show nothing, do not show a skeleton)
    //  - every signal is NO_DATA (no wearable, no
    //    permission, or fewer than 14 days of history)
    val hasAnything = readings.any { it.today != null && it.direction != WellnessDirection.NO_DATA }
    if (!hasAnything) return
    val reportable = readings.any { it.baseline.isReportable }
    if (!reportable) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wellness_section),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textSecondary,
        )
        Text(
            text = stringResource(R.string.wellness_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
        readings.forEach { reading ->
            WellnessLine(sky = sky, reading = reading)
        }
    }
}

/**
 * One row of the wellness card: the signal's name, today's
 * value, the direction band, and a small "vs your usual"
 * anchor.
 */
@Composable
private fun WellnessLine(sky: SkyContent, reading: WellnessReading) {
    val name = stringResource(wellnessSignalNameRes(reading.signal))
    val todayText = reading.today?.let { formatWellnessValue(reading.signal, it) }
        ?: stringResource(R.string.wellness_no_value_today)
    val directionText = stringResource(wellnessDirectionRes(reading.direction))
    val medianText = reading.baseline.median?.let { formatWellnessValue(reading.signal, it) }
        ?: stringResource(R.string.wellness_baseline_building)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = sky.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = todayText,
            style = MaterialTheme.typography.bodyMedium,
            color = sky.textPrimary,
        )
        Text(
            text = "  $directionText",
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
        )
    }
    Text(
        text = stringResource(R.string.wellness_vs_usual, medianText),
        style = MaterialTheme.typography.bodySmall,
        color = sky.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 2.dp),
    )
}

/**
 * The display name for a [WellnessSignal]. Local to this file
 * because the home card is the only place that needs the
 * short form; the settings panel uses the same resource
 * directly.
 */
private fun wellnessSignalNameRes(signal: WellnessSignal): Int = when (signal) {
    WellnessSignal.HRV -> R.string.wellness_signal_hrv
    WellnessSignal.RESTING_HEART_RATE -> R.string.wellness_signal_resting_hr
    WellnessSignal.STEPS -> R.string.wellness_signal_steps
    WellnessSignal.SLEEP_MINUTES -> R.string.wellness_signal_sleep
    WellnessSignal.MINDFULNESS_MINUTES -> R.string.wellness_signal_mindfulness
}

/**
 * The wording for a [WellnessDirection] band. Direction-only,
 * deliberately never labelled "good" or "bad" — see
 * [WellnessDirection]'s KDoc.
 */
private fun wellnessDirectionRes(direction: WellnessDirection): Int = when (direction) {
    WellnessDirection.NO_DATA -> R.string.wellness_dir_no_data
    WellnessDirection.AT -> R.string.wellness_dir_at
    WellnessDirection.ABOVE -> R.string.wellness_dir_above
    WellnessDirection.MUCH_ABOVE -> R.string.wellness_dir_much_above
    WellnessDirection.BELOW -> R.string.wellness_dir_below
}

/**
 * Render a [WellnessSignal] value for the home card.
 *
 * The units match the source data, not the population
 * literature — steps are integer, sleep is minutes, HRV is
 * milliseconds, and so on. The format here is the home card's
 * version: integer when the source is integer, "%.0f ms" for
 * HRV, "%.0f bpm" for resting heart rate. The settings panel
 * uses the same formats via [org.mindanchor.report.ValueFormat].
 */
private fun formatWellnessValue(signal: WellnessSignal, value: Double): String = when (signal) {
    WellnessSignal.HRV -> "%.0f ms".format(value)
    WellnessSignal.RESTING_HEART_RATE -> "%.0f bpm".format(value)
    WellnessSignal.STEPS -> "%,d".format(value.toLong())
    WellnessSignal.SLEEP_MINUTES -> "${value.toInt()} min"
    WellnessSignal.MINDFULNESS_MINUTES -> "${value.toInt()} min"
}

// combinedClickable, for the long-press on a favourite.
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
private fun HomeSurface(
    sky: SkyContent,
    favorites: List<DisplayApp>,
    /**
     * v0.42.0: the 2x2 needs grid ("What do you need right now?")
     * is hidden when this is false. Default `true` keeps the
     * v0.35.0 / v0.40.1 / v0.41.0 home intact for callers that
     * do not pass the parameter (e.g. previews, the launcher
     * tests that pre-date the toggle). The LauncherRoot reads
     * the value from AppearancePrefs.needsGridVisible.
     */
    needsGridVisible: Boolean = true,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
    loopPhase: LoopPhase = LoopPhase.NONE,
    loopNote: String? = null,
    loopPostponedAt: Instant? = null,
    onLoopSave: (String) -> Unit = {},
    onLoopClear: () -> Unit = {},
    onLoopPostpone: (Instant) -> Unit = {},
    onLoopCancelPostpone: () -> Unit = {},
    /**
     * v0.28.0: open the Distress Thermometer activity. Wired to
     * the "Ground me here" button on the home Distress card.
     * The first question the home surface asks is "how is it
     * right now?" — validation-first, before any task-capture.
     */
    onOpenDistressThermometer: () -> Unit = {},
    /** v0.26.0 §3.2: long-press the clock. */
    onOpenGroundMe: () -> Unit = {},
    /** Shown only when last night's report actually has something in it. */
    hasReport: Boolean = false,
    onOpenReport: () -> Unit = {},
    /**
     * v0.20.1 round 5: route to [org.mindanchor.model.NoteActivity].
     * Notes are a one-tap home-screen affordance for the
     * "I want to remember this" capture pattern (brief §A).
     * TopEnd so it does not collide with TopStart (Support)
     * or BottomStart (Digest) or BottomEnd (Settings).
     */
    onOpenNotes: () -> Unit = {},
    /**
     * v0.20.1 round 5 follow-up: route to
     * [org.mindanchor.model.CheckInHistoryActivity].
     * The history is a read-only list of past
     * check-ins; the *write* side is the
     * phone-unlock trigger, the *read* side is
     * the home-screen affordance. Same pattern
     * as the notes (capture) — separate the write
     * and read surfaces so neither clutters the
     * other.
     */
    onOpenCheckInHistory: () -> Unit = {},
    /**
     * v0.25.2-A (Task 6): route to the
     * letter inbox + reader (LauncherSurface.Letter).
     * Wired to the new "letters" TextButton at
     * the top of the TopEnd Column (above notes
     * + history). Mirrors the [onOpenReport]
     * pattern: the lambda body lives at the
     * call site in [LauncherRoot] and sets the
     * letter state (selectedDate, cameFrom) and
     * the surface dispatcher.
     */
    onOpenLetters: () -> Unit = {},
    /**
     * v0.26.4 §3.4: route to [org.mindanchor.chain.ChainCaptureActivity].
     * The chain capture is the "what just happened?" surface —
     * 5 fields (event / interpretation / part / want /
     * part-to-bring) for a person mid-dysregulation to
     * externalise the moment before acting on it. It is
     * not a daily ritual; it is a low-friction affordance
     * that the home surface should make one tap away
     * without burying it under settings.
     */
    onOpenChainCapture: () -> Unit = {},
    /**
     * v0.26.4 §3.4: route to [org.mindanchor.ifs.IfsPickerActivity].
     * "Which part is loud?" is a 2-column chip grid of named
     * IFS parts. Same shape as the chain capture: a
     * low-friction affordance for a person mid-dysregulation
     * to name the part before acting on it. Home surface
     * affordance.
     */
    onOpenIfsPicker: () -> Unit = {},
    /**
     * v0.26.4 §3.4: route to [org.mindanchor.export.ExportActivity].
     * The "Export for my therapist" affordance. One tap on
     * the home surface to dump notes, OneThing, OpenLoop,
     * BedtimeList, wellness N-of-1, check-ins, BPD profile,
     * chain captures, IFS picks to JSON (excludes Letter
     * content). System share sheet for delivery to the
     * therapist.
     */
    onOpenExport: () -> Unit = {},
    /**
     * v0.35.0: the "Be heard" affordance on the needs card.
     * Routes to [org.mindanchor.support.SupportActivity] —
     * the launcher's existing 8-surface support menu
     * (self-compassion, radical acceptance, opposite action,
     * interpersonal, ACCEPTS, half-smile, IMPROVE, the
     * check-the-facts skill). The "Be heard" label is the
     * need-language the home asks for; the activity it
     * opens is the existing surface.
     */
    onOpenSupport: () -> Unit = {},
    /**
     * v0.35.0: the "A moment" affordance. Routes to
     * [org.mindanchor.support.AcceptsActivity] — the DBT
     * ACCEPTS skill (Activities, Contributing, Comparisons,
     * Emotions, Pushing away, Thoughts, Sensations). A
     * single-tap DBT path for the "I need to come down"
     * need, which is what the home asks the user to name
     * before routing.
     */
    onOpenAccepts: () -> Unit = {},
    /**
     * v0.35.0: the "Check in" affordance. Routes to
     * [org.mindanchor.support.DiaryCardActivity] — the
     * DBT diary card (DBT skills training handouts,
     * Linehan 2015). A one-tap path to the diary card
     * the user already fills in at the day's end; the
     * "Check in" door is the same diary card, framed as
     * a needs-first affordance.
     */
    onOpenDiaryCard: () -> Unit = {},
    /**
     * v0.35.0: the "Get through this" affordance. Opens
     * the [LauncherSurface.GetThrough] sub-menu — a
     * three-button sheet that surfaces the existing
     * chain capture, IFS picker, and export activities
     * in the order a person mid-dysregulation is most
     * likely to want them.
     */
    onOpenGetThrough: () -> Unit = {},
    /**
     * v0.35.0: the three StateFlows the "Where it comes
     * from" home card reads. The card itself is hidden
     * entirely when no source has any data to surface —
     * see [DataSourcesCard] for the visibility rules.
     */
    healthConnectStatus: LauncherViewModel.HealthConnectStatus =
        LauncherViewModel.HealthConnectStatus.NotGranted,
    corosDataStatus: LauncherViewModel.CorosDataStatus =
        LauncherViewModel.CorosDataStatus.NotConnected,
    ppgLastMeasurement: LauncherViewModel.PpgLastMeasurement? = null,
    /**
     * v0.20.4: the home-screen quick-notes
     * affordance. The card shows a one-line
     * input, a save button, and the most recent
     * notes. The save callback writes to the
     * same [org.mindanchor.data.NotesPrefs] the
     * full [org.mindanchor.model.NoteActivity]
     * reads from — the two surfaces share the
     * store. [onOpenNotes] is reused to route
     * the "View all" link and the per-row tap
     * to the full activity.
     */
    recentNotes: List<Note> = emptyList(),
    onAddQuickNote: (String) -> Unit = {},
    /**
     * v0.20.5: the wellness card — per-signal readings for
     * today against the person's own history. Null is
     * "still loading", not "no data": the card is hidden
     * entirely when [wellnessReadings] is null or when
     * every reading is [WellnessDirection.NO_DATA]. The
     * home stays the home when there is nothing to show.
     */
    wellnessReadings: List<org.mindanchor.vitals.WellnessReading>? = null,
    /**
     * v0.22.0 (WP-10 step 2): the "what makes this different"
     * callout. Renders a single line of small text below
     * the greeting for the first
     * [org.mindanchor.data.LauncherPrefs.INTRO_CALLOUT_LAUNCHES]
     * home-surface displays, then disappears forever. The
     * parent is expected to call [onRecordLaunch] once on
     * every home-surface display so the callout can know
     * when to hide.
     */
    showIntroCallout: Boolean = false,
    onRecordLaunch: () -> Unit = {},
) {
    val now = rememberMinuteTick()
    val clockFormat = rememberClockFormat()

    // v0.20.9: nested safe-drawing on the outer Box and
    // imePadding on the inner scroll container. The outer
    // Box keeps the corner buttons clear of the status and
    // navigation bars in normal use; imePadding on the
    // inner Column shrinks the scroll area to the gap
    // between the top safe area and the keyboard, so the
    // "Put it down" / "Add line" / save buttons of the
    // bedtime list and the per-field "Save" buttons of the
    // quick-notes card stay above the keyboard instead of
    // being layered under it. Without imePadding on the
    // scroll container, focusing a field that sits near the
    // bottom of the Column let the keyboard cover the field
    // and the buttons below it.
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // Centred when it fits, scrollable when it does not.
        //
        // The content used to be centred with no way to scroll, so anything
        // taller than the screen was simply cut off and unreachable. That
        // needed neither an exotic device nor landscape to happen: a large
        // font scale, or enough favourites, was sufficient — and a person
        // who has set a large font scale is exactly the person who cannot
        // recover by squinting. The bottom padding keeps the last favourite
        // clear of the drawer and settings buttons layered over this.
        //
        // v0.20.9: the modifier order is now
        //   fillMaxSize -> imePadding -> padding -> verticalScroll
        // The previous order had `padding` *inside* the
        // verticalScroll, which meant the 88dp bottom
        // padding was applied to the content, not to the
        // scroll container itself. The content then
        // scrolled into the padding area and the last
        // items (the quick-notes empty state, the
        // favourites) ended up layered under the bottom
        // navigation row. Moving the padding outside
        // the scroll shrinks the scrollable area by 88dp
        // on the bottom, so the last content item is
        // always 88dp above the bottom navigation no
        // matter how far the user scrolls.
        //
        // v0.20.9: the scroll container also takes
        // imePadding so the bedtime list, open-loop
        // capture, and quick-notes input fields are not
        // covered by the soft keyboard. Each input field
        // opts in to BringIntoViewRequester so the
        // focused field is scrolled into view above the
        // keyboard.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 88.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(clockFormat)),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                ),
                color = sky.textPrimary,
                // v0.26.0 §3.2: long-press the clock to open GroundMe
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onOpenGroundMe,
                ),
            )
            Text(
                text = greetingFor(
                    now.hour,
                    stringResource(R.string.greeting_morning),
                    stringResource(R.string.greeting_day),
                    stringResource(R.string.greeting_evening),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textSecondary,
            )

            // v0.22.0 (WP-10 step 2): the "what makes this
            // different" callout. One line of small text
            // pointing at the friction gate, shown for the
            // first 3 launches and then never again. The
            // recording fires on a side effect so the
            // callout is one launch closer to hidden on
            // every display, regardless of which side of
            // the threshold this display is on.
            if (showIntroCallout) {
                LaunchedEffect(Unit) { onRecordLaunch() }
                Text(
                    text = stringResource(R.string.intro_callout),
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // v0.35.0: the "What do you need right now?"
            // 2×2 needs card replaces the v0.32.x Distress
            // Thermometer card. The Distress Thermometer
            // (0-100 slider, "how is it right now") is no
            // longer a home card; it is still reachable
            // from Settings → Pauses, and the full v0.28.0
            // rationale (DBT validate-then-suggest, the
            // first question is "what do you need" not
            // "how distressed are you") is in
            // NeedsCard.kt's KDoc.
            //
            // The four doors are need-language ("I need X"),
            // not action-language ("do X"). The home asks
            // what is needed first, then offers one
            // well-shaped path. Research: Linehan 1993
            // (DBT ch. 8), Schwartz 1995 (IFS), Lindsay
            // 2024 (JMIR).
            //
            // v0.42.0: gated on [needsGridVisible]. When the
            // user has turned the toggle off in Settings,
            // the entire 2x2 (header + caption + four doors)
            // is skipped. The home collapses to clock →
            // greeting → quick-notes. Support remains
            // reachable from the top-left "Open Support"
            // button, so the affordance is hidden, not lost.
            if (needsGridVisible) {
                NeedsCard(
                    sky = sky,
                    onBeHeard = onOpenSupport,
                    onMoment = onOpenAccepts,
                    onCheckIn = onOpenDiaryCard,
                    onGetThrough = onOpenGetThrough,
                )
            }

            // v0.35.0: the "Where it comes from" data-sources
            // card. Surfaces the three wearable sources the
            // user has (or has not) opted in to: Health
            // Connect (the "any watch" surface), Coros Pace 3
            // (the side-channel), and PPG (the camera HRV).
            // The card is hidden entirely when no source has
            // any data — see DataSourcesCard for the
            // visibility rules. The card is provenance, not
            // summary; it does not surface clinical
            // judgments, scores, or "good/bad" labels.
            DataSourcesCard(
                sky = sky,
                healthConnectStatus = healthConnectStatus,
                corosDataStatus = corosDataStatus,
                ppgLastMeasurement = ppgLastMeasurement,
            )

            // v0.20.4: the quick-notes card. Always
            // visible — the brief is "I want to
            // remember this", and the moment the
            // user thinks it is the moment the card
            // has to be there.
            //
            // v0.25.7+ WP-3: the card was previously
            // placed *after* the OpenLoop / BedtimeList
            // cards. On a 1080x2400 device (the most
            // common emulator size and a real mid-range
            // phone) the bedtime list, one-thing card,
            // and an active OpenLoopCard together push
            // the quick-notes card below the fold — the
            // brief's URL-bar-equivalent affordance
            // becomes invisible. The fix is to promote
            // the quick-notes card to the top of the
            // action stack: after the time / greeting
            // (always) and the OpenLoop (when active),
            // before the bedtime list. The "rough
            // centre of the home screen" comment is
            // updated to "above the fold on 1080x2400
            // with an active worry".
            QuickNotesCard(
                sky = sky,
                recent = recentNotes,
                onSave = onAddQuickNote,
                onOpenAll = onOpenNotes,
            )

            // v0.28.0: OneThingCard removed from the home surface.
            // The data model (the `oneThing` state and the
            // viewModel::setOneThing wiring) is kept — OneThing
            // is still part of the export payload and can be
            // re-introduced in a different surface later. Three
            // task-capture cards (OpenLoop + OneThing + BedtimeList)
            // was already one too many for a BPD-strict home
            // (DBT: low cognitive load is the floor); replacing
            // OneThing with the Distress Thermometer as the
            // primary surface makes the home BPD-strict: the
            // first question the user answers is "how is it
            // right now", and the rest of the cards become
            // optional. See docs/research/14-v0.26.6-audit.md §3
            // for the research basis.
            //
            // v0.26.6: BedtimeListCard removed from the home surface.
            // Three task-capture cards (OpenLoop + OneThing + BedtimeList)
            // was one too many — for a person with BPD (DBT: low
            // cognitive load is the floor), three competing
            // capture affordances is exactly the kind of surface
            // clutter that the brief specifically says to avoid.
            // The data model (sleep/BedtimeList.kt), the DataStore
            // (data/LauncherPrefs.kt), the strings, and the
            // bedtimeList state flow are kept — only the
            // composable's home-surface call is gone. A future
            // release can re-introduce a bedtime surface under the
            // v0.27.x DBT diary card if the user asks for it.

            // v0.20.5: the wellness card. Same idiom
            // as the report link — silent when there is
            // nothing to say, one quiet block when
            // there is. Hidden entirely (not blanked)
            // when the wearable has not been paired,
            // when the user has not granted permission,
            // or when fewer than 14 days of history
            // are on file. The card is a glance
            // surface; an empty card is a standing
            // invitation to look.
            wellnessReadings?.let { readings ->
                WellnessCard(sky = sky, readings = readings)
            }

            // One quiet line, and only when there is genuinely something
            // to read. No badge, no count, no dot — this app has none of
            // those anywhere by design, and a permanent entry point would
            // be a standing invitation to check, which is the habit the
            // rest of the launcher exists to unwind. A steady week
            // produces no report and therefore no line at all.
            if (hasReport) {
                Text(
                    text = stringResource(R.string.report_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = sky.textSecondary,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onOpenReport)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }

            // v0.35.0: the "Right now" section that v0.32.0
            // added is removed. The three reflective actions
            // it surfaced (chain capture, IFS picker, export)
            // are now reached from the "Get through this"
            // needs-card door → GetThroughSubMenu. The
            // surface stack stays shorter (one fewer card
            // on the home) and the sub-menu is the
            // discoverable path for the user who knows what
            // they need. The data model (the three activities
            // themselves) is untouched — the sub-menu just
            // re-routes the entry point.

            Column(
                modifier = Modifier.padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                favorites.forEach { app ->
                    // The target is the full width of the row, not the width
                    // of the word. A favourite named "X" used to offer a
                    // sliver to hit; anyone with a tremor, large fingers or
                    // shaking hands was aiming at almost nothing. 48dp is
                    // the documented minimum and the floor here.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.headlineSmall,
                            color = sky.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // v0.20.9: navigationBarsPadding on the three
        // bottom-corner navigation buttons. The outer
        // Box already has safeDrawingPadding, which
        // should keep these above the system gesture
        // bar, but in practice on the test emulator
        // the nav-bar inset is being reported as 0
        // for the corner-aligned TextButtons and the
        // "digest" / "search" / "settings" row sits
        // underneath the gesture bar. The defensive
        // fix mirrors the statusBarsPadding added to
        // the top corners: ask for the nav-bar inset
        // on the buttons themselves.
        TextButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .semantics { role = Role.Button },
            onClick = onOpenDrawer,
        ) {
            Text(
                text = stringResource(R.string.open_drawer),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textSecondary,
            )
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                // Same defensive end padding as the
                // top-right Column: keeps the
                // TextButton's right edge inside the
                // screen on rounded-corner devices and
                // on emulators that crop the last
                // pixel.
                .padding(end = 8.dp)
                .semantics { role = Role.Button },
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        val context = LocalContext.current
        // Support is one tap from the home screen and never buried: during
        // acute distress or dissociation, three taps and a scroll is too far.
        //
        // v0.20.9: statusBarsPadding on the top-corner
        // buttons. The outer Box already has
        // safeDrawingPadding which should keep the
        // buttons clear of the status bar, but on a
        // real phone with the soft keyboard up the
        // status-bar inset was being eaten somewhere
        // upstream and the "support" / "history"
        // labels rendered behind the status-bar
        // icons. The defensive fix is to ask for the
        // status-bar inset on the buttons
        // themselves; the doubled padding when
        // safeDrawingPadding is working is small
        // (~24dp on top of ~24dp) and not a real
        // cost on a phone screen.
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(context, SupportActivity::class.java))
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .semantics { role = Role.Button },
        ) {
            Text(
                text = stringResource(R.string.support_shortcut),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        // v0.20.1 round 5: notes + check-in history
        // entry points. TopEnd, so neither collides
        // with TopStart (Support), BottomStart
        // (Digest), or BottomEnd (Settings). One-tap,
        // no scrolling. The brief: "I want to
        // remember this" — the entry must be
        // reachable the moment the user thinks it.
        // Two stacked buttons (notes on top, history
        // below) keep the home screen uncluttered
        // without forcing the user into a menu.
        //
        // v0.20.9: same statusBarsPadding as the
        // support button on the other corner — see
        // its KDoc for the rationale.
        //
        // v0.25.2-A (Task 6): a third stacked button
        // — "letters" — sits at the top of this
        // Column, above notes + history. The
        // Column's existing 8dp end padding (Bug 6
        // fix from v0.25.1) already applies, so the
        // new button inherits the same touch-target
        // breathing room without a per-button
        // re-pad. The brief: a letter is the one
        // thing the launcher writes for the user —
        // it must be one tap from the home surface
        // the same way notes and history are.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                // Defensive 8dp end padding so the
                // TextButton's right edge sits inside
                // the screen on rounded-corner devices
                // and on emulators that crop the last
                // pixel. Without this the touchable
                // area can be partially clipped and
                // the "notes" / "history" buttons
                // miss taps at the very right of the
                // screen. The 8dp is small enough not
                // to shift the visible label position.
                .padding(end = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = onOpenLetters,
                // v0.25.10 (B1): use stringResource for the label.
                // v0.25.10 (B6): Role.Button for screen readers.
                // v0.37.1: matched labelMedium + sky.textSecondary
                // so the three stacked TopEnd buttons (Letters,
                // notes, history) read as one quiet nav column
                // instead of "Letters looks like a primary CTA".

            ) {
                Text(
                    text = stringResource(R.string.letters_shortcut),
                    style = MaterialTheme.typography.labelMedium,
                    color = sky.textSecondary,
                )
            }
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = onOpenNotes,
                // v0.25.10 (B6): Role.Button

            ) {
                Text(
                    text = stringResource(R.string.notes_shortcut),
                    style = MaterialTheme.typography.labelMedium,
                    color = sky.textSecondary,
                )
            }
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = onOpenCheckInHistory,
                // v0.25.10 (B6): Role.Button

            ) {
                Text(
                    text = stringResource(R.string.check_in_history_shortcut),
                    style = MaterialTheme.typography.labelMedium,
                    color = sky.textSecondary,
                )
            }
        }

        TextButton(
            onClick = {
                runCatching { context.startActivity(Intent(context, DigestActivity::class.java)) }
            },
            // v0.20.9: same navigationBarsPadding as the
            // other two bottom corners — see their KDoc
            // for the rationale.
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .semantics { role = Role.Button },
        ) {
            Text(
                text = stringResource(R.string.digest_screen_title),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSurface(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
) {
    // v0.25.17 BUG-004: lifecycle-aware collect. The
    // search query is held in the ViewModel; the
    // lifecycle-aware primitive keeps the search
    // surface from collecting on every keystroke
    // after the user has navigated away.
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results = viewModel.searchResults(state)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { results.firstOrNull()?.let(onLaunch) },
            ),
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(results, key = { it.component }) { app ->
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongPress(app) },
                        )
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

internal fun greetingFor(hour: Int, morning: String, day: String, evening: String): String =
    when (hour) {
        in 5..11 -> morning
        in 12..17 -> day
        else -> evening
    }

/** v0.26.0 §3.3 demo. */
@Composable
private fun BeforeYouSendDemo(onDismiss: () -> Unit) {
    org.mindanchor.friction.BeforeYouSendInterstitial(
        context = org.mindanchor.friction.BeforeYouSendHeuristic.contextFor(
            length = 320,
            allCapsRatio = 0.6f,
            after23 = true,
            closeContact = true,
        ),
        profile = org.mindanchor.data.BpdProfile(),
        onDismiss = onDismiss,
    )
}
