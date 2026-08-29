package org.mindanchor.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.llm.LlmProvider
import org.mindanchor.ui.Spacing

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LlmSettingsScreen(viewModel: LlmSettingsViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.model.collectAsState()
    val lastTestResult by viewModel.lastTestResult.collectAsState()
    val provider by viewModel.provider.collectAsState()
    val signupUrl by viewModel.signupUrl.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.Edge),
    ) {
        Text(
            text = stringResource(R.string.settings_llm_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = Spacing.Tight),
        )
        Text(
            text = stringResource(R.string.settings_llm_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.Loose),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.Loose),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (p in LlmProvider.values()) {
                val label = if (p.isFree) {
                    "${p.displayName} ✓ Free"
                } else {
                    p.displayName
                }
                FilterChip(
                    selected = provider == p,
                    onClick = { viewModel.setProvider(p) },
                    label = { Text(label) },
                )
            }
        }

        val keyButtonLabel = if (provider.isFree) {
            stringResource(R.string.settings_llm_get_key_free, provider.displayName)
        } else {
            stringResource(R.string.settings_llm_get_key, provider.displayName)
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(signupUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.Loose),
        ) {
            Text(keyButtonLabel)
        }

        ModelPickerRow(
            current = model,
            suggestedModels = provider.suggestedModels,
            onSelect = { viewModel.setModel(it) },
        )
        Spacer(modifier = Modifier.height(Spacing.Loose))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.setApiKey(it) },
            label = { Text(stringResource(R.string.settings_llm_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.Loose))

        SettingsRow(
            label = stringResource(R.string.settings_llm_connection),
            value = if (lastTestResult.testedAtMillis == 0L) {
                stringResource(R.string.settings_llm_never_tested)
            } else {
                lastTestResult.message
            },
            valueColor = if (lastTestResult.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(modifier = Modifier.height(Spacing.Loose))

        OutlinedButton(
            onClick = { viewModel.testConnection() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_llm_test_connection))
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SettingsRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(top = 2.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ModelPickerRow(
    current: String,
    suggestedModels: List<String>,
    onSelect: (String) -> Unit,
) {
    // v0.72+ (2026-08-29) — a provider's suggestedModels can go
    // stale (a model gets deprecated on the provider's side; see
    // LlmProvider.kt's GOOGLE_AI_STUDIO/OPENROUTER entries, both
    // confirmed 404ing as of this date). Previously this field
    // was a closed TextButton + DropdownMenu with no way to enter
    // a model not on the hardcoded list, which left a user with
    // no in-app recovery until a new release shipped updated
    // names. The field is now free-text; the dropdown is a
    // quick-fill for the suggestions, not the only way in.
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = onSelect,
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("Suggestions")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (m in suggestedModels) {
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = {
                            onSelect(m)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
