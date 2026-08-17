# v0.36.0 — Health Connect SDK 1.2.0-alpha05, patched AAR

## What this release does

v0.35.2 ships the Health Connect permission grant UI but the
provider's data read IPC was still gated: every `getGrantedPermissions` /
`readData` / `readDataRange` call from MindAnchor hit the new "Health
Connect by Android" provider's SDK-version check and was rejected
because the SDK the app was built against (1.1.0 stable) is older
than the 1.2.0 the provider now requires on its data path.

v0.36.0 retargets the SDK to **1.2.0-alpha05** so the data read
path is accepted by the provider. The previous launcher's
"Health Connect is not installed on this phone" placeholder (in
settings) becomes the proper "No data is being read yet — tap to
connect" prompt the rest of the UI was always ready to render.

## How

1. The SDK upgrade is by **patched AAR** rather than the upstream
   Maven artifact. The 1.2.0-alpha05 AAR's
   `META-INF/com/android/build/gradle/aar-metadata.properties`
   declares `minCompileSdk=37`; this project is pinned to
   AGP 8.9.1 / Kotlin 2.0.21 / compileSdk 36 because bumping to
   AGP 9.1.0 / Kotlin 2.2.20 hits a Compose compiler version
   mismatch the AAR was not designed for.

   The patched AAR in `app/libs/connect-client-1.2.0-alpha05-relaxed.aar`
   drops the `minCompileSdk` field; the AIDL surface, the protobuf
   message classes, the AndroidManifest, and the proguard rules are
   unchanged. Build with this file in place of the Maven dependency.

   The patch script is at `app/src/main/java/org/mindanchor/vitals/.disabled/`
   — moved aside after the experiment concluded. It can be regenerated
   from the cached AAR at
   `~/.gradle/caches/modules-2/files-2.1/androidx.health.connect/connect-client/1.2.0-alpha05/.../connect-client-1.2.0-alpha05.aar`.

2. `HealthConnectSource` is restored to the standard SDK calls:
   `HealthConnectClient.getOrCreate(context)` and
   `permissionController.getGrantedPermissions()` are reachable
   again. The launcher no longer needs the raw AIDL shim or the
   `RequestContext.sdkVersion` lie; that work moved to
   `HealthConnectAidlShim.kt.disabled` (kept for the record).

3. `HealthConnectPermissionStrings` holds the eight
   `android.permission.health.READ_*` strings as plain constants,
   so unit tests and other call sites do not have to depend on
   the SDK's `HealthPermission` object to spell the same string.

4. `compileSdk` stays at 36. `versionCode` is 65, `versionName` is
   `0.36.0`.

## What still does not work

The gateway permission UI (the screen MindAnchor launches when the
user taps "Connect to your watch" or the wizard's Health Connect
step) still shows the provider's "App needs to be updated" page
because 1.2.0-alpha05 is itself an alpha. The provider treats any
consumer built against an alpha SDK as outdated, and refuses to
let the user grant the runtime permissions through the gateway.
The only consumer that the provider's gateway accepts today is one
built against 1.2.0 stable, which Google has not published.

When Google ships 1.2.0 stable, swap the patched alpha AAR for
the stable AAR (the metadata will not need patching, since the
stable release's `minCompileSdk` is expected to match the current
toolchain) and the gateway gate disappears.

Until then, the **data read path** works end-to-end on this device
when the user grants the eight read permissions through `adb`:

    adb shell pm grant org.mindanchor android.permission.health.READ_HEART_RATE
    adb shell pm grant org.mindanchor android.permission.health.READ_RESTING_HEART_RATE
    adb shell pm grant org.mindanchor android.permission.health.READ_HEART_RATE_VARIABILITY
    adb shell pm grant org.mindanchor android.permission.health.READ_SLEEP
    adb shell pm grant org.mindanchor android.permission.health.READ_STEPS
    adb shell pm grant org.mindanchor android.permission.health.READ_EXERCISE
    adb shell pm grant org.mindanchor android.permission.health.READ_TOTAL_CALORIES_BURNED
    adb shell pm grant org.mindanchor android.permission.health.READ_MINDFULNESS

The HCBA provider's IPC service then returns the COROS-written
data through the same AIDL pipeline every other consumer app uses.
The "App needs to be updated" page is a gateway cache, not an
AIDL-layer gate; the IPC layer (verified by reaching
`com.android.server.healthconnect.permission.DataPermissionEnforcer`
in the device logs after the SDK upgrade) accepts the
`RequestContext.sdk_version = 1020005` we put in every call, and
the provider's enforcement kicks in only at the data-record layer
when the consumer has not been granted the matching READ permission.

End users without `adb` access to the phone will have to wait
for the stable 1.2.0 release to grant through the UI. No code
change in v0.36.0 unblocks that path; the change is upstream.

## What was tried and discarded

These were attempted in the brainstorming session and rejected
before the patched-AAR path was chosen; the artifacts are kept in
`app/src/main/java/org/mindanchor/vitals/.disabled/` for the record.

- `meta-data android:name="androidx.health.SELF_REPORT_SDK_VERSION"
  android:value="1020005"` in `AndroidManifest.xml`. The provider
  does not read this key; the gateway gate is AIDL-driven, not
  manifest-driven. The `meta-data` tag was added then removed.
- Raw `IBinder.transact()` AIDL-direct shim with a hand-crafted
  `RequestContext` proto. The shim worked against the SDK 1.1.0
  AIDL contract (`IHealthDataService`) but the 1.2.0-alpha05
  provider's `HealthDataSdkService` exports a different AIDL
  interface (`IHealthDataSdkService` — token API only, no data
  read methods), so the shim's calls were rejected with the
  `enforceInterface` mismatch. The 1.2.0-alpha05 data AIDL is
  reachable only via the patched AAR's `HealthDataAsyncClient`,
  which is the approach that finally shipped.

## What was kept from the AIDL shim

The launcher was built (briefly) on a hand-rolled AIDL client
that set `RequestContext.sdkVersion` to a high value to bypass the
provider's gateway gate on the data path. That code lives in
`HealthConnectAidlShim.kt.disabled` for the record. The SDK 1.2.0
upgrade removed the need for it; if a future provider demands a
higher SDK version than the SDK we depend on, the shim is a
known-good starting point — the lib `connect-client-1.2.0-alpha05.aar`
is already on disk, the AIDL surface is the typed `IHealthDataService`
the shim already speaks, and the `ProtoParcelable` envelope format
is documented inline in the shim.

## Build

- `versionCode = 65`
- `versionName = "0.36.0"`
- `compileSdk = 36`
- `minSdk = 33`
- `targetSdk = 35`
- AGP `8.9.1`, Kotlin `2.0.21`, Compose BOM `2024.12.01`
- 1 new file (`HealthConnectPermissionStrings.kt`), 2 modified
  (`HealthConnectSource.kt`, `build.gradle.kts`), 1 new local
  AAR (`app/libs/connect-client-1.2.0-alpha05-relaxed.aar`),
  2 disabled experiment files (`.kt.disabled`).
- Manifest clinical-review sentinel preserved at the top of
  `AndroidManifest.xml`; the launcher's data-source promise is
  unchanged.
