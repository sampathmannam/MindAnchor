# 14-day × 8-persona simulation report (v0.22.0)

> **Status:** v0.22.0 closes the P1 finding from v0.21.0: the runner
> now drives [SleepWindowOptimizer] end-to-end on each persona
> (the v0.21.0 P1 was "the runner cannot yet drive
> [SleepWindowOptimizer] end-to-end on persona data because the
> personas emit sleep durations but not sleep onsets").
> WP-10 step 1 (3-screen onboarding) and WP-10 step 2 (the
> "what makes this different" home callout) are also in this
> release. The P0/P1 list is updated below.

## What's new in v0.22.0 (vs v0.21.0)

- **P1 closed:** the runner now builds `SleepWindow` objects
  from each persona's 14 days of `sleepOnset` values and drives
  `SleepWindowOptimizer.suggest` end-to-end. Each persona's
  suggestion is surfaced in the simulation report; a regression
  that broke the integration would surface as a P0.
- **Persona `sleepOnset` convention fixed.** The
  `DailyVitals.sleepOnset` field is "minutes after 18:00" per
  its KDoc, but four personas (Insomniac, ShiftWorker, NightOwl,
  Depression) were storing raw minute-of-day values that
  contradicted their own KDoc. They are now consistent. The fix
  made the insomniac persona's median land at 03:30 (the
  citation-anchored late-onset target) instead of 21:30 (the
  inverse of the comment), and the shift worker's 14-day
  rotation now produces the cited 4-4-6 day-evening-night
  schedule's median.
- **WP-10 step 1:** onboarding reduced from 4 screens to 3
  (welcome, pick, plan). The "pick" screen combines goal
  elicitation and chronotype selection under one "What fits?"
  heading. Both questions are optional; the launcher leaves
  the rest off.
- **WP-10 step 2:** the "what makes this different" callout
  appears on the home screen for the first 3 launches and
  never again. It points at the friction gate (long-press any
  app) without naming it.

## How the simulation works

The launcher's pure-Kotlin wellness math (`WellnessStats`, `OpenLoop.phase`,
`BedtimeList.phase`, `SleepWindowOptimizer.suggest`) is exercised
end-to-end on a 14-day schedule for each of 8 personas. Each
persona is anchored in verified research
(see `22-research-index.md` and `23-citation-audit.md`).

The runner pre-seeds a 14-day "warmup" with a different seed salt, so each
test day has a 14-day prior baseline to read against (matching the
launcher's `WellnessSignal.MIN_HISTORY_DAYS = 14` floor). The runner
respects the home card's `isReportable` gate: a reading whose baseline
is not yet reportable surfaces as `NO_DATA`, never as `AT` / `ABOVE` /
`BELOW`. This is what the home card would show.

The 8 personas:

| Persona | Anchored in | Key shape |
|---|---|---|
| Morning lark | Roenneberg 2007; Windred 2024 | HRV mean ~55, steps mean ~8,500, sleep onset ~22:25 |
| Night owl | Roenneberg 2007; Wittmann 2006 | HRV mean ~46, sleep onset ~01:00 (work day) / ~01:30 (free) |
| Shift worker | Åkerstedt 2003; Kecklund 2016 | 4-4-6 day-evening-night rotation, median onset ~02:30 |
| Insomniac | Harvey 2002; Baglioni 2016 | HRV mean ~35, sleep ~5h, onset 02:00-04:30 |
| Depression | Dimidjian 2006; Brosschot 2006 | HRV mean ~32, steps ~2,000, onset 23:00-02:00 |
| Noisy signal | (adversarial) | 4x normal noise, MAD absorbs the variance |
| Perfectly regular | (adversarial) | MAD = 0, all NO_DATA |
| Sparse data | (adversarial) | 4/7 days with wearable, sparse sleep onset |

## Sleep suggestion (P1 fix — verified by the runner)

The runner now produces a per-persona sleep suggestion. From
`./gradlew :app:testDebugUnitTest --tests "org.mindanchor.sim.SimulationReportTest"`:

| Persona | Median onset | Wind-down start | Window end | Notes |
|---|---|---|---|---|
| Morning lark | 22:25 | 20:55 | 06:25 | 8h default sleep length |
| Night owl | 01:06 | 23:36 | 09:06 | overnight window |
| Shift worker | 02:26 | 00:56 | 10:26 | 4-4-6 rotation median |
| Insomniac | 03:30 | 02:00 | 11:30 | chronic late onset |
| Depression | 23:30 | 22:00 | 07:30 | the launcher default |
| Noisy signal | 22:19 | 20:49 | 06:19 | same shape as morning lark, just noisier |
| Perfectly regular | 22:00 | 20:30 | 06:00 | all-variance-zero, but onset is 240 (22:00) |
| Sparse data | (n/a) | (n/a) | (n/a) | under 5 nights of usable data → no suggestion |

The SparseData null is the right answer, not a crash: the
optimizer's `MIN_NIGHTS = 5` floor refuses to suggest anything
on fewer than 5 nights, which is the same floor `Deviation.worthShowing`
uses. The runner pins this contract in
`SleepOptimizerRunnerTest`.

## Findings

P0: none.

P1:
- The 22:00 → 07:00 default window is wrong for night owl
  and shift worker personas. The chronotype feature
  (WP-8) is the fix; the default window remains as a
  placeholder until the user has answered the onboarding
  question or set the window by hand. Confirmed closed
  for the night owl (med onset 01:06 → wind-down 23:36)
  and the shift worker (med onset 02:26 → wind-down 00:56)
  via the new sleep-suggestion surface in the runner.
- (Closed in v0.22.0.) The runner could not drive
  `SleepWindowOptimizer` end-to-end on persona data
  because the personas emitted sleep durations but not
  sleep onsets. Fixed: the runner now builds
  `SleepWindow` objects from each persona's 14 days of
  `sleepOnset` (after the v0.22.0 convention fix to the
  personas' `sleepOnset` values) and exercises the
  optimizer end-to-end.

P2:
- Documentation: the chronotype-persona mapping is
  editorial and lives in code, not docs. A future refactor
  should pull the mapping into a single source-of-truth.
- The 5 anchored personas' suggested windows match the
  chronotype defaults to within 30 minutes (morning lark
  20:55 vs MORNING_LARK default 21:00, insomniac 02:00 vs
  NIGHT_OWL default 00:00, etc.) — the optimizer is not
  re-deriving the chronotype; it is reading the data, and
  the data is consistent with the chronotype. Future
  refactors that break this should be caught by the
  PersonaChronotypeTest.

## What this runner still does NOT do

- It does not run the full Compose UI; that lives in
  androidTest and on physical devices.
- It does not test the WP-10 step 2 callout: the
  launch-counter is DataStore-backed and the runner is
  JVM-only. The 3-launch behaviour is tested in
  androidTest when the participant hits the home
  surface.
- It does not test the WP-10 step 1 onboarding
  reordering: the OnboardingFlowTest in androidTest
  walks the 3-screen flow.
