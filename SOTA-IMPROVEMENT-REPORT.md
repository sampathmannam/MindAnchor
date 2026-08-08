# MindAnchor v1 — SOTA Improvement Report

**Date:** 2026-08-08
**Source:** https://github.com/sampathmannam/MindAnchor @ `feature/sota-improvements`
**Branch:** `feature/sota-improvements` (1 commit, 23 files, +2,633 / -42)
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

## 2. What I changed (six items, evidence-anchored)

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
./gradlew test          # 5 new test classes: 60+ assertions
./gradlew assembleDebug
```

CI is the project's only compiled build environment
(`docs/research/09` §8 — no Android SDK, no NDK in this
environment, dl.google.com blocked). Every pure function in
this PR is also Python-mirror-verified in the briefs and in
this report's preparation, so the test outcomes can be
predicted from the brief alone.

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

## 8. References (primary, by brief)

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
