package org.mindanchor.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The v0.26+ (spec Phase 3 of the protective-layer
 * spec, `docs/superpowers/specs/2026-08-23-protective-layer-design.md`)
 * "Healthy defaults" walkthrough.
 *
 * The Composable shows the user's current default apps
 * (browser, SMS, email, dialer) and offers a one-tap
 * "Change" button for each that deep-links to the
 * system default-app settings. Each row also names a
 * privacy-respecting alternative (DuckDuckGo, Signal,
 * K-9 Mail, FairEmail).
 *
 * ## Why "we like this", not "you should switch"
 *
 * The spec is explicit: recommendations are presented
 * as "we like this" not "you should switch". The
 * launcher does not auto-install, does not nag, and
 * does not score. The user picks.
 *
 * ## Why no new permissions
 *
 * The walkthrough is `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`
 * (with a fallback to `ACTION_APPLICATION_SETTINGS`).
 * No new permission is requested. The user grants the
 * default in the system settings app, not in
 * MindAnchor.
 *
 * ## Not currently routed
 *
 * The inline summary in [SettingsScreen] (Phase 3
 * surface) covers the one-tap "Open system defaults"
 * affordance. This full screen Composable is the
 * per-category breakdown; routing it through
 * HomeActivity is a follow-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthyDefaultsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Healthy defaults",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "We like these. You pick.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DefaultRow(
            title = "Browser",
            currentDefault = readDefault(context, "browser"),
            recommendation = "DuckDuckGo",
            rationale = "No search-history leak, no cross-site tracking by default.",
            onChange = { openDefaultAppsSettings(context) },
        )
        DefaultRow(
            title = "Messages",
            currentDefault = readDefault(context, "sms"),
            recommendation = "Signal",
            rationale = "End-to-end encrypted; the project's SMS recommendation is the default.",
            onChange = { openDefaultAppsSettings(context) },
        )
        DefaultRow(
            title = "Email",
            currentDefault = readDefault(context, "email"),
            recommendation = "K-9 Mail or FairEmail",
            rationale = "Both are open-source, on-device-only; no analytics.",
            onChange = { openDefaultAppsSettings(context) },
        )
        DefaultRow(
            title = "Phone",
            currentDefault = readDefault(context, "dialer"),
            recommendation = "Your current default",
            rationale = "There is no widely-installed, privacy-respecting dialer; we don't recommend changing it.",
            onChange = { openDefaultAppsSettings(context) },
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun DefaultRow(
    title: String,
    currentDefault: String,
    recommendation: String,
    rationale: String,
    onChange: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Currently: $currentDefault",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "We like: $recommendation",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onChange,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change")
            }
        }
    }
}

/**
 * Reads the current default for the given category. Best-effort: the
 * platform does not expose a single, stable API for "default browser"
 * across all SDKs, so this returns a short human label rather than a
 * package name. The row shows it as "Currently: <label>"; the
 * underlying package is not user-facing in the spec.
 */
private fun readDefault(context: Context, category: String): String = when (category) {
    "browser" -> context.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://example.com")),
            0,
        )
        .firstOrNull()
        ?.activityInfo
        ?.loadLabel(context.packageManager)
        ?.toString()
        ?: "System default"
    "sms" -> android.provider.Telephony.Sms.getDefaultSmsPackage(context) ?: "System default"
    "email" -> "System default"
    "dialer" -> context.getSystemService(android.telecom.TelecomManager::class.java)
        ?.defaultDialerPackage
        ?: "System default"
    else -> "System default"
}

private fun openDefaultAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
    }.getOrElse {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
