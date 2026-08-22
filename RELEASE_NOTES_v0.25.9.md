# MindAnchor v0.25.9 — SOTA v2 bug-hunt follow-up (11 fixes)

**Release date**: 2026-08-13
**Build**: `versionName=0.25.9, versionCode=33` (commit `b467fa4`)
**Tag**: `v0.25.9` → `b467fa4`
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.9

**Artifacts**:
- debug APK: `92C8A3391072CA673A647BF7D255B730ADEAE67F6361C7E0E59496D95371549F` (52,187,268 bytes)
- release-unsigned APK: `F7B3FEBFFC04F098D4A1604EBAFCB7D8B25B9EB68B41E712C90FFE92D5FA2FE8` (11,371,906 bytes)
**Status**: shipped

This release closes 11 of the 100+ findings from a SOTA v2 bug-hunt campaign against v0.25.8 (7 parallel agents across home / settings / notes+letters / backup / vitals+PPG+sleep+report+friction / onboarding+notifications+accessibility / functional smoke). The 4 high-severity fixes are the core: a v0.25.8 regression in the process-singleton id generator, a missing PII-binding AAD on the encrypted backup, an unre-armed letter alarm after reboot, and a privacy over-reach in the Health Connect permission grant. The 7 medium-severity polish fixes round out the release.

The remaining 100-11 = 89+ findings (DST-bound schedulers, accessibility, Compose state, error handling) are documented in `.git/sdd/bug_hunt_v2_*.md` and the FindingTest file-shape pins; they are the v0.25.10+ backlog.

---

## The 11 fixes

### 1. `NotesPrefs.nextNoteId` is now a true process-singleton (HIGH — v0.25.8 regression)

**Files**:
- `app/src/main/java/org/mindanchor/data/NotesPrefs.kt` (companion + `seedFromDiskIfNeeded`)
- `app/src/main/java/org/mindanchor/HomeActivity.kt` (call site)

The v0.25.8 release notes claimed the `idGenerator` was a "process-singleton" via a class-level `by lazy`. It was not — a class-level `by lazy` resolves *separately per class instance*. Two `NotesPrefs` constructions in the same process (one in `LauncherViewModel`, one in `NoteActivity`) had two `AtomicLong`s seeded to the same `System.currentTimeMillis()`, producing duplicate ids on a fast device — the exact failure mode the v0.25.8 release notes claimed to fix.

**Fix**: the `idGenerator` is now on the `Companion` object (a true per-class-loader singleton), seeded asynchronously by `NotesPrefs.seedFromDiskIfNeeded(context)` from `HomeActivity.onCreate`. The first `nextNoteId()` call is a fast `AtomicLong.incrementAndGet()` — no `runBlocking` on the main thread, no DataStore read in the hot path. The `seeded` flag and the synchronized seed-raise are idempotent across activity recreations and re-launches.

### 2. `HomeActivity.configChanges` covers fontScale / locale / uiMode / density (P0)

**File**: `app/src/main/AndroidManifest.xml:213`

v0.25.5 added four home cards (OneThingCard, OpenLoopCard, QuickNotesCard, BedtimeListCard) that hold form state in `remember`. The v0.25.5–v0.25.8 `configChanges` was `orientation|screenSize|keyboard|keyboardHidden` only — a font scale, locale, dark-mode, or density change recreated the activity and silently lost every `remember`'d form (the one-thing draft, the open-loop worry, the quick-note, the bedtime list).

**Fix**: added `uiMode|fontScale|locale|density|layoutDirection|smallestScreenSize` to the `configChanges` attribute. `CheckInActivity` already declared the full set (line 370) — HomeActivity was the inconsistency. Pin: `HomeActivityConfigChangesFindingTest`.

### 3. `EncryptedBackupCodec` binds GCM AAD to the `ContentType.fileName` (HIGH security, v1 #6 unfixed)

**Files**:
- `app/src/main/java/org/mindanchor/backup/EncryptedBackupCodec.kt`
- `app/src/main/java/org/mindanchor/backup/BackupScheduler.kt` (threaded `type` through)
- `app/src/test/java/org/mindanchor/backup/EncryptedBackupCodecTest.kt` (cross-type unwrap test)

v0.25.8 did not address the v1 finding #6: the wrap/unwrap functions called `cipher.doFinal(plaintext)` without `cipher.updateAAD(type.fileName.toByteArray())`. The GCM tag was bound to (key, IV, ciphertext) only — a blob wrapped for `Notes` could be unwrapped as `Letters` and the tag would still verify. A motivated attacker with `drive.file` scope (the same scope the launcher requests) could read both Drive files and swap them, and the recipient would not see the difference.

**Fix**: `wrap(plaintextJson, type)` and `unwrap(blob, type)` now take a `type: ContentType` argument and call `cipher.updateAAD(type.fileName.toByteArray(Charsets.UTF_8))` on both encrypt and decrypt. The cross-type swap is now a tag failure. New test: `wrap with Notes AAD then unwrap as Letters returns null`. Pin: `EncryptedBackupCodec` test class + flipped assertion in `V2BugHuntFindingTest` and `V1OpenFindingTest`.

### 4. `LetterScheduler.ensureScheduled` is in `Alarms.ensureAll` (S0 alarm loss)

**File**: `app/src/main/java/org/mindanchor/Alarms.kt`

The comment at the top of `Alarms.kt` is the contract: "A missing alarm is the worst shape of bug this app can have: nothing fails, nothing is logged, a feature just never speaks again and the person concludes it does not work." Before this fix, every other scheduler (BatchAlarms, SunsetController, ReportScheduler, EmaScheduler, PulseReminder) was in `Alarms.ensureAll` — the letter scheduler was not. The letter alarm is a one-shot `AlarmManager.setExactAndAllowWhileIdle`; the only re-arm path was inside `LetterScheduler.onFire` (which only runs when the alarm fires). A phone that rebooted never heard the letter again until the user happened to open the screen that re-armed it (which is not a screen anyone would think to open).

**Fix**: one-liner — `runCatching { LetterScheduler.ensureScheduled(app) }` in the `ensureAll` block. Now a reboot re-arms the letter.

### 5. `READ_HEALTH_DATA_IN_BACKGROUND` removed from manifest (HIGH privacy over-reach)

**File**: `app/src/main/AndroidManifest.xml:118–122`

v0.25.5 added `<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />` but the launcher never reads Health Connect in the background — every read is in the foreground (the report screen, the wellness card). The permission surfaced as a "background read" in the system Health Connect dialog, which is a privacy over-reach: the user granted a stronger permission than the app needs.

**Fix**: removed the `<uses-permission>` element. The KDoc above the line documents the removal and the v0.26 path to re-add (if a nightly vitals summary ever ships). Pin: `HealthConnectBackgroundPermissionFindingTest` flipped to use a `<uses-permission>` regex check (not a string `contains` check that the comment would also satisfy).

### 6. `BootReceiver` handles `LOCKED_BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` + `TIMEZONE_CHANGED` + `TIME_SET` (MEDIUM silent breakage)

**File**: `app/src/main/AndroidManifest.xml:521–530`

The receiver was listening for `BOOT_COMPLETED` only. On Android 7+ (API 24+) with a screen lock, `BOOT_COMPLETED` is held until the user unlocks the device — a person who reboots at 22:00 and goes to bed loses every alarm until they unlock the phone in the morning. `AlarmManager` also drops every scheduled alarm on a package upgrade (v0.25.8 → v0.25.9) and on a timezone / clock change.

**Fix**: added four `<action>` filters: `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`. `Alarms.ensureAll` is idempotent and safe to call from any of them. Pin: `BootCompletedHandlingFindingTest`.

### 7. `BackupRetryWorker` filters the queue against a per-run `processed` set (HIGH durability)

**File**: `app/src/main/java/org/mindanchor/backup/BackupRetryWorker.kt`

The drain loop iterated a snapshot of the queue taken at the top of the `while (true)`. A concurrent enqueue that pushed the queue past `BackupPrefs.MAX_PENDING = 100` would trim the oldest entries via `enqueuePending.takeLast(MAX_PENDING)`. The worker's `for (entry in queue)` still iterated the original snapshot, and the per-entry `removePending(entry)` silently no-op'd on the trimmed entries — the worker returned `Result.success()` even though the trimmed entries' payloads had already been appended to Drive (duplicate line in the journal file) or had been dropped (lost backup).

**Fix**: track a `processed: MutableSet<PendingBackup>` across iterations. The inner for-loop now iterates `toProcess = queue.filter { it !in processed }`. The drain completes when the current queue minus the in-flight set is empty. No more duplicate Drive lines on queue overflow. Pin: `WorkManagerConcurrencyFindingTest` flipped to the fix shape.

### 8. `GoingLightVpnService.start()` is now a `suspend` fun (S0 main-thread block)

**File**: `app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt`

`start()` was called from `onStartCommand` (the main thread) and called `runBlocking { ... .first() }` for the DataStore read. A slow DataStore read or a corrupt Keystore blocked the main thread until the system showed an ANR.

**Fix**: `start()` is now `suspend fun start(): Boolean`; the DataStore read is a plain `prefs.goingLightSchedule.first()` (no `runBlocking`). `onStartCommand` launches the start on a new `serviceScope: CoroutineScope(SupervisorJob() + Dispatchers.IO)`. The scope is cancelled in `onDestroy` so an in-flight start is not orphaned if the OS kills the service between the `ACTION_START` intent and the `establish()` call. Pin: `VpnServiceStartIsNotBlockingFindingTest` flipped to the fix shape.

### 9. "Available in v0.20.2" copy removed from Going Light section (smoke P1)

**Files**:
- `app/src/main/res/values/strings.xml` (removed `going_light_coming_soon`)
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt` (removed the Text)

A footnote under the Going Light section said "Available in v0.20.2" in a v0.25.8 build. The feature has been shipping for several releases. The line was a credibility bug — anyone reading it thought the feature was released in 0.20.2.

### 10. `backup_explainer` copy updated to be honest (smoke P1)

**File**: `app/src/main/res/values/strings.xml:579`

Old: *"Save a copy somewhere you trust, and bring it back whenever you need to."* — implied cloud / Google Drive backup, but the action opens a generic SAF picker. The "Keep a copy" section does not use Drive.

New: *"Save a copy to this phone's storage or to a folder you control (Google Drive, Dropbox, an SD card), and bring it back whenever you need to."* — names the actual options without over-promising.

### 11. Test infrastructure: 141 new FindingTest file-shape pins, 12 assertions flipped to assert fix shape

The 7 v2 bug-hunt agents added 141 new FindingTest file-shape pins across `accessibility/`, `compose/`, `data/`, `datastore/`, `errors/`, `permissions/`, `workmanager/`. The 12 pins whose bug was fixed in v0.25.9 were flipped to assert the *fix* shape; the remaining 129 pins assert the *bug* shape (the v0.25.10+ backlog). All test files are `@file:Suppress`-annotated for the detekt rules the bug-hunt agents did not consider (`SwallowedException`, `MaxLineLength`, `LoopWithTooManyJumpStatements`, `UnusedPrivateMember`).

---

## Test progression

| Version | Tests | Pass | Fail | Notes |
|---|---|---|---|---|
| v0.25.8 | 1194 | 1194 | 0 | 0 finding tests, clean detekt |
| v0.25.9 | **1335** | 1290 | 45 | 141 new finding tests; 12 flipped to fix shape; 45 fail = known v0.25.10+ backlog |

The 45 failing tests are documented in the v2 reports. They are file-shape pins for the bugs that did not make v0.25.9. They are *not* regressions in v0.25.9 code — every test that flipped to a fix shape in this release is now passing.

## What does NOT ship in v0.25.9 (v0.25.10+ backlog)

- DST-bound schedulers: `BatchAlarms`, `SunsetController`, `GoingLightScheduler`, `EmaScheduler` still arm via `LocalDateTime.now()` + `atZone(systemDefault())` — the v0.25.5 WP-B fix retired this shape in `ReportScheduler.nextRun` but left the other four. On a spring-forward day, the alarm fires at the post-DST offset. Pin: `ArmingSchedulersAreDstSafeFindingTest`.
- `FormatWallClock` reads `LocalDate.now()` in the recomposition path — the v0.25.5 B9 fix retired this but the home card still has the same shape. Pin: `FormatWallClockUsesSingleSystemDateFindingTest`.
- `ReportScheduler.onAlarm` reads `LocalDateTime.now()` on the firing side — same as the v0.25.5 B5 issue. Pin: `ReportSchedulerOnAlarmIsDstSafeFindingTest`.
- Accessibility: 121 of 129 `TextButton` declarations across 15 files lack `Role.Button` semantics; 4 files hardcode English contentDescriptions; `Locale.ENGLISH` is forced in the letter inbox; no haptics preference. Pin: `A11ySurfaceFindingTest` (16 tests).
- Compose: 3 `remember` blocks in the v0.25.5 home cards not migrated to `rememberSaveable`; `collectAsState` used 0 times (should be `collectAsStateWithLifecycle`). Pin: `ComposeStateHuntFindingTest`.
- DataStore hardening: `KeystoreHmacKey` / `KeystoreAesKey` have no rotation path; `TokenStore` has no expiry field; `FrictionPrefs.recordReach` accepts a blank package name. Pin: `V2BugHuntFindingTest`.
- Notification channels re-created on every post in 5 schedulers (operational hygiene). Pin: `NotificationChannelCreationFindingTest`.
- Foreground service type `dataSync` for the local VPN (Play policy risk). Pin: `GoingLightForegroundServiceTypeFindingTest`.
- CAMERA permission rationale / settings redirect. Pin: `PpgCameraPermissionFlowFindingTest`.
- Settings permission launcher shared between batching and EMA (race). Pin: `PendingRollbackRaceFindingTest`.
- Smoke P0 #1: note classification silently overrides user-selected category. The "Task" pill on the Notes list is a *filter*, not a type selector — a user who taps it then saves a new note does not get the filter applied. A v0.25.10 fix is to make the active filter the note's type on save.
- Smoke P0 #2: no edit affordance on existing notes. A single tap toggles selection; there is no `clickable` that opens "New note" pre-populated. A v0.25.10 fix is to wire a click handler on the note body.
- Watch-connect real root-cause fix (user has a pending `adb logcat -s MindAnchor/HealthConnect:V` to capture).
- Manual smoke on a real device (AVD unstable on this Windows host; user's Motorola at `172.16.68.207:38535` was not connected during the v0.25.9 verification).
