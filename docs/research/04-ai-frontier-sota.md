# SOTA Survey 4: AI + Multimodal Frontier for Phone-Based Mental Health (2024–2026)

> Frontier report prepared for MindAnchor. Lens: what an open-source Android launcher could feasibly ship on-device. Hype explicitly separated from validated results.

## 1. LLMs for mental health

| System | Year | Status/Finding | Feasibility for MindAnchor |
|---|---|---|---|
| **Therabot (Dartmouth), NEJM AI RCT** | 2025 | First RCT of a fully generative fine-tuned AI therapy chatbot: 210 adults (MDD/GAD/eating-disorder risk), 4 wk vs waitlist. ~51% depression symptom reduction, ~31% anxiety; therapeutic-alliance ratings comparable to human therapists. Caveats: waitlist control, staff monitored all transcripts and intervened, years of hand-curated fine-tuning. https://ai.nejm.org/doi/full/10.1056/AIoa2400802 | VALIDATED but not replicable casually: the safety net was human oversight. Do not ship an unsupervised "therapist." |
| **MentalLLaMA** | 2023/24 | First open-source instruction-tuned LLM series (LLaMA-2 7B/13B, IMHI 105K dataset) for interpretable mental-health *analysis*, not therapy. https://github.com/SteveKGYang/MentalLLaMA | Weights + dataset open; 7B is quantizable to phone-runnable. Analysis-only framing is the safer use. |
| **Mental-LLM (IMWUT 2024)** | 2024 | Instruction-tuned Alpaca/FLAN-T5 beat GPT-4 zero-shot on mental-health prediction from online text; fine-tuning matters more than scale. https://dl.acm.org/doi/10.1145/3643540 | Directly encouraging for small on-device fine-tunes. |
| **ChatCounselor / PsyLLM / SoulChat** | 2023–25 | Counseling-tuned LLaMA-7B variants. No clinical outcomes evidence. | Research artifacts, not treatments. |
| **Woebot** | 2025 | Most-studied rules-based chatbot (14 RCTs, FDA Breakthrough designation) **shut its consumer app June 30, 2025** — no FDA pathway for generative versions. | Cautionary tale: regulatory limbo killed the best-evidenced product. |
| **Wysa** | ongoing | ~30+ peer-reviewed publications, FDA Breakthrough designation (chronic pain), NHS-deployed; hybrid rules+NLP, deliberately not free-generative. | The "scripted CBT exercises + NLU routing" pattern is the proven, low-risk architecture an open-source app can copy. |
| **Limbic Access** | 2024–25 | UKCA Class IIa medical device; NHS Talking Therapies triage; published data on improved referral completeness — triage, not therapy. | Triage/screening framing is regulator-tolerated; therapy framing is not. |

**Documented risks:** Stanford FAccT 2025 study: therapy chatbots gave unsafe responses ~20% of the time vs ~7% for human therapists; enabled delusions, missed suicide cues (listed tall bridges after a job-loss disclosure), stigmatized schizophrenia/alcohol dependence; newer/bigger models no better (https://news.stanford.edu/stories/2025/06/ai-mental-health-care-tools-dangers-risks). Context: Character.AI wrongful-death litigation (2024–25); Illinois WOPR Act (Aug 2025) banning AI-delivered therapy; Utah/Nevada disclosure laws. **Design implication:** any conversational feature needs crisis-keyword escalation to human resources, no diagnosis claims, and "wellness, not therapy" scoping.

## 2. On-device small LLMs on Android

- **Gemma 3n E2B/E4B** (Google, 2025): mobile-first, Per-Layer Embeddings → 5B/8B params in ~2–3 GB effective RAM; multimodal (text/image/audio); open weights; runs via MediaPipe LLM Inference API / LiteRT on mid-tier phones. Best current open option.
- **Llama 3.2 1B/3B** (2024), **Phi-4-mini 3.8B** (2025), **Qwen2.5/Qwen3 1.5–4B**: Q4-quantized to 1–2.7 GB; ~10–25 tok/s on Pixel 9/S25-class SoCs via llama.cpp, MLC-LLM, or MediaPipe. 1B models run on 4 GB-RAM devices; 3–4B need ~6–8 GB.
- **Android AICore + ML Kit GenAI APIs** (stable 2025): third-party apps get shared **Gemini Nano** on-device for summarization/proofreading/rewriting — no model download, but flagship-only (Pixel 8+/S24+), and the experimental Prompt API is gated. https://developer.android.com/ai/gemini-nano

**Realistic verdict:** a launcher can today ship a fully private wellbeing model (Gemma 3n E2B or Llama 3.2 1B) for journaling reflection, reframing prompts, notification digest summarization. Constraints: battery (use short bursts), 2–3 GB storage, flagship-skew. Hybrid plan: ML Kit GenAI where available, bundled GGUF fallback. NOT realistic on-device: a safe open-ended therapy agent (see §1 risks).

## 3. Multimodal affect sensing

- **Kintsugi**: 2025 *Annals of Family Medicine* study (n=14,898): AUC ~0.80-class detection of moderate-severe depression from ~25 s of free speech — company-affiliated authors, PHQ-9 as ground truth. **Sonde Health**: internal cohort only. **Ellipsis Health**: pivoted to voice care agents.
- **Independent reality check**: 2025 JMIR Mental Health meta-analysis + systematic reviews: promising in-corpus accuracy but severe cross-corpus/cross-language generalization failure; vocal biomarkers underperform a plain PHQ-9 questionnaire (https://mental.jmir.org/2025/1/e67802). **HYPE flag: vendor "80% accuracy" claims have not survived independent, out-of-domain validation.**
- **BiAffect (typing dynamics)**: strongest camera/mic-free line. 2024–25: keystroke metadata predicts mood-disturbance severity and cognition in bipolar disorder (Frontiers Psychiatry 2025, n=127), affect in suicidal ideation (npj Digital Medicine 2024, https://www.nature.com/articles/s41746-024-01048-1). Research-app scale; effects group-level, not individual-diagnostic.
- **Feasibility:** a launcher owning the keyboard/IME position is uniquely placed for BiAffect-style metadata ("how you type, not what") — fully local, no content capture. Ship as self-insight trends, never diagnosis. Avoid camera-based affect (weak science + EU AI Act restrictions on emotion recognition).

## 4. Wearables

- **Apple State of Mind** (iOS 17+): circumplex-model mood logging; UCLA + 2025 Apple Health Study use it; engagement evidence (80% report increased emotional awareness), not outcome evidence. Not accessible from Android.
- **Fitbit/Pixel Watch Body Response (cEDA)**: first continuous wrist EDA; Google-published training on stress protocols; no independent clinical validation of the consumer feature.
- **HRV generally**: group-level association with stress/depression solid; individual daily inference noisy (sleep, alcohol, illness confounds). Oura/Whoop "stress"/"recovery" scores proprietary and unvalidated for mental-health outcomes.
- **Feasibility:** read via **Health Connect** (open Android API — Fitbit, Samsung, Garmin, Whoop, Oura all write to it): sleep regularity + resting HR + HRV *trends* are the defensible signals for adapting launcher behavior (e.g., gentler mornings after bad sleep). Sleep is the best-validated passive predictor of next-day mood.

## 5. Closed-loop / adaptive systems

- **Oralytics** (Susan Murphy lab, 2024–25): online RL (pooled Thompson sampling) for prompt timing in a registered clinical trial — proof RL works in deployment; algorithm design published. https://arxiv.org/abs/2406.13127
- **HeartSteps v2/v3**: RL decides 5×/day whether to nudge activity; personalization beats uniform prompting. Validated methodology, modest effects, domain not yet mood.
- **Mobile RL for mental health specifically**: still pre-evidence; vulnerability/receptivity detection remains the bottleneck.
- **Feasibility:** a bandit (not deep RL) choosing *when* to surface check-ins/nudges from context (unlock events, time, sleep) is genuinely implementable — the single most transferable frontier idea for a launcher.

## 6. Novel HCI interaction ideas (CHI/IMWUT/UIST 2024–26)

- **Breathm** (CHI EA 2024): adaptive slow-breathing guidance starting from the user's natural rate → transferable as a **breathing-paced unlock/app-open gate** using haptics + animation, no hardware needed.
- **Breath of Life** (CHI EA 2025): biofeedback game training breathing techniques for real-life emotion regulation.
- **Smartwatch digital-wellbeing interventions** (ACM 2024): moving friction to the watch reduces phone pickups.
- **One-minute micro-interventions** (2025–26 pipeline): brief in-context interventions work only when matched to the interruption context — supports launcher-embedded micro-moments over standalone app sessions.
- **Apollo Neuro (haptic regulation)**: one small peer-reviewed 2025 RCT + a crossover claiming ~10–11% HRV increase, but most cited studies are company-run/unpublished. **HYPE-leaning**; still, phone-vibration slow-rhythm haptics during stress moments is free to try and low-risk.
- **Ambient mood displays**: steady CHI stream; evidence limited to short deployments; treat as design inspiration (mood-responsive launcher theming), not intervention.

## Bottom line for MindAnchor

**Validated & buildable now:** scripted CBT/behavioral-activation content (Wysa pattern); Health Connect sleep/HRV trends; keystroke-metadata mood self-tracking (BiAffect pattern); bandit-timed nudges (Oralytics pattern); breathing-gated unlock with haptics; on-device Gemma 3n/Llama 3.2 for private journaling summarization and notification digests.

**Hype / avoid:** open-ended generative "therapist" (Stanford 20% unsafe-response rate; Illinois ban); vendor voice-biomarker accuracy claims; consumer wearable "stress scores" as clinical signals; camera-based emotion recognition.

**Watch:** Therabot replications with active controls; FDA generative-AI mental-health framework (advisory committee Nov 2025); ML Kit Prompt API opening up; Gemma-class multimodal audio on-device (would enable *private* voice-journaling analysis).
