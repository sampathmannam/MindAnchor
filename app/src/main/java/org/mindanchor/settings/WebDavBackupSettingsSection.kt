package org.mindanchor.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindanchor.R
import org.mindanchor.backup.BackupRepository
import org.mindanchor.backup.EncryptedBackupCodec
import org.mindanchor.backup.WebDavBackupTarget
import org.mindanchor.backup.WebDavCredentialStore

/**
 * The WebDAV backup settings section. v0.23.0.
 *
 * Opt-in: nothing is shown that resembles a default-on
 * toggle. The user adds their own WebDAV folder, tests
 * the connection, and only then sees a "Back up now"
 * button. Removing the configuration forgets the URL,
 * the username, and the app-password; existing remote
 * copies are not touched.
 *
 * The composable is split into a top-level
 * orchestration function (this one) and four
 * sub-composables for the live fields, the
 * upload/forget controls, the restore controls, and
 * the not-yet-configured placeholder. The split is a
 * detekt-shape requirement (the v0.23.0 round 1
 * collapsed function was 207 lines, which the
 * LongMethod rule rejects at threshold 60) and a
 * readability win.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
fun WebDavBackupSettingsSection(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebDavState(context)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader()
        WebDavCredentialForm(state = state)
        state.message?.let { MessageRow(messageRes = it) }
        if (state.isConfigured) {
            ConfiguredActions(state = state)
        } else {
            NotConfiguredHint()
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SectionHeader() {
    Text(
        text = stringResource(R.string.webdav_section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Text(
        text = stringResource(R.string.webdav_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The state holder for the WebDAV section. The fields
 * are bundled into a single object so the sub-composables
 * can take one parameter instead of seven. The
 * [rememberWebDavState] factory wires the callbacks.
 */
private class WebDavState(
    val context: Context,
    val store: WebDavCredentialStore,
    val target: WebDavBackupTarget,
    val repo: BackupRepository,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var url by mutableStateOf(store.read()?.first.orEmpty())
    var username by mutableStateOf(store.read()?.second.orEmpty())
    var password by mutableStateOf(store.read()?.third.orEmpty())
    var passwordVisible by mutableStateOf(false)
    val isConfigured: Boolean get() = store.isConfigured()
    var message by mutableStateOf<Int?>(null)
    var busy by mutableStateOf(false)
    var listing by mutableStateOf(false)
    var remoteList by mutableStateOf<List<WebDavBackupTarget.RemoteBackup>?>(null)

    fun resetForgotten() {
        url = ""
        username = ""
        password = ""
    }

    fun onTest() {
        busy = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                target.testConnection(url.trim(), username.trim(), password)
            }
            message = testResultMessage(result)
            busy = false
        }
    }

    fun onSave() {
        busy = true
        message = null
        scope.launch {
            store.write(url.trim(), username.trim(), password)
            message = R.string.webdav_saved
            busy = false
        }
    }

    fun onBackUpNow() {
        busy = true
        message = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val text = repo.export(now)
                val wrapped = EncryptedBackupCodec.wrap(text) ?: return@withContext false
                val stamp = java.time.LocalDate.now().toString()
                val name = "mindanchor-backup-$stamp.enc"
                val creds = store.read() ?: return@withContext false
                target.put(creds.first, creds.second, creds.third, name, wrapped)
            }
            message = if (ok) R.string.webdav_backup_uploaded else R.string.webdav_upload_failed
            busy = false
        }
    }

    fun onForget() {
        busy = true
        message = null
        scope.launch {
            store.clear()
            resetForgotten()
            message = R.string.webdav_cleared
            busy = false
        }
    }

    fun onList() {
        busy = true
        listing = true
        message = R.string.webdav_restore_listing
        remoteList = null
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                val creds = store.read() ?: return@withContext null
                target.listBackups(creds.first, creds.second, creds.third)
            }
            if (files == null) {
                message = R.string.webdav_restore_failed
            } else if (files.isEmpty()) {
                message = R.string.webdav_restore_empty
            } else {
                message = null
            }
            remoteList = files
            busy = false
            listing = false
        }
    }

    fun onPick(file: WebDavBackupTarget.RemoteBackup) {
        busy = true
        message = null
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                val creds = store.read() ?: return@withContext false
                val bytes = target.get(
                    creds.first,
                    creds.second,
                    creds.third,
                    file.name,
                ) ?: return@withContext false
                val json = EncryptedBackupCodec.unwrap(bytes) ?: return@withContext false
                repo.import(json, System.currentTimeMillis())
            }
            message = if (imported) R.string.backup_restored else R.string.webdav_restore_decrypt_failed
            busy = false
        }
    }
}

@Composable
private fun rememberWebDavState(context: Context): WebDavState {
    val store = remember { WebDavCredentialStore(context) }
    val target = remember { WebDavBackupTarget() }
    val repo = remember { BackupRepository(context) }
    val scope = rememberCoroutineScope()
    return remember { WebDavState(context, store, target, repo, scope) }
}

@Suppress("FunctionNaming")
@Composable
private fun WebDavCredentialForm(state: WebDavState) {
    val canSubmit = !state.busy &&
        state.url.isNotBlank() &&
        state.username.isNotBlank() &&
        state.password.isNotBlank()

    OutlinedTextField(
        value = state.url,
        onValueChange = { state.url = it },
        label = { Text(stringResource(R.string.webdav_url_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
    OutlinedTextField(
        value = state.username,
        onValueChange = { state.username = it },
        label = { Text(stringResource(R.string.webdav_username_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = { state.password = it },
        label = { Text(stringResource(R.string.webdav_password_hint)) },
        singleLine = true,
        visualTransformation = if (state.passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { state.passwordVisible = !state.passwordVisible }) {
                Text(if (state.passwordVisible) "hide" else "show")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(enabled = canSubmit, onClick = state::onTest) {
            Text(stringResource(R.string.webdav_test))
        }
        TextButton(enabled = canSubmit, onClick = state::onSave) {
            Text(stringResource(R.string.webdav_save))
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ConfiguredActions(state: WebDavState) {
    TextButton(enabled = !state.busy, onClick = state::onBackUpNow) {
        Text(stringResource(R.string.webdav_backup_now))
    }
    TextButton(enabled = !state.busy, onClick = state::onForget) {
        Text(stringResource(R.string.webdav_clear))
    }
    RestoreControls(state = state)
}

@Suppress("FunctionNaming")
@Composable
private fun RestoreControls(state: WebDavState) {
    Text(
        text = stringResource(R.string.webdav_restore_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    TextButton(enabled = !state.busy && !state.listing, onClick = state::onList) {
        Text(stringResource(R.string.backup_restore))
    }
    val files = state.remoteList
    if (files != null && files.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (file in files) {
                TextButton(enabled = !state.busy, onClick = { state.onPick(file) }) {
                    Text(file.name)
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun NotConfiguredHint() {
    Text(
        text = stringResource(R.string.webdav_not_configured),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun MessageRow(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun testResultMessage(result: WebDavBackupTarget.TestResult): Int = when (result) {
    WebDavBackupTarget.TestResult.Ok -> R.string.webdav_test_ok
    WebDavBackupTarget.TestResult.Unauthorized -> R.string.webdav_test_unauthorized
    WebDavBackupTarget.TestResult.NotFound -> R.string.webdav_test_not_found
    WebDavBackupTarget.TestResult.Insecure -> R.string.webdav_test_insecure
    is WebDavBackupTarget.TestResult.NetworkError -> R.string.webdav_test_network
}
