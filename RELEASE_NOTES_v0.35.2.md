# v0.35.2 — Health Connect wizard step launches the dedicated HC UI

**Tag:** v0.35.2
**Commit:** c9f0425 (versionCode 64, versionName 0.35.2)
**Date:** 2026-08-17

## What changed

The v0.35.1 "Set up your data sources" wizard step had three attempts
at the same goal — launch the dedicated Health Connect permission UI so
the user can grant the eight `android.permission.health.*` read
permissions to MindAnchor:

1. **v0.35.1 (original)** — fired the system
   `ActivityResultContracts.RequestMultiplePermissions` with the eight
   custom permission strings. The system had no UI to render for
   them (they are not standard runtime permissions) and closed the
   dialog in ~50ms, advancing the wizard without granting anything.

2. **v0.35.2 attempt 1** — switched to the SDK 1.1.0
   `HealthConnectSource.requestPermissionsContract()`. On Android 14+
   that contract wraps the same broken system contract (verified in
   the SDK bytecode: `HealthPermissionsRequestModuleContract` delegates
   to `ActivityResultContracts.RequestMultiplePermissions`). Same
   50ms auto-dismiss, same broken outcome.

3. **v0.35.2 attempt 2** — fired the legacy
   `android.health.connect.action.REQUEST_HEALTH_PERMISSIONS` intent
   directly at `com.google.android.healthconnect.controller`. On
   Android 17 the receiving activity is signature-protected and the
   launch raised `SecurityException: requires
   android.permission.GRANT_RUNTIME_PERMISSIONS`. The app crashed.

4. **v0.35.2 (shipped)** — fires the modern
   `androidx.health.ACTION_REQUEST_PERMISSIONS` intent, which the new
   **Health Connect by Android**
   (`com.google.android.apps.healthdata`, versionCode 268669+,
   versionName 2026.x.x.x) accepts via its `.deeplink.DefaultGateway`
   activity. The intent action is resolved by the system at launch
   time so the same code works whether the user has HCBA or the older
   `com.google.android.healthconnect.controller` provider.

The contract is extracted to
`app/src/main/java/org/mindanchor/vitals/HealthConnectRequestContract.kt`
so the Settings → Sources → Health Connect section uses the same
launcher (and the same `remember { ... }` caching that v0.23.0 applied
to the previous contract — Compose keys on the contract INSTANCE,
so an inline factory call creates a new contract every recomposition
and forces the launcher to re-register with the activity's
ActivityResultRegistry).

## Verified on phone ZD2232FCR5 (Motorola Signature, Android 17, API 37)

1. Re-walked the wizard: Welcome → Health Connect → Pair a watch →
   COROS account → PPG baseline → Done → Open the home.
2. The Health Connect tap now actually opens the dedicated Health
   Connect by Android UI (no more 50ms flash, no more crash).
3. On this device the gateway returned the per-app "MindAnchor needs
   to be updated to continue syncing with Health Connect" page —
   MindAnchor v0.35.2 is still on Health Connect SDK 1.1.0 and HCBA
   requires 1.2.0+. The page is reachable, the user can act on it.
4. The user can skip Health Connect and finish the wizard
   (Pair-a-watch / COROS / PPG are all skippable, Done still opens
   the home).
5. Settings → Sources → Wearable section still lists the eight
   `android.permission.health.*` types the launcher reads, with the
   same "Connect to your watch" affordance.

## Known limitation (v0.36.0 work)

The new **Health Connect by Android** (HCBA) on Android 14+ requires
the consuming app to be built against Health Connect SDK 1.2.0+.
MindAnchor v0.35.2 still uses 1.1.0. Until v0.36.0 upgrades the SDK
the HCBA gateway returns the "App update needed" page and the
COROS-app → Health Connect → MindAnchor chain is blocked.

The other 2/3 of the integration still work:

- **PPG (camera)** — works; PpgScreen + PpgSessionStore are unchanged.
- **BLE heart-rate (universal SmartwatchConnector)** — works; the
  v0.34.0 GATT 0x180D path is unchanged.
- **Polar Flow direct OAuth2** — works; PolarSection in Settings
  is unchanged. The Coros step body still embeds the Polar form
  (the v0.35.2 cleanup of the Coros/Polar content mismatch is
  still pending — flagged in the v0.35.1 spec doc).
- **COROS Pace 3 direct** — not feasible. COROS does not have a
  public OAuth2 API; their partner programme requires
  25,000+ MAU and approval.

## Files

**New:**
- `app/src/main/java/org/mindanchor/vitals/HealthConnectRequestContract.kt`
  (3118 bytes) — shared `ActivityResultContract` that fires the
  modern `androidx.health.ACTION_REQUEST_PERMISSIONS` intent.

**Modified:**
- `app/src/main/java/org/mindanchor/onboarding/steps/HealthConnectStep.kt`
  — uses the shared `HealthConnectRequestPermissionsContract`,
  delegates permission list to `HealthConnectSource.effectivePermissions(context)`
  (the same source the Settings panel iterates).
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
  — uses the shared contract, with the existing
  `remember { healthConnectPermissionContract = ... }` cache and
  the `viewModel.refreshHealthConnectStatus()` post-callback
  preserved.
- `app/src/test/java/org/mindanchor/onboarding/SetupWizardStepTest.kt`
  — updated assertions: must use the new contract, must NOT import
  `ActivityResultContracts` (the broken system path), must NOT use
  the SDK's `requestPermissionsContract()` (the SDK-wrapped broken
  system path), must fire the modern action.
- `app/build.gradle.kts` — versionCode 63→64, versionName
  "0.35.1"→"0.35.2".

## Tests

1491 + 9 (existing wizard tests) unit tests pass. The wizard's
Health Connect step is exercised by `SetupWizardStepTest.
HealthConnectStep Composable exists and grants the 8 HC
permissions (v0-35-1)`.
