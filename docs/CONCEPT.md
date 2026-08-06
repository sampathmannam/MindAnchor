# MindAnchor OS — A Research-Backed, Mental-Health-First Mobile Operating System

> Concept & feature ideation document. Every feature below is tied to published research
> (citations at the bottom). This is the "why + what" document; implementation planning
> comes later.

---

## 1. Vision

Modern smartphone OSes are optimized for engagement — variable rewards, infinite feeds,
interruptive notifications. MindAnchor inverts the objective function: **the OS succeeds
when the user's attention, sleep, mood, and relationships improve**, not when screen time
goes up. Every subsystem — launcher, notifications, display, apps, sensors — is designed
from peer-reviewed mental-health and human-computer-interaction research.

**Design principles**

1. **Calm by default** — the OS never manufactures urgency it didn't receive.
2. **Friction where it heals, none where it hurts** — micro-frictions on compulsive loops,
   zero friction on reaching a human or a coping tool.
3. **Evidence or it ships behind a flag** — features cite the study they operationalize.
4. **Privacy as therapy's precondition** — all mood/sensor inference on-device; nothing
   leaves the phone without explicit, revocable consent.
5. **Autonomy, not paternalism** — the OS suggests and defaults; the user always overrides.

---

## 2. Platform strategy (how to actually build it)

| Tier | What it is | Effort | Reach |
|------|------------|--------|-------|
| **Tier 1: Launcher + suite** | Android launcher, notification listener, accessibility service, DND controller | Weeks–months | Any Android phone — best MVP |
| **Tier 2: AOSP fork ("ROM")** | Custom Android build (like GrapheneOS/CalyxOS) with deep hooks into SystemUI, notification ranking, display pipeline | Months–years | Pixel-class devices, enthusiasts |
| **Tier 3: Dedicated device** | Minimal-phone hardware (e.g. e-ink) running Tier 2 | Long-term | Niche, premium wellness market |

**Decision: MindAnchor is Tier 1.** It ships as a simple, open-source Android app suite —
no custom ROM, no rooting, installable on any normal Android phone. Android exposes
almost everything needed (launcher role, NotificationListenerService, UsageStatsManager,
AccessibilityService, DND policy access) without forking the OS. Tiers 2–3 stay on the
long-term roadmap only.

### 2.1 Open source & simplicity commitments

- **License**: GPLv3 (keeps forks open — fitting for a project whose whole premise is
  "no hidden engagement incentives"). All research citations and design rationale live in
  the repo, so "research-backed" is auditable, not marketing.
- **Distribution**: F-Droid first (FOSS-only store), plus GitHub releases APK; Play Store
  later if policies allow the accessibility/notification permissions.
- **Zero backend**: no accounts, no server, no analytics, no network permission in the MVP.
  All data (usage stats, mood check-ins) stays in a local encrypted database with local
  export. This makes the privacy promise structural, not contractual.
- **Simple stack**: single Kotlin app, Jetpack Compose UI, Room for storage, WorkManager
  for the notification-batch scheduler. No ML, no cloud, no SDKs. A contributor should be
  able to read the whole codebase in an afternoon.
- **Simple product**: launch with the 4 MVP features in §5 and nothing else. Every later
  feature must cite a study in its PR description to be merged — the contribution bar is
  part of the project's identity.

---

## 3. Core OS subsystems, each research-backed

### 3.1 Attention & Notification Architecture

**A. Batched notifications (default 3×/day).**
The single best-evidenced intervention in this space: Fitz et al. (2019) randomized 237
people and found notifications batched to three times per day improved attention and mood
and lowered stress vs. as-they-arrive delivery — while *disabling* them entirely increased
anxiety/FoMO. So: batch, don't block. Calls and designated humans always break through.

**B. Sender-tiered interruption budget.**
Interruptions carry a real cognitive cost — task interruption studies show it takes on the
order of 20+ minutes to fully re-engage after an interruption (Mark et al., 2008). Each app
gets an *interruption budget*; humans > groups > apps > marketing. Marketing/engagement
notifications (detected on-device by a small classifier) are never allowed to buzz — they
land silently in a digest.

**C. No badges, no red dots.**
Color and badge cues are engineered triggers for checking habits (Eyal's "hook" model;
checking-habit research by Oulasvirta et al., 2012 shows brief compulsive checking sessions
are habit-driven, cue-triggered). MindAnchor's launcher has no numeric badges; a single
neutral "digest ready" cue appears at batch time.

**D. Attention receipts.**
Weekly, the OS shows *who bought your attention*: "TikTok interrupted you 84 times; your
sister twice." Self-monitoring alone measurably changes behavior (self-monitoring is a core
component of effective behavior-change interventions; Michie et al., 2009 meta-analysis).

### 3.2 The Anti-Compulsion Launcher

**A. Intention prompt on app open ("What are you here to do?").**
A one-screen pause before opening a flagged app. A large PNAS field experiment (Grüning et
al., 2023, with the "one sec" app; n ≈ 280k app openings) showed a brief friction screen
reduced problematic app openings by ~37% and users kept it voluntarily — friction works and
is tolerated when self-chosen.

**B. Time-boxed sessions with graceful exits.**
User states "10 minutes of Instagram"; at expiry the app fades to grayscale and shows one
tap: "Done" / "+5 min (this is your 2nd extension today)." Implementation-intention
research (Gollwitzer & Sheeran, 2006 meta-analysis, d ≈ .65) shows pre-committed if-then
plans dramatically improve follow-through vs. in-the-moment willpower.

**C. Grayscale as a first-class display mode.**
Grayscale reduces smartphone screen time in randomized trials (Holte & Ferraro, 2020;
Grüning et al. 2022 found ~ 20–40 min/day reductions) with essentially zero side effects.
MindAnchor: feeds and flagged apps render grayscale by default; camera, maps, photos stay
in color. At Tier 2 (AOSP) this is per-surface, not global.

**D. Text-first launcher, no app grid dopamine wall.**
Home screen is a typed/spoken intent field + 4 chosen tools (à la minimalist launchers).
Search-first launching removes cue exposure to icon triggers (cue-reactivity logic borrowed
from addiction research: remove the cue, weaken the loop).

**E. Infinite-scroll interruption.**
At Tier 2, the OS can detect continuous scroll gestures > N minutes in engagement-classed
apps and inject a gentle full-screen breather ("You've scrolled 1,200m today — still
finding what you came for?"). Rooted in "meaningful friction" and just-in-time adaptive
intervention (JITAI) frameworks (Nahum-Shani et al., 2018).

### 3.3 Circadian & Sleep Subsystem

Sleep is the highest-leverage mental-health variable the OS controls. Poor sleep is
bidirectionally causal with depression and anxiety; improving sleep improves mental health
(Freeman et al., 2017, OASIS randomized trial of digital CBT-I in *Lancet Psychiatry*:
treating insomnia reduced paranoia and hallucinations, mediated depression/anxiety
improvements; Scott et al., 2021 meta-analysis: improving sleep quality causally improves
composite mental health, dose-response).

- **Sunset mode**: from a user-set wind-down hour, the whole OS shifts — warm dim display,
  notifications fully batched to morning, feeds locked (one-tap override), only calls +
  alarm + designated humans. Bedtime phone use and short sleep are consistently associated
  in adolescents (Carter et al., 2016 JAMA Pediatrics meta-analysis: bedtime device access
  → ~2× odds of inadequate sleep).
- **Built-in digital CBT-I program** as a system app (sleep-restriction scheduling, stimulus
  control, sleep diary auto-filled from phone-use signals). Digital CBT-I has strong RCT
  evidence (Espie et al., 2019 "Sleepio" trials; Freeman et al., 2017).
- **True bedtime lockout of "engagement-class" apps** with a deliberately slow override
  (20-second hold), preserving autonomy while defeating impulsivity — impulse purchases of
  attention, like snacking, drop sharply with even short delays (delay-discounting /
  commitment-device literature).
- **Morning protection**: no feed access for the first N minutes after wake (user-set);
  alarm dismissal screen offers 60-second light stretch/breathing instead of the feed.

### 3.4 Mood-Aware OS (Digital Phenotyping, strictly on-device)

The phone passively senses behavior that predicts mood: typing dynamics, GPS entropy
(fewer places visited correlates with depressive symptoms — Saeb et al., 2015), sleep
regularity, call/text sociality, accelerometer activity. This is *digital phenotyping*
(Onnela & Rathmell; Insel, 2017, JAMA).

- **Weekly "state of you" report**: sleep regularity, movement, sociality, focus — trends,
  not scores. Framed as reflection, not diagnosis.
- **Change-point alerts**: "Your sleep has shifted 2h later and you've left home half as
  often over the last 10 days — want to check in?" Behavioral change-points precede
  self-reported mood deterioration in student studies (StudentLife, Wang et al., 2014).
- **Optional EMA check-ins**: 1-tap mood sampling (Ecological Momentary Assessment is the
  research gold standard for in-context mood measurement; Shiffman et al., 2008), scheduled
  at low-interruption moments.
- **Everything on-device.** Models run locally; raw sensor data never uploaded. Clinician
  sharing is opt-in, scoped, and revocable. (Privacy isn't just ethics — perceived
  surveillance itself elevates stress.)

### 3.5 Micro-Intervention Layer (JITAI engine)

A system service that delivers the *right tool at the right moment* — the just-in-time
adaptive intervention framework (Nahum-Shani et al., 2018):

- **Panic/grounding button** on lock screen: one press → paced breathing at ~6 breaths/min
  (slow-paced breathing lowers state anxiety; meta-analysis Zaccaro et al., 2018) or 5-4-3-2-1
  grounding. Zero friction to coping tools.
- **Behavioral-activation nudges**: when the OS detects a low-movement, low-sociality
  stretch, it suggests one tiny scheduled activity ("text Ana?" "10-min walk at 4pm?").
  Behavioral activation is as effective as CBT for depression in the COBRA trial (Richards
  et al., 2016, *Lancet*).
- **Micro-CBT moments**: 30–60s thought-reframing or savoring exercises offered (never
  forced) at transition moments. Brief app-based interventions show small-to-moderate
  effects on depressive symptoms (Linardon et al., 2019 meta-analysis of mental-health
  apps).
- **Crisis protocol**: hard-wired, always-available escalation — local crisis line (e.g.
  988 in the US, iCall/Tele-MANAS 14416 in India) reachable from any screen; safety-plan
  card (Stanley & Brown safety planning intervention — reduces suicidal behavior, Stanley
  et al., 2018) stored on-device and surfaced automatically if crisis-adjacent searches or
  app states are detected. The OS never plays therapist in a crisis; it connects to humans.

### 3.6 Social Connection Subsystem

Loneliness rivals smoking as a mortality/mental-health risk factor (Holt-Lunstad et al.,
2010 meta-analysis). *Active* social media use relates to higher well-being while *passive*
scrolling relates to lower well-being (Verduyn et al., 2017 review).

- **People-first UI**: the communication hub is organized around ~15 chosen humans, not
  apps. "Talk to Mom" is a first-class OS intent; the app used is an implementation detail.
- **Passive-consumption meter**: per social app, the OS distinguishes creating/messaging
  time vs. lurking time and reports the ratio — nudging toward active use.
- **Reconnection prompts**: "You and Priya used to talk weekly; it's been 6 weeks — call
  her?" (Experimental work shows people underestimate how much reaching out is
  appreciated — Liu, Rim, Min & Min, 2022/2023, JPSP "surprised by the gratitude of
  reconnection".)
- **Call-over-text defaults**: voice conveys connection cues text strips out (Kumar &
  Epley, 2021: voice communication creates stronger social bonds than text with no added
  awkwardness).

### 3.7 App Store with an Evidence Bar

- **Engagement-pattern labels**: every listed app is statically/behaviorally audited for
  dark patterns — infinite scroll, autoplay, variable-reward mechanics, streak coercion —
  and labeled like nutrition facts ("attention facts panel").
- **Evidence tiers for wellness apps**: most mental-health apps have no evidence and poor
  privacy (Larsen et al., 2019 review of app-store claims); MindAnchor's store ranks
  RCT-backed tools first and requires privacy disclosures machine-checked against actual
  network behavior.
- **Default suite**: CBT-I, mood journal, breathing, behavioral-activation planner — all
  open-source system apps, no accounts required.

### 3.8 Display, Sound & Aesthetics of Calm

- **Warm, low-arousal design language**: no red accents for non-emergencies (red increases
  arousal/avoidance motivation — Elliot & Maier's color-in-context research), muted
  palette, generous whitespace, slow easing curves.
- **Notification sounds**: designed non-startling (rising, harmonic, < 65 dB equivalents);
  distinct "human vs. machine" sound classes so the nervous system learns most buzzes are
  ignorable.
- **Nature micro-exposures**: lock screen rotates slow nature scenes; even brief nature
  imagery/micro-breaks measurably aid attention restoration and stress recovery (Attention
  Restoration Theory, Kaplan; Lee et al., 2015 40-second green-roof micro-break study).
- **No autoplaying motion anywhere in SystemUI.**

### 3.9 Work/Life Boundary Engine

- **Context profiles** (Work / Home / Sacred hours) that swap the entire launcher, app
  availability, and notification policy — because boundary-blurring telepressure predicts
  burnout and poor sleep (Barber & Santuzzi, 2015).
- **Email/Slack quiet hours honored at OS level**, with auto-responders offered.
- **Focus sessions** integrate Pomodoro-style blocks with full notification suppression;
  even a silent phone *visible on the desk* reduces available cognitive capacity (Ward et
  al., 2017 "brain drain"), so focus mode prompts "flip phone face-down / dock it" rituals.

### 3.10 Measurement, Safety & Ethics Layer

- **Outcome dashboard, not engagement dashboard**: the OS's own KPIs are user-visible —
  sleep regularity, pickups, mood trend, time-in-flow. If MindAnchor features don't move
  them for you, the OS says so and offers to turn features off (honest null results).
- **Clinical guardrails**: mood features carry "not a medical device" framing; PHQ-9/GAD-7
  style screeners (validated instruments — Kroenke et al., 2001; Spitzer et al., 2006) are
  offered only with context and warm handoff to care resources; item-9-type risk responses
  trigger the crisis protocol.
- **Research mode**: opt-in, IRB-partnered data donation to validate features; every
  feature ships with a pre-registered hypothesis ("batching → ↓ perceived stress at 4
  weeks") and an in-OS A/B framework so the "research-backed" claim stays honest
  post-launch.
- **Vulnerable-population modes**: adolescent mode (stricter defaults, aligned with
  bedtime-device evidence), grief/anniversary sensitivity (photo "memories" features are
  opt-in, never ambush), addiction-recovery mode (blocklists with sponsor-style
  accountability contacts).

---

## 4. What NOT to build (evidence says no)

- **Blanket notification blocking** — increases anxiety/FoMO vs. batching (Fitz et al., 2019).
- **Screen-time shame** — scare-metrics and streak-guilt mirror the coercion loops we're
  removing; framing effects matter, and total screen time is a weak well-being predictor
  anyway (Orben & Przybylski, 2019: screen-time/well-being associations are tiny; *how*
  and *when* matter more than *how much*).
- **Chatbot-as-therapist in crisis flows** — route to humans; automation stops at triage.
- **Gamified wellness (points/streaks/leaderboards)** — reintroduces extrinsic-reward
  compulsion into the exact place intrinsic motivation is needed (Self-Determination
  Theory, Ryan & Deci: autonomy-supportive framing beats controlling framing for sustained
  behavior change).
- **Blue-light filtering as a headline feature** — evidence for "night shift"-style filters
  improving sleep is weak/mixed; the *content and timing* of use matters more than the
  spectrum. Ship warm display for comfort, but hang the sleep claims on CBT-I, wind-down,
  and batching instead.

---

## 5. Suggested MVP (Tier 1 launcher, ~4 features)

1. **Batched notifications** with human-breakthrough list (Fitz et al. design, 3×/day default).
2. **Intention-prompt + time-boxed sessions** for user-flagged apps (one-sec/PNAS design).
3. **Sunset mode** (wind-down hour: grayscale + batch-until-morning + feeds locked).
4. **Weekly attention receipt** (self-monitoring report).

Each maps to a single strong study, is buildable with public Android APIs
(NotificationListenerService, UsageStatsManager, AccessibilityService, launcher role), and
is measurable with an in-app PSS-4 / WHO-5 pulse survey for honest before/after data.

All four fit in one simple open-source Kotlin app with zero servers — see §2.1. The
"operating system" framing is the product vision; the codebase starts as a launcher +
notification service that *behaves* like a calmer OS.

---

## 6. State of the art & novel features (from the 2024–2026 SOTA survey)

Full literature and landscape reports live in [`docs/research/`](research/):
[01 sensing & JITAI](research/01-sensing-and-jitai-sota.md) ·
[02 attention design](research/02-attention-design-sota.md) ·
[03 products & OSS landscape](research/03-products-and-oss-landscape.md) ·
[04 AI frontier](research/04-ai-frontier-sota.md).

### 6.1 Where the field actually is

- **The "mental-health OS" layer is vacant.** No maintained FOSS wellbeing launcher exists
  (Siempo, the closest prior art, died ~2020 — its GPL code is on GitHub); no serious
  mental-health AOSP fork exists; Apple/Google ship importance-ranking, not
  delivery-scheduling, and nothing state-aware.
- **The strongest causal evidence** is for: sender-tiered notification *batching* (Fitz
  2019 — while *disabling* notifications failed twice: Dekker 2024 "Beyond the Buzz" null
  + FoMO increase; "Sound of Silence" 2022 showed muting harms high-FoMO users);
  app-open friction (one sec, PNAS 2023 + CHI 2024 n=1,039 longitudinal); and gating
  mobile *internet content while preserving calls/messages* (Castelo, PNAS Nexus 2025:
  −0.57 SD mental-health symptoms — larger than typical antidepressant meta-analytic
  effects).
- **Cross-person mood prediction does not generalize** (Müller 2021: AUC 0.82 → 0.57 on a
  diverse sample). Per-user, on-device anomaly detection against one's own baseline is
  the only defensible sensing design (mindLAMP relapse work, npj Schizophrenia 2023).
- **Habituation is the universal failure mode** — fixed delays, fixed nudges, and goal
  reminders all decay (HeartSteps, Sense2Stop, Lyngs CHI 2020). Adaptive timing beats
  fixed and random schedules (DIAMANTE RL trial, JMIR 2024; Oralytics, deployed RL).
- **Sleep regularity (SRI), not duration, is the strongest phone-derivable target**
  (UK Biobank ~60k: top-quintile regularity → 20–48% lower all-cause mortality; regularity
  predicts lower incident depression/anxiety).
- **AI therapy is validated only under human supervision** (Therabot NEJM AI 2025 RCT) and
  unsafe unsupervised (Stanford 2025: ~20% unsafe responses; Illinois banned AI-delivered
  therapy). On-device small LLMs (Gemma 3n, Llama 3.2 1B) are, however, feasible today
  for private summarization/reflection.

### 6.2 Novel features nobody ships (each research-backed)

1. **Default sender-tiered notification batching at the launcher level.** The
   best-evidenced intervention in the field, and literally unclaimed product territory —
   only crude app-side batchers (Mindful, Bundel) exist. Humans pass instantly; machines
   wait for the next batch window. (Fitz 2019; Mehrotra: sender relationship is the
   strongest acceptance predictor.)
2. **Adaptive anti-habituation friction.** Every shipping blocker uses fixed delays that
   lose potency in weeks. MindAnchor's friction *escalates with recent overuse and
   time-of-night, varies its content, and rests when you're doing fine* — a bandit
   algorithm (Oralytics pattern) decides when friction is worth spending. (HeartSteps
   decay; Scrolling in the Deep CHI 2025: context-blind prompts desensitize.)
3. **Per-user baseline "anchor score" — on-device anomaly detection, not diagnosis.**
   Sleep-window regularity from screen-state, typing-rhythm metadata (BiAffect pattern —
   how you type, never what), movement and sociality vs. *your own* trailing baseline.
   Deviations trigger a gentle check-in, nothing else. (npj Schizophrenia 2023: anomalies
   2.12× more frequent pre-relapse; Müller 2021 forbids cross-person models.)
4. **Sleep Regularity Index as the headline metric.** No consumer product leads with SRI;
   the evidence says it should be the number on the dashboard, with the launcher
   enforcing a consistent wind-down/wake window rather than counting hours.
5. **Goal-elicitation onboarding that configures the OS per-user.** ReDD workshop finding
   (CHI 2024 Best Paper HM): matching tool to individual struggle beats any single tool;
   imposed minimalism fails, self-endorsed structure works ("Going Light," CHI 2026).
   Onboarding = articulate your struggles → the OS assembles your feature set.
6. **"Castelo mode": internet-content fasting that preserves communication.** One-tap
   scheduled blocks of mobile-internet content (feeds, browsers) while calls/SMS/maps
   stay live — replicating the exact mechanism of the strongest 2025 trial, which
   full-abstinence products get wrong (moderation beats detox; Brailovskaia).
7. **Embodied & physical friction, open-sourced.** Breathing-paced app-open gates
   (haptic-guided, starting from your natural rate — Breathm CHI 2024), and NFC-tag
   unlocking ("your feed lives in the kitchen drawer" — Brick/Unpluq mechanism, currently
   proprietary-only).
8. **On-device LLM digest, private by construction.** Gemma 3n/Llama 3.2 1B summarizes
   each notification batch into three calm sentences and reflects journaling back —
   no cloud, auditable FOSS. (Scoped as wellness, never therapy: crisis keywords route
   to 988/Tele-MANAS humans.)
9. **Self-evaluating OS.** No FOSS wellbeing project instruments itself for evidence.
   MindAnchor ships local telemetry (ActivityWatch-style), per-user n-of-1 experiments
   (features A/B themselves against *your* WHO-5/PSS-4 pulse), and opt-in anonymized
   study export — making "research-backed" a live property, not a launch claim.
10. **An "attention-capture damaging patterns" firewall.** Use the CHI 2023 ACDP typology
    (11 patterns: infinite scroll, autoplay, pull-to-refresh, time fog…) as a requirements
    checklist: per-app scorecards in the launcher, and countermeasures (scroll-session
    breathers, autoplay kill, scheduled feed access) applied per pattern.

### 6.3 Evidence-driven corrections to v1 of this document

- **Drop stats-only dashboards as a core feature** — self-monitoring alone is the weakest
  intervention class (TOCHI 2023 meta-analysis). Attention receipts stay, but demoted to
  supporting cast.
- **Never ship blanket mute/DND as a wellbeing feature** — it backfires for high-FoMO
  users. Batching with human-breakthrough only.
- **Expect small average effects and design for heterogeneity** — app-based interventions
  average g≈0.28 (Linardon 2024, 176 RCTs); the Delphi consensus (2025) supports
  sleep/attention/compulsion harms, not "screens cause depression." Market mechanisms,
  measure per-user, de-escalate when someone's data shows a feature isn't helping.

## 7. Key citations

- Fitz, N., Kushlev, K., et al. (2019). Batching smartphone notifications can improve well-being. *Computers in Human Behavior*.
- Grüning, D. J., et al. (2023). Directing smartphone use through the self-nudge app one sec. *PNAS*.
- Gollwitzer, P. M., & Sheeran, P. (2006). Implementation intentions and goal achievement: meta-analysis. *Advances in Experimental Social Psychology*.
- Holte, A. J., & Ferraro, F. R. (2020). True colors: grayscale setting reduces screen time. *Computers in Human Behavior* (see also Grüning et al., 2022).
- Carter, B., et al. (2016). Association between portable screen-based media device access and sleep outcomes. *JAMA Pediatrics*.
- Freeman, D., et al. (2017). Effects of digital CBT for insomnia on mental health (OASIS). *Lancet Psychiatry*.
- Scott, A. J., et al. (2021). Improving sleep quality leads to better mental health: meta-analysis of RCTs. *Sleep Medicine Reviews*.
- Espie, C. A., et al. (2019). Digital CBT-I (Sleepio) RCTs. *JAMA Psychiatry* and prior.
- Saeb, S., et al. (2015). Mobile phone sensor correlates of depressive symptom severity. *JMIR*.
- Wang, R., et al. (2014). StudentLife: assessing mental health via smartphones. *UbiComp*.
- Shiffman, S., et al. (2008). Ecological momentary assessment. *Annual Review of Clinical Psychology*.
- Nahum-Shani, I., et al. (2018). Just-in-time adaptive interventions (JITAIs). *Annals of Behavioral Medicine*.
- Zaccaro, A., et al. (2018). How breath-control can change your life: slow breathing meta-analysis. *Frontiers in Human Neuroscience*.
- Richards, D. A., et al. (2016). Behavioural activation vs CBT for depression (COBRA). *The Lancet*.
- Linardon, J., et al. (2019). Efficacy of app-supported smartphone interventions: meta-analysis. *World Psychiatry*.
- Stanley, B., et al. (2018). Safety planning intervention and suicidal behavior. *JAMA Psychiatry*.
- Holt-Lunstad, J., et al. (2010). Social relationships and mortality risk: meta-analysis. *PLOS Medicine*.
- Verduyn, P., et al. (2017). Do social network sites enhance or undermine subjective well-being? *Social Issues and Policy Review*.
- Kumar, A., & Epley, N. (2021). It's surprisingly nice to hear you. *JEP: General*.
- Ward, A. F., et al. (2017). Brain drain: mere presence of one's smartphone. *JACR*.
- Barber, L. K., & Santuzzi, A. M. (2015). Telepressure and employee recovery. *JOHP*.
- Mark, G., et al. (2008). The cost of interrupted work. *CHI*.
- Oulasvirta, A., et al. (2012). Habits make smartphone use more pervasive. *Personal and Ubiquitous Computing*.
- Michie, S., et al. (2009). Effective techniques in healthy eating/physical activity interventions: self-monitoring. *Health Psychology*.
- Orben, A., & Przybylski, A. K. (2019). The association between adolescent well-being and digital technology use. *Nature Human Behaviour*.
- Larsen, M. E., et al. (2019). Using science to sell apps. *npj Digital Medicine*.
- Kroenke, K., et al. (2001). The PHQ-9. *JGIM*; Spitzer, R. L., et al. (2006). GAD-7. *Archives of Internal Medicine*.
- Lee, K. E., et al. (2015). 40-second green roof views sustain attention. *Journal of Environmental Psychology*.
- Ryan, R. M., & Deci, E. L. (2000). Self-determination theory. *American Psychologist*.
