# 27 — Passive observation calibration simulation

**Date:** 2026-08-30
**Scope:** Program 2A detector mechanics only
**Status:** Deterministic JVM acceptance evidence

## Method

The simulation generates 240 consecutive final daily records beginning on 2026-01-01. Resting heart rate,
sleep minutes, steps, and screen minutes each have an independent residual generated with `java.util.Random`
seed `42` and the equation:

```text
x[t] = 0.65 * x[t-1] + seededGaussianNoise
```

Each residual is transformed into that feature's declared unit around a fixed centre. The first 120 days are
the frozen reference/calibration history. The remaining 120 days are evaluated chronologically with calibration
seed `42`. Repeating generation and evaluation with the same seeds produced byte-for-byte equal observation
sequences.

Injected copies add `0.5`, `1.0`, `1.5`, or `2.0` times the applicable personal-baseline scale for `1`, `2`,
`3`, or `7` days. Resting heart rate and screen time shift upward; sleep and steps shift downward. The injection
window begins on 2026-05-01, the first unshifted evaluation day; the corresponding seven-day unshifted window
has zero crossings. Delay is zero-based from the first injected day, so delay `1` means the second injected day.

## Deterministic results

The unshifted 120-day evaluation produced **1 observation episode**. The declared budget is 4 episodes over
120 valid days, with one additional finite-sample episode allowed by this acceptance test, so the observed count
is within the limit of 5.

| Shift (baseline-scale units) | 1 day | 2 days | 3 days | 7 days |
|---|---:|---:|---:|---:|
| 0.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.0 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.5 | 0 / none | 0 / none | 0 / none | 1 / 3 |
| 2.0 | 0 / none | 1 / 1 | 2 / 1 | 4 / 1 |

Each cell is `crossing days / first-crossing delay`. The required seven-day, 2.0-scale case produced four
crossing days, first crossing on the second injected day. A seven-day, 2.0-scale resting-heart-rate-only copy
with no second eligible domain produced **0 crossings** and therefore no deviation observation.

The acceptance run also confirmed that no ineligible day emitted a deviation and that no threshold or baseline
observation was available before day 61.

## Interpretation boundary

“Synthetic shifts test detector mechanics. They do not estimate sensitivity or specificity for anxiety,
depression, anger, BPD, or any other condition.”

These values are deterministic outputs of this synthetic generator and this engine version. They are not clinical
performance estimates, population claims, or evidence that any listed shift corresponds to a mental-health state.
