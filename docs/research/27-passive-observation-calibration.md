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
chronologically with calibration seed `42`. The engine checks revision-ingestion cutoffs in chronological order and
freezes at the first cutoff whose canonical history has a chronological 60-day prefix meeting the weekday/weekend
floors. Every historical day is scored against its own weekday/weekend stratum built from that same frozen cutoff
and prefix. Repeating primary-stream generation and evaluation produced byte-for-byte equal observation sequences.

Every synthetic `PassiveDay` has explicit `sourceUpdatedTime` and `ingestedAt`. The engine discards revisions not
known by the observation cutoff, selects one newest eligible final revision per date and segment, and sorts distinct
dates chronologically before baseline or calibration. The frozen cutoff and through-day are persisted with the
observation. A correction visible before first eligibility belongs to the reference; a later revision inside the
frozen prefix cannot alter its centres or scales. The calibration result persisted with every scored observation
contains seed `42`, block days `7`, calibration days `30`, simulations `512`, target episodes per 30 days `1.0`, and
refractory days `2`.

The predeclared multi-seed criterion allows the declared four-episode budget plus one finite-sample episode for
each 120-day stream: at most 25 total episodes across all five streams. Every seed's count is reported, including
individual streams above five; no seed is removed based on its result.

The shift experiment uses the predeclared first evaluation window (offset `0`, beginning 2026-05-01); it is not
selected after inspecting the generated stream. For those seven control days all four available features are set to
their frozen own-stratum centres. Injected copies then add `0.5`, `1.0`, `1.5`, or `2.0` times the applicable
personal-baseline scale for `1`, `2`, `3`, or `7` days. Resting heart rate and screen time shift upward; sleep and
steps shift downward. This constructed control isolates corroboration mechanics while the separate five-seed AR(1)
streams retain the stochastic false-episode check. Delay is zero-based from the first injected day.

Program 2A rule version `passive-observation-rules-v4` calibrates the second-largest eligible domain magnitude,
so at least two domains must cross the same threshold. Candidate thresholds are traversed downward from the
maximum, which is guaranteed safe under strict crossings. The selected threshold is the last safe candidate
before the first episode-budget violation. Refractory grouping makes episode counts non-monotonic: dense
crossings at a low threshold can merge into one episode and create a disconnected lower “safe” island. Those
islands are rejected rather than interpreted as low observation burden.

Daily personal baselines use weekday/weekend strata only. Wake-relative processing remains a Program 2B contract
for 15-minute quality and source-alignment windows; this daily simulation makes no wake-relative aggregate claim.
After pooling, a feature needs at least 14 eligible values or remains absent.

The frozen reference is never silently replaced. A separately persisted trailing candidate uses the latest 56
eligible distinct days (eight complete weeks). For each feature it mirrors the reference's pooling decision: pooled
with pooled, or the same weekday/weekend stratum with at least 14 values. `BASELINE_SHIFT_CANDIDATE` requires
candidate/reference centre disagreement of at least
`1.0` frozen-reference scale in two domains for seven consecutive eligible observations. It records persistent
baseline disagreement, not improvement or deterioration. After a deviation, the first eligible in-range day is
`RANGE_RETURN_PENDING`; the second is `WITHIN_PERSON_RANGE`; ineligible days do not count or break that sequence.

## Deterministic results

| Generator seed | Unshifted episodes over 120 days |
|---:|---:|
| 1 | 5 |
| 7 | 8 |
| 42 | 5 |
| 2026 | 3 |
| 20260830 | 4 |
| **Aggregate** | **25** |

The aggregate result is 25 episodes against the predeclared limit of 25. Seed `7` produced 8 episodes and is
reported unchanged rather than replaced with a passing stream.

| Shift (baseline-scale units) | 1 day | 2 days | 3 days | 7 days |
|---|---:|---:|---:|---:|
| 0.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.0 | 0 / none | 0 / none | 0 / none | 0 / none |
| 1.5 | 0 / none | 0 / none | 0 / none | 0 / none |
| 2.0 | 1 / 0 | 2 / 0 | 3 / 0 | 7 / 0 |

Each cell is `crossing days / first-crossing delay`. The seven-day, 2.0-scale four-domain case produced seven
crossing days beginning on the first injected day. The corresponding unshifted control produced **0 observation
crossings** and **0 domain crossings**. A seven-day, 2.0-scale resting-heart-rate-only copy kept sleep, activity,
and routine available at their own-stratum centres and produced **0 crossings**. Shifting exactly resting heart rate
and sleep produced **7 crossings**, demonstrating the declared one-domain/two-domain mechanics.

The JVM test pins these values as one structured `SimulationMetrics` expectation: all five seed counts, aggregate,
injection date and offset, both unshifted control counts, one/two-domain counts, and all 16 shift cells must match
this table. The printed line is diagnostic output only.

## Provenance correction

Program 2A no longer registers `passive-daily-features`; raw-to-daily and 15-minute aggregation is not performed
until Program 2B. The registry now names the transformations this build performs:
`passive-personal-baseline@personal-baseline-v3`,
`passive-block-calibration@block-calibration-v3`, and
`passive-observation-explanation@observation-explanation-v1`. The deterministic transformation-set hash changed
from `160a63549fcb1c515daf8083532bbd98aa57c96e2913b66b7b557e5580337aa6` to
`e36fe716c37f318166ccb8d764af56c546a6aa8b57df6dab34be48b4447d9fea`.

The rule version advanced to `passive-observation-rules-v4` and the model version to
`personal-robust-baseline-v3`. Because the current research-export fixture carries this provenance as content, its
frozen content hash changed from `edfbaee8dc3c317201a3e0bf0f006688a8821a90a3515ad3394fd86588926a77` to
`2d1e1fe37f3793a7179732e58752d520c742d1c5bd9e7d31ac8919949cce59d7`. This re-pin reflects only Program 2A
provenance content (the rule/model versions and the registered transformation versions/descriptions); the export
projection, document version, and encoder did not change.

The acceptance run also confirmed that no ineligible day emitted a deviation and that no threshold or baseline
observation was available before day 61.

## Interpretation boundary

“Synthetic shifts test detector mechanics. They do not estimate sensitivity or specificity for anxiety,
depression, anger, BPD, or any other condition.”

These values are deterministic outputs of this synthetic generator and this engine version. They are not clinical
performance estimates, population claims, or evidence that any listed shift corresponds to a mental-health state.
