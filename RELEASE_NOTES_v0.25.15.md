# v0.25.15 — v0.25.10+ backlog sweep: custom Savers + 4 simple remember→rememberSaveable + 16 KB page-size

**Tag**: `v0.25.15` on `feature/v0.25.15-17-state-cleanup`
**Version code**: 39 (was 38)
**Version name**: 0.25.15 (was 0.25.14)
**Test count**: 1369 debug + 1369 release = **2738 / 0 failed** (same total as v0.25.14 — the v0.25.15 work *renames* 4 BUG-shape pins to fix-shape and adds no new test methods)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.15

## What this release does

Five SOTA v2 bug-hunt findings flipped from BUG-shape pins to
fix-shape pins, plus a one-line native build fix for the
Android 15+ 16 KB page-size requirement.

**Part 1 — BUG-012 deferred half: custom `Saver`s for the 3
`LauncherRoot` state fields that don't fit the default
`autoSaver`** (this release, v0.25.15):

- `actionsFor: DisplayApp?` → `rememberSaveable` with the new
  `DisplayAppNullableSaver` (a `mapSaver` keyed on
  `component` / `label` / `isFavorite` / `isHidden`)
- `gateFor: DisplayApp?` → same `DisplayAppNullableSaver`
- `letterSelectedDate: LocalDate?` → `rememberSaveable` with
  the new `LocalDateNullableSaver` (a `Saver<LocalDate?, String>`
  that round-trips through `DateTimeFormatter.ISO_LOCAL_DATE`)

All 6 `LauncherRoot` state fields are now `rememberSaveable`.
The custom-Saver plumbing lands in two file-level `private val`s
in `HomeScreen.kt` so the same Saver is reused by both
`actionsFor` and `gateFor`.

**Part 2 — `remember` → `rememberSaveable` for the 4
auto-Saveable `remember` calls** (this release, v0.25.15):

- BUG-008 `BedtimeListCard` drafts
  (`mutableStateListOf<String>` is auto-Saveable)
- BUG-009 `AppActionsDialog` rename flow
  (`Boolean` and `String` are auto-Saveable)
- BUG-010 `EmaScreen` valence + saved
  (`Int?` and `Boolean` are auto-Saveable)
- BUG-011 `PulseScreen` answers + savedScore
  (`List<Int>` and `Int?` are auto-Saveable)

The 4 fixes are each a one-keyword swap. The interesting bit is
not the change but the FindingTest: each gets a new fix-shape
pin scoped to its function (`indexOf(pattern, fnIdx)`), so a
future regression that re-introduces `remember` at the same
call site fails the same FindingTest with a different message.

**Part 3 — 16 KB page-size support for Android 15+**
(this release, v0.25.15):

- `add_link_options("-Wl,-z,max-page-size=16384")` at the top
  of `app/src/main/cpp/CMakeLists.txt`, applied to
  `libmindanchor_llama.so` (the only shared library this
  CMakeLists produces).
- Apps targeting Android 15+ (API 35) with 16 KB page-size
  kernels must be built with this linker flag, otherwise the
  loader refuses the library and the app crashes on launch
  with a `dlopen failed` error. The fix is a single line at
  the top of the CMakeLists.

## Why

A config change (font scale, locale, density, layoutDirection)
without `configChanges` declared on the activity recycles the
Composable tree. A `remember`-held state is lost. A
`rememberSaveable`-held state survives — the value is written
to a Bundle, the tree is rebuilt, the value is restored.

The 3 LauncherRoot state fields that held `DisplayApp?` and
`LocalDate?` could not be migrated in v0.25.14 because the
default `autoSaver` does not know how to Bundle a data class
with 4 fields or a `LocalDate` (which is not Parcelable). The
custom `Saver` factory functions in `HomeScreen.kt` are the
documented Compose pattern for "I have a small data class,
give me a Saver": the `mapSaver` for `DisplayApp?` round-trips
through a `Map<String, Any?>` of the 4 fields; the
`Saver<LocalDate?, String>` for `LocalDate?` round-trips
through the ISO-8601 local date string.

The 16 KB page-size requirement was announced by Google in
2024-10 with a 2025-11-01 deadline. Without the linker flag,
the app would launch fine on every current device but crash
on every Android 15+ device with a 16 KB page-size kernel
(the default for new arm64 and x86_64 builds going forward).

## Test flips

- `BUG-008 BedtimeListCard CAPTURE-mode drafts use
  rememberSaveable (v0_25_15 fix)` — flipped from BUG-shape to
  fix-shape. The pin now uses `indexOf(pattern, fnIdx)` to
  scope the `mutableStateListOf<String>().apply { add("") }`
  shape to the BedtimeListCard CAPTURE branch.
- `BUG-009 AppActionsDialog rename flow uses rememberSaveable
  (v0_25_15 fix)` — flipped from BUG-shape to fix-shape.
  Scoped to `fun AppActionsDialog(`.
- `BUG-010 EmaScreen valence and saved use rememberSaveable
  (v0_25_15 fix)` — flipped from BUG-shape to fix-shape.
  Scoped to `fun EmaScreen(`.
- `BUG-011 PulseScreen answers and savedScore use
  rememberSaveable (v0_25_15 fix)` — flipped from BUG-shape
  to fix-shape. Scoped to `fun PulseScreen(`.
- `BUG-012 LauncherRoot complex state uses rememberSaveable
  (v0_25_15 fix)` — flipped from the v0.25.14
  deferred-work pin to a fix-shape pin. The pin asserts the
  `stateSaver = …NullableSaver` parameter is present on all
  3 sites, which is the load-bearing shape: a regression
  that reverts to `remember` fails the same FindingTest
  with a different message; a regression that reverts to
  plain `rememberSaveable` (without the custom Saver) also
  fails it.

The BUG-012 enum pin (v0.25.14 partial fix) and the
BUG-005 / BUG-006 / BUG-007 pins are unchanged.

## Files changed

- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  - 2 new file-level private vals: `DisplayAppNullableSaver`
    and `LocalDateNullableSaver`
  - 3 `remember` → `rememberSaveable(stateSaver = ...)`:
    `actionsFor`, `gateFor`, `letterSelectedDate`
  - 1 `remember` → `rememberSaveable`: BedtimeListCard `drafts`
  - KDoc block on each Saver explaining the contract
- `app/src/main/java/org/mindanchor/launcher/AppActionsDialog.kt`
  - 2 `remember` → `rememberSaveable`: `renaming`, `newLabel`
  - Drop unused `remember` import
- `app/src/main/java/org/mindanchor/model/EmaScreen.kt`
  - 2 `remember` → `rememberSaveable`: `valence`, `saved`
  - Drop unused `remember` import
- `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt`
  - 2 `remember` → `rememberSaveable`: `answers`, `savedScore`
  - Drop unused `remember` import
- `app/src/main/cpp/CMakeLists.txt`
  - `add_link_options("-Wl,-z,max-page-size=16384")` at the
    top of the file (one line, applies to
    `libmindanchor_llama.so` and through `target_link_libraries`
    to the static llama.cpp objects)
- `app/src/test/java/.../ComposeStateHuntFindingTest.kt`
  - BUG-008 / 009 / 010 / 011 / 012-deferred all flipped from
    BUG-shape to fix-shape, each scoped to its function
- `app/build.gradle.kts`
  - `versionCode 38 → 39`
  - `versionName "0.25.14" → "0.25.15"`

## Verification

- `:app:compileDebugKotlin` — clean
- `:app:compileDebugUnitTestKotlin` — clean (no new tests; 4
  tests renamed, 1 test re-scoped)
- `:app:testDebugUnitTest` — 1369 tests, 0 failed, 0 errored
- `:app:testReleaseUnitTest` — 1369 tests, 0 failed, 0 errored
- `:app:detekt` — clean
- `:app:assembleDebug` — `app-debug.apk` (52,473,820 bytes)
- Emulator `emulator-5554` (Android 14, x86_64, 1080x2400):
  - Installed (`versionCode=39`, `versionName=0.25.15`)
  - `adb logcat -d | grep FATAL` — no FATAL for
    `org.mindanchor`
  - `adb logcat -d | grep -i '16.?KB\|max-page\|libmindanchor'`
    — no 16 KB / max-page / libmindanchor warning on first
    launch
  - Home surface rendered: clock "8:10", "Winding down."
    greeting, Notes card with the QuickNotes input, "test
    · 2:12 PM" recent note, OneThing "finish the project /
    Done with it"
  - QuickNotesCard input field focus verified — typed and
    observed the cursor in the field
  - Letters surface opened — "A letter from your week",
    "No letters yet. Phi-4 isn't installed — open Settings →
    Model to install it." The letters surface is the
    recipient of the new `LocalDateNullableSaver`; opening
    the surface is the static load-bearing check that the
    Saver wiring is wired (the runtime verification of
    `letterSelectedDate` surviving rotation is deferred —
    the only letter reader in this build is empty without
    Phi-4 installed, so a real `letterSelectedDate` value
    cannot be produced on this emulator)

## Release artifacts

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `E4DCFFBE05C27282E7D90748EF2710C75555D9C77ED0D7286CE9B883CF1C86CE`
  - Size: 52,473,820 bytes (50.0 MiB)
- **Release APK**: not built in this release (key not in env).
  The debug APK is sufficient for emulator verification.

## Screenshot

`C:\Users\Sampath\AppData\Local\Temp\v0_25_15_home.png` — home
surface with the QuickNotesCard input, versionCode 39,
versionName 0.25.15.

## What this is NOT

- **Not the BUG-013 haptics gate.** That lands in v0.25.16
  with the new `HapticFeedbackGate` CompositionLocal.
- **Not the BUG-017 `modelFits` wiring.** That lands in
  v0.25.16 with the `LauncherViewModel.modelFits: StateFlow<Boolean>`.
- **Not the BUG-018 `SaveableStateHolder` for Settings tabs.**
  That lands in v0.25.16.
- **Not the broad `collectAsStateWithLifecycle` migration.**
  4 sites in `HomeScreen.kt` (and all sites in 10 other
  files) still use `collectAsState`. v0.25.17 closes the
  remaining gap.
- **Not a process-death fix.** `am force-stop` still kills the
  activity without `onSaveInstanceState`. The
  `rememberSaveable` primitive is what makes the framework
  able to do its job; whether the user can manually reproduce
  it on the emulator is a separate, lower-priority question.

## Next steps (v0.25.16+)

1. BUG-013: `HapticFeedbackGate` CompositionLocal
   (HomeScreen, NoteScreen, LetterScreen, FrictionGate).
2. BUG-017: `LauncherViewModel.modelFits: StateFlow<Boolean>`
   wiring — replace the `modelFits = remember { mutableStateOf(false) }`
   stub with the flow.
3. BUG-018: `SaveableStateHolder` on the Settings tabs
   (Pauses / Reading / Backup / Watch connect).
4. v0.25.17: broad `collectAsStateWithLifecycle` migration
   for the remaining 4 sites in `HomeScreen.kt` and the 11
   other files in the BUG-004 set.

The v0.25.10+ backlog is being closed incrementally. v0.25.15
closes 5 of 18 findings (BUG-008, BUG-009, BUG-010, BUG-011,
and the BUG-012 deferred half). v0.25.16 closes BUG-013,
BUG-017, BUG-018. v0.25.17 closes BUG-004 fully.
