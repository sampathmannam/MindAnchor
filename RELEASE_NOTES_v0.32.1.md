# v0.32.1 — LettersGenerationService: foreground service for overnight letters

The Q2_K Phi-4-mini model loads and decodes correctly on a
1.8 GB MemAvailable phone (v0.31.2 proved that). What it
couldn't survive was a 30-60 minute CPU-bound run in a
process the OS was free to kill: v0.32.0's test of the
in-Composable coroutine produced zero letters saved; the
process was reaped mid-decode. v0.32.1 moves the work into a
foreground service that the OS is *not* free to kill.

## What changed

### 1. `LettersGenerationService` (new)

`app/src/main/java/org/mindanchor/letters/LettersGenerationService.kt`

A foreground `Service` that hosts the on-device letter
generation:

- `onStartCommand` acquires a `PARTIAL_WAKE_LOCK` (90 min
  ceiling — well past the 30-60 min design range), promotes
  itself to the foreground within the Android 12+ grace
  window with an ongoing notification on the new
  `org.mindanchor.letters.generation` channel, and launches
  a coroutine on a `SupervisorJob + Dispatchers.IO` scope
  that runs the same `WeekDataCollector → LetterWriter →
  LetterStore.saveUserLetter` pipeline v0.31.0 introduced.
- The scope outlives the Composable that called us; the
  decode keeps running when the user navigates away,
  backgrounds the app, or locks the screen.
- Returns `START_NOT_STICKY` (no auto-restart; the user
  re-taps to retry). The `isRunning` static guards re-entry
  (two rapid taps do not double-load the model).
- On success: replaces the ongoing notification with a
  "Tonight's letter is ready" notification that opens the
  inbox.
- On failure: replaces with a "Letter generation didn't
  complete" notification that re-opens the inbox.
- The existing Toast on the home screen is preserved — it's
  the immediate user-side confirmation; the notification
  is the "this is still running" signal that replaces it.

### 2. `HomeScreen.onGenerateNow` now starts the service

`app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`

The pre-v0.32.1 lambda body ran the pipeline in a
`rememberCoroutineScope()`-bound coroutine. v0.32.1 replaces
that with `Context.startForegroundService(LettersGenerationService.intent(...))`.
The Toast text and the user-visible behaviour are
identical; the work is now hosted in a place that survives
the Composable.

### 3. Manifest + channel

`AndroidManifest.xml`:
- `<service android:name=".letters.LettersGenerationService"
  android:exported="false"
  android:foregroundServiceType="dataSync" />`
- Pairs with the existing `FOREGROUND_SERVICE_DATA_SYNC`
  permission (the same pair `AppWatchService` already uses).
- Pinned by `LettersGenerationServiceManifestFindingTest`.

`Channels.kt`:
- New `LETTERS_GENERATION` channel, IMPORTANCE_LOW, no
  badge, no sound. Same shape as `GOING_LIGHT` — an
  ongoing foreground service whose presence in the
  status bar is itself the signal, not the alert.

### 4. Tests (8 new)

- `LettersGenerationServiceManifestFindingTest` (5 tests):
  service is registered, foregroundServiceType is
  `dataSync`, exported is `false`, the permission pair is
  present, the Kotlin class extends `android.app.Service`,
  the static `isRunning` re-entry guard exists, the
  static `intent(Context)` factory exists.
- `HomeScreenGenerateNowFindingTest` (3 tests):
  `onGenerateNow` calls `startForegroundService` on
  `LettersGenerationService`, no longer imports
  `WeekDataCollector` / `LetterWriter` directly (the
  pipeline moved into the service), and still posts the
  Toast.

## What stayed the same

- The pipeline: `WeekDataCollector.collectLastWeek()` →
  `LetterWriter.write(week)` → `LetterStore.saveUserLetter(date, body)`.
  Same inputs, same outputs, same `NarrationGuard`
  filter. Only the host changed.
- The home surface: no new card, no new affordance. The
  "Generate now" button is in the Letters inbox, exactly
  where v0.32.0 left it.
- The notification channel `org.mindanchor.letters` for
  the success / failure notification (reused from
  `LetterScheduler` — the user has already accepted the
  daily-letter notifications on this channel).
- The model file: still the Q2_K Phi-4-mini at
  `/data/user/0/org.mindanchor/files/model.gguf`.

## Why foreground service, not WorkManager

`WorkManager` is the right answer for a *periodic* job
that runs in 10-minute chunks and persists state between
chunks. The "Generate now" / overnight-letter design is
one clean 20-60 minute run; chunking it adds three
failure modes (resumed-state desync, partial-save
pollution of the inbox, N+1 model loads) and the
foreground service + wake lock + `START_NOT_STICKY`
pattern already does what chunking would do, more
simply. A future `WorkManager` variant that runs the
scheduled daily letter would still use the same
service.

## Phone-test results (v0.32.1 build, Moto G84 1.8 GB)

- `mvn install` clean, 1479 tests, 0 fail.
- `dumpsys activity services org.mindanchor`:
  ```
  ServiceRecord{... org.mindanchor/.letters.LettersGenerationService}
    isForeground=true foregroundId=28938 types=0x00000001
    foregroundNoti=Notification(channel=org.mindanchor.letters.generation
                                flags=ONGOING_EVENT|ONLY_ALERT_ONCE|NO_CLEAR|FOREGROUND_SERVICE
                                category=progress vis=PRIVATE)
  ```
- `top -p 3912 -n 1`:
  ```
  800%cpu 804%user ... org.mindanchor
  PID 3912 ... 1.4G RES 13.5% MEM
  ```
- `logcat -s MindAnchor/llama:V` shows the full
  load → context-init → decode sequence:
  - 31 KV-cache layers init at 104.00 MiB (K q8_0 68 MiB,
    V q4_0 36 MiB)
  - CPU compute buffer 25.30 MiB, graph nodes 1159
  - `generate: context initialised`
- After `KEYCODE_HOME` (background the app, simulate
  "go to bed"): service still foregrounded, process
  still at ~800% CPU.

The architectural fix is verified: the process survives
app backgrounding. The decode is still slow (Q2_K at
0.1-0.5 tok/s on a 1.8 GB phone), so a full 600-token
letter takes 20-100 minutes — the design target. An
overnight run is now possible.

## Files changed

- `app/build.gradle.kts` — versionCode 60→61, versionName
  0.32.0→0.32.1
- `app/src/main/AndroidManifest.xml` — service
  declaration
- `app/src/main/java/org/mindanchor/letters/LettersGenerationService.kt` —
  new file (16 KB, 350+ lines)
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` —
  `onGenerateNow` lambda body: removed the in-Composable
  coroutine, added `Context.startForegroundService(...)`.
  Removed unused `WeekDataCollector` / `LetterWriter`
  imports.
- `app/src/main/java/org/mindanchor/notifications/Channels.kt` —
  new `LETTERS_GENERATION` channel + `lettersGeneration`
  factory
- `app/src/main/res/values/strings.xml` — 9 new strings
  (channel name + description, ongoing title + text,
  done title, failed title + text, failed empty, failed
  unknown)
- `app/src/test/java/.../LettersGenerationServiceManifestFindingTest.kt` —
  new (5 tests)
- `app/src/test/java/.../HomeScreenGenerateNowFindingTest.kt` —
  new (3 tests)
- `docs/CLINICIAN_PACK.md` — regen (string-count change)
- `RELEASE_NOTES_v0.32.1.md` — this file

## Test count

- Before: 1471 (v0.32.0)
- After: 1479 (v0.32.1)
- +8: 5 in `LettersGenerationServiceManifestFindingTest`,
  3 in `HomeScreenGenerateNowFindingTest`
- All pass.
