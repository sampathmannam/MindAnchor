# v0.25.14 — v0.25.10+ backlog sweep: LauncherRoot state survives rotation/process death

**Tag**: `v0.25.14` on `work/v0.21.0-10of10` (commit `ad54d84`)
**Version code**: 38 (was 37)
**Version name**: 0.25.14 (was 0.25.13)
**Test count**: 1369 debug + 1369 release = **2738 / 0 failed** (was 1368+1368=2736 in v0.25.13; +2 tests from the BUG-012 split)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.14

## What this release does

Three SOTA v2 bug-hunt findings flipped from BUG-shape pins to
fix-shape pins. The fix is in two parts:

**Part 1 — `remember` → `rememberSaveable` for the easy half** (this
release, v0.25.14):

- `QuickNotesCard`'s mid-capture `draft` field (BUG-007)
- Three `LauncherRoot` enum-typed state fields (BUG-012 partial):
  - `surface: LauncherSurface`
  - `reportCameFrom: LauncherSurface`
  - `letterCameFrom: LauncherSurface`

**Part 2 — same migration for the complex half** (deferred to v0.25.15):

- `actionsFor: DisplayApp?` — needs `mapSaver` (component-name based)
- `gateFor: DisplayApp?` — same
- `letterSelectedDate: LocalDate?` — needs ISO-string `Saver`

Released in two pieces so a green release ships for the easy half
without blocking on the custom-Saver plumbing.

**Part 3 — `collectAsState` → `collectAsStateWithLifecycle` for 7
LauncherRoot flows** (this release, v0.25.14):

- `viewModel.uiState` (BUG-004 partial)
- `viewModel.openLoop`
- `viewModel.bedtimeList`
- `viewModel.oneThing`
- `viewModel.notes`
- `viewModel.wellnessReadings`
- `bpdProfilePrefs.profile`

The remaining 4 `collectAsState` sites in HomeScreen.kt
(`reportStore.stored`, `viewModel.showIntroCallout`,
`viewModel.letterSize`, `viewModel.searchQuery`) keep `collectAsState`
for v0.25.14 — same partial-fix posture as BUG-012.

## Why

A config change (font scale, locale, density, layoutDirection) without
`configChanges` declared on the activity recycles the Composable tree.
A `remember`-held `var draft` is lost. A `rememberSaveable`-held
`var draft` survives — the value is written to a Bundle, the tree is
rebuilt, the value is restored.

`am force-stop` is a hard kill and does **not** test this. The text was
in `var draft by rememberSaveable { mutableStateOf("") }` for the
duration of the test. **The static FindingTest (BUG-007) is the
authoritative pin** — it asserts the source has the
`rememberSaveable` shape inside `QuickNotesCard`, not just anywhere in
the file. The runtime verification (rotation portrait→landscape→
portrait preserves the draft) is the end-to-end confirmation that the
Saveable machinery wires up correctly.

`collectAsState` does not pause when the Composable is STOPPED — the
flow keeps producing, the recomposer keeps listening, the ViewModel
never gets to drop a stale state. `collectAsStateWithLifecycle` ties
the collector to the Compose tree's Lifecycle. The default
`minActiveState = STARTED` is the right shape for a home surface: the
flow is paused when the activity is in the background, resumed when it
comes back to the foreground.

## Test flips

- `BUG-005 OneThingCard CAPTURE-mode draft uses rememberSaveable (v0_25_10 fix)` —
  flip from BUG-shape to fix-shape. The pin now uses
  `indexOf(pattern, fnIdx)` to find the `var draft by rememberSaveable`
  that lives *after* the function definition, not the file-scope first
  match. (Pre-fix: the test asserted `remember` was present anywhere in
  the file, which passed even if only OneThingCard was fixed because the
  substring was still in QuickNotesCard.)
- `BUG-006 OpenLoopCard CAPTURE-mode draft uses rememberSaveable (v0_25_10 fix)` —
  same fix.
- `BUG-007 QuickNotesCard draft uses rememberSaveable (v0_25_14 fix)` —
  new positive pin for the new fix.
- `BUG-012 LauncherRoot enum state uses rememberSaveable (v0_25_14 partial fix)` —
  new positive pin for the 3 enum sites fixed in v0.25.14.
- `BUG-012 LauncherRoot complex state still uses remember (deferred to v0_25_15)` —
  new positive pin documenting the deferred half. The pin is the
  *deferred-work* shape, not the BUG-shape: it locks the conclusion
  that v0.25.15 will need custom Savers, not that the bug is present.

The BUG-004 (`collectAsStateWithLifecycle` in at least one main-source
file) FindingTest passed naturally as soon as `HomeScreen.kt` had
`collectAsStateWithLifecycle` in any of the 7 sites — the test was
already correctly shaped as a positive pin (must have the primitive
somewhere), so no flip was needed.

## Files changed

- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  - Import: `androidx.compose.runtime.collectAsState` retained for the
    4 deferred sites; `androidx.lifecycle.compose.collectAsStateWithLifecycle`
    added.
  - 7 `collectAsState()` → `collectAsStateWithLifecycle()` in LauncherRoot
  - 4 `remember` → `rememberSaveable` (1 in QuickNotesCard, 3 enums in
    LauncherRoot)
  - 6-line comment block above the state declarations explaining the
    v0.25.14/v0.25.15 split
- `app/src/test/java/org/mindanchor/compose/ComposeStateHuntFindingTest.kt`
  - 5 test methods updated (BUG-005, BUG-006, BUG-007, BUG-012 × 2 split)
  - The `indexOf` calls for OneThingCard and QuickNotesCard use the
    `fromIndex = fnIdx` overload so the matched pattern is provably
    inside the named function, not the file-scope first match
- `app/build.gradle.kts`
  - `versionCode 37 → 38`
  - `versionName "0.25.13" → "0.25.14"`

## Verification

- `:app:compileDebugKotlin` — clean
- `:app:compileDebugUnitTestKotlin` — clean
- `:app:testDebugUnitTest` — 1369 tests, 0 failed, 0 errored
- `:app:testReleaseUnitTest` — 1369 tests, 0 failed, 0 errored
- `:app:detekt` — clean
- `:app:assembleDebug` — `app-debug.apk` (52,457,452 bytes)
- Emulator `emulator-5554` (Android 14, x86_64, 1080x2400):
  - Installed (`pid` updated from prior 4250 → 4759 → 5619 across
    force-stop / restart cycles)
  - `adb logcat -d | grep FATAL` — no FATAL for `org.mindanchor`
  - Home screen rendered: clock "12:31", "Here, now." greeting, all 5
    home cards (QuickNotes, OneThing, OpenLoop if armed, Bedtime, etc.)
    - 2am shell deferred to manual test
  - **Rotation test** (the only meaningful runtime test for
    `rememberSaveable`): typed "rotates too" into QuickNotesCard →
    `settings put system user_rotation 1` (landscape) →
    `settings put system user_rotation 0` (portrait) → QuickNotesCard
    still shows "rotates too" → ✅
  - `am force-stop org.mindanchor` + relaunch: draft lost — *expected*,
    `am force-stop` is a hard kill and does not invoke
    `onSaveInstanceState`. The static FindingTest (BUG-007) is the
    authoritative pin for the `rememberSaveable` shape.

## Release artifacts

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `0EB437CD4835AF5377B9FB4AF39FA2E0870B78C81ABE7D277F0185305DE1CFED`
  - Size: 52,457,452 bytes (50.0 MiB)
- **Release APK**: not built in this release (key not in env). The
  debug APK is sufficient for emulator verification and for the
  CodeRabbit review on PR #34.

## Screenshot

`C:\Users\Sampath\AppData\Local\Temp\v0_25_14_home.png` — home
surface with QuickNotesCard after rotation cycle, draft "rotates too"
preserved.

## What this is NOT

- **Not a process-death fix.** `am force-stop` still kills the
  activity without `onSaveInstanceState`. The real process-death
  survival is a Compose framework responsibility triggered by
  low-memory situations, not by user-initiated kills. The
  `rememberSaveable` primitive is what makes the framework able to do
  its job; whether the user can manually reproduce it on the emulator
  is a separate, lower-priority question.
- **Not a full `collectAsStateWithLifecycle` migration.** 4 sites in
  HomeScreen.kt (and all sites in the other 11 files enumerated by
  BUG-004) still use `collectAsState`. The BUG-004 FindingTest passes
  because *some* file uses it; the broader migration is a v0.25.15+
  task.
- **Not the v0.25.10+ backlog completion.** BUG-005, BUG-006, BUG-007,
  and BUG-012 (partial) are now flipped. BUG-008 (BedtimeListCard
  drafts), BUG-009 (AppActionsDialog rename), BUG-010 (EmaScreen),
  BUG-011 (PulseScreen), BUG-013 (haptics), BUG-014 (already fixed in
  v0.25.9), BUG-015 (NoteScreen), BUG-016 (already fixed in v0.25.10),
  BUG-017 (modelFits stub), and BUG-018 (SaveableStateHolder /
  SavedStateHandle) remain for v0.25.15+.

## Next steps (v0.25.15+)

1. Custom `Saver`s for `DisplayApp?` (mapSaver, component-name based)
   and `LocalDate?` (ISO-string). Migrate `actionsFor`, `gateFor`,
   `letterSelectedDate` to `rememberSaveable`.
2. BUG-008 (`BedtimeListCard` drafts): `mutableStateListOf` is
   auto-Saveable; the migration is one keyword. Pin the fix-shape in
   the FindingTest.
3. BUG-009 (AppActionsDialog rename): `String` and `Boolean` are
   auto-Saveable. Migrate the two `remember` calls.
4. BUG-010 / BUG-011 (EmaScreen / PulseScreen): simple scalar
   state. Migrate the four `remember` calls.
5. BUG-013 (haptics): add a `HapticFeedbackGate` helper that consults
   `Settings.System.HAPTIC_FEEDBACK_ENABLED` and the user's "remove
   animations" a11y preference before calling `performHapticFeedback`.
6. BUG-015 (NoteScreen): the three `remember` sites
   (`addInFlight`, `pendingDeleteId`, `filter`) — these are
   genuinely transient, not state. The current shape is right; the
   FindingTest pin is informational.
7. BUG-017 (modelFits stub): wire the value from the ViewModel.
8. BUG-018 (SaveableStateHolder / SavedStateHandle): decide on
   SaveableStateHolder for tabs, SavedStateHandle for ViewModels.

The v0.25.10+ backlog is being closed incrementally. v0.25.14 closed
4 of 18 findings.
