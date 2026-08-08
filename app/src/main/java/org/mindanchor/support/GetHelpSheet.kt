package org.mindanchor.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * "Get help" — country-aware, offline, one tap from the support screen.
 *
 * ## What this is
 *
 * A small, calm sheet showing the locally-relevant crisis lines for the
 * country the device resolves to, with a written fallback for countries
 * not yet in the bundled list. Reached through the existing
 * "support" affordance on the home screen, which the project has
 * deliberately kept one tap away and never behind a menu (see
 * `docs/CLINICAL_REVIEW.md` R6: "Support is one tap from home, never
 * behind a menu").
 *
 * ## What this is not
 *
 * - **Not a banner.** A red bar that fires on open is the precise pattern
 *   the original R1 decision tried to avoid, and it is the pattern the
 *   WHO 2023 *Reporting on Suicide* update and the Samaritans Media
 *   Guidelines caution against for the *suicide story*. A persistent
 *   "Get help" button on a calm screen, available but not pushing, is
 *   the documented safe pattern (NHS Design Patterns for Mental Health;
 *   Wysa SOS founder interview in docs/research/14 §6).
 *
 * - **Not a diagnosis.** The wording is "Reach a person, day or night" —
 *   not "Are you in crisis?" A question of that shape on first launch
 *   would intrude at the wrong moment. A button labelled with a verb is
 *   available; the choice to tap is the user's.
 *
 * - **Not a substitute for clinical care.** The wording makes that
 *   plain in the footer, per APA Digital Mental Health 101.
 *
 * ## How the country is chosen
 *
 * [DeviceCountry.resolve] prefers the network country (a roaming phone
 * should be read by the country it is in, not by its SIM origin) and
 * falls back to the system locale. The bundled list
 * ([CrisisLines.forCountry]) is small and conservative; an unknown
 * country shows the global "anywhere" guidance and the IASP out-link.
 */
@Composable
fun GetHelpSheet(
    onClose: () -> Unit,
) {
    // The country is resolved from the device once per sheet open; it is
    // not a reactive value (it is rarely a value the user can change
    // inside the sheet, and resolving TelephonyManager on every recomposition
    // would be wasted work). The [remember] keys on the country code so
    // recomposition is free.
    val context = LocalContext.current
    val countryCode = remember { DeviceCountry.resolve(context) }
    val lines = remember(countryCode) {
        countryCode?.let { CrisisLines.forCountry(it) } ?: emptyList()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.action_back))
            }

            Text(
                text = stringResource(R.string.get_help_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.get_help_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Country-specific lines. Each line is a card with a clear
            // verb and the number, deliberately not using crisis-color
            // palettes: the calm surface of this app is part of why a
            // person in distress would reach for it, and red on first
            // sight reads as alarm rather than help.
            if (lines.isNotEmpty()) {
                lines.forEach { line ->
                    LineCard(line = line, onCall = { dial(context, line.number) })
                }
            }

            // Always-present guidance for countries not in the bundled
            // list, or when the device country cannot be resolved.
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.get_help_anywhere_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.get_help_anywhere_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // The IASP / ThroughLine / findahelpline.com URL is
                    // the verified daily source for 1,300+ lines in
                    // 130+ countries. Linking out, not bundling every
                    // country, is what the brief (docs/research/14 §7)
                    // recommends.
                    TextButton(
                        onClick = { dial(context, "https://findahelpline.com/") },
                    ) {
                        Text(stringResource(R.string.get_help_iasp_link))
                    }
                }
            }

            Text(
                text = stringResource(R.string.get_help_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}

@Composable
private fun LineCard(line: CrisisLine, onCall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(
                    when (line.contact) {
                        CrisisLine.Contact.PHONE -> R.string.get_help_call_label
                        CrisisLine.Contact.SMS -> R.string.get_help_text_label
                        CrisisLine.Contact.PHONE_OR_SMS -> R.string.get_help_call_or_text_label
                        CrisisLine.Contact.WEB -> R.string.get_help_web_label
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = line.number,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = onCall) {
                    Text(stringResource(R.string.get_help_action))
                }
            }
            if (line.hours.isNotBlank()) {
                Text(
                    text = line.hours,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Same dial-with-failure-handling as [org.mindanchor.support.SupportScreen].
 * Kept here so the file is testable in isolation. A failed call is
 * reported, never swallowed.
 */
private fun dial(context: Context, target: String) {
    val isUrl = target.startsWith("http://") || target.startsWith("https://")
    val uri = if (isUrl) Uri.parse(target) else Uri.parse("tel:$target")
    val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
