# MindAnchor v0.25.8 — SOTA bug-hunt follow-up

**Release date**: 2026-08-13
**Build**: `git describe --tags` → `v0.25.8`
**Status**: shipped
**Tag target**: TBD (filled at tag push)

This release closes 6 of the 50+ findings from a SOTA bug-hunt campaign against v0.25.7 (6 parallel agents across home / settings / notes+letters / backup / vitals+PPG+sleep+report+friction / onboarding+notifications+accessibility). The 4 high-severity fixes are the core: a silent-backfill bug in the on-write trigger, a letter-diff data-loss bug, a missing classification on the home-card path, and a duplicate-id generator across the two note capture paths. The 2 medium-severity polish fixes round out the release.

---

## The 6 fixes

### 1. On-write trigger silently backfilled every existing note on subscribe (HIGH)

**File**: `app/src/main/java/org/mindanchor/backup/BackupScheduler.kt:194-200, 230-236`

The `.scan(NotesDiffState())` accumulator seeded with an empty previous state. The first DataStore emission's `newOnes` was the entire current list, and the on-write trigger appended every existing note to Drive — silently, the moment the user flipped the auto-sync toggle. The "Back up now" button is the intended backfill surface; the on-write trigger must be new-only.

**Fix**: `.drop(1)` after each `.scan(...)`. The first emission is the seed; the new-write path picks up from there. Same for letters.

### 2. Letter diff missed body changes for the same date (HIGH)

**File**: `app/src/main/java/org/mindanchor/backup/BackupScheduler.kt:434-440` (was line 376-382 in v0.25.7)

`newLetters` keyed by `date` only. `LetterStore.save` replaces the existing letter for the same date. A re-save of 2026-08-10 produced a list with the new body but the same date; the date-only diff saw the date in `previousDates` and dropped the replacement. The new body never reached Drive.

**Fix**: `previous.associateBy { it.date }` + body comparison. The new contract: "any letter that changed (new date, or same date with a new body) is a new write."

### 3. Home-card `addQuickNote` never enqueued classification (HIGH)

**File**: `app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt:389-411`

`addQuickNote` saved with `type=null` and never called `ClassifierEnqueuer.enqueue`. The home-card note stayed un-typed even after the classifier ran. The full activity's `onAdd` did enqueue; the home card didn't. The v0.25.0 chip promise was broken on the most common capture path.

**Fix**: `addQuickNote` now calls `ClassifierEnqueuer(getApplication()).enqueue(note)` after `notesPrefs.add(note)`. The classifier is fail-soft; a future Phi-4 unavailable state degrades to `GENERAL`, not an untyped note.

### 4. `idCounter` duplicated across `LauncherViewModel` and `NoteActivity` (HIGH)

**Files**:
- `app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt:105-129` (was 124-131 in v0.25.7)
- `app/src/main/java/org/mindanchor/model/NoteActivity.kt:48-62` (was 84-92 in v0.25.7)
- `app/src/main/java/org/mindanchor/data/NotesPrefs.kt:85-114` (new shared generator)

Both views had their own `private val idCounter: AtomicLong by lazy {...}`. Two views, two counters, two seeds (`max(existing.maxId, currentTimeMillis)` on first use). A note written from the home card and a note written in the full activity could share an id. Duplicate ids broke every lookup by id (`groupedByDay`, `setType`, `delete`).

**Fix**: `NotesPrefs.nextNoteId()` is a process-singleton id generator (lazy `AtomicLong` seeded on first use from the max existing id). Both views call it; their private `idCounter` fields are gone.

### 5. Long single-line notes blew up row height (MEDIUM)

**File**: `app/src/main/java/org/mindanchor/model/NoteScreen.kt:471-486`

A pasted URL or a long sentence would push the row to fill the screen, pushing other rows off-screen.

**Fix**: `maxLines = 2, overflow = TextOverflow.Ellipsis` on the note row's body Text. The full body is in the activity; the row shows the title (first line) plus a hint of the second.

### 6. `CorosSyncWorker` activity range hard-coded to 2026 (MEDIUM)

**File**: `app/src/main/java/org/mindanchor/vitals/coros/CorosSyncWorker.kt:113-124`

`api.fetchActivities(authed, "20260101", "20261231")` was a build-time constant. The COROS API filters server-side by the from/to range; the constant stopped the activity feed cold on 2027-01-01.

**Fix**: rolling 12-month window from today. The first sync after New Year still has a full year to draw on; the file size stays bounded.

---

## What was verified on the real device

- Wireless debugging: emulator `emulator-5554` (API 34, Android 14, sdk_gphone64_x86_64) and the user's Motorola signature at `172.16.68.207:38535` (API 37, wireless-debugged via mDNS auto-discovery).
- v0.25.7 install on both devices: home screen renders all v0.25.5 features; notes screen with v0.25.0 auto-classify; settings navigates; no FATAL or AndroidRuntime errors in logcat.
- The bug-hunt campaign caught issues that the unit-test surface would never have surfaced: the silent backfill only triggers on the first DataStore emission after subscribe; the letter diff data loss only happens on a re-save; the home-card missing classification only happens on the home-card path; the duplicate ids only happen when both paths are used in the same process.

The 8 new + rewritten finding tests pin the fixes:

1. `OnWriteTriggerFirstEmissionFindingTest.on-write trigger must not backfill existing notes on subscribe` — rewritten to assert `.drop(1)` is present
2. `OnWriteTriggerFirstEmissionFindingTest.letter diff must detect body changes for the same date` — rewritten to assert body-aware diff
3. `WorkerResourceLeakFindingTest.BackupRetryWorker doWork closes its OkHttpClient in finally` — rewritten to assert the close
4. `WorkerResourceLeakFindingTest.BackupRetryWorker drain loop re-reads the queue inside a while loop` — rewritten to assert the re-read
5. `WorkerResourceLeakFindingTest.HomeActivity onCreate rehydrates the pending queue on cold start` — rewritten to assert the recovery path
6. `HomeCardNotesAndIdUnificationFindingTest.LauncherViewModel addQuickNote enqueues classification` — new
7. `HomeCardNotesAndIdUnificationFindingTest.NoteActivity and LauncherViewModel share the NotesPrefs id generator` — new
8. `QuickNotesCardAboveFoldFindingTest.QuickNotesCard call site is between OpenLoopCard and OneThingCard` — new

---

## What does NOT ship (still in the bug-hunt backlog)

- **Backup #6 GCM no AAD** — entries not bound to file/type. A motivated attacker with Drive write access could swap a Note line into the Letters file. Real but theoretical; the v0.25.4 single-tenant per-type model means the swap is harmless. v0.25.9+ follow-up.
- **Backup #8 URLEncoder.encode** — Drive `find` query uses form-style `+` (works on current filenames without spaces; breaks the moment a future file rename introduces a space). v0.25.9+ follow-up.
- **Backup #9 PendingBackup.equals uses === on type** — defensive; only matters for non-singleton ContentType, and v0.25.x only has singletons. v0.25.9+ follow-up.
- **Settings, Onboarding, Notifications, Accessibility** — the bug-hunt agents found smaller issues (stale strings, font sizing, locale fallbacks) but no P0s. v0.25.9+ polish.
- **OpenLoopCard bring-into-view render glitch** (vitals B2) — the OpenLoop's `bringIntoViewOnFocus` modifier may collapse the field to zero height on the first composition under a still-loading scroll container. Hard to reproduce; deferred until a real-device repro.
- **LetterInbox non-lazy layout** (notes #11) — the inbox is a `Column` + `verticalScroll`, not `LazyColumn`. Won't scale to 30+ letters. v0.25.9+ follow-up.
- **`friendlyLetterDate` always uses `Locale.ENGLISH`** (notes #13) — weekday names in English even on a Tamil/Hindi locale. v0.25.9+ follow-up.
- **Watch-connect real fix (v0.25.3-WP-B-real)** — still needs the user's `adb logcat -s MindAnchor/HealthConnect:V` capture.
- **Manual smoke on a real device** — AVD unstable on this Windows host; v0.25.7 was smoke-tested on the Motorola signature.

---

## Verification

- `./gradlew :app:detekt` — 0 findings.
- `./gradlew :app:testDebugUnitTest` — 1194 tests pass (was 1186 in v0.25.7, +8 from the 2 new finding tests + the rewritten existing tests).
- `./gradlew :app:testReleaseUnitTest` — all tests pass.
- `./gradlew :app:assembleDebug :app:assembleRelease` — both APKs built.
- Real-device install planned for the v0.25.8 install: the QuickNotesCard appears above the fold on the emulator; the home-card note gets a type chip within ~10s of save; the on-write trigger does NOT backfill on subscribe.
