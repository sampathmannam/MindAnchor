# Program 2 Passive Intelligence Design

**Date:** 2026-08-30
**Status:** Approved
**Audience:** MindAnchor engineering and future research reviewers

## 1. Decision

Program 2 is an observation system, not a mental-health classifier. It records what data was available, decides whether that data is usable, compares eligible days with the person's own prior history, and preserves non-diagnostic observations for later validation. It does not trigger protocols, modify launcher restrictions, diagnose a condition, or feed any AnchorCore intervention hook.

The production output names observable departures only:

- `WITHIN_PERSON_RANGE`
- `RANGE_RETURN_PENDING`
- `TRANSIENT_DEVIATION`
- `SUSTAINED_DEVIATION`
- `BASELINE_SHIFT_CANDIDATE`
- `NO_OBSERVATION`

The accompanying data state is always explicit:

- `AVAILABLE_FINAL`
- `AVAILABLE_PROVISIONAL`
- `INSUFFICIENT_DATA`
- `SUPPRESSED_EXERCISE`
- `BASELINE_BUILDING`

MindAnchor may say, “Sleep and physiology were different from your usual range.” It may not say, “You were anxious,” “You were depressed,” “You had a BPD episode,” or any equivalent diagnosis or prediction.

## 2. Evidence correction

Public wearable datasets are useful for sensor processing, motion-artifact handling, exercise suppression, synchronization tests, and offline simulations. They are not production ground truth for this person's anxiety, depression, anger, or BPD. Population correlations and laboratory stress labels do not transfer directly to an individual using different devices in daily life.

The operational design therefore has three layers:

1. **Sensor integrity:** provenance, coverage, freshness, backfill, exercise overlap, and device changes.
2. **Personal deviation:** transparent robust statistics calibrated on the person's own eligible history.
3. **Personal association:** later comparison with temporally separated personal outcome records. This is research-only until prospective validation supports it.

## 3. Data contract

### 3.1 Raw and derived retention

- Health Connect is the primary Android ingestion boundary. COROS is optional and contributes only through a supported, authorized source.
- Raw high-frequency samples remain local for 14 days.
- Versioned 15-minute feature windows remain for at least one year and are included in encrypted continuity backup.
- Daily features, observation decisions, source provenance, model/rule versions, and research-ledger records are retained long term.
- No absent value is zero-filled, carried forward, interpolated, or permitted to trigger an observation.

### 3.2 Time semantics

Every stored input or decision records the time needed to reconstruct what was known:

- `eventTime`: when the underlying measurement occurred.
- `sourceUpdatedTime`: when its source last changed the record, when available.
- `ingestedAt`: when MindAnchor read it.
- `asOfTime`: the cutoff used for a particular observation.

Later Health Connect backfills may create a new finalized record or decision. They never overwrite the original point-in-time decision.

Every daily feature revision therefore carries required `sourceUpdatedTime` and `ingestedAt` values; there are no
implicit timing defaults. For a decision at `asOfTime`, the engine discards revisions ingested after that cutoff,
keeps the newest eligible final revision for each `LocalDate` within the matching baseline segment (ordered by
`sourceUpdatedTime`, then `ingestedAt`), and sorts the resulting distinct days chronologically. Duplicate revisions
cannot increase a sample floor or reorder a resampling block.

### 3.3 Features

The initial feature set is deliberately small:

- Physiology: resting heart rate and HRV RMSSD when available.
- Sleep: minutes and sleep-onset timing.
- Activity: steps and active/exercise minutes.
- Routine: first unlock and screen minutes.
- SpO2: quality/confounder context only; it is not an activation score.

Each feature names its source, unit, transformation version, coverage, and eligibility. Fifteen-minute features exist to assess quality, align sources, suppress physical activity, and aggregate daily values. They do not carry a mental-state label.

## 4. Eligibility and finalization

An ingestion adapter determines whether a window or day is final from observed source latency. Source-specific watermarks use recorded p99 ingestion delay once sufficient history exists. Before then, the implementation uses a conservative documented cutoff.

The estimator may emit a deviation only from `AVAILABLE_FINAL` data. `AVAILABLE_PROVISIONAL`, `INSUFFICIENT_DATA`, `SUPPRESSED_EXERCISE`, and `BASELINE_BUILDING` all produce `NO_OBSERVATION`.

Exercise suppression is applied to overlapping physiology windows. A daily record is not discarded merely because some exercise occurred; it retains unaffected sleep, routine, and activity evidence. A domain with inadequate eligible coverage is absent rather than repaired.

Device or source-version changes start a new baseline segment. Historical segments remain unchanged.

## 5. Personal baseline

The first observation baseline requires at least 60 distinct valid, final, non-exercise days, including at least eight weekdays and eight weekend days. The first chronological prefix meeting those floors becomes the frozen reference. A daily feature is compared with the appropriate weekday/weekend stratum when that stratum has at least 14 observations; otherwise weekday and weekend values are pooled and that pooling is recorded. After pooling, a feature still requires at least 14 eligible values: 0–13 values leave that feature absent, so it cannot score or corroborate.

Daily aggregates do not claim a wake-relative baseline. Wake-relative semantics apply only to the 15-minute
quality and source-alignment windows implemented by Program 2B; Program 2A daily personal baselines use only the
declared weekday/weekend strata.

For each feature, the center is the median. Dispersion is scaled MAD (`1.4826 * MAD`). When MAD is zero, the implementation may use a predeclared IQR scale (`IQR / 1.349`); if that is also zero, that feature has no score. No arbitrary epsilon is inserted.

Features are grouped into physiology, sleep, activity, and routine domains. A domain score is computed only from eligible constituent features and retains the signed feature evidence. At least two eligible domains are required. The observation detector uses the second-largest eligible domain magnitude, so a crossing requires corroboration from two domains while the explanation can name every domain that crossed its calibrated boundary.

## 6. Threshold calibration and episodes

There is no universal “anxiety threshold.” The initial detector calibrates its observation boundary from contiguous blocks of the person's own historical corroborated day scores. Seven-day circular block resampling preserves weekly rhythm and short-term autocorrelation. Candidate thresholds are traversed downward from the maximum, which is guaranteed safe under strict crossings, and calibration selects the last budget-compliant candidate before the first violation. Refractory grouping makes episode count non-monotonic, so disconnected lower-threshold safe islands created by dense crossings are rejected rather than mistaken for low burden. The declared engineering budget is no more than one observation episode per 30 valid days in the calibration sample; it is not a clinical constant.

A first crossing is `TRANSIENT_DEVIATION`. Two crossings among three eligible days are `SUSTAINED_DEVIATION`. Adjacent crossings within 48 hours belong to the same episode. After a deviation, the first eligible in-range day is `RANGE_RETURN_PENDING`; the second consecutive eligible in-range day returns to `WITHIN_PERSON_RANGE`. Ineligible days are `NO_OBSERVATION`: they neither count toward nor break this eligible-day sequence.

A frozen reference baseline and a trailing candidate baseline are maintained separately. The candidate uses the
latest 14 point-in-time eligible distinct days. For every feature shared by both baselines, candidate/reference
centre disagreement is standardized by the frozen reference scale. At least two domains must each reach `1.0`
frozen-scale unit, and that corroborated disagreement must persist for seven consecutive eligible observations,
before `BASELINE_SHIFT_CANDIDATE` is emitted. Ineligible days do not count. The candidate never silently replaces
the frozen reference, and the state records disagreement only—not improvement or deterioration.

## 7. Staged models

- **At 60 valid days:** robust personal baseline and calibrated daily detector may become active.
- **At 90–120 valid days:** one EWMA over eligible daily residuals runs in shadow mode. It cannot affect user-visible state.
- **At 180 valid days:** state-space, HMM, Isolation Forest, one-class SVM, or conformal experiments may run offline or in shadow mode only.
- **After at least six months and 50 temporally separated personal labels:** a simple penalized personal association model may be evaluated chronologically. It remains research-only until held-out prospective performance is credible.

More sensor rows do not substitute for independent personal episodes or labels.

## 8. Persistence and provenance

Derived feature records may be revised by explicit append-only supersession. Observation decisions are append-only. Each decision records:

- decision and data status;
- `asOfTime` and whether it was provisional or final;
- baseline segment and sample counts;
- domain and feature evidence;
- calibrated threshold and algorithm versions;
- calibration seed and complete block/calibration/simulation/budget/refractory configuration;
- trailing-candidate sample count, standardized disagreement threshold, corroborating-domain floor, persistence
  days, and domain evidence;
- study phase and source device;
- explanations and exclusions.

Program 2 replaces Program 1's `rule-set-none-v1` and `model-set-none-v1` provenance values. Program 2A registers
the transformations it actually performs: personal baseline, block calibration, and fixed observation explanation.
The raw-to-daily and 15-minute feature-window transformation is registered only when Program 2B performs it. The
first Program 2 write therefore opens a new study phase automatically.

Feature records and decisions participate in encrypted continuity snapshots, replacement-phone restore, canonical research export, and the frozen data dictionary. Snapshot and research-export versions change additively in the same commit as their payload shapes.

## 9. User experience

The passive-intelligence surface shows:

- current data status and last finalized day;
- which sources contributed and which were missing;
- the observation state using non-clinical language;
- the specific domains and features that differed;
- baseline-building progress;
- a direct statement that the result is not a diagnosis;
- a history of frozen observations and later backfills.

No notification, launcher restriction, protocol suggestion, or intervention is introduced in Program 2. Patterns remain descriptive and user-verifiable.

## 10. Validation

All evaluation is chronological. Predictions are frozen before later data or labels are read. Random cross-validation is forbidden for this time series.

Program 2 records and reports:

- eligible and expected windows;
- p50, p95, and p99 ingestion lag;
- longest gap and backfill rate;
- original-to-final state flip rate;
- observation episodes per 30 valid person-days;
- state occupancy and episode duration;
- simulated-shift sensitivity and delay at 0.5, 1.0, 1.5, and 2.0 scaled-MAD units lasting 1, 2, 3, and 7 days;
- stability by weekday/weekend, device segment, and missingness quartile;
- battery and background-work observations from physical-device operation.

Synthetic shifts validate detector mechanics only. They do not establish clinical sensitivity.

## 11. Delivery split

Program 2 is implemented as three independently releasable subprograms:

1. **2A — observation engine:** pure contracts, eligibility, robust baselines, block-calibrated episodes, explanations, and simulation tests.
2. **2B — operational pipeline:** Health Connect windows, freshness/backfill accounting, Room persistence, WorkManager scheduling, continuity backup/restore, and research export.
3. **2C — surfaces and field validation:** passive-intelligence screen, settings/status wiring, runbooks, battery/data-loss logging, and acceptance evidence.

Program 3 cannot begin until Program 2 has accumulated normal-use evidence and the false-observation burden is quantified.

## 12. Acceptance criteria

Program 2 is complete when:

- all non-final, missing, insufficient, and exercise-confounded paths are proven unable to emit a deviation;
- the baseline cannot activate below the declared 60-day and calendar-composition floors;
- calibration is deterministic and meets its episode budget on the supplied history;
- an observation can be reconstructed from point-in-time data and version identifiers;
- replacement-phone restore reproduces the same finalized feature and observation history;
- exports contain raw provenance, missingness, exclusions, decisions, and algorithm versions;
- Program 2 has no path to intervention, app blocking, or AnchorCore adaptation;
- automated tests, migrations, lint/static analysis, and documented physical-device checks pass.
