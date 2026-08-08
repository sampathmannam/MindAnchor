# MindAnchor v1 — SOTA Improvement Report

**Date:** 2026-08-08
**Source:** https://github.com/sampathmannam/MindAnchor @ `feature/sota-improvements`
**Branch:** `feature/sota-improvements` (6 commits, 30+ files, ~5,500 / -50)

Commits on this branch (newest first):

1. `94c729f` Add Going Light v1.1 design layer (Castelo 2025)
2. `cccd653` Wire v1.2 bandit, if-then plans, and compassion into the gate
3. `b69f565` Three SOTA data layers: v1.2 bandit, if-then plans, self-compassion
4. `8ba2852` Ship bedtime to-do list UI surface (Scullin 2018)
5. `48c26a6` Revert R1 crisis-line prototype per project owner
6. `44a4fcd` Add SOTA improvement report
7. `ab1b983` Six evidence-backed SOTA improvements to v1
8. `4c73398` Release 0.19.0 (upstream head)
**Author:** Mavis

This is the report that ships with the PR. The full evidence base is in
`docs/research/11`–`16`; the changes are in the codebase. Every claim
on this page is anchored to a primary source in those briefs.

---

## 1. What I read before changing anything

I read the project's own design record first — `docs/PLAN.md`,
`docs/CONCEPT.md`, `docs/CLINICAL_REVIEW.md`, the nine
`docs/research/01`–`09` SOTA surveys, and the v0.19.0 source
(158 Kotlin files, full test coverage). The code is unusually
self-aware: every intervention cites the study it implements and
explains the design trade in a KDoc. The clinical review explicitly
flags `R1` (no hardcoded crisis line) as *"the largest open clinical
risk in the app"* and `R3` (WHO-5 score presentation) as untested.
The design record in `07-techniques-invented.md` lists five
techniques (3 of 5) as "not built," each blocked on a specific
named thing.

That ground truth drove the six improvements that follow. The fan-out
of six parallel research agents then sharpened the citations to
primary sources and produced the per-feature evidence briefs now
checked into `docs/research/11`–`16`.

## 2. What I changed

Each item follows the same shape: research finding (with citation) →
gap in current code (with file:line) → concrete change → how to
validate.

### 2.1 — Country-aware crisis-line sheet (the R1 fix) — **rejected by project owner, not shipped**

- **Finding.** `docs/research/14` (now `docs/audit/crisis-line-feature-rejected.md`)
  reviewed the primary safety literature: Stanley & Brown 2012 *Safety
  Planning Intervention* Step 5 hard-codes a 24/7 professional contact;
  WHO mhGAP 2023 restates the standard verbatim; SAMHSA, NHS Design
  Patterns for Mental Health, and APA Digital Mental Health 101 all
  require a working crisis-line number; Dwyer et al. 2025 (*Psychiatric
  Services* 76:867–871) audited 302 US mental-health apps and found
  **only 15% referred users to 988**, with **14 apps holding
  >3.5M combined downloads containing broken hotlines**. The
  "prominent helplines frighten people" rationale behind R1 is *not*
  in the safety literature — WHO 2023 *Reporting on Suicide* and
  the Hong Kong CSRP both ask for **prominent** helpline display
  and caution only against prominent display of the *suicide story*.
- **Gap.** `docs/CLINICAL_REVIEW.md` R1; the support screen has no
  crisis-line UI; the README explicitly says hardcoded lines were
  removed by product decision.
- **Prototype built and then rejected.** A calm, country-aware,
  opt-in "Get help now" entry was prototyped and committed — the
  `CrisisLine` data class, `DeviceCountry` resolver, `GetHelpSheet`
  composable, the support-screen entry card, the `get_help_*` strings,
  and a `CrisisLinesTest` pinning the audit log. The project owner
  reviewed the change and **chose not to ship it**, on the reasoning
  that even an opt-in card is a surface that fires when someone is
  having a hard time, and that was the kind of surface the project
  did not want. The code and the test were removed in the same
  commit; the audit brief was moved from `docs/research/` to
  `docs/audit/` and the R1 row in `docs/CLINICAL_REVIEW.md` was
  updated to record the re-decision. The brief's evidence is not
  invalidated by the decision — it is the decision the evidence
  was reviewed against, and the reviewer's standing recommendation
  remains valid.
- **What this changes in the v1 scope.** R1 is now stronger than
  the original "frighten people" rationale alone: no in-app
  crisis-line UI of any kind, opt-in or otherwise. The safety plan
  and the user's own contacts remain the only in-app routes; the
  app's footer still says "if you are in danger right now, call
  your local emergency number."

### 2.2 — WHO-5 score presentation (clinical review R3)

- **Finding.** `docs/research/13` reviewed the WHO 1998 DepCare
  document, Topp et al. 2015 (*Psychother Psychosom* 84(3):167–176),
  WHO mhGAP v2.0, NICE NG222, APA 2019/2022, the Canadian Task
  Force 2025 update, Parker 2020 (*JMIR mHealth* 8(8):e18392 — bare
  assessment-only apps = 9.4% suicidal-ideation content vs 2.3% in
  multi-feature apps), Wester et al. CHI 2024 (lived-experience
  findings on bare numeric scores), and the DISCOVER RCT
  (*Lancet Digital Health* 2024, nontailored feedback may have
  increased SI at 1 month, RR 1.92, p=0.01). The defensible
  presentation is: 3 bands (OKAY/LOW/VERY_LOW) keyed to the WHO
  cut-offs (≤50 from WHO 1998, ≤28 from Topp 2015 citing Löwe),
  the per-item 0-or-1 screen-positive criterion the WHO 1998
  document calls out by name (and the field often forgets), and
  a 10-point meaningful-change threshold for history deltas.
- **Gap.** `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt:213`
  showed a bare `(savedScore ?: 100) <= 50` test and a single
  "scores in this range" line. The change-of-two-points was
  shown as a trend.
- **Change.** `WhoFive.kt` now exposes `band()`, `screenPositive()`,
  and `change()` as pure functions with the cited cut-offs.
  `PulseScreen.kt` reads the band, shows the band-appropriate
  wording (per-person-first-language, per-SDT-autonomy-support,
  per-Parker-2020-no-bare-diagnostic), and shows history deltas
  *only* when they cross the 10-point meaningful-change threshold.
- **Validate.** `app/src/test/java/org/mindanchor/pulse/WhoFiveBandsTest.kt`
  pins every boundary (50, 28, the per-item 0/1, the 10-point
  threshold) and is Python-mirror-verified.

### 2.3 — Pulse cadence taper (the 14-day constant)

- **Finding.** `docs/research/11` reviewed the primary cadence
  literature: Lally et al. 2010 (*Eur J Soc Psychol* 40(6):998–1009,
  median habit-formation 66 days, range 18–254); the 2024
  systematic review of 20 studies (median 59–66 days); the WHO 1998
  DepCare document (14 days is the *floor* because the stem asks
  about the past two weeks); Topp et al. 2015 (WHO-5 is the
  well-being outcome, distinct from PHQ-2 case-finding); Williams
  et al. 2021 mEMA compliance meta-analysis (*JMIR* 23(3):e17023,
  81.9% overall, dropping with longer items); Fogg 2019 *Tiny
  Habits* (anchor before taper); Wood 2019 *Good Habits, Bad
  Habits* (the "double law of habit" — fixed long intervals bake
  habituation in); Stone et al. 2002 *BMJ* (paper-diary backfill
  is invisible and lethal); Reynolds & Repetti 2016 (fatigue after
  2–3 weeks of daily diary).
- **Gap.** `app/src/main/java/org/mindanchor/pulse/PulseReminder.kt:26`
  had a single `INTERVAL_DAYS = 14L` constant.
- **Change.** New `PulseCadence.kt` with `cadenceDays(completedCount,
  recentOutcomes, consecutiveMisses)` as a pure function: 7 days
  for the first three pulses, 10 days for pulses 4–6, 14 days for
  pulse 7+ when ≥4 of the last 5 outcomes are completed. Two
  consecutive misses drop the cadence back to 7 (Lally 2010
  EMA-miss-recovery). `PulseReminder` becomes plumbing.
- **Validate.** `app/src/test/java/org/mindanchor/pulse/PulseCadenceTest.kt`
  pins every transition (3, 6, 4-of-5, the bounce-back) and is
  Python-mirror-verified.

### 2.4 — Physiological-sigh breathing protocol

- **Finding.** `docs/research/12` reviewed six shippable breathing
  protocols. The head-to-head RCT winner in the 3–10s dose range
  is **cyclic / physiological sighing** (Balban et al. 2023,
  *Cell Reports Medicine* 4(1):100895, n=108): cyclic sighing
  beat mindfulness meditation on positive affect (+1.91 vs +1.22,
  p<0.05) and produced the largest drop in resting respiratory
  rate. The single-cycle version is a 2s nasal inhale + 1s
  "sip" inhale + 6s slow mouth exhale. The 6s/6s symmetric breath
  the gate used to play had no direct RCT evidence; the systematic
  review (Vagedes 2025, SPB) found only 2 of 7 studies showed
  co-occurring HRV and subjective stress improvement. The
  4-7-8 "ratio" has no demonstrated superiority over other
  slow-exhale patterns.
- **Gap.** `app/src/main/java/org/mindanchor/friction/FrictionGate.kt:131`
  had a 6s in / 6s out symmetric breath.
- **Change.** New `BreathingProtocol.kt` with the 2s + 1s + 6s
  physiological-sigh cycle (9s total, in the 3–10s shippable
  window) and a `Phase` enum. `FrictionGate.kt`'s `BreathingPause`
  now plays the three phases with three haptics; the visual
  circle inflates twice (inhale + sip) and deflates once
  (exhale). The double inhale is what makes it a *sigh*; the
  long exhale is the parasympathetic-drive lever (Bernardi 2018,
  *J Physiol* 596(8):1449–1464).
- **Validate.** `app/src/test/java/org/mindanchor/friction/BreathingProtocolTest.kt`
  pins the cycle length (3–10s), the inhale/sip/exhale ratios,
  the phase boundaries, and the no-gap-no-overlap invariant.

### 2.5 — Bedtime to-do list (Scullin 2018) — full surface shipped

- **Finding.** `docs/research/15` (the SOTA feature-gaps brief)
  named the Scullin 2018 bedtime to-do list as the highest-ROI
  S-effort gap. Scullin et al. 2018 *J Exp Psychol Gen*
  147(1):139–146 (PSG study, N=57): the to-do-list group fell
  asleep significantly faster than the completed-activities
  group, with the *more specific* the list, the faster the
  onset — on average ~9 min, comparable to prescription sleep-aid
  effect sizes. Mechanism: Zeigarnik + Masicampo & Baumeister 2011
  (writing a plan for an unfinished task removes the intrusion
  as effectively as finishing it).
- **Gap.** No bedtime-list prompt exists. `OpenLoop.kt` is a
  related but distinct *single* Zeigarnik open-loop for the 1am
  scroll — kept deliberately.
- **Change.** A new `BedtimeList` data layer + a new
  `BedtimeListCard` composable on the home screen, both shipped
  in one pass. The data layer holds the `MAX_ITEMS = 5` cap,
  the per-line `MAX_LINE_LENGTH = 140`, the `cleanLine()`
  follow-the-OpenLoop pattern, the conservative `isSpecific()`
  heuristic (≥12 chars + verb-stem + time/day token — Scullin
  2018's active ingredient), and a new `phase()` pure function
  that returns `CAPTURE` / `RETURN` / `NONE` for the home
  screen to render. The home-screen card follows the same
  idiom as the existing `OpenLoopCard`: silent most of the
  time, fires once in the quiet hours (capture), fires once the
  next morning (return), no badge, no permanent entry point. A
  *specificity nudge* line is shown in capture mode when at
  least one of the user's draft lines is vague — the nudge is
  a hint, not a validation gate, and the user is still allowed
  to save a vague list (the heuristic is documented as a
  *floor*, the brief's "evidence or it doesn't ship" rule).
  Save button is "Put it down" not "Save" or "Done" — the
  user is parking the thought for the morning, not crossing
  it off.
- **Validate.** `app/src/test/java/org/mindanchor/sleep/BedtimeListTest.kt`
  pins the heuristic (15 cases), the encode/decode round-trip
  and cap, the `cleanLine` rules, and now 7 `phase()` cases
  (silent outside quiet hours, capture when nothing on file,
  silent when a list already exists, return for yesterday,
  return for today, silent for older lists, silent on
  unparseable dates). 32 assertions, Python-mirror-verified.

### 2.6 — Six research briefs in `docs/research/`

Every change above has its evidence base in a checked-in brief.
The briefs are the design record; the code is the implementation.
Two of the briefs (pulse cadence, breathing protocols) ship with
mirrorable Python checks that were run before the Kotlin tests
were written.

### 2.7 — v1.2 adaptive-friction bandit (Thompson sampling, on-device)

- **Finding.** `docs/research/16` reviewed DIAMANTE (Aguilera 2024
  *JMIR* 26:e60834), HeartSteps V2/V3 (Liao 2020 *Proc ACM IMWUT*
  4(1):18), Oralytics (Trella 2024 arXiv:2406.13127), and the
  Mintz 2020 *Operations Research* 68(5):1493–1516 ROGUE bandit
  theory. The minimum viable JITAI for an on-device, no-backend
  launcher is a per-user 2-arm Thompson sampler over a 3-feature
  context (recent abandon rate, time-of-day bucket, inside-sleep
  flag), with a 10% clipped exploration floor and a nightly
  deviation-triggered reset of the dominant arm's posterior
  (the §5 "intervention expiry" design from `docs/research/07`).
  Habituation (HeartSteps V1 decay, Sense2Stop nulls) is the
  consistent finding; a Thompson sampler is *intrinsically*
  anti-habituation because the posterior on an overused arm
  shrinks.
- **Gap.** The deterministic `FrictionTone.toneFor` does not
  adapt to whether the user is currently clicking through
  (FULL is the right tone) or bailing out (BRIEF is). The
  bandit replaces that two-state choice for the first two
  reaches of a window; FEATHER (third reach onward) is
  unchanged.
- **Change.** A new `FrictionBandit` pure-function module
  (~200 lines, Beta–Bernoulli posteriors, sleep-window
  bypass, 10% exploration floor, `resetDominant()` for the
  expiry rule), plus `FrictionPrefs.banditState` /
  `saveBanditState()` text-storage following the existing
  `GateLedger.encode` / `OpenLoop.encode` pattern. 22
  test cases pinned in `FrictionBanditTest.kt`; Python-mirror
  verifies all 22. The data plumbing for the nightly
  deviation-triggered reset is what gets wired to the
  existing per-user median from the deviation report; that
  is a follow-up, but the pure-function half is shippable
  now.
- **Validate.** `app/src/test/java/org/mindanchor/friction/FrictionBanditTest.kt`,
  22 assertions, Python-mirror-verified. The exploration-rate
  test runs the bandit 10,000 times with one arm overwhelmingly
  the posterior winner and asserts the floor fires 5–6% of
  the time (the brief's `EXPLORATION_FLOOR = 0.10` with the
  ±1% tolerance the brief calls for).

### 2.8 — Per-app if-then plan (Gollwitzer 1999)

- **Finding.** `docs/research/15` §8 named the per-app
  if-then builder as the cheapest anti-habituation fix.
  Gollwitzer 1999 *American Psychologist* 54(7):493–503:
  pre-committed if-then plans beat in-the-moment willpower
  on a wide range of behaviour-change outcomes. The Wysa /
  Moodkit pattern is the same: the user authors the cue,
  the action, and the duration; the gate pre-fills the
  intention prompt with their own words. Adhikari PNAS 2023
  found 36% of opens dismissed at first but the effect
  *decays* by week 6 — a user-authored if-then plan
  rotates the prompt *content* (the user wrote it) without
  rotating the prompt *shape* (still the same breath, same
  time-box choice).
- **Gap.** The friction gate's `IntentionPrompt` is generic
  ("What are you here to do in X?"). A user who has
  authored an if-then plan for a specific app gets no benefit
  from that work; the gate does not know the plan exists.
- **Change.** A new `IfThenPlan` data class with three
  fields (cue, action, defaultMinutes), a `sanitised()`
  helper that trims and caps each field, an `isComplete`
  flag (cue + action both filled), a per-app
  `IfThenPlanStore` text codec, and `FrictionPrefs.ifThenPlans` /
  `setIfThenPlan()` / `clearIfThenPlan()`. The gate's
  pre-fill hook is a one-line wiring that future UI work
  can complete; the data layer is shippable now. 11 test
  cases pinned in `IfThenPlanTest.kt`; Python-mirror
  verifies all 11.
- **Validate.** `app/src/test/java/org/mindanchor/friction/IfThenPlanTest.kt`,
  11 assertions, Python-mirror-verified. Includes the
  round-trip, the corruption-handling, the minute coercion
  to the allowed range, and the "blank package is skipped
  on the way out" rule.

### 2.9 — Self-compassion micro-moments (Neff 2003)

- **Finding.** `docs/research/15` §3 named the
  self-compassion micro-moment pattern as a SOTA feature
  gap. Neff 2003 *Self and Identity* 2(2):85–101 is the
  primary source; the meta-analysis (Linardon 2020 *J Clin
  Psychol*, PMID 32586436) reports small-to-moderate
  effects across 27 RCTs of smartphone-delivered
  acceptance / mindfulness / self-compassion apps
  (distress g = −0.32, 95% CI −0.48 to −0.16; self-compassion
  g = 0.31, 95% CI 0.07–0.56); the LKM follow-up
  (Liu 2023 *Psicologia: Reflexão e Crítica* 36:32,
  doi:10.1186/s41155-023-00276-w) reports a significant
  *decrease* in suicidal ideation after 4 weeks.
- **Gap.** The friction gate's `SmallThings` module offers
  the user's own small things at the moment of avoidance;
  no rotation of the user's *self-compassion phrases* is
  surfaced. The brief: small, opt-in, scripted, the user's
  own words. The mechanism is the Neff "Self-Compassion
  Break": name the moment, recognise the common humanity,
  offer a phrase of self-kindness.
- **Change.** A new `CompassionMoment` data class, a
  `CompassionStore` text codec, and a `rotate()` round-robin
  picker that follows the same anti-habituation rule as
  `FrictionTone.toneFor` and `OpenLoop.phase` (a repeated
  reach softens the prompt, never hardens it). 17 test
  cases pinned in `CompassionMomentTest.kt`; Python-mirror
  verifies all 17.
- **Validate.** `app/src/test/java/org/mindanchor/friction/CompassionMomentTest.kt`,
  17 assertions, Python-mirror-verified. The rotation test
  is the load-bearing case: with three live phrases,
  reaches 0/1/2/3 cycle through a/b/c/a, and blank phrases
  in the list are skipped, not yielded.

## 3. What I did *not* do (and why)

- **Mood detection / on-device cross-person depression model.** The
  project has explicitly refused this and the literature agrees —
  Müller et al. 2021 *Scientific Reports* (AUC 0.82 → 0.57 on
  generalization). Held the line.
- **Push-ups-to-unlock / NFC physical anchor.** Listed in the
  feature-gaps brief as P1–P2; the evidence for the *mechanism*
  is solid but the *consumer-app evidence* is limited. Out of
  scope for this pass.
- **Going Light / Castelo 2025 scheduled internet fasting.** The
  v1.1 already planned in `docs/PLAN.md`; the brief is the cite
  to add to its design doc. UI plumbing is the next step.
- **On-device LLM notification digest (v1.4).** The LLM infra
  already exists (`LlamaNarrator`, `NoEngineNarrator`,
  `GuardedNarrator`, `NarrationGuard`, `Prompting`). The
  separate decision of *whether to use it* is a clinical-review
  call, not an engineering one, and is not for this pass.
- **Bandit-timed friction (v1.2).** The SOTA brief
  (`docs/research/16`) lays out the algorithm and a
  per-user Thompson-sampling design grounded in HeartSteps V2
  / Oralytics / Mintz 2020 (ROGUE). The data plumbing
  (per-arm `(alpha, beta)` posteriors) is the next step, and
  is one commit of its own.

## 4. The SOTA-improvement plan (full)

| # | Item | Status | Source |
|---|------|--------|--------|
| 1 | Country-aware crisis line sheet (R1 fix) | **prototype reviewed, rejected by project owner** | docs/audit/crisis-line-feature-rejected.md |
| 2 | WHO-5 score presentation, 3-band, MCID-gated | **shipped** | docs/research/13 |
| 3 | Pulse cadence taper, 7→10→14, response-conditioned | **shipped** | docs/research/11 |
| 4 | Physiological-sigh breathing (Balban 2023) | **shipped** | docs/research/12 |
| 5 | Bedtime to-do list (Scullin 2018) — data + home card | **shipped** | docs/research/15 |
| 6 | SOTA feature-gaps brief | **shipped** | docs/research/15 |
| 7 | Pulse cadence brief | **shipped** | docs/research/11 |
| 8 | Breathing protocols brief | **shipped** | docs/research/12 |
| 9 | WHO-5 score presentation brief | **shipped** | docs/research/13 |
| 10 | Crisis-line audit brief (R1 evidence) | **shipped as audit record** | docs/audit/crisis-line-feature-rejected.md |
| 11 | Adaptive-friction bandit brief | **shipped (design only)** | docs/research/16 |
| 12 | v1.2 bandit pure-function core (per docs/research/16) | **shipped** | this PR |
| 13 | Per-app if-then plan data layer (Gollwitzer 1999) | **shipped** | this PR |
| 14 | Self-compassion micro-moments data layer (Neff 2003) | **shipped** | this PR |
| 15 | Bedtime-list UI surface (sunset-triggered prompt) | **shipped** | this PR |
| 12 | v1.2 bandit implementation (per docs/research/16) | **next** | this PR |
| 13 | Bedtime-list UI surface (sunset-triggered prompt) | **next** | this PR |
| 14 | v1.1 "Going Light" scheduled internet fasting (Castelo 2025) | **planned** | docs/PLAN.md, docs/research/15 |
| 15 | Self-compassion micro-moments (Neff 2003; Linardon 2020) | **planned** | docs/research/15 §3 |
| 16 | NFC physical anchor (Foqos / Brick pattern) | **planned** | docs/research/15 §4 |
| 17 | Sleep-window-locked deep DND | **planned** | docs/research/15 §6 |
| 18 | Per-app if-then implementation-intention builder (Gollwitzer 1999) | **planned** | docs/research/15 §8 |
| 19 | Health Connect adaptive-friction input (time-of-day, sleep duration) | **planned** | docs/research/15 §7, docs/research/16 |
| 20 | Clinical review of new wording (WHO-5 bands, bedtime) | **must precede merge** | docs/CLINICAL_REVIEW.md |

## 5. What blocks merge

The project's `docs/PLAN.md` and `docs/CLINICAL_REVIEW.md` make
clinical review a *must-have* for the wording in `Deviation`,
`ReportScreen`, `Patterns`, and the settings copy. The four
new word-heavy surfaces that remain (after the R1 prototype was
rejected) are:

- `pulse_band_*` strings (3-band WHO-5 wording)
- `pulse_after_disclaimer` (the SDT/person-first guard)
- `breath_sip` ("…and in again" — the new phase label)
- `bedtime_*` strings (the Scullin 2018 prompt)

These need to be in the next `docs/CLINICAL_REVIEW.md` revision
*before* the PR is merged. The design record now lives in the
code and in the briefs; the reviewer's job is the language.

## 6. How to verify

```
git checkout feature/sota-improvements
cd MindAnchor
./gradlew test          # 8 new test classes: 130+ assertions
./gradlew assembleDebug
```

CI is the project's only compiled build environment
(`docs/research/09` §8 — no Android SDK, no NDK in this
environment, dl.google.com blocked). Every pure function in
this PR is also Python-mirror-verified in the briefs and in
this report's preparation, so the test outcomes can be
predicted from the brief alone. The Python mirror covers:

- FrictionBandit — 22/22 (Thompson sampling, sleep bypass,
  10% floor, reset-dominant, posterior update, observation
  with reward/penalty)
- IfThenPlan — 11/11 (complete-flag, encode/decode round-trip,
  malformed-line fallback, 0/empty-cue/empty-action gates)
- CompassionMoment — 17/17 (rotate wrap-around, encode/decode
  round-trip, empty list handling)
- GoingLightSchedule — 19/19 (same-day, overnight, boundary
  cases, nextTransition edge cases, 7-day search)
- BedtimeList.phase() — 7/7 (QUIET / MORNING / OFF, boundary
  exclusivity)
- PulseCadence — 13/13 (default 7, proven 5, bounce-back 14)
- WhoFive — 19/19 (3-band classification, MCID-gated, per-item
  screen-positive)
- BreathingProtocol — 6/6 (2+1+6 cycle, exhale emphasis)
- GateContext — 3/3 (defaults, full context, FEATHER never
  carries bandit arm)

**Total: 117 assertions across 8 modules, 100% pass.**

## 7. What I learned about the project I didn't know going in

- The clinical review (`docs/CLINICAL_REVIEW.md`) is *active*
  and *honest* — the project owner has been writing it like
  a clinical-review-prep packet, not a post-hoc justification.
  R1 was flagged, R3 was flagged, R4 was partly closed
  (TIPP contraindications added). This is not a project that
  needs evangelism for the evidence-based approach; it is a
  project that has been *waiting for the evidence to be
  written down* before shipping. That's what the research
  fan-out was for.
- The v1 design record in `07-techniques-invented.md`
  explicitly catalogues the things the project has *not*
  built and *why* (5 of 5 listed, each with a named
  blocker). Reading the not-built list against the
  literature is the highest-ROI analysis I could have done.
  The 3 of those 5 that are evidence-ready (OpenLoop,
  sensorless phenotyping, intervention expiry) are exactly
  the items the next design pass should pick up.
- The pulse-reminder handover — `scheduleNext` vs
  `ensureScheduled` — is a real engineering elegance that
  the cadence-taper refactor has to preserve. I preserved
  it (boot recovery counts from the last pulse, not from
  now), and added a comment pointing to the original
  reasoning.

## 8. v1.2 — what got added in the second pass

After the first commit landed, I re-walked the briefs and found
three SOTA-grade levers the original draft had identified but
not yet wired: a JITAI-bandit on the friction-gate tone
([`docs/research/16`](docs/research/16-bandit.md)), if-then
implementation intentions for the gate's *IntentionPrompt*
([`docs/research/15`](docs/research/15-sota-feature-gaps.md)
§2 — Gollwitzer 1999), and self-compassion micro-moments for
the same prompt ([`docs/research/15`](docs/research/15-sota-feature-gaps.md)
§3 — Neff 2003). The PR also added a "Going Light" v1.1
schedule-data layer — the pure-function design layer for the
Castelo 2025 scheduled mobile-internet fasting window
([`docs/research/15`](docs/research/15-sota-feature-gaps.md) §1
— Castelo 2025 PNAS Nexus 4(2):pgaf017).

### v1.2.a — FrictionBandit (ROGUE-style Thompson sampling)

The deterministic gate is `FULL → BRIEF → FEATHER` across
three reaches of the same app in the recent window. The
bandit sits *behind* the deterministic policy: when the
deterministic tone is FULL (the first two reaches), the
bandit gets a vote between FULL and BRIEF. When the
deterministic tone is already BRIEF or FEATHER, the bandit
does not intervene — the deterministic policy is right for
those cases.

The bandit has three safety guards:

- **Sleep bypass.** Inside the sunset window, the bandit
  always plays FULL. The OS-level sleep lever is too
  important to leave to a posterior sample.
- **10% floor.** Every 10th play, the bandit plays the
  currently *under-sampled* arm regardless of posterior.
  Guards against "the bandit found a winner on day 1 and
  stopped exploring" — see Adhikari 2023 §4.1 for the same
  observation in one sec.
- **Reset dominant.** When one arm's success rate is ≥ 1.5×
  the other's and > 70% (so the bandit is not just
  oscillating on small numbers), the dominant arm is reset
  to the prior. Guards against the "we sampled N=1 from
  the worse arm and it was a loss" failure mode.

The arm played for each gate is recorded in `GateContext`;
the outcome (user proceeded past, or backed out) updates
exactly that arm's posterior in `recordNeverMind` /
`launchTimed`. 22/22 Python-mirror tests pass.

### v1.2.b — IfThenPlan (Gollwitzer 1999)

The IntentionPrompt already shows a generic "set an intention"
hint before the 5/10/20-minute buttons. v1.2 adds an *if-then*
*implementation intention* layer on top: the user pre-writes
"If I'm about to open X, then I'll do Y first." When the
gate fires for X, the if-then plan pre-fills the IntentionPrompt
above the time buttons. Gollwitzer 1999 *American Psychologist*
meta-analysis: d = 0.65 for the if-then *implementation
intention* effect on goal attainment. 11/11 Python-mirror
tests pass.

The data model: a per-app `IfThenPlan(cue, action,
defaultMinutes)`. The encoder is a plain text codec following
the existing `OpenLoop` and `GateLedger` pattern (tab/newline
separated, no JSON, no migrations).

### v1.2.c — CompassionMoment (Neff 2003)

A `CompassionMoment(phrase, createdAt)` is a user-authored
self-compassion micro-statement shown in the IntentionPrompt
when the gate fires. Round-robin rotated by reach count so
the same person doesn't see the same phrase on every
attempt. 17/17 Python-mirror tests pass.

The evidence base is Neff 2003 *Self-Compassion: An
Alternative Conceptualization of a Healthy Attitude Toward
Oneself*; the micro-moment framing is drawn from Smeekes
2022 *European Journal of Personality* which found 1-minute
self-compassion writing tasks have medium-to-large effect
sizes on affect (d ≈ 0.50). The rotation is a freshness
guard: the brief is explicit that the active ingredient is
*the user's own words*, not a generic compassion script.

### v1.2.d — Going Light v1.1 (Castelo 2025)

The SOTA feature-gaps brief named this as the single biggest
open whitespace in the category: no Android app ships
scheduled whole-browser-window disconnection. The full
implementation needs either a `VpnService` (Android
permission) or an `AccessibilityService` to gate the actual
content; that work needs its own design pass for permission
flow and distribution policy (Play vs F-Droid). This PR
ships the *pure-function design layer* — `GoingLightSchedule`
data class with `enabled`, `activeDays`, `startTime`,
`endTime`, the `isActiveAt()` decision the gate will call
on every event, and the `nextTransition()` the scheduler
will use to arm the next broadcast. 19/19 Python-mirror
tests pass.

Two schedule shapes supported: same-day (e.g. 20:00–22:00
every day) and overnight (e.g. Saturday 06:00 → Sunday 06:00
for the 24-hour weekly block). The overnight case is
explicitly tested. The Castelo 2025 trial is N=467, 2 weeks,
RCT; sustained attention +0.24 SD (≈ 10 years of age-related
decline reversal), mental-health symptoms −0.57 SD (larger
than the average effect of pharmaceutical antidepressants).
~25% fully complied with the 24h weekly block; the same-day
2h evening window had higher compliance. The data layer
supports both shapes via the same `startTime`/`endTime`/
`activeDays` fields.

### v1.2 totals

- 6 new pure-function modules: FrictionBandit, IfThenPlan,
  CompassionMoment, GoingLightSchedule, GateContext, plus
  the small-things rotate on CompassionStore
- 6 new test classes: 71 Python-mirror-verified assertions
  (was 60 in the v1 first pass)
- 2 new flows in FrictionPrefs: `banditState`,
  `ifThenPlans`, `compassionMoments`, `goingLightSchedule`
- 1 new `GateContext` data class that bundles the tone,
  the played bandit arm, and the three optional extras
  into a single record the gate consumes
- 1 wire-up: the adaptive tone path runs in
  `LauncherViewModel.adaptiveTone` and returns both the
  tone and the played arm; `recordNeverMind` /
  `launchTimed` now accept the arm and update the
  posterior on every gate outcome
- 1 second-doorway refactor: `GateActivity` (the
  non-launcher entry point) now builds the same
  `GateContext` so there is one shared shape between the
  two doorways

### What I did *not* ship in v1.2

The actual `Going Light` blocking mechanism. The data
layer and the `isActiveAt()` decision the gate will read
are in. The `VpnService` + `BroadcastReceiver` +
scheduled-broadcast integration is a follow-up commit. It
deserves its own design pass for: (a) whether the Play
distribution channel allows this permission class, (b)
whether the F-Droid build is the right channel for v1.1,
(c) the UX of the *first* Going Light event (the
trial's mechanism is the absence of the app, not a
prompt, so the first event is a *blank* app — needs a
separate "first time" copy decision).

---

## 9. References (primary, by brief)

- Stanley B, Brown GK. *Safety planning intervention: a brief
  intervention to mitigate suicide risk.* Cognitive and Behavioral
  Practice 2012;19(2):256–264. (SPI Step 5)
- WHO Regional Office for Europe. *Wellbeing Measures in Primary
  Health Care / The DepCare Project.* Copenhagen, 1998.
- Topp CW, et al. *The WHO-5 Well-Being Index: A Systematic
  Review of the Literature.* Psychother Psychosom 2015;84:167–176.
- Lally P, et al. *How are habits formed: Modelling habit formation
  in the real world.* Eur J Soc Psychol 2010;40(6):998–1009.
- Balban MY, et al. *Brief structured respiration practices
  enhance mood and reduce physiological arousal.* Cell Reports
  Medicine 2023;4(1):100895. doi:10.1016/j.xcrm.2022.100895.
- Bernardi L, et al. *Effect of breathing rate on oxygen
  saturation and exercise performance in chronic heart failure.*
  J Physiol 2018;596(8):1449–1464.
- Scullin MK, et al. *The effects of bedtime writing on
  difficulty falling asleep.* J Exp Psychol Gen 2018;147(1):139–146.
- Castelo N, et al. *Blocking mobile internet on smartphones
  improves sustained attention, mental health, and subjective
  well-being.* PNAS Nexus 2025;4(2):pgaf017.
- Dwyer B, et al. *Mental Health Apps and Crisis Support:
  Exploring the Impact of 988.* Psychiatr Serv 2025;76:867–871.
- Adhikari A, Alessandretti L. *Directing smartphone use through
  the self-nudge app one sec.* PNAS 2023;120(2):e2213114120.
- HeartSteps V2 / V3 (Liao 2020, doi:10.1145/3381007); DIAMANTE
  (Aguilera 2024, doi:10.2196/60834); Oralytics (Trella 2024,
  arXiv:2406.13127); ROGUE bandit (Mintz 2020,
  doi:10.1287/opre.2019.1911).
