# 21 — LauncherViewModel split (FrictionViewModel, PulseViewModel, SleepViewModel, SettingsViewModel)

## Why this brief

`LauncherViewModel.kt` is 434 lines and owns four
distinct concerns:

1. **Friction** — `gateFor()`, `adaptiveTone()`,
   `banditContext()`, `recordNeverMind()`,
   `launchTimed()`, plus the FrictionBandit and
   GateLedger interactions.
2. **Pulse / bedtime** — the bedtime list flow, the
   open-loop flow, the minuteTick-driven phase
   calculation.
3. **Settings** — `searchQuery`, the small-things
   flow, the if-then plan flow, the compassion flow.
4. **The app launcher itself** — `LauncherUiState`,
   the favorites, the friction packages, the
   always-open packages.

For a single-developer project of this size, the
*current* shape is fine. The senior-architect review
flagged it as "doing too much" because the v0.20.0
SOTA additions pushed the file from "manageable" to
"needs to be split." The right refactor: four
ViewModels, one for each concern, and a small
`LauncherViewModel` orchestrator that composes them.

The split is structural code that the project's
Python-mirror test pattern cannot fully validate —
it needs a real Kotlin compiler. The refactor
strategy here is *incremental and reversible*: the
new ViewModels are extracted first, the
`LauncherViewModel` becomes a thin facade that
delegates to them, and the callers update in a
follow-up commit. The public API of
`LauncherViewModel` does not change.

## Primary research

- Android Architecture Components guide, ViewModel:
  https://developer.android.com/topic/libraries/architecture/viewmodel
  - "A ViewModel is scoped to the Lifecycle of the
    object it's attached to. ... This means a
    ViewModel will not be destroyed if its owner
    is destroyed for a configuration change."
- "Now in Android" sample (Google's official
  Android architecture sample):
  https://github.com/android/nowinandroid
  - The "feature" module pattern: each feature has
    its own ViewModel; the app composes the feature
    ViewModels in a top-level shell.
- Hilt vs. manual DI: the project does not use Hilt
  (a deliberate choice — the dependency graph is
  hand-rolled to keep the binary small). The
  ViewModel split follows the same hand-rolled
  pattern: each new ViewModel takes the `Application`
  in its constructor and reads the data layer
  directly. No DI framework is added.

## What this PR ships

1. `FrictionViewModel.kt` — the friction-gate
   concerns, extracted from `LauncherViewModel`.
   Owns: `gateFor()`, `adaptiveTone()`,
   `banditContext()`, `recordNeverMind()`,
   `launchTimed()`, the FrictionBandit and
   GateLedger interactions. ~150 lines.

2. `LauncherViewModel.kt` — refactored to be a thin
   facade. The friction methods now delegate to
   `FrictionViewModel`. The public API is unchanged:
   callers continue to call
   `launcherViewModel.gateFor(...)` and the
   delegation is transparent.

3. `FrictionViewModelTest.kt` — the friction logic
   is now testable in isolation, without the
   other three concerns in the test fixture. The
   test surface is smaller and the tests are
   focused.

## What this PR does NOT ship

- The split of `PulseViewModel`, `SleepViewModel`,
  and `SettingsViewModel`. These are follow-up
  commits that each get their own PR.
- The split of `LauncherUiState`. The UI state
  combines four concerns; splitting it requires
  splitting the home screen Composable, which is
  the follow-up to the follow-up.
- Hilt or any DI framework. The project is
  hand-rolled; this refactor follows the existing
  pattern.

## Risk

- The `LauncherViewModel.gateFor()` callers in
  `HomeScreen.kt` and `GateActivity.kt` continue to
  work because the public API does not change. The
  refactor is a *private* change in the public-API
  sense: the existing methods now delegate.
- The `Application` instance is shared across the
  new `FrictionViewModel` and the existing
  `LauncherViewModel`. Both write to the same
  `FrictionPrefs` DataStore. Concurrent writes are
  serialized by the DataStore's coroutine flow;
  the refactor does not introduce a race condition.
- The `viewModelScope` on the new
  `FrictionViewModel` is a separate scope from
  `LauncherViewModel.viewModelScope`. A flow
  collected in the new scope outlives one and is
  cancelled by the other. This is intentional:
  the friction state should outlive a
  `LauncherViewModel` recreation (e.g. on config
  change) and be cancelled only when the
  `FrictionViewModel` itself is cleared. The
  existing `viewModelScope`-lived flows
  (`gateTallies`, `banditState`, `smallThings`)
  are now collected in the new scope.

## Verification

- Brace/paren balance re-checked on all changed
  files.
- All 117 existing assertions from v0.20.0 + v0.20.1
  + the SOTA work must still pass.
- The `gateFor` signature and behavior must be
  byte-for-byte identical to before the refactor;
  the existing `GateContextTest` (3 cases) is the
  ground truth.
- 2 new tests in `FrictionViewModelTest.kt`:
  1. The new ViewModel constructs from the same
     Application and reads the same prefs
  2. The delegated `gateFor()` produces the same
     `GateContext` as the previous implementation

## Primary sources

- Android Architecture Components: ViewModel
- Now in Android (Google's architecture sample)
- The project's own SOTA-IMPROVEMENT-REPORT.md
  (the v1.2 bandit section that pushed
  `LauncherViewModel` over the threshold)
