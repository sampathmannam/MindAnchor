@file:Suppress("MaxLineLength", "FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")
package org.mindanchor.settings

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mindanchor.Alarms
import org.mindanchor.R
import org.mindanchor.admin.DeviceOwner
import org.mindanchor.friction.AppWatchService
import org.mindanchor.grayscale.Grayscale
import org.mindanchor.launcher.DisplayApp
import org.mindanchor.narrate.ModelSlot
import org.mindanchor.notifications.BatchSchedule
import org.mindanchor.onboarding.Goal
import org.mindanchor.report.MeasureSource
import org.mindanchor.report.Signal
import org.mindanchor.onboarding.GoalMap
import org.mindanchor.onboarding.SettingsSection
import org.mindanchor.reader.ReadingSize
import org.mindanchor.sunset.Chronotype
import org.mindanchor.vitals.HealthConnectSource
import org.mindanchor.vitals.coros.CorosConnectionState
import org.mindanchor.vitals.coros.CorosSyncWorker
import org.mindanchor.ui.NatureScene
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
 * v0.20.9: Modifier extension that scrolls the nearest
 * scrollable ancestor to bring the receiving composable
 * into view when it gains focus. See the same-named helper
 * in HomeScreen for the rationale — the settings screen
 * has the same risk on the COROS form, the time-picker
 * hour/minute inputs (when those are introduced), and any
 * other free-text field added in this file.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewOnFocus(): Modifier {
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
 * A half-hour stepper for one end of the quiet hours.
 *
 * Steppers rather than a clock dialog: the targets are large, which
 * matters for anyone with tremor or in distress, and nudging is what
 * people actually do to a bedtime.
 */
@Composable
private fun timeNudgerRow(
    label: String,
    /** The current value to display next to the label. */
    value: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // v0.20.9: the time-nudger rows used to render
            // only "Starts / Earlier / Later" and
            // "Ends / Earlier / Later" — the value lived
            // only in the "Quiet hours run 22:00 to 07:00."
            // line above, and a person who wanted to know
            // what the start time was had to read two
            // lines and remember the first while reading
            // the second. The fix is to show the value on
            // the same row as the label, with the earlier /
            // later buttons to its right. The same
            // shape works for the batching time-slot
            // picker, which had the same defect.
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.time_nudger_row, label, value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onEarlier) {
            Text(stringResource(R.string.time_earlier))
        }
        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onLater) {
            Text(stringResource(R.string.time_later))
        }
    }
}

/** One probe row: the signal's name and what arrived, or an honest dash. */
@Composable
private fun ProbeLine(labelRes: Int, value: String?) {
    Text(
        text = stringResource(
            R.string.probe_line,
            stringResource(labelRes),
            value ?: stringResource(R.string.probe_absent),
        ),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * One row of the wellness section in the settings panel: the
 * signal's name, today's band, the personal median, the MAD,
 * and the raw z-score. The row reads top-to-bottom as
 *
 *   HRV — above your usual
 *   your usual: 42 ms
 *   typical spread (MAD): 8 ms
 *   robust z-score: 1.32
 *
 * Numbers only — the row is read by a person who tapped into
 * the section, not a glance surface. The home card collapses
 * the same information to a single line.
 */
@Composable
private fun WellnessSignalRow(reading: org.mindanchor.vitals.WellnessReading) {
    val name = stringResource(wellnessSettingsSignalNameRes(reading.signal))
    val bandLine = stringResource(
        wellnessSettingsBandRes(reading.direction),
        name,
    )
    val medianLine = reading.baseline.median?.let { median ->
        stringResource(
            R.string.wellness_settings_line,
            stringResource(R.string.wellness_settings_baseline_label),
            formatWellnessSettingsValue(reading.signal, median),
        )
    } ?: stringResource(R.string.wellness_baseline_building)
    val madLine = reading.baseline.mad?.let { mad ->
        stringResource(
            R.string.wellness_settings_line,
            stringResource(R.string.wellness_settings_mad_label),
            formatWellnessSettingsValue(reading.signal, mad),
        )
    } ?: ""
    val zLine = reading.zScore?.let { z ->
        stringResource(R.string.wellness_settings_z_score, z)
    } ?: ""

    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = bandLine,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = medianLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (madLine.isNotEmpty()) {
            Text(
                text = madLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (zLine.isNotEmpty()) {
            Text(
                text = zLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun wellnessSettingsSignalNameRes(
    signal: org.mindanchor.vitals.WellnessSignal,
): Int = when (signal) {
    org.mindanchor.vitals.WellnessSignal.HRV -> R.string.wellness_signal_hrv
    org.mindanchor.vitals.WellnessSignal.RESTING_HEART_RATE -> R.string.wellness_signal_resting_hr
    org.mindanchor.vitals.WellnessSignal.STEPS -> R.string.wellness_signal_steps
    org.mindanchor.vitals.WellnessSignal.SLEEP_MINUTES -> R.string.wellness_signal_sleep
    org.mindanchor.vitals.WellnessSignal.MINDFULNESS_MINUTES -> R.string.wellness_signal_mindfulness
}

private fun wellnessSettingsBandRes(
    direction: org.mindanchor.vitals.WellnessDirection,
): Int = when (direction) {
    org.mindanchor.vitals.WellnessDirection.NO_DATA -> R.string.wellness_settings_band_no_data
    org.mindanchor.vitals.WellnessDirection.AT -> R.string.wellness_settings_band_at
    org.mindanchor.vitals.WellnessDirection.ABOVE -> R.string.wellness_settings_band_above
    org.mindanchor.vitals.WellnessDirection.MUCH_ABOVE -> R.string.wellness_settings_band_much_above
    org.mindanchor.vitals.WellnessDirection.BELOW -> R.string.wellness_settings_band_below
}

/**
 * Render a [WellnessSignal] value for the settings panel.
 *
 * Routes the user-visible unit text through the existing
 * [value_milliseconds] / [value_bpm] / [value_minutes] resources so a
 * translator can reword "min" to "minutes" (or reorder words for
 * a RTL locale) without touching the launcher. Step count uses
 * [NumberFormat.getIntegerInstance] so the locale's thousands
 * separator is honoured — `%,d` was hard-coding en-US.
 */
@Composable
private fun formatWellnessSettingsValue(
    signal: org.mindanchor.vitals.WellnessSignal,
    value: Double,
): String = when (signal) {
    org.mindanchor.vitals.WellnessSignal.HRV ->
        stringResource(R.string.value_milliseconds, value.roundToInt())
    org.mindanchor.vitals.WellnessSignal.RESTING_HEART_RATE ->
        stringResource(R.string.value_bpm, value.roundToInt())
    org.mindanchor.vitals.WellnessSignal.STEPS ->
        NumberFormat.getIntegerInstance().format(value.roundToLong())
    org.mindanchor.vitals.WellnessSignal.SLEEP_MINUTES,
    org.mindanchor.vitals.WellnessSignal.MINDFULNESS_MINUTES ->
        stringResource(R.string.value_minutes, value.toInt())
}

private fun signalLabelRes(signal: Signal): Int = when (signal) {
    Signal.HRV -> R.string.signal_hrv
    Signal.RESTING_HEART_RATE -> R.string.signal_resting_hr
    Signal.SLEEP_MINUTES -> R.string.signal_sleep_minutes
    Signal.SLEEP_ONSET -> R.string.signal_sleep_onset
    Signal.MINDFULNESS_MINUTES -> R.string.signal_mindfulness
    Signal.STEPS -> R.string.signal_steps
    Signal.FIRST_UNLOCK -> R.string.signal_first_unlock
    Signal.SCREEN_TIME -> R.string.signal_screen_time
    Signal.VALENCE -> R.string.signal_valence
    Signal.AROUSAL -> R.string.signal_arousal
}

/** A null source — from a future version's file — reads as plain absence. */
private fun sourceLabelRes(source: MeasureSource?): Int = when (source) {
    MeasureSource.MEASURED_HERE -> R.string.source_measured
    MeasureSource.WEARABLE -> R.string.source_wearable
    MeasureSource.PHONE_INFERRED -> R.string.source_phone
    null -> R.string.arriving_never
}

private fun Goal.labelRes(): Int = when (this) {
    Goal.INTERRUPTIONS -> R.string.goal_interruptions
    Goal.COMPULSIVE_APPS -> R.string.goal_compulsive
    Goal.SLEEP -> R.string.goal_sleep
    Goal.MEASUREMENT -> R.string.goal_measurement
}

private fun Chronotype.labelRes(): Int = when (this) {
    Chronotype.MORNING_LARK -> R.string.chronotype_morning_lark
    Chronotype.NEUTRAL -> R.string.chronotype_neutral
    Chronotype.NIGHT_OWL -> R.string.chronotype_night_owl
    Chronotype.SHIFT_WORKER -> R.string.chronotype_shift_worker
    Chronotype.UNKNOWN -> R.string.chronotype_unknown
}

/**
 * One chronotype radio row. The whole row is the target, not just the
 * radio dot — see [GoalRow] for the same accessibility reasoning.
 * 48dp tall, screen-reader role is RadioButton, label and selected
 * state are one node.
 */
@Suppress("FunctionNaming")
@Composable
private fun ChronotypeRadioRow(
    chronotype: Chronotype,
    selected: Chronotype,
    onChange: (Chronotype) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected == chronotype,
                role = Role.RadioButton,
                onClick = { onChange(chronotype) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == chronotype, onClick = null)
        Text(
            text = stringResource(chronotype.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * v0.25.2-A (Task 10): the dialog for picking the daily letter's
 * time. Lives in its own sub-Composable so the parent
 * [SettingsScreen] does not grow past the detekt [LongMethod]
 * threshold — the dialog's [rememberTimePickerState] + [TimePicker]
 * + [AlertDialog] wiring is the part the parent would otherwise
 * inline.
 *
 * 24-hour because the spec is local-time-of-day, and a user who
 * has just chosen "08:00" in the toggle row should not have to
 * translate AM/PM in a second control. The confirm button hands
 * the picked [TimePickerState.hour] / [TimePickerState.minute]
 * straight back to the caller; nothing in here writes to the
 * store, so the dialog is safe to open, dismiss, and reopen
 * without ever touching [org.mindanchor.letters.LetterStore.setTime].
 */
@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LetterTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        text = {
            TimePicker(state = state)
        },
    )
}

/**
 * The six places settings actually falls into, replacing one scroll of
 * eighteen sections where the thing somebody came for was never on
 * screen when they arrived. A settings screen you have to scan before
 * you can use it is one you learn to avoid opening, so each destination
 * here is named for what it does rather than for the machinery behind
 * it.
 */
enum class SettingsGroup { QUIET, PAUSES, MEASURING, READING, PLAN, PHONE }

private fun SettingsGroup.titleRes(): Int = when (this) {
    SettingsGroup.QUIET -> R.string.settings_group_quiet
    SettingsGroup.PAUSES -> R.string.settings_group_pauses
    SettingsGroup.MEASURING -> R.string.settings_group_measuring
    SettingsGroup.READING -> R.string.settings_group_reading
    SettingsGroup.PLAN -> R.string.settings_group_plan
    SettingsGroup.PHONE -> R.string.settings_group_phone
}

/** v0.26.0 */
@Composable
private fun BpdProfileCheckbox(checked: Boolean, labelRes: Int, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox) { on -> onToggle(on) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * One row of the settings index: a name and, underneath it, a line
 * saying what it covers. No icon, no chevron, no count — this app puts
 * nothing on screen that turns a setting into something to check rather
 * than something to read, and the index is not exempt from that just
 * because it is new.
 */

/**
 * v0.35.1: "Run setup wizard again" affordance in Settings →
 * Sources. A row of label + description + a button. The button
 * launches the wizard activity and clears the per-step skipped
 * flags so the user lands on the first not-yet-completed step,
 * not on Welcome. The button is a one-shot action, not a
 * toggle, so a single tap fires `runCatching { startActivity }`
 * and the result is the user looking at the wizard.
 */
@Composable
private fun RerunSetupWizardRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column {
        Text(
            text = stringResource(R.string.setup_wizard_rerun_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.setup_wizard_rerun_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A11y: TextButton in SettingsScreen must carry
        // `role = Role.Button` so screen readers announce the
        // element as a button. The a11y finding test
        // (B6) enforces the count match — every TextButton call
        // site in this file must have a corresponding
        // `role = Role.Button` semantic.
        androidx.compose.material3.TextButton(
            modifier = Modifier.semantics { role = androidx.compose.ui.semantics.Role.Button },
            onClick = {
                scope.launch {
                    runCatching {
                        val intent = android.content.Intent(
                            context,
                            org.mindanchor.onboarding.SetupWizardActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                }
            },
        ) {
            Text(stringResource(R.string.setup_wizard_continue))
        }
    }
}

@Composable
private fun GroupRow(titleRes: Int, descriptionRes: Int, marked: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            // In the app's own colour, because on this screen that is
            // what "you can tap this" looks like.
            //
            // Found by looking at a screenshot rather than at the code.
            // Every other tappable thing here — the back link, the
            // default-launcher request — is a TextButton and therefore
            // primary-coloured, while these six sat in plain onSurface
            // like the section headings they replaced. Six words stacked
            // down the page in heading colour read as a table of
            // contents, not as six doors. That is the honest cost of
            // having no chevrons and no cards: the only cue left is
            // colour, so it has to carry the whole job.
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (marked) {
            Text(
                text = stringResource(R.string.goal_marker),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    /**
     * v0.25.2-A (Task 10): opens the letter inbox + reader on its
     * own surface. Wired from the home screen with the same
     * `cameFrom = LauncherSurface.Settings` discipline as
     * [onOpenReport] — the back button on the letter surface
     * returns here, not to the home, because the user came from
     * here. The button is also reachable from the new Daily letter
     * sub-section below; both paths converge on the same callback.
     */
    onOpenLetters: () -> Unit = {},
    /** v0.26.0 §3.3 */
    onOpenBeforeYouSend: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    // v0.22.1 — fix: when the user toggles a feature on that needs
    // POST_NOTIFICATIONS and then denies the permission, the in-app toggle
    // used to stay ON with no notifications actually delivered, no
    // explanation, and no way to know the reason. Capture a rollback
    // callback for the duration of the request and invoke it if the result
    // comes back false. The pre-fix launcher was a no-op `{}`, so the
    // toggle stayed optimistic even on deny.
    var pendingRollback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) pendingRollback?.invoke()
        pendingRollback = null
    }
    // v0.25.11: a second RequestPermission launcher for
    // the EMA toggle. The pre-fix shape was a single
    // launcher shared between the notification-batching
    // and the EMA toggles; a user who flipped batching
    // on, then EMA on before the first permission dialog
    // returned, would have their batching rollback
    // overwritten by the EMA rollback (and vice versa).
    // Each toggle now has its own launcher; the shared
    // `pendingRollback` slot at the root is still the
    // pattern (a second toggle's overwrite is final), but
    // the race is bounded to the slot, not the launcher
    // callback.
    val emaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) pendingRollback?.invoke()
        pendingRollback = null
    }

    val batchingEnabled by viewModel.batchingEnabled.collectAsStateWithLifecycle()
    val batchedApps by viewModel.batchedApps.collectAsStateWithLifecycle()

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

    // Read once and used by every section's goal marker as well as by
    // the index rows below; hoisted above the groups so it stays in
    // scope no matter which one is open.
    val goals by viewModel.goals.collectAsStateWithLifecycle()

    // null shows the index of six destinations; a value opens that
    // group's own screen in its place.
    var group by remember { mutableStateOf<SettingsGroup?>(null) }

    // v0.25.16 BUG-018: a [SaveableStateHolder] for the six
    // sub-screens so per-tab `rememberSaveable` state survives a
    // tab-switch within a single Settings open. The pre-fix
    // shape: each `if (group == SettingsGroup.X) { ... }` block
    // was composed conditionally, so the slot table for the
    // previous tab's content was torn down the moment the user
    // tapped a new group row — and any `rememberSaveable` state
    // that was inside (e.g. the "Pick a moment" date picker in
    // the OpenLoop postpone dialog, the half-typed email in
    // the COROS bridge form) was lost.
    //
    // The holder is created once for the lifetime of the
    // SettingsScreen Composable. The wrapper below uses the
    // tab's enum name as the key, so when the user navigates
    // PAUSES → READING → PAUSES, the PAUSES slot table is
    // restored from the holder. A future v0.26+ WP that
    // converts the `if (group == ...)` blocks into a single
    // `when (group)` can wrap each branch in
    // `saveableStateHolder.SaveableStateProvider(group.name)
    // { ... }` and inherit this state preservation.
    val saveableStateHolder = rememberSaveableStateHolder()

    // Back closes an open group first and only leaves the
    // screen on a second press. Without this, the global
    // back handler in [HomeScreen] would short-circuit to
    // the home surface the moment a group is open and the
    // user would lose the index. The visible "back" text
    // button below has the same predicate, so both paths
    // behave identically.
    BackHandler(enabled = true) {
        if (group != null) {
            group = null
        } else {
            onBack()
        }
    }

    // COROS bridge form state. Held at the screen level so
    // the input fields survive recomposition while the user
    // types; the [remember] keys are the fields' identity,
    // not the connection state, so a re-read after a login
    // failure does not wipe the half-typed email.
    var corosEmailDraft by remember { mutableStateOf("") }
    var corosPasswordDraft by remember { mutableStateOf("") }
    var corosRegionDraft by remember { mutableStateOf("eu") }
    var corosLoginInProgress by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            // v0.20.9: imePadding on the scroll container
            // so the soft keyboard does not cover the
            // COROS form fields, the check-in interval
            // inputs, the weekday pickers, the sunset
            // time pickers, or any other text field the
            // user is editing. safeDrawingPadding on the
            // outer wrapper should already cover the
            // IME insets, but explicit imePadding on the
            // scroll container is the documented
            // pattern and the one that survives a
            // future refactor that moves the
            // safeDrawingPadding to a smaller scope.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        // Back closes an open group first and only leaves the screen on a
        // second press, so the index is always the landing spot on the way
        // out rather than being skipped over.
        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { if (group != null) group = null else onBack() }) {
            Text(stringResource(R.string.action_back))
        }

        Text(
            text = stringResource(group?.titleRes() ?: R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        if (group == null) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
            if (!isDefault) {
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
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

            GroupRow(
                titleRes = R.string.settings_group_quiet,
                descriptionRes = R.string.settings_group_quiet_desc,
                marked = GoalMap.isChosen(SettingsSection.BATCHING, goals) ||
                    GoalMap.isChosen(SettingsSection.SUNSET, goals) ||
                    GoalMap.isChosen(SettingsSection.GRAYSCALE, goals) ||
                    GoalMap.isChosen(SettingsSection.OWNER, goals),
                onClick = { group = SettingsGroup.QUIET },
            )
            GroupRow(
                titleRes = R.string.settings_group_pauses,
                descriptionRes = R.string.settings_group_pauses_desc,
                marked = GoalMap.isChosen(SettingsSection.WATCH, goals),
                onClick = { group = SettingsGroup.PAUSES },
            )
            GroupRow(
                titleRes = R.string.settings_group_measuring,
                descriptionRes = R.string.settings_group_measuring_desc,
                marked = GoalMap.isChosen(SettingsSection.SLEEP, goals),
                onClick = { group = SettingsGroup.MEASURING },
            )
            GroupRow(
                titleRes = R.string.settings_group_reading,
                descriptionRes = R.string.settings_group_reading_desc,
                marked = GoalMap.isChosen(SettingsSection.HEALTH_CONNECT, goals),
                onClick = { group = SettingsGroup.READING },
            )
            GroupRow(
                titleRes = R.string.settings_group_plan,
                descriptionRes = R.string.settings_group_plan_desc,
                marked = false,
                onClick = { group = SettingsGroup.PLAN },
            )
            GroupRow(
                titleRes = R.string.settings_group_phone,
                descriptionRes = R.string.settings_group_phone_desc,
                marked = false,
                onClick = { group = SettingsGroup.PHONE },
            )
        }

        if (group == SettingsGroup.PHONE) {
            // --- What you said you wanted ---
            //
            // Onboarding asked, stored the answer, and nothing ever read it
            // again — which made the whole step decorative and left this a
            // long screen where everything looks equally relevant to everyone.
            // Nothing here switches anything on: onboarding promises in as
            // many words that it will not, and imposed structure is the thing
            // "Going Light" found fails.
            var editingGoals by remember { mutableStateOf(false) }
            run {
                Text(
                    text = stringResource(R.string.goals_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                )
                if (editingGoals) {
                    Goal.entries.forEach { goal ->
                        // One node, not two: the row carries the toggle
                        // semantics and the checkbox is only a picture of
                        // the state, so a screen reader hears the words
                        // and the checked state together. Same pattern on
                        // every stateful row in this app.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = goal in goals,
                                    role = Role.Checkbox,
                                ) {
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
                TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { editingGoals = !editingGoals }) {
                    Text(
                        stringResource(
                            if (editingGoals) R.string.goals_done else R.string.goals_change,
                        ),
                    )
                }
            }
        }

        if (group == SettingsGroup.PAUSES) {
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
            val stale by viewModel.stalePauses.collectAsStateWithLifecycle()
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
                        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { viewModel.keepPause(packageName) }) {
                            Text(stringResource(R.string.stale_keep))
                        }
                        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { viewModel.dropPause(packageName) }) {
                            Text(stringResource(R.string.stale_drop))
                        }
                    }
                }
            }
        }

        if (group == SettingsGroup.PAUSES) {
            // v0.26.0 §3.3
            Text(stringResource(R.string.bys_try_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
            Text(stringResource(R.string.bys_try_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onOpenBeforeYouSend) { Text(stringResource(R.string.bys_try_action)) }

            // v0.26.0 BPD profile
            Text(stringResource(R.string.bpd_profile_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
            Text(stringResource(R.string.bpd_profile_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bpdProfile by viewModel.bpdProfile.collectAsStateWithLifecycle()
            BpdProfileCheckbox(bpdProfile.longMessagesIRegret, R.string.bpd_profile_long_messages) { viewModel.setBpdProfile(bpdProfile.copy(longMessagesIRegret = it)) }
            BpdProfileCheckbox(bpdProfile.lateNightImpulses, R.string.bpd_profile_late_night) { viewModel.setBpdProfile(bpdProfile.copy(lateNightImpulses = it)) }
            BpdProfileCheckbox(bpdProfile.sometimesISplit, R.string.bpd_profile_split) { viewModel.setBpdProfile(bpdProfile.copy(sometimesISplit = it)) }
            BpdProfileCheckbox(bpdProfile.namedPersonToCall, R.string.bpd_profile_named_person) { viewModel.setBpdProfile(bpdProfile.copy(namedPersonToCall = it)) }
            BpdProfileCheckbox(bpdProfile.okAtNight, R.string.bpd_profile_ok_at_night) { viewModel.setBpdProfile(bpdProfile.copy(okAtNight = it)) }
        }

        // v0.25.16 BUG-018: each SettingsGroup's content is
        // wrapped in a [SaveableStateProvider] keyed on the
        // group's enum name. When the user navigates from
        // PAUSES to READING and back, the PAUSES slot table
        // is restored from the holder rather than recomposed
        // from scratch. The pre-v0.25.16 shape was a plain
        // `if (group == X) { ... }` that tore down the
        // slot table on every tab switch — the half-typed
        // "Small thing" draft and the "Pick a moment" date
        // picker in the OpenLoop postpone dialog were lost
        // the moment the user opened a different group.
        if (group == SettingsGroup.PAUSES) {
            saveableStateHolder.SaveableStateProvider("PAUSES") {
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
            val smallThings by viewModel.smallThings.collectAsStateWithLifecycle()
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
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { viewModel.removeSmallThing(thing) }) {
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
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        viewModel.addSmallThing(draft)
                        draft = ""
                    },
                ) {
                    Text(stringResource(R.string.small_things_add))
                }
            }

            // --- Self-compassion micro-moments ---
            //
            // Neff 2003 Self-Compassion Break: at the moment of
            // reaching for a doomscroll app, optionally surface
            // a phrase the user has previously written — their
            // own words, never the launcher's. Linardon 2020,
            // Behavior Therapy 51(4):646-658 (DOI 10.1016/j.beth.2019.10.002)
            // — meta-analysis of 27 RCTs of smartphone apps for
            // acceptance / mindfulness / self-compassion. Reports
            // g = −0.32 (95% CI −0.48 to −0.16) for distress and
            // g = 0.31 (95% CI 0.07-0.56) for self-compassion.
            // Same "only their own words" fence as small things;
            // same shape; same cap (six phrases is enough for
            // a rotation without any one becoming wallpaper).
            Text(
                text = stringResource(R.string.compassion_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.compassion_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val compassionMoments by viewModel.compassionMoments.collectAsStateWithLifecycle()
            compassionMoments.forEach { moment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = moment.phrase,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { viewModel.removeCompassionMoment(moment.phrase) }) {
                        Text(stringResource(R.string.small_things_remove))
                    }
                }
            }
            if (compassionMoments.size < org.mindanchor.friction.CompassionList.MAX) {
                var draft by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.compassion_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        viewModel.addCompassionMoment(draft)
                        draft = ""
                    },
                ) {
                    Text(stringResource(R.string.small_things_add))
                }
            }
            }
        }

        if (group == SettingsGroup.QUIET) {
            // --- Notification batching (F1) ---
            SectionHeading(R.string.batching_section, SettingsSection.BATCHING, goals)
            Text(
                text = stringResource(R.string.batching_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!hasNotificationAccess) {
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = batchingEnabled, role = Role.Switch) { enabled ->
                            if (enabled) {
                                // v0.22.1: arm the rollback so a denied
                                // permission request leaves the toggle OFF
                                // instead of stuck ON with no notifications.
                                pendingRollback = { viewModel.setBatchingEnabled(false) }
                                permissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS,
                                )
                            } else {
                                pendingRollback = null
                            }
                            viewModel.setBatchingEnabled(enabled)
                        }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.batching_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = batchingEnabled, onCheckedChange = null)
                }

                if (batchingEnabled) {
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = viewModel::releaseNow) {
                        Text(stringResource(R.string.digest_release_now))
                    }

                    // The three release times, editable. The default 08:00 /
                    // 12:30 / 18:00 is the studied dosage, not a claim about
                    // anybody's day — a night shift makes a lunchtime batch
                    // meaningless, and the whole point of batching is that
                    // interruptions land when a person can absorb them.
                    val releaseTimes by viewModel.releaseTimes.collectAsStateWithLifecycle()
                    Text(
                        text = stringResource(R.string.batching_times_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    releaseTimes.forEachIndexed { slot, time ->
                        // forEachIndexed is inline, so stringResource here is
                        // still a call from the composable body.
                        // v0.20.9: the row now shows the
                        // slot label *and* the time on the
                        // same line — see timeNudgerRow KDoc.
                        timeNudgerRow(
                            // v0.25.1: the time moved out of
                            // the label slot — `batching_time_slot`
                            // is just "Arrives at" now, and the
                            // time lives in the value slot. The
                            // pre-fix call passed `time.format` to
                            // both, which the `time_nudger_row`
                            // format string rendered as
                            // "Arrives at 08:00 08:00".
                            label = stringResource(R.string.batching_time_slot),
                            value = time.format(HOUR_MINUTE),
                            onEarlier = { viewModel.nudgeReleaseTime(slot, -BatchSchedule.NUDGE_MINUTES) },
                            onLater = { viewModel.nudgeReleaseTime(slot, BatchSchedule.NUDGE_MINUTES) },
                        )
                    }

                    // Said here, next to the times themselves, because
                    // this is where somebody forms the belief that 18:00
                    // means 18:00. From Android 14 an app targeting 34+
                    // is not granted exact alarms by default, so every
                    // scheduler in this app falls back to a window of up
                    // to an hour — and nothing said so.
                    if (!Alarms.canBeExact(context)) {
                        Text(
                            text = stringResource(R.string.exact_alarms_explainer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            TextButton(
        modifier = Modifier
            .semantics { role = Role.Button }
            .padding(vertical = 4.dp),
        onClick = {
                                    runCatching {
                                        activityLauncher.launch(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.fromParts("package", context.packageName, null),
                                            ),
                                        )
                                    }
                                },
                            ) {
                                // Trailing chevron so the affordance
                                // reads as a button, not a label.
                                // The launcher has no other right-arrow
                                // icons; this is the one place the user
                                // is being asked to leave the app, and
                                // the cue is worth the one glyph.
                                Text(
                                    text = stringResource(R.string.exact_alarms_grant) + "  →",
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.batching_choose_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    allApps.forEach { app ->
                        val packageName = app.component.substringBefore('/')
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = packageName in batchedApps,
                                    role = Role.Switch,
                                ) { viewModel.setAppBatched(packageName, it) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = packageName in batchedApps, onCheckedChange = null)
                        }
                    }
                }
            }
        }

        if (group == SettingsGroup.PHONE) {
            // --- Home screen appearance ---
            val natureScene by viewModel.natureScene.collectAsStateWithLifecycle()
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
                // The row is the one target and the radio is only a
                // picture of the state. When the radio kept its own
                // onClick there were two tap targets per line and the
                // inner one had no words — a screen reader landed on an
                // unnamed radio button between every named row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = natureScene == scene,
                            role = Role.RadioButton,
                        ) { viewModel.setNatureScene(scene) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = natureScene == scene, onClick = null)
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (group == SettingsGroup.PAUSES) {
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
        modifier = Modifier.semantics { role = Role.Button },
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
        }

        if (group == SettingsGroup.QUIET) {
            // --- Sunset mode (F4) ---
            val sunsetEnabled by viewModel.sunsetEnabled.collectAsStateWithLifecycle()
            SectionHeading(R.string.sunset_section, SettingsSection.SUNSET, goals)
            Text(
                text = stringResource(R.string.sunset_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Chronotype picker (Roenneberg 2007, Wittmann 2006, Åkerstedt
            // 2003, Kecklund 2016). The first answer came from onboarding;
            // this lets the user change it without re-running onboarding,
            // and changes here overwrite the default window the same way
            // — only if the user has not already picked their own.
            val chronotype by viewModel.chronotype.collectAsStateWithLifecycle()
            Text(
                text = stringResource(R.string.chronotype_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.chronotype_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChronotypeRadioRow(Chronotype.MORNING_LARK, chronotype) {
                viewModel.setChronotype(it)
            }
            ChronotypeRadioRow(Chronotype.NEUTRAL, chronotype) {
                viewModel.setChronotype(it)
            }
            ChronotypeRadioRow(Chronotype.NIGHT_OWL, chronotype) {
                viewModel.setChronotype(it)
            }
            ChronotypeRadioRow(Chronotype.SHIFT_WORKER, chronotype) {
                viewModel.setChronotype(it)
            }
            ChronotypeRadioRow(Chronotype.UNKNOWN, chronotype) {
                viewModel.setChronotype(it)
            }

            // The window used to be hardcoded to 22:00 → 07:00. That is
            // somebody else's bedtime: wrong for shift workers, wrong for
            // anyone on call, wrong for night staff — and a wind-down that
            // begins three hours after you went to bed is not a wind-down.
            val sunsetStart by viewModel.sunsetStart.collectAsStateWithLifecycle()
            val sunsetEnd by viewModel.sunsetEnd.collectAsStateWithLifecycle()
            Text(
                text = stringResource(
                    R.string.sunset_window,
                    sunsetStart.format(HOUR_MINUTE),
                    sunsetEnd.format(HOUR_MINUTE),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            // v0.20.9: each row now shows the time on
            // the same line as the label, so the user
            // does not have to read "Quiet hours run
            // 22:00 to 07:00." and hold the start in
            // their head while reading the end.
            timeNudgerRow(
                label = stringResource(R.string.sunset_starts),
                value = sunsetStart.format(HOUR_MINUTE),
                onEarlier = { viewModel.nudgeSunset(-30, 0) },
                onLater = { viewModel.nudgeSunset(30, 0) },
            )
            timeNudgerRow(
                label = stringResource(R.string.sunset_ends),
                value = sunsetEnd.format(HOUR_MINUTE),
                onEarlier = { viewModel.nudgeSunset(0, -30) },
                onLater = { viewModel.nudgeSunset(0, 30) },
            )
            if (!hasDndAccess) {
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = sunsetEnabled, role = Role.Switch) {
                            viewModel.setSunsetEnabled(it)
                        }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sunset_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = sunsetEnabled, onCheckedChange = null)
                }
            }
        }

        if (group == SettingsGroup.MEASURING) {
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
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onOpenPpg) {
                Text(stringResource(R.string.ppg_start))
            }
        }

        if (group == SettingsGroup.MEASURING) {
            // --- Sleep rhythm (F5) ---
            val sleepSummary by viewModel.sleepSummary.collectAsStateWithLifecycle()
            SectionHeading(R.string.sleep_section, SettingsSection.SLEEP, goals)
            if (!hasUsageAccess) {
                Text(
                    text = stringResource(R.string.sleep_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
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
                    val mirrorOn by viewModel.sleepMirror.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = mirrorOn, role = Role.Switch) {
                                viewModel.setSleepMirror(it)
                            }
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.mirror_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = mirrorOn, onCheckedChange = null)
                    }
                    Text(
                        text = stringResource(R.string.mirror_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val laterNights by viewModel.nightsLaterThanUsual.collectAsStateWithLifecycle()
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
                    // Suggested wind-down, opt-in. Built from the user's
                    // own sleep onsets (Windred et al. 2024 — regularity
                    // over duration). The button is the entire "apply"
                    // surface: a tap writes the window, a lack of a tap
                    // does nothing. The wording above avoids the word
                    // "should" — the suggestion is the launcher's best
                    // read of the data, not a prescription.
                    val suggestion by viewModel.sleepSuggestion.collectAsStateWithLifecycle()
                    suggestion?.let { s ->
                        Text(
                            text = stringResource(R.string.sleep_suggestion_heading),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.sleep_suggestion_line,
                                s.nightsUsed,
                                s.medianOnset.format(HOUR_MINUTE),
                                s.startTime.format(HOUR_MINUTE),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { viewModel.applySleepSuggestion(s) }) {
                            Text(stringResource(R.string.sleep_suggestion_apply))
                        }
                    }
                }
            }
        }

        if (group == SettingsGroup.PHONE) {
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
                        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { onUnhide(app) }) {
                            Text(stringResource(R.string.action_unhide))
                        }
                    }
                }
            }
        }

        if (group == SettingsGroup.READING) {
            // --- Daily letter (v0.25.2-A) ---
            //
            // The headline entry on the Reading surface. The
            // toggle is always editable, on purpose: a person who
            // has not yet imported a model needs to be able to
            // *say* they want a letter without the row being
            // dead, and the daily alarm is held by the
            // [org.mindanchor.letters.LetterScheduler] which
            // already does the right thing when the model is
            // missing (a quiet "nothing today" — see
            // [org.mindanchor.letters.LetterScheduler.onFire]).
            // The "Generate now" button is the one row that
            // gates on `modelFits`, because pushing a button
            // that visibly does nothing is its own small
            // dishonesty. The inbox count gates on
            // `unreadCount > 0` for the same reason — a button
            // that says "Open inbox (0)" reads as a stat
            // rather than an affordance.
            SectionHeading(R.string.letters_section, null, goals)
            Text(
                text = stringResource(R.string.letters_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()
            val lettersEnabled by viewModel.lettersEnabled.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = lettersEnabled, role = Role.Switch) {
                        viewModel.setLettersEnabled(it)
                    }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.letters_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = lettersEnabled, onCheckedChange = null)
            }
            val lettersTime by viewModel.lettersTime.collectAsStateWithLifecycle()
            var showLetterTimePicker by remember { mutableStateOf(false) }
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        enabled = lettersEnabled,
                onClick = { showLetterTimePicker = true },
            ) {
                Text(
                    stringResource(
                        R.string.letters_time,
                        lettersTime.first,
                        lettersTime.second,
                    ),
                )
            }
            if (showLetterTimePicker) {
                LetterTimePickerDialog(
                    initialHour = lettersTime.first,
                    initialMinute = lettersTime.second,
                    onDismiss = { showLetterTimePicker = false },
                    onConfirm = { hour, minute ->
                        viewModel.setLettersTime(hour, minute)
                        showLetterTimePicker = false
                    },
                )
            }
            val letterRunning by viewModel.letterRunning.collectAsStateWithLifecycle()
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        enabled = !letterRunning && lettersEnabled && modelFits,
                onClick = viewModel::runLetterNow,
            ) {
                Text(
                    stringResource(
                        if (letterRunning) R.string.letters_running_now
                        else R.string.letters_run_now,
                    ),
                )
            }
            val unreadCount by viewModel.unreadLetterCount.collectAsStateWithLifecycle()
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        enabled = unreadCount > 0,
                onClick = onOpenLetters,
            ) {
                Text(stringResource(R.string.letters_open_inbox, unreadCount))
            }
        }

        if (group == SettingsGroup.READING) {
            // --- Reading size (v0.25.2-B) ---
            //
            // The A- / A / A+ control. Mirrors the segmented control
            // in the reader's top row (Task 18) so a person who has
            // not yet opened a letter can still pick a size. The
            // A- / A / A+ labels are locale-safe and RTL-safe; the
            // control is the same on every device, every locale.
            SectionHeading(R.string.reading_size_section, null, goals)
            Text(
                text = stringResource(R.string.reading_size_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val letterSize by viewModel.letterSize.collectAsStateWithLifecycle()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                listOf(ReadingSize.SMALL, ReadingSize.MEDIUM, ReadingSize.LARGE)
                    .forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = letterSize == s,
                            onClick = { viewModel.setLetterSize(s) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        ) {
                            Text(
                                text = when (s) {
                                    ReadingSize.SMALL  -> "A-"
                                    ReadingSize.MEDIUM -> "A"
                                    ReadingSize.LARGE  -> "A+"
                                    else -> "A"
                                },
                            )
                        }
                    }
            }
        }

        if (group == SettingsGroup.READING) {
            // --- Last night's look (nightly report) ---
            //
            // The only part of this screen that ever runs on its own: a
            // background worker, gated on the phone charging and idle, that
            // compares the day against this person's own history and pulls
            // up what the research says the thing measured actually is. See
            // ReportComposer for why it never joins those two together, and
            // ReportScheduler for why an ordinary, quiet night is success too.
            SectionHeading(R.string.report_section, null, goals)
            Text(
                text = stringResource(R.string.report_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val reportEnabled by viewModel.reportEnabled.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = reportEnabled, role = Role.Switch) {
                        viewModel.setReportEnabled(it)
                    }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.report_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = reportEnabled, onCheckedChange = null)
            }
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onOpenReport) {
                Text(stringResource(R.string.report_open))
            }

            // When it last actually ran, and a way to run it right now.
            // The nightly alarm is unattended and a silently stopped one
            // is indistinguishable from a run of quiet nights; this line
            // is the difference, and the button proves the whole pipeline
            // on this phone in its first minute rather than trusting a
            // 3am alarm to demonstrate it eventually.
            val generatedDay by viewModel.reportGeneratedDay.collectAsStateWithLifecycle()
            Text(
                text = stringResource(
                    R.string.report_last_built,
                    generatedDay ?: stringResource(R.string.report_never_built),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val reportRunning by viewModel.reportRunning.collectAsStateWithLifecycle()
            TextButton(modifier = Modifier.semantics { role = Role.Button }, enabled = !reportRunning, onClick = viewModel::runReportNow) {
                Text(
                    stringResource(
                        if (reportRunning) R.string.report_running_now else R.string.report_run_now,
                    ),
                )
            }
        }

        if (group == SettingsGroup.READING) {
            // --- Research on file (the corpus every report draws on) ---
            //
            // Twenty-six bundled passages is a seed, not a library, and the
            // retrieval behind every report gets better the more there is to
            // retrieve from. Everything else here can be improved by shipping
            // an update; the research should not have to wait on one, nor be
            // limited to what one person thought to include. See CorpusImport
            // for why an import merges rather than replaces.
            SectionHeading(R.string.corpus_section, null, goals)
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
            val corpusSize by viewModel.corpusSize.collectAsStateWithLifecycle()
            val corpusImported by viewModel.corpusImported.collectAsStateWithLifecycle()
            val lastImport by viewModel.lastImport.collectAsStateWithLifecycle()
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
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { corpusPicker.launch(arrayOf("*/*")) }) {
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
                TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = viewModel::clearCorpus) {
                    Text(stringResource(R.string.corpus_clear))
                }
            }
        }

        if (group == SettingsGroup.READING) {
            // --- Model (the small model a future writing engine would run) ---
            //
            // No inference engine is built into this app yet — see
            // org.mindanchor.narrate.NoEngineNarrator. Importing a model here
            // does not yet make any writing happen; it records the file and,
            // exactly like ModelSlot was built to, reports honestly whether
            // this phone has enough memory to run it once an engine exists.
            SectionHeading(R.string.model_section, null, goals)
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
            val modelPresent by viewModel.modelPresent.collectAsStateWithLifecycle()
            val modelFit by viewModel.modelFit.collectAsStateWithLifecycle()
            val modelImportFailed by viewModel.modelImportFailed.collectAsStateWithLifecycle()
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
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { modelPicker.launch(arrayOf("*/*")) }) {
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
                TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = viewModel::clearModel) {
                    Text(stringResource(R.string.model_clear))
                }
            }
            // v0.23.0: one-tap Phi-4 mini download.
            // The download runs through the system
            // DownloadManager; when it finishes, the
            // launcher listens for
            // ACTION_DOWNLOAD_COMPLETE and prompts the
            // user with a Yes-then-import.
            Phi4ModelDownloadSection(viewModel = viewModel)

            // v0.25.4: Google Drive backup (replaces
            // v0.23.0 WebDAV). The section lives in
            // the Reading group because the "what
            // you wrote" surface is the natural
            // home for "where the writes go" — the
            // letters feature sits here, the
            // nightly report reuses the same
            // ReaderPrefs, and the user looking
            // for the "I lost my phone, where's
            // my data?" affordance is reading the
            // same screen.
            GoogleDriveBackupSettingsSection(viewModel = viewModel)
        }

        if (group == SettingsGroup.MEASURING) {
            // --- Check-ins (EMA) ---
            //
            // The other half of "Labels" alongside the EMA above: a handful
            // of taps a day rather than a fortnightly instrument. The count
            // is stated plainly and never as a target — a skipped prompt is
            // normal, not a shortfall, so nothing here is styled as a streak.
            SectionHeading(R.string.ema_section, null, goals)
            Text(
                text = stringResource(R.string.ema_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val emaEnabled by viewModel.emaEnabled.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = emaEnabled, role = Role.Switch) { enabled ->
                        if (enabled) {
                            // v0.22.1: same rollback as batching — without
                            // POST_NOTIFICATIONS granted, EMA has no way to
                            // actually prompt the user, so the toggle should
                            // not stay ON after a deny.
                            pendingRollback = { viewModel.setEmaEnabled(false) }
                            emaPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            pendingRollback = null
                        }
                        viewModel.setEmaEnabled(enabled)
                    }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ema_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = emaEnabled, onCheckedChange = null)
            }
            val emaCount by viewModel.emaCount.collectAsStateWithLifecycle()
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
        }

        if (group == SettingsGroup.MEASURING) {
            // --- Wellness signals (N-of-1, from Health Connect) ---
            //
            // The detailed view of the surface the home card
            // summarises. Each signal is shown with today's
            // value, the person's own median, the typical
            // spread (MAD), the raw robust z-score, and the
            // band. The wording is direction-only, never
            // "good" or "bad" — see [WellnessDirection] and
            // docs/research/08 for why.
            //
            // The whole section is hidden when the launcher
            // has not yet read any signal today: a settings
            // panel that started with a confusing empty list
            // would be a small UX failure, and the Wearable
            // section above already says "no data is being
            // read yet" when that is the case.
            SectionHeading(R.string.wellness_settings_title, null, goals)
            Text(
                text = stringResource(R.string.wellness_settings_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val wellnessReadings by viewModel.wellnessReadings.collectAsStateWithLifecycle()
            LaunchedEffect(permissionEpoch) { viewModel.refreshWellness() }
            val wellness = wellnessReadings
            if (wellness == null) {
                // Still loading on first composition. The
                // loading message is a different string from
                // the no-data one below because the two are
                // different states — "Reading from your watch…"
                // is honest when the launcher is mid-read;
                // "no data has been read yet" is honest when
                // the read has run and returned empty. A
                // skeleton would be a small lie here — the
                // load is sub-second and a quiet placeholder
                // is the honest reading.
                Text(
                    text = stringResource(R.string.wellness_settings_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else if (wellness.none { it.baseline.isReportable }) {
                // The history is on file but the
                // baseline is not yet reportable on any
                // signal. The home card hides itself
                // under the same condition; the
                // settings panel shows the reason.
                Text(
                    text = stringResource(R.string.wellness_settings_baseline_building),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                wellness.forEach { reading ->
                    WellnessSignalRow(reading = reading)
                }
                Text(
                    text = stringResource(R.string.wellness_settings_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        if (group == SettingsGroup.MEASURING) {
            // --- Wearable (Health Connect permission flow) ---
            //
            // v0.20.4: the launcher used to read Health Connect
            // without ever asking the user for permission. The reads
            // returned SecurityException, the system dialog never
            // appeared, and the data was silently empty. This section
            // is the missing on-ramp: one button, the system
            // Health Connect dialog, and a live count of what is
            // actually shared. The privacy promise — "the data never
            // leaves this phone" — is stated plainly in the section,
            // because the rule for this app is to say what it does
            // out loud, not to ask the user to take it on trust.
            SectionHeading(R.string.health_connect_title, SettingsSection.HEALTH_CONNECT, goals)
            Text(
                text = stringResource(R.string.health_connect_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Recompute the status every time the user navigates
            // back to this section — the activity may have been
            // backgrounded across a permission grant or revoke. The
            // outer [permissionEpoch] is the same one that the rest
            // of this composable uses to re-read notification + usage
            // grants; piggy-backing on it means the Health Connect
            // status refreshes for the same reasons and at the same
            // time, no extra plumbing required.
            val hcStatus by viewModel.healthConnectStatus.collectAsStateWithLifecycle()
            LaunchedEffect(permissionEpoch) { viewModel.refreshHealthConnectStatus() }

            val statusText = when (val s = hcStatus) {
                SettingsViewModel.HealthConnectStatus.Unknown ->
                    stringResource(R.string.health_connect_status_loading)
                SettingsViewModel.HealthConnectStatus.Unavailable ->
                    stringResource(R.string.health_connect_status_unavailable)
                is SettingsViewModel.HealthConnectStatus.Available -> when {
                    s.granted == 0 ->
                        stringResource(R.string.health_connect_status_not_granted)
                    s.granted == s.total ->
                        stringResource(R.string.health_connect_status_full, s.total)
                    else ->
                        stringResource(R.string.health_connect_status_partial, s.granted, s.total)
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )

            // The request-permissions launcher. Created here so the
            // contract lives as long as the measuring section is on
            // screen.
            //
            // v0.23.0 — silent-failure fix: the contract factory was
            // being called inline (line 1796 of the previous
            // revision), which means every recomposition produced a
            // new ActivityResultContract instance. Compose's
            // rememberLauncherForActivityResult keys on the contract
            // *instance*, not the class, so a new contract every
            // recomposition forced the launcher to re-register with
            // the activity's ActivityResultRegistry. On a phone
            // with a slightly slower result pipeline — particularly
            // the Tamil Nadu Police test device behind a
            // corporate-managed ActivityManager that buffers
            // dispatches — the onClick lambda drifted from the
            // registered launcher between recomposition and tap.
            // The click went to a launcher that was no longer in the
            // registry; the system returned
            // `ActivityResultCallback not registered`; the click
            // was silently swallowed. No exception, no log line, no
            // dialog. Same shape as the v0.22.1 EMA / Batching
            // silent-toggle bug.
            //
            // The fix is to cache the contract instance with
            // `remember`. The key is then stable across
            // recompositions, the launcher is stable, the click is
            // stable. Renamed to [healthConnectPermissionLauncher]
            // to avoid shadowing the outer [permissionLauncher]
            // declared for the notification-batching
            // POST_NOTIFICATIONS request (line ~345) — the two
            // contracts are different shapes, but a same-named
            // local would silently bind to the wrong one the next
            // time a maintainer added a button in this block.
            val healthConnectPermissionContract = remember {
                HealthConnectSource.requestPermissionsContract()
            }
            // v0.25.3-WP-B: hcLaunchError surfaces a launch-dispatch
            // failure (ActivityNotFoundException, SecurityException,
            // send-cancel) instead of swallowing it. Result handler
            // clears it on a real callback.
            var hcLaunchError by remember { mutableStateOf<String?>(null) }
            val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
                contract = healthConnectPermissionContract,
            ) { granted ->
                Log.w("MindAnchor/HealthConnect", "permission result: " + granted.size + " granted")
                hcLaunchError = null
                viewModel.refreshHealthConnectStatus()
            }

            // Show the connect/change button only when the launcher
            // has actually probed the source AND Health Connect is
            // present. The previous guard was `!is Unavailable`,
            // which also rendered the button for the `Unknown`
            // initial state — a window of a few hundred milliseconds
            // between the composable first appearing and the
            // `LaunchedEffect` coroutine finishing its
            // [HealthConnectSource.isAvailable] check. A tap in that
            // window invoked [healthConnectPermissionLauncher.launch]
            // before the source had been probed; on a phone without
            // Health Connect the launch dispatched an Intent for a
            // package that didn't exist and the dialog never opened.
            // The user saw the button, tapped it, and nothing
            // happened — the silent-failure shape this whole
            // permission flow is supposed to avoid.
            //
            // The local [s] capture is required because [hcStatus] is a
            // delegated property and Kotlin cannot smart-cast across
            // the [is] check; capturing it into a non-delegated [val]
            // restores the smart-cast.
            val s = hcStatus
            if (s is SettingsViewModel.HealthConnectStatus.Available) {
                val buttonLabelRes =
                    if (s.granted == 0) R.string.health_connect_button_connect
                    else R.string.health_connect_button_change
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        // v0.25.3-WP-B: runCatching surfaces a launch
                        // failure instead of swallowing it. The Log.w
                        // gives a known handle for adb logcat.
                        Log.w("MindAnchor/HealthConnect", "launch requested")
                        runCatching {
                            healthConnectPermissionLauncher.launch(
                                HealthConnectSource.effectivePermissions(context),
                            )
                        }.onFailure { t ->
                            Log.e("MindAnchor/HealthConnect", "launch failed: " + t.javaClass.simpleName, t)
                            hcLaunchError = t.javaClass.simpleName
                        }
                    },
                ) {
                    Text(stringResource(buttonLabelRes))
                }
                val launchError = hcLaunchError
                if (launchError != null) {
                    Text(
                        text = stringResource(R.string.health_connect_launch_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // "What this app reads" — a list that maps each granted
            // permission to a one-line description in plain English.
            // Iterated from [HealthConnectSource.permissionLabelsInOrder]
            // — keyed on the same set the system dialog was opened
            // against — so a future permission added to the source
            // surfaces here without a parallel edit in this file.
            // Each label-key falls back to the raw permission name
            // when no string resource is configured for it, which
            // makes a misnamed permission crash-resistant instead of
            // silently absent.
            val labelKeyToRes: Map<String, Int> = mapOf(
                "heart_rate" to R.string.health_connect_reads_heart_rate,
                "resting_heart_rate" to R.string.health_connect_reads_resting_heart_rate,
                "heart_rate_variability" to R.string.health_connect_reads_heart_rate_variability,
                "sleep" to R.string.health_connect_reads_sleep,
                "steps" to R.string.health_connect_reads_steps,
                "exercise" to R.string.health_connect_reads_exercise,
                "calories" to R.string.health_connect_reads_calories,
                "mindfulness" to R.string.health_connect_reads_mindfulness,
            )
            Text(
                text = stringResource(R.string.health_connect_what_reads_header),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            HealthConnectSource.permissionLabelsInOrder().forEach { (labelKey, _) ->
                val text = labelKeyToRes[labelKey]?.let { stringResource(it) } ?: labelKey
                Text(
                    text = "•  " + text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // "What this app does NOT do" — three explicit denials.
            // The same N-of-1 rule that runs through the rest of the
            // launcher: state the limit, do not ask the user to
            // infer it.
            Text(
                text = stringResource(R.string.health_connect_what_we_dont_header),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            for (res in listOf(
                R.string.health_connect_does_not_write,
                R.string.health_connect_does_not_send,
                R.string.health_connect_does_not_diagnose,
            )) {
                Text(
                    text = "•  " + stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (group == SettingsGroup.MEASURING) {
            // --- Wearable bridge (COROS, opt-in side-channel) ---
            //
            // v0.20.7: the third tier of the wearable story.
            // Health Connect is the default; the camera PPG is
            // for HRV when no watch is present; the COROS
            // bridge is the opt-in escape hatch for the
            // signals a particular watch does not release to
            // Health Connect (HRV on a COROS Pacer 3, for
            // example). The user has to come here and decide.
            //
            // The form is deliberately simple — three text
            // fields, one region selector, two buttons — and
            // the privacy trade-off is stated plainly above
            // the form. A trust-me button on a screen that
            // asks for a third-party password would be the
            // wrong shape; the user has to see what the
            // bridge does before they can decide.
            SectionHeading(R.string.coros_bridge_section, null, goals)
            Text(
                text = stringResource(R.string.coros_bridge_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val corosState by viewModel.corosState.collectAsStateWithLifecycle()
            val corosLastSync by viewModel.corosLastSyncEpochMs.collectAsStateWithLifecycle()
            val corosSyncRunning by viewModel.corosSyncRunning.collectAsStateWithLifecycle()
            val corosSyncError by viewModel.corosSyncError.collectAsStateWithLifecycle()
            LaunchedEffect(permissionEpoch) { viewModel.refreshCorosState() }

            when (val s = corosState) {
                is CorosConnectionState.Connected -> {
                    Text(
                        text = stringResource(
                            R.string.coros_connected_label,
                            s.email,
                            s.region,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    val lastSyncText = corosLastSync?.let { ms ->
                        val instant = java.time.Instant.ofEpochMilli(ms)
                        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                        val formatter = java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm")
                        stringResource(
                            R.string.coros_last_sync_label,
                            zoned.format(formatter),
                        )
                    } ?: stringResource(R.string.coros_last_sync_never)
                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    corosSyncError?.let { msg ->
                        Text(
                            text = stringResource(R.string.coros_sync_failed, msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        enabled = !corosSyncRunning,
                            onClick = viewModel::corosSyncNow,
                        ) {
                            Text(
                                stringResource(
                                    if (corosSyncRunning) R.string.coros_sync_running
                                    else R.string.coros_sync_now_button,
                                ),
                            )
                        }
                        TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = viewModel::disconnectCoros) {
                            Text(stringResource(R.string.coros_disconnect_button))
                        }
                    }
                }
                CorosConnectionState.AwaitingConsent -> {
                    Text(
                        text = stringResource(R.string.coros_login_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is CorosConnectionState.Failed -> {
                    Text(
                        text = stringResource(R.string.coros_login_failed, s.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                CorosConnectionState.NotConnected -> {
                    // The form is rendered for both
                    // NotConnected and Failed — the user
                    // gets to retry without re-entering the
                    // email if the first attempt failed.
                }
            }

            // The form. Always visible when the bridge is
            // not currently Connected — the email / password
            // fields are the affordance the user needs in
            // both first-time setup and the post-failure
            // retry path.
            val showForm = corosState !is CorosConnectionState.Connected
            if (showForm) {
                OutlinedTextField(
                    value = corosEmailDraft,
                    onValueChange = { corosEmailDraft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.coros_email_hint)) },
                    // v0.20.9: scroll the form up so the
                    // email field is visible above the
                    // keyboard. The COROS form sits below
                    // the privacy explainer and the
                    // connected-state block, so on a
                    // small-screen device the email
                    // field is well below the fold when
                    // the keyboard comes up.
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = corosPasswordDraft,
                    onValueChange = { corosPasswordDraft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.coros_password_hint)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    // v0.20.9: same scroll-into-view as
                    // the email field.
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.coros_region_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                listOf(
                    "us" to R.string.coros_region_us,
                    "eu" to R.string.coros_region_eu,
                    "asia" to R.string.coros_region_asia,
                ).forEach { (regionCode, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = corosRegionDraft == regionCode,
                                role = Role.RadioButton,
                            ) { corosRegionDraft = regionCode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = corosRegionDraft == regionCode,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        enabled = !corosLoginInProgress &&
                        corosEmailDraft.isNotBlank() && corosPasswordDraft.isNotBlank(),
                    onClick = {
                        corosLoginInProgress = true
                        coroutineScope.launch {
                            runCatching {
                                viewModel.connectCoros(
                                    email = corosEmailDraft.trim(),
                                    password = corosPasswordDraft,
                                    region = corosRegionDraft,
                                )
                            }
                            // The viewmodel's [connectCoros]
                            // re-throws on failure; success
                            // moves the state to Connected and
                            // the form above vanishes. Clear
                            // the password draft in either
                            // case so it does not sit on
                            // screen if the user closes the
                            // section and returns.
                            corosPasswordDraft = ""
                            corosLoginInProgress = false
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (corosLoginInProgress) R.string.coros_login_in_progress
                            else R.string.coros_connect_button,
                        ),
                    )
                }
            }

            // The "what this bridge does" block. Same shape
            // as the Health Connect "what this app does NOT
            // do" block above: a list of plain English
            // sentences, the user can read the trade-off
            // without scrolling back up to the explainer.
            Text(
                text = stringResource(R.string.coros_what_this_does_header),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            for (res in listOf(
                R.string.coros_does_login,
                R.string.coros_does_sync,
                R.string.coros_does_merge,
                R.string.coros_does_not_store,
                R.string.coros_does_not_logout,
            )) {
                Text(
                    text = "•  " + stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (group == SettingsGroup.MEASURING) {
            // --- What is arriving (coverage and the wearable probe) ---
            //
            // Facts, not assumptions. The signal list was designed around
            // what a wearable could deliver; what one actually delivers
            // is only learnable by looking, and a signal silently absent
            // for weeks is the failure this section exists to make
            // impossible to miss.
            SectionHeading(R.string.arriving_section, null, goals)
            Text(
                text = stringResource(R.string.arriving_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val coverage by viewModel.coverage.collectAsStateWithLifecycle()
            val entries = coverage
            if (entries == null) {
                Text(
                    text = stringResource(R.string.arriving_not_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                entries.forEach { entry ->
                    Text(
                        text = stringResource(
                            R.string.arriving_line,
                            stringResource(signalLabelRes(entry.signal)),
                            entry.daysOnFile,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = if (entry.lastDay == null) {
                            stringResource(R.string.arriving_never)
                        } else {
                            stringResource(
                                R.string.arriving_last,
                                entry.lastDay,
                                stringResource(sourceLabelRes(entry.lastSource)),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val probing by viewModel.probing.collectAsStateWithLifecycle()
            TextButton(modifier = Modifier.semantics { role = Role.Button }, enabled = !probing, onClick = viewModel::probeYesterday) {
                Text(
                    stringResource(
                        if (probing) R.string.probe_checking else R.string.probe_button,
                    ),
                )
            }
            val probe by viewModel.probe.collectAsStateWithLifecycle()
            probe?.let { vitals ->
                Text(
                    text = stringResource(R.string.probe_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Spelled out field by field rather than looped, because
                // each one formats differently and a bedtime shown as
                // "1410" would defeat the purpose of a screen that exists
                // to be read literally.
                ProbeLine(R.string.signal_hrv, vitals.hrvRmssd?.let { "%.0f ms".format(it) })
                ProbeLine(
                    R.string.signal_resting_hr,
                    vitals.restingHeartRate?.let { "%.0f bpm".format(it) },
                )
                ProbeLine(
                    R.string.signal_sleep_minutes,
                    vitals.sleepMinutes?.let { "$it min" },
                )
                ProbeLine(
                    R.string.signal_sleep_onset,
                    vitals.sleepOnset?.let { "%02d:%02d".format(it / 60, it % 60) },
                )
                ProbeLine(R.string.signal_steps, vitals.steps?.toString())
            }
        }

        if (group == SettingsGroup.MEASURING) {
            // --- v0.35.0: Smartwatches + Polar AccessLink ---
            //
            // The "Where it comes from" home card surfaces the
            // three wearable sources the user has. The
            // settings section is the action surface: pair a
            // watch, scan for one, set auto-reconnect, and
            // for Polar specifically, sign in to the web
            // bridge that pulls nightly HRV.
            //
            // The two blocks (Smartwatches roster + Polar
            // sign-in) are siblings inside the same
            // "Sources" group, in that order. The roster
            // surfaces the connector that is already wired
            // (the universal BLE HR connector from v0.34.0)
            // and the Polar section surfaces the second
            // connector in the static roster. New vendors
            // land as a `register(...)` call in
            // MindAnchorApp.onCreate and as a sibling block
            // here — no other surface changes.
            Spacer(Modifier.height(24.dp))
            SmartwatchesSection()
            Spacer(Modifier.height(16.dp))
            PolarSection()
            // v0.35.1: the "Run setup wizard again" affordance.
            // Sits in the Sources group, below the per-source
            // sections, because the wizard is the guided tour of
            // this exact group. A tap launches the wizard
            // activity and the user can re-walk the 5 steps. The
            // wizard clears its per-step skipped flags on entry
            // so the user lands on the first not-yet-completed
            // step, not from Welcome.
            Spacer(Modifier.height(16.dp))
            RerunSetupWizardRow()
        }

        if (group == SettingsGroup.PLAN) {
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
        modifier = Modifier.semantics { role = Role.Button },
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
        }

        if (group == SettingsGroup.QUIET) {
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
                TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { confirmingRelease = true }) {
                    Text(stringResource(R.string.owner_release))
                }
                if (confirmingRelease) {
                    AlertDialog(
                        onDismissRequest = { confirmingRelease = false },
                        title = { Text(stringResource(R.string.owner_release)) },
                        text = { Text(stringResource(R.string.owner_release_cost)) },
                        confirmButton = {
                            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = {
                                confirmingRelease = false
                                viewModel.releaseDeviceOwner { permissionEpoch++ }
                            }) {
                                Text(stringResource(R.string.owner_release_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { confirmingRelease = false }) {
                                Text(stringResource(R.string.action_close))
                            }
                        },
                    )
                }
            }
        }

        if (group == SettingsGroup.QUIET) {
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
            val greyNights by viewModel.grayscaleAtNight.collectAsStateWithLifecycle(initialValue = false)

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = grayscaleNow, role = Role.Switch) {
                            Grayscale.set(context, it)
                            grayscaleNow = Grayscale.isOn(context)
                        }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.grayscale_now),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = grayscaleNow, onCheckedChange = null)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = greyNights, role = Role.Switch) {
                            viewModel.setGrayscaleAtNight(it)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.grayscale_at_night),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = greyNights, onCheckedChange = null)
                }
                Text(
                    text = stringResource(R.string.grayscale_shares_a_switch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (group == SettingsGroup.PHONE) {
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
        modifier = Modifier.semantics { role = Role.Button },
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
        modifier = Modifier.semantics { role = Role.Button },
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

            // v0.23.0 WebDAV backup has been
            // removed in v0.25.4. The local file
            // picker (the "Save a copy…" / "Restore
            // from a copy…" buttons above) is the
            // default backup path; the Google Drive
            // path lives in the Reading group
            // (see GoogleDriveBackupSettingsSection
            // at the bottom of the Reading sub-sections).

            // v0.25.0: re-classify every note on
            // demand. Sits after the backup section
            // because both are "do something to
            // existing data" affordances; the
            // backup is "send it elsewhere", the
            // re-classify is "refresh derived data".
            NoteReclassifySection()

            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )

            // v0.20.1 round 5 follow-up: Going Light
            // settings entry. The data layer
            // (FrictionPrefs.goingLightSchedule) is
            // already wired and the VpnService is
            // implemented; the *enabling* surface
            // and the *first-time copy* are pending
            // the v0.20.2 follow-up (the first-time
            // copy is clinical-review-gated per the
            // B+K gate, and the OS-level VPN
            // permission dialog is a one-time
            // consent the user must see in context).
            //
            // What ships in v0.20.1 round 5: a
            // neutral section heading + explainer
            // that tells the user the feature
            // exists and that the *enabling* is on
            // the way. The string is not
            // clinical-review-gated wording; it
            // describes what the feature will do,
            // not how it will be presented to the
            // user the first time they turn it on.
            Text(
                text = stringResource(R.string.going_light_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.going_light_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
