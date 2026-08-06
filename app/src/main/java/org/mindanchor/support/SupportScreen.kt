package org.mindanchor.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mindanchor.R
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyPlan

/**
 * Support: crisis contacts, a safety plan, and a few skills — one tap from
 * the home screen.
 *
 * Shaped by the Stanley & Brown safety planning intervention, which is the
 * best-evidenced brief tool in suicide prevention and asks a person to
 * write, while calm, what they want to do when they are not. Everything
 * here is stored on the device only, works with no network, and reaches
 * real humans rather than trying to be one.
 */
@Composable
fun SupportScreen(
    onClose: () -> Unit,
    viewModel: SupportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val plan by viewModel.plan.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var dialFailure by remember { mutableStateOf<String?>(null) }

    // A crisis button must never fail silently. Swallowing the exception
    // leaves someone staring at a screen that did nothing while believing
    // they placed a call, so a failure has to say so and hand back the
    // number in plain text.
    fun dial(number: String) {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        dialFailure = if (opened) null else number
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }

            Text(
                text = stringResource(R.string.support_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.support_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Reach someone, first and without scrolling ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.support_reach_someone),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    contacts.forEach { contact ->
                        TextButton(
                            onClick = { dial(contact.phone) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (contact.name.isBlank()) {
                                    contact.phone
                                } else {
                                    "${contact.name} · ${contact.phone}"
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (contacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.support_no_contacts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // The reader's own country first. Nothing is hidden on
                    // the strength of a locale guess — people travel, borrow
                    // phones, and set their region to somewhere they do not
                    // live — so the rest stay listed underneath.
                    val country = LocalConfiguration.current.locales[0]?.country
                    CrisisLines.forCountry(country).forEach { line ->
                        TextButton(
                            onClick = { dial(line.number) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(line.labelRes), modifier = Modifier.weight(1f))
                        }
                    }
                    Text(
                        text = stringResource(R.string.crisis_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    dialFailure?.let { number ->
                        Text(
                            text = stringResource(R.string.dial_failed, number),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // --- Skills for right now ---
            Text(
                text = stringResource(R.string.skills_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.skills_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The caution belongs with TIPP specifically. Two of its four
            // steps — cold water and hard movement — swing heart rate
            // sharply on purpose, which is exactly why they work and
            // exactly why they are not for everyone. Presenting them with
            // no caveat was the one place this screen asked something
            // physical of a person without saying who should not do it.
            listOf(
                Triple(R.string.skill_stop_title, R.string.skill_stop_body, null),
                Triple(
                    R.string.skill_tipp_title,
                    R.string.skill_tipp_body,
                    R.string.skill_tipp_caution,
                ),
                Triple(R.string.skill_grounding_title, R.string.skill_grounding_body, null),
            ).forEach { (title, body, caution) ->
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                caution?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // --- The plan ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.plan_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editing = !editing }) {
                    Text(
                        stringResource(
                            if (editing) R.string.action_done else R.string.action_edit,
                        ),
                    )
                }
            }

            val current = plan ?: SafetyPlan()
            if (editing) {
                SafetyPlanEditor(
                    plan = current,
                    onChange = viewModel::savePlan,
                    contacts = contacts,
                    onAddContact = viewModel::addContact,
                    onRemoveContact = viewModel::removeContact,
                )
            } else {
                SafetyPlanReader(current)
            }

            Text(
                text = stringResource(R.string.support_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}

@Composable
private fun SafetyPlanReader(plan: SafetyPlan) {
    if (plan.isEmpty) {
        Text(
            text = stringResource(R.string.plan_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    listOf(
        R.string.plan_warning_signs to plan.warningSigns,
        R.string.plan_coping to plan.copingSteps,
        R.string.plan_distractions to plan.distractions,
        R.string.plan_reasons to plan.reasonsForLiving,
        R.string.plan_environment to plan.environmentSafety,
    ).forEach { (label, value) ->
        if (value.isNotBlank()) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SafetyPlanEditor(
    plan: SafetyPlan,
    onChange: (SafetyPlan) -> Unit,
    contacts: List<CrisisContact>,
    onAddContact: (String, String, Boolean) -> Unit,
    onRemoveContact: (CrisisContact) -> Unit,
) {
    PlanField(R.string.plan_warning_signs, R.string.plan_warning_hint, plan.warningSigns) {
        onChange(plan.copy(warningSigns = it))
    }
    PlanField(R.string.plan_coping, R.string.plan_coping_hint, plan.copingSteps) {
        onChange(plan.copy(copingSteps = it))
    }
    PlanField(R.string.plan_distractions, R.string.plan_distractions_hint, plan.distractions) {
        onChange(plan.copy(distractions = it))
    }
    PlanField(R.string.plan_reasons, R.string.plan_reasons_hint, plan.reasonsForLiving) {
        onChange(plan.copy(reasonsForLiving = it))
    }
    PlanField(R.string.plan_environment, R.string.plan_environment_hint, plan.environmentSafety) {
        onChange(plan.copy(environmentSafety = it))
    }

    Text(
        text = stringResource(R.string.plan_contacts),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    contacts.forEach { contact ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${contact.name} · ${contact.phone}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onRemoveContact(contact) }) {
                Text(stringResource(R.string.action_remove))
            }
        }
    }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var professional by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.contact_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text(stringResource(R.string.contact_phone)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.contact_is_professional),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = professional, onCheckedChange = { professional = it })
    }
    // Disabled rather than silently refusing: a tap that does nothing
    // reads as a broken app, and this screen cannot afford to look broken.
    TextButton(
        onClick = {
            onAddContact(name, phone, professional)
            name = ""
            phone = ""
            professional = false
        },
        enabled = phone.isNotBlank(),
    ) {
        Text(stringResource(R.string.contact_add))
    }
    if (phone.isBlank()) {
        Text(
            text = stringResource(R.string.contact_needs_number),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = stringResource(R.string.contact_bypass_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlanField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(hintRes)) },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        minLines = 2,
    )
}
