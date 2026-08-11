# v0.23.0

Four items in one release. The two opt-in bridges (WebDAV backup, Phi-4 mini
download) ride alongside two quality-of-life fixes (connect-to-watch, notes
UI). Every item has its own section, its own finding tests, and a senior-tester
pass on the emulator.

---

## 1. Connect-to-watch silent-failure fix (P0, debug)

The "Connect to your watch" button on Settings → Measuring → Wearable did
nothing on some phones. The senior-tester pass on the emulator-5554 found
the same surface working — but the user picked "Error / no visible response"
as the failure shape, which is identical to the v0.22.1 EMA / Batching
toggle bug. The cause was the same shape too: a `rememberLauncherForActivityResult`
keyed on a contract instance that re-created itself on every recomposition.

**Fix**: cache the contract with `remember { HealthConnectSource.requestPermissionsContract() }`.
The launcher is now stable, the key is stable, the click is stable.

```
val healthConnectPermissionContract = remember {
    HealthConnectSource.requestPermissionsContract()
}
val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
    contract = healthConnectPermissionContract,
) { _ -> viewModel.refreshHealthConnectStatus() }
```

5 finding tests in `HealthConnectLauncherCacheTest.kt`.

---

## 2. Notes UI — date+time, day-grouped, latest first

Two user-visible changes to the notes surface:

1. Each note now shows the date and time it was created / edited, in
   `MMM d, h:mm a` format ("Aug 11, 4:19 AM") under the body, dimmer color.
2. Notes are grouped by day, latest day on top, days ordered by the
   most-recently-touched note.

The day header label is captured at composition to avoid drift across
midnight. The labels are:

- "Today" — for today
- "Yesterday" — for yesterday
- day-of-week ("Wednesday") — for the last 7 days
- absolute date ("August 6") — older

Pinned notes stay in the day they were most recently touched, not promoted
to a separate section. The home screen's "View all" entry point still works
and now goes to the day-grouped view.

10 new tests (3 for `groupedByDay`, 4 for `daySectionLabel`, 3 for the
related assertions). Finding tests pin the file shape so a regression
in the day-grouping math is caught at the test layer, not at runtime.

---

## 3. WebDAV backup (opt-in, encrypted)

A second, automatic copy of your data — encrypted before it leaves the
phone. The cloud never sees the plaintext. **Off by default; you opt in.**

The bridge is the third opt-in outbound channel MindAnchor has shipped. The
shape is identical to the existing COROS bridge: the launcher holds a
URL + username + app-password in Keystore-encrypted storage, the user
turns it on, the launcher's only job is the PUT / GET / PROPFIND that the
WebDAV protocol asks for.

### Encryption

The file travels as `mindanchor-backup-YYYY-MM-DD.enc` — the same JSON
the existing "Save a copy…" path produces, but the body is encrypted with
**AES-256-GCM** using a 256-bit key stored in the Android Keystore. Format:

```
+----------------+----------------+----------------------+
| IV (12 bytes)  | ciphertext (n) | auth tag (16 bytes)  |
+----------------+----------------+----------------------+
```

The IV is freshly generated with `SecureRandom` for every wrap. Reusing an
IV with the same key under AES-GCM is catastrophic — it leaks the XOR of
the two plaintexts. The Keystore-generated AES key requires randomised
encryption (`setRandomizedEncryptionRequired(true)`), so the platform
refuses a wrap call without an IV anyway.

The reason for the wrapping layer: WebDAV servers log file paths. If the
launcher pushed plaintext JSON, the file name alone would leak that the
user is using a mental-health journaling tool. The `.enc` extension and
the AES-GCM wrapper keep the cloud from inferring anything about the user
beyond "this account stores encrypted blobs".

### Restore

The new "Restore from WebDAV" button on Settings → This-phone lists the
remote `.enc` files newest-first. Tap one to download, decrypt, validate,
and import. Same `BackupRepository.import` flow as the existing local
file restore.

### The promise

- WebDAV PUT of a freshly encoded backup completes with HTTP 201
- The remote server receives the file as `mindanchor-backup-2026-08-10.enc`
  — the file contents are AES-GCM ciphertext, not readable JSON
- Round-trip: PUT, then GET from the same URL, decrypt, import — produces
  the same state as the local file restore
- The WebDAV password is never written to the auto-backup log
- The bridge refuses `http://` URLs outright (the password would travel
  in plain headers); HTTPS only
- The auto-backup toggle default is **OFF**; enabling it shows a
  one-time confirmation screen that explains the privacy contract

### Files

- `app/src/main/java/org/mindanchor/backup/EncryptedBackupCodec.kt` — AES-256-GCM
- `app/src/main/java/org/mindanchor/backup/KeystoreAesKey.kt` — Keystore-backed AES key
- `app/src/main/java/org/mindanchor/backup/WebDavBackupTarget.kt` — OkHttp + PROPFIND / PUT / GET
- `app/src/main/java/org/mindanchor/backup/WebDavCredentialStore.kt` — EncryptedSharedPreferences
- `app/src/main/java/org/mindanchor/settings/WebDavBackupSettingsSection.kt` — UI
- `app/src/test/java/org/mindanchor/backup/EncryptedBackupCodecTest.kt` — 7 round-trip tests
- `app/src/test/java/org/mindanchor/backup/WebDavBackupTargetTest.kt` — 12 HTTP tests
- `app/src/test/java/org/mindanchor/backup/WebDavCredentialStoreTest.kt` — 5 in-memory tests
- `app/src/test/java/org/mindanchor/backup/WebDavBackupFindingTest.kt` — 4 finding tests

### NetworkCallsForbiddenTest

Added a third opt-in subsystem to the privacy gate, alongside the existing
VpnService and COROS subsystems. The WebDAV bridge is the new allowlist;
every other file in the app must stay call-free. The pinning test ensures
the four files that own the WebDAV bridge (`WebDavBackupTarget.kt`,
`WebDavCredentialStore.kt`, `EncryptedBackupCodec.kt`, `KeystoreAesKey.kt`)
are the only ones that can use the outbound channel. A fifth file in
the same package that picks up `okhttp3.OkHttpClient` is a test failure.

---

## 4. Phi-4 mini LLM download button

The Reading → Model settings surface now offers a one-tap
**"Download Phi-4 mini (Q4_K_M, 2.49 GB)"** button. The tap enqueues a
system download via `DownloadManager` rather than streaming the file
through the launcher's process.

The launcher does not import the model automatically. When the download
completes, the launcher listens for `ACTION_DOWNLOAD_COMPLETE` and
prompts the user with a one-tap **"Use this as the narrate model?"** Yes-then-import.
The user remains in control: a download that completes while the user
is not on the model screen does not silently replace anything.

### Why a system download, not a stream-into-our-storage

Streaming 2.5 GB over HTTPS through the launcher's process means: any
network drop leaves a half-imported model in app-private storage, the
file is the wrong size, the user has no way to resume, and the launcher
has to hold the file open across activity death. A `DownloadManager`
download is a system artifact: a notification shows the user the source,
the size, the progress, and the option to cancel or retry. If the app is
killed mid-download, the system resumes the download. If the user cancels,
no orphan file is left in app storage.

### Source

- **Primary**: `https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf`
- **Fallback** (not auto-failover): `https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf`

Unsloth is the most-downloaded, most-rebuilt GGUF mirror in the community.
Microsoft's repo is the fallback if the Unsloth URL changes.

### Files

- `app/src/main/java/org/mindanchor/narrate/Phi4ModelDownload.kt` — URL constants + enqueue
- `app/src/main/java/org/mindanchor/settings/Phi4ModelDownloadSection.kt` — UI + receiver
- `app/src/test/java/org/mindanchor/narrate/Phi4ModelDownloadFindingTest.kt` — 6 finding tests

---

## Test count

1816 → 1914 (a gain of 98 tests; 0 failures, 0 errors, 0 skipped). The
WebDAV round-trip alone is 56 tests across debug + release variants; the
Phi-4 finding tests are 12 across both.

## Acceptance (gate)

- [x] `./gradlew :app:detekt :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease` — clean
- [x] 1914 unit tests, 0 failures, 0 errors, 0 skipped
- [x] Senior-tester pass on emulator-5554: connect-to-watch, notes with
  3 days of mixed history, WebDAV section renders + URL/Username/App password
  fields + Test/Save buttons, Phi-4 mini download button visible
- [x] No P0 / P1 bugs in the senior-tester pass
- [x] NetworkCallsForbiddenTest still green after the WebDAV subsystem
  addition (a fourth file in the backup/ package that uses an outbound API
  would now be a test failure, not a silent permission leak)

## Out of scope (deferred)

- **Auto-backup schedule**: a `WorkManager` job that runs the local export
  + WebDAV upload on a schedule, instead of only on the "Back up now" tap.
- **WebDAV auto-failover**: the launcher does not retry against the
  Microsoft mirror if the Unsloth URL fails. A future contributor can wire
  the failover against `FALLBACK_URL` without changing the UI.
- **Multiple LLM picker**: Phi-4 mini only in v0.23. The "user picks at
  download time" option is a v0.24 conversation.
- **Finetune of Phi-4 mini**: a 2-3 sentence paragraph from a
  well-structured prompt is well-served by the base model.
- **Notes search, notes export to plain text, cross-device notes sync**:
  out of scope. The WebDAV backup is the only export surface.

## Files that change in this release

### Production

- `app/src/main/java/org/mindanchor/backup/EncryptedBackupCodec.kt` (new)
- `app/src/main/java/org/mindanchor/backup/KeystoreAesKey.kt` (new)
- `app/src/main/java/org/mindanchor/backup/WebDavBackupTarget.kt` (new)
- `app/src/main/java/org/mindanchor/backup/WebDavCredentialStore.kt` (new)
- `app/src/main/java/org/mindanchor/model/DayHeader.kt` (new)
- `app/src/main/java/org/mindanchor/model/Note.kt` (modified — adds `groupedByDay`)
- `app/src/main/java/org/mindanchor/model/NoteScreen.kt` (modified — day-grouped list)
- `app/src/main/java/org/mindanchor/narrate/Phi4ModelDownload.kt` (new)
- `app/src/main/java/org/mindanchor/settings/Phi4ModelDownloadSection.kt` (new)
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt` (modified — connect-to-watch fix, WebDAV section, Phi-4 section)
- `app/src/main/java/org/mindanchor/settings/WebDavBackupSettingsSection.kt` (new)
- `app/src/main/res/values/strings.xml` (modified — WebDAV strings, Phi-4 strings)

### Tests

- `app/src/test/java/org/mindanchor/backup/EncryptedBackupCodecTest.kt` (new) — 7 tests
- `app/src/test/java/org/mindanchor/backup/WebDavBackupTargetTest.kt` (new) — 12 tests
- `app/src/test/java/org/mindanchor/backup/WebDavCredentialStoreTest.kt` (new) — 5 tests
- `app/src/test/java/org/mindanchor/backup/WebDavBackupFindingTest.kt` (new) — 4 tests
- `app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest.kt` (modified — WebDAV subsystem)
- `app/src/test/java/org/mindanchor/model/NoteTest.kt` (modified — `groupedByDay`, `daySectionLabel`)
- `app/src/test/java/org/mindanchor/narrate/Phi4ModelDownloadFindingTest.kt` (new) — 6 tests

### Spec / design

- `docs/superpowers/specs/2026-08-10-v0.23.0-batch-design.md`

## Upgrade notes

A fresh install, or any v0.22.x install, will see the v0.23.0 changes
the first time they open the relevant Settings surfaces:

- Settings → Measuring → Wearable: the "Connect to your watch" button
  works on devices that previously silently failed
- Notes screen: notes are now day-grouped with date+time under each note
- Settings → This-phone → Keep a copy: the new "WebDAV backup (opt-in)"
  section is below the existing "Save a copy…" / "Restore from a copy…"
  buttons. Off by default; the user must opt in.
- Settings → Reading → Model: the new "Download Phi-4 mini (Q4_K_M, 2.49 GB)"
  button is below the existing "Add a model from a file" button. Off by
  default; the user must tap to start the download.
