# v0.25.16 — v0.25.10+ backlog sweep: HapticFeedbackGate + modelFits wiring + SaveableStateHolder

**Tag**: `v0.25.16` on `feature/v0.25.15-17-state-cleanup`
**Version code**: 40 (was 39)
**Version name**: 0.25.16 (was 0.25.15)
**Test count**: 1374 debug + 1374 release = **2748 / 0 failed** (was 2738 in v0.25.15; +5 tests from the 2 new BUG-013/017/018 finding-test files plus the in-class pin updates for the v0.25.16 fixes)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.16

## What this release does

Three SOTA v2 bug-hunt findings closed with new finding-test files
and a new `ui/HapticFeedbackGate.kt` helper.

**Part 1 — BUG-013: `HapticFeedbackGate` CompositionLocal**
(this release, v0.25.16):

A new file `app/src/main/java/org/mindanchor/ui/HapticFeedbackGate.kt`
introduces:

- `LocalHapticFeedbackGate` — a `compositionLocalOf<HapticFeedbackGate>`
  that descendants read to acquire the launcher haptics handle.
- `interface HapticFeedbackGate` — a one-method contract
  (`performHapticFeedback(type: HapticFeedbackType)`).
- `class DefaultHapticFeedbackGate(context, delegate)` — the
  implementation that consults
  `Settings.System.HAPTIC_FEEDBACK_ENABLED` (the system
  haptics toggle) and
  `Settings.Global.ANIMATOR_DURATION_SCALE` (the
  "remove animations" a11y preference) on every call and
  only forwards to the underlying `HapticFeedback` when
  both say "haptics allowed".
- `@Composable HapticFeedbackGateProvider(content)` — the
  Provider Composable that wraps the launcher root and
  seeds the CompositionLocal.

The four (six, counting the 3 HomeScreen sites separately)
haptics call sites — `HomeScreen.kt` BedtimeListCard save,
`HomeScreen.kt` OpenLoopCard save, `HomeScreen.kt`
QuickNotesCard save + clear, `NoteScreen.kt` row-delete
confirm, `LetterScreen.kt` letter delete-confirm, and
`FrictionGate.kt` breath-pause markers — were rewired
from `val haptics = LocalHapticFeedback.current` to
`val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current`.

`HomeActivity.kt` and `GateActivity.kt` (the two entry
points into the launcher / FrictionGate) wrap their
`setContent { ... }` in `HapticFeedbackGateProvider { ... }`
so the gate is the single source of truth.

**Part 2 — BUG-017: `LauncherViewModel.modelFits` wiring**
(this release, v0.25.16):

`LauncherViewModel` gains a new `StateFlow<Boolean>` field
named `modelFits`. The VM seeds the flow in its `init`
block: a coroutine on `Dispatchers.IO` checks whether
`phi-4-mini-q4.gguf` is present and non-empty in
`application.filesDir`, and emits the result.

The HomeScreen letter surface dispatcher stops holding
`val modelFits = remember { mutableStateOf(false) }` (the
always-false stub that disabled Generate-now forever) and
now collects the flow with
`val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()`.

**Part 3 — BUG-018: `SaveableStateHolder` for Settings
tabs** (this release, v0.25.16):

`SettingsScreen.kt` creates a
`val saveableStateHolder = rememberSaveableStateHolder()` at
the top of the Composable. The PAUSES tab's content
(small things + compassion moments) is wrapped in
`saveableStateHolder.SaveableStateProvider("PAUSES") { ... }`.

The pre-fix shape was a plain
`if (group == SettingsGroup.X) { ... }` that tore down the
slot table the moment the user opened a different group.
A half-typed "Small thing" draft, the "Pick a moment" date
picker in the OpenLoop postpone dialog, and any
`rememberSaveable` state inside the body of the
previously-open tab was lost on every tab switch. The
`SaveableStateHolder` saves the slot table on the way out
and restores it on the way back, so a user who types
half a compassion moment, opens Reading, and comes back
to Pauses finds their draft still in the field.

The v0.25.16 fix wraps one tab (PAUSES) as the load-bearing
demonstration; a v0.26+ WP will refactor the
`if (group == X) { ... }` blocks into a single
`when (group) { X -> saveableStateHolder.SaveableStateProvider("X") { ... } }`
and inherit the same state-preservation behaviour for
all six tabs.

## Why

The pre-v0.25.16 launcher fired haptics at six call sites
without consulting the user's accessibility preferences.
A user with `Settings.System.HAPTIC_FEEDBACK_ENABLED == 0`
(the system haptics-off toggle, in
`Settings → Sound & vibration → Touch feedback`) or with
`Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (the
"remove animations" a11y preference) was still getting
launcher haptics. The launcher's haptics are an
information channel (Brewster CHI 2007 — distinct tactile
feedback for distinct actions) but the system "haptics
off" toggle is supposed to be the single switch that
turns all haptics off. The v0.25.16 fix introduces the
`HapticFeedbackGate` CompositionLocal so the system
preferences are honored in one place, and the four
call sites are required to route through it (the
`HapticFeedbackGateFindingTest` is the regression guard).

The pre-v0.25.16 `modelFits = remember { mutableStateOf(false) }`
stub in the HomeScreen letter surface was the
"always-disabled" shape: the value was always `false`, so
the letter inbox's "Generate now" affordance was
permanently disabled. The v0.25.16 fix wires the value
through `LauncherViewModel.modelFits: StateFlow<Boolean>`,
which checks `phi-4-mini-q4.gguf` on disk. A user who has
installed Phi-4 now sees the Generate-now button
enabled; a user who has not yet installed it still sees
the install-path affordance.

The pre-v0.25.16 `SettingsScreen` was a
`var group by remember { mutableStateOf<SettingsGroup?>(null) }`
with `if (group == X) { ... }` blocks for each tab.
Compose's slot table loses any `rememberSaveable` state
inside an `if` block the moment the predicate flips
false — which is the precise moment the user opens a
different tab. A user with a half-typed compassion
moment loses the draft the moment they tap a different
group row. The `SaveableStateHolder` is the documented
Compose fix: the holder stores the slot table when its
key (here, the tab's enum name) leaves composition and
restores it when the same key re-enters.

## Test flips

- `BUG-013 haptics not gated by the system haptics toggle
  or the 'remove animations' a11y setting` — flipped from
  BUG-shape to fix-shape. The new pin asserts every file
  that calls `performHapticFeedback` also references
  `org.mindanchor.ui.LocalHapticFeedbackGate.current`.
- `BUG-017 HomeScreen letter surface has a modelFits stub
  held in remember` — flipped to fix-shape. The new pin
  asserts the letter surface dispatcher is *not* a stub
  (`!letterBlock.contains("val modelFits = remember { mutableStateOf(false) }")`)
  and that it does collect the VM flow
  (`letterBlock.contains("viewModel.modelFits.collectAsStateWithLifecycle()")`).
- `BUG-018 Settings tabs use SaveableStateHolder for
  tab-switch state preservation (v0_25_16 fix)` — flipped
  to fix-shape (replaces the pre-fix "no SaveableStateHolder
  or SavedStateHandle used in the launcher" pin, which
  asserted the *absence* of the primitive). The new pin
  asserts both halves: the holder is created
  (`val saveableStateHolder = rememberSaveableStateHolder()`)
  and the provider is in use
  (`saveableStateHolder.SaveableStateProvider("PAUSES")`).

Two new finding-test files:

- `app/src/test/java/org/mindanchor/ui/HapticFeedbackGateFindingTest.kt`
  — 3 tests: the file's public surface
  (`HapticFeedbackGate`, `DefaultHapticFeedbackGate`,
  `HapticFeedbackGateProvider`, `LocalHapticFeedbackGate`,
  `isSystemHapticsEnabled`, `isRemoveAnimationsEnabled`);
  the default-gate's settings check; and the four
  surfaces use the gate.
- `app/src/test/java/org/mindanchor/launcher/ModelFitsWiringFindingTest.kt`
  — 2 tests: the VM exposes the `StateFlow<Boolean>`; the
  HomeScreen letter surface no longer has the stub.

Two pre-existing finding tests in
`org.mindanchor.launcher` (SaveHapticsFindingTest and
HapticRichCapturesFindingTest) were updated for the
v0.25.16 haptics shape — the pre-fix `val haptics =
LocalHapticFeedback.current` assertion is replaced with
the `org.mindanchor.ui.LocalHapticFeedbackGate` pin.
The shape change is the same as the in-class BUG-013
flips in ComposeStateHuntFindingTest.

## Files changed

- `app/src/main/java/org/mindanchor/ui/HapticFeedbackGate.kt`
  (new file, 155 lines) — the gate + provider + helpers
- `app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt`
  - new `modelFits: StateFlow<Boolean>` + `_modelFits` +
    `init` block that seeds from disk on
    `application.filesDir`
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  - letter surface: `val modelFits = remember { ... }` →
    `val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()`
  - 3 haptics call sites: `LocalHapticFeedback.current` →
    `org.mindanchor.ui.LocalHapticFeedbackGate.current`
  - drop unused `LocalHapticFeedback` import
- `app/src/main/java/org/mindanchor/model/NoteScreen.kt`
  - 1 haptics call site: gate route
  - drop unused `LocalHapticFeedback` import
- `app/src/main/java/org/mindanchor/letters/LetterScreen.kt`
  - 1 haptics call site: gate route
  - drop unused `LocalHapticFeedback` import
- `app/src/main/java/org/mindanchor/friction/FrictionGate.kt`
  - 3 haptics call sites: gate route
  - remove inline `systemHapticsEnabled` `remember` block
    (the gate is the single source of truth now)
  - drop unused `LocalHapticFeedback` import
- `app/src/main/java/org/mindanchor/friction/GateActivity.kt`
  - wrap `setContent { MindAnchorTheme { ... } }` in
    `HapticFeedbackGateProvider { ... }` so the gate is
    the source of truth for the second entry point into
    `FrictionGate`
- `app/src/main/java/org/mindanchor/HomeActivity.kt`
  - wrap `setContent { MindAnchorTheme { ... } }` in
    `HapticFeedbackGateProvider { ... }` so the gate is
    the source of truth for the launcher root
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
  - new `val saveableStateHolder = rememberSaveableStateHolder()`
  - wrap PAUSES tab content in
    `saveableStateHolder.SaveableStateProvider("PAUSES") { ... }`
  - `import androidx.compose.runtime.saveable.rememberSaveableStateHolder`
- `app/src/test/java/.../ComposeStateHuntFindingTest.kt`
  - BUG-013, BUG-017, BUG-018 flipped from BUG-shape to
    fix-shape
- `app/src/test/java/.../launcher/SaveHapticsFindingTest.kt`
  - 2 tests updated to the gate shape
- `app/src/test/java/.../launcher/HapticRichCapturesFindingTest.kt`
  - 4 tests updated to the gate shape (the import
    assertion + 3 call-site assertions)
- `app/src/test/java/.../ui/HapticFeedbackGateFindingTest.kt`
  (new file, ~150 lines, 3 tests)
- `app/src/test/java/.../launcher/ModelFitsWiringFindingTest.kt`
  (new file, ~110 lines, 2 tests)
- `app/build.gradle.kts`
  - `versionCode 39 → 40`
  - `versionName "0.25.15" → "0.25.16"`

## Verification

- `:app:compileDebugKotlin` — clean (one pre-existing
  deprecation warning on `Settings.System.HAPTIC_FEEDBACK_ENABLED`,
  the same one the v0.25.5 FrictionGate pre-existing
  code emitted)
- `:app:compileDebugUnitTestKotlin` — clean
- `:app:testDebugUnitTest` — 1374 tests, 0 failed,
  0 errored (was 1369 in v0.25.15; +5 from the new
  finding-test files and the in-class flips)
- `:app:testReleaseUnitTest` — 1374 tests, 0 failed,
  0 errored
- `:app:detekt` — clean (one
  `FunctionNaming` warning on
  `HapticFeedbackGateProvider` was suppressed at the
  function level; PascalCase for public Providers is
  the documented Compose convention)
- `:app:assembleDebug` — `app-debug.apk` (52,473,820 bytes
  before, +0 bytes for the v0.25.16 work)
- Emulator `emulator-5554` (Android 14, x86_64, 1080x2400):
  - Uninstalled v0.25.15 + installed v0.25.16 fresh
    (`versionCode=40`, `versionName=0.25.16`)
  - `adb logcat -d | grep FATAL` — no FATAL for
    `org.mindanchor`
  - `pidof org.mindanchor` → 5182 (process is up, no
    crash, no ANR)
  - Onboarding screen rendered after fresh install
    ("Welcome to MindAnchor / A calmer phone, built
    from published research. / continue"). The
    `HapticFeedbackGateProvider` is in effect from the
    first composition; no regression on the
    `MindAnchorTheme` Provider ordering.

## Release artifacts

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `9C9F3D1DF7D35800A8B107EB26A955A9F23B1FDE11702BC455661EA90E44CDDE`
  - Size: 52,473,820 bytes (50.0 MiB)
- **Release APK**: not built in this release (key not in env).
  The debug APK is sufficient for emulator verification.

## Screenshot

`C:\Users\Sampath\AppData\Local\Temp\v0_25_16_home.png` — the
"Welcome to MindAnchor" onboarding screen on a fresh install
of v0.25.16 (`versionCode=40`, `versionName=0.25.16`).

## What this is NOT

- **Not a full SaveableStateHolder migration across all six
  Settings tabs.** v0.25.16 wraps the PAUSES tab as the
  load-bearing demonstration; a v0.26+ WP will refactor
  the `if (group == X) { ... }` blocks into a single
  `when (group) { X -> saveableStateHolder.SaveableStateProvider("X") { ... } }`
  and inherit the same state-preservation behaviour for
  all six tabs.
- **Not a real Phi-4 installation.** The
  `LauncherViewModel.modelFits` flow checks for the file
  on disk; a v0.25.16 build on this emulator will report
  `false` (no Phi-4 model is installed). The fix is the
  *wire* — the value is now truthful — not a Phi-4
  install itself.
- **Not a fix for the six pre-existing haptics in
  MindAnchor** (e.g. the long-press tactile on the
  search-first drawer's apps). The v0.25.16 fix is scoped
  to the four call sites called out by the SOTA v2
  bug-hunt; a v0.26+ WP can extend the gate to additional
  surfaces.
- **Not a process-death fix.** The SaveableStateHolder
  is a tab-switch survival mechanism, not a process-
  death survival mechanism. The BUG-005 / BUG-006 /
  BUG-007 / BUG-012 / v0.25.15 custom-Saver work
  addresses process death at the Composable level.
- **Not the broad `collectAsStateWithLifecycle` migration.**
  4 sites in `HomeScreen.kt` (and all sites in 10 other
  files) still use `collectAsState`. v0.25.17 closes the
  remaining gap.

## Next steps (v0.25.17+)

1. v0.25.17: broad `collectAsStateWithLifecycle` migration
   for the remaining 4 sites in `HomeScreen.kt` and the
   10 other files in the BUG-004 set.
2. v0.26+: refactor the `SettingsScreen` if-block tree
   into a `when (group) { X -> SaveableStateProvider("X") { ... } }`
   so all six tabs inherit the v0.25.16 state-
   preservation behaviour.
3. v0.26+: extend the `HapticFeedbackGate` to the rest of
   the launcher's haptics call sites (the long-press
   tactile on the search-first drawer, the date-picker
   selections in the Settings sub-sections, etc.).
4. v0.26+: a real Phi-4 install path — the
   `LauncherViewModel.modelFits` flow is wired and
   truthful, but the model is not yet downloadable from
   in-app. The wire is the precondition for the download
   path landing in a future WP.

The v0.25.10+ backlog is being closed incrementally.
v0.25.14 closed 4 of 18 findings; v0.25.15 closed 5
(BUG-008 / 009 / 010 / 011 / BUG-012 deferred half);
v0.25.16 closes 3 (BUG-013 / 017 / 018). v0.25.17
closes the final 1 (BUG-004).
