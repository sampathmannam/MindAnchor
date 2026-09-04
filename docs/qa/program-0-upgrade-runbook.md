# Program 0 upgrade runbook: v0.70.x → v0.71.0

## What this document is

A precise, followable procedure for physically verifying that a real
phone running the last signed v0.70.x build upgrades cleanly, in place,
to v0.71.0 — old data intact, new Room migrations applied correctly, no
network required on first launch.

**This document was written as Task 13's deliverable. It has not been
physically executed.** This environment has no physical Android device
and no historical signed v0.70.x APK artifact to install from (the
project's first signed release does not exist yet — see
`docs/RELEASING.md` §6, still an owner-only pending step at the time
this runbook was written). Executing this procedure on real hardware is
explicitly Task 14's job, not this one's. Nothing below should be read
as a claim that the upgrade has been tested; it is the script for
whoever does test it.

## Prerequisites

- Two APKs:
  - the last APK signed with the project release key before this task's
    version bump (i.e. `versionCode` ≤ 94, `versionName` a `0.70.x`),
    from a prior official GitHub Release
  - the v0.71.0 APK this task's `versionCode = 95` bump produces, built
    and signed per `docs/RELEASING.md`
- A real Android device or emulator image at API 33+ (the app's
  `minSdk`), ideally a real device — process death, doze, and OEM
  battery-management quirks that matter to a mental-health app that
  holds a safety plan don't reproduce reliably on an emulator.
- `adb` on the machine driving the test, with the device authorized.
- Airplane mode available on the device, to verify the no-network claim
  in step 8.

## Procedure

### 1. Install the last signed v0.70.x APK

```sh
adb install path/to/mindanchor-v0.70.x.apk
```

Confirm the install succeeded and the app opens (a fresh v0.70.x
install, or an existing one already on the device — either is a valid
starting point, but note which one this run used).

### 2. Create fixtures that exercise every kind of data the upgrade must preserve

Do this by hand, through the app's real UI (not via `adb shell am
instrument` seeding — the point is to prove the *installed app*, not a
test harness, wrote the data the upgrade must carry forward):

- **Notification**: let at least one real notification get held by the
  launcher (or trigger the seeding path the app already exposes for
  this, if driving real notifications from other apps is impractical on
  the test device).
- **Safety plan**: fill in the safety plan screen with distinguishable
  placeholder content (a name, a coping step, a contact) that can be
  visually confirmed unchanged after the upgrade.
- **JournalStore entry**: write at least one journal entry through the
  Journal UI.
- **Note**: create at least one Note.
- **Letter**: write and save at least one Letter (a future-dated letter
  to self).

Record (screenshot or written note) the exact content of each fixture
before moving on — this is the "before" state step 6 compares against.

### 2a. Note what "before" cannot mean here

v0.70.x has no continuity/research-export feature at all — it is
entirely new in Program 0 (Tasks 1–12 on this branch). There is no
v0.70.x-produced continuity content hash to compare against; asking
"does the v0.71.0 export match the v0.70.x export" is not a question
that can be asked, because v0.70.x never produced one. Do not attempt to
construct or fake a "before" hash for this feature. What step 9 below
compares instead is a real, executable check: hash the v0.71.0 export
immediately after the upgrade, then hash it again after a subsequent
uninstall/reinstall-plus-restore-from-backup cycle (that full backup/
restore round-trip is Task 14/15 territory once device testing exists;
this runbook step exists so whoever runs it knows exactly what
comparison to make and why the "before" side of it doesn't exist yet).

### 3. Install v0.71.0 over the existing install

```sh
adb install -r path/to/mindanchor-v0.71.0.apk
```

`-r` is required — a plain `install` would refuse to replace an existing
package, and a bare uninstall-then-install would defeat the entire point
of this test (that is exactly the destructive path the app must never
force a user into). Confirm:

- the install succeeds (Android's own signature check passing here is
  itself informative — a mismatched signing key would make `adb install
  -r` fail outright, independent of anything the app's own code does)
- Android does not report a downgrade conflict (it would if
  `versionCode` in the v0.71.0 build were not genuinely greater than the
  v0.70.x build's — see `ReleaseSafetyTest`'s `versionCode > 94` check,
  which pins this at the source level but cannot prove the two real APKs
  compare correctly the way this on-device step can)

### 4. Verify the Room migration path ran, not a destructive recreate

`AnchorDatabase` is at schema version 6 as of this task
(`app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt`), reached
via `MIGRATION_1_2` through `MIGRATION_5_6`. Two paths matter for this
runbook, matching the schema versions real v0.70.x installs could be at:

- **3→4→5→6**: an install that predates the v0.70.x `tier` column
  removal (`MIGRATION_4_5`'s subject).
- **4→5→6**: a v0.70.x install already at schema version 4.

To confirm which path an actual device install exercises, and that it
succeeds without `fallbackToDestructiveMigration` ever firing (there is
none in the source —
`app/src/test/java/org/mindanchor/release/ReleaseSafetyTest.kt` pins
that at the JVM level, but this step is the on-device proof it's not
just absent from source, it's not needed):

```sh
adb logcat -c
# launch the app (open it from the launcher, or:)
adb shell am start -n org.mindanchor/.MainActivity
adb logcat -d | grep -i -E "room|migrat|AnchorDatabase|IllegalStateException"
```

A successful migration shows no Room exception in logcat and the app
opens to its normal home screen rather than a crash loop. If Room's
schema-verification step ever fails (e.g. an unrecorded manual schema
edit), it throws immediately on database open with a message naming the
identity-hash mismatch — that failure would be unmistakable in this log
capture.

The fixture-APK/database half of this step —constructing standalone v3
and v4 SQLite databases from `app/schemas/` and running the migrations
against them directly, the way
`app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt`
already does in CI — is covered by that existing instrumented test, not
by this manual runbook; this step is specifically the *real upgrade
install* proof the JVM/instrumented suite can't produce on its own
(a genuine `adb install -r` over a genuine prior install, not a
synthetic pre-populated database file).

### 5. Confirm the app opens without a network connection

Put the device in airplane mode *before* the first post-upgrade launch
(if step 4 already opened the app, force-stop it and relaunch under
airplane mode to get a clean first-launch-after-upgrade measurement):

```sh
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE
adb shell am force-stop org.mindanchor
adb shell am start -n org.mindanchor/.MainActivity
```

Confirm the app opens normally and the fixtures from step 2 are visible.
This is a direct on-device check of the project's standing no-network
promise (`NetworkCallsForbiddenTest` in the JVM suite pins the source
side of the same promise) — the first launch after an upgrade is exactly
the moment a background migration or "check for update" call would be
most tempting to add, and exactly the moment this app must not make one.

### 6. Confirm every step-2 fixture is intact

With the app still in airplane mode (or restore normal connectivity
first if some check needs it — record which), walk through each
fixture created in step 2 and confirm its content is unchanged:

- Held notification still present
- Safety plan content byte-for-byte the placeholder text entered in
  step 2
- JournalStore entry still present with its original content
- Note still present with its original content
- Letter still present with its original content

### 7. Confirm the new Journal import path works on the upgraded install

Program 0 (Task 6/12's `JournalLegacyImporter` / Journal Patterns work)
is new in v0.71.0. On the freshly-upgraded install, exercise the Journal
import path the app exposes and confirm it runs without error against
the pre-existing (v0.70.x-created) data — this is the "and new Journal
import" half of the brief's requirement, distinct from "old data
survived unmodified" (step 6).

### 8. Export the post-upgrade continuity content hash

Through Settings → the continuity/backup section this task's sibling
tasks built, run "Export research JSON" (see
`app/src/main/java/org/mindanchor/continuity/ResearchExportBuilder.kt`)
and record the resulting `contentSha256` (the UI shows a truncated
12-character prefix of it, per `ResearchExportBuilder.truncatedHash`).

This is the v0.71.0-immediately-after-upgrade baseline — see §2a above
for why there is no v0.70.x-side value to compare it against.

### 9. Second comparison point: uninstall/reinstall + restore

Once Task 14/15's device testing performs a full backup → uninstall →
reinstall → restore cycle, repeat step 8's export and compare the
`contentSha256` against the value recorded there. A match proves the
continuity data survives a full device-loss-and-recovery cycle, not just
an in-place version upgrade. This step is listed here for completeness
(the brief explicitly calls for this comparison) but its execution
depends on infrastructure (a working backup/restore round-trip on real
hardware) that belongs to Task 14/15, not this task.

## What this runbook does not cover

- Performance or battery impact of the upgrade — out of scope for a
  correctness runbook.
- Multiple-version-skip upgrades (e.g. a very old v0.6x install jumping
  straight to v0.71.0) — the migration chain
  (`MIGRATION_1_2` … `MIGRATION_5_6`) is written to support this, but
  this runbook only specifies the two paths (3→4→5→6, 4→5→6) the brief
  named as the realistic v0.70.x starting points.
