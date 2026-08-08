# SOTA Feature-Gap Analysis for MindAnchor v1 — Research Brief

**Audience:** MindAnchor maintainers (open-source Android mental-health launcher).
**Source:** Research agent commissioned to compare MindAnchor's locked v1 feature set against the 2025–2026 evidence base, the published competitor landscape, and the design record in `docs/research/07`.

**Method:** Primary citations only (peer-reviewed papers, official product docs, RCT protocols). Where a feature is *already* best-in-class for MindAnchor, that is stated honestly before the gap list.

---

## What is already best-in-class in MindAnchor v1

The v1 bones are unusually well-grounded. Three things MindAnchor ships that the literature and the product landscape converge on as SOTA:

- **Sender-tiered notification batching** is the *direct* implementation of the Kushlev 2016 / Fitz 2019 notification-line-of-research (Kushlev, Proulx, Dunn *Psychon Bull Rev* 2016; Fitz, Kushlev, Ariely *Computers in Human Behavior* 2019). One sec, Freedom and Opal all ship some form of batching; none do *sender-tiered* batching with the designated-humans-passthrough pattern, which is MindAnchor's defensible niche.
- **Text-first launcher + intention + breathing-paced delay** is the cleanest current productization of the Adhikari PNAS 2023 friction finding (36% of attempted opens dismissed, 57% reduction in app-opens over 6 weeks) combined with Gollwitzer 1999 *American Psychologist* implementation-intentions research.
- **WHO-5 + safety plan + DBT skills (STOP, TIPP, 5-4-3-2-1)** is a defensible, evidence-anchored skill set per Topp 2015 (WHO-5 validation), Stanley & Brown 2012 (SPI), and Linehan 1993 (DBT).

**These are not the gap.** The gaps are *adjacent* capabilities where a single iteration adds disproportionate defensibility.

---

## Prioritized gap list (8 features, ordered by effort-adjusted impact)

### 1. "Going Light" — Scheduled mobile-internet fasting window (Castelo 2025) — P0 — Effort: M

**What:** A scheduled time window (e.g. 8pm–10pm, or a weekly 24-hour block) during which the launcher and a lightweight Android companion module cut the *mobile-internet* connection (browser, social, YouTube via `VpnService`-based local filtering or the AccessibilityService-based app-block + the launcher routing "http/https" through a captive on-device filter), while leaving SMS/voice and offline apps untouched. Sunset mode gates *notifications*; Going Light gates the *content those notifications link to*.

**Why it works:** Castelo, Kushlev, Ward, Esterman, Reiner, *PNAS Nexus* 2025;4(2):pgaf017, doi:10.1093/pnasnexus/pgaf017. RCT, N = 467, 2 weeks of full mobile-internet block. 91% improved on at least one outcome. Sustained-attention gains ≈ 10 years of age-related decline reversal. **Depression-symptom reductions *larger than the average effect of pharmaceutical antidepressants***. Gains persisted at 4-week follow-up even after the internet was restored. Mechanism: time reallocated to in-person socializing, exercise, nature, and ~18 min more sleep per night.

**Who ships it:** *No consumer app does this on Android in a scheduled, productized way.* Freedom (used in the actual study) is iOS-first and subscription-only. Cold Turkey, Opal, and ScreenZen ship "block apps/sites" but not "scheduled whole-browser-window disconnection." **This is the single biggest open whitespace in the category right now.**

**Buildable in MindAnchor?** **Yes.** VPN-tethered local blocklist (NetGuard/Blokada pattern). Fits GPLv3. No `INTERNET` permission needed; outbound traffic is dropped locally. Effort M because the v1.1 "scheduled internet-content fasting" module is already earmarked in `docs/PLAN.md`; the Castelo paper is the *cite* to add to its design doc and the *promotion to P0*.

**Effort:** M (1–2 dev months).

---

### 2. Bedtime "Open-Loop" release — Scullin-style to-do list + Zeigarnik closure — P0 — Effort: S

**What:** When Sunset mode triggers, before grayscale and DND, surface a 5-minute prompt: *"Write down everything you have to do tomorrow — be specific (time, who, first step)."* The list is stored locally and either shown in the morning digest or "released" in the morning notification batch.

**Why it works:** Scullin, Krueger, Ballard, Pruett, Bliwise, *Journal of Experimental Psychology: General* 2018;147(1):139–146, doi:10.1037/xge0000374. Polysomnography study, N = 57. The to-do-list group fell asleep significantly faster than the completed-activities group. The *more specific* the list, the faster the onset — on average ~9 min gain, comparable to prescription sleep-aid effect sizes. Mechanism: externalization closes the Zeigarnik open-loop so working memory can release the load.

**Who ships it:** No mental-health launcher does this. The best current productization is the "Constructive Worry" technique in CBT-I apps (CBT-I Coach, Sleepio) — but they are full sleep apps, not launchers. **True gap.**

**Buildable in MindAnchor?** **Trivially.** Single screen with a text field, fed by existing goal-elicitation data, surfaced at Sunset trigger. Fits no-backend/no-internet. Reinforces existing SRI tracking.

**Effort:** S (≤ 1 dev week). Highest impact-per-line-of-code on this list.

---

### 3. Self-compassion micro-moments — P1 — Effort: S

**What:** Add 2–3 of Neff's micro-exercises to the existing WHO-5 pulse — 2-minute variants of "Self-Compassion Break" (Neff 2003, *Self and Identity*, doi:10.1080/15298860309027) and "Loving-Kindness to Self" — surfaced as opt-in after the WHO-5, not as an obligatory flow. Also: at the moment of opening a flagged "doomscroll" app, optionally replace the friction screen with a one-breath self-compassion prompt.

**Why it works:**
- Linardon et al. 2020, *J Clin Psychol* meta-analysis of 27 RCTs of smartphone-delivered acceptance/mindfulness/self-compassion apps: psychological distress g = −0.32 (95% CI −0.48 to −0.16), self-compassion g = 0.31 (95% CI 0.07–0.56). PMID 32586436.
- Liu et al. 2023, *Psicologia: Reflexão e Crítica*, doi:10.1186/s41155-023-00276-w — app-guided 4-week LKM in college students: significant increase in self-compassion and positive psychological capital, significant *decrease* in suicidal ideation.
- Zainal & Newman 2024, *JMIR Mental Health* — brief self-guided MEMI increased self-kindness, decreased ER difficulties in social anxiety.

Effect sizes are small-to-moderate; the contribution is *compounding on top of* WHO-5 + STOP/TIPP/5-4-3-2-1, not a replacement.

**Who ships it:** Wysa, Woebot, Youper (AI-CBT chatbots — different modality). Headspace, Calm (longer-form). *No* minimalist launcher / friction app ships brief in-launcher self-compassion micro-moments as a deliberate design pattern.

**Buildable in MindAnchor?** **Yes, very well.** Static text prompts + existing friction screen. No LLM needed (Neff's scripted exercises are public-domain protocol). Fits no-internet. Differentiates MindAnchor from every other launcher in the table that focuses only on *removing* the bad behavior without offering the *replacement* skill.

**Effort:** S (≤ 2 dev weeks including writing the 6–8 micro-prompts in clear, non-clinical language).

---

### 4. NFC tag / Bluetooth physical anchor for Sunset / Deep Focus — P1 — Effort: M

**What:** Let the user program a cheap NFC tag (or Bluetooth beacon like a Tile/Sticker) that, when tapped, instantly arms Sunset mode / a custom friction profile / a "light" session. Conversely, *requires* a physical tag-tap to disarm an active deep-DND window — the same "you have to physically get up" mechanism that makes Brick and Foqos effective.

**Why it works:** Wendy Wood *Good Habits, Bad Habits* (2019) and the Fogg / Lally et al. *Eur J Soc Psychol* 2010 (doi:10.1002/ejsp.674) habit-formation literature all converge on the same point: environmental context cues are the strongest single predictor of behavior repetition. A physical object that lives in a specific place is the strongest possible environmental cue. Plus the PNAS 2025 Castelo mechanism: "your phone becomes a tool again, not a 24/7 buffet" — the *physical* artifact makes that re-framing salient at the moment of decision.

**Who ships it:** **Brick** (paid NFC cube, $99) and **Foqos** (open-source iOS, uses any NFC tag or QR code) are the two most-cited examples. **Unpluq** (paid, $30) and **Blok** ($60) also do variants. On Android specifically, this is *uncommon* — Foqos is iOS-only. MindAnchor would be the first *GPLv3 Android mental-health launcher* to ship this.

**Buildable in MindAnchor?** **Yes.** Android `NfcAdapter` + `ForegroundDispatch` is the standard idiom; Foqos' open-source code is a reference. No backend needed. The physical anchor can also disarm a custom deep-DND profile — a "I really need to sleep" lock that only the bedside-tag can break. Effort M because the Android foreground-dispatch lifecycle is fiddly.

**Effort:** M (≈ 1 dev month). High *defensibility* — the open-source-Android-launcher-with-NFC-anchor niche is empty.

---

### 5. Push-up / movement micro-friction as one of the friction *options* — P2 — Effort: M

**What:** Add an optional "pay for the unlock in reps" mode for a user-selected category of apps (e.g. social). Uses the front camera + ML Kit Pose Detection to count reps (on-device only, no video leaves the device) before allowing the app to open.

**Why it works:** Acute-exercise → craving-reduction literature (Hauck et al. 2020 *Sports Medicine*, review of acute exercise effects on nicotine craving) shows cravings drop *during* a single bout and stay suppressed ~30–50 min after. The mechanism (intense movement displacing an acute craving) is the same one that twenty push-ups at midnight would exploit for smartphone urges.

**Who ships it:** Several indie Android apps ("PushUps" gate apps, "Exercise Lock"). *No* mental-health-focused launcher packages this with the rest of the friction taxonomy.

**Buildable in MindAnchor?** **Yes, with caveats.** ML Kit Pose Detection is on-device, GPL-friendly, no INTERNET. The camera-in-the-launcher UX is the friction; this is also a privacy signal the user has to opt into explicitly. Effort M. **Honestly, this is the lowest-priority item in the list** because the *evidence is mechanistic* (nicotine), the *smartphone-urge* literature is anecdotal, and a *less* gimmicky alternative is to use the existing breathing-paced delay with a longer hold (e.g. 60 s instead of 10 s). Include it as an opt-in preset, don't make it a flagship.

**Effort:** M. Ship as a plug-in *option*, not a default.

---

### 6. Sleep-window-locked deep DND with passcode requirement (a.k.a. "Sleep Lock") — P1 — Effort: S

**What:** When the user's tracked sleep window begins (derived from the existing SRI tracker), arm a system DND *with a mandatory passcode to exit*. On stock Android, the honest path is a MindAnchor-launched Activity that becomes the only one allowed above the lock screen until the wake window — not the ordinary Android Focus mode that you swipe out of.

**Why it works:** The 2024 *Sleep Medicine Reviews* / 2025 sleep-hygiene systematic reviews continue to find that *consistency* of pre-sleep phone behavior is the single strongest modifiable predictor of sleep-onset latency. Apple iOS 16+ ships an analogous "Sleep Focus" but it is trivially dismissible by swipe. The literature is clear that *easy-to-dismiss* DND has near-zero durable effect; *hard-to-dismiss* DND (a slow, deliberate unlock, not a swipe) does.

**Who ships it:** Apple Focus (dismissible), Android Bedtime Mode (dismissible), Samsung Modes (dismissible). *No* shipping app combines a SRI-derived *trigger* with a *non-trivially-bypassable* exit cost. **Also a research gap the field is wide open on.**

**Buildable in MindAnchor?** **Partially.** A launcher cannot enforce system-wide Activity-allowlisting on stock Android without a `DevicePolicyManager` device-owner grant (which is a Setup-Wizard flow the user must consent to). The honest path is: MindAnchor *is* the always-on launch surface during the sleep window and exposes a deliberately slow unlock (e.g. 30-s typing + breath gate). Fits the "high-friction sunset" theme.

**Effort:** S (≤ 1 dev week for the launcher-side version). Move to M if you actually go after `DevicePolicyManager`.

---

### 7. Health Connect–backed adaptive friction (the v1.2 bandit) — P2 — Effort: L

**What:** Pipe read-only Health Connect signals (resting HR, HRV when available, sleep duration) into the v1.2 bandit already planned in `docs/research/16`. The bandit selects *which* of the friction variants (breath, intention, 5-4-3-2-1, mirror, scroll-reflect prompt) to surface on a given app-open attempt. *No new v1.2 work; just add a Health-Connect-vended state feature.*

**Why it works:** HeartSteps v2/v3 (Liao et al. 2020, *Proc ACM Interact Mob Wearable Ubiquit Technol*, doi:10.1145/3381007) and DIAMANTE (Aguilera et al. 2024, *JMIR*, doi:10.2196/60834) are the SOTA references for the *bandit* part: linear Thompson-sampling, daily decision points, contextual features (time of day, day type, recent step count). 2024–2026 *JMIR Mental Health* / *Formative JMIR* JITAI RCTs demonstrate that contextual features including *time-of-day* and *day-of-week* — not HRV — are the dominant predictors of JITAI engagement.

**HRV caveat:** Hovsepian et al. 2025, *Sensors* 25:7147, doi:10.3390/s25237147 — wearable HRV → prior-day stress/mood correlations are largely non-significant *within person* (small-to-moderate, often NS). Treat HRV as a *trend display* in the SRI dashboard rather than a bandit input.

**Who ships it:** HeartSteps (academic), DIAMANTE (academic). *No shipping consumer app uses Health Connect signals to time a friction intervention.* This is real research-backed differentiation.

**Buildable in MindAnchor?** **Yes** for time-of-day + day-of-week + recent sleep duration (the proven signals). **Caveat for HRV:** weak evidence for HRV-driven (vs time-of-day-driven) personalization. Recommend the proven signals first.

**Effort:** L total, but break it into: S for the Health Connect read + trend display; L for the bandit itself (per `docs/research/16`).

---

### 8. Per-app *time-boxed session* with explicit "if-then" plan, not just a delay — P2 — Effort: M

**What:** MindAnchor v1 already has time-boxed sessions + intention + breath gate (F3, F6). Two refinements MindAnchor could ship cheaply:

(a) per-app session *length* (different for Instagram vs Email vs a work app), and
(b) the *implementation intention* in the structured "If I'm about to open X, then I will do Y for Z minutes" form (Gollwitzer 1999 *American Psychologist*), not a free-text "what's your intention" prompt.

**Why it works:** Adhikari & Alessandretti 2023 *PNAS* showed 36% of opens dismissed; the dissipation of effect over the 6 weeks is the well-documented *habituation* problem. Switching the *prompt style* on a per-app basis (Gollwitzer implementation-intention structure) is the cheapest anti-habituation fix. Wysa, Woebot, Moodkit, MoodTools all use the same underlying CBT protocol — MindAnchor does *not* need to add another therapy; it needs to tighten the *one* prompt it already has.

**Who ships it:** ScreenZen ships per-app delays; Wysa / Moodkit ship the structured if-then builder. *No* one ships *both* integrated into a launcher's friction gate.

**Buildable in MindAnchor?** **Yes, trivially.** UX / copy revision more than code. The current free-text intention field becomes a three-field builder: *When am I about to open this? / What will I do? / For how long?* with a soft "specificity nudge" on vague cues. Effort M, mostly in the dialogue/UI work and the per-app config screen.

**Effort:** M (≈ 2 dev weeks). Probably the highest research-vs-effort ratio on this list.

---

## What I deliberately did *not* recommend, and why

- **"Behavioral activation at point of avoidance"** (the v1 item in `07/not-built`): the 2024 *JMIR Mental Health* Santopetro 2024 RCT (doi:10.2196/54252) and the 2025 *JMIR* meta-analysis of digital BA (doi:10.2196/68054) both show small-to-moderate effects on depression that *decay to non-significance at 12 months*. The active ingredient is *daily enjoyable activity scheduling* — which is what MindAnchor's goal-elicitation onboarding already establishes. The novel v1 work is a *real-time avoidance interceptor* that surfaces a saved goal at the moment of opening a flagged avoidance app. The evidence is suggestive but mechanism-light; keep this on the v1.5 backlog, not v1.x.
- **Sensorless phenotyping / within-person anomaly detection:** the *behavioral-sensing* literature is real (Mohr et al. 2017, *JMIR mHealth uHealth*) but the *consumer-grade, on-device, no-internet* version is still research-grade. Too speculative for a v1 feature.
- **A "Going Light" companion mood-detection model:** correctly refused in v1. Hold that line. (Mohr et al. *PLOS Med* 2020 and the 2024 follow-ups on *passively-sensed depression* still have unacceptable false-positive rates for an unattended-launcher use case.)
- **A LLM notification digest (v1.4):** the only honest path to v1.4 is on-device Llama-3-8B-Instruct 4-bit quantized via LiteRT-LM or MediaPipe LLM Inference, ≈ 5 GB on-device model. Technically buildable by 2026, but the *user-experience* of an offline LLM in a 200 MB launcher is bad and the privacy promise is the same as the on-device summary MindAnchor can do with a 1 MB extractive-summarization model. Defer.

---

## TL;DR prioritization

| # | Feature | Effort | Why now |
|---|---------|--------|---------|
| 1 | Going Light (Castelo 2025) | M | Biggest open whitespace in category, v1.1 already planned |
| 2 | Bedtime to-do list (Scullin 2018) | S | Cheapest, highest-citation ROI |
| 3 | Self-compassion micro-moments | S | Distinct from existing WHO-5/STOP/TIPP/5-4-3-1 |
| 4 | NFC physical anchor | M | Foqos/Brick parity on Android open-source |
| 5 | Push-up unlock (optional) | M | Mechanism transfer from nicotine; treat as opt-in |
| 6 | Sleep-window deep DND | S | Closes a real product gap (none do hard-to-dismiss) |
| 7 | Health Connect → bandit | L | v1.2 already planned; *demote HRV, keep sleep duration* |
| 8 | Structured if-then builder | M | Tightens existing feature rather than adding new |

---

## Primary sources cited

- Castelo N, Kushlev K, Ward AF, Esterman M, Reiner PB. *Blocking mobile internet on smartphones improves sustained attention, mental health, and subjective well-being.* PNAS Nexus 2025;4(2):pgaf017. DOI: 10.1093/pnasnexus/pgaf017. https://academic.oup.com/pnasnexus/article/4/2/pgaf017/8016017
- Scullin MK, Krueger ML, Ballard HK, Pruett N, Bliwise DL. *The effects of bedtime writing on difficulty falling asleep.* J Exp Psychol Gen 2018;147(1):139–146. DOI: 10.1037/xge0000374. https://psycnet.apa.org/record/2017-49574-001
- Neff KD. *Self-compassion: An alternative conceptualization of a healthy attitude toward oneself.* Self and Identity 2003;2(2):85–101. DOI: 10.1080/15298860309027. https://www.tandfonline.com/doi/abs/10.1080/15298860309027
- Linardon J, et al. *Can acceptance, mindfulness, and self-compassion be learned by smartphone apps? A systematic and meta-analytic review of RCTs.* J Clin Psychol 2020. PMID 32586436.
- Liu C, et al. *The effects of short video app–guided loving-kindness meditation.* Psicol Reflex Crit 2023;36:32. DOI: 10.1186/s41155-023-00276-w.
- Adhikari A, Alessandretti L, et al. *Directing smartphone use through the self-nudge app one sec.* PNAS 2023;120(2):e2213114120. DOI: 10.1073/pnas.2213114120. https://www.pnas.org/doi/10.1073/pnas.2213114120
- Liao P, Greenewald KE, Klasnja P, Murphy SA. *Personalized HeartSteps: A reinforcement learning algorithm for optimizing physical activity.* Proc ACM Interact Mob Wearable Ubiquit Technol 2020. DOI: 10.1145/3381007. https://dl.acm.org/doi/10.1145/3381007
- Aguilera A, et al. *Results From the Diabetes and Mental Health Adaptive Notification Tracking and Evaluation (DIAMANTE) Study.* JMIR 2024. DOI: 10.2196/60834. https://www.jmir.org/2024/1/e60834
- Hovsepian K, et al. *Resting HRV Measured by Consumer Wearables.* Sensors 2025;25:7147. DOI: 10.3390/s25237147.
- Topp CW, et al. *The WHO-5 Well-Being Index.* Psychother Psychosom 2015;84:167–176. DOI: 10.1159/000376585.
- Gollwitzer PM. *Implementation intentions: Strong effects of simple plans.* American Psychologist 1999;54(7):493–503. https://psycnet.apa.org/record/1999-04333-005
- Kushlev K, Proulx JDE, Dunn EW. *"The High Price of Material Progress": The Toll of a Good Thing.* Psychon Bull Rev 2016 (Kushlev email-frequency study). DOI: 10.3758/s13423-016-1085-7
- Fitz N, Kushlev K, Ariely D. *The effect of smartphone-based interruptions on attention, attitude, and task performance.* Comput Hum Behav 2019.
- Wood W. *Good Habits, Bad Habits.* 2019 (Faber & Faber). Habit-formation curve; "2–3 months" to form a simple habit; double law of habit.
- Lally P, van Jaarsveld CHM, Potts HWW, Wardle J. *How are habits formed: Modelling habit formation in the real world.* Eur J Soc Psychol 2010;40(6):998–1009. DOI: 10.1002/ejsp.674.
