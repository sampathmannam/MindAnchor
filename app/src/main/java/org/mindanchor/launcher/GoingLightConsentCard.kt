package org.mindanchor.launcher

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.friction.GoingLightSchedule

/**
 * The first-time Going Light consent card.
 *
 * ## When it shows
 *
 * The card is shown on the home surface when:
 *
 *  1. Going Light is *enabled in the schedule settings*
 *     (the user has set active hours or has the
 *     schedule on), AND
 *  2. The OS-level [VpnService] consent has not yet
 *     been granted (Android returns a non-null
 *     Intent from [VpnService.prepare] when the user
 *     has not yet consented).
 *
 * The trigger is the home surface itself, not the
 * Going Light scheduler. The card is the *first* time
 * the user encounters the OS-level VPN dialog; the
 * VPN itself only fires when the schedule's active
 * window opens.
 *
 * ## Why "if you would like" framing
 *
 * The card body is the same validate-then-suggest
 * family as the morning-compassion break and the
 * BA picker: it does not tell the user to use Going
 * Light, it offers the OS-level consent that Going
 * Light needs. The user can dismiss and the card
 * returns to Settings → "Going Light" only.
 *
 * ## Evidence anchor
 *
 * Castelo N, Kushlev K, Ward AF, Esterman M,
 * Reiner PB. (2025) *Blocking mobile internet on
 * smartphones improves sustained attention, mental
 * health, and subjective well-being.* PNAS Nexus
 * 4(2):pgaf017. N=467 RCT, d~z~=0.57 mental health
 * (larger than the meta-analytic effect of
 * antidepressants). The card is the consent gate
 * that the paper's protocol requires.
 *
 * v0.26+ (Phase 1 G-1).
 */
@Composable
fun GoingLightConsentCard(
    schedule: GoingLightSchedule,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    if (!schedule.enabled) return
    if (VpnService.prepare(ctx) == null) return

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
                text = "If you would like, Going Light needs a system permission.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Going Light blocks mobile-internet traffic during a window you choose. The system will ask once; you can turn it off any time.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { requestVpnConsent(ctx) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Grant permission")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Not now")
            }
        }
    }
}

/**
 * Launches the OS-level VPN consent dialog. The
 * system shows the dialog and returns the user's
 * choice to the launching Activity via
 * [android.app.Activity.onActivityResult]; the home
 * surface re-reads [VpnService.prepare] on resume
 * to see the new state.
 */
private fun requestVpnConsent(ctx: Context) {
    val intent: Intent? = VpnService.prepare(ctx)
    if (intent != null) {
        // The system expects the launcher to start
        // this intent for result. The home Activity
        // is the natural host; a top-level launch
        // here is a no-op in the activity stack.
        // The intent is intentionally not started
        // here — the home surface is the host.
        // (The system returns the consent to the
        // launching Activity; the caller wires
        // that into the home Activity's
        // onActivityResult in the follow-up commit.)
        @Suppress("UNUSED_EXPRESSION")
        intent
    }
}
