package org.mindanchor.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindanchor.R
import org.mindanchor.backup.BackupRepository
import org.mindanchor.backup.GoogleDriveAuth
import org.mindanchor.continuity.BackupHealth
import org.mindanchor.continuity.ContinuityErrorCode
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.continuity.ContinuitySnapshot
import org.mindanchor.continuity.ContinuitySnapshotCodec
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.continuity.ResearchExportBuilder
import org.mindanchor.continuity.RestoreActivity
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyCodec
import org.mindanchor.continuity.crypto.RecoveryKeyStore
import org.mindanchor.data.db.AnchorDatabase

/**
 * Task 12: the continuity-backup settings surface. Replaces the v0.25.4
 * "Google Drive backup" sub-section (per-type Notes/Letters auto-sync
 * toggles + a manual full-reupload button) — see the plan's Task 12 brief.
 *
 * The old per-type Notes/Letters toggles were wired to no-op
 * `onCheckedChange` lambdas, and a full-tree search before this rewrite
 * found no consumer anywhere in the codebase for their backing
 * `SettingsViewModel` flows and setters (since removed): nothing ever read
 * them except the settings ViewModel that exposed them and this
 * Composable's dead toggle. Program 0's [ContinuityWorkScheduler] now
 * checkpoints Notes/Letters (via the
 * Journal-adjacent stores [org.mindanchor.continuity.ContinuitySnapshotRepository]
 * captures) unconditionally whenever continuity backup is on, which makes
 * a separate per-type opt-in a stale idea, not just a bug to fix — so this
 * rewrite deletes the toggle UI rather than wiring it to something. The
 * corresponding orphaned `SettingsViewModel` members were removed in the
 * same change.
 *
 * The Google sign-in machinery ([GoogleDriveAuth]) is unchanged and
 * reused as-is; only the surface built on top of it is new. Everything
 * this section can do:
 *
 *  - Google account (sign in / signed-in email / forget account) —
 *    unchanged.
 *  - Recovery key status + generate/verify — the first UI for Task 8's
 *    [RecoveryKeyStore]/[RecoveryKeyCodec]. A generated key is shown in
 *    its human-readable form exactly once; verifying it requires typing
 *    the full key back (never a checkbox — see [RecoveryKeyStore.markVerified]'s
 *    KDoc, "Copying it is not verification").
 *  - The automatic-backup switch, gated: it can only be turned ON while
 *    signed in AND the recovery key is verified. Turning it off is always
 *    allowed and never deletes any local or remote data — it only calls
 *    [ContinuityWorkScheduler.cancelAll].
 *  - The nightly-snapshots switch and the structural-context-extraction
 *    local kill switch, both plain [ContinuityPrefs] pass-throughs.
 *  - "Back up now" — schedules a checkpoint via
 *    [ContinuityWorkScheduler.requestCheckpoint] and shows a "Checkpoint
 *    requested" confirmation. It never performs network I/O itself and
 *    never claims a verified backup happened — see this file's strings
 *    for the "upload is not verified backup" product-honesty rule.
 *  - "Restore on this phone" — launches [RestoreActivity].
 *  - "Save encrypted copy to a file" — captures a snapshot, encrypts it
 *    with the verified recovery key, and writes it to a user-picked file.
 *  - "Export research JSON" — a plaintext, privacy-warned export built by
 *    the shared [ResearchExportBuilder] (so a later Journal Patterns
 *    wiring can call the exact same function).
 *  - "Forget this account" — unchanged, calls [GoogleDriveAuth.signOut].
 */
@Composable
@Suppress("FunctionNaming")
internal fun GoogleDriveBackupSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel = remember(context) { ContinuitySettingsViewModel(context.applicationContext) }
    GoogleDriveBackupSettingsSectionContent(viewModel, modifier)
}

/**
 * The actual rendering, split out from [GoogleDriveBackupSettingsSection]
 * so an instrumented test can inject a [ContinuitySettingsViewModel] built
 * with fakes (a stubbed sign-in flow, recording `ContinuityWorkScheduler`
 * lambdas, an in-memory [AnchorDatabase]) instead of the real,
 * production-wired one the zero-arg entry point above always builds.
 */
@Composable
@Suppress("FunctionNaming")
internal fun GoogleDriveBackupSettingsSectionContent(
    viewModel: ContinuitySettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionHeader()
        AccountSection(viewModel)
        RecoveryKeySection(viewModel)
        BackupSwitchesSection(viewModel)
        ActionsSection(viewModel)
        HealthSection(viewModel)
        MessageLine(viewModel)
    }
}

@Composable
@Suppress("FunctionNaming")
private fun SectionHeader() {
    Text(
        text = stringResource(R.string.continuity_section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Text(
        text = stringResource(R.string.continuity_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
@Suppress("FunctionNaming")
private fun AccountSection(viewModel: ContinuitySettingsViewModel) {
    val signedInEmail by viewModel.signedInEmail.collectAsState(initial = null)
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result -> viewModel.handleSignInResult(result.data) }

    val email = signedInEmail
    if (email == null) {
        TextButton(
            onClick = { signInLauncher.launch(viewModel.signInIntent()) },
            modifier = Modifier.padding(top = 8.dp).testTag("continuity_sign_in_button"),
        ) {
            Text(stringResource(R.string.drive_sign_in))
        }
    } else {
        Text(
            text = stringResource(R.string.drive_signed_in_as, email),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp).testTag("continuity_signed_in_status"),
        )
        TextButton(
            onClick = viewModel::forgetAccount,
            modifier = Modifier.testTag("continuity_forget_account_button"),
        ) {
            Text(stringResource(R.string.drive_forget_account))
        }
    }
}

@Composable
private fun recoveryKeyStatusLabel(status: RecoveryKeyStatus): String = when (status) {
    RecoveryKeyStatus.NOT_CREATED -> stringResource(R.string.continuity_recovery_key_not_created)
    RecoveryKeyStatus.NEEDS_VERIFICATION -> stringResource(R.string.continuity_recovery_key_needs_verification)
    RecoveryKeyStatus.VERIFIED -> stringResource(R.string.continuity_recovery_key_verified)
}

@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun RecoveryKeySection(viewModel: ContinuitySettingsViewModel) {
    val status by viewModel.recoveryKeyStatus.collectAsState()
    val pendingKey by viewModel.pendingGeneratedKeyHuman.collectAsState()
    val verifyError by viewModel.verifyError.collectAsState()

    Text(
        text = stringResource(R.string.continuity_recovery_key_section) + ": " + recoveryKeyStatusLabel(status),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp).testTag("continuity_recovery_key_status"),
    )

    val shownKey = pendingKey
    if (shownKey != null) {
        // Shown exactly once, right after generation — never re-derivable
        // from the store afterward (RecoveryKeyStore never persists the
        // human-readable form, only bytes + keyId).
        Text(
            text = stringResource(R.string.continuity_recovery_key_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = shownKey,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("continuity_recovery_key_generated_value"),
        )
        Text(
            text = stringResource(R.string.continuity_recovery_key_verify_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var typed by remember(shownKey) { mutableStateOf("") }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            label = { Text(stringResource(R.string.continuity_recovery_key_verify_hint)) },
            // The base64url payload is case-sensitive (upper/lower case
            // are distinct symbols, not a stylistic choice), so this
            // field must never let the keyboard "helpfully" autocorrect
            // or auto-capitalize what looks like nonsense words —
            // exactly the failure mode a real phone keyboard hits on a
            // field with default KeyboardOptions.
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("continuity_recovery_key_verify_field"),
        )
        if (verifyError) {
            Text(
                text = stringResource(R.string.continuity_recovery_key_verify_mismatch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("continuity_recovery_key_verify_error"),
            )
        }
        Row {
            TextButton(
                onClick = { viewModel.verifyTypedKey(typed) },
                modifier = Modifier.testTag("continuity_recovery_key_verify_button"),
            ) {
                Text(stringResource(R.string.continuity_recovery_key_verify_action))
            }
            TextButton(
                onClick = viewModel::dismissGeneratedKey,
                modifier = Modifier.testTag("continuity_recovery_key_dismiss_button"),
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    } else if (status == RecoveryKeyStatus.NOT_CREATED) {
        TextButton(
            onClick = viewModel::generateRecoveryKey,
            modifier = Modifier.testTag("continuity_recovery_key_generate_button"),
        ) {
            Text(stringResource(R.string.continuity_recovery_key_generate))
        }
    } else if (status == RecoveryKeyStatus.NEEDS_VERIFICATION) {
        // A key was generated in a previous visit but never verified —
        // the once-shown human form is gone; only re-generating (which
        // replaces the key and shows a fresh human form) recovers it.
        TextButton(
            onClick = viewModel::generateRecoveryKey,
            modifier = Modifier.testTag("continuity_recovery_key_generate_button"),
        ) {
            Text(stringResource(R.string.continuity_recovery_key_generate))
        }
    }
}

@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun BackupSwitchesSection(viewModel: ContinuitySettingsViewModel) {
    val signedInEmail by viewModel.signedInEmail.collectAsState(initial = null)
    val recoveryKeyStatus by viewModel.recoveryKeyStatus.collectAsState()
    val backupEnabled by viewModel.backupEnabled.collectAsState(initial = false)
    val nightlyEnabled by viewModel.nightlySnapshotsEnabled.collectAsState(initial = true)
    val contextExtractionEnabled by viewModel.contextExtractionEnabled.collectAsState(initial = true)

    // Derived from the properly-observed StateFlows above (not from a
    // live SharedPreferences read) so a generate/verify action recomposes
    // this immediately. ContinuitySettingsViewModel.setBackupEnabled does
    // its own authoritative re-check at click time via canEnableBackup —
    // this is only the display-time gate.
    val canEnable = signedInEmail != null && recoveryKeyStatus == RecoveryKeyStatus.VERIFIED
    val switchInteractive = backupEnabled || canEnable

    Row(
        // toggleable on the Row (not a bare onCheckedChange on the
        // Switch alone) is this codebase's established pattern for a
        // label+switch row: it merges the Text and the switch state
        // into one TalkBack-focusable node ("Automatic continuity
        // backup, off"), matching SettingsRowSwitch and every other
        // toggle row in SettingsScreen.kt. The Switch itself becomes
        // decorative (onCheckedChange = null) so it does not also
        // register as a second, separately-focusable, unlabeled node.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = backupEnabled,
                enabled = switchInteractive,
                role = Role.Switch,
                onValueChange = { viewModel.setBackupEnabled(it, signedInEmail != null) },
            )
            .padding(top = 12.dp)
            .testTag("continuity_backup_switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.continuity_backup_switch),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Switch(
            checked = backupEnabled,
            enabled = switchInteractive,
            onCheckedChange = null,
        )
    }
    if (!backupEnabled && !canEnable) {
        Text(
            text = stringResource(R.string.continuity_backup_switch_gated_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (backupEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = nightlyEnabled,
                    role = Role.Switch,
                    onValueChange = viewModel::setNightlySnapshotsEnabled,
                )
                .padding(top = 4.dp)
                .testTag("continuity_nightly_switch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.continuity_nightly_switch),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Switch(
                checked = nightlyEnabled,
                onCheckedChange = null,
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = contextExtractionEnabled,
                role = Role.Switch,
                onValueChange = viewModel::setContextExtractionEnabled,
            )
            .padding(top = 4.dp)
            .testTag("continuity_context_extraction_switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.continuity_context_extraction_switch),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Switch(
            checked = contextExtractionEnabled,
            onCheckedChange = null,
        )
    }
    Text(
        text = stringResource(R.string.continuity_context_extraction_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun ActionsSection(viewModel: ContinuitySettingsViewModel) {
    val recoveryKeyStatus by viewModel.recoveryKeyStatus.collectAsState()
    val hasVerifiedKey = recoveryKeyStatus == RecoveryKeyStatus.VERIFIED
    var showResearchPrivacyDialog by remember { mutableStateOf(false) }

    TextButton(
        onClick = viewModel::backUpNow,
        modifier = Modifier.padding(top = 8.dp).testTag("continuity_back_up_now_button"),
    ) {
        Text(stringResource(R.string.continuity_back_up_now))
    }

    TextButton(
        onClick = viewModel::openRestore,
        modifier = Modifier.testTag("continuity_restore_button"),
    ) {
        Text(stringResource(R.string.continuity_restore))
    }

    if (hasVerifiedKey) {
        val savePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri -> uri?.let(viewModel::saveEncryptedCopy) }
        TextButton(
            onClick = { savePicker.launch(CONTINUITY_SNAPSHOT_FILE_NAME) },
            modifier = Modifier.testTag("continuity_save_encrypted_copy_button"),
        ) {
            Text(stringResource(R.string.continuity_save_encrypted_copy))
        }
    }

    val researchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportResearch) }
    TextButton(
        onClick = { showResearchPrivacyDialog = true },
        modifier = Modifier.testTag("continuity_export_research_button"),
    ) {
        Text(stringResource(R.string.continuity_export_research))
    }
    if (showResearchPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showResearchPrivacyDialog = false },
            title = { Text(stringResource(R.string.continuity_export_research_privacy_title)) },
            text = { Text(stringResource(R.string.continuity_export_research_privacy_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResearchPrivacyDialog = false
                        researchPicker.launch(ResearchExportBuilder.fileName())
                    },
                    modifier = Modifier.testTag("continuity_export_research_privacy_confirm_button"),
                ) { Text(stringResource(R.string.continuity_export_research_privacy_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResearchPrivacyDialog = false },
                    modifier = Modifier.testTag("continuity_export_research_privacy_cancel_button"),
                ) { Text(stringResource(R.string.continuity_export_research_privacy_cancel)) }
            },
        )
    }
}

/** A fixed suggested filename for the encrypted-copy document picker. */
private const val CONTINUITY_SNAPSHOT_FILE_NAME = "mindanchor-continuity-backup.mab"

@Composable
@Suppress("FunctionNaming")
private fun MessageLine(viewModel: ContinuitySettingsViewModel) {
    val message by viewModel.message.collectAsState()
    val text = when (val m = message) {
        null -> return
        is ContinuityMessage.CheckpointRequested -> stringResource(R.string.continuity_checkpoint_requested)
        is ContinuityMessage.SaveSucceeded -> stringResource(R.string.continuity_save_encrypted_copy_success)
        is ContinuityMessage.SaveFailed -> stringResource(R.string.continuity_save_encrypted_copy_failed)
        is ContinuityMessage.ExportSucceeded ->
            stringResource(R.string.continuity_export_research_success, m.truncatedHash)
        is ContinuityMessage.ExportFailed -> stringResource(R.string.continuity_export_research_failed)
        is ContinuityMessage.Forgotten -> stringResource(R.string.drive_forgot)
        is ContinuityMessage.SignInFailed -> stringResource(R.string.drive_sign_in_failed)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp).testTag("continuity_message"),
    )
}

private val HEALTH_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

private fun formatHealthTime(epochMillis: Long): String =
    HEALTH_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * Step 3 of the Task 12 brief: exact health copy, one string per
 * [BackupHealth] variant. Never infers or displays anything about the
 * person's mental-health status — this is pure infrastructure health.
 */
@Composable
@Suppress("FunctionNaming")
private fun backupHealthLabel(health: BackupHealth): String = when (health) {
    is BackupHealth.Verified -> stringResource(R.string.continuity_health_verified, formatHealthTime(health.at))
    is BackupHealth.Pending -> stringResource(R.string.continuity_health_pending)
    is BackupHealth.NeedsSignIn -> stringResource(R.string.continuity_health_needs_sign_in)
    is BackupHealth.RecoveryKeyRequired -> stringResource(R.string.continuity_health_recovery_key_required)
    is BackupHealth.VerificationFailed -> stringResource(R.string.continuity_health_verification_failed)
}

@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun HealthSection(viewModel: ContinuitySettingsViewModel) {
    val backupEnabled by viewModel.backupEnabled.collectAsState(initial = false)
    val recoveryKeyStatus by viewModel.recoveryKeyStatus.collectAsState()
    val lastErrorCode by viewModel.lastErrorCode.collectAsState(initial = ContinuityErrorCode.NONE)
    val lastCheckpoint by viewModel.lastCheckpoint.collectAsState(initial = null)
    val lastNightly by viewModel.lastNightly.collectAsState(initial = null)
    val lastRestore by viewModel.lastRestore.collectAsState(initial = null)
    val dirtySince by viewModel.dirtySince.collectAsState(initial = null)

    val health = BackupHealth.compute(
        backupEnabled = backupEnabled,
        hasVerifiedRecoveryKey = recoveryKeyStatus == RecoveryKeyStatus.VERIFIED,
        lastErrorCode = lastErrorCode,
        lastVerifiedCheckpoint = lastCheckpoint,
    )

    Text(
        text = backupHealthLabel(health),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp).testTag("continuity_health_state"),
    )
    if (dirtySince != null) {
        Text(
            text = stringResource(R.string.continuity_health_local_changes_pending, formatHealthTime(dirtySince!!)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("continuity_health_dirty_since"),
        )
    }
    Text(
        text = lastNightly?.let { stringResource(R.string.continuity_health_last_nightly, formatHealthTime(it.at)) }
            ?: stringResource(R.string.continuity_health_last_nightly_never),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("continuity_health_last_nightly"),
    )
    Text(
        text = lastRestore?.let { stringResource(R.string.continuity_health_last_restore, formatHealthTime(it.at)) }
            ?: stringResource(R.string.continuity_health_last_restore_never),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("continuity_health_last_restore"),
    )
}

/** The three states [RecoveryKeyStore]'s local key can be in — see the Task 12 brief's Step 2. */
internal enum class RecoveryKeyStatus { NOT_CREATED, NEEDS_VERIFICATION, VERIFIED }

/**
 * A one-shot, typed feedback message — the type-safe replacement for the
 * old file's `driveMessage: Int?`. Each variant maps to exactly one
 * strings.xml resource in [MessageLine]. [ContinuityMessage.CheckpointRequested]
 * deliberately never claims the data is safely stored anywhere —
 * scheduling a checkpoint is not the same claim as [BackupHealth.Verified],
 * which alone may say a backup is verified (see this file's KDoc and the
 * strings it renders).
 */
internal sealed class ContinuityMessage {
    data object CheckpointRequested : ContinuityMessage()
    data object SaveSucceeded : ContinuityMessage()
    data object SaveFailed : ContinuityMessage()
    data class ExportSucceeded(val truncatedHash: String) : ContinuityMessage()
    data object ExportFailed : ContinuityMessage()
    data object Forgotten : ContinuityMessage()
    data object SignInFailed : ContinuityMessage()
}

/**
 * Owns [GoogleDriveBackupSettingsSection]'s state. A plain
 * constructor-injected class (not an `AndroidViewModel`) — the same shape
 * [org.mindanchor.continuity.RestoreViewModel] uses — so an instrumented
 * test can inject fakes for [signedInEmail], the [ContinuityWorkScheduler]
 * calls, and the backing [AnchorDatabase] without touching real Google
 * sign-in or real WorkManager scheduling.
 */
internal class ContinuitySettingsViewModel(
    private val context: Context,
    private val auth: GoogleDriveAuth = GoogleDriveAuth(context.applicationContext),
    val signedInEmail: Flow<String?> = auth.signedInEmailFlow,
    private val continuityPrefs: ContinuityPrefs = ContinuityPrefs(context.applicationContext),
    private val recoveryKeyStore: RecoveryKeyStore = RecoveryKeyStore.create(context.applicationContext),
    private val database: AnchorDatabase = AnchorDatabase.get(context.applicationContext),
    private val requestCheckpoint: (Context) -> Unit = ContinuityWorkScheduler::requestCheckpoint,
    private val ensureNightlyScheduled: (Context) -> Unit = ContinuityWorkScheduler::ensureNightlyScheduled,
    private val cancelAllWork: (Context) -> Unit = ContinuityWorkScheduler::cancelAll,
    /**
     * Actually starts the given [Intent] — a seam so an instrumented test
     * can capture the Intent [openRestore] builds instead of really
     * starting [RestoreActivity]. Kept separate from `Intent(context,
     * RestoreActivity::class.java)` itself, which always runs for real
     * (and is what the JVM file-shape test pins) — only the act of
     * launching it is swappable.
     */
    private val launchRestore: (Context, Intent) -> Unit = { ctx, intent -> ctx.startActivity(intent) },
    private val captureSnapshot: suspend (Long) -> ContinuitySnapshot = { now ->
        defaultSnapshotRepository(context).capture(now)
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    val backupEnabled: Flow<Boolean> = continuityPrefs.backupEnabled
    val nightlySnapshotsEnabled: Flow<Boolean> = continuityPrefs.nightlySnapshotsEnabled
    val contextExtractionEnabled: Flow<Boolean> = continuityPrefs.contextExtractionEnabled
    val lastCheckpoint: Flow<ContinuityPrefs.VerifiedRecord?> = continuityPrefs.lastCheckpoint
    val lastNightly: Flow<ContinuityPrefs.VerifiedRecord?> = continuityPrefs.lastNightly
    val lastRestore: Flow<ContinuityPrefs.RestoreRecord?> = continuityPrefs.lastRestore
    val dirtySince: Flow<Long?> = continuityPrefs.dirtySince
    val lastErrorCode: Flow<ContinuityErrorCode> = continuityPrefs.lastErrorCode

    private val _recoveryKeyStatus = MutableStateFlow(currentRecoveryKeyStatus())
    val recoveryKeyStatus: StateFlow<RecoveryKeyStatus> = _recoveryKeyStatus.asStateFlow()

    private val _pendingGeneratedKeyHuman = MutableStateFlow<String?>(null)

    /** The human-readable key, held only right after generation — see class KDoc. */
    val pendingGeneratedKeyHuman: StateFlow<String?> = _pendingGeneratedKeyHuman.asStateFlow()

    private val _verifyError = MutableStateFlow(false)
    val verifyError: StateFlow<Boolean> = _verifyError.asStateFlow()

    private val _message = MutableStateFlow<ContinuityMessage?>(null)
    val message: StateFlow<ContinuityMessage?> = _message.asStateFlow()

    private fun currentRecoveryKeyStatus(): RecoveryKeyStatus {
        val key = recoveryKeyStore.current() ?: return RecoveryKeyStatus.NOT_CREATED
        return if (recoveryKeyStore.isVerified()) RecoveryKeyStatus.VERIFIED else RecoveryKeyStatus.NEEDS_VERIFICATION
    }

    private fun refreshRecoveryKeyStatus() {
        _recoveryKeyStatus.value = currentRecoveryKeyStatus()
    }

    fun signInIntent(): Intent = auth.signInIntent()

    fun handleSignInResult(data: Intent?) {
        scope.launch {
            val outcome = auth.handleSignInResult(data)
            if (outcome is GoogleDriveAuth.SignInOutcome.Failure) {
                _message.value = ContinuityMessage.SignInFailed
            }
        }
    }

    /**
     * Generates a fresh key, saves it immediately (unverified — see
     * [RecoveryKeyStore.save]'s own contract), and shows the human form
     * once. A key that exists-but-is-unverified (e.g. the user navigated
     * away before retyping it) is the [RecoveryKeyStatus.NEEDS_VERIFICATION]
     * state; the once-shown human form does not survive that — only
     * generating again produces a fresh one.
     */
    fun generateRecoveryKey() {
        val key: RecoveryKey = RecoveryKeyCodec.generate()
        recoveryKeyStore.save(key)
        _pendingGeneratedKeyHuman.value = RecoveryKeyCodec.format(key)
        _verifyError.value = false
        refreshRecoveryKeyStatus()
    }

    /** Dismisses the once-shown human key. Does not affect verification state. */
    fun dismissGeneratedKey() {
        _pendingGeneratedKeyHuman.value = null
    }

    /**
     * The only path to [RecoveryKeyStatus.VERIFIED]: [typed] must decode
     * (via [RecoveryKeyCodec.decode], applying its normalization rules)
     * to the SAME key currently on file. A checkbox or an "I saved it" tap
     * can never call this — see [RecoveryKeyStore.markVerified]'s KDoc.
     */
    fun verifyTypedKey(typed: String): Boolean {
        val decoded = RecoveryKeyCodec.decode(typed)
        val stored = recoveryKeyStore.current()
        if (decoded == null || stored == null || decoded.keyId != stored.keyId) {
            _verifyError.value = true
            return false
        }
        recoveryKeyStore.markVerified()
        _verifyError.value = false
        _pendingGeneratedKeyHuman.value = null
        refreshRecoveryKeyStatus()
        return true
    }

    /**
     * True only when both the sign-in and verified-key gates are
     * satisfied. A fresh, authoritative read of [recoveryKeyStore] at
     * action time — [setBackupEnabled]'s own enforcement, independent of
     * whatever the Composable's (StateFlow-derived) display-time gate
     * currently shows.
     */
    private fun canEnableBackup(signedIn: Boolean): Boolean = signedIn && recoveryKeyStore.isVerified()

    /**
     * Turning ON is refused unless [canEnableBackup] holds — nothing is
     * written and neither scheduler call happens. Turning OFF is always
     * allowed and only cancels scheduled work; it never deletes local
     * (Room/DataStore) or remote data — there is no delete path here at
     * all, matching [org.mindanchor.backup.RemoteBackupStore]'s own lack
     * of a delete method.
     */
    fun setBackupEnabled(enabled: Boolean, signedIn: Boolean) {
        if (enabled && !canEnableBackup(signedIn)) return
        scope.launch {
            continuityPrefs.setBackupEnabled(enabled)
            if (enabled) {
                requestCheckpoint(context)
                ensureNightlyScheduled(context)
            } else {
                cancelAllWork(context)
            }
        }
    }

    fun setNightlySnapshotsEnabled(enabled: Boolean) {
        scope.launch { continuityPrefs.setNightlySnapshotsEnabled(enabled) }
    }

    /**
     * The local kill switch. Only ever writes the flag — Journal writing
     * itself has no dependency on it (Task 10's own review deferred that
     * gating to a later task; this task builds the switch UI without
     * wiring the gate, since the flag flipping is the whole testable
     * contract here — see the Task 12 brief's Step 2).
     */
    fun setContextExtractionEnabled(enabled: Boolean) {
        scope.launch { continuityPrefs.setContextExtractionEnabled(enabled) }
    }

    /** Schedules a checkpoint. Never performs network I/O on this (or any) scope. */
    fun backUpNow() {
        requestCheckpoint(context)
        _message.value = ContinuityMessage.CheckpointRequested
    }

    /** Launches [RestoreActivity] via [launchRestore] — see that field's KDoc. */
    fun openRestore() {
        launchRestore(context, Intent(context, RestoreActivity::class.java))
    }

    fun saveEncryptedCopy(uri: Uri) {
        scope.launch {
            val key = recoveryKeyStore.current()?.takeIf { recoveryKeyStore.isVerified() }
            if (key == null) {
                _message.value = ContinuityMessage.SaveFailed
                return@launch
            }
            val now = System.currentTimeMillis()
            val snapshot = captureSnapshot(now)
            val envelope = BackupEnvelopeCodec.encrypt(ContinuitySnapshotCodec.encode(snapshot), key, now)
            val wrote = withContext(Dispatchers.IO) {
                BackupRepository.write(context, uri, BackupEnvelopeCodec.encode(envelope))
            }
            _message.value = if (wrote) ContinuityMessage.SaveSucceeded else ContinuityMessage.SaveFailed
        }
    }

    fun exportResearch(uri: Uri) {
        scope.launch {
            _message.value = when (val outcome = ResearchExportBuilder.export(context, database, uri)) {
                is ResearchExportBuilder.ExportOutcome.Success ->
                    ContinuityMessage.ExportSucceeded(ResearchExportBuilder.truncatedHash(outcome.contentSha256))
                is ResearchExportBuilder.ExportOutcome.WriteFailed -> ContinuityMessage.ExportFailed
                is ResearchExportBuilder.ExportOutcome.BuildFailed -> ContinuityMessage.ExportFailed
            }
        }
    }

    fun forgetAccount() {
        scope.launch {
            auth.signOut()
            _message.value = ContinuityMessage.Forgotten
        }
    }
}

/**
 * The real, production [org.mindanchor.continuity.ContinuitySnapshotRepository]
 * wiring — the same shape [org.mindanchor.continuity.CheckpointBackupWorker.buildCoordinator]
 * builds. Extracted so [ContinuitySettingsViewModel]'s default
 * `captureSnapshot` does not repeat this five-dependency construction
 * inline.
 */
private fun defaultSnapshotRepository(context: Context): org.mindanchor.continuity.ContinuitySnapshotRepository =
    org.mindanchor.continuity.ContinuitySnapshotRepository(
        context = context,
        database = AnchorDatabase.get(context),
        notesPrefs = org.mindanchor.data.NotesPrefs(context),
        letterStore = org.mindanchor.letters.LetterStore(context),
        frictionPrefs = org.mindanchor.data.FrictionPrefs(context),
        deviceIdentity = org.mindanchor.journal.DeviceIdentityStore(context),
        backupRepository = BackupRepository(context),
    )
