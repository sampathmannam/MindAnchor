# MindAnchor v0.26.1 — BPD §3.4 chain capture + IFS picker + lock-screen "ground me" + data export + AppWatchService

**Release date**: 2026-08-14
**Build**: `versionName=0.26.1, versionCode=42`
**Tag**: `v0.26.1` → HEAD
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.26.1

**Artifacts**:
- debug APK: built locally, SHA-256 `9ECC456B4794C7400C3844163F9216A3A2006C975D77074C09095816F2FC55BB` (52,730,272 bytes)
- release APK (unsigned): pending

**Status**: shipped

**Test result**: 1386 tests, 0 fail, 0 error. Detekt clean.

v0.26.1 is the v0.26.0 §3.4 "When things get hard" surface package
plus the §3.3 SMS tone-check side-channel. The work is the
launcher's first step beyond the calm-by-default *coordination*
frame and into a *predictive* frame for the moments when the
launcher is the only thing on the phone that knows what the
user is going through.

The work is deliberately split:

- **§3.4 surfaces (user-initiated)**: the "What just happened?"
  chain capture, the "Which part is loud?" IFS picker, the
  lock-screen "Ground me" tile + activity, and the
  "Data export for my therapist" share. Every one of these is
  reached on the user's terms, never on the launcher's
  schedule. None of them is wired into a home-surface entry
  point in this commit; the home entry points are owned by
  Agent 1 in a sibling worktree and land in a follow-up.
- **§3.3 side-channel (system-initiated)**: the SMS tone-check
  notification. The receiver fires on `SMS_RECEIVED`, the
  service posts a high-importance "Tone check before sending"
  prompt, and the prompt deep-links to the existing
  `BeforeYouSendInterstitial` with the SMS context as intent
  extras. This is the only place the v0.26.1 work is *not*
  user-initiated, and the audit log
  (`org.mindanchor.watch.SmsToneCheckPrefs`) makes the receipt
  observable: every intercepted SMS is a record on disk.

The "measure before you act" rule is the load-bearing
constraint. None of the §3.4 surfaces act on the user; they
record. The §3.3 side-channel posts one notification per
intercepted SMS and stops. A future commit that turns the
chain capture into a model input, or the SMS log into an
autoreply gate, has to ship a held-out test set that beats
the baseline; the same rule that gates v0.25.0's auto-classify
gates any v0.27+ model. The note in `RELEASE_NOTES_v0.25.14.md`
on the "predictions only on measured evidence" rule applies
unchanged.

---

## What shipped

### §3.4 — "What just happened?" 5-field chain capture
- New: `app/src/main/java/org/mindanchor/chain/ChainCaptureActivity.kt`
  + `ChainCaptureScreen.kt` + `ChainCapturePrefs.kt` (DataStore
  `chain_store.json`).
- Five fields, each an `OutlinedTextField` with
  `rememberSaveable`: event / interpretation / part / want /
  part-to-bring. The schema follows the IFS-flavored
  "five-step unpacking" line that Schwartz 1995 sketches in
  *Internal Family Systems Therapy*, and the BPD-specific
  framing in Linehan 1993 — the *interpretation* field is
  the BPD-active step (what story are you telling yourself
  about it?), the rest is the IFS unpacking.
- A blank-all-five is a no-op. The append is a single
  tab-separated line per entry, with `atMillis` first so a
  chronological sort is a string sort. The screen is
  `singleTask`, `excludeFromRecents="true"`, with the same
  `taskAffinity` discipline as the rest of the secondary
  surfaces.
- Pinned by `ChainCapturePrefs` shape + the manifest
  declaration. The home-surface entry point is Agent 1's
  call, not this commit's.

### §3.4 — "Which part is loud?" IFS picker
- New: `app/src/main/java/org/mindanchor/ifs/IfsPickerActivity.kt`
  + `IfsPickerScreen.kt` + `IfsPickerPrefs.kt` (DataStore
  `ifs_store.json`).
- A `FlowRow` chip grid of the seven default parts
  (Schwartz 1995 *Internal Family Systems Therapy* —
  "The angry part", "The scared part", "The part that wants
  to disappear", "The critic part", "The protector part",
  "The critic's critic", "The one who notices").
- The latest pick is highlighted on the next visit. The
  picker never auto-picks — the user *names* the part
  deliberately. The names are user-language; the storage
  layer is verbatim. A future i18n pass translates the
  chip labels, the rest of the surface is unchanged.

### §3.4 — "Data export for my therapist"
- New: `app/src/main/java/org/mindanchor/export/ExportActivity.kt`.
- One button, one JSON file. The export shape is a flat
  `ExportPayload` with named keys for *every* category the
  launcher holds: notes (full), OneThing, OpenLoop,
  BedtimeList, wellness (N-of-1 framed, with median + MAD
  per signal), check-ins, BPD profile (the five opt-in
  flags), chain captures, IFS picks.
- **Letter content is never exported.** A literal `note`
  field at the top of the file declares the exclusion so a
  future reader sees the promise at first glance, not buried
  in a docs/research/ file. Pinned by
  `ExportSanityFindingTest` ("`ExportPayload` excludes
  letter content by shape") — a regression that adds a
  `letters` or `letterBody` field on the data class flips
  the test red.
- File written to the app's external-files `export/`
  subdirectory; FileProvider path added to
  `res/xml/file_paths.xml`. Share via
  `Intent.ACTION_SEND` with the system share sheet. The
  launcher does not pick the recipient.

### §3.4 — Lock-screen "Ground me" tile
- New: `app/src/main/java/org/mindanchor/lock/GroundMeTile.kt`
  (a `TileService`) + `GroundMeActivity.kt` (a thin host for
  the existing `org.mindanchor.launcher.GroundMeScreen` from
  v0.25.11/v0.25.13).
- The tile is a single-affordance surface: one tap, one
  activity, one screen. The user can reach grounding
  exercises from the lock screen or the shade without
  unlocking the phone. The tile is not gated by time-of-day
  — a person who wants to ground at 3pm is just as welcome
  as one who wants to ground at 3am.
- Manifest: tile service is `BIND_QUICK_SETTINGS_TILE`-guarded,
  `exported="true"`, with the system `QS_TILE` intent filter.
  Pinned by `GroundMeTileFindingTest` (5 tests).

### §3.3 — SMS tone-check side-channel
- New: `app/src/main/java/org/mindanchor/watch/AppWatchService.kt`
  (a `Service` with `foregroundServiceType="dataSync"`) +
  `SmsInterceptor.kt` (a `BroadcastReceiver` for
  `android.provider.Telephony.SMS_RECEIVED`) +
  `SmsToneCheckPrefs.kt` (DataStore `sms_tone_check`).
- On `SMS_RECEIVED`: decode the `pdus` extra, take the
  sender + a 280-char body excerpt, append to the
  `sms_tone_check` store, then start `AppWatchService` as a
  foreground service so the notification post is not killed
  under Android 12+ background-start restrictions. The
  notification is a single high-importance prompt:
  *"Tone check before sending. A message just arrived from
  {sender}. Open to read it before you reply."* Tapping it
  deep-links to a new
  `org.mindanchor.friction.BeforeYouSendHostActivity` that
  hosts the existing `BeforeYouSendInterstitial` with the
  SMS context as intent extras.
- Manifest: `RECEIVE_SMS` + `FOREGROUND_SERVICE_DATA_SYNC`
  permissions, the service's `foregroundServiceType="dataSync"`,
  and the receiver's `exported="true"` (system broadcasts
  require it). Pinned by
  `AppWatchServiceManifestFindingTest` (7 tests).
- The body excerpt is capped at 280 chars (the
  `MAX_BODY_CHARS` constant in `SmsToneCheckLedger`). A full
  SMS body is never persisted; the excerpt is what fits the
  prompt, and storing more is a privacy over-reach.
- The receiver does not abort the broadcast and is not a
  default SMS app. Becoming a default SMS app is a much
  larger commitment (full lifecycle replacement, the
  platform permission grant flow) and is explicitly out of
  scope for v0.26.1.

---

## Why the data lives in DataStores, not Room

The §3.4 surfaces use new DataStores (`chain_store`,
`ifs_store`) following the same pattern as
`bpd_profile` (v0.26.0). The whole launcher is a
text-encoded-behind-a-DataStore-key architecture: a single
corrupt byte costs one entry, not the surface. The
chain-capture ledger and the IFS-pick ledger are append-only,
both < a few KB even after years, and the entire string
fits in one DataStore edit. The export reads them as Flow
`.first()` snapshots.

The SMS log follows the same shape: one DataStore,
one key, an append-only text ledger, no Room migration. The
tone-check is opt-in (a permission gate in
`SmsInterceptor.hasPostNotificationsPermission` mirrors
`EmaScheduler.postPrompt`'s gate). A user who never grants
`POST_NOTIFICATIONS` never sees a tone-check, but the audit
log is still appended so a therapist session can read the
SMS log without ever having shown the prompt.

---

## Anti-patterns that did not happen

The v0.25.13 release notes pinned the
"`Box(modifier = Modifier.fillMaxSize())` inside a Row or
Column whose parent has `fillMaxSize`" anti-pattern. The
v0.26.1 surfaces use `Column` with `verticalArrangement =
Arrangement.spacedBy(...)` everywhere a list of fields is
shown. The `Modifier.fillMaxSize()` in
`ChainCaptureScreen.Header` and `IfsPickerScreen.Header`
lives on the `Column`, not on a child `Box`. The
`IfsPickerScreen.PartGrid` uses `FlowRow` (which
auto-sizes per child) rather than a `Row` with a
`fillMaxSize` child, so a 7-part grid on a narrow screen
wraps rather than clipping.

The export activity's status row uses `Column` with
`Arrangement.spacedBy(16.dp)`, no inner `Box` with
`fillMaxSize`. The save button is a `Surface` with
`fillMaxWidth().heightIn(min = 48.dp)` — the same shape
as the v0.25.12 `BeforeYouSendInterstitial` fix.

---

## Files added

- `app/src/main/java/org/mindanchor/chain/ChainCaptureActivity.kt`
- `app/src/main/java/org/mindanchor/chain/ChainCaptureScreen.kt`
- `app/src/main/java/org/mindanchor/chain/ChainCapturePrefs.kt`
- `app/src/main/java/org/mindanchor/ifs/IfsPickerActivity.kt`
- `app/src/main/java/org/mindanchor/ifs/IfsPickerScreen.kt`
- `app/src/main/java/org/mindanchor/ifs/IfsPickerPrefs.kt`
- `app/src/main/java/org/mindanchor/export/ExportActivity.kt`
- `app/src/main/java/org/mindanchor/lock/GroundMeTile.kt`
- `app/src/main/java/org/mindanchor/lock/GroundMeActivity.kt`
- `app/src/main/java/org/mindanchor/watch/AppWatchService.kt`
- `app/src/main/java/org/mindanchor/watch/SmsInterceptor.kt`
- `app/src/main/java/org/mindanchor/watch/SmsToneCheckPrefs.kt`
- `app/src/main/java/org/mindanchor/friction/BeforeYouSendHostActivity.kt`
- `app/src/test/java/org/mindanchor/export/ExportSanityFindingTest.kt`
- `app/src/test/java/org/mindanchor/lock/GroundMeTileFindingTest.kt`
- `app/src/test/java/org/mindanchor/watch/AppWatchServiceManifestFindingTest.kt`

## Files modified

- `app/src/main/AndroidManifest.xml` — new activities,
  service, receiver, tile; two new permissions.
- `app/src/main/res/values/strings.xml` — 22 new strings.
- `app/src/main/res/xml/file_paths.xml` — added
  `<external-files-path>` for the export share.
- `app/build.gradle.kts` — `versionCode` 38 → 42,
  `versionName` `0.25.14` → `0.26.1`.

---

## How to verify

```bash
# Unit tests + detekt
./gradlew :app:testDebugUnitTest :app:detekt
#  -> 1386 tests, 0 fail, 0 error. Detekt clean.

# Build the debug APK
./gradlew :app:assembleDebug

# End-to-end on emulator-5554 (API 34, MindAnchorTest)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.mindanchor/.chain.ChainCaptureActivity
adb shell am start -n org.mindanchor/.ifs.IfsPickerActivity
adb shell am start -n org.mindanchor/.export.ExportActivity
# Chain capture, IFS picker, and export should each
# render three or more rows. The IFS picker's 7 chips
# must all be visible (no silent collapse).
#
# The lock-screen tile and the SMS tone-check require
# emulator-side setup (system shade for the tile, an
# incoming SMS for the receiver). Both surface a
# single notification; the prompt is the surface.
```
