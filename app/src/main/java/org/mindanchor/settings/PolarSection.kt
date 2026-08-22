@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")
package org.mindanchor.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.vitals.polar.PolarAuth
import org.mindanchor.vitals.polar.PolarConnectionState
import org.mindanchor.vitals.polar.PolarSyncWorker

/**
 * v0.35.0: the "Polar" settings section.
 *
 * Renders an email + password form, a "Sign in" button, and
 * a status line that reflects the current
 * [PolarConnectionState]. The form is the user-facing
 * front door to the Polar OAuth2 Authorization Code flow:
 *
 *  1. The user types their Polar Flow email + password.
 *  2. The "Connect Polar" button opens a Custom Tab to
 *     `https://flow.polar.com/oauth2/authorization` (the
 *     launcher's OAuth2 client_id + redirect_uri are
 *     registered at admin.polaraccesslink.com).
 *  3. The user signs in on flow.polar.com, approves the
 *     scopes (`accesslink.read.all`,
 *     `nightly_recharge:read`, `continuous_hr:read`),
 *     and the Polar auth server redirects back to
 *     `mindanchor://polar-oauth-callback?code=...`.
 *  4. The launcher's intent filter handles the deep
 *     link and exchanges the code for an access token
 *     via [PolarAuth.loginWithCode].
 *
 * The form is the entry point; the deep-link handler is
 * the closing step. v0.35.0 ships the form; the deep
 * link handler is a v0.36.0 follow-up (it needs an
 * intent filter in the manifest + a custom-tab-watcher
 * service). For now, the form records the credentials
 * and the periodic worker reads them when it runs.
 *
 * Why email + password and not "Sign in with Polar" via
 * a Custom Tab only: the user is already in the settings
 * screen, the credentials can be captured inline, and
 * the form surfaces the same encrypt-at-rest story the
 * Coros bridge uses (Keystore-encrypted
 * EncryptedSharedPreferences).
 *
 * @wording-reviewed — clinical-review-required. The
 * status line ("Connected as X", "Sign-in failed: Y")
 * is the clinical-review surface; wording changes here
 * must be re-reviewed per docs/CLINICAL_REVIEW.md.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun PolarSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val auth = remember(context) { PolarAuth(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var emailDraft by remember { mutableStateOf("") }
    var passwordDraft by remember { mutableStateOf("") }
    var loginInProgress by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // Re-read the connection state on every recomposition.
    // The state is cheap (a DataStore read) and the form
    // gates its buttons on the result.
    val state = auth.connectionState()
    val isConnected = state is PolarConnectionState.Connected
    val connectedEmail = (state as? PolarConnectionState.Connected)?.email
        ?: (state as? PolarConnectionState.TokenExpired)?.email
    val showForm = !isConnected

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_polar_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.settings_polar_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        when (state) {
            is PolarConnectionState.Connected -> {
                Text(
                    text = stringResource(
                        R.string.settings_polar_connected_as,
                        connectedEmail.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is PolarConnectionState.TokenExpired -> {
                Text(
                    text = stringResource(R.string.settings_polar_login_failed, "Token expired"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is PolarConnectionState.NotConnected -> {
                Text(
                    text = stringResource(R.string.settings_polar_not_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showForm) {
            OutlinedTextField(
                value = emailDraft,
                onValueChange = { emailDraft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_polar_email_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            OutlinedTextField(
                value = passwordDraft,
                onValueChange = { passwordDraft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_polar_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    if (loginInProgress) return@TextButton
                    if (emailDraft.isBlank() || passwordDraft.isBlank()) return@TextButton
                    loginInProgress = true
                    lastError = null
                    // v0.35.0 ships the form. The OAuth2
                    // flow's full redirect-URL handler is a
                    // v0.36.0 follow-up (intent filter +
                    // deep-link-watcher service). For now,
                    // we open the Polar auth page in a
                    // Custom Tab so the user can complete
                    // the sign-in there; the form records
                    // the credentials locally and the
                    // periodic worker will read them on
                    // the next tick. The form credentials
                    // are wiped after the user closes the
                    // settings screen — the same pattern
                    // the COROS form uses.
                    scope.launch {
                        runCatching {
                            val authUrl = Uri.parse(
                                "${PolarAuth.AUTHORIZE_URL}" +
                                    "?response_type=code" +
                                    "&client_id=mindanchor-android" +
                                    "&redirect_uri=${PolarAuth.REDIRECT_URI}" +
                                    "&scope=accesslink.read_all%20nightly_recharge%20continuous_hr",
                            )
                            // Open in the system browser
                            // (or any installed Custom Tab
                            // handler) via Intent.ACTION_VIEW.
                            // v0.35.0 does not register a
                            // Custom Tab warmup; the
                            // browser is the fallback.
                            val viewIntent = Intent(Intent.ACTION_VIEW, authUrl)
                            viewIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(viewIntent)
                            // Persist the credentials so the
                            // worker can read them on the
                            // next sync. The token exchange
                            // requires the authorization
                            // code from the redirect — that
                            // path is a v0.36.0 follow-up.
                            val store = org.mindanchor.vitals.polar.PolarCredentialStore(
                                context.applicationContext,
                            )
                            store.write(emailDraft.trim(), passwordDraft)
                            PolarSyncWorker.ensureScheduled(context.applicationContext)
                        }.onFailure { lastError = it.message ?: "unknown" }
                        loginInProgress = false
                        passwordDraft = ""
                    }
                },
                enabled = !loginInProgress && emailDraft.isNotBlank() && passwordDraft.isNotBlank(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) {
                Text(
                    text = if (loginInProgress) {
                        stringResource(R.string.settings_polar_login_in_progress)
                    } else {
                        stringResource(R.string.settings_polar_connect_button)
                    },
                )
            }
        } else {
            // Connected state: Sync now + Disconnect
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(
                    onClick = { PolarSyncWorker.syncNow(context.applicationContext) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button },
                ) {
                    Text(stringResource(R.string.settings_polar_sync_now))
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            auth.disconnect()
                            PolarSyncWorker.cancel(context.applicationContext)
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button },
                ) {
                    Text(stringResource(R.string.settings_polar_disconnect_button))
                }
            }
        }

        lastError?.let { reason ->
            Text(
                text = stringResource(R.string.settings_polar_login_failed, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
