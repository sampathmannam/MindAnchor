# MindAnchor v0.30.1 — Phi-4 model auto-integration

The phone E2E test of v0.30.0 surfaced a critical bug in the
Phi-4 model integration: even after a successful download, the
"Use AI" and "Generate now" buttons on Letters were always greyed
and the "Model" settings card always read "No model on file",
no matter how many times the user re-downloaded. v0.30.1 fixes
both halves of that failure.

## What was broken

### 1. `Phi4ModelDownload.isPhi4File` only matched the canonical filename
The Android `DownloadManager` appends `-N` to the destination
filename on collision with an existing file in the public
Downloads collection. The launcher was looking for
`Phi-4-mini-instruct-Q4_K_M.gguf` (the exact name suggested to
the system) and rejected `Phi-4-mini-instruct-Q4_K_M-4.gguf`
(the actual file the user ended up with after the system
auto-suffixed the fourth attempt). The receiver filtered
itself out, the "Use this as the narrate model?" prompt never
appeared, and the file sat on disk unimported.

### 2. `LauncherViewModel.modelFits` was hard-coded to the wrong filename
A second, independent bug: the launcher's `modelFits` state
flow — the value that gates Letters' "Use AI" / "Generate now"
buttons — was hard-coded to look for `phi-4-mini-q4.gguf`,
a filename v0.23.0 had renamed to `model.gguf` in
`ModelStore.MODEL_FILE_NAME`. The launcher was always
reading "model not on file" even after a successful
import, so the buttons were always disabled. SettingsViewModel
read the right file, so the settings card correctly said
"A model is on file", and the launcher said it wasn't —
and Letters believed the launcher.

## What v0.30.1 changes

### Fix #1 — suffix-collision accepted (Phi4ModelDownload.kt)
- `isPhi4File(uriString)` now matches by prefix + `.gguf`
  extension, not exact filename. Both the canonical
  `Phi-4-mini-instruct-Q4_K_M.gguf` and the system's
  suffixed `Phi-4-mini-instruct-Q4_K_M-4.gguf` are
  recognised as the same artefact.
- New `DOWNLOAD_BASENAME_PREFIX` constant pins the
  prefix; the suffix-collision logic is testable in
  isolation.
- New finding test `isPhi4File accepts the DownloadManager
  collision-suffix basename` pins the new behaviour and
  guards the negative cases (a different quantisation with
  the same family prefix is still rejected; a `.txt` file
  with the right prefix is still rejected).

### Fix #2 — single source of truth for `modelFits` (LauncherViewModel.kt)
- `modelFits` now wraps `ModelStore.fitFlow()` directly.
  The settings VM publishes into the same singleton flow
  on every import / clear, so the launcher and the
  settings card agree without either having to know the
  other exists.
- No more private `_modelFits` MutableStateFlow that could
  drift out of sync. The init block now just calls
  `ModelStore.refreshFit(application)` on cold start so
  the singleton's state is real, not the default `false`.

### Fix #3 — "Use existing download" offer (Phi4ModelDownload.kt + Phi4ModelDownloadSection.kt)
- A user who has any Phi-4 file already in the public
  Downloads dir (a previous in-app attempt whose
  completion broadcast was missed, a browser download,
  a sideloaded copy) no longer has to re-download the
  2.49 GB to get the launcher to recognise it. The
  Model settings card now scans the public Downloads
  dir on entry, finds the most recent file matching
  the prefix + `.gguf` + 100 MB floor, and offers to
  import it with one tap.
- The scan uses `Uri.fromFile()` for the import — the
  same path `ModelStore.importFrom` already handles
  via `contentResolver.openInputStream`. No new code
  path on the import side.
- New strings `model_existing_offer`, `model_existing_use`,
  `model_existing_dismiss`.

## Test evidence

Installed on the test phone (Moto G84, ZD2232FCR5) over
v0.30.0:

1. Opened Settings → Reading → Model. The new offer
   appeared: "A Phi-4 file is already in the phone's
   Downloads folder. Use it as the narrate model?" with
   "Use the file in Downloads" and "Not now" buttons.
2. Tapped "Use the file in Downloads". The model card
   flipped from "No model on file" to "A model is on file.
   This phone has room to run it, with room to spare."
3. Went back to Home, tapped "Letters" in the top-right
   rail. The empty state body changed from "No letters yet.
   Phi-4 isn't installed — open Settings → Model to install
   it." to "Letters write themselves overnight. The first
   one will land here at 7 am." — proving the launcher
   now agrees with the settings card. "Use AI" and
   "Generate now" went from greyed to active.

## Known limitations (NOT fixed in v0.30.1)

These are documented in `phone_test_v031_findings.md` so
they are not lost, but are out of scope for the
auto-integration fix:

1. **No narration generated for the nightly report.**
   The model file is on disk and `Narrators.forDevice()`
   returns `LlamaNarrator`, but `llama_model_load_from_file`
   in the native library returns null, the narration is
   null, the report saves without it, the screen does
   not show the "Tonight, in plain words" section. Most
   likely cause is the 2.32 GB model + 512 MB KV cache
   exceeding what `ModelSlot.fit()` budgeted. Needs
   diagnostic logging in the C++ layer to narrow down.

2. **Letters "Use AI" / "Generate now" are still
   unwired placeholders.** The buttons are correctly
   enabled now (Fix #2 above), but the `onClick` is
   `/* wired in Task 10 */` in `LetterScreen.kt:267`
   and `:339`. Tapping them does nothing. This is
   documented future work, not a v0.30.1 regression.

3. **"Write a letter now" saves an empty letter.**
   The empty state's button calls
   `onSaveUserLetter(today, "")` and the store silently
   rejects blank bodies (`LetterStore.kt:246`). Tapping
   the button is a no-op. The KDoc at
   `LetterScreen.kt:286-292` says the parent is supposed
   to open a separate composer surface, but no such
   surface exists. Worth fixing as a separate small WP
   (open an inline composer dialog with a text field
   and a Save / Cancel pair).

4. **5 Phi-4 files in /storage/emulated/0/Download/**
   from re-enqueued downloads across the v0.30.0 test
   session. 12.45 GB total. Worth a one-time cleanup.

## Test counts

- v0.30.0: 1469 tests
- v0.30.1: 1471 tests (+2 from the new FindingTest
  case for the suffix-collision acceptance + negative
  cases)

## APK

- versionCode 55 → 56
- versionName 0.30.0 → 0.30.1
- 5 files changed (3 main + 1 test + 1 strings)
- detekt: clean
- assembled at `app/build/outputs/apk/debug/app-debug.apk`
