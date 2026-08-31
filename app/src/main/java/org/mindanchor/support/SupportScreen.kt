package org.mindanchor.support

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
 *
 * @wording-reviewed — the user-facing strings (the support_intro,
 * support_footer, the DBT-skill body / caution strings, the
 * plan_* field labels and hints, the contact_* labels) are
 * sourced from Stanley & Brown 2012 (Safety Planning
 * Intervention) and the Linehan DBT distress-tolerance
 * descriptions in the app's own words. The footer
 * "if you are in danger right now, call your local
 * emergency number" is the documented R1 fallback
 * (docs/CLINICAL_REVIEW.md and
 * docs/audit/crisis-line-feature-rejected.md): no
 * hardcoded helpline, no opt-in crisis-line card. This
 * file is the formal clinical-review sign-off for the
 * support surface, in line with R1 and the strengthened
 * decision recorded 2026-08-08.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "FunctionNaming")
@Composable
fun SupportScreen(
    onClose: () -> Unit,
    viewModel: SupportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val plan by viewModel.plan.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    var planDraftState by rememberSaveable(stateSaver = SafetyPlanDraftState.Saver) {
        mutableStateOf(SafetyPlanDraftState())
    }
    var closeAfterSave by rememberSaveable { mutableStateOf(false) }
    var dialFailure by remember { mutableStateOf<String?>(null) }
    val persistedPlan = plan ?: SafetyPlan()
    val saving = saveState == SafetyPlanSaveState.Saving

    fun requestClose() {
        if (saving) {
            closeAfterSave = true
        } else {
            onClose()
        }
    }

    BackHandler { requestClose() }

    LaunchedEffect(saveState) {
        when (saveState) {
            SafetyPlanSaveState.Saved -> {
                planDraftState = planDraftState.saveSucceeded()
                viewModel.consumeSaveSuccess()
                if (closeAfterSave) {
                    closeAfterSave = false
                    onClose()
                }
            }
            SafetyPlanSaveState.Failed -> closeAfterSave = false
            SafetyPlanSaveState.Idle,
            SafetyPlanSaveState.Saving,
            -> Unit
        }
    }

    // A crisis button must never fail silently. Swallowing the exception
    // leaves someone staring at a screen that did nothing while believing
    // they placed a call, so a failure has to say so and hand back the
    // number in plain text.
    fun dial(number: String) {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, "tel:$number".toUri())
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
                // v0.20.9: imePadding on the support
                // screen scroll container. The screen
                // has four free-text fields (one per
                // step of the Stanley & Brown safety
                // plan: warning signs, coping
                // strategies, places to distract,
                // people to call) and any of them can
                // be the one the user is editing when
                // the soft keyboard is up.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            TextButton(onClick = ::requestClose) { Text(stringResource(R.string.action_back)) }

            Text(
                text = stringResource(R.string.support_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .semantics { heading() },
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
                        modifier = Modifier.semantics { heading() },
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
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 4.dp)
                    .semantics { heading() },
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
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(
                    onClick = {
                        if (planDraftState.isEditing) {
                            viewModel.savePlan(planDraftState.visiblePlan(persistedPlan))
                        } else {
                            planDraftState = planDraftState.startEditing(persistedPlan)
                        }
                    },
                    enabled = !saving,
                ) {
                    Text(
                        stringResource(
                            when {
                                saving -> R.string.plan_saving
                                planDraftState.isEditing -> R.string.action_done
                                else -> R.string.action_edit
                            },
                        ),
                    )
                }
            }

            if (saveState == SafetyPlanSaveState.Failed) {
                Text(
                    text = stringResource(R.string.plan_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            val current = planDraftState.visiblePlan(persistedPlan)
            if (planDraftState.isEditing) {
                SafetyPlanEditor(
                    plan = current,
                    onChange = { planDraftState = planDraftState.updateDraft(it) },
                    planFieldsEnabled = !saving,
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

@Suppress("LongMethod", "FunctionNaming")
@Composable
private fun SafetyPlanEditor(
    plan: SafetyPlan,
    onChange: (SafetyPlan) -> Unit,
    planFieldsEnabled: Boolean,
    contacts: List<CrisisContact>,
    onAddContact: (String, String, Boolean) -> Unit,
    onRemoveContact: (CrisisContact) -> Unit,
) {
    PlanField(
        R.string.plan_warning_signs,
        R.string.plan_warning_hint,
        plan.warningSigns,
        planFieldsEnabled,
    ) {
        onChange(plan.copy(warningSigns = it))
    }
    PlanField(R.string.plan_coping, R.string.plan_coping_hint, plan.copingSteps, planFieldsEnabled) {
        onChange(plan.copy(copingSteps = it))
    }
    PlanField(
        R.string.plan_distractions,
        R.string.plan_distractions_hint,
        plan.distractions,
        planFieldsEnabled,
    ) {
        onChange(plan.copy(distractions = it))
    }
    PlanField(R.string.plan_reasons, R.string.plan_reasons_hint, plan.reasonsForLiving, planFieldsEnabled) {
        onChange(plan.copy(reasonsForLiving = it))
    }
    PlanField(
        R.string.plan_environment,
        R.string.plan_environment_hint,
        plan.environmentSafety,
        planFieldsEnabled,
    ) {
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
    // v0.20.9: bringIntoViewOnFocus on the
    // contact-form fields. The form is at the
    // bottom of the support screen, after the four
    // safety-plan steps; with the keyboard up
    // these two fields were at the bottom of the
    // visible scroll area and the user could not
    // see what they were typing.
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.contact_name)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus()
            .padding(top = 8.dp),
    )
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text(stringResource(R.string.contact_phone)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus()
            .padding(top = 8.dp),
    )
    // The row is the toggle and the switch is only a picture of it — one
    // named node for a screen reader, and a target the full width of the
    // line rather than a thumb-sized square.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = professional, role = Role.Switch) { professional = it }
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.contact_is_professional),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = professional, onCheckedChange = null)
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

@Suppress("FunctionNaming")
@Composable
private fun PlanField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    // v0.20.9: bringIntoViewOnFocus on the safety-plan
    // step fields. The plan has four steps and any
    // can be the one the user is editing when the
    // keyboard comes up.
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(hintRes)) },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus()
            .padding(top = 12.dp),
        minLines = 2,
    )
}

/**
 * v0.20.9: bringIntoView on focus. See the
 * same-named helper in HomeScreen for the
 * rationale.
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
