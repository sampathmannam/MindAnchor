package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The v0.28+ (Phase 3 G-31) plain-language data-flow
 * card.
 *
 * The card is the home-surface / Settings → About
 * surface for the privacy contract the project
 * promises. The full doc-track is in
 * `docs/CLINICAL_REVIEW.md` §7; this Composable
 * surfaces the same content in the in-app surface
 * the user can reach in one tap.
 *
 * ## Why the four sections, in this order
 *
 *  1. **What the app holds.** The user reads this
 *     first because it is the question they ask
 *     before "where does it go" (Pew 2017 *Americans
 *     and Privacy*; the project's own research/04
 *     privacy survey).
 *  2. **Where the data goes.** The phone, the VPN
 *     interface (no tunnel), the screen. Explicit
 *     on-device, not implied.
 *  3. **Where the data does not go.** The negative
 *     list closes the privacy surface (no cloud backup
 *     unless Google Drive backup is turned on — and even
 *     then, never the safety plan or crisis contacts —
 *     no analytics, no device-to-device transfer, no
 *     LLM call unless the user opts in).
 *  4. **What the user can do.** Delete everything;
 *     export the on-device log. The two affordances
 *     that make the negative list actionable.
 *
 * The four-section structure is the same one
 * `docs/CLINICAL_REVIEW.md` §7 uses; the Composable
 * is the in-app surface, the doc is the
 * version-controlled surface.
 */
@Composable
fun PrivacyFlowCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Your data, in plain language",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PrivacySection(
                title = "What the app holds on the device",
                body = "Your suicide safety plan and the phone numbers of the people you would call at your worst. " +
                    "The text of the notifications you have read or chosen to read. " +
                    "Your mood history and the WHO-5 responses. The letters you have written, the notes you have saved, " +
                    "the open cognitive loops you have parked. Wearable data the launcher reads from Health Connect " +
                    "(heart rate, sleep, HRV, steps, mindfulness minutes) — read only, never written back. " +
                    "The local-only decisions the launcher has made for you (per-app session lengths, if-then plans, " +
                    "batched-notification schedule, Going Light windows).",
            )
            PrivacySection(
                title = "Where the data goes",
                body = "The phone, by default. Every byte of the above lives on the device, in the app's private storage, " +
                    "encrypted with the Android Keystore. Backup is off unless you turn on Google Drive backup " +
                    "(Settings → Reading, opt-in) — that syncs your notes, letters, check-ins and wellness readings " +
                    "to your own Drive each night; your safety plan and crisis contacts never go, even then. " +
                    "Device-to-device transfer is refused. " +
                    "The Going Light VPN captures loopback traffic and decides forward-or-drop per packet, locally; " +
                    "the loopback interface is the only place the captured packet goes. " +
                    "Everything you see is rendered from local data.",
            )
            PrivacySection(
                title = "Where the data does not go",
                body = "The phone's network by default (the INTERNET permission is held only because the VpnService API " +
                    "requires it; the runtime telemetry confirms zero outbound bytes unless you've opted into Google " +
                    "Drive backup or the COROS bridge). Your safety plan and crisis contacts, full stop — those never " +
                    "leave the phone, even with Google Drive backup on. An analytics service. " +
                    "A device-to-device transfer. There is no on-device model or LLM of any kind running on the " +
                    "phone: the daily letter's writing is opt-in and cloud-only, silent until you add a provider " +
                    "key in Settings → Reading — no key, no outbound call, ever.",
            )
            PrivacySection(
                title = "What you can do",
                body = "Settings → About → \"Delete all my data\" wipes the app's private storage and the wearable cache. " +
                    "Settings → About → \"Share diagnostic log\" produces a redacted text file (phone numbers, " +
                    "emails, and held-notification bodies are scrubbed before share). " +
                    "The wellness tool, not a medical device, is the project's standing rule.",
            )
            Text(
                text = "Full text in docs/PRIVACY.md. Updated 2026-08-24.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
    )
}
