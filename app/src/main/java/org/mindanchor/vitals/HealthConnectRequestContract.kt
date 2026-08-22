/*
 * v0.35.2 — Shared Health Connect permission request contract.
 *
 * Fires the dedicated Health Connect permission UI directly,
 * not through the system permission dialog. The Health Connect
 * SDK 1.1.0's own `PermissionController.createRequestPermissionResultContract()`
 * routes through the system `RequestMultiplePermissions` on
 * Android 14+, which auto-dismisses for `android.permission.health.*`
 * because those are not standard runtime permissions (the system
 * has no UI to render for them and closes the dialog in ~50ms).
 *
 * The right UI is the dedicated Health Connect provider's
 * deeplink gateway, addressed by the modern
 * `androidx.health.ACTION_REQUEST_PERMISSIONS` action (the legacy
 * `android.health.connect.action.REQUEST_HEALTH_PERMISSIONS` is
 * rejected with `SecurityException: requires GRANT_RUNTIME_PERMISSIONS`
 * on Android 17 because that activity is signature-protected).
 *
 * The provider's deep-link gateway is the same one the SDK uses
 * internally for `ACTION_HEALTH_CONNECT_SETTINGS` and
 * `ACTION_MANAGE_HEALTH_DATA`. We don't pin a package so the
 * launcher works whether the user has the new
 * "Health Connect by Android" app (`com.google.android.apps.healthdata`,
 * versionCode 268669+, versionName 2026.x.x.x) or the older
 * standalone provider (`com.google.android.healthconnect.controller`,
 * versionName 17).
 *
 * Result Intent's extras are provider-specific; the version-stable
 * approach is to re-read granted permissions via
 * `HealthConnectClient.permissionController.getGrantedPermissions()`
 * after the user returns.
 *
 * Used by:
 *   - `app/src/main/java/org/mindanchor/onboarding/steps/HealthConnectStep.kt`
 *     (the wizard's "Set up your data sources" flow)
 *   - `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
 *     (the Settings → Sources → Health Connect section)
 *
 * Both call sites must cache the contract with `remember { ... }` —
 * Compose's launcher keys on the contract INSTANCE, so an inline
 * factory call creates a new contract every recomposition and
 * forces the launcher to re-register with the activity's
 * ActivityResultRegistry. The same fix that the v0.23.0 EMA /
 * Batching launcher cache applied to the previous contract.
 */
package org.mindanchor.vitals

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class HealthConnectRequestPermissionsContract :
    ActivityResultContract<Set<String>, Set<String>>() {

    override fun createIntent(context: Context, input: Set<String>): Intent {
        // The modern action accepted by the new Health Connect
        // gateway. Verified via `pm dump com.google.android.apps.healthdata`:
        //   filter for `androidx.health.ACTION_REQUEST_PERMISSIONS` resolves
        //   to `com.google.android.apps.healthdata/.deeplink.DefaultGateway`.
        return Intent("androidx.health.ACTION_REQUEST_PERMISSIONS")
            .putExtra("androidx.health.extra.PERMISSIONS", input.toTypedArray())
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> {
        // The dedicated Health Connect UI does not return a stable
        // result Intent on all providers. The rest of the launcher
        // re-reads granted permissions from the SDK in its own
        // schedule (see `WellnessRepository`), so we return the
        // empty set as a hint of "user came back from the
        // dedicated UI" — the actual granted set is read separately.
        return emptySet()
    }
}
