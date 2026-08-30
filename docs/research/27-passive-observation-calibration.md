# 27 — Passive observation calibration simulation

**Date:** 2026-08-30
**Scope:** Program 2A detector mechanics only
**Status:** Deterministic JVM acceptance evidence

## Method

For each predeclared generator seed `1`, `7`, `42`, `2026`, and `20260830`, the simulation generates 240
consecutive final daily records beginning on 2026-01-01. Seed `20260830` is retained as the primary injected-shift
evidence stream; it was not replaced after observing its result. Resting heart rate, sleep minutes, steps, and
screen minutes each have an independent residual generated with `java.util.Random` and the equation:

```text
x[t] = 0.65 * x[t-1] + seededGaussianNoise
```

Each residual is transformed into that feature's declared unit around a fixed centre. For every seed, the first
120 days are the frozen reference/calibration history and the remaining 120 days are evaluated chronologically
with calibration seed `42`. Repeating primary-stream generation and evaluation produced byte-for-byte equal
observation sequences.

The predeclared multi-seed criterion allows the declared four-episode budget plus one finite-sample episode for
each 120-day stream: at most 25 total episodes across all five streams. Every seed's count is reported, including
individual streams above five; no seed is removed based on its result.

Injected copies add `0.5`, `1.0`, `1.5`, or `2.0` times the applicable personal-baseline scale for `1`, `2`,
`3`, or `7` days. Resting heart rate and screen time shift upward; sleep and steps shift downward. The injection
window begins on 2026-05-19 at evaluation offset 18. It is selected by a predeclared control rule: all seven
unshifted days must have zero observation-level crossings and zero domain-level threshold crossings. Delay is
zero-based from the first injected day.

Program 2A rule version `passive-observation-rules-v2` calibrates the second-largest eligible domain magnitude,
so at least two domains must cross the same threshold. Candidate thresholds are traversed downward from the
maximum, which is guaranteed safe under strict crossings. The selected threshold is the last safe candidate
before the first episode-budget violation. Refractory grouping makes episode counts non-monotonic: dense
crossings at a low threshold can merge into one episode and create a disconnected lower “safe” island. Those
islands are rejected rather than interpreted as low observation burden.

## Deterministic results

| Generator seed | Unshifted episodes over 120 days |
|---:|---:|
| 1 | 1 |
| 7 | 6 |
| 42 | 5 |
| 2026 | 4 |
| 20260830 | 3 |
| **Aggregate** | **19** |

The aggregate result is 19 episodes against the predeclared limit of 25. Seed `7` produced 6 episodes and is
reported unchanged rather than replaced with a passing stream.

| Shift (baseline-scale units) | 1 day | 2 days | 3 days | 7 days |
|---|---:|---:|---:|---:|
| 0.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.0 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 2.0 | 1 / 0 | 1 / 0 | 1 / 0 | 4 / 0 |

Each cell is `crossing days / first-crossing delay`. The required seven-day, 2.0-scale four-domain case produced
four crossing days, first crossing on the first injected day. A seven-day, 2.0-scale resting-heart-rate-only copy
kept sleep, activity, and routine available and unchanged; it produced **0 crossings**. A corresponding copy
shifting exactly resting heart rate and sleep produced **2 crossings**, demonstrating that two corroborating
domains can produce an observation while one shifted domain cannot in the zero-domain-crossing control window.

The acceptance run also confirmed that no ineligible day emitted a deviation and that no threshold or baseline
observation was available before day 61.

## Interpretation boundary

“Synthetic shifts test detector mechanics. They do not estimate sensitivity or specificity for anxiety,
depression, anger, BPD, or any other condition.”

These values are deterministic outputs of this synthetic generator and this engine version. They are not clinical
performance estimates, population claims, or evidence that any listed shift corresponds to a mental-health state.
