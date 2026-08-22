/*
 * v0.67.0 — in-app Privacy Policy screen.
 *
 * The previous v0.66.x build had no privacy policy surface
 * at all. The data-extraction rules in
 * `app/src/main/res/xml/data_extraction_rules.xml` declared
 * the on-device scope, but there was no plain-language page
 * the user could open. For a mental-health surface that holds
 * a suicide safety plan and the phone numbers of the people
 * someone would call at their worst, "no policy" is a real
 * gap, not a paperwork one.
 *
 * The screen is intentionally simple: a single scrollable
 * column of long-form text. No state, no settings, no
 * toggles. The user reads it once (or not at all) and
 * navigates back. v0.67.0 is in-app only — there is no public
 * URL yet. The "no public URL" choice is deliberate for a
 * private-R&D pilot: the v0.66.x build is shared with
 * clinicians, not the Play Store, and a published URL is
 * only meaningful once the deployment target is public.
 *
 * The text is in the v0.66.x code (not in `strings.xml`)
 * because the policy is the surface's contract, not a
 * translatable string. The Tamil / Hindi / Kannada
 * localisation passes (v0.67.0 item D-6) leave this file
 * as-is — the policy is a developer-facing artefact, not a
 * user-facing label, and the v0.66.x build only has the
 * one-language audience to read it for now.
 *
 * BPD-safety invariants in the text:
 *   - Says plainly "MindAnchor is a personal R&D tool, not
 *     a substitute for therapy."
 *   - Lists the four crisis lines (iCall, Vandrevala,
 *     AASRA, Tele-MANAS) so the user can verify what they
 *     see on the Today surface.
 *   - Does not make any clinical claim.
 *   - Does not say "your data is private" without
 *     enumerating what the data is.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Privacy Policy surface. Reached from
 * `JournalSettings → About → Privacy & data`.
 *
 * The screen is intentionally BPD-quiet: paper-coloured
 * background, no banners, no call-to-action. The user is
 * here to read a contract, not to take an action.
 */
@Composable
internal fun JournalPrivacyPolicy(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsPrefs = remember { JournalSettingsPrefs(context) }
    val displayName by settingsPrefs.displayName
        .collectAsStateWithLifecycle(initialValue = "")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaperCard),
    ) {
        // Top bar — back arrow + small "PRIVACY" wordmark. The
        // wordmark is the same Terracotta + JournalSerif small
        // caps the rest of the journal surfaces use, so the
        // privacy screen feels like a continuation of the
        // journal, not a separate "legal" surface.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                ChevronLeftGlyph(color = Ink.copy(alpha = 0.30f))
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "PRIVACY",
                style = JournalSmallCaps,
                color = Terracotta,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PolicyHeader(
                title = "Privacy and data",
                subtitle = "What MindAnchor stores, who can see it, and what leaves the device.",
            )

            PolicySection(
                heading = "1. On this device only",
                body = "MindAnchor holds a suicide safety plan, the phone numbers of the people you would call at your worst, a mood history, and the text of any held notifications. None of this is sent to a server. MindAnchor has no backend, no account system, and no analytics.",
            )
            PolicySection(
                heading = "2. Cloud backup is off",
                body = "Auto-backup to Google Drive and device-to-device transfer are both disabled in code. A safety plan and the names of crisis contacts are not the kind of record that should travel to a new phone without your action.",
            )
            PolicySection(
                heading = "3. The PDF you can share is your choice",
                body = "MindAnchor can render the last 14 days of diary, mood, and skill entries to a single PDF on this device, then hand it to a sharing app of your choice. The file never leaves the device until you tap a recipient. The recipient sees the same disclaimer you see on the first page of the PDF.",
            )
            PolicySection(
                heading = "4. The four crisis numbers",
                body = "The crisis numbers on the Today surface and in the PDF are hard-coded: iCall 9152987821 (Mon-Sat 8am-10pm, TISS Mumbai), Vandrevala 18602662362 (24/7, multilingual), AASRA 9820466726 (24/7, suicide prevention), Tele-MANAS 14416 (24/7, 20 languages, Govt. of India). Long-press to dial — the call is not placed without you pressing the green button.",
            )
            PolicySection(
                heading = "5. Permissions you have granted",
                body = "The app holds permissions for: notifications (so reminders can fire), exact alarms (so a user-scheduled reminder fires at the right time), Health Connect (so a paired wearable can read its own data back into the journal), and Bluetooth (so a paired heart-rate strap can talk to the connector). Every permission is requested only when the matching surface is used, and every one of them can be revoked from Android Settings at any time.",
            )
            PolicySection(
                heading = "6. This is not a clinical tool",
                body = "MindAnchor is a personal R&D tool, not a substitute for therapy. The DBT-grounded skills (TIPP, DEAR MAN, S.T.O.P., 3-Minute Breathing Space, Wise Mind) are drawn from publicly available protocols and are not adjusted to a clinical picture. The mood scale (Crushed / Heavy / Steady / Light / Bright) is a self-report vocabulary, not a diagnostic.",
            )
            PolicySection(
                heading = "7. If you delete the app",
                body = "Uninstalling MindAnchor removes the app's data from this device. There is no cloud copy to delete (see §2). The PDF you shared is on the recipient's device — that is between you and them.",
            )

            if (displayName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PolicySection(
                    heading = "Your display name",
                    body = "The app uses \"$displayName\" in the file name of the export PDF and as the data subject on this device. You can change it from Settings → Display name.",
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "This text is the contract. It is not a published URL because the v0.67.0 build is a private R&D pilot, not a public release. The same text is in `app/src/main/java/org/mindanchor/journal/JournalPrivacyPolicy.kt` for review.",
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                ),
                color = Ink.copy(alpha = 0.45f),
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PolicyHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Light,
                fontSize = 28.sp,
                letterSpacing = 4.sp,
            ),
            color = Terracotta,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
            ),
            color = Ink.copy(alpha = 0.60f),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PolicySection(heading: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = heading,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            ),
            color = Ink,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            color = Ink.copy(alpha = 0.80f),
        )
    }
}

/**
 * v0.67.0: removed — the shim above did not actually
 * resolve the right overload (the lifecycle-compose
 * `collectAsStateWithLifecycle` is an extension on `Flow<T>`
 * with a different signature, not the shim-friendly
 * `(T) -> State<T>` shape). Replaced with the direct call
 * site below.
 */
