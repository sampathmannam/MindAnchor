@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming", "LongMethod")
package org.mindanchor.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R

/**
 * v0.29.0: the ACT values clarification surface (Hayes et al.
 * 1999/2004; Wilson & Murrell 2004). The user writes one
 * sentence per value domain — "in this corner of my life,
 * what do I want it to be about?"
 *
 * ## What this is and is not
 *
 * The card is the standard ACT values clarification exercise
 * — the 8-domain taxonomy is what every ACT workbook uses.
 * The values are *chosen life directions*, not goals (a
 * value is not something you finish, it is something you
 * keep doing). The screen is for *the moment when a person
 * has time to think about what they want their life to be*
 * (per docs/research/14-v0.26.6-audit.md §3.5), not a crisis
 * surface. The Support group places it after the in-the-moment
 * skills and the reflective practices, last.
 *
 * The card is saved on a single Save tap, then the user can
 * revisit the surface and see their previously-written
 * values. The draft is auto-loaded from [ValuesPrefs] on
 * first composition so the user never has to re-type what
 * they already wrote.
 *
 * ## BPD-safety
 *
 * No directive language. No judgment on the user's words.
 * The placeholder is the same shape as the values the user
 * writes — "in this corner of my life, I want to be the kind
 * of person who..." — so the affordance is the same as the
 * answer, not a checklist. All fields are optional; the Save
 * button saves what is there. The Done button dismisses
 * without consequence.
 */
@Composable
fun ValuesScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { ValuesPrefs(context.applicationContext) }

    var relationships by rememberSaveable { mutableStateOf("") }
    var health by rememberSaveable { mutableStateOf("") }
    var work by rememberSaveable { mutableStateOf("") }
    var growth by rememberSaveable { mutableStateOf("") }
    var leisure by rememberSaveable { mutableStateOf("") }
    var spirituality by rememberSaveable { mutableStateOf("") }
    var community by rememberSaveable { mutableStateOf("") }
    var parenting by rememberSaveable { mutableStateOf("") }

    var savedMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // v0.29.0: load the saved card on first composition so the
    // user sees their previously-written values. The
    // LaunchedEffect runs once per composition, not on every
    // recomposition, so the load is not retriggered by the
    // user's edits. The eight field-level rememberSaveable
    // are the source of truth for the in-flight draft — the
    // LaunchedEffect only writes to them once, at start, and
    // only if they are still empty (i.e. the user has not
    // started editing).
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!loaded) {
            val saved = prefs.load()
            if (relationships.isEmpty()) relationships = saved.relationships
            if (health.isEmpty()) health = saved.health
            if (work.isEmpty()) work = saved.work
            if (growth.isEmpty()) growth = saved.growth
            if (leisure.isEmpty()) leisure = saved.leisure
            if (spirituality.isEmpty()) spirituality = saved.spirituality
            if (community.isEmpty()) community = saved.community
            if (parenting.isEmpty()) parenting = saved.parenting
            loaded = true
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "ACT values clarification. " +
                    "Eight domains: relationships, health, work, growth, leisure, " +
                    "spirituality, community, parenting. One sentence each. " +
                    "Save at the bottom."
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onDone,
                modifier = Modifier.semantics { role = Role.Button },
            ) { Text(stringResource(R.string.action_back)) }

            Text(
                text = stringResource(R.string.values_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.values_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ValueField(
                labelRes = R.string.values_relationships_label,
                hintRes = R.string.values_field_hint,
                value = relationships,
                onValueChange = { relationships = it },
            )
            ValueField(
                labelRes = R.string.values_health_label,
                hintRes = R.string.values_field_hint,
                value = health,
                onValueChange = { health = it },
            )
            ValueField(
                labelRes = R.string.values_work_label,
                hintRes = R.string.values_field_hint,
                value = work,
                onValueChange = { work = it },
            )
            ValueField(
                labelRes = R.string.values_growth_label,
                hintRes = R.string.values_field_hint,
                value = growth,
                onValueChange = { growth = it },
            )
            ValueField(
                labelRes = R.string.values_leisure_label,
                hintRes = R.string.values_field_hint,
                value = leisure,
                onValueChange = { leisure = it },
            )
            ValueField(
                labelRes = R.string.values_spirituality_label,
                hintRes = R.string.values_field_hint,
                value = spirituality,
                onValueChange = { spirituality = it },
            )
            ValueField(
                labelRes = R.string.values_community_label,
                hintRes = R.string.values_field_hint,
                value = community,
                onValueChange = { community = it },
            )
            ValueField(
                labelRes = R.string.values_parenting_label,
                hintRes = R.string.values_field_hint,
                value = parenting,
                onValueChange = { parenting = it },
            )

            TextButton(
                onClick = {
                    val card = ValuesCard(
                        relationships = relationships,
                        health = health,
                        work = work,
                        growth = growth,
                        leisure = leisure,
                        spirituality = spirituality,
                        community = community,
                        parenting = parenting,
                    )
                    scope.launch {
                        prefs.save(card)
                        savedMessage = "Saved. Read it again when you want to."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = 16.dp)
                    .semantics { role = Role.Button },
            ) { Text(stringResource(R.string.values_save)) }

            savedMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(hintRes)) },
        modifier = Modifier.fillMaxWidth(),
    )
}
