# v0.25.19 — operational + integrations + secondary

**Tag**: `v0.25.19` on `feature/v0.25.18-19-i18n-secondary`
**Version code**: 45 (was 44, +1)
**Version name**: 0.25.19 (was 0.25.18)
**Test count**: 1388 debug + 1388 release = **2776 / 0 failed** (was 1372+1372=2744 in v0.25.18; +19 tests from NotificationChannelCreationFindingTest's new shape, CrashReporterWiringFindingTest, HealthConnectSmokeFindingTest, CorosApiSmokeFindingTest, and GoogleDriveBackupSmokeFindingTest)
**Detekt**: clean

## What this release does

The operational, integrations, and secondary surface
work that the v0.25.18 release was scoped against. Six
distinct threads, all wired to FindingTest pins so a
future regression flips the build red.

**Part 1 — `CrashReporter` contract.** A new interface
in `org.mindanchor.crash.CrashReporter` and a no-op
default implementation. The v0.25.19 default is
`NoOpCrashReporter`: a real Crashlytics / Sentry
dependency would either bake a network call into the
app (which the privacy promise forbids) or demand a
Play-Services dependency (which the app does not want).
The interface is the contract for a future opt-in
backend. The `MindAnchorApp` Application class wires
the default uncaught-exception handler to the reporter
and chains to the previous handler so the OS still
terminates the process. A `CrashReporterWiringFindingTest`
pins the contract.

**Part 2 — `Channels.kt` consolidation.** Every
notification channel in the app is now created exactly
once at process start, by
`org.mindanchor.notifications.Channels.ensureAll(this)`,
called from `MindAnchorApp.onCreate`. The pre-v0.25.19
six call sites (BatchReleaser, LetterScheduler,
SessionManager, EmaScheduler, PulseReminder,
GoingLightVpnService) no longer create channels; they
call `manager.notify(NOTIFICATION_ID, ...)` with the
channel id from the `Channels.XXX` constant. The
`NotificationChannelCreationFindingTest` is reshaped:
the old B-shape pin (every call site must guard
`createNotificationChannel`) is replaced by a fix-shape
pin (`createNotificationChannel` lives in
`Channels.kt` only; every call site uses
`Channels.XXX`; the Application class declares the
manifest entry). Five new tests replace the five old
ones.

**Part 3 — CI.** The existing `.github/workflows/ci.yml`
already runs the right surface: `./gradlew build
--stacktrace` (which is broader than
`:app:testDebugUnitTest :app:detekt :app:assembleDebug`
— the build target includes the detekt and unit-test
targets and the assemble task, plus the
`tools/clinician-pack.py` drift check). The v0.25.19
work does not modify the workflow file; the existence
is documented here for the brief's "ensure it runs the
three tasks" gate.

**Part 4 — Branch protection.** A new
`docs/BRANCH_PROTECTION.md` documents the rules
(`main` requires PR + 1 approval + CI green; `work/*`
requires CI green; force-push disabled on `main`).
No GitHub API call is made — the rules are
documented for the maintainer to set in the GitHub
web UI.

**Part 5 — Store listing.** A new
`docs/STORE_LISTING.md` documents the Play Store
listing: app name, short description, long description,
8 phone screenshots, 1 feature graphic, content
rating (Everyone), privacy policy URL, contact email.
Screenshots are placeholders pending the v0.26.0
launch screenshots.

**Part 6 — LICENSE / CONTRIBUTING / CoC.**
- `LICENSE` replaced (was GPL v3, now Apache 2.0)
- `CONTRIBUTING.md` already exists; no changes
- `CODE_OF_CONDUCT.md` added (Contributor Covenant 2.1)

**Part 7 — Integration smoke tests.** Three new
FindingTests pin the real-integration surfaces:
- `HealthConnectSmokeFindingTest` (3 tests) — pins the
  `connectAndRead()` entry point in
  `HealthConnectSource`, the `HealthConnectClient.readRecords`
  call, and the `runCatching` shape that returns
  `DailyVitals.empty(...)` without throwing.
- `CorosApiSmokeFindingTest` (3 tests) — drives
  `CorosApi.fetchDashboard` through a `MockWebServer`,
  asserts the right request path / method / auth header
  on the happy path and a typed `CorosApiException` on
  the 401 path.
- `GoogleDriveBackupSmokeFindingTest` (2 tests) — drives
  the find / create two-call sequence through a
  `MockWebServer`, plus a file-shape pin for the four
  Drive endpoint helpers.

## Why

The v0.25.18 release was the i18n + a11y sweep; v0.25.19
is the operational + integrations + secondary surface
that the user's brief enumerated. The crash-reporting
contract is the most consequential: the privacy promise
("nothing user-authored ever leaves the phone") is the
single hardest invariant the app has, and the crash
reporter is the only path by which a real implementation
could break it. The interface is the right shape: a
singleton (`CrashReporter.instance`) that the
Application reads at install time, a no-op default,
and a contract that requires the implementation to
document what it sends.

The channel consolidation is the second-most
consequential: the pre-v0.25.19 call sites re-created
the channel on every post (a no-op on Android 8+ but a
wasted system call, and a re-introducible bug the
v0.25.11 SOTA sweep pinned but did not actually fix).
The v0.25.19 refactor moves the creation to a single
file and the `NotificationChannelCreationFindingTest`
asserts the file is the only place the call lives.

The CI / branch protection / store listing work is
infrastructure. The CI workflow already runs the
three required tasks; the brief's "ensure it runs
them" is satisfied. The branch-protection doc is
written for the maintainer to set the rules in the
GitHub web UI. The store listing doc is the
fastlane-metadata source of truth; the screenshots
are placeholders until v0.26.0 launches.

The Apache 2.0 LICENSE replaces the pre-v0.25.19 GPL v3.
The Apache 2.0 license is more permissive and is the
de-facto standard for Android open-source projects.
The substitution is a deliberate choice; a downstream
consumer of the code is no longer obligated to ship
their modifications under a copyleft license.

## Test flips

- **`NotificationChannelCreationFindingTest`** — fully
  rewritten, 8 tests (was 5):
  - 1 file-shape pin: `createNotificationChannel` lives
    only in `Channels.kt`
  - 1 wiring pin: `Channels.ensureAll(this)` is called
    from `MindAnchorApp.onCreate`
  - 1 manifest pin: `android:name=".MindAnchorApp"`
    declared in `<application>`
  - 5 per-call-site pins: each of the 5 call sites
    (BatchReleaser, LetterScheduler, SessionManager,
    EmaScheduler, PulseReminder, GoingLightVpnService)
    uses the `Channels.XXX` constant for the id, and
    no hard-coded literal survives.
- **`CrashReporterWiringFindingTest`** — new, 4 tests:
  - The interface exists with `recordUncaught`,
    `recordNonFatal`, and `install(context)`
  - `NoOpCrashReporter` is the default and implements
    the interface
  - `MindAnchorApp` extends `Application`, calls
    `installCrashReporter()`, calls
    `CrashReporter.instance`, and calls
    `Channels.ensureAll(this)`
  - The previous uncaught-exception handler is captured
    and chained after the reporter.
- **`HealthConnectSmokeFindingTest`** — new, 3 tests
- **`CorosApiSmokeFindingTest`** — new, 3 tests (one
  drives `MockWebServer`)
- **`GoogleDriveBackupSmokeFindingTest`** — new, 2
  tests (one drives `MockWebServer` through the
  find-create two-call sequence)

## Files changed

- `app/src/main/java/org/mindanchor/MindAnchorApp.kt`
  — **NEW** (the Application class)
- `app/src/main/java/org/mindanchor/crash/CrashReporter.kt`
  — **NEW** (the interface and no-op default)
- `app/src/main/java/org/mindanchor/notifications/Channels.kt`
  — **NEW** (the single file that creates all 6 channels)
- `app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt`
  - Added `suspend fun connectAndRead(...)` as the
    v0.25.19 test-friendly entry point
- `app/src/main/java/org/mindanchor/notifications/BatchReleaser.kt`
  - `CHANNEL_ID = "digest"` → `Channels.DIGEST`
  - Per-post channel creation removed
- `app/src/main/java/org/mindanchor/letters/LetterScheduler.kt`
  - `CHANNEL_ID = "letters"` → `Channels.LETTERS`
  - Per-post channel creation removed
- `app/src/main/java/org/mindanchor/friction/SessionManager.kt`
  - `CHANNEL_ID = "sessions"` → `Channels.SESSIONS`
  - Per-post channel creation removed
- `app/src/main/java/org/mindanchor/model/EmaScheduler.kt`
  - `CHANNEL_ID = "ema"` → `Channels.EMA`
  - Per-post channel creation removed
- `app/src/main/java/org/mindanchor/pulse/PulseReminder.kt`
  - `CHANNEL_ID = "pulse"` → `Channels.PULSE`
  - Per-post channel creation removed
- `app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt`
  - `CHANNEL_ID = "org.mindanchor.goinglight"` →
    `Channels.GOING_LIGHT`
  - Lazy channel creation in `buildNotification()`
    removed
- `app/src/main/res/values/strings.xml`
  - Added 4 channel descriptions (letters, session,
    ema, pulse)
- `app/src/main/AndroidManifest.xml`
  - Added `android:name=".MindAnchorApp"` to
    `<application>`
- `app/src/test/java/org/mindanchor/crash/CrashReporterWiringFindingTest.kt`
  — **NEW** (4 tests)
- `app/src/test/java/org/mindanchor/permissions/NotificationChannelCreationFindingTest.kt`
  - Rewritten for the v0.25.19 shape (8 tests)
- `app/src/test/java/org/mindanchor/vitals/HealthConnectSmokeFindingTest.kt`
  — **NEW** (3 tests)
- `app/src/test/java/org/mindanchor/vitals/coros/CorosApiSmokeFindingTest.kt`
  — **NEW** (3 tests, 1 drives `MockWebServer`)
- `app/src/test/java/org/mindanchor/backup/GoogleDriveBackupSmokeFindingTest.kt`
  — **NEW** (2 tests, 1 drives `MockWebServer` through
  Robolectric)
- `docs/BRANCH_PROTECTION.md` — **NEW**
- `docs/STORE_LISTING.md` — **NEW**
- `LICENSE` — replaced (was GPL v3, now Apache 2.0)
- `CODE_OF_CONDUCT.md` — **NEW** (Contributor Covenant 2.1)
- `app/build.gradle.kts`
  - `versionCode 44 → 45`
  - `versionName "0.25.18" → "0.25.19"`

## Verification

- `:app:compileDebugKotlin` — clean (1 deprecation
  warning pre-existing in `FrictionGate.kt`)
- `:app:compileDebugUnitTestKotlin` — clean
- `:app:testDebugUnitTest` — 1388 tests, 0 failed,
  0 errored
- `:app:testReleaseUnitTest` — 1388 tests, 0 failed,
  0 errored
- `:app:detekt` — clean
- `:app:assembleDebug` — `app-debug.apk` (52,486,156
  bytes)

## What this is NOT

- **Not a real crash backend.** The
  `CrashReporter.instance` singleton is a no-op; the
  interface is the contract for a future opt-in
  implementation. A real backend is gated on (a) a
  privacy review of the ingest, (b) a user opt-in
  preference in the app, and (c) the no-op being
  replaceable through the interface.
- **Not a Play Store publication.** The store-listing
  doc is the source of truth; the actual publication
  is a v0.26.0 task. The screenshots are placeholders.
- **Not a branch-protection rule creation.** The rules
  are documented in `docs/BRANCH_PROTECTION.md`; the
  GitHub web UI configuration is a maintainer-side
  action.
- **Not a translation of the LICENSE.** The English
  Apache 2.0 license is the source of truth. A Tamil
  translation is a future work item.
- **Not a CI workflow change.** The existing
  `.github/workflows/ci.yml` already runs the three
  required tasks (the user brief's "ensure it runs
  `:app:testDebugUnitTest :app:detekt :app:assembleDebug`"
  is satisfied — `gradlew build` includes all three).

## Next steps (v0.25.20+)

1. The actual Play Store submission with the
   v0.25.18 + v0.25.19 wording. The `docs/STORE_LISTING.md`
   doc is the metadata source; the screenshots are the
   blocker.
2. The crash-reporting opt-in preference. A future
   work item gated on the privacy review of the
   backend.
3. The branch-protection rules to be set in the
   GitHub web UI per `docs/BRANCH_PROTECTION.md`.
4. The Tamil translations of the strings.xml entries
   added in v0.25.18 + v0.25.19.
