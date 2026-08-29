package org.mindanchor.continuity

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import org.mindanchor.backup.GoogleDriveAuth
import org.mindanchor.backup.GoogleDriveObjectStore
import org.mindanchor.backup.RemoteBackupStore
import org.mindanchor.continuity.crypto.RecoveryKeyCodec
import org.mindanchor.continuity.crypto.RecoveryKeyStore

/**
 * The read-only preview [RestoreScreen] shows for a selected candidate.
 * Deliberately narrow: a timestamp, the app version that wrote it, and
 * three counts. NEVER any Journal/Notes/Letters body or title text — see
 * [RestoreCandidate]'s KDoc.
 */
data class RestorePreview(
    val createdAt: Long,
    val appVersionName: String,
    val journalEntryCount: Int,
    val morningMeasureCount: Int,
    val sourceDeviceId: String,
)

/** [RestoreScreen]'s whole state machine, one case per thing the user can see. */
sealed class RestoreUiState {
    /** Waiting for the user to enter a recovery key (and, implicitly, sign in). */
    data object EnterKey : RestoreUiState()

    /** The typed text does not parse as a valid `MA1-...` recovery key. */
    data object InvalidKeyFormat : RestoreUiState()

    /** Downloading and verifying candidates (Step 3 of the brief). */
    data object CheckingCandidate : RestoreUiState()

    /** A verified, decryptable candidate was found and is awaiting the user's explicit confirmation. */
    data class CandidateFound(val preview: RestorePreview, val usedFallbackFrom: String?) : RestoreUiState()

    /** The recovery key does not match any candidate's envelope. Stops immediately — see [RestoreCandidateSelector]. */
    data object WrongRecoveryKey : RestoreUiState()

    /** No decryptable backup exists at all. */
    data object NoBackupFound : RestoreUiState()

    /** A network/auth problem while listing or downloading candidates. */
    data class RemoteError(val code: String) : RestoreUiState()

    /** The local-data preflight refused to start (this phone already has meaningful local data). */
    data object PreflightBlocked : RestoreUiState()

    /** The restore is running; [completedStages] fills in as each stage is durably persisted. */
    data class Restoring(val completedStages: Set<RestoreStage>) : RestoreUiState()

    /** [RestoreStage.VERIFIED] reached: the content hash matched. */
    data class RestoreComplete(val contentHash: String) : RestoreUiState()

    /** Any other [RestoreResult] the coordinator returned. */
    data class RestoreFailed(val result: RestoreResult) : RestoreUiState()
}

/**
 * Owns [RestoreScreen]'s state. A plain constructor-injected class (not an
 * [androidx.lifecycle.ViewModel]) — the same shape
 * `org.mindanchor.journal.JournalViewModel` uses — built fresh by
 * [RestoreActivity] in `onCreate`.
 */
class RestoreViewModel(
    private val context: Context,
    private val auth: GoogleDriveAuth = GoogleDriveAuth(context.applicationContext),
    private val remoteBackupStore: RemoteBackupStore = GoogleDriveObjectStore(currentAccessToken = auth::currentAccessToken),
    // Defaults to the same on-device store RestoreCoordinator.resume()'s
    // real production currentVerifiedKey reads from (see checkForBackup()).
    private val recoveryKeyStore: RecoveryKeyStore = RecoveryKeyStore.create(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    // Defaults to the real production wiring; overridable so
    // `RestoreScreenTest` can drive the confirm-and-restore flow against an
    // in-memory Room database instead of the app's real on-device
    // singleton, the same "every other Room-backed test builds its own
    // database" convention the rest of this codebase's androidTest suite
    // follows (see RestoreResumeTest).
    private val coordinatorBuilder: (Context) -> RestoreCoordinator = RestoreCoordinator::build,
) {
    val signedInEmail: Flow<String?> = auth.signedInEmailFlow

    var recoveryKeyInput: String by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow<RestoreUiState>(RestoreUiState.EnterKey)
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    private var selectedCandidate: RestoreCandidate? = null

    fun signInIntent(): Intent = auth.signInIntent()

    fun handleSignInResult(data: Intent?) {
        scope.launch { auth.handleSignInResult(data) }
    }

    fun onRecoveryKeyChanged(value: String) {
        recoveryKeyInput = value
    }

    /**
     * Step 3: downloads, decrypts, and verifies candidates in order. The
     * only mutation is on success: a candidate decrypting correctly is
     * exactly the "user re-entered the full generated key and it proved
     * correct" event [RecoveryKeyStore.markVerified]'s contract calls for
     * (see that KDoc) — on a replacement phone, this typed-and-proven key is
     * also the only source [RestoreCoordinator.resume] will later have for
     * it, so it must be persisted here, before "Restore" is ever tappable.
     */
    fun checkForBackup() {
        val key = RecoveryKeyCodec.decode(recoveryKeyInput)
        if (key == null) {
            _uiState.value = RestoreUiState.InvalidKeyFormat
            return
        }
        _uiState.value = RestoreUiState.CheckingCandidate
        scope.launch {
            when (val result = RestoreCandidateSelector.select(remoteBackupStore, key)) {
                is CandidateSelectionResult.Found -> {
                    selectedCandidate = result.candidate
                    recoveryKeyStore.save(key)
                    recoveryKeyStore.markVerified()
                    _uiState.value = RestoreUiState.CandidateFound(previewOf(result.candidate.snapshot), result.usedFallbackFrom)
                }
                is CandidateSelectionResult.WrongRecoveryKey -> _uiState.value = RestoreUiState.WrongRecoveryKey
                is CandidateSelectionResult.NoneAvailable -> _uiState.value = RestoreUiState.NoBackupFound
                is CandidateSelectionResult.RemoteError -> _uiState.value = RestoreUiState.RemoteError(result.code)
            }
        }
    }

    /** Step 6(5): the explicit "Restore" confirmation. Nothing happens automatically before this call. */
    fun confirmRestore() {
        val candidate = selectedCandidate ?: return
        _uiState.value = RestoreUiState.Restoring(emptySet())
        scope.launch {
            val coordinator = coordinatorBuilder(context.applicationContext)
            val result = coordinator.beginRestore(
                remoteName = candidate.remoteName,
                envelopeBytes = candidate.envelopeBytes,
                expectedContentHash = candidate.snapshot.contentSha256,
                onStageCompleted = { stage ->
                    val current = _uiState.value
                    val completed = (current as? RestoreUiState.Restoring)?.completedStages ?: emptySet()
                    _uiState.value = RestoreUiState.Restoring(completed + stage)
                },
            )
            _uiState.value = when (result) {
                is RestoreResult.Verified -> RestoreUiState.RestoreComplete(result.contentHash)
                else -> RestoreUiState.RestoreFailed(result)
            }
        }
    }

    private fun previewOf(snapshot: ContinuitySnapshot): RestorePreview = RestorePreview(
        createdAt = snapshot.createdAt,
        appVersionName = snapshot.appVersionName,
        journalEntryCount = snapshot.payload.journalEntries.size,
        morningMeasureCount = snapshot.payload.morningMeasures.size,
        sourceDeviceId = snapshot.sourceDeviceId,
    )
}

private val PREVIEW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

private fun formatCreatedAt(epochMillis: Long): String =
    PREVIEW_DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * Task 11's replacement-phone restore screen. Shows, in order: Google
 * sign-in status, recovery-key entry, the selected candidate's preview
 * (once both are ready), an explicit confirmation button, and per-stage
 * restore progress. NEVER renders any Journal/Notes/Letters body or title
 * text — [RestorePreview] structurally cannot carry any (see its KDoc).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun RestoreScreen(viewModel: RestoreViewModel, onBack: () -> Unit) {
    val signedInEmail by viewModel.signedInEmail.collectAsState(initial = null)
    val state by viewModel.uiState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result -> viewModel.handleSignInResult(result.data) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Restore from backup") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignInStatusSection(
                signedInEmail = signedInEmail,
                onSignIn = { signInLauncher.launch(viewModel.signInIntent()) },
            )
            RecoveryKeySection(
                value = viewModel.recoveryKeyInput,
                onValueChange = viewModel::onRecoveryKeyChanged,
                enabled = signedInEmail != null,
                onCheck = viewModel::checkForBackup,
            )
            RestoreStateSection(state = state, onConfirm = viewModel::confirmRestore)
            TextButton(onClick = onBack, modifier = Modifier.testTag("restore_back_button")) { Text("Back") }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun SignInStatusSection(signedInEmail: String?, onSignIn: () -> Unit) {
    if (signedInEmail == null) {
        Text("Not signed in", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("restore_sign_in_status"))
        Button(onClick = onSignIn, modifier = Modifier.testTag("restore_sign_in_button")) { Text("Sign in with Google") }
    } else {
        Text(
            "Signed in as $signedInEmail",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("restore_sign_in_status"),
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun RecoveryKeySection(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onCheck: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text("Recovery key") },
        // Same reasoning as GoogleDriveBackupSettingsSection's own
        // re-entry field: the base64url payload is case-sensitive, so
        // this field must not let the keyboard autocorrect/capitalize
        // what it sees as nonsense words while the user is typing a
        // key by hand from a written-down copy.
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Ascii,
        ),
        modifier = Modifier.fillMaxWidth().testTag("restore_recovery_key_field"),
    )
    Button(
        onClick = onCheck,
        enabled = enabled && value.isNotBlank(),
        modifier = Modifier.testTag("restore_check_button"),
    ) {
        Text("Check for backup")
    }
}

@Composable
@Suppress("FunctionNaming")
private fun RestoreStateSection(state: RestoreUiState, onConfirm: () -> Unit) {
    when (state) {
        is RestoreUiState.EnterKey -> Unit
        is RestoreUiState.InvalidKeyFormat -> Text("That doesn't look like a valid recovery key.")
        is RestoreUiState.CheckingCandidate -> Text("Checking for a backup…")
        is RestoreUiState.CandidateFound -> CandidatePreviewSection(state, onConfirm)
        is RestoreUiState.WrongRecoveryKey -> Text("Wrong recovery key.", modifier = Modifier.testTag("restore_wrong_key_message"))
        is RestoreUiState.NoBackupFound -> Text("No backup was found for this account.")
        is RestoreUiState.RemoteError -> Text("Couldn't reach Google Drive (${state.code}). Try again.")
        is RestoreUiState.PreflightBlocked -> Text(
            "This phone already has Journal, Notes, or other data on it. " +
                "Create a local encrypted copy of what's on this phone first, " +
                "then come back to restore.",
        )
        is RestoreUiState.Restoring -> RestoreProgressSection(state.completedStages)
        is RestoreUiState.RestoreComplete -> Text(
            "Restore complete. Content hash: ${state.contentHash.take(16)}…",
            modifier = Modifier.testTag("restore_complete_message"),
        )
        is RestoreUiState.RestoreFailed -> Text(
            "Restore did not complete: ${state.result}",
            modifier = Modifier.testTag("restore_failed_message"),
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun CandidatePreviewSection(state: RestoreUiState.CandidateFound, onConfirm: () -> Unit) {
    val preview = state.preview
    Text("Backup from ${formatCreatedAt(preview.createdAt)}", modifier = Modifier.testTag("restore_candidate_created_at"))
    if (state.usedFallbackFrom != null) {
        Text(
            "(Latest was unreadable — using the newest readable backup instead)",
            modifier = Modifier.testTag("restore_candidate_fallback_notice"),
        )
    }
    Text("App version: ${preview.appVersionName}", modifier = Modifier.testTag("restore_candidate_app_version"))
    Text("Journal entries: ${preview.journalEntryCount}", modifier = Modifier.testTag("restore_candidate_entry_count"))
    Text("Morning check-ins: ${preview.morningMeasureCount}", modifier = Modifier.testTag("restore_candidate_measure_count"))
    Text("Source device: ${preview.sourceDeviceId}", modifier = Modifier.testTag("restore_candidate_source_device"))
    Button(onClick = onConfirm, modifier = Modifier.testTag("restore_confirm_button")) { Text("Restore") }
}

@Composable
@Suppress("FunctionNaming")
private fun RestoreProgressSection(completedStages: Set<RestoreStage>) {
    val orderedStages = listOf(
        RestoreStage.DOWNLOADED,
        RestoreStage.DECRYPTED,
        RestoreStage.ROOM_MERGED,
        RestoreStage.DATASTORES_MERGED,
        RestoreStage.VERIFIED,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("restore_progress_section")) {
        for (stage in orderedStages) {
            val mark = if (stage in completedStages) "✓" else "•"
            Text("$mark ${stage.name}", modifier = Modifier.testTag("restore_progress_${stage.name}"))
        }
    }
}
