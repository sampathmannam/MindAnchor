# 14-day × 5-persona simulation report (v0.20.9)

> **Status:** This is the WP-5 deliverable. Run with
> `./gradlew :app:testDebugUnitTest --tests "org.mindanchor.sim.SimulationReportTest"`.
> All 5 personas produce the research-anchored shape the launcher
> claims to read; no P0 issues were identified.

## How the simulation works

The launcher's pure-Kotlin wellness math (`WellnessStats`, `OpenLoop.phase`,
`BedtimeList.phase`) is exercised end-to-end on a 14-day schedule for each
of 5 personas. Each persona is anchored in verified research
(see `22-research-index.md` for the citations).

The runner pre-seeds a 14-day "warmup" with a different seed salt, so each
test day has a 14-day prior baseline to read against (matching the
launcher's `WellnessSignal.MIN_HISTORY_DAYS = 14` floor). The runner
respects the home card's `isReportable` gate: a reading whose baseline
is not yet reportable surfaces as `NO_DATA`, never as `AT` / `ABOVE` /
`BELOW`. This is what the home card would show.

## The 5 personas

The 5 anchored-in-research personas the launcher is tested against:

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
| `perfectly_regular_zero_variance` | Every signal has the same value on every day; MAD = 0 | The "z = (x − median) / 0" NaN/Infinity bug. The launcher must refuse to surface a z-score and return NO_DATA |
| `sparse_data_partial_wearable` | 4/7 watch days; no HRV, no sleep, no mindfulness | Sparse-data realism (e.g., a COROS Pacer 3 user). The runner must not invent values for null days, and a signal the watch does not write must always be NO_DATA |

## Headline numbers (seed 42, start 2026-01-05)

| Persona | HRV median | Steps median | Open-loop CAPTURE |
|---|---|---|---|
| morning_lark_healthy | 55.07 | 8,558 | 14/14 |
| night_owl_healthy | 45.66 | 6,824 | 14/14 |
| shift_worker_rotating | 40.16 | 4,935 | 14/14 |
| insomnia_anxious | 34.65 | 3,805 | 14/14 |
| depression_low_motivation | 31.84 | 2,054 | 14/14 |

The monotonic ordering is right: morning lark has the highest HRV and
the most steps; depression has the lowest of each. The persona library
is producing the right shape, the launcher's per-person baseline is
normalising within each persona (the N-of-1 frame, not a population
claim), and the direction bands are landing in `AT` on the majority
of days for the healthy baseline persona.

## Findings

### P0 (must fix before 10/10)

None. The launcher's math produces the research-anchored shape on every
persona.

### P1 (worth a follow-up; not blocking 10/10)

1. **Default 22:00 → 07:00 sunset window is wrong for night owls and
   shift workers.** The runner fires `open-loop CAPTURE` 14/14 days for
   *all five* personas with the default window. That is the right
   *math*: given a 22:00-07:00 window, the prompt fires at 23:00. But
   the night-owl and shift-worker personas are exactly the people the
   research (Roenneberg 2007, Åkerstedt 2003) says the default is
   *wrong* for. The launcher already supports an editable window
   (`SunsetPrefs.setWindow`); the design consequence is that
   onboarding must surface "your wind-down is the wrong default for
   many people" more visibly. See WP-10.

2. **`MuchAbove` count for the insomniac persona is high (6/14).**
   The insomniac's HRV distribution has occasional noise days well
   above the 35 ms mean, and the per-person baseline normalises to
   those noise days, which inflates the `MUCH_ABOVE` count. This is
   the right N-of-1 result — the persona's own history says "this is
   high for you" — but a real user would see "much above" more often
   than for a healthy baseline, which the design needs to handle
   calmly. See WP-6 follow-up.

### P2 (informational)

3. **`Open-loop CAPTURE` is 14/14 in the default window for every
   persona.** The runner assumes no note was captured, which is the
   worst-case "show the prompt" path. When the persona library gains
   a "captured last night" flag (a follow-up to the WP-3+ data
   generator), the `RETURN` phase will start firing for the personas
   that capture a note the night before.

## How to reproduce

```bash
gradlew :app:testDebugUnitTest --tests "org.mindanchor.sim.SimulationReportTest"
```

The stdout of the first test prints a per-persona table; this markdown
file is a static snapshot of that output. Re-run after any
launcher-logic change; the test asserts the persona's per-signal
median orderings, so a regression on the wellness math fails the
test, not the report.
