package org.mindanchor.settings

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mindanchor.R
import org.mindanchor.admin.DeviceOwner
import org.mindanchor.friction.AppWatchService
import org.mindanchor.grayscale.Grayscale
import org.mindanchor.launcher.DisplayApp
import org.mindanchor.narrate.ModelSlot
import org.mindanchor.onboarding.Goal
import org.mindanchor.onboarding.GoalMap
import org.mindanchor.onboarding.SettingsSection
import org.mindanchor.ui.NatureScene
import java.time.format.DateTimeFormatter

/**
 * A section title, marked when the person named a reason for it.
 *
 * The marker is a quiet line of small text rather than a colour, a badge
 * or a count. This app has no badges anywhere by design, and a settings
 * screen that scores you against your own stated goals is the shape of
 * thing it exists to be the opposite of.
 */
@Composable
private fun SectionHeading(titleRes: Int, section: SettingsSection?, goals: Set<Goal>) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
        )
        if (section != null && GoalMap.isChosen(section, goals)) {
            Text(
                text = stringResource(R.string.goal_marker),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * A half-hour stepper for one end of the quiet hours.
 *
 * Steppers rather than a clock dialog: the targets are large, which
 * matters for anyone with tremor or in distress, and nudging is what
 * people actually do to a bedtime.
 */
@Composable
private fun TimeNudger(labelRes: Int, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEarlier) {
            Text(stringResource(R.string.time_earlier))
        }
        TextButton(onClick = onLater) {
            Text(stringResource(R.string.time_later))
        }
    }
}

private fun Goal.labelRes(): Int = when (this) {
    Goal.INTERRUPTIONS -> R.string.goal_interruptions
    Goal.COMPULSIVE_APPS -> R.string.goal_compulsive
    Goal.SLEEP -> R.string.goal_sleep
    Goal.MEASUREMENT -> R.string.goal_measurement
}

/**
 * Minimal settings: default-launcher role, notification batching, hidden
 * apps, and a short honest "about". Everything else waits for its milestone.
 */
@Composable
fun SettingsScreen(
    allApps: List<DisplayApp>,
    hiddenApps: List<DisplayApp>,
    onUnhide: (DisplayApp) -> Unit,
    onBack: () -> Unit,
    /** Opens the heart-rhythm reading on its own surface. */
    onOpenPpg: () -> Unit = {},
    /** Opens last night's report on its own surface. */
    onOpenReport: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val batchingEnabled by viewModel.batchingEnabled.collectAsState()
    val batchedApps by viewModel.batchedApps.collectAsState()

    // Special access is granted in system settings, so nothing in this
    // composition changes when the user comes back — the screen used to keep
    // insisting the permission was missing until something else forced a
    // recomposition. Re-read the grants on every resume instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionEpoch by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasNotificationAccess = remember(permissionEpoch) { viewModel.hasNotificationAccess() }
    val hasDndAccess = remember(permissionEpoch) { viewModel.hasDndAccess() }
    val hasUsageAccess = remember(permissionEpoch) { viewModel.hasUsageAccess() }
    LaunchedEffect(hasUsageAccess) {
        if (hasUsageAccess) viewModel.refreshSleep()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }

        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        val roleManager = context.getSystemService(RoleManager::class.java)
        val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        if (!isDefault) {
            TextButton(
                onClick = {
                    roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        ?.let { activityLauncher.launch(it) }
                },
            ) {
                Text(stringResource(R.string.set_default_launcher))
            }
        } else {
            Text(
                text = stringResource(R.string.is_default_launcher),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- What you said you wanted ---
        //
        // Onboarding asked, stored the answer, and nothing ever read it
        // again — which made the whole step decorative and left this a
        // long screen where everything looks equally relevant to everyone.
        // Nothing here switches anything on: onboarding promises in as
        // many words that it will not, and imposed structure is the thing
        // "Going Light" found fails.
        val goals by viewModel.goals.collectAsState()
        var editingGoals by remember { mutableStateOf(false) }
        run {
            Text(
                text = stringResource(R.string.goals_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            if (editingGoals) {
                Goal.entries.forEach { goal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setGoals(
                                    if (goal in goals) goals - goal else goals + goal,
                                )
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = goal in goals, onCheckedChange = null)
                        Text(
                            text = stringResource(goal.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else if (goals.isEmpty()) {
                // Shown rather than hidden. Hiding the whole block when
                // nothing is named left the only way to name something
                // behind a replay of onboarding, which cannot be replayed.
                Text(
                    text = stringResource(R.string.goals_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Resolved through map, which is inline, so stringResource
                // still runs in the composable body. joinToString is not
                // inline, and calling it from that lambda does not compile.
                val named = goals.map { stringResource(it.labelRes()) }
                Text(
                    text = named.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { editingGoals = !editingGoals }) {
                Text(
                    stringResource(
                        if (editingGoals) R.string.goals_done else R.string.goals_change,
                    ),
                )
            }
        }

        // --- Pauses that have stopped being pauses ---
        //
        // The one place this is allowed to appear. Never a notification,
        // never a card on the home screen, never anything uninvited: a
        // person having a bad month does not need their phone volunteering
        // that their guards look pointless. They have to come and ask.
        //
        // It reports and declines to interpret. Whether going through every
        // time means the pause is useless or means it is quietly working is
        // not something a launcher can know, so both doors are the same
        // size and neither is recommended.
        val stale by viewModel.stalePauses.collectAsState()
        if (stale.isNotEmpty()) {
            Text(
                text = stringResource(R.string.stale_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.stale_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stale.forEach { (packageName, tally) ->
                val label = allApps
                    .firstOrNull { it.component.substringBefore('/') == packageName }
                    ?.label
                    ?: packageName
                Text(
                    text = stringResource(R.string.stale_line, label, tally.shown),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row {
                    TextButton(onClick = { viewModel.keepPause(packageName) }) {
                        Text(stringResource(R.string.stale_keep))
                    }
                    TextButton(onClick = { viewModel.dropPause(packageName) }) {
                        Text(stringResource(R.string.stale_drop))
                    }
                }
            }
        }

        // --- Small things ---
        //
        // Behavioural activation: the small thing shifts mood, and the
        // moment somebody reaches for a distraction is the only moment
        // anything can see that a small thing is being avoided. What makes
        // that safe rather than cruel is that these are the person's own
        // words, written while calm — nothing here is ever seeded with
        // suggestions about how somebody ought to feel better.
        Text(
            text = stringResource(R.string.small_things_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.small_things_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val smallThings by viewModel.smallThings.collectAsState()
        smallThings.forEach { thing ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thing,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.removeSmallThing(thing) }) {
                    Text(stringResource(R.string.small_things_remove))
                }
            }
        }
        if (smallThings.size < org.mindanchor.friction.SmallThings.MAX) {
            var draft by remember { mutableStateOf("") }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.small_things_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            TextButton(
                onClick = {
                    viewModel.addSmallThing(draft)
                    draft = ""
                },
            ) {
                Text(stringResource(R.string.small_things_add))
            }
        }

        // --- Notification batching (F1) ---
        SectionHeading(R.string.batching_section, SettingsSection.BATCHING, goals)
        Text(
            text = stringResource(R.string.batching_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!hasNotificationAccess) {
            TextButton(
                onClick = {
                    runCatching {
                        activityLauncher.launch(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.grant_notification_access))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.batching_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = batchingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            permissionLauncher.launch(
                                android.Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                        viewModel.setBatchingEnabled(enabled)
                    },
                )
            }

            if (batchingEnabled) {
                TextButton(onClick = viewModel::releaseNow) {
                    Text(stringResource(R.string.digest_release_now))
                }
                Text(
                    text = stringResource(R.string.batching_choose_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                allApps.forEach { app ->
                    val packageName = app.component.substringBefore('/')
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = packageName in batchedApps,
                            onCheckedChange = { viewModel.setAppBatched(packageName, it) },
                        )
                    }
                }
            }
        }

        // --- Home screen appearance ---
        val natureScene by viewModel.natureScene.collectAsState()
        Text(
            text = stringResource(R.string.appearance_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.appearance_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            NatureScene.ROTATE to R.string.scene_rotate,
            NatureScene.MEADOW to R.string.scene_meadow,
            NatureScene.WATER to R.string.scene_water,
            NatureScene.FOREST to R.string.scene_forest,
            NatureScene.OFF to R.string.scene_off,
        ).forEach { (scene, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setNatureScene(scene) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = natureScene == scene,
                    onClick = { viewModel.setNatureScene(scene) },
                )
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // --- Where the pause applies ---
        //
        // The pause used to exist only inside this launcher, which meant it
        // covered the deliberate route to an app and missed the compulsive
        // one — nobody navigates home and searches for an app when a
        // notification already put it one tap away.
        //
        // Turning this on means enabling an accessibility service, which is
        // the most alarming thing this app ever asks for and ought to be.
        // So the screen says what it can and cannot do before it asks, and
        // works fine forever if the answer is no.
        SectionHeading(R.string.watch_section, SettingsSection.WATCH, goals)
        Text(
            text = stringResource(R.string.watch_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.watch_cannot_read),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Read fresh on every resume: the service can be switched off from
        // Android's own settings without this app being told, and a screen
        // insisting it is on when it is not is worse than no screen.
        val watching = remember(permissionEpoch) { AppWatchService.running }
        Text(
            text = stringResource(
                if (watching) R.string.watch_on else R.string.watch_off,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        ) {
            Text(
                stringResource(
                    if (watching) R.string.watch_manage else R.string.watch_turn_on,
                ),
            )
        }

        // --- Sunset mode (F4) ---
        val sunsetEnabled by viewModel.sunsetEnabled.collectAsState()
        SectionHeading(R.string.sunset_section, SettingsSection.SUNSET, goals)
        Text(
            text = stringResource(R.string.sunset_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The window used to be hardcoded to 22:00 → 07:00. That is
        // somebody else's bedtime: wrong for shift workers, wrong for
        // anyone on call, wrong for night staff — and a wind-down that
        // begins three hours after you went to bed is not a wind-down.
        val sunsetStart by viewModel.sunsetStart.collectAsState()
        val sunsetEnd by viewModel.sunsetEnd.collectAsState()
        Text(
            text = stringResource(
                R.string.sunset_window,
                sunsetStart.format(HOUR_MINUTE),
                sunsetEnd.format(HOUR_MINUTE),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        TimeNudger(
            labelRes = R.string.sunset_starts,
            onEarlier = { viewModel.nudgeSunset(-30, 0) },
            onLater = { viewModel.nudgeSunset(30, 0) },
        )
        TimeNudger(
            labelRes = R.string.sunset_ends,
            onEarlier = { viewModel.nudgeSunset(0, -30) },
            onLater = { viewModel.nudgeSunset(0, 30) },
        )
        if (!hasDndAccess) {
            TextButton(
                onClick = {
                    runCatching {
                        activityLauncher.launch(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.grant_dnd_access))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sunset_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = sunsetEnabled,
                    onCheckedChange = viewModel::setSunsetEnabled,
                )
            }
        }

        // --- Heart rhythm ---
        //
        // The watch measures HRV and keeps it: it never leaves the COROS
        // app, and it cannot be derived from heart rate, because RMSSD is
        // defined over beat-to-beat intervals and averaged BPM has already
        // thrown that away. So it is measured here instead — which also
        // means it survives changing watch, or wearing none at all.
        SectionHeading(R.string.ppg_section, SettingsSection.SLEEP, goals)
        Text(
            text = stringResource(R.string.ppg_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenPpg) {
            Text(stringResource(R.string.ppg_start))
        }

        // --- Sleep rhythm (F5) ---
        val sleepSummary by viewModel.sleepSummary.collectAsState()
        SectionHeading(R.string.sleep_section, SettingsSection.SLEEP, goals)
        if (!hasUsageAccess) {
            Text(
                text = stringResource(R.string.sleep_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    runCatching { activityLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    viewModel.refreshSleep()
                },
            ) {
                Text(stringResource(R.string.grant_usage_access))
            }
        } else {
            val summary = sleepSummary
            if (summary == null || summary.windows.isEmpty()) {
                Text(
                    text = stringResource(R.string.sleep_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                summary.regularityScore?.let { score ->
                    Text(
                        text = stringResource(R.string.sleep_regularity, score),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                summary.windows.forEach { window ->
                    Text(
                        text = stringResource(
                            R.string.sleep_window_row,
                            window.wakeDate.toString(),
                            "%.1f".format(window.durationHours),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A mirror, not a diagnosis.
                //
                // Cross-person inference from phone signals does not
                // transfer — an AUC of 0.82 in 57 students became 0.57 in
                // 5,262. So this keeps the within-person baseline, which
                // does generalise, and drops the inference entirely: it
                // counts nights and names nothing. Off until asked for,
                // and it never notifies.
                val mirrorOn by viewModel.sleepMirror.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mirror_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = mirrorOn, onCheckedChange = viewModel::setSleepMirror)
                }
                Text(
                    text = stringResource(R.string.mirror_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val laterNights by viewModel.nightsLaterThanUsual.collectAsState()
                laterNights?.let { count ->
                    Text(
                        text = stringResource(R.string.mirror_line, count),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.sleep_regularity_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // --- Hidden apps ---
        Text(
            text = stringResource(R.string.hidden_apps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        if (hiddenApps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_hidden_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            hiddenApps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onUnhide(app) }) {
                        Text(stringResource(R.string.action_unhide))
                    }
                }
            }
        }

        // --- Wellbeing pulse (F7) ---
        SectionHeading(R.string.pulse_section, SettingsSection.PULSE, goals)
        Text(
            text = stringResource(R.string.pulse_section_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(context, org.mindanchor.pulse.PulseActivity::class.java),
                    )
                }
            },
        ) {
            Text(stringResource(R.string.pulse_take))
        }

        // --- Last night's look (nightly report) ---
        //
        // The only part of this screen that ever runs on its own: a
        // background worker, gated on the phone charging and idle, that
        // compares the day against this person's own history and pulls
        // up what the research says the thing measured actually is. See
        // ReportComposer for why it never joins those two together, and
        // ReportWorker for why an ordinary, quiet night is success too.
        SectionHeading(R.string.report_section, SettingsSection.PULSE, goals)
        Text(
            text = stringResource(R.string.report_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val reportEnabled by viewModel.reportEnabled.collectAsState()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.report_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = reportEnabled, onCheckedChange = viewModel::setReportEnabled)
        }
        TextButton(onClick = onOpenReport) {
            Text(stringResource(R.string.report_open))
        }

        // --- Research on file (the corpus every report draws on) ---
        //
        // Twenty-six bundled passages is a seed, not a library, and the
        // retrieval behind every report gets better the more there is to
        // retrieve from. Everything else here can be improved by shipping
        // an update; the research should not have to wait on one, nor be
        // limited to what one person thought to include. See CorpusImport
        // for why an import merges rather than replaces.
        SectionHeading(R.string.corpus_section, SettingsSection.PULSE, goals)
        Text(
            text = stringResource(R.string.corpus_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // Nothing checks whether an imported passage explains or
            // interprets. Saying so is the honest alternative to
            // pretending to a check that is not there.
            text = stringResource(R.string.corpus_verbatim),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val corpusSize by viewModel.corpusSize.collectAsState()
        val corpusImported by viewModel.corpusImported.collectAsState()
        val lastImport by viewModel.lastImport.collectAsState()
        LaunchedEffect(Unit) { viewModel.refreshCorpus() }
        Text(
            text = stringResource(R.string.corpus_count, corpusSize),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        val corpusPicker = rememberLauncherForActivityResult(
            // OpenDocument rather than GetContent: it returns a document
            // this app may read again later, and it lets a plain .tsv
            // through on providers that would not offer it under a
            // stricter MIME type. "*/*" because text/tab-separated-values
            // is not a type every file provider on every phone reports,
            // and a picker that shows nothing selectable is a dead end.
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::importCorpus) }
        TextButton(onClick = { corpusPicker.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.corpus_import))
        }
        Text(
            text = stringResource(R.string.corpus_format),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastImport?.let { result ->
            // Reported in whatever combination actually happened: added
            // and corrected, or nothing usable, plus the counts that tell
            // somebody their file was in the wrong format rather than
            // leaving them to wonder why it did so little.
            Column(modifier = Modifier.padding(top = 8.dp)) {
                val line = when {
                    result.unreadable -> stringResource(R.string.corpus_result_unreadable)
                    result.added == 0 && result.replaced == 0 ->
                        stringResource(R.string.corpus_result_none)
                    else -> stringResource(R.string.corpus_result_changed, result.added, result.replaced)
                }
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
                if (result.skippedRows > 0) {
                    Text(
                        text = stringResource(R.string.corpus_result_skipped, result.skippedRows),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.truncated) {
                    Text(
                        text = stringResource(R.string.corpus_result_truncated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (corpusImported) {
            TextButton(onClick = viewModel::clearCorpus) {
                Text(stringResource(R.string.corpus_clear))
            }
        }

        // --- Model (the small model a future writing engine would run) ---
        //
        // No inference engine is built into this app yet — see
        // org.mindanchor.narrate.NoEngineNarrator. Importing a model here
        // does not yet make any writing happen; it records the file and,
        // exactly like ModelSlot was built to, reports honestly whether
        // this phone has enough memory to run it once an engine exists.
        SectionHeading(R.string.model_section, SettingsSection.PULSE, goals)
        Text(
            text = stringResource(R.string.model_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.model_no_engine),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val modelPresent by viewModel.modelPresent.collectAsState()
        val modelFit by viewModel.modelFit.collectAsState()
        val modelImportFailed by viewModel.modelImportFailed.collectAsState()
        LaunchedEffect(Unit) { viewModel.refreshModel() }
        Text(
            text = stringResource(if (modelPresent) R.string.model_present else R.string.model_none),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (modelPresent) {
            val fitRes = when (modelFit) {
                ModelSlot.Fit.FITS -> R.string.model_fit_fits
                ModelSlot.Fit.TIGHT -> R.string.model_fit_tight
                ModelSlot.Fit.TOO_LARGE -> R.string.model_fit_too_large
                ModelSlot.Fit.UNSUPPORTED -> R.string.model_fit_unsupported
            }
            Text(
                text = stringResource(fitRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val modelPicker = rememberLauncherForActivityResult(
            // OpenDocument, and "*/*", for the same reason as the corpus
            // picker above: a GGUF is not a MIME type every file provider
            // on every phone reports, and a picker offering nothing
            // selectable is a dead end.
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::importModel) }
        TextButton(onClick = { modelPicker.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.model_import))
        }
        if (modelImportFailed) {
            Text(
                text = stringResource(R.string.model_import_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (modelPresent) {
            TextButton(onClick = viewModel::clearModel) {
                Text(stringResource(R.string.model_clear))
            }
        }

        // --- Check-ins (EMA) ---
        //
        // The other half of "Labels" alongside the pulse above: a handful
        // of taps a day rather than a fortnightly instrument. The count
        // is stated plainly and never as a target — a skipped prompt is
        // normal, not a shortfall, so nothing here is styled as a streak.
        SectionHeading(R.string.ema_section, SettingsSection.PULSE, goals)
        Text(
            text = stringResource(R.string.ema_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val emaEnabled by viewModel.emaEnabled.collectAsState()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ema_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = emaEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.setEmaEnabled(enabled)
                },
            )
        }
        val emaCount by viewModel.emaCount.collectAsState()
        Text(
            text = stringResource(R.string.ema_count, emaCount),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(
                if (emaCount < org.mindanchor.model.EmaSchedule.LABELS_BEFORE_TAPER) {
                    R.string.ema_learning
                } else {
                    R.string.ema_settled
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Your people and your plan ---
        Text(
            text = stringResource(R.string.support_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.support_section_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(context, org.mindanchor.support.SupportActivity::class.java),
                    )
                }
            },
        ) {
            Text(stringResource(R.string.support_open))
        }

        // --- Enforced quiet hours ---
        //
        // The only thing in this app that a person cannot walk straight
        // through. That is the point, and also why it is buried this far
        // down, gated behind a factory reset, and reversible from here.
        SectionHeading(R.string.owner_section, SettingsSection.OWNER, goals)
        Text(
            text = stringResource(R.string.owner_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val isOwner = remember(permissionEpoch) { DeviceOwner.isDeviceOwner(context) }
        if (!isOwner) {
            Text(
                text = stringResource(R.string.owner_needs_setup),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = DeviceOwner.setupCommand(context),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.owner_active),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.owner_protected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Confirmed, but never discouraged. Handing this back is the
            // escape hatch and must stay one tap from reachable — the
            // dialog exists only because getting it back afterwards needs
            // a factory reset, which is not a thing to discover after an
            // accidental tap. It states that cost and gets out of the way.
            var confirmingRelease by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmingRelease = true }) {
                Text(stringResource(R.string.owner_release))
            }
            if (confirmingRelease) {
                AlertDialog(
                    onDismissRequest = { confirmingRelease = false },
                    title = { Text(stringResource(R.string.owner_release)) },
                    text = { Text(stringResource(R.string.owner_release_cost)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmingRelease = false
                            viewModel.releaseDeviceOwner { permissionEpoch++ }
                        }) {
                            Text(stringResource(R.string.owner_release_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingRelease = false }) {
                            Text(stringResource(R.string.action_close))
                        }
                    },
                )
            }
        }

        // --- Colour ---
        //
        // Needs a permission Android will not hand to an app from inside
        // the app, which is correct. So the screen states plainly what to
        // run, once, from a computer — and works fine forever if nobody
        // ever does.
        SectionHeading(R.string.grayscale_section, SettingsSection.GRAYSCALE, goals)
        Text(
            text = stringResource(R.string.grayscale_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Keyed on the resume epoch like every other grant on this screen.
        // Without it, someone runs the adb command on their computer, comes
        // back to the phone, and is still told to run the adb command.
        // The current state is keyed too: the sunset schedule turns
        // grayscale on at 22:00 without this screen's involvement.
        val grayscaleGranted = remember(permissionEpoch) { Grayscale.isGranted(context) }
        var grayscaleNow by remember(permissionEpoch) { mutableStateOf(Grayscale.isOn(context)) }
        val greyNights by viewModel.grayscaleAtNight.collectAsState(initial = false)

        if (!grayscaleGranted) {
            Text(
                text = stringResource(R.string.grayscale_needs_grant),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = Grayscale.grantCommand(context.packageName),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.grayscale_now),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = grayscaleNow,
                    onCheckedChange = {
                        Grayscale.set(context, it)
                        grayscaleNow = Grayscale.isOn(context)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.grayscale_at_night),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = greyNights,
                    onCheckedChange = { viewModel.setGrayscaleAtNight(it) },
                )
            }
            Text(
                text = stringResource(R.string.grayscale_shares_a_switch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- Keeping a copy ---
        //
        // The counterpart to refusing cloud backup. Without this, a reset
        // phone takes the safety plan with it and there is no way back.
        Text(
            text = stringResource(R.string.backup_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.backup_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var backupMessage by remember { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        val repo = remember { org.mindanchor.backup.BackupRepository(context) }

        val saveTo = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val now = System.currentTimeMillis()
                val text = repo.export(now)
                backupMessage = if (org.mindanchor.backup.BackupRepository.write(context, uri, text)) {
                    R.string.backup_saved
                } else {
                    R.string.backup_failed
                }
            }
        }
        val restoreFrom = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = org.mindanchor.backup.BackupRepository.read(context, uri)
                backupMessage = when {
                    text == null -> R.string.backup_failed
                    repo.import(text, System.currentTimeMillis()) -> R.string.backup_restored
                    else -> R.string.backup_not_a_backup
                }
            }
        }

        TextButton(
            onClick = {
                val stamp = java.time.LocalDate.now().toString()
                runCatching {
                    saveTo.launch(org.mindanchor.backup.BackupRepository.fileName(stamp))
                }
            },
        ) {
            Text(stringResource(R.string.backup_save))
        }
        TextButton(
            onClick = {
                runCatching { restoreFrom.launch(arrayOf("application/json", "text/plain", "*/*")) }
            },
        ) {
            Text(stringResource(R.string.backup_restore))
        }
        backupMessage?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
