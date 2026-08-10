## v0.22.0 — P1 closed, WP-10 onboarding polish, citation audit

The v0.21.0 P1 finding (the simulation runner could not yet
drive `SleepWindowOptimizer` end-to-end on persona data) is
closed in v0.22.0, with a real bug found and fixed along the
way. WP-10 onboarding polish ships both its steps. A citation
audit document pins the verified-citation boundary.

### What's new

**Simulation runner drives `SleepWindowOptimizer` end-to-end.**
`WellnessSimulationRunner.summarize` builds `SleepWindow`
objects from each persona's 14 days of sleep onsets and
surfaces the optimizer's suggestion per persona. The P1
finding is verified by `SleepOptimizerRunnerTest` (7 tests
including the SparseData < 5 nights null-return). The
`SimulationReport` now prints the suggestion as a new line
per persona:

```
PERSONA: morning_lark_healthy — Healthy morning lark
  ...
  sleep suggestion   = median=22:25  start=20:55  end=06:25  (from 14 nights)
PERSONA: insomnia_anxious — Insomnia with elevated anxiety
  ...
  sleep suggestion   = median=03:30  start=02:00  end=11:30  (from 14 nights)
PERSONA: sparse_data_partial_wearable — Sparse wearable data persona
  ...
  sleep suggestion   = no suggestion yet (under 5 nights of usable data)
```

**Persona `sleepOnset` convention fix (real bug found).** The
`DailyVitals.sleepOnset` field documents itself as "minutes
after 18:00" (the `Deviation.minutesAfterSixPm` convention),
but four personas (Insomniac, ShiftWorker, NightOwl,
Depression) were storing raw minute-of-day values that
contradicted their own KDoc. Convention is fixed for all
four. The insomniac persona's median onset moved from
21:30 (the inverse of its own comment) to 03:30 (the cited
late-onset target); the shift worker's 4-4-6 day-evening-night
rotation now produces the citation-anchored median.

**WP-10 step 1: 3-screen onboarding.** The "pick" screen
combines goal elicitation and chronotype selection under
one "What fits?" heading. Both questions are optional; the
launcher leaves the rest off. The 4-screen flow becomes 3.
Under-60-seconds is the WP-10 acceptance target.

**WP-10 step 2: "what makes this different" callout.** One
line of small text below the home greeting, shown for the
first 3 home-surface displays and never again. Points at the
friction gate (long-press any app) without naming it. Driven
by `LauncherPrefs.INTRO_CALLOUT_LAUNCHES`.

**Citation audit doc (`docs/research/23-citation-audit.md`).**
The WP-1 deliverable the plan's file structure promised.
Lists every verified citation used in the codebase, every
misattribution that was corrected (the four "Bernardi 2018",
"Carney 2010", "aan het Rot 2012 BA", "Linardon 2020 J. Clin.
Psychol." cases), and every UNANCHORED claim that was
removed.

### Findings (this version)

P0: none.

P1: none. (The v0.21.0 P1 about the runner not driving the
optimizer end-to-end is closed; see the new tests and the
"sleep suggestion" line in the simulation report.)

P2:
- Documentation: the chronotype-persona mapping is
  editorial and lives in code, not docs. A future refactor
  should pull the mapping into a single source-of-truth.
- The suggested windows from the optimizer and the chronotype
  defaults are within 30 minutes for each anchored persona
  — the optimizer is reading the data, not re-deriving the
  chronotype, and the data is consistent with the chronotype.
  Future refactors that break this should be caught by
  `PersonaChronotypeTest`.

### Acceptance (gates)

- `./gradlew :app:detekt :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — clean
- 899 unit tests, 0 failures
- 8 personas × 14 days — no P0 or P1 simulation findings

### Files

- SHA-256: 3AA73BF4D3D356F6151CEC818337E99CE3AACA0D0ACFFD6F4780A878262EAB15
- Size: 10.6 MB
- Min SDK: 33 (Android 13); Target SDK: 35 (Android 15)

### Open follow-ups (out of v0.22.0 scope)

- **WP-9 live 2-week field test** continues on the physical
  device. Any real-device P0 merges into the same fix queue.
- **WP-10 walkthrough acceptance:** the 3rd-party walkthrough
  script lives at `docs/qa/3rd-party-onboarding-test.md`.
  Two non-developers need to be timed through the new
  3-screen flow; the actual walkthroughs are a human task
  that has to happen outside this codebase.

### Verifying

```
sha256sum app-release.apk
# 3aa73bf4d3d356f6151cec818337e99ce3aaca0d0acffd6f4780a878262eab15
```
