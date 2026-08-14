# v0.25.17 — v0.25.10+ backlog sweep: broad `collectAsStateWithLifecycle` migration

**Tag**: `v0.25.17` on `feature/v0.25.15-17-state-cleanup`
**Version code**: 41 (was 40)
**Version name**: 0.25.17 (was 0.25.16)
**Test count**: 1375 debug + 1375 release = **2750 / 0 failed** (was 2748 in v0.25.16; +1 from the per-file BUG-004 regression guard added in this release — the in-class flips for the 4 v0.25.14 / 6 v0.25.16 / 3 v0.25.17 findings replaced 4 BUG-shape tests with fix-shape tests, the new `collectAsStateWithLifecycle` per-file pin is a positive pin on top of the original "at least one" pin)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.17

## What this release does

One SOTA v2 bug-hunt finding closed with a broad
`collectAsState` → `collectAsStateWithLifecycle` migration
across the 12-file main-source set.

**Part 1 — BUG-004: every `collectAsState` call site
migrated to the lifecycle-aware primitive** (this
release, v0.25.17):

The migration touches **15 call sites** across **11 files**
(4 sites in `HomeScreen.kt` + 11 sites in 10 other files):

- `app/src/main/java/org/mindanchor/HomeActivity.kt`
  - `onboardingPrefs.done.collectAsState(initial = null)`
  - `goHomeSignal.collectAsState()`
  - `letterDateSignal.collectAsState()`
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  - `reportStore.stored.collectAsState(initial = null)`
  - `viewModel.showIntroCallout.collectAsState()`
  - `viewModel.letterSize.collectAsState()` (the
    letter surface, distinct from the SettingsViewModel
    side which was already migrated in v0.25.16)
  - `viewModel.searchQuery.collectAsState()` (the
    search-first drawer's query)
- `app/src/main/java/org/mindanchor/model/CheckInHistoryActivity.kt`
  - `prefs.checkIns.collectAsState(initial = CheckInState())`
- `app/src/main/java/org/mindanchor/settings/GoogleDriveBackupSettingsSection.kt`
  - `auth.signedInEmailFlow.collectAsState(initial = null)`
  - `viewModel.autoSyncNotes.collectAsState()`
  - `viewModel.autoSyncLetters.collectAsState()`
- `app/src/main/java/org/mindanchor/vitals/PpgScreen.kt`
  - `capture.state.collectAsState()`
  - `sessionStore.recent(3).collectAsState(initial = emptyList())`
- `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt`
  - `viewModel.history.collectAsState()`
- `app/src/main/java/org/mindanchor/report/ReportScreen.kt`
  - `store.stored.collectAsState(initial = null)`
  - `store.facts.collectAsState(initial = null)`
  - `store.feedback.collectAsState(initial = null)`
  - `readerPrefs.size.collectAsState(initial = ReadingSize.MEDIUM)`
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
  - `viewModel.plan.collectAsState()`
  - `viewModel.contacts.collectAsState()`
- `app/src/main/java/org/mindanchor/digest/DigestScreen.kt`
  - `viewModel.journal.collectAsState()`
- `app/src/main/java/org/mindanchor/ui/CalmBackground.kt`
  - `appearance.scene.collectAsState(initial = null)`

Each call site uses the lifecycle-aware primitive with
the same `initialValue` as the pre-fix `initial`
parameter. The import in each file changes from
`androidx.compose.runtime.collectAsState` to
`androidx.lifecycle.compose.collectAsStateWithLifecycle`.

`NoteActivity.kt` and `SettingsScreen.kt` were already on
`collectAsStateWithLifecycle` from prior work (v0.25.14
+ v0.25.16 respectively) and did not need migration.

## Why

`collectAsState` does not pause when the Composable is
STOPPED. The flow keeps producing, the recomposer keeps
listening, the ViewModel never gets to drop a stale
state. `collectAsStateWithLifecycle` ties the collector
to the Compose tree's Lifecycle; the default
`minActiveState = STARTED` is the right shape for a
home surface: the flow is paused when the activity is in
the background, resumed when it comes back to the
foreground.

The original BUG-004 FindingTest passed naturally as
soon as `HomeScreen.kt` had the primitive in any of the
7 sites (v0.25.14). v0.25.16 expanded to 8 sites across
`HomeScreen.kt` + `SettingsScreen.kt`. The v0.25.17
broad migration closes the finding for the entire
12-file main-source set: every `collectAsState` use
across the launcher is now the lifecycle-aware variant.

## Test flips

The original "at least one" BUG-004 pin is unchanged (it
is a positive pin — must be present somewhere) and still
passes as a sanity check. v0.25.17 adds a stronger
per-file regression guard:

- `BUG-004 every main-source Composable in the 12-file
  set uses collectAsStateWithLifecycle (v0_25_17 fix)` —
  a new positive pin that asserts the primitive is in
  use in *every* one of the 12 files. A v0.25.17+
  regression that reverts a single file to plain
  `collectAsState` flips this assertion red with a
  per-file error message (`missing=[$filename (no
  collectAsStateWithLifecycle)]`).

## Files changed

- `app/src/main/java/org/mindanchor/HomeActivity.kt` —
  3 sites migrated, `collectAsState` import dropped
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` —
  4 sites migrated, `collectAsState` import dropped
- `app/src/main/java/org/mindanchor/model/CheckInHistoryActivity.kt` —
  1 site migrated, import swapped
- `app/src/main/java/org/mindanchor/settings/GoogleDriveBackupSettingsSection.kt` —
  3 sites migrated, import swapped
- `app/src/main/java/org/mindanchor/vitals/PpgScreen.kt` —
  2 sites migrated, import swapped
- `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt` —
  1 site migrated, import swapped
- `app/src/main/java/org/mindanchor/report/ReportScreen.kt` —
  4 sites migrated, import swapped
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt` —
  2 sites migrated, import swapped
- `app/src/main/java/org/mindanchor/digest/DigestScreen.kt` —
  1 site migrated, import swapped
- `app/src/main/java/org/mindanchor/ui/CalmBackground.kt` —
  1 site migrated, import swapped
- `app/src/test/java/.../ComposeStateHuntFindingTest.kt` —
  new per-file BUG-004 pin added (the original
  "at least one" pin is unchanged)
- `app/build.gradle.kts`
  - `versionCode 40 → 41`
  - `versionName "0.25.16" → "0.25.17"`

## Verification

- `:app:compileDebugKotlin` — clean
- `:app:compileDebugUnitTestKotlin` — clean
- `:app:testDebugUnitTest` — 1375 tests, 0 failed,
  0 errored (was 1374 in v0.25.16; +1 from the per-file
  BUG-004 pin)
- `:app:testReleaseUnitTest` — 1375 tests, 0 failed,
  0 errored
- `:app:detekt` — clean
- `:app:assembleDebug` — `app-debug.apk` (52,473,820 bytes,
  unchanged from v0.25.16)
- Emulator `emulator-5554` (Android 14, x86_64, 1080x2400):
  - Uninstalled v0.25.16 + installed v0.25.17 fresh
    (`versionCode=41`, `versionName=0.25.17`)
  - `adb logcat -d | grep FATAL` — no FATAL for
    `org.mindanchor`
  - `pidof org.mindanchor` → 5865 (process is up, no
    crash, no ANR)
  - Onboarding screen rendered after fresh install
    ("Welcome to MindAnchor / A calmer phone, built
    from published research. / continue"). The
    `HapticFeedbackGateProvider` is in effect from the
    first composition; no regression on the
    `MindAnchorTheme` Provider ordering. The 3
    `collectAsStateWithLifecycle(initialValue = null)`
    calls in `HomeActivity.kt` show the calm
    "still loading" sky on first composition, then
    transition to the onboarding screen once the
    `onboardingPrefs.done` flow emits.

## Release artifacts

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `05A86FC7C3A9E97AC16FE82D94A7E6AC9D73AF58FBE4C6EE0D32C35972818CD1`
  - Size: 52,473,820 bytes (50.0 MiB)
- **Release APK**: not built in this release (key not in env).
  The debug APK is sufficient for emulator verification.

## Screenshot

`C:\Users\Sampath\AppData\Local\Temp\v0_25_17_home.png` — the
"Welcome to MindAnchor" onboarding screen on a fresh install
of v0.25.17 (`versionCode=41`, `versionName=0.25.17`).

## What this is NOT

- **Not a process-death fix.** `am force-stop` still kills
  the activity without `onSaveInstanceState`. The
  `collectAsStateWithLifecycle` migration is the
  lifecycle-state backpressure fix (a STOPPED surface
  stops collecting); the `rememberSaveable` / custom
  `Saver` work in v0.25.14 / v0.25.15 is the process-death
  survival fix.
- **Not the full BUG-004 FindingTest rewrite.** The
  original "at least one" pin is preserved (sanity
  check); the v0.25.17 release adds a stronger per-file
  regression guard on top. The two pins together
  document both the original finding (the primitive
  must be in use somewhere) and the v0.25.17 fix (the
  primitive is in use in every file).
- **Not the v0.26.x work.** This release closes the
  v0.25.10+ backlog. The v0.26.0+ WPs (SaveableStateHolder
  refactor for all 6 Settings tabs, haptics-gate
  extension to the rest of the launcher's haptics call
  sites, a real Phi-4 install path) are independent
  work that lands in v0.26+.

## Next steps (v0.26+)

1. Refactor `SettingsScreen` if-block tree into a
   `when (group) { X -> saveableStateHolder.SaveableStateProvider("X") { ... } }`
   so all 6 Settings tabs inherit the v0.25.16
   SaveableStateHolder state-preservation behaviour
   (the v0.25.16 fix wraps only the PAUSES tab as the
   load-bearing demonstration).
2. Extend `HapticFeedbackGate` to additional surfaces
   (the long-press tactile on the search-first drawer's
   apps, the date-picker selections in the Settings
   sub-sections, etc.).
3. A real Phi-4 install path — the
   `LauncherViewModel.modelFits` flow is wired and
   truthful, but the model is not yet downloadable from
   in-app. The wire is the precondition for the download
   path landing in a future WP.

The v0.25.10+ backlog is now fully closed:

- v0.25.14: BUG-005, BUG-006, BUG-007, BUG-012 partial (4)
- v0.25.15: BUG-008, BUG-009, BUG-010, BUG-011, BUG-012
  deferred (5)
- v0.25.16: BUG-013, BUG-017, BUG-018 (3)
- v0.25.17: BUG-004 (1)
- **Total: 13 of 13 SOTA v2 bug-hunt findings fixed
  across 4 releases, +1 release note (v0.25.15) for
  the 16 KB page-size fix that closed the Android 15+
  loader refusal warning.**
