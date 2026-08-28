package org.mindanchor.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.mindanchor.R
import org.mindanchor.backup.BackupScheduler
import org.mindanchor.backup.ContentType
import org.mindanchor.backup.GoogleDriveAuth
import org.mindanchor.backup.GoogleDriveBackupTarget

/**
 * The Google Drive backup sub-section. v0.25.4
 * (WP-C); consolidated to one nightly sync
 * covering every content type in v0.70.7.
 * Replaces the v0.23.0 WebDAV sub-section in the
 * Settings → Reading group.
 *
 * Opt-in shape mirrors the v0.23.0 design this
 * extends: the section exposes a "Sign in with
 * Google" button (default state) which, on
 * success, flips to "Signed in as <email>" + the
 * nightly-sync toggle + the "Back up now" /
 * "Restore from Google Drive" / "Forget this
 * account" affordances. The toggle gates
 * [org.mindanchor.backup.DriveNightlySync]'s
 * alarm; "Forget this account" clears the local
 * credentials and re-prompts on the next sign-in.
 *
 * The `Back up now` button dispatches
 * [BackupScheduler.backupAll] — every note,
 * letter, check-in, and wellness reading not
 * already in this phone's Drive. `Restore from
 * Google Drive` is the reverse:
 * [BackupScheduler.restoreAll] pulls in whatever
 * is in Drive but missing locally, which is the
 * path a new phone signed into the same account
 * uses to pick up where the old one left off. Both
 * schedulers are created on demand (one per
 * click) with a fresh [GoogleDriveBackupTarget]
 * per content type, so the network round-trips
 * are independent and the OkHttp client is
 * disposable.
 *
 * The section is a Composable, not a class —
 * the parent SettingsScreen already has the
 * `viewModel` and the `LocalContext` for the
 * [GoogleDriveAuth] instance; this composable
 * is the per-section piece of that surface.
 */
@Composable
@Suppress("FunctionNaming")
internal fun GoogleDriveBackupSettingsSection(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The auth is created once per settings
    // visit; clearing the activity in the
    // remember call would lose the user's
    // email on every recomposition.
    val auth = remember(context) { GoogleDriveAuth(context.applicationContext) }
    val signedInEmail by auth.signedInEmailFlow.collectAsState(initial = null)
    val driveNightlySyncEnabled by viewModel.driveNightlySyncEnabled.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var driveMessage by remember { mutableStateOf<Int?>(null) }

    // The result launcher is registered at
    // composition time and held in the
    // remember-scoped state. The activity
    // result fires on the IO dispatcher;
    // the launcher's block runs on the main
    // thread (it touches the UI state).
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        coroutineScope.launch {
            val outcome = auth.handleSignInResult(result.data)
            if (outcome is GoogleDriveAuth.SignInOutcome.Failure) {
                driveMessage = R.string.drive_sign_in_failed
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionHeader()
        val email = signedInEmail
        if (email == null) {
            SignedOutContent(
                onSignIn = { signInLauncher.launch(auth.signInIntent()) },
            )
        } else {
            SignedInContent(
                email = email,
                nightlySyncEnabled = driveNightlySyncEnabled,
                onToggleNightlySync = viewModel::setDriveNightlySyncEnabled,
                auth = auth,
                coroutineScope = coroutineScope,
                onMessage = { driveMessage = it },
            )
        }
        driveMessage?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The header row: section title + the
 * user-facing explainer. Extracted so the
 * orchestrator [GoogleDriveBackupSettingsSection]
 * stays under the detekt `LongMethod` cap.
 */
@Composable
@Suppress("FunctionNaming")
private fun SectionHeader() {
    Text(
        text = stringResource(R.string.drive_section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Text(
        text = stringResource(R.string.drive_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The "Sign in with Google" affordance,
 * shown when the user has not yet signed
 * in. The toggles and the "Back up now" /
 * "Forget" affordances are hidden so the
 * surface does not invite a click that
 * requires an account.
 */
@Composable
@Suppress("FunctionNaming")
private fun SignedOutContent(onSignIn: () -> Unit) {
    TextButton(
        onClick = onSignIn,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.drive_sign_in))
    }
}

/**
 * The signed-in surface: the email, the one
 * nightly-sync toggle, and the "Back up now" /
 * "Restore from Google Drive" / "Forget this
 * account" buttons. The manual buttons dispatch
 * [BackupScheduler] directly; the toggle instead
 * arms [org.mindanchor.backup.DriveNightlySync]
 * via [onToggleNightlySync] (see
 * [SettingsViewModel.setDriveNightlySyncEnabled]).
 */
@Composable
@Suppress("FunctionNaming")
private fun SignedInContent(
    email: String,
    nightlySyncEnabled: Boolean,
    onToggleNightlySync: (Boolean) -> Unit,
    auth: GoogleDriveAuth,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.drive_signed_in_as, email),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    GoogleDriveAutoSyncRow(
        labelRes = R.string.drive_nightly_sync,
        checked = nightlySyncEnabled,
        onCheckedChange = onToggleNightlySync,
    )
    // The manual full-sync path. The scheduler is
    // created on demand; one per click keeps the
    // network round-trips independent and the
    // OkHttp client disposable.
    TextButton(
        onClick = {
            coroutineScope.launch {
                val result = buildScheduler(context, auth).backupAll()
                onMessage(
                    if (result.ok) R.string.drive_backup_uploaded
                    else R.string.drive_upload_failed,
                )
            }
        },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.drive_backup_now))
    }
    TextButton(
        onClick = {
            coroutineScope.launch {
                val outcome = runCatching { buildScheduler(context, auth).restoreAll() }
                onMessage(
                    outcome.fold(
                        onSuccess = { result ->
                            if (result.total > 0) R.string.drive_restore_done else R.string.drive_restore_nothing
                        },
                        onFailure = { R.string.drive_restore_failed },
                    ),
                )
            }
        },
    ) {
        Text(stringResource(R.string.drive_restore_now))
    }
    TextButton(
        onClick = {
            coroutineScope.launch {
                auth.signOut()
                onMessage(R.string.drive_forgot)
            }
        },
    ) {
        Text(stringResource(R.string.drive_forget_account))
    }
}

/** One [BackupScheduler] wired to all four content types, for a single manual button click. */
private fun buildScheduler(context: android.content.Context, auth: GoogleDriveAuth): BackupScheduler {
    val client = OkHttpClient()
    val appContext = context.applicationContext
    return BackupScheduler(
        context = appContext,
        notesTarget = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.Notes),
        lettersTarget = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.Letters),
        checkInsTarget = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.CheckIns),
        wellnessTarget = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.WellnessReadings),
    )
}

/**
 * A single auto-sync toggle row. The row carries
 * the label and the toggle, so a screen reader
 * hears the words and the checked state together
 * (the standard pattern used throughout the
 * Settings screen, see the Goal toggles for
 * the model). The class is `internal` so the
 * test surface can construct one for a
 * file-shape test if a future test wants to
 * exercise the composable directly.
 */
@Composable
@Suppress("FunctionNaming")
private fun GoogleDriveAutoSyncRow(
    labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
