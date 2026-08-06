# SOTA Survey 1: Smartphone-Based Mental Health Sensing & Intervention (2020–2026)

> Research report prepared for MindAnchor (OS-level launcher + notification control).
> Effect sizes and cautionary/null findings flagged.

## 1. Digital phenotyping platforms & predictive validity

**Platforms in active use:** mindLAMP (Torous/BIDMC, open-source, iOS+Android, passive + active + intervention delivery), RADAR-base/RADAR-CNS (King's College London consortium, wearable + phone), AWARE (open sensing framework), Beiwe (Onnela, Harvard), Ilumivu (commercial EMA). mindLAMP is the most directly relevant to an OS product because it is open-source and combines passive sensing with intervention delivery.

- **Schizophrenia relapse — POSITIVE but modest.** Henson/Torous et al., *Schizophrenia* (npj) 2023, three-site two-country mindLAMP study: anomalies in passive data (geolocation, accelerometer, screen state) were **2.12× more frequent** in the month before relapse; passive-data anomaly-detection model beat a survey-only naive model. https://www.nature.com/articles/s41537-023-00332-5
- **CrossCheck (Dartmouth, schizophrenia).** Foundational passive-sensing relapse work (63 patients, up to 1 yr, 6 sensor modalities). Later routine-clustering reanalyses (Adler et al., *JMIR mHealth* 2022) improved relapse prediction but remain small-N and patient-dependent. https://mhealth.jmir.org/2022/4/e31006/PDF
- **Depression symptom severity — CAUTIONARY.** Meyerhoff, Mohr et al., *JMIR* 2021: sensor changes (GPS, exercise duration) *preceded* depression/anxiety changes, but the relationship was largely one-directional and effects were small; not the clean bidirectional signal earlier hype implied. https://www.jmir.org/2021/9/e22844
- **RADAR-MDD — CAUTIONARY.** Zhang et al., *JMIR* 2023 ("Challenges in Using mHealth Data… to Predict Depression Symptom Severity"): substantial missingness and heterogeneity limit predictive performance; the consortium's own framing is that RMT feasibility/acceptability are established but *predictive* validity for relapse is not yet clinically reliable. https://www.jmir.org/2023/1/e45233
- **KEY NEGATIVE — generalization failure.** Müller, Chen, Harari et al., *Scientific Reports* 2021: the same GPS-mobility pipeline that hit **AUC 0.82 in N=57 students collapsed to AUC 0.57 (≈chance) in a demographically heterogeneous N=5,262 US sample** (57M GPS points). Small homogeneous-sample results do not transfer. https://www.nature.com/articles/s41598-021-93087-x
- **2025 systematic review — mixed.** *JMIR* 2025 (mobile sensing for depression, 9 studies): 67% could predict episodes/severity, but only **1 of 9 (11%)** could distinguish worsening vs. relapse vs. recovery — the clinically actionable task remains largely unsolved. https://www.jmir.org/2025/1/e57418
- **Engagement ceiling — CAUTIONARY.** Baumel et al., *JMIR* 2019: median **15-day retention 3.9%** across 93 mental-health apps; median ~5 uses before abandonment. https://www.jmir.org/2019/9/e14567/citations

**OS implications:** (a) Passive sensing is real but weak and non-generalizing — treat sensor-derived risk as *low-confidence nudging input*, never diagnosis. (b) Personalized/within-person anomaly detection (deviation from a user's own baseline, as in the mindLAMP relapse work) generalizes better than cross-person models — build per-user baselines on-device. (c) OS-level sensing sidesteps the 3.9% retention problem: an always-on launcher collects data passively without depending on the user opening an app. (d) Handle missingness natively — it is the dominant failure mode.

## 2. Just-in-time adaptive interventions (JITAI) / micro-randomized trials

- **Framework of record.** Nahum-Shani & Murphy, *Annual Review of Psychology* 2026, "Just-in-Time Adaptive Interventions: Where Are We Now and What Is Next?" — canonical JITAI decision-point/tailoring-variable framework; flags receptivity vs. state-of-vulnerability distinction and habituation as the core unsolved problems. https://www.annualreviews.org/content/journals/10.1146/annurev-psych-121024-044244
- **HeartSteps v1/v2 — POSITIVE but DECAYING.** Activity suggestions produced a real but *transient* step lift; the treatment effect **decays with repeated prompts** (habituation) — a repeatedly documented finding driving the move to RL timing. Time-varying effect modeling: https://arxiv.org/html/2410.15049v1
- **Sense2Stop (smoking/stress) — CAUTIONARY/NULL.** MRT of stress-management prompts triggered by wearable stress detection (Spring, Nahum-Shani et al., *Annals of Behavioral Medicine* 2023). Prompts did not robustly reduce momentary stress; effects were small/context-dependent and consistent with habituation. Protocol: https://pubmed.ncbi.nlm.nih.gov/34375749/
- **DIAMANTE (RL messaging, diabetes+depression) — POSITIVE.** Aguilera et al., *JMIR* 2024, 3-arm RCT. The **adaptive RL-messaging arm increased steps 19%** vs. 3.9% (control) and 1.6% (random); RL arm gained ~608 steps/day over 24 weeks while control/random *declined*. Shows RL-timed messaging beats both fixed and random schedules. https://www.jmir.org/2024/1/e60834 — but note: the win is engagement/steps, not a depression-symptom outcome.
- **Oralytics (RL oral-care timing) — POSITIVE (algorithmic).** Trella, Murphy et al., *arXiv* 2024: deployed online RL in a real clinical trial (fall 2023–summer 2024); resampling analysis shows the algorithm *learned* action advantages by state. Proof RL can run safely live on phones. https://arxiv.org/abs/2406.13127

**OS implications:** (a) RL-timed micro-prompts beat random and fixed schedules (DIAMANTE) — timing is the lever, and an OS that sees real-time context (screen state, location, motion, app usage) owns the best decision-point signal. (b) **Habituation is the enemy** (HeartSteps decay, Sense2Stop nulls) — budget prompts scarcely, vary content, and gate on genuine receptivity; do not spam. (c) Prompting works better for behavior (steps) than for directly moving mood — position nudges as behavioral scaffolding, not therapy.

## 3. Ecological momentary / micro-interventions (EMI) — RCT & meta-analytic evidence

- **Standalone apps — POSITIVE but SMALL.** Linardon et al., *World Psychiatry* 2024, meta-analysis of **176 RCTs**: depression **g=0.28** (NNT 11.5), generalized anxiety **g=0.26** (NNT 12.4). Larger effects when apps include CBT components, mood monitoring, or chatbots. Small effects are the honest ceiling. https://onlinelibrary.wiley.com/doi/abs/10.1002/wps.21183
- **EMI meta-analysis.** Versluis et al., *JMIR* 2016 (still the anchor): within-subject **g=0.57** on mental health, larger with human support. Newer transdiagnostic-app meta-analysis, *npj Digital Medicine* 2025, continues to show small-to-moderate effects. https://www.nature.com/articles/s41746-025-01860-3
- **CAUTIONARY — adverse events under-reported.** *npj Digital Medicine* 2024 meta-analysis of adverse events in mental-health-app trials: harms are systematically under-measured; symptom worsening does occur. https://www.nature.com/articles/s41746-024-01388-y
- **Human support amplifies effect** across every meta-analysis (Versluis, Linardon).

**OS implications:** (a) Effects are small and depend on CBT content + mood monitoring — bundle short evidence-based CBT micro-exercises, not generic tips. (b) Add a light human/social layer where possible (support consistently boosts g). (c) Instrument for harm, including a way to detect worsening and de-escalate. (d) Don't oversell: g≈0.28 means most users won't notice — set expectations and design for the responders.

## 4. Sleep / circadian phone-based interventions

- **Sleep Regularity Index (SRI) — STRONG target.** Windred, Phillips et al., *Sleep* 2024 (UK Biobank, ~60k, accelerometry): **SRI beats sleep duration** as a mortality predictor — top vs. bottom quintile ~**20–48% lower all-cause mortality**. https://academic.oup.com/sleep/article/47/1/zsad285/7344663 Companion work (2024): irregular sleep predicted risk for **131 diseases**, more than double short-duration. SRI is computable from phone-derived sleep/wake proxies (screen-off, motion, first-unlock).
- **Sleep regularity → mental health.** Accelerometer study (2024/2025): regular sleep patterns (not just duration) associated with **lower incident depression and anxiety**. https://pmc.ncbi.nlm.nih.gov/articles/PMC12404321/
- **Digital CBT-I — POSITIVE vs. inactive, CAUTIONARY vs. active.** 2023 meta-analysis (7 RCTs, 3,597): dCBT-I cut sleep-onset latency **15.5 min**, wake-after-sleep-onset **15.6 min**, +7.9% sleep efficiency vs. inactive controls — but **no significant difference vs. active controls**. Fully-automated dCBT-I confirmed effective, *npj Digital Medicine* 2025. https://www.nature.com/articles/s41746-025-01514-4 dCBT-I also reduces depressive symptoms and builds resilience protecting against later depression.

**OS implications:** (a) Make **sleep regularity (SRI), not duration, the headline metric** — it's the strongest-evidenced, phone-derivable target here, and OS-level unlock/screen-state logs estimate sleep-wake windows without a wearable. (b) Embed dCBT-I components (stimulus control, sleep-window scheduling) — strongest content-based sleep lever. (c) Use the launcher to enforce a consistent wind-down/wake schedule (regularity) rather than nagging about hours slept.

## 5. OS/system-level work — MindAnchor's actual lane

- **Notification batching — POSITIVE, causal.** Fitz, Kushlev, Ariely et al., *Computers in Human Behavior* 2019 (RCT, N=237): **three daily batches** made users happier, less stressed, more attentive and productive vs. as-usual; mediated by improved subjective attention quality. https://www.sciencedirect.com/science/article/abs/pii/S0747563219302596
- **Notification disabling — MIXED.** *Media Psychology* 2024: disabling notifications changes behavior but has complex/uneven wellbeing effects — blanket blocking can raise FOMO/anxiety in some users. https://www.tandfonline.com/doi/full/10.1080/15213269.2024.2334025
- **Screen-time reduction — POSITIVE, small-moderate.** *BMC Medicine* 2025 RCT (N=111, ≤2h/day for 3 weeks): small-to-moderate improvements in depressive symptoms, stress, sleep, wellbeing. https://link.springer.com/article/10.1186/s12916-025-03944-z
- **Focus/DND bundle field studies** report ~50% notification reduction with stress decreases and improved morning HRV — mechanism is interruption reduction, not content.

**OS implications:** This is the strongest-evidenced, most defensible ground for an OS product. (a) **Batch notifications into a few scheduled releases** with a priority allow-list — do this by default; it has direct causal RCT support and a clear mechanism (attention quality). (b) Prefer **batching/delay over blanket disabling** — full silencing backfires via FOMO for some users; make priority contacts always pass. (c) Screen-time reduction and enforced wind-down are legitimate OS-level levers with RCT backing. (d) OS-level operation is a structural advantage over apps: it defeats the 3.9% app-retention problem, and passive per-user baselines feed the anomaly-detection and JITAI-timing approaches that generalize best.

## Cross-cutting takeaways for the build

1. **Sensing:** low-confidence, per-user baselines only; cross-person mood/depression models don't generalize (AUC 0.82→0.57). Build anomaly detection against the user's own history.
2. **Intervention timing:** RL/adaptive timing beats fixed and random (DIAMANTE) — but ration prompts hard; habituation kills effects (HeartSteps, Sense2Stop).
3. **Content:** small effects (g≈0.28), maximized by CBT + mood monitoring + any human/social support; instrument for harm.
4. **Sleep:** target regularity (SRI), not duration — best evidence-to-phone-metric fit; embed dCBT-I.
5. **Core edge (notification control):** batching has the cleanest causal evidence; batch-and-prioritize by default rather than block.
