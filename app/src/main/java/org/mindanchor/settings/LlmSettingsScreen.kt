package org.mindanchor.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.llm.GroqModels
import org.mindanchor.ui.Spacing

/**
 * The "Daily letter (LLM)" section in Settings → Reading.
 * Inserted between the legacy "Daily letter" section
 * (on-device Phi-4 model) and the "Reading size" section.
 *
 * The section has 5 rows: Provider (single value, "Groq"),
 * Model (3 Groq models), API key (password-masked),
 * Connection (status row, updates only on Test tap), and
 * Test connection (button).
 *
 * The Provider row is a static label — the picker is
 * deliberately hidden because v0.25.7 ships Groq only.
 * The Anthropic entry is reserved for v0.25.8+ and is
 * not shown to the user yet.
 */
@Suppress("FunctionNaming")
@Composable
fun LlmSettingsScreen(viewModel: LlmSettingsViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.model.collectAsState()
    val lastTestResult by viewModel.lastTestResult.collectAsState()
    val provider by viewModel.provider.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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

        // Provider
        SettingsRow(
            label = stringResource(R.string.settings_llm_provider),
            value = provider.name,
        )
        Spacer(modifier = Modifier.height(Spacing.Loose))

        // Model picker
        ModelPickerRow(
            current = model,
            onSelect = { viewModel.setModel(it) },
        )
        Spacer(modifier = Modifier.height(Spacing.Loose))

        // API key
        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.setApiKey(it) },
            label = { Text(stringResource(R.string.settings_llm_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.Loose))

        // Connection status
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

        // Test connection button
        OutlinedButton(
            onClick = { viewModel.testConnection() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_llm_test_connection))
        }
    }
}

/**
 * A label-on-the-left, value-on-the-right row. The
 * value's color is parameterised so the Connection row
 * can render the success/failure/never-tested states
 * in their semantic colors.
 */
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

/**
 * The model dropdown. A [TextButton] opens a
 * [DropdownMenu] listing the three Groq model IDs
 * (see [GroqModels.ALL]). Tapping one fires
 * [onSelect] and collapses the menu.
 */
@Suppress("FunctionNaming")
@Composable
private fun ModelPickerRow(
    current: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Model" is a universally understood term; same shape
        // as the A-/A/A+ labels in the reading-size picker
        // (see SettingsScreen.kt — those are also hardcoded).
        Text(
            text = "Model",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { expanded = true }) {
            Text(current)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (m in GroqModels.ALL) {
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
