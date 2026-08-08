# Bandit-timed friction for MindAnchor v1.2 — SOTA brief

**Scope.** What evidence exists for adaptive, on-device, RL-timed anti-habituation nudges, and what can actually ship in MindAnchor given the existing `FrictionTone.kt` (deterministic in `recentOpens` + `insideSleepWindow`) and the per-app `GateLedger` (`shown`, `abandoned`, `sinceDay`) plus the nightly per-user deviation report (robust z-score vs. personal median).

**TL;DR recommendation.** A per-user, 2-arm (FEATHER-vs-DEFAULT) **discounted Thompson sampler over a 3-feature context** (`recent_success_rate_24h`, `inside_sleep_window`, `time_of_day_bucket`) using **Beta posteriors on the shown→shown-or-passed reward signal**, with a **forced exploration floor of 10% per arm**, updated nightly against the deviation report, and reset to uniform priors when the deviation report flags a 7-day "stale" interval (the intervention-expiry design from `docs/research/07` §5). This is ~120 lines of Kotlin, runs on-device on a `Beta(a,b)` conjugate update, and is the direct intellectual descendant of the HeartSteps V2 / Oralytics Thompson samplers — the two published, in-production bandit JITAIs for behavior change.

---

## 1. DIAMANTE (Aguilera et al., JMIR 2024)

- **Citation.** Aguilera A, et al. *J Med Internet Res* 2024;26:e60834. doi:10.2196/60834. URL: https://www.jmir.org/2024/1/e60834
- **Trial.** NCT03490253, n=168, 6-month 3-arm RCT (Control / Random / Adaptive RL) in low-income English/Spanish-speaking adults with diabetes + depression.
- **Algorithm.** Contextual multi-armed bandit with **Thompson sampling**, action space = {6 behavioral message types}, context = prior-day step count, time since last message of each type, language, and a handful of state features; reward = same-day step count. Published earlier as the Yom-Tov / Aguilera "contextual RL for micro-randomized trials" line of work; the JMIR 2024 paper is the deployment RCT.
- **Effect size.** Adaptive arm: **+3.6 steps/day** (95% CI 2.45–4.78; p<.001), cumulative **+606 steps (+19%)** over 168 days, vs. +1.6% (Random) and +3.9% (Control). Note: Random and Control *both declined* in slope; the adaptive arm's gain is the divergence from a negative trend.
- **Shippable in 50–200 lines Kotlin?** Yes for the algorithm shape; the data pipeline (passive step counts from Google Fit/HealthKit, day-level reward) is the heavy lift, not the sampler.

## 2. HeartSteps V2 / V3 (Liao, Klasnja, Murphy 2020)

- **Citation.** Liao P, Greenewald K, Klasnja P, Murphy S. *Proc ACM Interact Mob Wearable Ubiquitous Technol* 2020;4(1):18. doi:10.1145/3381007. URL: https://dl.acm.org/doi/10.1145/3381007
- **Algorithm.** **Linear Thompson sampling** with a delayed-effect proxy `η_d` (a "value" term that penalizes treatments likely to induce future disengagement based on running dosage) and a Bernoulli action sampler with **probability clipping in [0.2, 0.9]** to keep the no-treatment option reachable. State `S = {availability, context Z, dosage X}`. Posterior update on `β` once per day from the day's 5 decision points.
- **Habituation finding (the headline for MindAnchor).** In HeartSteps V1 (Klasnja et al. *Ann Behav Med* 2019;53:573–582, doi:10.1093/abm/kay067), walking suggestions raised the 30-min step count by **+271 steps (~107%) at day 0 but only +65 steps by day 20**. Exit interviews: "the suggestions became boring after 2–4 weeks." The V2 algorithm is the explicit response: RL personalization to slow habituation, plus a hard average budget (1.5 pushes/day).
- **Shippable in 50–200 lines Kotlin?** The full Liao algorithm needs a linear-Gaussian posterior with matrix ops and the η proxy → ~300 lines. The simpler "TS with a Beta posterior on the shown→effective binary outcome" is the MindAnchor-appropriate reduction.

## 3. Oralytics (Trella, Murphy et al., 2024)

- **Citation.** Trella AL, Zhang KW, et al. *Oralytics Reinforcement Learning Algorithm.* arXiv:2406.13127v2, Sep 2024. URL: https://arxiv.org/abs/2406.13127. Companion deployment paper: arXiv:2409.02069 (AAAI 2025), URL: https://arxiv.org/abs/2409.02069. Trial protocol: Nahum-Shani et al. *Contemp Clin Trials* 2024;139:107464, doi:10.1016/j.cct.2024.107464.
- **Algorithm.** **Contextual bandit, Thompson sampling, Bayesian linear regression reward model, full pooling across all users in the study.** Context features include recent engagement, time-of-day, day-of-week; action = whether-and-when to send a prompt; reward = engagement proxy in the next hour. The 2024 design paper explicitly motivates posterior sampling for habituation: "frequent selection of a specific action will lead to habituation" → Bayesian posterior shrinkage toward underused arms is itself an anti-habituation mechanism.
- **Effect size.** Trial endpoints (dental self-care) reported in protocol; the algorithm paper's contribution is design, not the RCT effect. The closest reported production A/B (the Adaptive Text Messaging trial at USC) found ~+6.6 percentage-point adherence gain (74.3% vs 67.7%) for a Thompson-sampled RL text-messaging arm vs. control over 6 months.
- **Shippable in 50–200 lines Kotlin?** The full pooling + Bayesian linear regression is not. But the **per-user Thompson sampler with a conjugate prior on a 1-d success rate** is the MindAnchor-realistic subset, and is mathematically what Trella's design reduces to when each user has their own posterior (the paper itself notes per-user variants are reasonable when privacy rules out pooling).

## 4. Contextual bandits vs. deep RL

For a launcher with ~3 friction tones, a 3-feature context, and one user, **deep RL is wrong**. Bandit / contextual-bandit is the right frame because (a) the action space is tiny and discrete, (b) the reward is effectively one-step (the next gate event), and (c) deep models overfit per-user with the data volumes a single phone produces. Trella's own Oralytics design discussion explicitly rejects deep RL for the same reasons and uses contextual TS. Mintz et al. (*Operations Research* 2020;68(5):1493–1516, doi:10.1287/opre.2019.1911) — see §7 — formalize the deeper point: when rewards are *non-stationary* (i.e., habituation), the right model is structured non-stationary bandits, not deep RL.

## 5. JITAI / Liao 2020 / Nahum-Shani & Murphy 2026 — minimum viable JITAI

- **Liao 2020** (above) is the **RL component of a JITAI**: decision points, tailoring variables, intervention options, a distal outcome.
- **Framework.** Nahum-Shani I, Smith SN, Spring BJ, et al. *Ann Behav Med* 2018;52(6):446–462, doi:10.1007/s12160-016-9830-8. (The "JITAIs in mHealth" organizing paper; this is what your code is implementing whether or not you call it that.)
- **2026 review.** Nahum-Shani I, Murphy SA. *Annu Rev Psychol* 2026;77:679–703, doi:10.1146/annurev-psych-121024-044244. (Epub Sep 2025.) URL: https://pubmed.ncbi.nlm.nih.gov/40939059/. Names three open challenges — (1) individuals can't engage when they need it most, (2) suboptimal engagement with digital interventions, (3) underuse of social context. The second is MindAnchor's exact problem.
- **Minimum viable JITAI for MindAnchor.** Five ingredients from Nahum-Shani et al. 2018 §2: **decision points** (every gate event), **tailoring variables** (`recentOpens_rate_24h`, `insideSleepWindow`, `time_of_day_bucket`), **intervention options** (the existing `FULL / BRIEF / FEATHER` set), a **decision rule** (the bandit below), and an **outcome** (the next entry in `GateLedger`).

## 6. Bandit algorithm trade-offs

- **ε-greedy.** Simplest; ε=0.1 means 10% random exploration forever. Tends to keep sampling clearly-bad arms; replicates poorly (Kuleshov et al., *Replicable Bandits*, arXiv:2407.15377).
- **Thompson sampling (conjugate Beta on Bernoulli reward).** Asymptotically optimal regret (Russo & Van Roy, *JMLR* 2014); converges in 10s of trials per arm; **directly incentivizes anti-habituation** because the posterior on an overused arm shrinks; uses 2 floats per arm. **This is the default for mobile health** (Oralytics, Drink Less, HeartSteps, DIAMANTE).
- **UCB / LinUCB.** Tighter finite-sample bounds but no native treatment of non-stationarity; less robust to per-user priors on a phone.
- **Smoothed Thompson sampling / Boltzmann.** Required for *replicability* across study re-runs (Kuleshov 2024), but MindAnchor has one user per model so this is moot. **Pick plain Thompson with a 10% clipped exploration floor** and ship.

## 7. The "self-resetting" / intervention-expiry design (`docs/research/07` §5)

The doc is right that **no funded product ships this** — it is a *growth-negative* feature. The published theory that supports it is Mintz, Aswani, Kaminsky, Flowers, Fukuoka. *Nonstationary Bandits with Habituation and Recovery Dynamics.* **Operations Research 68(5):1493–1516, 2020**, doi:10.1287/opre.2019.1911. arXiv:1707.08423. URL: https://pubmed.ncbi.nlm.nih.gov/32730896/. They define the **ROGUE (Reducing or Gaining Unknown Efficacy) bandit** in which frequent selection *decreases* an action's reward (habituation) and abstention *recovers* it. A 2025 follow-up, **ROGUE-TS** (arXiv:2511.02944), proves that Thompson sampling in this model achieves sublinear regret and *naturally* retires overused arms — the math says "expiry" is exactly what a posterior-shrinkage bandit does for free. So: a TS bandit on `FrictionTone` *is* the self-resetting mechanism; you don't need a separate effect detector for "did this change behavior" — you just need the posterior on the most-sampled arm to fall below a 7-day-deviation-report threshold and let the next decision sample away from it.

## 8. On-device engineering — the 120-line Kotlin sketch

**State (per arm, per user, persisted to DataStore):** `alpha: Float, beta: Float, lastSampledDay: Int, recentSuccesses: Int, recentTrials: Int` (last 24h sliding window for the success-rate context feature).

**Context vector `x` (3 features, all in {0,1} or {0,1,2,3}):**
- `x1 = recent_success_rate_24h` bucket (0 = <0.25, 1 = 0.25–0.5, 2 = 0.5–0.75, 3 = ≥0.75)
- `x2 = inside_sleep_window ? 1 : 0`
- `x3 = time_of_day_bucket` (morning / afternoon / evening / night, 0–3)

**Per-arm posterior.** A 2D weight vector `w_a ∈ R^4` (intercept + 3 features) with a Gaussian prior `N(0, σ²I)` updated via online Bayesian linear regression on a binary reward `r ∈ {0,1}` ("did the user proceed past the gate within 60s?"). This is the Trella / Liao shape, but per-user. **No float64 needed**; float32 is fine for a 4-d weight vector and a 3-arm context. ~80 LOC for the posterior update.

**Action selection (the one block that has to be right):**
```
for each arm a:
    w̃_a ~ N(w_a, σ² I)               // Thompson sample
    score_a = sigmoid(w̃_a · x)
choose arm a* = argmax_a score_a, but
with probability 0.1 sample uniformly  // exploration floor
```
**~15 LOC.**

**Reward update:** at the next gate event, look up `GateLedger` for the chosen arm; `r = 1` if the user opened the target app within 60s, else `r = 0`; run one stochastic-gradient step on the posterior. **~10 LOC.**

**Self-reset (the §5 mechanism, free with TS):** if the deviation report (already exists, nightly) flags that the user's median `shown→proceed` rate has not improved for **7 consecutive days** on the dominant arm, **reset that arm's posterior to the prior** `N(0, σ²I)` and the next sample will naturally drift. This is the *exact* intervention-expiry behavior the docs call out, implemented as posterior reset rather than a hand-rolled effect detector. The robust-z-score from the deviation report is the trigger signal you already have.

**Privacy / no-network.** All state lives in a single file under `Context.dataStore`; no network calls; no telemetry of the posterior updates. The per-user pooling that Oralytics uses is intentionally *not* done here (MindAnchor has no backend and shouldn't add one).

**Constraints check.** No network, no server, no float64. Conjugate-Beta version fits in ~120 lines including comments; the linear-Gaussian version (closer to Oralytics/Liao) fits in ~200. Either runs in <1ms per decision on a mid-range phone.

---

## Per-item summary

| Item | Primary citation | Algorithm | Effect size | Shippable on phone? |
|---|---|---|---|---|
| 1. DIAMANTE | https://www.jmir.org/2024/1/e60834 | Contextual TS over 6 message types | +19% daily steps vs. Random/Control over 6mo | Yes (sampler); data ingestion is the lift |
| 2. HeartSteps V2 | https://dl.acm.org/doi/10.1145/3381007 | Linear TS + delayed-effect proxy | Initial +107% effect → +24% avg; *habituation by day 20* | Full version: ~300 LOC; reduced Beta version: ~120 LOC |
| 3. Oralytics | https://arxiv.org/abs/2406.13127 | Contextual TS, BLR reward, full pooling | +6.6pp adherence in adjacent RL-text-messaging RCT | Per-user subset: yes; full pooling: no |
| 4. Contextual vs deep RL | Mintz 2020, Trella 2024 | n/a | n/a | Use contextual bandit, not deep RL |
| 5. JITAI / Liao / Nahum-Shani | https://pubmed.ncbi.nlm.nih.gov/40939059/ | JITAI = decision points + tailoring + options + rule + outcome | (framework paper) | Already present in MindAnchor data |
| 6. Bandit trade-offs | Kuleshov arXiv:2407.15377; Russo & Van Roy 2014 | TS > ε-greedy for non-stationary mHealth | TS converges 8% faster than LinUCB in production A/Bs | Yes |
| 7. Self-resetting | Mintz et al., *Operations Research* 2020 (doi:10.1287/opre.2019.1911) | ROGUE bandit; ROGUE-TS (arXiv:2511.02944) | Sublinear regret with habituation+recovery | TS *is* the self-resetter; reset posterior on deviation trigger |
| 8. On-device impl | Trella 2024, Liao 2020 | Per-user contextual TS, 3-feature context, conjugate prior | n/a | ~120–200 lines Kotlin, no network, no server |

## Concrete next step for v1.2

Replace the deterministic `FrictionTone.kt` rule with:

1. A `BanditPolicy` class holding per-arm `(alpha, beta, w, Σ)` persisted in DataStore.
2. A `decide(ctx: FrictionContext): FrictionTone` that samples Thompson on a 3-feature `ctx`, with a clipped 10% exploration floor.
3. A `recordOutcome(arm, proceeded: Boolean)` that updates the chosen arm's posterior and feeds `GateLedger`.
4. A nightly hook (already exists for the deviation report) that **resets any arm whose 7-day-deviation-z-score is unchanged** — this is the §5 "intervention expiry," grounded in Mintz 2020's ROGUE theory rather than a hand-rolled effect detector.
