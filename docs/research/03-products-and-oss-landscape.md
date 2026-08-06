# SOTA Survey 3: Calm-Phone / Wellbeing-Launcher Product & OSS Landscape (2024–2026)

> Competitive/landscape report prepared for MindAnchor.

## 1. Dedicated minimal hardware

| Product | Status | Mechanism | Reception |
|---|---|---|---|
| **Light Phone III** ($699, shipping) | Active, proprietary (custom "LightOS" on Android base, not open) | Curated tool list (calls/SMS, music, podcasts, directions, notes, simple camera); no browser, no app store, no feed anywhere | Premium build praised; Engadget: "minimalism stretched to the point of frustration" — practical gaps (no email, 2FA, rideshare, banking) mean it rarely survives as an only device |
| **Mudita Kompakt** (~$439) | Active; Red Dot 2025 | E Ink, custom AOSP fork ("MuditaOS K"). **Not fully open source** — original FreeRTOS-based MuditaOS (Pure/Harmony) is fully OSS on GitHub, but Kompakt's AOSP build is withheld over driver licensing; "open or open-core" promised | 6-month owner reviews: calm and legible, but stock software "feels incomplete"; its saving grace is APK **sideloading** — the openness, not the minimalism, is what makes it viable |
| **Minimal Phone MP01** ($499) | Active (Indiegogo-origin) | E Ink + physical QWERTY, but **full Android 14 + Google Play** — friction via display, not restriction | "High-quality boring" — friction by making consumption unpleasant, while retaining escape hatches |
| **Punkt MP02** | Still sold, aging (2018 design) | Voice/SMS only + tethering | Design icon; sluggish UI and price are chronic complaints |
| **Boox Palma 2 / 2 Pro** | Active; Palma 2 Pro (Android 15) adds cellular | E-reader co-opted as de facto dumbphone: full Android + Play Store on E Ink | Android Authority: "the most realistic" minimalist phone. Pattern: users prefer *full capability behind an unpleasant display* to hard restriction |

**Hardware takeaway:** the market converged on E Ink + Android-with-escape-hatches; pure restriction devices get abandoned as second phones.

## 2. Android launchers

- **Olauncher** — GPL-3.0, text-only home. Upstream on **indefinite hiatus**; the living lineage is forks, notably **mLauncher (DroidWorksStudio)**, actively maintained on F-Droid. Still the reference FOSS minimal launcher.
- **Niagara Launcher** — active, polished, freemium, **closed source**. Wellbeing-adjacent but not a dedicated wellbeing product.
- **Siempo** — the closest prior art to MindAnchor: "smartphone interface for mental health," intention-setting home screen, notification batching, app renaming/greyscale. Open-sourced under **GPL-3.0** (`Get-Siempo/siempo-android-launcher`) when the company wound down; **effectively dead since ~2019–2020**. Salvageable ideas, unsalvageable Java codebase.
- **Blloc Ratio** — company collapsed ~2023; closed source; "monochrome tree + integrated messaging" vision defunct. Cautionary tale: deep OS-like launcher integration is expensive to maintain.
- **iOS "dumbphone launchers"** — **Blank Spaces** (subscription; embodied friction: push-ups-to-unlock, "touch grass" GPS unlock, breathing), **Dumbify**, **Minimalist – Dumb Phone**. All proprietary subscription apps atop widgets + Screen Time API.
- Other maintained FOSS building blocks: **Kvaesitso** (search-first FOSS launcher), **Fossify Launcher**, **Unlauncher**.

## 3. Screen-time / blocker apps — SOTA friction mechanisms

- **one sec** — the evidence leader: PNAS 2023 field experiment (36% of intercepted opens abandoned; −37% open attempts by week 6); CHI 2024 longitudinal (n=1,039) confirms durable effect. Mechanism: breathing pause + intention prompt.
- **ScreenZen** — free, configurable delays (to 30s), intention prompts, per-session limits. Repeatedly the "best free" pick.
- **Opal** — hard/soft blocking sessions, "Focus Score" gamification, subscription; enforcement over friction.
- **Clearspace** — cognitive-load friction (breathing, exercise, counting-backwards) + open budgets.
- **Jomo** — iOS/Mac blocking, rules engine, social accountability.
- **Brick** (one-time purchase, iOS + Android since Sept 2025) and **Unpluq** (subscription) — **physical NFC friction**: unlock requires tapping a tag deliberately left elsewhere. Distance-based friction is the current SOTA for defeating "just this once" overrides; **Foqos** is a FOSS (iOS) NFC-blocking alternative.
- **Freedom** — active veteran, cross-platform VPN/filter blocking, no novel friction.
- Known failure mode across all: **habituation** — fixed delays lose potency in weeks; escalating/adaptive friction is mostly unexplored in shipping products.

## 4. Platform features

- **Android 15/16:** **Notification Cooldown** (progressively lowers volume for rapid-fire notifications), improved **Modes** (multiple custom modes, per-mode schedules + notification filters), Focus Mode app pausing, per-app screen-time reminders. Digital Wellbeing itself is stagnant and **closed source**.
- **iOS 18–26 / Apple Intelligence:** **Notification summaries** (on-device LLM, throttled in 18.3 after hallucinated news summaries — a cautionary tale), **Priority Notifications** (on-device importance ranking), **Reduce Interruptions Focus**. Screen Time API (FamilyControls/DeviceActivity) is the only sanctioned third-party hook — which is why iOS blockers are all shaped the same.
- Nobody at platform level ships mood/state-aware behavior; everything keys off content importance, not user condition.

## 5. OS/ROM attempts & FOSS building blocks

- **No serious mental-health AOSP fork exists.** Closest: MuditaOS (FreeRTOS one, feature-phone scope), dead XDA hobby ROMs, discontinued ProtonAOSP. GrapheneOS forums explicitly punt wellbeing to the launcher/app layer. **This layer is empty.**
- **Google Creative Lab digital-wellbeing-experiments-toolkit** (Apache-2.0): open-sourced experiments — Paper Phone, Envelope, **Post Box (notification batching)**, Morph, Desert Island — abandoned but directly reusable concepts/code. https://github.com/googlecreativelab/digital-wellbeing-experiments-toolkit
- **FOSS building blocks:** **Mindful** (`akaMrNagar/Mindful`, GPL-2.0, Flutter) — blocking + notification batching/scheduled delivery, offline, F-Droid; **Bundel** (Kotlin notification-batching prototype); **DigiPaws** (GPL, gamified blocking); **Open TimeLimit** (GPL, category schedules); **ActivityWatch Android** (MPL-2.0, local usage tracking); **TrackerControl**; **Fossify** suite for de-Googled basics.

## 6. FDA-cleared digital therapeutics (what "regulatory-grade" looks like)

- **Rejoyn** (Otsuka/Click, FDA 510(k) Apr 2024; first prescription digital therapeutic for MDD): 6-week CBT + "Emotional Faces Memory Task" cognitive-emotional training, adjunct to meds, RCT-backed (effect sizes questioned; went free in the US in 2025).
- **DaylightRx** (Big Health, cleared Sept 2024): first FDA-cleared GAD treatment; 90-day CBT (cognitive restructuring, applied relaxation, stimulus control).
- **EndeavorRx** (Akili): De Novo 2020 game-based ADHD treatment; Akili pivoted then was acquired (2024) — the *business* model failed even where clearance succeeded; Pear Therapeutics' 2023 bankruptcy is the same story. 2025 CMS digital-mental-health CPT codes are the new hope.
- **Regulatory-grade =** fixed-duration protocolized intervention, RCT with prespecified endpoints, locked software version, adverse-event reporting, prescription gating — the opposite of iterative launcher UX. Lesson: clearance ≠ adoption; reimbursement plumbing is the bottleneck.

## Gap analysis — what nobody ships that research supports

1. **OS-level notification batching by default.** RCT support (Fitz 2019); Apple/Google ship only importance-ranking, not delivery-scheduling. Only Mindful/Bundel do it app-side, crudely. An open launcher + NotificationListenerService batcher with sender-aware exceptions is unclaimed territory.
2. **Adaptive/escalating friction.** Every product uses fixed delays; habituation is the documented failure mode. Nobody ships friction that scales with usage, time of day, or recent relapse.
3. **State-aware intervention.** No product conditions on inferred user state (sleep, typing dynamics, usage-pattern anomalies) to change phone behavior. Privacy-preserving on-device inference + FOSS auditability is the credible way to do it.
4. **The whole-OS layer is vacant.** Between proprietary hardware and launcher apps, no maintained FOSS Android configuration ("mental-health-first OS") exists — Siempo died proving demand, not absence of it.
5. **Evidence-grade open tooling.** one sec is the only consumer product with peer-reviewed effect sizes; no FOSS project instruments itself for evaluation (local telemetry + opt-in study export would be novel).
6. **Physical/embodied friction on FOSS Android.** NFC-tag unlocking (Brick/Unpluq) and embodied unlocks (push-ups, "touch grass") are proprietary and mostly iOS; trivial to replicate openly.
7. **Graceful capability, not amputation.** Restriction devices get abandoned over 2FA/maps/rideshare gaps; research-supported design is *tiered access with friction*.

## Key sources

Engadget Light Phone III review · Mudita open-source status & Kompakt 6-month review · Good e-Reader Minimal Phone review · Android Authority Palma 2 Pro · Olauncher GitHub · Siempo GitHub (`Get-Siempo/siempo-android-launcher`) · one sec PNAS 2023 (https://www.pnas.org/doi/10.1073/pnas.2213114120) · CHI 2024 frictions study (https://dl.acm.org/doi/full/10.1145/3613904.3642370) · grayscale RCT (https://journals.sagepub.com/doi/10.1177/20501579231212062) · Brick/Unpluq/Foqos reviews · Apple notification summaries/priority docs · Android 16 Notification Cooldown coverage · Google DW experiments toolkit · Mindful GitHub (https://github.com/akaMrNagar/Mindful) · ActivityWatch F-Droid · Rejoyn/DaylightRx FDA clearances · PDT landscape analyses.
