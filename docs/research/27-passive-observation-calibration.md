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
120 days are supplied as point-in-time reference/calibration history and the remaining 120 days are evaluated
chronologically with calibration seed `42`. The personal reference freezes at the first chronological 60-day
prefix meeting the weekday/weekend floors; all 120 effective historical days are scored against that reference for
calibration. Repeating primary-stream generation and evaluation produced byte-for-byte equal observation sequences.

Every synthetic `PassiveDay` has explicit `sourceUpdatedTime` and `ingestedAt`. The engine discards revisions not
known by the observation cutoff, selects one newest eligible final revision per date and segment, and sorts distinct
dates chronologically before baseline or calibration. The calibration result persisted with every scored observation
contains seed `42`, block days `7`, calibration days `30`, simulations `512`, target episodes per 30 days `1.0`, and
refractory days `2`.

The predeclared multi-seed criterion allows the declared four-episode budget plus one finite-sample episode for
each 120-day stream: at most 25 total episodes across all five streams. Every seed's count is reported, including
individual streams above five; no seed is removed based on its result.

Injected copies add `0.5`, `1.0`, `1.5`, or `2.0` times the applicable personal-baseline scale for `1`, `2`,
`3`, or `7` days. Resting heart rate and screen time shift upward; sleep and steps shift downward. The injection
window begins on 2026-05-19 at evaluation offset 18. It is selected by a predeclared control rule: all seven
unshifted days must have zero observation-level crossings and zero domain-level threshold crossings. Delay is
zero-based from the first injected day.

Program 2A rule version `passive-observation-rules-v3` calibrates the second-largest eligible domain magnitude,
so at least two domains must cross the same threshold. Candidate thresholds are traversed downward from the
maximum, which is guaranteed safe under strict crossings. The selected threshold is the last safe candidate
before the first episode-budget violation. Refractory grouping makes episode counts non-monotonic: dense
crossings at a low threshold can merge into one episode and create a disconnected lower “safe” island. Those
islands are rejected rather than interpreted as low observation burden.

Daily personal baselines use weekday/weekend strata only. Wake-relative processing remains a Program 2B contract
for 15-minute quality and source-alignment windows; this daily simulation makes no wake-relative aggregate claim.
After pooling, a feature needs at least 14 eligible values or remains absent.

The frozen reference is never silently replaced. A separately persisted trailing candidate uses the latest 14
eligible distinct days. `BASELINE_SHIFT_CANDIDATE` requires candidate/reference centre disagreement of at least
`1.0` frozen-reference scale in two domains for seven consecutive eligible observations. It records persistent
baseline disagreement, not improvement or deterioration. After a deviation, the first eligible in-range day is
`RANGE_RETURN_PENDING`; the second is `WITHIN_PERSON_RANGE`; ineligible days do not count or break that sequence.

## Deterministic results

| Generator seed | Unshifted episodes over 120 days |
|---:|---:|
| 1 | 2 |
| 7 | 6 |
| 42 | 4 |
| 2026 | 3 |
| 20260830 | 5 |
| **Aggregate** | **20** |

The aggregate result is 20 episodes against the predeclared limit of 25. Seed `7` produced 6 episodes and is
reported unchanged rather than replaced with a passing stream.

| Shift (baseline-scale units) | 1 day | 2 days | 3 days | 7 days |
|---|---:|---:|---:|---:|
| 0.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.0 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 2.0 | 0 / none | 0 / none | 0 / none | 3 / 3 |

Each cell is `crossing days / first-crossing delay`. The required seven-day, 2.0-scale four-domain case produced
three crossing days, first crossing on the fourth injected day (zero-based delay `3`). A seven-day, 2.0-scale resting-heart-rate-only copy
kept sleep, activity, and routine available and unchanged; it produced **0 crossings**. A corresponding copy
shifting exactly resting heart rate and sleep produced **2 crossings**, demonstrating that two corroborating
domains can produce an observation while one shifted domain cannot in the zero-domain-crossing control window.

The JVM test pins these values as one structured `SimulationMetrics` expectation: all five seed counts, aggregate,
injection date and offset, both unshifted control counts, one/two-domain counts, and all 16 shift cells must match
this table. The printed line is diagnostic output only.

## Provenance correction

Program 2A no longer registers `passive-daily-features`; raw-to-daily and 15-minute aggregation is not performed
until Program 2B. The registry now names the transformations this build performs:
`passive-personal-baseline@personal-baseline-v2`,
`passive-block-calibration@block-calibration-v2`, and
`passive-observation-explanation@observation-explanation-v1`. The deterministic transformation-set hash changed
from `441e76b3167fd7b96c8a493111bb0916c3805fa2ac008b0b3ce604a001c27316` to
`160a63549fcb1c515daf8083532bbd98aa57c96e2913b66b7b557e5580337aa6`.

Because the current research-export fixture carries the registry as content, its frozen content hash changed from
`cc8199225d02400487f5ae11275bc3d78e0fe578951678e7dbc56bae6c0bff25` to
`edfbaee8dc3c317201a3e0bf0f006688a8821a90a3515ad3394fd86588926a77`. This re-pin is solely the registry-content
delta; the export projection, document version, and encoder did not change.

The acceptance run also confirmed that no ineligible day emitted a deviation and that no threshold or baseline
observation was available before day 61.

## Interpretation boundary

“Synthetic shifts test detector mechanics. They do not estimate sensitivity or specificity for anxiety,
depression, anger, BPD, or any other condition.”

These values are deterministic outputs of this synthetic generator and this engine version. They are not clinical
performance estimates, population claims, or evidence that any listed shift corresponds to a mental-health state.
