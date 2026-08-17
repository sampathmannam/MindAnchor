# v0.37.0 — Health Connect alpha-gate bypass

**Status**: design approved by user, in progress to implementation
**Goal**: let an end user on stock Android grant MindAnchor the 8 `READ_*` Health Connect permissions without adb, despite the 1.2.0-alpha05 provider gateway being gated.

## Background

v0.36.0 (`e2cbc11`) shipped the patched 1.2.0-alpha05 AAR. The data read IPC works end-to-end after `adb shell pm grant org.mindanchor android.permission.health.READ_*` — verified by reaching `com.android.server.healthconnect.permission.DataPermissionEnforcer` in the device logs. The blocker is the grant UI: the provider's gateway Activity treats any consumer built against an alpha SDK as outdated and shows the "MindAnchor needs to be updated" page instead of the grant UI. End users without adb have no path to grant.

## Approach (combo, stop on first hit)

### Approach 2 — receiver enumeration (run first)
Dump `com.google.android.apps.healthdata` (HCBA)'s exported activities and receivers via `PackageManager.getPackageInfo(...).activities` / `.receivers`. Build a list of candidates whose intent filter includes `androidx.health.*` or `*HEALTH*` or any data-type intent. Send a hand-crafted intent to each via `startActivityForResult`. The first one whose result is `RESULT_OK` with a non-empty grant set is the hit. **Most likely to work** because the HCBA app has its own "Connected apps" / "Manage data access" surface that is independent of the gateway intent and may not be SDK-gated.

### Approach 1 — intent extras lie (run if 2 fails)
Craft the gateway `Intent` by hand (not via `HealthConnectRequestPermissionsContract`) and add extras found by grepping the 1.2.0-alpha05 AAR:
- `EXTRA_HEALTH_SDK_VERSION` = `1030000` (claim a future stable 1.3.0)
- Any `EXTRA_*` constant whose name contains `SDK` or `VERSION` in the AAR's resources.arsc
If the provider's receiver reads any of these and accepts the higher claim, the gateway renders.

### Approach 3 — service-level bypass (run if 1+2 fail)
Bind to `HealthDataSdkService` via the legacy `androidx.health.platform.client.ACTION_BIND_SDK_SERVICE` (the 1.2.0-alpha05 provider still exports this service). Use reflection on the AIDL stub to enumerate the binder transaction codes. For each, attempt the call with a stubbed `RequestContext` claiming stable SDK. The legacy service on 1.2.0-alpha05 has the new `IHealthDataSdkService` interface (token API), but the AIDL surface may still include a tokenless method that the old 1.1.0 data path could call. Lowest expected hit rate; the cheat code for if all else fails.

### Stop rule
The orchestrator returns `Success` the first time `startActivityForResult` returns `RESULT_OK` and the `granted` set in the result is non-empty. Anything else (`RESULT_CANCELED`, `SecurityException`, "App needs to be updated" page, no grant returned) is `Failure`, the next approach runs.

### Final fallback (if all three fail)
The v0.36.0 release-notes path: copy-able `adb shell pm grant` commands in the debug screen. The user pastes them in a terminal, the data flow works, end of story for now.

## Components

```
app/src/main/java/org/mindanchor/vitals/
  HcAlphaBypass.kt              orchestrator, runs 2 → 1 → 3 → fallback
  HcReceiverProbe.kt            approach 2
  HcIntentExtrasProbe.kt        approach 1
  HcServiceLevelProbe.kt        approach 3
  HealthConnectSource.kt        [mod] add bypassRequestPermissions() entry point

app/src/main/java/org/mindanchor/ui/settings/
  HealthConnectDebugScreen.kt   debug screen with "Run bypass" button + result log + pm grant fallback
```

## UX

- New entry in Settings → "Data sources" → "Health Connect (debug)". Visible to all builds, marked "alpha".
- One button: **Run alpha-gate bypass**. Tapping starts the orchestrator.
- Result log shows: which approach ran, what it tried, what it got, final outcome.
- "Copy adb commands" button always present, copies the 8 `pm grant` lines for the fallback path.
- No change to the home flow or the wizard. The standard "Connect" path still goes through `HealthConnectRequestPermissionsContract` and shows the same "App needs to be updated" page.

## Error handling

- Each probe is wrapped in `runCatching` and returns `Result<ProbeResult>`. No thrown exception escapes the orchestrator.
- The orchestrator is the only public entry point. It returns `BypassOutcome { winner: ProbeName?, granted: Set<String>, attempts: List<Attempt> }`.
- The debug screen renders the outcome; the standard `HealthConnectRequestPermissionsContract` is unaffected.

## Testing

- Build the APK, install on `ZD2232FCR5` (which already has HCBA 1.2.0-alpha05 and COROS writing to it).
- Run the bypass from the debug screen. Capture screenshots of each attempt's result.
- If any approach returns a non-empty `granted` set, verify by re-reading `HealthConnectSource.grantedPermissions(context)` — the per-record read IPC should now return non-empty.
- If all three fail, document each failure reason in the v0.37.0 release notes and ship the debug screen + `pm grant` fallback as the deliverable.

## Build

- `versionCode` 65 → 66
- `versionName` "0.36.0" → "0.37.0"
- 4 new files, 1 modified, 1 new UI screen
- No toolchain bump. No AAR change. The patched 1.2.0-alpha05 AAR stays.

## Out of scope

- Toolchain upgrade to AGP 9.1 / Kotlin 2.2 / compileSdk 37
- Patching the AAR further (the v0.36.0 metadata patch is enough)
- Companion app architecture
- Stable 1.2.0 SDK arrival (we swap the patched AAR for the stable Maven artifact and drop the bypass when that happens)
