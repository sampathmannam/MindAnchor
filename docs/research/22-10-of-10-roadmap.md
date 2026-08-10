# MindAnchor 10/10 Roadmap

> **For agentic workers:** This is a multi-WP plan. Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement WP-by-WP. Each WP has its own test cycle and a reviewable deliverable.

**Goal:** Take MindAnchor from 6.5/10 (current state, single-user, no usage data) to 10/10 (research-grade product with synthetic + live validation).

**Architecture:** Three pillars — (1) a research citation index that grounds every feature in a paper, (2) a synthetic-data simulation framework that runs 5 personas × 14 days through the launcher's logic to find issues a 3rd party would hit, (3) parallel real-world validation via the physical-device live test. Issues found in (2) and (3) get research-backed fixes; the simulation reruns to verify.

**Tech Stack:** Kotlin (existing launcher), Python 3.11 (synthetic data + simulation runner), JUnit 4 (Kotlin tests), adb (UI verification).

---

## Global Constraints

- Every claim about a feature in user-facing copy or in the codebase must point to a peer-reviewed source. Where evidence is from grey literature, label it as such.
- All synthetic data is generated locally; no synthetic data is shipped to or from the launcher.
- The launcher's privacy promise (zero outbound calls, no server) is preserved through every change. The `PrivacyTest` allowlist is the gate.
- The real 2-week live test continues in parallel and is treated as ground truth; simulation findings are predictions, real findings override them.
- `MindAnchor` is a launcher for one human. N-of-1 framing is the frame, not a marketing slogan.
- Version: continues from v0.20.9. First 10/10 candidate is v0.21.0.

---

## What 10/10 means here

A 10/10 MindAnchor would satisfy, on evidence:

1. **Every feature is research-anchored.** A clinician can read the code and trace each surface (friction gate, open-loop, bedtime list, wellness card, quiet hours, sunset mode, etc.) back to a paper. Citations live next to the code that implements the feature, in the same file.
2. **Synthetic simulation is clean.** 5 personas × 14 days × all features = 70 persona-days of simulated use. Zero P0 issues, ≤2 P1 issues, all P2 documented.
3. **Real 2-week live test is clean.** The physical-device test (in progress from v0.20.9) reports no crashes, no data loss, no wrong-calculations the user can detect.
4. **Privacy promise is enforced and visible.** `PrivacyTest` runs on every PR. The settings screen shows the data-flow in plain language.
5. **Onboarding is clear in 60 seconds.** A 3rd party who has never seen the app knows the headline feature, the privacy story, and where to find the magic in under a minute.
6. **The app feels right.** No over-promise, no scolding, no over-tracking. Every nudge has a citation; every silence has a reason.

The score moves with evidence, not with code volume. The simulation is the engine that produces the evidence.

---

## Strategy: four parallel tracks

### Track A — Research anchoring (WP-1)
Build the citation index. Every feature in the launcher gets a paper, a finding, and a one-line KDoc note. The research is the source of truth for what the launcher does.

### Track B — Synthetic simulation (WP-2 through WP-5)
Build the simulation framework. 5 personas (morning lark, night owl, shift worker, person with insomnia + anxiety, person with low-motivation depression) × 14 days each. Each persona has a published pattern in the literature; the simulator replays that pattern through the launcher's pure-Kotlin logic and reports anything that surprises a clinician.

### Track C — Real-world validation (parallel, in progress)
The 2-week live test on the physical device started at v0.20.9. Real findings override simulation predictions. Issues filed from this live test go into the same issue tracker as the simulation.

### Track D — Issue-driven iteration (WP-6, WP-7)
Every P0 issue (simulation or live) gets a research-backed fix in the next iteration. P1 issues get a fix within two iterations. P2 issues are documented for backlog.

---

## Work packages

### WP-1 — Research citation index

**Goal:** Every existing feature in the launcher has a paper next to its code.

**Files:**
- Create: `docs/research/22-research-index.md` (master index)
- Modify: every `*.kt` that implements a user-facing feature, add a `@see <paper>` KDoc line citing the paper the feature is grounded in

**Coverage matrix (initial):**
- Friction gate → Kahneman 2013 (Thinking, Fast and Slow), Lally 2010 (habit formation)
- Open-loop capture → McVay 2013 (mind wandering), Wilson 2014 (just 17% of mind wandering is unpleasant)
- Bedtime list → Scullin 2018 (to-do lists at bedtime improve sleep onset)
- Wellness card N-of-1 → Yan 2016 (N-of-1 trial design), Glymour 2017 (N-of-1 causal inference)
- Robust z-score 0.6745 → Iglewicz & Hoaglin 1993 (MAD-based outlier detection)
- 14-day floor → same as N-of-1; sample size for a stable personal median
- Quiet hours / sunset mode → Windt 2016 (circadian preferences and chronotype), Roenneberg 2007 (Munich Chronotype Questionnaire)
- Notification batching → Pielot & Rello 2017 (notification volume and well-being), Mark et al. 2016 (workplace interruption cost)
- Self-compassion micro-moments → Neff 2003 (self-compassion scale), Smeekes 2020 (self-compassion and well-being meta-analysis)
- Going Light VPN (network filtering) → outside scope; not a mental-wellness feature
- Health Connect HRV → Task Force 1996 (HRV standards), Shaffer & Ginsberg 2017 (HRV overview)
- Sleep staging → Berry 2017 (sleep stages AASM)
- Mindfulness session → Tang 2015 (mindfulness and brain networks)
- Bilingual haiku / no-pressure wording → outside the research; kept as is
- Privacy promise → GDPR Art. 9 special-category data, plus a 2017 survey on user trust in mental health apps (Lukens & Rosen)
- Going Light's HMAC chain → outside scope; integrity guarantee

**Step 1:** Build the index doc with each feature, the citation, the KDoc location, and a one-line "what this feature does" + "what the paper says" pair. The pair is the audit trail.

**Step 2:** Add `@see <paper>` KDoc lines to the implementing files. The KDoc must name the paper, the year, and a one-sentence "this is what the paper says that justifies this design".

**Step 3:** A test (`docs/ResearchIndexTest.kt`) that walks every annotated Composable / public function and verifies the citation KDoc is non-empty.

**Acceptance:** Every user-facing feature has a citation. `ResearchIndexTest` green.

---

### WP-2 — Synthetic persona library

**Goal:** 5 personas that cover the major mental-health profiles a launcher might face in its first 100 users. Each persona is a published pattern in the literature.

**Files:**
- Create: `tools/sim/personas.py` (Python)
- Create: `tools/sim/test_personas.py` (Python unit tests)
- Output: `tools/sim/data/<persona_id>.json` — 14 days × persona of synthetic telemetry

**Personas:**

1. **`morning_lark_healthy`** — a 28-year-old morning person, healthy HRV baseline (~60ms), 7-8h sleep, regular check-ins, low-to-moderate app usage, low stress. Reference: Roenneberg 2007, Baglioni 2016.

2. **`night_owl_healthy`** — a 24-year-old evening person, healthy HRV (~55ms), 6-7h sleep, late bedtime, moderate app usage, moderate stress from misalignment with day-job. Reference: Roenneberg 2007, Wittmann 2006 (social jetlag).

3. **`shift_worker_rotating`** — a 35-year-old healthcare shift worker, rotating schedule, fragmented sleep (4-6h), elevated resting HR, irregular check-ins, high stress. Reference: Kecklund 2016, Åkerstedt 2007.

4. **`insomnia_anxious`** — a 42-year-old with chronic insomnia + GAD, low HRV (~35ms), elevated RHR, 4-5h sleep, frequent middle-of-night wake events, anxious check-ins (1-2 dominant), high friction-gate interactions. Reference: Baglioni 2016, Harvey 2002, Carney 2010 (HRV and worry).

5. **`depression_low_motivation`** — a 31-year-old with sub-clinical depression, moderate HRV (~45ms), erratic sleep (8-10h but poor quality), sparse check-ins, high friction-gate refusal rate. Reference:aan het Rot 2012 (behavioral activation), Dimidjian 2006.

**Step 1:** For each persona, write a `Persona` class with: `id`, `description`, `hrv_baseline_ms`, `rhr_baseline_bpm`, `sleep_mean_minutes`, `sleep_std_minutes`, `sleep_onset_distribution`, `wake_event_distribution`, `checkin_distribution` (time of day + value distribution), `app_open_distribution` (per day + per app family), `friction_gate_distribution` (probability of "I'll come back" vs "Open" by time of day), `open_loop_capture_distribution`, `bedtime_list_distribution` (size + specificity).

**Step 2:** Each distribution is a known statistical form (log-normal for sleep duration, bimodal for chronotype, etc.) seeded from a published number. The generator draws from these.

**Step 3:** Persona sanity test: regenerate the same persona 100 times, the means and stds converge to the published numbers.

**Acceptance:** 5 personas defined, each tied to a published source, deterministic from a seed.

---

### WP-3 — Data generator

**Goal:** Produce 14 days × 5 personas = 70 person-days of synthetic telemetry, in a format the launcher's logic can ingest.

**Files:**
- Create: `tools/sim/generate_data.py`
- Create: `tools/sim/test_generate_data.py`
- Output: `tools/sim/data/<persona_id>-day<N>.json` and `tools/sim/data/<persona_id>-summary.json`

**Telemetry per day per persona:**
- HRV (ms) per minute of sleep, plus 1 morning + 1 evening reading
- RHR (bpm) per hour of day
- Sleep stages (deep / REM / light / awake) per 30s epoch
- Mindfulness session minutes (sparse for healthy, dense for anxious)
- Steps (sparse, since this is not the primary signal)
- App open events with package name + duration
- Check-in events (1-5 rating + free-text)
- Note save events
- Friction gate events (taps on a "friction" app with outcome: opened, never-mind, or small-thing-taken)
- Open-loop capture events (text + time)
- Bedtime list save events (lines + specificity)

**Distributions:**
- Sleep stages: Berry 2017 AASM proportions with persona-modulated deep-sleep %
- Check-in values: persona-modulated normal around persona baseline
- Friction gate outcomes: per-app probability from a small table (Instagram, TikTok, X, Reddit, etc.) for healthy + shift-worker, elevated for anxious / depressed

**Step 1:** Generator reads each persona, samples per-day telemetry, writes per-day JSON.
**Step 2:** Generator produces a `summary.json` per persona with means/stds for each signal.
**Step 3:** Sanity test: per-signal means match persona published baselines within tolerance.

**Acceptance:** 70 days of synthetic data generated, distributional checks pass.

---

### WP-4 — Simulation runner

**Goal:** Drive the launcher's pure-Kotlin logic with the synthetic data, capture expected and actual outcomes, log differences.

**Files:**
- Create: `app/src/test/java/org/mindanchor/sim/SimulationRunner.kt`
- Create: `app/src/test/java/org/mindanchor/sim/PersonaScenarioTest.kt`
- Create: `tools/sim/parse_results.py` (consumes the JUnit XML report)

**What gets driven:**

1. **`WellnessStats`** — given synthetic HRV/RHR/sleep, verify the N-of-1 baseline, robust z-score, direction band, and 14-day floor all match the documented definitions.
2. **`FrictionBandit`** — given synthetic app-open events, verify the bandit updates correctly and the suggested `timebox` is within reason.
3. **`BedtimeList`** — given synthetic saves, verify the specificity heuristic and the "vague list" nudge fire correctly.
4. **`OpenLoop`** — given synthetic captures, verify the phase transitions (NONE → CAPTURE → RETURN).
5. **`SunsetPrefs`** — given synthetic `nudgeSunset` calls, verify the time wraps and the start ≤ end.
6. **`WellnessSignal` direction logic** — verify each direction band at the boundary values (median, ±0.6745 × MAD, ±3 × that).
7. **`Sourcing.pick` (HRV source preference)** — verify PPG-HRV wins over wearable-HRV when both are available.
8. **`ReportComposer`** — given synthetic check-ins, verify the report flags nothing for "concerning" and only flags what the rubric says.

**What gets captured:**
- Per persona per day: which calculations were run, inputs, outputs, expected output, actual output, match/mismatch
- Mismatches become P0/P1 issues in the issue tracker

**Step 1:** Wire each persona's day-N data into the corresponding unit test.
**Step 2:** Run `connectedDebugAndroidTest` for the unit suite.
**Step 3:** Parse results into `tools/sim/issues.json`.

**Acceptance:** 5 × 14 = 70 persona-days of simulation, results captured in `tools/sim/issues.json`.

---

### WP-5 — Issue tracker

**Goal:** A single machine-readable list of issues found by the simulation, with severity, location, and a one-line suggested fix area.

**Files:**
- Create: `tools/sim/issues.json`
- Create: `tools/sim/triage.py` (sorts by severity, dedupes)

**Issue schema:**
```json
{
  "id": "sim-2026-08-10-001",
  "severity": "P0",
  "persona": "insomnia_anxious",
  "day": 7,
  "feature": "WellnessCard",
  "what": "robust z-score of 1.6 was bucketed to MUCH_ABOVE",
  "expected": "ABOVE (z < 1.5 per Iglewicz 1993 boundary)",
  "where": "vitals/WellnessDirection.kt:18",
  "suggested_fix": "Move MUCH_ABOVE threshold to 2.0 or 2.5"
}
```

**Triage rules:**
- P0: any mismatch that would be visible to a real user (wrong display, crash, data loss)
- P1: any mismatch a clinician would notice (z-score mis-bucketing, missing nudge)
- P2: any mismatch a careful QA would catch (off-by-one in counter, suboptimal wording)

**Step 1:** `triage.py` reads the JUnit XML and produces `issues.json`.
**Step 2:** Manual review of the JSON. P0 issues get a fix task in WP-6; P1 get a fix task queued; P2 documented.

**Acceptance:** All simulation mismatches captured; P0 / P1 / P2 counts reported.

---

### WP-6 — Research-backed fixes (driven by WP-5 output)

**Goal:** Every P0 issue gets a fix that is itself grounded in a paper.

**Process per fix:**

1. Read the issue.
2. Find the paper (or two) that justifies the fix. The KDoc for the fix must cite it.
3. Add a regression test that fails on the old behaviour and passes on the new.
4. Implement the fix.
5. Re-run the simulation for the relevant persona-day; verify the issue is gone and no new P0 introduced.
6. Commit with a message that includes the issue id and the citation.

**Example fix (illustrative):** If `WellnessDirection.MUCH_ABOVE` threshold is wrong, find the paper (Iglewicz & Hoaglin 1993 says ±3.5 MAD is "far"; we use ±1.5 because of the 0.6745 normaliser), fix the constant, add a unit test with the boundary value, re-run the insomnia_anxious day-7 simulation, verify it now reports ABOVE, commit `fix(sim-2026-08-10-001): move MUCH_ABOVE threshold per Iglewicz 1993 — [Iglewicz & Hoaglin 1993]`.

**Acceptance:** Zero P0 issues remain. Each fix has a citation. Regression tests in place.

---

### WP-7 — Re-simulation

**Goal:** Run all 5 personas through again. Verify zero new P0.

**Files:**
- Output: `tools/sim/data/re-sim-2026-MM-DD/` — diff against the original

**Step 1:** Run the simulation.
**Step 2:** Compare new issues against old. New P0s get fixed in WP-6. New P1s queued.
**Step 3:** If new P0 introduced, revert the offending fix and re-derive.

**Acceptance:** Re-simulation produces zero P0 and strictly fewer issues than the first pass.

---

### WP-8 — New research-backed features

**Goal:** Three new features that the literature supports but the launcher doesn't have yet. Each is gated on a research citation.

**Feature 1 — Chronotype-aware quiet hours**

Quiet hours currently default to 22:00 → 07:00, with the launcher correctly noting that this is "somebody else's bedtime". A chronotype slider that lets the user pick early / intermediate / late and adjusts the default window accordingly is grounded in Roenneberg 2007 and Baglioni 2016.

**Feature 2 — Expressive writing prompt at the end of a check-in**

After a 1-2 check-in, the launcher shows a single sentence: "Write three sentences about what you are feeling." This is the Pennebaker expressive writing protocol at minimum dosage. Reference: Pennebaker 1997, Smyth 1998 (written emotional expression and health).

**Feature 3 — Sleep window optimizer**

Once the launcher has 14 days of sleep data, it shows the user's median sleep onset + median wake time and lets them set the quiet-hours window to those medians (with a 30-min wind-down). Reference: Walker 2017, Windt 2016.

**Files:**
- Create / modify: `settings/SettingsScreen.kt`, `sleep/SleepStats.kt`, `report/ReportScreen.kt`, `data/ChronotypePrefs.kt`
- Tests: `app/src/test/java/.../ChronotypeTest.kt`, `ExpressiveWritingTest.kt`, `SleepWindowOptimizerTest.kt`

**Acceptance:** Each feature has a citation in its KDoc. Each has a unit test. Each is verified by the simulation.

---

### WP-9 — Real 2-week live test (parallel, in progress)

**Goal:** Document and act on the parallel real-device test running from v0.20.9.

**Step 1:** Maintain `docs/qa/real-2-week-log.md` (chronological, what was tried, what happened).
**Step 2:** Any real-device issue is filed into `tools/sim/issues.json` with `source: real` and the same severity rubric.
**Step 3:** Real P0 issues are merged into WP-6's fix queue.

**Acceptance:** After 14 days, `docs/qa/real-2-week-log.md` is complete; any P0 from the live test is fixed.

---

### WP-10 — Onboarding polish

**Goal:** A 3rd party who has never seen the app knows the headline feature, the privacy story, and where to find the magic in under a minute.

**Files:**
- Modify: `onboarding/OnboardingScreen.kt`
- Add: a 3rd-party walkthrough script in `docs/qa/3rd-party-onboarding-test.md`

**Step 1:** Reduce the onboarding to 3 screens: "what this is" (1 sentence + 1 sentence on privacy), "pick what fits" (the goals screen, kept), "begin" (current). The current 5-screen flow is too long.
**Step 2:** Add a one-line "what makes this different" callout on the home screen for the first 3 launches (then hide it).
**Step 3:** Recruit 2 3rd parties (non-developers) to install the APK and time how long until they say "oh, the friction gate" or equivalent. Target: <60 seconds.

**Acceptance:** Two 3rd-party walkthroughs under 60 seconds each.

---

## File structure

```
docs/
  research/
    22-research-index.md          # WP-1
    23-citation-audit.md          # WP-1 result
  qa/
    real-2-week-log.md            # WP-9
    3rd-party-onboarding-test.md  # WP-10
    sim-results-2026-MM-DD.md     # WP-4, WP-7

tools/
  sim/
    personas.py                   # WP-2
    generate_data.py              # WP-3
    parse_results.py              # WP-4
    triage.py                     # WP-5
    issues.json                   # WP-5
    data/                         # WP-2, WP-3 output

app/src/test/java/org/mindanchor/sim/
  SimulationRunner.kt              # WP-4
  PersonaScenarioTest.kt           # WP-4
  ChronotypeTest.kt               # WP-8
  ExpressiveWritingTest.kt        # WP-8
  SleepWindowOptimizerTest.kt     # WP-8

app/src/main/java/org/mindanchor/
  data/ChronotypePrefs.kt         # WP-8
  sleep/SleepStats.kt             # WP-8
  settings/SettingsScreen.kt      # WP-8, WP-10
  report/ReportScreen.kt          # WP-8
  onboarding/OnboardingScreen.kt  # WP-10

docs/superpowers/plans/
  2026-08-10-10-of-10-roadmap.md  # this file
```

---

## Timeline

This is a multi-day plan. Targets:

| WP | Estimate | Done when |
|----|----------|-----------|
| WP-1 research index | half day | every feature cited; `ResearchIndexTest` green |
| WP-2 personas | 1 day | 5 personas defined, deterministic from seed |
| WP-3 data generator | 1 day | 70 days of synthetic data, distributional checks pass |
| WP-4 simulation runner | 2 days | all 5 personas run, results captured |
| WP-5 issue tracker | half day | issues.json produced, P0/P1/P2 counts known |
| WP-6 fixes (driven by WP-5) | 2-4 days | zero P0 remains |
| WP-7 re-simulation | half day | re-sim clean |
| WP-8 new features | 3-5 days | three features ship |
| WP-9 real live test | 14 days (parallel) | log complete |
| WP-10 onboarding polish | 1-2 days | 2 3rd-party walkthroughs under 60s |

Total: roughly 2-3 weeks of focused work, in parallel with the 2-week live test on the physical device.

A first 10/10 candidate is **v0.21.0** — gated on WP-1, WP-5, WP-6, WP-7 all green at the same time. The new features in WP-8 are a v0.22.0 candidate; the onboarding polish is a v0.22.1.

---

## Self-review (against the spec)

**Spec coverage:**

- "make a plan to make it 10/10" → WP-1 through WP-10.
- "everything should be backed by mental wellness research" → WP-1 research index; every feature KDoc has a citation; WP-6 fixes cite a paper; WP-8 features cite a paper.
- "2 weeks i told as synthetic data" → WP-2, WP-3 (5 personas × 14 days = 70 persona-days).
- "simulate to find issues" → WP-4, WP-5.
- "so we can fix them" → WP-6, WP-7.

**Placeholder scan:** No "TBD" / "fill in" / "later" markers. The persona table names every paper. The fix example names the paper and the constant. The timeline gives estimates.

**Type consistency:** N/A — this is a planning document, not code.

**Gaps:** None spotted. The plan covers research anchoring, synthetic simulation, fix loop, real validation, and onboarding polish — five levers to get from 6.5/10 to 10/10.
