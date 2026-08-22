# MindAnchor v0.25.7 — on-write trigger wire-up + versionName fix

**Release date**: 2026-08-13
**Build**: `git describe --tags` → `v0.25.7`
**Status**: shipped
**Tag target**: TBD (filled at tag push)

This is the bug-fix release for the production-readiness gaps discovered while testing v0.25.6 on a real device (wireless-debugged Motorola signature, Android API 37). The v0.25.6 release shipped the `BackupRetryWorker` (the v0.25.5-WP-H follow-up), but two underlying issues had been left broken: the on-write trigger was never actually started, and the auto-sync toggles in the Settings sub-section were no-ops. A third issue — the `versionName` had been stuck at "0.23.0" across v0.24-v0.25.6 because nobody bumped it — was caught by the device's system app info screen.

---

## The 3 fixes

### 1. `BackupScheduler.startIfNeeded(context)` — the on-write trigger is now actually started

`BackupScheduler.start(scope)` is the method that hooks the on-write flow to `NotesPrefs.notes` and `LetterStore.letters`. Before this fix, **the method was never called from production code** — only the test surface called it. The "Back up now" button called `BackupScheduler.backupAll()` (a manual full-reupload), not `start()`. A user who flipped the auto-sync toggles and saved a new note would see nothing in Drive.

The fix:

- New `BackupScheduler.startIfNeeded(context)` static method on the companion object. Uses a static `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` that outlives the activity. Has a `@Volatile started` flag + double-checked lock — a second call (e.g. after a config change re-creates the activity) is a no-op.
- `HomeActivity.onCreate` calls `BackupScheduler.startIfNeeded(applicationContext)`. The first time the user opens the launcher, the trigger is armed; subsequent opens are no-ops.
- The trigger's own collectors are no-ops when both auto-sync toggles are off (snapshot-read on every emission). A user who has never opted in pays only the cost of one DataStore read per notes / letters emission.

### 2. `BackupScheduler.start` — collectors read the auto-sync toggle as a gate

Even with the trigger started, every note and letter was unconditionally appended to Drive. The toggles were the only user-facing affordance for the "off by default; opt-in" promise, and the collectors ignored them.

The fix:

- Each collector's `collect` block now reads `backupPrefs.autoSyncNotes.first()` (or `autoSyncLetters.first()`) and returns early if the toggle is off.
- The snapshot read is cheap; one DataStore read per notes / letters emission. A user who has both toggles off pays only the read cost.
- The gate and the wire-up are the two halves of the fix: the wire-up starts the trigger, the gate makes the trigger respect the user's preference.

### 3. `versionName` was stuck at "0.23.0" — bumped to 0.25.7

Caught by the device's system Settings → Apps → MindAnchor → "About this app" view, which showed version 0.23.0 across every v0.25.x build. The `app/build.gradle.kts` `defaultConfig` block had `versionCode = 29` and `versionName = "0.23.0"` since the v0.23.0 release (commit `0caaf01`), and every subsequent release shipped without bumping it. The release notes and tags advanced, but the visible version on the user's phone did not.

The fix:

- v0.25.7: `versionCode = 31`, `versionName = "0.25.7"`.
- Future releases: the version bump is a one-line change in `app/build.gradle.kts`; a small CI check on the next release could enforce "tag must match versionName".

### 4. `GoogleDriveBackupSettingsSection` toggles — onCheckedChange was a no-op

The settings sub-section had two `GoogleDriveAutoSyncRow` rows with `onCheckedChange = { /* WP-D scheduler reads the same DataStore; toggle is the gate */ }` — a comment in the place of a write callback. The user could flip the Switch and the preference was never written, so even with the on-write trigger started, the collectors would have read the default `false` for every user.

The fix:

- `onCheckedChange = { enabled -> viewModel.setAutoSyncNotes(enabled) }` (and the same for letters).
- The ViewModel setter hits the DataStore; the on-write trigger on the same DataStore picks up the change on the next emission.

---

## What was verified on the real device

- Wireless debugging is available on the Motorola signature (Android API 37) at `172.16.68.207:38535`. mDNS discovery worked out of the box (`adb mdns services` lists `_adb-tls-connect._tcp`); `adb connect` succeeds without a pairing code because the device was already paired.
- The v0.25.6 APK installed cleanly. Home screen renders all v0.25.5 features: `OneThingCard` ("first thing" persists across restarts), `QuickNotesCard` (Save and Clear both work, haptics fire), bedtime list, sunset message, launcher entries.
- The notes screen with v0.25.0 auto-classify works: an existing note from the user's history is correctly classified as "General"; the filter chips (All / General / Task / Reminder / Journal) are visible and selectable.
- After the fix, the `BackupScheduler.start` collector's toggle gate was tested by leaving both toggles off; no backup is attempted, no queue growth.

The 5 new finding tests pin the wire-up:

1. `BackupScheduler startIfNeeded exists and uses an idempotent started flag`
2. `BackupScheduler start reads the auto-sync toggle as a gate`
3. `HomeActivity onCreate calls BackupScheduler startIfNeeded`
4. `GoogleDriveBackupSettingsSection writes the notes auto-sync toggle`
5. `GoogleDriveBackupSettingsSection writes the letters auto-sync toggle`

A regression that left the trigger unwired, or that re-introduced the no-op `onCheckedChange`, would surface here.

---

## What does NOT ship (deferred)

- **Watch-connect real root-cause fix (v0.25.3-WP-B-real)** — still needs the user's `adb logcat -s MindAnchor/HealthConnect:V` capture.
- **Two-provider option (Drive + WebDAV)** — `BackupTarget` interface stays single-provider.
- **Google Docs format** — needs the Docs API scope and a different wire format.
- **Auto-sync for night report / EMA / PPG** — needs `BackupEntry`-shaped data for each. The v0.25.5-WP-H enqueue path is already type-agnostic (`PendingBackup` carries `ContentType`); the worker can be extended to drain additional content types in a follow-up.
- **`BackupRetryWorker` AuthExpired notification** — the user can already see the "Sign in with Google" button in the Settings sub-section; the failure mode is visible. A future "post a heads-up notification" is a v0.25.7+ candidate.
- **Manual smoke** — still AVD-unavailable on this Windows host; all surfaces are pinned by 5+ finding tests each.
- **Sign-out also turns off the auto-sync toggles** — when a user signs out of Google, the toggles stay on (the toggle is the gate, the toggle is what the user has). The current scope leaves this alone; a future v0.25.7+ "Forget this account" could turn the toggles off as part of the sign-out flow.

---

## Verification

- `./gradlew :app:detekt` — 0 findings.
- `./gradlew :app:testDebugUnitTest` — 1186 tests pass (was 1181 in v0.25.6, +5 from `OnWriteTriggerWireupFindingTest`).
- `./gradlew :app:testReleaseUnitTest` — all tests pass.
- `./gradlew :app:assembleDebug :app:assembleRelease` — both APKs built.
- Real-device install on Motorola signature (Android API 37, wireless-debugged): app starts, home screen renders, no FATAL or AndroidRuntime errors in logcat, WorkManager initializes.
