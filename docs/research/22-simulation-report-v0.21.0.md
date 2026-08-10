# 14-day × 8-persona simulation report (v0.21.0)

> **Status:** This is the WP-5 deliverable for the 10/10 candidate
> v0.21.0. Run with
> `./gradlew :app:testDebugUnitTest --tests "org.mindanchor.sim.SimulationReportTest"`.
> All 8 personas produce the research-anchored shape the launcher
> claims to read; no P0 issues were identified.

## How the simulation works

The launcher's pure-Kotlin wellness math (`WellnessStats`, `OpenLoop.phase`,
`BedtimeList.phase`) is exercised end-to-end on a 14-day schedule for each
of 8 personas. Each persona is anchored in verified research
(see `22-research-index.md` for the citations).

The runner pre-seeds a 14-day "warmup" with a different seed salt, so each
test day has a 14-day prior baseline to read against (matching the
launcher's `WellnessSignal.MIN_HISTORY_DAYS = 14` floor). The runner
respects the home card's `isReportable` gate: a reading whose baseline
is not yet reportable surfaces as `NO_DATA`, never as `AT` / `ABOVE` /
`BELOW`. This is what the home card would show.

## The 5 research-anchored personas

| Persona | Anchored in | Key shape |
|---|---|---|
| Morning lark | Roenneberg 2007; Windred 2024 | HRV mean ~55, steps mean ~8,500 |
| Night owl | Roenneberg 2007; Wittmann 2006 | HRV mean ~46, sleep onset 01:00-01:30 |
| Shift worker | Åkerstedt 2003; Kecklund 2016 | HRV mean ~40, irregular sleep |
| Insomniac | Harvey 2002; Baglioni 2016 | HRV mean ~35, sleep ~5h |
| Depression | Dimidjian 2006; Brosschot 2006 | HRV mean ~32, steps ~2,000 |

## The 3 adversarial personas

Added in WP-6 to exercise edge cases the anchored personas never reach:

| Persona | Why it exists | What it catches |
|---|---|---|
| `noisy_signal_high_variance` | 4x normal noise; the personal MAD should absorb it | A regression where the band cut-offs (1.0, 2.0) are too tight and over-fire ABOVE/MUCH_ABOVE |
| `perfectly_regular_zero_variance` | MAD = 0 for every signal; division by zero would crash | The `coerceAtLeast(1e-6)` floor in `WellnessStats` |
| `sparse_data_partial_wearable` | 4/7 watch days are present, 3/7 are null | The "not reportable" gate in `WellnessStats.baseline.isReportable` |

## v0.21.0 additions

### Chronotype (WP-8)

Each persona has an editorial chronotype assignment; the mapping is
pinned in `PersonaChronotypeTest`. The runner does not yet drive
`SunsetPrefs.setChronotype` end-to-end (the DataStore interaction
needs Robolectric, deferred to a follow-up), but the
`Chronotype.defaultWindow` is exercised directly for each persona:

| Persona | Chronotype | Default window |
|---|---|---|
| Morning lark | MORNING_LARK | 21:00 → 06:00 |
| Night owl | NIGHT_OWL | 00:00 → 08:00 |
| Shift worker | SHIFT_WORKER | 09:00 → 17:00 |
| Insomniac | NIGHT_OWL | 00:00 → 08:00 |
| Depression | NEUTRAL | 22:00 → 07:00 |
| Noisy signal | NEUTRAL | 22:00 → 07:00 |
| Perfectly regular | NEUTRAL | 22:00 → 07:00 |
| Sparse data | UNKNOWN | (treated as NEUTRAL at runtime) |

### Sleep window optimizer (WP-8)

`SleepWindowOptimizer.suggest` is exercised in pure unit tests
(`SleepWindowOptimizerTest`), 9 cases including the midnight-crosser
and the day-sleeper. The runner cannot drive it end-to-end because
the personas do not produce sleep onset times, only sleep
durations — extending the persona library to emit onset times is
a follow-up.

### Expressive writing (WP-8, Pennebaker 1997)

The check-in reflection surface is the launcher's expressive-writing
feature. `CheckIn` has a 1000-character `reflection: String` field,
the test pins that an empty reflection is a legal check-in (not a
missing one) and that the reflection is stored verbatim, never
summarised or mood-tagged. The check-in never derives a mood field
from the reflection; the no-mood-inference rule is enforced by the
absence of a field.

## Findings (this version)

P0: none.

P1:
- The 22:00 → 07:00 default window is wrong for night owl and
  shift worker personas. The chronotype feature (WP-8) is the
  fix; the default window remains as a placeholder until the
  user has answered the onboarding question or set the
  window by hand.
- The simulation runner does not yet exercise
  `SleepWindowOptimizer` end-to-end on persona data because
  the personas emit sleep durations but not sleep onsets.
  Follow-up: add `sleepOnsetMinutes: Int?` to `DailyVitals` and
  the persona library, then wire the runner.

P2:
- Documentation: the chronotype-persona mapping is editorial
  and lives in code, not docs. A future refactor should pull
  the mapping into a single source-of-truth (an
  `EditorialMapping` object or a YAML file the docs can
  reference).
