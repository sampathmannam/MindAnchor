# Program 0 continuity runbook: offline, background, battery, process-death, and replacement-phone behavior

## What this document is

A precise, followable procedure for physically verifying the properties
Task 14's brief asks for that no JVM or single-emulator instrumented test
can prove: a real signed APK, a real Google account, a real second
physical device, a real 24-hour battery-measurement window, and real
process death under real Android memory pressure (not a `runBlocking`
coroutine cancellation standing in for one).

**This document was written as Task 14's deliverable. Steps 4, 5, and 6
below have not been physically executed.** This environment has exactly
one Android emulator (`MindAnchorTest`, API 34), no physical device, no
second device, no real Google account configured for automated sign-in,
and no ability to hold a 24-hour wall-clock measurement window open.
Executing the physical-device portions of this procedure is explicitly
owner/whoever-has-hardware-access's job, not this task's. Nothing below,
nor in `program-0-continuity-log.md` / `program-0-battery-log.md`, should
be read as a claim that these steps have been run on real hardware.

What Task 14 **did** genuinely automate and run, in this same environment,
against this same commit:

- `ContinuityRoundTripTest.kt` (`app/src/androidTest/java/org/mindanchor/continuity/`) —
  a full capture → encrypt → wipe → restore → recapture round trip, on the
  real emulator, using an in-memory Room database and the app's real
  on-device DataStores. This proves the entire pipeline composes
  end-to-end for the first time; see the Task 14 report for the real
  hash-equality and field-equality evidence.
- `OfflineStartupBoundaryTest.kt` (`app/src/test/java/org/mindanchor/continuity/`) —
  a JVM source-scan pinning that `HomeActivity.kt`, `JournalActivity.kt`,
  `JournalViewModel.kt`, `ContinuitySnapshotRepository.kt`, and
  `RestoreCoordinator.kt` never reference `okhttp3`, `GoogleDriveObjectStore`,
  or `currentAccessToken` — the source-level half of "the app opens
  offline."
- The full automated verification set: `testDebugUnitTest`,
  `connectedDebugAndroidTest`, `koverXmlReportDebug`, `lintDebug` — see
  the Task 14 report for real counts.

Neither of those two new tests, nor anything already in the suite, can
prove a *real* second device receives a *real* Drive-uploaded backup, or
that a *real* phone survives 24 real hours of ordinary use without an
excessive battery cost. That is what this runbook is for.

## What is already proven automatically, and what genuinely is not

Before repeating work on a real device, read this table — several of the
Step 5 sub-cases the brief calls out already have a solid automated
equivalent in this codebase; a few genuinely do not.

| Brief sub-case | Automated equivalent | Verdict |
|---|---|---|
| Force-stop right after the staged file is **downloaded**, relaunch, verify resume with no duplicates | `RestoreResumeTest.interruptedRightAfterDownloadedResumesToTheSameEndStateAsACleanRun` (`app/src/androidTest/java/org/mindanchor/continuity/RestoreResumeTest.kt`) — real in-memory Room + real on-device DataStores, injected failure right after `DOWNLOADED` is persisted | **Already proven on-device.** This manual step re-confirms against a real process kill (not a caught exception) and real Drive-downloaded bytes. |
| Force-stop right after **decrypt**, relaunch, verify resume with no duplicates | `RestoreResumeTest.interruptedRightAfterDecryptedResumesToTheSameEndStateAsACleanRun` | **Already proven on-device.** Same caveat as above. |
| Force-stop right after **Room-merge**, relaunch, verify resume with no duplicates | `RestoreResumeTest.interruptedRightAfterRoomMergedResumesToTheSameEndStateAsACleanRun` | **Already proven on-device.** Same caveat as above. |
| Force-stop right after **DataStore-merge**, relaunch, verify resume with no duplicates | `RestoreCoordinatorTest.\`interrupted right after DATASTORES_MERGED resumes to the same end state as a clean run\`` (`app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt`) **and** `RestoreResumeTest.interruptedRightAfterDataStoresMergedResumesToTheSameEndStateAsACleanRun` (`app/src/androidTest/java/org/mindanchor/continuity/RestoreResumeTest.kt`) | **Already proven on-device.** Same caveat as the other `RestoreResumeTest` rows above — this manual step re-confirms against a real process kill, not a caught exception. |
| Upload a deliberately corrupted `Latest` file; verify restore falls back to the newest decryptable versioned snapshot | *(none found)* | **Not covered by any existing automated test**, at either the JVM or androidTest level. `RestoreCandidate.kt`'s `RestoreCandidateSelector.select` implements the fallback (`CandidateSelectionResult.Found(candidate, usedFallbackFrom = ContinuityFiles.LATEST)` when the `LATEST` object is missing/corrupt/an unsupported version), and `RestoreScreen.kt` renders the "Latest was unreadable" UI text for it, but no `RestoreCandidateSelectorTest.kt` (or equivalent) exists anywhere in the tree — confirmed by search. This is a genuine, real gap, not just an untested-on-real-hardware case. Whoever runs this step should treat it as first-time coverage, not a re-confirmation. |
| Enter a wrong recovery key; verify zero local data mutation | `RestoreCoordinatorTest.\`a wrong recovery key is reported distinctly from corruption and mutates nothing\`` **and** `RestoreScreenTest.wrongRecoveryKeyIsReportedDistinctlyAndNothingIsStaged` (`app/src/androidTest/java/org/mindanchor/continuity/RestoreScreenTest.kt`) — the latter drives the real Compose UI and asserts the staging file is never written | **Already well proven**, both at the coordinator level and the on-screen UI level. This manual step re-confirms against a real typed-by-hand key on a real keyboard and a real Drive-hosted candidate. |
| Revoke Google access; verify local use remains normal and backup health says "Needs sign-in" | `BackupHealthTest.\`AUTH error code is NeedsSignIn\`` (`app/src/test/java/org/mindanchor/continuity/BackupHealthTest.kt`) proves the pure domain mapping (`ContinuityErrorCode.AUTH` → `BackupHealth.NeedsSignIn`) | **Domain logic proven; the real revocation is not, and cannot be, automated here.** This environment has no real Google account to revoke access from. Genuinely pending manual execution. |

Step 4 (real signed APK + real Google sign-in + a real second device) and
the 24-hour battery window (Step 6) have no automated equivalent at all —
they are physical-device-only by nature and are listed as fully pending
below.

## Prerequisites

- Two physical Android devices (or one physical device plus a factory-reset
  or freshly-provisioned second one) — a real replacement-phone scenario
  does not reproduce meaningfully on a single emulator, since the whole
  point is a genuinely different device identity, storage, and Google
  account session.
- A real signed release APK (see `docs/RELEASING.md` — as of this task,
  the release keystore itself is still an owner-only pending step; this
  runbook cannot be completed until a real signed APK exists).
- A real Google account with Drive access, used consistently across both
  devices for the backup/restore steps.
- `adb` on the machine driving the test, with both devices authorized.
- A stopwatch or wall-clock reference for the 24-hour battery window
  (Step 6) — this genuinely takes a full day; do not compress it.

## Step 4: Real replacement-phone restore

1. On device A, install the signed release APK, sign in with the real
   Google account, generate and record the recovery key (write it down;
   never paste it into this runbook or any log — see the standing
   instruction in `program-0-continuity-log.md`), enable continuity
   backup, and create fixtures that exercise everything
   `ContinuityRoundTripTest.kt` already proves round-trips in-process:
   a multiline Journal entry, a morning measure, a Quick Note, a Letter
   (read), a frictioned app, and an always-open app.
2. Wait for (or manually trigger) a checkpoint or nightly snapshot upload
   to complete — confirm via the backup-health UI that it shows
   `Verified`, not `Pending`.
3. On device B (the "replacement phone"), install the same signed APK,
   sign in with the same Google account, and open the restore flow.
   Enter the recovery key from step 1.
4. Confirm the candidate preview shows the correct counts (per
   `RestoreScreenTest.candidatePreviewShowsCountsAndMetadataButNeverJournalBodyOrTitleText`'s
   already-proven UI contract: counts and metadata only, never Journal
   body or title text) and confirm the restore.
5. Confirm every fixture from step 1 is present on device B, byte-for-byte,
   and that the backup-health UI on device B shows `Verified` with a
   content hash. Record the source and restored content hashes in
   `program-0-continuity-log.md` (hashes only — never the underlying
   Journal text or the recovery key).
6. Run the restore a second time on device B (or force-stop and relaunch
   mid-restore, per Step 5 below) and confirm no duplicates appear.

## Step 5: Failure-mode drills

Run each row from the table above that is not already marked "well
proven" — specifically, prioritize the two genuinely under-tested rows:

### 5a. Force-stop right after DataStore-merge (the one real gap in RestoreResumeTest's coverage)

1. Stage a restore on a real device (begin the restore, let it reach
   `ROOM_MERGED`).
2. Force-stop the app (`adb shell am force-stop org.mindanchor`) as close
   as practically achievable to the moment the DataStore merge (Notes /
   Letters / friction sets / legacy backup import) is in flight — this is
   inherently timing-sensitive on a real device in a way an injected
   exception in a test is not; a few retries may be needed to land inside
   the window.
3. Relaunch the app. `RestoreCoordinator.resumeIfPending` should self-repair
   automatically on `HomeActivity.onCreate` (no user prompt).
4. Confirm the restore reaches `VERIFIED` and that Notes/Letters/friction
   sets show no duplicates.

### 5b. Corrupted Latest file → fallback to a versioned snapshot

This is the one sub-case with **no existing automated coverage at any
level** — treat it as genuinely new ground, not a re-confirmation.

1. With continuity backup enabled and at least one nightly versioned
   snapshot already uploaded (not just the mutable `Latest` object),
   manually corrupt the `Latest` object in Drive (e.g. overwrite its bytes
   with garbage via the Drive web UI or API) without touching the
   versioned snapshots (named `MindAnchor-Continuity-Snapshot-<timestamp>-<snapshotId>.mab`,
   see `ContinuityFiles.kt`).
2. Start a restore. Confirm the UI shows the "Using backup from ... (Latest
   was unreadable)" fallback affordance (`RestoreScreen.kt`'s
   `usedFallbackFrom` rendering) and that the restore proceeds against the
   newest decryptable versioned snapshot instead of failing outright.
3. Confirm the restored content matches that versioned snapshot's own
   content hash, not garbage.

### 5c. Wrong recovery key (re-confirmation only — already well proven)

Enter a syntactically-valid but wrong recovery key against a real staged
candidate; confirm the UI reports "Wrong recovery key," confirm nothing
was staged (`adb shell run-as org.mindanchor ls files/continuity/` should
show no `restore-staged.mab`), and confirm no local data changed.

### 5d. Revoke Google access

1. With continuity backup enabled and previously `Verified`, revoke the
   app's Drive access from the real Google account's third-party-access
   settings (myaccount.google.com → Security → Third-party access).
2. Confirm ordinary local app use (Journal, Notes, Letters, morning
   measure) continues working normally with no crash and no forced
   re-authentication prompt blocking the UI.
3. Wait for (or trigger) the next checkpoint/nightly attempt; confirm it
   fails with an auth error and the backup-health UI shows "Needs
   sign-in" (`BackupHealth.NeedsSignIn`, per the already-proven
   `BackupHealthTest` domain mapping — this step is the real-account
   half of that same logic).

## Step 6: 24-hour battery and background-work audit

This step has no automated equivalent at all — it requires a real device
left in real, ordinary use (not idle on a charger) for a real 24-hour
wall-clock period.

1. Install the signed release APK on a real device with continuity backup
   enabled and at least one Journal/Notes/Letters fixture already present.
2. Capture a `dumpsys batterystats` baseline
   (`adb shell dumpsys batterystats --reset` then, after the window,
   `adb shell dumpsys batterystats > after.txt`) at the start of the
   24-hour window.
3. Use the device normally for 24 hours — do not leave it idle on a
   charger the whole time; ordinary use is the point.
4. At the end of the window, capture the "after" `batterystats` dump,
   and record in `program-0-battery-log.md`:
   - the checkpoint count (how many `CheckpointBackupWorker` runs
     actually fired — each on-write save requests one, replacing any
     already-queued run per `ContinuityWorkScheduler.CHECKPOINT_WORK_NAME`'s
     `ExistingWorkPolicy.REPLACE`)
   - whether the nightly snapshot (`NightlySnapshotWorker`, targeted for
     local 02:00 per `ContinuityWorkScheduler.NIGHTLY_TARGET_TIME`, subject
     to `setRequiresBatteryNotLow(true)`) actually completed
   - total bytes uploaded to Drive over the window
   - a wake-lock / exact-alarm / foreground-service / permission audit —
     confirm the app is not holding a partial wake lock or an exact alarm
     it does not need (continuity backup uses ordinary WorkManager
     constraints, not `setExactAndAllowWhileIdle`, so there should be
     none to find; this step is the real-device confirmation of that)
   - device model, Android version, battery-optimization state
     (whether the OS has the app on its own restricted/unrestricted list),
     and network conditions (Wi-Fi vs. mobile data) during the window

Per the standing instruction already in `program-0-battery-log.md`: do
not invent a battery threshold after seeing the result. Record the
observation; a formal threshold is Program 2 sensor-work territory, not
this task's.

## What this runbook does not cover

- Performance or UI-latency measurement — out of scope for a
  continuity/battery runbook.
- Real signed-APK reproducibility (`tools/verify-reproducible-release.sh`)
  and the release-signing setup itself — that is Task 13's
  `docs/RELEASING.md` §6 and `docs/qa/program-0-upgrade-runbook.md`'s
  territory, not repeated here.
