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
import org.mindanchor.sleep.BedtimeList
import org.mindanchor.sleep.BedtimePhase
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private enum class LauncherSurface { Home, Drawer, Settings, Ppg, Report, Letter }

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
    val state by viewModel.uiState.collectAsState()
    val openLoop by viewModel.openLoop.collectAsState()
    val bedtimeList by viewModel.bedtimeList.collectAsState()
    val recentNotes by viewModel.notes.collectAsState()
    val wellnessReadings by viewModel.wellnessReadings.collectAsState()
    var surface by remember { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by remember { mutableStateOf<DisplayApp?>(null) }
    var gateFor by remember { mutableStateOf<DisplayApp?>(null) }

    // Where the report was opened from, so back returns there. Two ways
    // in now — the settings section and a line on the home screen — and
    // sending somebody who came from home into settings would be a small
    // daily disorientation.
    var reportCameFrom by remember { mutableStateOf(LauncherSurface.Settings) }
    // v0.25.2-A (Task 6): the letter inbox + reader. Same shape as
    // reportCameFrom — selected date is null on the inbox, non-null
    // in the reader; cameFrom remembers where the user came from so
    // the inbox's back button returns there. Two entry points: the
    // new "letters" TopEnd corner on the home surface, the (later)
    // Reading sub-section in Settings, and the letter notification
    // (Task 8), which writes letterDateSignal from HomeActivity.
    var letterSelectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var letterCameFrom by remember { mutableStateOf(LauncherSurface.Home) }
    val context = LocalContext.current
    val reportStore = remember(context) { ReportStore(context.applicationContext) }
    val storedReport by reportStore.stored.collectAsState(initial = null)
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
        LauncherSurface.Home -> CalmBackground { sky ->
            val showIntroCallout by viewModel.showIntroCallout.collectAsState()
            HomeSurface(
                sky = sky,
                favorites = state.favorites,
                onOpenDrawer = { surface = LauncherSurface.Drawer },
                onOpenSettings = { surface = LauncherSurface.Settings },
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
                loopPhase = openLoop.first,
                loopNote = openLoop.second,
                onLoopSave = viewModel::saveOpenLoop,
                onLoopClear = viewModel::clearOpenLoop,
                bedtimePhase = bedtimeList.first,
                bedtimeItems = bedtimeList.second,
                onBedtimeSave = viewModel::saveBedtimeList,
                onBedtimeClear = viewModel::clearBedtimeList,
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
                wellnessReadings = wellnessReadings,
                showIntroCallout = showIntroCallout,
                onRecordLaunch = viewModel::recordHomeLaunch,
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
        // to letterCameFrom when in the inbox. Letters and
        // modelFits are stubs pending Task 9's SettingsViewModel
        // fields; the call site does not depend on them being
        // real flows today.
        LauncherSurface.Letter -> Surface(modifier = Modifier.fillMaxSize()) {
            val letters: List<Letter> = remember { emptyList() }
            val modelFits = remember { mutableStateOf(false) }
            val letterSize = remember { mutableStateOf(ReadingSize.MEDIUM) }
            val letterStore = remember(context.applicationContext) {
                LetterStore(context.applicationContext)
            }
            val letterScope = rememberCoroutineScope()
            LetterScreen(
                letters = letters,
                modelFits = modelFits.value,
                date = letterSelectedDate,
                size = letterSize.value,
                onSelect = { date -> letterSelectedDate = date },
                onBack = {
                    if (letterSelectedDate != null) {
                        letterSelectedDate = null
                    } else {
                        surface = letterCameFrom
                    }
                },
                onDelete = { date -> letterScope.launch { letterStore.delete(date) } },
                onSetSize = { size -> letterSize.value = size },
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
 * The one unfinished thing — see [org.mindanchor.friction.OpenLoop].
 *
 * Deliberately silent most of the time. It appears once in the quiet
 * hours to take a line, and once the next morning to give it back, and
 * otherwise draws nothing at all. A home screen that always has something
 * to say is a home screen people stop reading.
 */
@Composable
private fun OpenLoopCard(
    sky: SkyContent,
    phase: LoopPhase,
    note: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    when (phase) {
        LoopPhase.NONE -> Unit

        LoopPhase.CAPTURE -> {
            var draft by remember { mutableStateOf("") }
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
                TextButton(onClick = { onSave(draft) }) {
                    Text(stringResource(R.string.loop_save), color = sky.textPrimary)
                }
            }
        }

        LoopPhase.RETURN -> Column(
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
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.loop_clear), color = sky.textSecondary)
            }
        }
    }
}

/**
 * The bedtime to-do list — see [org.mindanchor.sleep.BedtimeList].
 *
 * Deliberately silent most of the time, exactly like the
 * [OpenLoopCard] sibling above. Appears once in the quiet hours to
 * take a 1–5 line list (with a specificity nudge, per Scullin
 * 2018 — the active ingredient is the *specific* item, not the
 * list shape), and once the next morning to hand it back. A home
 * screen that always has something to say is one people stop
 * reading.
 *
 * Distinct from the OpenLoop card on three points:
 *  - Multiple lines (1–5), not a single line.
 *  - The first line of the prompt is a *specificity nudge* — a
 *    heuristic in the data layer ([org.mindanchor.sleep.BedtimeList.isSpecific])
 *    marks a line as specific or not, and a vague list is
 *    encouraged to be re-written in more concrete terms before
 *    being put down.
 *  - The save button is "Put it down", not "Save" or "Done" — the
 *    user is parking the thought for the morning, not crossing
 *    it off.
 */
@Composable
private fun BedtimeListCard(
    sky: SkyContent,
    phase: BedtimePhase,
    items: List<String>,
    onSave: (List<String>) -> Unit,
    onClear: () -> Unit,
) {
    when (phase) {
        BedtimePhase.NONE -> Unit

        BedtimePhase.CAPTURE -> {
            // Up to BedtimeList.MAX_ITEMS draft lines. Each is a
            // pair of (current value, setter) so the user can
            // add lines, edit them, or remove the last one. A
            // single text field per line is the deliberately
            // simple shape — a multi-line text box would invite
            // the "task list" failure mode the brief explicitly
            // rules out.
            val drafts = remember {
                mutableStateListOf<String>().apply { add("") }
            }

            // Specificity nudge: shown when the user has at
            // least one non-empty draft and at least one of
            // those drafts is *not* specific per the heuristic.
            // The nudge is a one-line hint, not a validation
            // gate — the user is allowed to save a vague list;
            // they are simply told the heuristic exists.
            val hasAny = drafts.any { it.isNotBlank() }
            val hasVague = hasAny && drafts.any {
                BedtimeList.cleanLine(it)?.let { line -> !BedtimeList.isSpecific(line) } ?: true
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bedtime_capture),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                    textAlign = TextAlign.Center,
                )
                drafts.forEachIndexed { index, value ->
                    // v0.20.9: each bedtime-list line carries
                    // its own bringIntoViewOnFocus. The list
                    // can grow to BedtimeList.MAX_ITEMS lines
                    // and the one being typed into is the one
                    // the user is looking at; the parent
                    // scroll container needs the request per
                    // line, not just per card, because the
                    // requester is registered on the field
                    // that is currently focused.
                    OutlinedTextField(
                        value = value,
                        onValueChange = { drafts[index] = it },
                        singleLine = true,
                        placeholder = {
                            Text(stringResource(R.string.bedtime_capture_hint))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .padding(top = 8.dp),
                    )
                }
                if (drafts.size < BedtimeList.MAX_ITEMS) {
                    TextButton(onClick = { drafts.add("") }) {
                        Text(
                            stringResource(R.string.bedtime_add_line),
                            color = sky.textSecondary,
                        )
                    }
                }
                if (hasVague) {
                    Text(
                        text = stringResource(R.string.bedtime_specificity_nudge),
                        style = MaterialTheme.typography.bodySmall,
                        color = sky.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(
                    onClick = { onSave(drafts.toList()) },
                    enabled = drafts.any { it.isNotBlank() },
                ) {
                    Text(stringResource(R.string.bedtime_save), color = sky.textPrimary)
                }
            }
        }

        BedtimePhase.RETURN -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.bedtime_return_intro),
                style = MaterialTheme.typography.bodySmall,
                color = sky.textSecondary,
                textAlign = TextAlign.Center,
            )
            items.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = sky.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            TextButton(onClick = onClear) {
                Text(
                    stringResource(R.string.bedtime_clear),
                    color = sky.textSecondary,
                )
            }
        }
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
private fun QuickNotesCard(
    sky: SkyContent,
    recent: List<Note>,
    onSave: (String) -> Unit,
    onOpenAll: () -> Unit,
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
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
    loopPhase: LoopPhase = LoopPhase.NONE,
    loopNote: String? = null,
    onLoopSave: (String) -> Unit = {},
    onLoopClear: () -> Unit = {},
    bedtimePhase: BedtimePhase = BedtimePhase.NONE,
    bedtimeItems: List<String> = emptyList(),
    onBedtimeSave: (List<String>) -> Unit = {},
    onBedtimeClear: () -> Unit = {},
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

            OpenLoopCard(
                sky = sky,
                phase = loopPhase,
                note = loopNote,
                onSave = onLoopSave,
                onClear = onLoopClear,
            )

            // Sibling card to OpenLoopCard above. Same idiom (silent
            // most of the time, fires once in the quiet hours, once
            // in the morning), different mechanism (Scullin 2018:
            // a specific bedtime list, not a single open loop).
            // The two coexist; the brief is explicit that the
            // bedtime list is *not* a replacement for the open loop,
            // they close different kinds of unfinished work.
            BedtimeListCard(
                sky = sky,
                phase = bedtimePhase,
                items = bedtimeItems,
                onSave = onBedtimeSave,
                onClear = onBedtimeClear,
            )

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
            QuickNotesCard(
                sky = sky,
                recent = recentNotes,
                onSave = onAddQuickNote,
                onOpenAll = onOpenNotes,
            )

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
                .statusBarsPadding(),
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
            TextButton(onClick = onOpenLetters) { Text("letters") }
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
