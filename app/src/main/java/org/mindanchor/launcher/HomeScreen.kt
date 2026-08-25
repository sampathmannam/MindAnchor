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
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mindanchor.friction.CompassionateWrapNotifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.mindanchor.R
import org.mindanchor.digest.DigestActivity
import org.mindanchor.friction.FrictionGate
import org.mindanchor.friction.FrictionTone
import org.mindanchor.friction.GateContext
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterScreen
import org.mindanchor.letters.LetterStore
import org.mindanchor.letters.LetterWriteState
import org.mindanchor.model.Note
import org.mindanchor.model.NoteActivity
import org.mindanchor.reader.ReadingSize
import org.mindanchor.report.ReportScreen
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.vitals.PpgScreen
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.rememberClockFormat
import org.mindanchor.ui.rememberMinuteTick
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private enum class LauncherSurface { Home, Drawer, Settings, Ppg, Report, Letter, HealthyDefaults }

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
    /**
     * v0.25.9 (auto-update): a non-null value means a newer
     * MindAnchor version is on GitHub. The home surface shows
     * a snackbar; tapping the action opens the release page in
     * the browser. `onUpdateAction` is invoked on tap;
     * `onUpdateDismiss` on "not now" (the activity clears the
     * value so the snackbar does not reappear on the next
     * recomposition).
     */
    availableUpdate: org.mindanchor.update.UpdateInfo? = null,
    onUpdateAction: (org.mindanchor.update.UpdateInfo) -> Unit = {},
    onUpdateDismiss: () -> Unit = {},
    /**
     * v0.25.9 (deployability §8.3): whether MindAnchor is the
     * user's default home. The home surface shows a one-line
     * callout with a single action button while this is false.
     */
    isDefaultHome: Boolean = true,
    onSetDefaultHome: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val recentNotes by viewModel.notes.collectAsState()
    var surface by remember { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by remember { mutableStateOf<DisplayApp?>(null) }
    var gateFor by remember { mutableStateOf<DisplayApp?>(null) }
    // v0.30+ (Phase 4 G-5): the [Activity] reference
    // for the [startLockTaskOn] / [stopLockTaskOn] calls
    // is resolved from the Compose [LocalContext]. The
    // activity is host-process: the LaunchedEffect
    // body is on the main thread so the call is safe.
    val context = LocalContext.current

    // v0.26+ (Phase 1 G-22, G-21, G-1) — the protective
    // layer rituals' read-side state. Each card gates on
    // its setting, and Going Light gates on the schedule
    // + VpnService.prepare() (handled inside the Composable).
    val baPromptEnabledByState = viewModel.baPromptEnabled
    val morningCompassionEnabledByState = viewModel.morningCompassionEnabled
    val goingLightScheduleByState = viewModel.goingLightSchedule

    // v0.28+ (Phase 3 G-29, G-8, G-26) — the gratitude,
    // expressive-writing, and wind-down ritual toggles.
    // v0.29+ (Phase 4 G-6, G-28) — the push-up mode
    // and voice journal toggles. Each card gates on
    // its setting; the launcher view-model owns the
    // write side (the save callbacks).
    val gratitudeEnabledByState = viewModel.gratitudeEnabled
    val expressiveWritingEnabledByState = viewModel.expressiveWritingEnabled
    val windDownEnabledByState = viewModel.windDownEnabled
    val pushUpModeEnabledByState = viewModel.pushUpModeEnabled
    val voiceJournalEnabledByState = viewModel.voiceJournalEnabled

    // v0.29+ (Phase 4 G-5) — the Sleep Lock state.
    // The home surface shows the card when the user
    // is inside the configured sleep window. The
    // bedtime / waketime strings come from the
    // existing SunsetPrefs (read by the launcher
    // view-model). The on-device-only-Composable
    // stub here is the post-grant UI; the
    // v0.30+ (this turn) wiring calls
    // [Activity.startLockTask] / [Activity.stopLockTask]
    // so the sleep lock is enforced by the
    // platform, not just a Card overlay.
    val inSleepWindowByState = viewModel.inSleepWindow
    val inSleepWindow by inSleepWindowByState.collectAsState()
    // v0.30+ (G-5 device-owner follow-up): pin the
    // user to the launcher task while the sleep
    // window is active. The 30-second unlock
    // phrase is the only way out; pressing Home
    // does not exit the locked task.
    LaunchedEffect(inSleepWindow) {
        if (inSleepWindow) startLockTaskOn(context)
    }

    // v0.28+ (Phase 3 G-25) — the n-of-1 weekly
    // patterns. Read from the same ReportStore
    // the nightly report writes to. The home
    // surface gates on `isNotEmpty()` so a
    // fresh install (no nightly report yet)
    // shows nothing.
    val weeklyPatternsByState = viewModel.weeklyPatterns

    // v0.26+ (Phase 1 G-23) — the DEAR MAN dialog state
    // for the long-press affordance on the Anchor Note
    // title. The state is owned by the home surface, the
    // save callback is owned by the launcher view-model.
    val dearManDialogState = remember { DearManDialogState() }

    // v0.25.2-A (Task 6): the letter inbox + reader. Same shape as
    // reportCameFrom — selected date is null on the inbox, non-null
    // in the reader; cameFrom remembers where the user came from so
    // the inbox's back button returns there. Two entry points: the
    // new "letter" TopEnd corner on the home surface, the (later)
    // Reading sub-section in Settings, and the letter notification
    // (Task 8), which writes letterDateSignal from HomeActivity.
    var letterSelectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var letterCameFrom by remember { mutableStateOf(LauncherSurface.Home) }

    // v0.25.7 (Task 13): the LLM letter write state
    // (Idle / Writing / Reader / Error). Collected at
    // [LauncherRoot] level — sibling to the `surface`
    // dispatcher — so both the [LauncherSurface.Home]
    // branch's [HomeSurface] call and the
    // [LauncherSurface.Letter] branch's [LetterScreen]
    // call can forward the same value without
    // re-collecting. Driven by
    // [org.mindanchor.letters.LetterViewModel]; the
    // VM is constructed inside [LauncherViewModel] and
    // exposed as [viewModel.letterWriteState].
    val letterWriteState by viewModel.letterWriteState.collectAsState()

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
        LauncherSurface.Home -> CalmBackground { sky ->
            val showIntroCallout by viewModel.showIntroCallout.collectAsState()
            // v0.25.7 (Task 13): the LLM letter write state
            // (Idle / Writing / Reader / Error) is collected
            // once at the [LauncherRoot] level (see above)
            // and forwarded here to the [HomeSurface] call.
            // The [LauncherSurface.Letter] branch (below)
            // also receives the same `letterWriteState`
            // value — both surfaces see the same state.
            HomeSurface(
                sky = sky,
                favorites = state.favorites,
                onOpenDrawer = { surface = LauncherSurface.Drawer },
                onOpenSettings = { surface = LauncherSurface.Settings },
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
                recentNotes = recentNotes,
                onAddQuickNote = viewModel::addQuickNote,
                onAddCompassionateWrapNote = { event ->
                    // v0.26+ (Phase 1 G-19) — write the
                    // compassionate-wrap event to a Note
                    // via the existing NoteClassifier pipeline.
                    // The user tapped "Note" on the Snackbar;
                    // the launcher owns the storage; the
                    // notifier owns the trigger.
                    viewModel.recordCompassionateWrap(event)
                },
                heldNotificationsDao = org.mindanchor.data.db.AnchorDatabase
                    .get(context.applicationContext as android.app.Application)
                    .heldNotifications(),
                goingLightSchedule = goingLightScheduleByState.collectAsState().value,
                onGoingLightConsentDismissed = viewModel::dismissGoingLightConsent,
                morningCompassionEnabled = morningCompassionEnabledByState.collectAsState().value,
                baPromptEnabled = baPromptEnabledByState.collectAsState().value,
                onSaveBaEntry = { mastery, pleasure ->
                    viewModel.saveBaEntry(mastery, pleasure)
                },
                dearManDialogState = dearManDialogState,
                onSaveDearManScript = viewModel::saveDearManScript,
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
                // v0.25.2-A (Task 6): the "letter" TopEnd
                // corner. Wired here so the lambda body has
                // access to the letter state (selectedDate,
                // cameFrom) and the surface dispatcher. The
                // Settings entry will pass a sibling lambda
                // with cameFrom = LauncherSurface.Settings.
                //
                // v0.25.7 (Task 13): the label is now
                // singular ("letter", one per day), sourced
                // from R.string.letter_singular.
                onOpenLetters = {
                    letterSelectedDate = null
                    letterCameFrom = LauncherSurface.Home
                    surface = LauncherSurface.Letter
                },
                showIntroCallout = showIntroCallout,
                onRecordLaunch = viewModel::recordHomeLaunch,
                // v0.25.7 (Task 13): the LLM letter state
                // machine is driven by
                // [org.mindanchor.letters.LetterViewModel],
                // which [LauncherViewModel] constructs and
                // exposes as [viewModel.letterWriteState] +
                // 4 helper methods. The [HomeSurface]
                // forwards these to the
                // [LauncherSurface.Letter] branch's
                // [LetterScreen] call. The 5th callback,
                // [onOpenLlmSettings], is a UI navigation
                // only — it routes the user to Settings
                // (where the Daily letter (LLM) sub-section
                // lives); it does not touch the VM.
                letterWriteState = letterWriteState,
                onWriteToday = { viewModel.generateToday() },
                onRegenerate = { viewModel.regenerate() },
                onCancelWrite = { viewModel.cancelLetter() },
                onRetryError = { viewModel.generateToday() },
                onOpenLlmSettings = { surface = LauncherSurface.Settings },
                // v0.25.9 (auto-update): forwarded to the
                // HomeSurface so the snackbar can render.
                availableUpdate = availableUpdate,
                onUpdateAction = onUpdateAction,
                onUpdateDismiss = onUpdateDismiss,
                // v0.25.9 (deployability §8.3): while
                // isDefaultHome is false the surface shows
                // a "Set MindAnchor as your home screen"
                // callout with a single action button.
                isDefaultHome = isDefaultHome,
                onSetDefaultHome = onSetDefaultHome,
                // v0.28+ (Phase 3 G-29, G-8, G-26) — the
                // gratitude, expressive-writing, and
                // wind-down ritual cards. Each gates on its
                // setting; the save callbacks write to the
                // Letters store via the launcher
                // view-model.
                gratitudeEnabled = gratitudeEnabledByState.collectAsState().value,
                onSaveGratitude = viewModel::saveGratitude,
                onExpandGratitude = {
                    // v0.28+ (Phase 3 G-29) — the
                    // gratitude card's "Open full editor"
                    // affordance routes the user to the
                    // existing letter surface, same as
                    // the onOpenLetters corner button.
                    letterSelectedDate = null
                    letterCameFrom = LauncherSurface.Home
                    surface = LauncherSurface.Letter
                },
                expressiveWritingEnabled = expressiveWritingEnabledByState.collectAsState().value,
                onSaveExpressiveWriting = viewModel::saveExpressiveWriting,
                windDownEnabled = windDownEnabledByState.collectAsState().value,
                onBeginWindDown = { /* launcher applies
                    the wind-down (v0.28+ hook) */ },
                onDismissWindDown = { /* v0.28+ session
                    scope */ },
                // v0.29+ (Phase 4 G-6, G-28) — the
                // push-up mode and voice journal
                // toggles. The Composable-only stubs
                // are wired here; the actual ML Kit
                // Pose Detection + whisper.cpp JNI
                // bridges are follow-up commits.
                pushUpModeEnabled = pushUpModeEnabledByState.collectAsState().value,
                onPushUpsComplete = { /* launcher proceeds
                    with the launch (v0.29+ hook) */ },
                voiceJournalEnabled = voiceJournalEnabledByState.collectAsState().value,
                onVoiceRecordStart = { /* launcher starts
                    audio capture (v0.29+ hook) */ },
                onVoiceRecordStop = { /* launcher stops
                    audio capture (v0.29+ hook) */ },
                onVoiceTranscribe = { /* launcher invokes
                    whisper.cpp (v0.29+ hook) */ },
                // v0.29+ (Phase 4 G-5) — the Sleep Lock
                // card. Shown on the home surface when
                // the user is inside the configured
                // sleep window. The v0.30+ (this turn)
                // wiring calls [Activity.startLockTask]
                // when the sleep window opens and
                // [Activity.stopLockTask] when the
                // 30-second unlock phrase is matched.
                // [startLockTask] does NOT require the
                // launcher to be the device owner (it
                // requires the activity to be on top of
                // its own task stack — which the launcher
                // always is); the device-owner grant is
                // the heavier [setPackagesSuspended] path
                // that the [DeviceOwner] object owns.
                sleepLockBedtime = viewModel.sleepLockBedtime.collectAsState().value,
                sleepLockWaketime = viewModel.sleepLockWaketime.collectAsState().value,
                onSleepLockUnlock = { stopLockTaskOn(context) },
                inSleepWindow = inSleepWindowByState.collectAsState().value,
                // v0.28+ (Phase 3 G-25) — the n-of-1
                // weekly patterns. Read from the
                // latest nightly report. The card
                // hides when the list is empty.
                weeklyPatterns = weeklyPatternsByState.collectAsState().value,
                onDismissWeeklyPatterns = {
                    // Session-scoped dismiss; the
                    // card reappears on the next
                    // home-surface open if new
                    // patterns arrive. The launcher
                    // view-model exposes a hook
                    // (dismissWeeklyPatterns) for a
                    // future persisted dismiss; for
                    // now the Composable's
                    // `onDismiss` is the only
                    // surface and the launcher
                    // view-model hook is a no-op.
                    viewModel.dismissWeeklyPatterns()
                },
            )
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
                // v0.30+ (spec Phase 3) — the Healthy
                // defaults walkthrough is reachable from
                // the inline 'Healthy defaults' card in
                // Settings → About. The card itself
                // carries a one-tap 'Open system
                // defaults' button; this callback
                // routes to the full per-category
                // walkthrough surface.
                onOpenHealthyDefaults = {
                    surface = LauncherSurface.HealthyDefaults
                },
                onOpenPpg = { surface = LauncherSurface.Ppg },
                onOpenReport = {
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
            )
        }

        // v0.30+ (spec Phase 3) — the Healthy defaults
        // walkthrough surface. Reached from Settings →
        // About's 'Healthy defaults' card. The 'Back'
        // button on the screen returns to the surface
        // the user came from (Settings, in practice).
        LauncherSurface.HealthyDefaults -> Surface(modifier = Modifier.fillMaxSize()) {
            org.mindanchor.settings.HealthyDefaultsScreen(
                onBack = { surface = LauncherSurface.Settings },
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
            // v0.25.6: the home-screen report link is gone. The Report
            // is reachable from Settings only, and back returns to
            // Settings.
            ReportScreen(onBack = { surface = LauncherSurface.Settings })
        }

        // v0.25.2-A (Task 6): the letter inbox + reader. Dispatched
        // here because the parent (HomeScreen) holds the
        // letterSelectedDate / letterCameFrom state — the
        // LetterScreen Composable is otherwise stateless on which
        // date is selected. The back button clears the selected
        // date when in the reader (back to inbox) and falls back
        // to letterCameFrom when in the inbox. v0.25.9 (P0-1):
        // letters are now sourced from LetterStore (the same DataStore
        // backing the inbox) so the home letter surface reflects
        // whatever the user has actually generated.
        LauncherSurface.Letter -> Surface(modifier = Modifier.fillMaxSize()) {
            val modelFits = remember { mutableStateOf(false) }
            // v0.25.2-B (Task 15): letter size is read from the
            // LauncherViewModel (mirrors the SettingsViewModel.letterSize
            // from Task 9 — both VMs read from the same DataStore source).
            val letterSize by viewModel.letterSize.collectAsState()
            val letterStore = remember(context.applicationContext) {
                LetterStore(context.applicationContext)
            }
            // v0.25.9 (P0-1): wire letterStore.letters into the
            // LetterScreen inbox so the home letter surface reflects
            // the real inbox instead of a hard-coded empty list.
            val letters by letterStore.letters.collectAsState(initial = emptyList())
            val letterScope = rememberCoroutineScope()
            LetterScreen(
                letters = letters,
                modelFits = modelFits.value,
                date = letterSelectedDate,
                size = letterSize,
                // v0.25.7 (Task 13): the LLM letter write state
                // is collected at the [LauncherRoot] level (see
                // above) and forwarded here. The inbox /
                // writing / reader / error states are driven by
                // [LetterScreen] based on this value; the home
                // surface does not render them.
                writeState = letterWriteState,
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
                // v0.25.7 (Task 13): the LLM letter state machine
                // callbacks. Wired at the [LauncherRoot] level to
                // [org.mindanchor.letters.LetterViewModel] methods
                // (exposed as [viewModel] helpers). `onOpenLlmSettings`
                // is a UI navigation only (no VM method) — it routes
                // the user to Settings → Reading → Daily letter (LLM).
                onWriteToday = { viewModel.generateToday() },
                onRegenerate = { viewModel.regenerate() },
                onCancelWrite = { viewModel.cancelLetter() },
                onRetryError = { viewModel.generateToday() },
                onOpenLlmSettings = { surface = LauncherSurface.Settings },
            )
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun QuickNotesCard(
    sky: SkyContent,
    recent: List<Note>,
    onSave: (String) -> Unit,
    onOpenAll: () -> Unit,
    onLongPressTitle: (() -> Unit)? = null,
) {
    var draft by remember { mutableStateOf("") }
    // A small haptic tick on save, so the user feels
    // the capture even if the note disappears under
    // the keyboard or the screen is dim. LongPress is
    // the shortest available tick (≈5ms on most
    // devices) — short enough not to interrupt
    // typing, long enough to register. The user
    // pressed a button; the button is allowed to
    // answer.
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.quick_notes_section),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textSecondary,
            modifier = if (onLongPressTitle != null) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { onLongPressTitle() },
                )
            } else {
                Modifier
            },
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
            onClick = {
                onSave(draft)
                draft = ""
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            enabled = draft.isNotBlank(),
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
            TextButton(onClick = {
                draft = ""
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }) {
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
                    onClick = onOpenAll,
                    modifier = Modifier.fillMaxWidth(),
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
            TextButton(onClick = onOpenAll) {
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

// combinedClickable, for the long-press on a favourite.
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "UnusedParameter")
@Composable
private fun HomeSurface(
    sky: SkyContent,
    favorites: List<DisplayApp>,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
    /**
     * v0.20.1 round 5: route to [org.mindanchor.model.NoteActivity].
     * Notes are a one-tap home-screen affordance for the
     * "I want to remember this" capture pattern (brief §A).
     * TopEnd so it does not collide with BottomStart
     * (Digest) or BottomEnd (Settings). The TopStart
     * (Support) corner was removed in v0.25.7 (Task 13).
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
     * Wired to the new "letter" TextButton at
     * the top of the TopEnd Column (above notes
     * + history). Mirrors the [onOpenReport]
     * pattern: the lambda body lives at the
     * call site in [LauncherRoot] and sets the
     * letter state (selectedDate, cameFrom) and
     * the surface dispatcher.
     */
    onOpenLetters: () -> Unit = {},
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
     * v0.26+ (Phase 1 G-19) — the user tapped "Note" on the
     * compassionate-wrap Snackbar. Wired in [LauncherRoot]
     * to write the [CompassionateWrapNotifier.Event] to a
     * Note via [org.mindanchor.model.NoteClassifier]. The
     * home surface owns the trigger; the launcher
     * view-model owns the storage.
     */
    onAddCompassionateWrapNote: (CompassionateWrapNotifier.Event) -> Unit = {},
    /**
     * v0.26+ (Phase 1 G-20) — the held-notifications DAO
     * for the [HomeDietCard]. The card's data layer is the
     * `releasedCountSince(since)` query (added in
     * commit `75029c8`). The DAO is the read-side; the
     * [AnchorNotificationListenerService] is the write
     * side. Default null so existing call sites still
     * compile; the launcher view-model wires the real
     * DAO in [LauncherRoot].
     */
    heldNotificationsDao: org.mindanchor.data.db.HeldNotificationDao? = null,
    /**
     * v0.26+ (Phase 1 G-1) — the Going Light schedule for
     * the [GoingLightConsentCard]. The card shows when
     * the schedule is enabled but the OS-level VpnService
     * consent has not yet been granted.
     */
    goingLightSchedule: org.mindanchor.friction.GoingLightSchedule =
        org.mindanchor.friction.GoingLightSchedule(),
    /**
     * v0.26+ (Phase 1 G-1) — the user dismissed the Going
     * Light consent card; mark the dismissal so the card
     * does not re-appear on every home-surface open.
     */
    onGoingLightConsentDismissed: () -> Unit = {},
    /**
     * v0.26+ (Phase 1 G-21) — whether the morning
     * self-compassion break should show on the home
     * surface. True means the user has enabled the
     * ritual in Settings.
     */
    morningCompassionEnabled: Boolean = false,
    /**
     * v0.26+ (Phase 1 G-22) — whether the BA weekly prompt
     * should show on the home surface. True means the user
     * has enabled the ritual in Settings.
     */
    baPromptEnabled: Boolean = false,
    /**
     * v0.26+ (Phase 1 G-22) — the user saved a BA entry
     * from the picker. The launcher view-model writes the
     * mastery/pleasure pair to [org.mindanchor.letters.LetterStore.saveBaEntry].
     */
    onSaveBaEntry: (mastery: String, pleasure: String) -> Unit = { _, _ -> },
    /**
     * v0.26+ (Phase 1 G-23) — the DEAR MAN dialog state.
     * The home surface owns the state; the launcher
     * view-model owns the save callback.
     */
    dearManDialogState: DearManDialogState = remember { DearManDialogState() },
    /**
     * v0.26+ (Phase 1 G-23) — the user saved a DEAR MAN
     * script. The launcher view-model writes the
     * rule-based script to [org.mindanchor.letters.LetterStore.save].
     */
    onSaveDearManScript: (String) -> Unit = {},
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
    /**
     * v0.25.7 (Task 13): the LLM letter write state. Threaded through
     * [HomeSurface] (with [LetterWriteState.Idle] as the default) so
     * the [LauncherSurface.Letter] branch in [LauncherRoot] can forward
     * the live [LetterWriteState] flow to [LetterScreen]. The home
     * surface itself does not read this value — only the letter
     * branch does — but the param lives on [HomeSurface] so the
     * home / letter surfaces share the same callback plumbing.
     */
    letterWriteState: LetterWriteState = LetterWriteState.Idle,
    /**
     * v0.25.7 (Task 13): user action — write today's letter. Wired
     * to [org.mindanchor.letters.LetterViewModel.generateToday] in
     * [LauncherRoot]. Default no-op so existing call sites still
     * compile.
     */
    onWriteToday: () -> Unit = {},
    /**
     * v0.25.7 (Task 13): user action — regenerate today's letter.
     * Wired to [org.mindanchor.letters.LetterViewModel.regenerate]
     * in [LauncherRoot]. Default no-op.
     */
    onRegenerate: () -> Unit = {},
    /**
     * v0.25.7 (Task 13): user action — cancel an in-flight letter
     * generation. Wired to
     * [org.mindanchor.letters.LetterViewModel.cancel] in
     * [LauncherRoot]. Default no-op.
     */
    onCancelWrite: () -> Unit = {},
    /**
     * v0.25.7 (Task 13): user action — retry after an LLM error.
     * Wired to [org.mindanchor.letters.LetterViewModel.generateToday]
     * in [LauncherRoot] (same code path as the initial write).
     * Default no-op.
     */
    onRetryError: () -> Unit = {},
    /**
     * v0.25.7 (Task 13): user action — open LLM settings (Provider
     * / Model / API key). Wired to the Settings surface dispatcher
     * in [LauncherRoot]; the user lands in the Daily letter (LLM)
     * sub-section from there. Default no-op.
     */
    onOpenLlmSettings: () -> Unit = {},
    /**
     * v0.25.9 (auto-update): a non-null value triggers a
     * snackbar across the bottom of the home surface with
     * a "Get it" action and a "Not now" dismiss. The
     * snackbar is rendered by [SnackbarHost] above the
     * bottom corner buttons; the Box layer order keeps
     * it above the digest/search/settings row.
     */
    availableUpdate: org.mindanchor.update.UpdateInfo? = null,
    onUpdateAction: (org.mindanchor.update.UpdateInfo) -> Unit = {},
    onUpdateDismiss: () -> Unit = {},
    /**
     * v0.25.9 (deployability §8.3): while the user has
     * not set MindAnchor as the default home, the home
     * surface shows a single-line callout above the
     * "Notes" card. Tapping the action opens the system
     * Default-Apps settings; tapping "Not now" dismisses
     * the callout for this session (it reappears on the
     * next cold start until isDefaultHome is true).
     */
    isDefaultHome: Boolean = true,
    onSetDefaultHome: () -> Unit = {},
    /**
     * v0.28+ (Phase 3 G-29) — whether the gratitude card
     * should show on the home surface. True means the user
     * has enabled the ritual in Settings.
     */
    gratitudeEnabled: Boolean = false,
    /**
     * v0.28+ (Phase 3 G-29) — the user saved a gratitude
     * entry from the card. The launcher view-model writes
     * the one-or-two-sentence text to the Letters store.
     */
    onSaveGratitude: (String) -> Unit = {},
    /**
     * v0.28+ (Phase 3 G-29) — the user long-pressed the
     * gratitude card to open the full Letter editor. The
     * launcher switches to the letter surface.
     */
    onExpandGratitude: () -> Unit = {},
    /**
     * v0.28+ (Phase 3 G-8) — whether the expressive-writing
     * card should show on the home surface. True means the
     * user has enabled the ritual in Settings.
     */
    expressiveWritingEnabled: Boolean = false,
    /**
     * v0.28+ (Phase 3 G-8) — the user saved an
     * expressive-writing entry. The launcher view-model
     * writes the 3-sentence text to the Letters store.
     */
    onSaveExpressiveWriting: (String) -> Unit = {},
    /**
     * v0.28+ (Phase 3 G-26) — whether the wind-down card
     * should show on the home surface. The launcher
     * applies the wind-down (warmer colour, lower volume)
     * when the user taps Begin.
     */
    windDownEnabled: Boolean = false,
    /**
     * v0.28+ (Phase 3 G-26) — the user tapped Begin on the
     * wind-down card. The launcher applies the wind-down
     * changes.
     */
    onBeginWindDown: () -> Unit = {},
    /**
     * v0.28+ (Phase 3 G-26) — the user tapped Not now on
     * the wind-down card. The card dismisses for this
     * session; it reappears on the next home-surface open
     * after the wind-down time.
     */
    onDismissWindDown: () -> Unit = {},
    /**
     * v0.29+ (Phase 4 G-6) — whether push-up mode is on.
     * When on, opening a flagged app shows the push-up
     * counter; the user must complete N reps before the
     * app opens.
     */
    pushUpModeEnabled: Boolean = false,
    /**
     * v0.29+ (Phase 4 G-6) — the user completed N push-ups
     * on the gate. The launcher proceeds with the
     * launch.
     */
    onPushUpsComplete: () -> Unit = {},
    /**
     * v0.29+ (Phase 4 G-28) — whether voice journaling is
     * on. The Anchor Note gets a Record affordance.
     */
    voiceJournalEnabled: Boolean = false,
    /**
     * v0.29+ (Phase 4 G-28) — the user tapped Record on
     * the voice journal card. The launcher starts audio
     * capture on-device.
     */
    onVoiceRecordStart: () -> Unit = {},
    /**
     * v0.29+ (Phase 4 G-28) — the user tapped Stop on
     * the voice journal card. The launcher stops audio
     * capture.
     */
    onVoiceRecordStop: () -> Unit = {},
    /**
     * v0.29+ (Phase 4 G-28) — the user tapped Transcribe
     * on the voice journal card. The launcher invokes
     * whisper.cpp on-device.
     */
    onVoiceTranscribe: () -> Unit = {},
    /**
     * v0.29+ (Phase 4 G-5) — the bedtime and waketime
     * strings for the Sleep Lock card. The card is shown
     * during the configured sleep window.
     */
    sleepLockBedtime: String = "",
    sleepLockWaketime: String = "",
    /**
     * v0.29+ (Phase 4 G-5) — the user typed the unlock
     * phrase. The launcher dismisses the sleep lock.
     */
    onSleepLockUnlock: () -> Unit = {},
    /**
     * v0.29+ (Phase 4 G-5) — whether the user is inside
     * the configured sleep window. When true, the
     * launcher shows the sleep lock card instead of the
     * regular home surface.
     */
    inSleepWindow: Boolean = false,
    /**
     * v0.28+ (Phase 3 G-25) — the n-of-1 weekly
     * patterns. The card is shown on the home
     * surface when the latest nightly report
     * found at least one Signal/Label pair that
     * survived the LinkFinder significance
     * test. Empty list = card hidden. The
     * `onDismiss` is session-scoped: the
     * card reappears on the next home-surface
     * open if new patterns arrive.
     */
    weeklyPatterns: List<org.mindanchor.report.Pattern> = emptyList(),
    onDismissWeeklyPatterns: () -> Unit = {},
) {
    val now = rememberMinuteTick()
    val clockFormat = rememberClockFormat()

    // v0.25.9 (deployability §8.3): the user can dismiss
    // the default-home callout for the rest of the
    // session. It reappears on the next cold start until
    // isRoleHeld(ROLE_HOME) is true.
    var defaultHomeCalloutDismissed by remember { mutableStateOf(false) }

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

            // v0.29+ (Phase 4 G-13) — the "what this is, in
            // one line" callout. The research-backed +
            // on-device + your-data-never-leaves framing
            // for the first 3 launches. The G-13 version
            // is the polish over the v0.22.0 single-line
            // callout: a small Card with the headline, the
            // three-feature summary, and a "Got it"
            // dismiss. The dismiss is session-scoped; the
            // callout hides permanently once the user has
            // seen it on 3 launches (existing
            // LauncherPrefs.showIntroCallout gate, same as
            // before).
            if (showIntroCallout) {
                LaunchedEffect(Unit) { onRecordLaunch() }
                OnboardingCalloutCard(
                    onDismiss = { /* session-scoped; the
                        callout hides on launch 3+ */ },
                )
            }

            // v0.25.9 (deployability §8.3): the single
            // most likely source of "I installed it but
            // I don't know how to make it my home screen"
            // is an alpha cohort that never sees the callout
            // because we have no first-run affordance for
            // it. Show a one-line card above the Notes
            // section while the launcher is NOT the default
            // home. Tapping the action opens the system
            // Default-Apps settings; tapping "Not now"
            // dismisses for this session.
            if (!isDefaultHome && !defaultHomeCalloutDismissed) {
                DefaultHomeCallout(
                    sky = sky,
                    onAction = onSetDefaultHome,
                    onDismiss = { defaultHomeCalloutDismissed = true },
                )
            }

            // v0.20.4: the quick-notes card. Always
            // visible — the brief is "I want to
            // remember this", and the moment the
            // user thinks it is the moment the card
            // has to be there. Placed *after* the
            // conditional OpenLoop / BedtimeList
            // cards (which are silent most of the
            // time) and *before* the report link
            // and favourites, so the capture surface
            // is between the two summary cards and
            // the action surface — the rough centre
            // of the home screen.
            //
            // v0.25.6: the OpenLoop / OneThing /
            // BedtimeList / Wellness / Report
            // cards and link above are removed
            // permanently. The home now shows the
            // clock, the greeting, and the quick
            // notes card. The rest of the launcher
            // is reachable through the corner
            // buttons (search, settings, letter,
            // notes, history, digest).
            QuickNotesCard(
                sky = sky,
                recent = recentNotes,
                onSave = onAddQuickNote,
                onOpenAll = onOpenNotes,
                onLongPressTitle = dearManDialogState::show,
            )

            // v0.26+ (Phase 1 G-1) — the Going Light consent
            // card. Shows when the schedule is enabled but
            // the OS-level VpnService consent has not been
            // granted yet. The card itself decides when to
            // show (returns null otherwise) so the call
            // site is a no-op when the consent is in place.
            GoingLightConsentCard(
                schedule = goingLightSchedule,
                onDismiss = onGoingLightConsentDismissed,
            )

            // v0.26+ (Phase 1 G-21) — the morning
            // self-compassion break. Shown on the home
            // surface when the user has enabled the
            // ritual; the user dismisses or starts the
            // 90-second protocol from the card. The
            // "Begin" action is a no-op for v0.26.0; the
            // CompassionMoment rotation is the v0.27+
            // hook.
            if (morningCompassionEnabled) {
                MorningCompassionCard(
                    onStart = { /* v0.27+ */ },
                    onSkip = { /* v0.27+ */ },
                )
            }

            // v0.26+ (Phase 1 G-22) — the BA weekly
            // picker. Shown when the user has enabled
            // the ritual; the user picks mastery +
            // pleasure or skips. Save is a no-op in
            // v0.26.0 (the save hook is in the launcher
            // view-model and the home surface calls
            // onSaveBaEntry).
            if (baPromptEnabled) {
                BaPickerCard(
                    onSave = { mastery, pleasure ->
                        onSaveBaEntry(mastery, pleasure)
                    },
                    onSkip = { /* v0.27+ */ },
                )
            }

            // v0.26+ (Phase 1 G-23) — the DEAR MAN / GIVE / FAST
            // dialog. Long-press the Anchor Note title to open.
            // The script is rule-based (no LLM) and saved as a
            // Letter via onSaveDearManScript.
            if (dearManDialogState.visible) {
                DearManDialog(
                    onDismiss = { dearManDialogState.dismiss() },
                    onSave = { script ->
                        onSaveDearManScript(script)
                        dearManDialogState.dismiss()
                    },
                )
            }

            // v0.26+ (Phase 1 G-20) — the notification diet card.
            // Reports the trailing-7-day released count and the
            // Mark 2005 23-minute-interruption-recovery cost.
            // The card hides itself on a fresh install (zero
            // released) — never pre-fill with zeros.
            heldNotificationsDao?.let { HomeDietCard(dao = it) }

            // v0.26+ (Phase 1 G-19) — the compassionate-wrap
            // Snackbar host. AppWatchService posts events to
            // CompassionateWrapNotifier when the user closes a
            // doomscroll app after 30+ minutes; the host
            // shows the Snackbar with Note / Dismiss actions.
            // The Note action writes the event to a Note via
            // the launcher view-model.
            CompassionateWrapHost(
                onNote = onAddCompassionateWrapNote,
            )

            // v0.28+ (Phase 3 G-29) — the gratitude
            // card. Shown on the home surface when the
            // user has enabled the ritual. The 1-tap
            // text field saves to the Letters store as
            // a regular letter; the long-press expands
            // to the full Letter editor (the existing
            // letter surface).
            if (gratitudeEnabled) {
                GratitudeCard(
                    onSave = onSaveGratitude,
                    onExpand = onExpandGratitude,
                )
            }

            // v0.28+ (Phase 3 G-8) — the
            // expressive-writing prompt. Shown on the
            // home surface when the user has enabled
            // the ritual. The Pennebaker 1997
            // 3-sentence minimum-dosage entry point.
            if (expressiveWritingEnabled) {
                ExpressiveWritingCard(
                    onSave = onSaveExpressiveWriting,
                    onDismiss = { /* v0.28+ session
                        scope */ },
                )
            }

            // v0.28+ (Phase 3 G-26) — the wind-down
            // card. Shown on the home surface when the
            // user has enabled the ritual (and is
            // inside the wind-down window, which the
            // launcher view-model decides). The "Begin"
            // action applies the wind-down; the
            // "Not now" dismisses for this session.
            if (windDownEnabled) {
                WindDownCard(
                    onBegin = onBeginWindDown,
                    onDismiss = onDismissWindDown,
                )
            }

            // v0.29+ (Phase 4 G-5) — the Sleep Lock
            // card. Shown on the home surface during
            // the configured sleep window. The
            // 30-second typing + breath gate is the
            // exit. The DevicePolicyManager
            // device-owner grant flow is the
            // follow-up; the Composable is the
            // post-grant UI.
            if (inSleepWindow) {
                SleepLockCard(
                    bedtime = sleepLockBedtime,
                    waketime = sleepLockWaketime,
                    onUnlock = { onSleepLockUnlock() },
                )
            }

            // v0.28+ (Phase 3 G-25) — the n-of-1
            // weekly patterns card. The card is
            // hidden when the latest nightly
            // report found no patterns; the
            // gating is `patterns.isNotEmpty()`,
            // not the existing 14-day floor
            // (the patterns are pre-filtered by
            // PatternFinder to the
            // survived-significance set, so a
            // non-empty list already passed the
            // bar). The one-sentence composer is
            // the project's direction-bands
            // family: never "good" or "bad",
            // never causal, n-of-1 framing.
            if (weeklyPatterns.isNotEmpty()) {
                NOfOnePatternsCard(
                    patterns = weeklyPatterns,
                    onDismiss = onDismissWeeklyPatterns,
                )
            }

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
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
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
                .padding(end = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        val context = LocalContext.current

        // v0.20.1 round 5: notes + check-in history
        // entry points. TopEnd, so neither collides
        // with BottomStart (Digest) or BottomEnd
        // (Settings). One-tap, no scrolling. The
        // brief: "I want to remember this" — the
        // entry must be reachable the moment the
        // user thinks it. Two stacked buttons
        // (notes on top, history below) keep the
        // home screen uncluttered without forcing
        // the user into a menu.
        //
        // v0.20.9: statusBarsPadding keeps the
        // stacked buttons clear of the status bar.
        // The TopStart (Support) corner was removed
        // in v0.25.7 (Task 13); the statusBarsPadding
        // stays as a defensive measure for keyboards
        // / status-bar overlays.
        //
        // v0.25.2-A (Task 6): a third stacked button
        // — "letter" — sits at the top of this
        // Column, above notes + history. The
        // Column's existing 8dp end padding (Bug 6
        // fix from v0.25.1) already applies, so the
        // new button inherits the same touch-target
        // breathing room without a per-button
        // re-pad. The brief: a letter is the one
        // thing the launcher writes for the user —
        // it must be one tap from the home surface
        // the same way notes and history are.
        //
        // v0.25.7 (Task 13): the label is now
        // singular ("letter", one per day), sourced
        // from R.string.letter_singular. The
        // v0.25.2-A label was "letters" (plural) and
        // is removed.
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
            TextButton(onClick = onOpenLetters) { Text(stringResource(R.string.letter_singular)) }
            TextButton(
                onClick = onOpenNotes,
            ) {
                Text(
                    text = stringResource(R.string.notes_shortcut),
                    style = MaterialTheme.typography.labelMedium,
                    color = sky.textSecondary,
                )
            }
            TextButton(
                onClick = onOpenCheckInHistory,
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
                .navigationBarsPadding(),
        ) {
            // v0.25.9 (P0-3): "digest" alone was opaque.
            // Two-line label: title (button shape) +
            // sub-label (one-line purpose). Mirrors the
            // pattern on the rest of the launcher.
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = stringResource(R.string.digest_screen_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = sky.textPrimary,
                )
                Text(
                    text = stringResource(R.string.digest_sub_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = sky.textSecondary,
                )
            }
        }

        // v0.25.9 (auto-update): a SnackbarHost at the
        // bottom of the home surface. Shown only when
        // [availableUpdate] is non-null. The LaunchedEffect
        // re-shows the snackbar on every change of the
        // info value (which only happens on app start, so
        // this fires once per cold start that has an
        // update). [SnackbarDuration.Short] keeps it
        // out of the way of the corner buttons while
        // still being readable.
        val snackbarHostState = remember { SnackbarHostState() }
        // v0.25.11: capture the action label outside the
        // LaunchedEffect — `stringResource` is @Composable and
        // cannot run from a coroutine block. v0.25.10 hardcoded
        // these literals, which tripped HardcodedText and left
        // the matching <string> entries unreferenced.
        val actionLabel = androidx.compose.ui.res.stringResource(
            org.mindanchor.R.string.update_available_action,
        )
        LaunchedEffect(availableUpdate) {
            val info = availableUpdate ?: return@LaunchedEffect
            val message = context.getString(
                org.mindanchor.R.string.update_available,
                info.version,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onUpdateAction(info)
                SnackbarResult.Dismissed -> onUpdateDismiss()
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Lift the snackbar above the bottom corner
                // row (digest / search / settings) so the
                // action button is not under the gesture
                // bar.
                .padding(bottom = 72.dp),
        ) {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                )
            }
        }
    }
}

/**
 * v0.25.9 (deployability §8.3): a single-line card
 * that sits above the QuickNotesCard on the home
 * surface while the user has not set MindAnchor as
 * the default home. A one-tap "Set as home" action
 * opens the system Default-Apps settings; a small
 * "Not now" dismisses the callout for the rest of
 * the session.
 *
 * The card uses the same quiet palette as the rest
 * of the launcher (sky.textPrimary on a translucent
 * fill). A heavy coloured chip would shout over the
 * clock; the goal is to be readable, not loud.
 */
@Composable
private fun DefaultHomeCallout(
    sky: SkyContent,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        color = sky.textPrimary.copy(alpha = 0.08f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(
                    org.mindanchor.R.string.set_default_home_callout,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = androidx.compose.ui.res.stringResource(
                    org.mindanchor.R.string.set_default_home_sub,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            org.mindanchor.R.string.set_default_home_dismiss,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onAction) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            org.mindanchor.R.string.set_default_home_action,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
    val query by viewModel.searchQuery.collectAsState()
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

/**
 * v0.26+ (Phase 1 G-23) — the show/hide state of the
 * DEAR MAN / GIVE / FAST dialog on the home surface.
 * The home surface owns the state; the launcher
 * view-model owns the save callback.
 *
 * Default constructor uses [remember]-style
 * [androidx.compose.runtime.MutableState] so a single
 * instance survives recomposition but not process
 * death. The dialog is a one-shot trigger; if the
 * process is killed mid-dialog, the user re-opens
 * it with a long-press, which is the right cost.
 */
@androidx.compose.runtime.Stable
class DearManDialogState {
    // CodeRabbit review 2026-08-24 (PR #38): a plain
    // Kotlin field is not snapshot state, so the
    // long-press call to [show] did not schedule a
    // recomposition and the dialog never opened. The
    // KDoc already promised MutableState backing; the
    // implementation was missing it. The class is
    // now @Stable and the field is a [mutableStateOf]
    // delegate. The delegate imports (getValue /
    // setValue) are already present in this file.
    internal var visible: Boolean by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun show() { visible = true }
    fun dismiss() { visible = false }
}

/**
 * v0.30+ (Phase 4 G-5) — pin the user to the
 * launcher's task while the sleep window is active.
 *
 * [Activity.startLockTask] does NOT require the
 * launcher to be the device owner. It does require
 * the activity to be at the top of its own task
 * stack, which the launcher always is when it is
 * the default home. The [stopLockTaskOn] counterpart
 * is called from the 30-second unlock phrase in
 * [SleepLockCard].
 *
 * The call is wrapped in [runCatching] because the
 * platform can throw [IllegalStateException] when
 * the activity is paused or in a transition. The
 * sleep lock is a UX safeguard, not a security
 * boundary, so a missed call is a soft fail.
 */
internal fun startLockTaskOn(context: android.content.Context) {
    val activity = context.findActivity()
    if (activity != null) {
        runCatching { activity.startLockTask() }
    }
}

internal fun stopLockTaskOn(context: android.content.Context) {
    val activity = context.findActivity()
    if (activity != null) {
        runCatching { activity.stopLockTask() }
    }
}

/**
 * The LocalContext in a Compose hierarchy is the
 * [android.content.ContextWrapper] that wraps the
 * host activity. [ContextWrapper.getBaseContext]
 * is the wrapped context, and iterating through
 * the chain is the standard way to find the
 * Activity without coupling to a private API.
 * [startLockTask] / [stopLockTask] are [Activity]
 * methods, so we need the Activity, not the
 * Application context.
 */
internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
