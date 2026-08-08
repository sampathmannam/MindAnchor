package org.mindanchor.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
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
import org.mindanchor.sleep.BedtimeList
import org.mindanchor.sleep.BedtimePhase
import org.mindanchor.report.ReportScreen
import org.mindanchor.report.ReportStore
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.vitals.PpgScreen
import org.mindanchor.support.SupportActivity
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.rememberClockFormat
import org.mindanchor.ui.rememberMinuteTick
import java.time.format.DateTimeFormatter

private enum class LauncherSurface { Home, Drawer, Settings, Ppg, Report }

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
) {
    val state by viewModel.uiState.collectAsState()
    val openLoop by viewModel.openLoop.collectAsState()
    val bedtimeList by viewModel.bedtimeList.collectAsState()
    var surface by remember { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by remember { mutableStateOf<DisplayApp?>(null) }
    var gateFor by remember { mutableStateOf<DisplayApp?>(null) }

    // Where the report was opened from, so back returns there. Two ways
    // in now — the settings section and a line on the home screen — and
    // sending somebody who came from home into settings would be a small
    // daily disorientation.
    var reportCameFrom by remember { mutableStateOf(LauncherSurface.Settings) }
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

    BackHandler(enabled = surface != LauncherSurface.Home || gateFor != null) {
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
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    OutlinedTextField(
                        value = value,
                        onValueChange = { drafts[index] = it },
                        singleLine = true,
                        placeholder = {
                            Text(stringResource(R.string.bedtime_capture_hint))
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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

// combinedClickable, for the long-press on a favourite.
@OptIn(ExperimentalFoundationApi::class)
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
) {
    val now = rememberMinuteTick()
    val clockFormat = rememberClockFormat()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 88.dp),
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

        TextButton(
            onClick = onOpenDrawer,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                text = stringResource(R.string.open_drawer),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textSecondary,
            )
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomEnd),
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
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(context, SupportActivity::class.java))
                }
            },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Text(
                text = stringResource(R.string.support_shortcut),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        // v0.20.1 round 5: notes entry point. TopEnd
        // so it does not collide with TopStart
        // (Support), BottomStart (Digest), or
        // BottomEnd (Settings). One-tap, no
        // scrolling. The brief: "I want to remember
        // this" — the entry must be reachable the
        // moment the user thinks it.
        TextButton(
            onClick = onOpenNotes,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(
                text = stringResource(R.string.notes_shortcut),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        TextButton(
            onClick = {
                runCatching { context.startActivity(Intent(context, DigestActivity::class.java)) }
            },
            modifier = Modifier.align(Alignment.BottomStart),
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
