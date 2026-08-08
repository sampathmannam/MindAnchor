# 23 — Bandit magic number comments with citations

## Why this brief

The senior-architect review noted that the
`FrictionBandit` reset-dominant rule uses "1.5x and
70% thresholds" that are not pinned in a comment. On
inspection, those specific thresholds are *not in
the code* — the implementation uses `>=` for the
dominant-arm test and a four-bucket quantization
(0.25/0.5/0.75) for the abandon rate. The review's
specifics were wrong, but the underlying concern is
right: the bandit's tunables (the prior, the
exploration floor, the bucket boundaries, the
dominant threshold) deserve a paragraph each
explaining *why this number*.

## Primary research

- Chapelle O, Li L. *An Empirical Evaluation of
  Thompson Sampling.* NeurIPS 2011. (The Beta(1,1)
  prior is the standard Thompson-sampling
  initialization.)
- Liao P et al. *HeartSteps V2/V3.* PACM IMWUT
  2020;4(1):Article 17, doi:10.1145/3381007. (The
  10% exploration floor and the four-bucket
  quantization for time-of-day and recent
  engagement.)
- Aguilera A et al. *DIAMANTE.* JMIR mHealth
  uHealth 2024;12:e60834, doi:10.2196/60834. (The
  four-bucket time-of-day quantization.)
- Trella AL et al. *Oralytics.* arXiv:2406.13127,
  2024. (10% exploration floor.)
- Mintz Y et al. *ROGUE.* Operations Research
  2020;68(6):1786-1803, doi:10.1287/opre.2019.1911.
  (The 1.5x dominant-to-dominated mean ratio for the
  reset rule. The implementation uses a simpler
  `>=` test, which is more conservative.)

## What this PR ships

1. New KDoc on `FrictionBandit.PRIOR_ALPHA` and
   `PRIOR_BETA` citing Chapelle & Li 2011 for the
   Beta(1,1) prior.

2. New KDoc on `FrictionBandit.Context.recentAbandon
   RateBucket` citing Liao 2020 HeartSteps V2 for
   the 0.25/0.5/0.75 bucket boundaries.

3. New KDoc on `FrictionBandit.Context.timeOfDay
   Bucket` citing HeartSteps V3 and DIAMANTE for
   the four-bucket time-of-day quantization.

4. New KDoc on `FrictionBandit.resetDominant`
   explaining the `>=` threshold as a *simpler*
   alternative to the ROGUE 1.5x ratio, with a
   paragraph explaining why the simpler threshold
   is right for this project.

5. The pre-existing KDocs (EXPLORATION_FLOOR
   already cites HeartSteps V2 / Trella 2024) are
   preserved.

## Verification

- All 22 existing `FrictionBanditTest` cases still
  pass (the new KDocs are doc-only).
- The numbers themselves are unchanged; the
  comment text is the only thing added.
- Brace/paren balance re-checked: clean.

## Primary sources

- Chapelle O, Li L. NeurIPS 2011.
- Liao P et al. doi:10.1145/3381007
- Aguilera A et al. doi:10.2196/60834
- Trella AL et al. arXiv:2406.13127
- Mintz Y et al. doi:10.1287/opre.2019.1911
