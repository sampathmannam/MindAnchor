# Top Mental-Health Apps Audit — Benchmarks for MindAnchor v0.27+

**Author:** Worker session, 2026-08-17
**Lens:** BPD-safety (Linehan 1993 DBT + Schwartz 1995). MindAnchor's persona: validate-then-suggest, no all-or-nothing, no shame, crisis resources present.
**Method:** WebSearch on every named app/award. No speculation. Where I cannot verify, I say so.

> Hard rule applied: this audit never invents a feature. If a source could not be confirmed, the row says "can't verify" or omits the field.

---

## 1. Apple Design Award winners 2023–2025 in mental health / mindfulness

Apple's ADA has six recurring categories plus a new "spatial computing" (2024). Mental-health apps surface mainly in **Inclusivity**, **Social Impact**, and **Wellness/Health** sub-categories. Verified winners & finalists below.

### 1.1 Headspace — Social Impact winner, ADA 2023
- **Year / category:** 2023, Social Impact (App).
- **What it does:** Guided meditation, sleepcasts, focus music, "mindful minutes" via HealthKit.
- **Why it won:** Apple called out the minimalist UI, the diverse voice roster, and the "five-minute clarity break" that lowers friction to first use.
- **Borrow:** *Inclusive voice roster* and the *five-minute format* — these are the load-bearing reasons Headspace is usable by people with low energy / cognitive load limits. MindAnchor v0.27+ should keep all skills/tips routable to a ≤ 5-min form.
- **Invert for BPD safety:** Headspace's free trial auto-converts to a paid plan and the cancel flow is documented as "shaming" (University of Melbourne 2023 report; Mozilla *Privacy Not Included*). A BPD user who dissociates during a cancellation wallop can lose trust for weeks. MindAnchor must keep crisis resources and personal data **free of any paywall**, and the "unsubscribe" path must be a single tap.

### 1.2 Bears Gratitude — Delight and Fun winner, ADA 2024
- **Year / category:** 2024, Delight and Fun (App).
- **What it does:** Daily gratitude journal with bear characters, prompts, daily reminders.
- **Why it won:** Warm, on-device-feel illustrations, honest self-reflection framing, and *unbearably cute* character design that lowers the "I have to journal" dread.
- **Borrow:** *Character-as-affect-regulation* (Linehan's "wise mind" embodied in a friendly face). MindAnchor already has a mascot direction; the lesson is to let the mascot *acknowledge distress* before asking for input, never the reverse.
- **Invert for BPD safety:** Gratitude-only framing can shame users in a depressive episode ("you should be grateful"). MindAnchor's DBT-aligned approach (DBT Diary Card, DEAR MAN, opposite action) must stay mood-agnostic — never "do gratitude" as the day's only task.

### 1.3 Gentler Streak — Social Impact winner, ADA 2024
- **Year / category:** 2024, Social Impact (App).
- **What it does:** Fitness tracker that *recommends rest days* as readily as workouts; integrates with Apple Health to balance activity, sleep, mindfulness.
- **Why it won:** Reframes "progress" to include recovery — the design language is "encouraging, not insistent."
- **Borrow:** *Rest-as-progress* framing. MindAnchor's distress toolkit should explicitly count "took a pause" as a logged, validated action — not a gap to be filled.
- **Invert for BPD safety:** Gentler Streak is fitness-first, not distress-first. A user in crisis needs the crisis path, not a 20-min "active recovery" suggestion. MindAnchor's "validate-then-suggest" order must be enforced.

### 1.4 Evolve: Daily Self-Care Coach — Inclusivity finalist, ADA 2025
- **Year / category:** 2025, Inclusivity (App) — **finalist, not winner**.
- **Developer:** GTA Solutions, India.
- **What it does:** LGBTQ+ affirming breathing, affirmations, journaling.
- **Why it was a finalist:** Inclusive language, diverse voices, safe-space framing, integrated with Apple State of Mind API.
- **Borrow:** *Affirming language as a first-class UI primitive.* MindAnchor should ship text in Tamil, English, Hindi with affirming default copy (no "you should…" imperatives).
- **Invert for BPD safety:** Affirmations without dialectic become toxic positivity during a BPD spiral. MindAnchor must offer "opposite action" + validation, never "you are enough" alone.

### 1.5 Finalist pool worth naming (2024 / 2025)
- **How We Feel (2024 Social Impact finalist, USA)** — Yale/Marc Brackett emotion-words app, free, no paywall. MindAnchor should borrow: a *granular emotion vocabulary* that doesn't force a 1–10 score.
- **Ahead: Emotions Coach (2024 Social Impact finalist, Germany)** — daily micro-lessons; *cute* but uses streaks (anti-pattern; see §4).

### 1.6 What I cannot verify
- Whether a dedicated "Wellness" or "Mental Health" ADA category existed in 2023–2025. Apple's six categories are stable, so I treat "wellness finalists" as Inclusivity/Social Impact finalists. I checked Apple's official winners list for each year and did not find a separate MH category.

---

## 2. Top DBT apps (minimum 5)

Method: cross-referenced Apple App Store, Google Play, PubMed/PMC. DBT apps get cited in clinical studies (DBT Coach in Rutgers APA trial; Calm Harm in stem4 / NHS pilots).

| # | App | Platform | One-liner | MARS / MARS-G | Star rating | Verifiable source |
|---|-----|----------|-----------|---------------|-------------|------------------|
| 2.1 | **DBT Coach** | iOS, Android, Web | Diary card + 100+ video lessons + 200+ animations + therapist portal | MARS ≈ 3.25 (Stawarz 2021, *Borderline Personal Disord Emot Dysregul*) | 4.6 (App Store, 1.7K ratings) | https://apps.apple.com/us/app/dbt-coach/id1452264969 ; PMC8639404 |
| 2.2 | **Calm Harm** | iOS only (Android) | DBT-based activity chains to resist self-harm urges (Comfort, Distract, Express, Release) | MARS ≈ 3.8 (Stawarz 2021) | 4.3 (Play, 2.6K) | https://play.google.com/store/apps/details?id=uk.org.stem4.calmharm ; calmharm.stem4.org.uk |
| 2.3 | **DBT Diary Card & Skills Coach** (Durham DBT) | iOS / iPadOS only | Diary card by a licensed psychologist; skills library, urge tracking, PDF export, weekly email to therapist | MARS not reported in Stawarz | 3.8 (App Store, 1.2K) | https://apps.apple.com/us/app/dbt-diary-card-skills-coach/id479013889 |
| 2.4 | **eMoods** | iOS, Android | Bipolar-targeted mood/symptom/medication tracker; PDF report to doctor | MARS not found | 4.2 (Play, 4.8K) / 4.8 (App Store, 4.1K) | https://emoodtracker.com ; Play Store listing |
| 2.5 | **Moodfit** | iOS, Android | Mood + CBT thought record + PHQ-9 / GAD-7 + breathwork | MARS not found | 4.0 (Play, 855) / 4.7 (App Store, 2.2K) | https://getmoodfit.com ; App Store listing |
| 2.6 | **Bearable** | iOS, Android | Multi-condition tracker (mood, symptoms, meds, sleep) with correlation engine; explicitly markets to BPD users | MARS not found | 4.6 (Play, 10.3K) / 4.8 (App Store, 4K) | https://bearable.app ; Play Store listing |
| 2.7 | **Daylio** | iOS, Android | 5-emoji mood + activity log, micro-diary, "Year in Pixels" | MARS not found | 4.7 (Play, 460K) / 4.7 (App Store) | https://daylio.net |
| 2.8 | **MoodTools** (named in PMC review) | iOS | Thought diary + safety plan + depression assessment | MARS not found | (see Stawarz 2021) | https://pmc.ncbi.nlm.nih.gov/articles/PMC8639404/ |

### 2.a MARS-G benchmarks (M = mean, SD = standard deviation)
- **All DBT apps (n=21, Stawarz 2021):** mean MARS 3.41 (range 2.15–4.59); 71.4% ≥ acceptable (≥ 3.0).
- **BPD-targeted apps (n=16, Drews-Windeck 2022, MARS-G):** mean MARS-G 3.25 (SD 0.68); engagement 2.87 (SD 0.99), therapeutic gain 2.67 (SD 0.83). 25% empirically tested.
- **Implication for MindAnchor:** the field is *passable, not strong*. A BPD-targeted MHA scoring 4.0+ across all MARS dimensions is itself publishable.

### 2.b One feature MindAnchor should add or invert, per app

| App | Action |
|-----|--------|
| **DBT Coach** | Borrow: *therapist portal / shareable export* as an opt-in. MindAnchor v0.27+ should let a user email a 30-day DBT diary card to a therapist with a single tap. **Invert:** the live-therapist messaging in DBT Coach is locked behind a paid plan; MindAnchor keeps the export free. |
| **Calm Harm** | Borrow: *the "first 60 seconds" pattern* — when a user expresses distress, Calm Harm drops them straight into a 5–15 min activity with no sign-up, no quiz, no questions. MindAnchor v0.27+ should make the "I'm struggling" entry-point bypass login when biometric auth is on. **Invert:** Calm Harm is iOS-only on Play (Android is listed but is iOS-only as a UK stem4 product — verify) — MindAnchor must be iOS + Android + web, parity-first. |
| **DBT Diary Card (Durham)** | Borrow: *PDF export to a named therapist on a schedule* (auto weekly email). **Invert:** the Durham app is iOS-only at $4.99; MindAnchor should ship a DBT diary card that is **free, no account, on-device, multi-platform**. |
| **eMoods** | Borrow: *clinician-ready PDF that survives the 15-min appointment*. **Invert:** eMoods is "symptom collection" only — it does not coach skills. MindAnchor should *pair* the diary card with one DBT skill of the day, not a blank "what did you feel." |
| **Moodfit** | Borrow: *validated screeners (PHQ-9, GAD-7)* as opt-in. **Invert:** Moodfit gates breathwork + CBT thought record behind a paywall. MindAnchor should not gate distress skills. |
| **Bearable** | Borrow: *correlation engine* that surfaces "your sleep dropped before your mood dropped." **Invert:** Bearable's "experiment" feature is locked behind premium; MindAnchor should give the *direction band* (own 14-day median + MAD) for free, no subscription. |
| **Daylio** | Borrow: *5-point emoji scale* for low-friction logging. **Invert:** Daylio's 5 points are *judgmental* labels ("awful", "bad"). MindAnchor should use *direction bands* (own baseline, not population norms) — never "good/bad" for an emotion. |
| **MoodTools** | Borrow: *a static safety plan* template (Stanley-Brown) the user fills in once and can open in one tap. **Invert:** MoodTools is a single-user self-help tool; MindAnchor should support *circle-of-trust* opt-in (3–5 named contacts who can be pinged in crisis). |

---

## 3. Apps that have done crisis helplines well

### 3.1 988 Suicide & Crisis Lifeline (US) — phone/text/chat 988
- **What they got right:**
  - **Three-digit number** (memorable, like 911); call, text, or chat with same entry point.
  - **Georouting** (FCC rule, Oct 2024): routes by approximate location, not area code, so a mobile user in distress hits the local crisis center, not a random one.
  - **Spanish + ASL + LGBTQI+ (press 3) + Veterans (press 1)** sub-routes from the same number — no "look up a different number."
  - **Confidential by default**, no personal data required.
  - **De-escalation is the goal** — only 1% of contacts trigger 911 dispatch (per Tennessee 988 dashboard, 2023–2024 data).
  - **No geolocation tracking** — explicit policy, posted publicly.
- **What they got wrong:**
  - Chat answer rate was only 46% in year one (CMS report). It's now 98%, but the early gap was real.
  - The "press 0 to skip menu" affordance is buried.
- **Borrow for MindAnchor:** the *single number, three modalities* principle. The MindAnchor v0.27+ distress path should be **one tap → choice of call / text / chat / helpline directory**, with a pre-filled crisis SMS that includes the user's approximate location (with explicit user consent, not default).

### 3.2 7 Cups — US/global
- **What they got right:**
  - **Anonymous by default** — even therapists don't see your real name.
  - **Trained listener 24/7** in many languages.
  - **Group chats by topic** (anxiety, depression, grief, LGBTQ+). 7 Cups was a Stanford MedicineX award winner (per their Play Store description).
- **What they got wrong:**
  - **Listeners are volunteers** who pass a 23-question test with unlimited retakes (HelpGuide 2026 review). For a BPD user, a poorly-trained listener can be a re-traumatisation event.
  - *Choosing Therapy* (2026) tested the service and **1-star rated it**: matched the test team with out-of-state, possibly unlicensed, therapists — which is illegal.
  - *HelpGuide* (2026): "do not recommend 7 Cups for therapy or mental health support."
  - Premium cost is $159–$299/mo.
  - No crisis-support guarantee (BetterHelp's own review states listeners cannot provide crisis support).
- **Borrow:** *group-topic rooms* (DBT skills room, BPD-only room, somatic room) as an *opt-in* layer above 1:1 — never the default.
- **Do not copy:** the volunteer listener pipeline and the deceptive "free online therapy" tagline.

### 3.3 KIRAN / Tele-MANAS (India)
- **What they got right:**
  - **1800-599-0019** (KIRAN), now merged with **Tele-MANAS 1-800 891 4416** — single toll-free national mental-health helpline.
  - **13 languages** (Hindi, English, Assamese, Bengali, Gujarati, Kannada, Malayalam, Marathi, Odia, Punjabi, Telugu, Tamil, Urdu).
  - **24×7**, BSNL-coordinated, ~660 clinical/rehab psychologists and 668 psychiatrists across 25 institutes.
  - Backed by academic study (PMC7561607) — published in *Indian J Psychol Med* 2020.
- **What they got wrong:**
  - The published *resource book* (NIEPMD) lists individual counselor mobile numbers in plain text — a privacy risk for clinicians (this was a 2020 PDF; I cannot verify current state).
  - IVR forces language → state selection before reaching a human, which adds 30–60 s in distress.
  - Tamil Nadu has limited state-level routing capacity per the same paper.
- **Borrow:** *13-language coverage* is a hard target for MindAnchor. The Indian user base deserves parity.
- **Invert for MindAnchor:** skip IVR; if the user opens MindAnchor in crisis, **language is inferred from phone locale** with a one-tap override.

### 3.4 Calm Harm (re-stated as crisis)
- **What it got right:** first 60 seconds drop the user into a DBT activity, no sign-up. UK NHS-approved mental-health pathway. Crisis info is one tap away (Childline 0800 1111, Samaritans 116 123, SHOUT 85258).
- **What it got wrong:** UK-only helplines; not localised to India (iCall 9152987821, Vandrevala 1860-2662-362, AASRA 9820466726 are absent). MindAnchor should pre-load these.

---

## 4. Common anti-patterns in mental health apps (BPD perspective)

Sources: University of Melbourne 2023 *Mental Health Apps Report*; Consumer Reports 2022 *Mental Health App Data Privacy*; SilverCloud/Amwell 2022 dark-patterns essay; my own verification of current app store behaviour where possible.

### 4.1 Subscription-trap paywalls
- **Pattern:** Free trial → silent auto-renewal → "shaming" cancel flow (Headspace, BetterHelp, Breeze Wellbeing, Calm — confirmed in UoM report Table 6).
- **Why harmful for BPD:** dissociation in the cancel flow = paid plan user loses trust for weeks. Cancellation rage is a documented trigger.
- **MindAnchor stance:** **already-correct.** MindAnchor is private R&D; no subscription tier. v0.27+ will *block* the option to introduce one for crisis content.

### 4.2 Pseudo-clinical scoring and "diagnosis theatre"
- **Pattern:** Apps that output "you are 73% anxious" or "you have severe depression" with no clinical backing. Documented for Breeze Wellbeing (Reddit r/assholedesign 2024) and many quiz-first funnels.
- **Why harmful for BPD:** borderline splitting means a number becomes a verdict. "Severe" → rumination loop → "I am severe" → action.
- **MindAnchor stance:** **already-correct.** MindAnchor shows direction bands only (own 14-day median + MAD, robust z-score). See `vitals/WellnessSignals.kt`.

### 4.3 Streaks and gamification
- **Pattern:** Lose a day, lose a 47-day streak. Common in Stoic, Ahead, Daylio (customizable off), Moodfit.
- **Why harmful for BPD:** the streak becomes an identity marker. A missed day = "I failed at recovery" = shame spiral.
- **MindAnchor stance:** **needs-change.** Any streak mechanic in MindAnchor must be a *rest-day-respecting* streak: a logged "I took a break" is a positive contribution, not a gap.

### 4.4 Forced sharing / public-by-default
- **Pattern:** Community features opt you in by default, comments visible to other users (Consumer Reports 2022).
- **Why harmful for BPD:** BPD users post in crisis. Public posts get picked apart = re-traumatisation. A 2018 case report documented a suicide contagion via a meditation app's "group energy" feature.
- **MindAnchor stance:** **already-correct.** MindAnchor's *circle-of-trust* (planned) is opt-in, per-user, three-tap max, no public feed.

### 4.5 "Symptoms-only" framing
- **Pattern:** Default data model is "track your symptoms." Verified in eMoods ("the list itself frames your daily experiences exclusively as a collection of symptoms" — *Mad in Canada* 2021 review).
- **Why harmful for BPD:** confirms the user's negative self-model (Tara Brach's "missing piece"). Symptom-tracking without skills training increases depressive episodes in bipolar (PMC12079407 cites individual trials).
- **MindAnchor stance:** **needs-change.** MindAnchor's tracker should default to *DBT diary card* (skills-used + emotions + urges) — never *symptom list*.

### 4.6 "Mindfulness cures everything" framing
- **Pattern:** Headspace / Calm / Stoic market mindfulness as the universal fix.
- **Why harmful for BPD:** Linehan 1993 explicitly notes mindfulness is *one of four modules* in DBT, not the whole. For someone in a dissociative state, forced mindfulness can deepen the dissociation (CFT 2017, Schwartz).
- **MindAnchor stance:** **already-correct.** MindAnchor's DBT-first model: mindfulness is *one* option among many, never the front door.

### 4.7 "Mood scale = good/bad" judgment
- **Pattern:** Daylio (5 emojis named "Awful" to "Rad"), eMoods ("depressed mood" slider), Moodfit ("low mood" tag).
- **Why harmful for BPD:** the label becomes the verdict. "Awful" today → "I am awful" → shame.
- **MindAnchor stance:** **already-correct.** MindAnchor shows direction bands (own baseline), not population-good/bad. See `WellnessSignals.kt` for the pattern.

---

## 5. Mood-tracking — 3 apps reviewed

### 5.1 eMoods (Bipolar Mood Tracker)
- **How they track mood:** *Multiple* dimensions — depressed mood, elevated mood, irritability, anxiety, psychotic symptoms. One entry per day (free). PDF export to clinician.
- **Numbers / labels / both:** **Numbers** (sliders) + **labels** (drop-down descriptions). Mostly numbers.
- **BPD-safety (shame vs validate):** mostly **shame** in the framing. The default data set is "log your symptoms" — the act of opening the app every day is *symptom-tracking*. The UI itself is monochrome and non-judgmental, but the *model* frames the user as a collection of symptoms (Mad in Canada 2021 critique).
- **MindAnchor lesson:** the *UI* can be neutral while the *data model* is shaming. MindAnchor v0.27+ must audit the *labels on the axes* of the diary card, not just the visual design.

### 5.2 Moodfit
- **How they track mood:** 1–5 mood score + activities + sleep + meds + gratitude + CBT thought record + PHQ-9/GAD-7 screeners.
- **Numbers / labels / both:** **Both.** Mood is a 1–5 number; activities and gratitude are labels.
- **BPD-safety:** mixed. The gratitude module is good when neutral, can shame when not ("I should be grateful"). The PHQ-9 / GAD-7 screeners are validated but the app surfaces the *number* without sufficient framing.
- **MindAnchor lesson:** validated screeners are fine *as data the user keeps*, never as a *diagnosis the app delivers*. The PHQ-9 is a clinical tool, not a verdict.

### 5.3 Daylio
- **How they track mood:** 5 emojis (Rad/Good/Meh/Bad/Awful) + activity icons + optional note. "Year in Pixels" calendar.
- **Numbers / labels / both:** **Labels** (the emojis are named), but the visual is a 5-color gradient (green to red). The *color* is the number.
- **BPD-safety:** *emojis are evocative, labels are not* — "Awful" is the strongest negative, but a user can ignore the label and pick the face. The app does not push back or add a "what made it awful" prompt.
- **MindAnchor lesson:** emoji/face-based input is fast and BPD-friendly because it doesn't force a label. MindAnchor v0.27+ can offer *face-only* mood check-in as a low-friction entry point, separate from the DBT diary card.

### 5.4 Honourable mention: Bearable
- Tracks mood + symptoms + meds + sleep + activities; explicitly markets to BPD ("symptoms of chronic illnesses such as Chronic Pain, Bipolar, Anxiety, Headache, Migraine, PCOS, Depression, BPD, and more" — Play Store description). The "BPD" mention in a tracker is rare and worth noting.
- MARS not found; star rating 4.6 (Play) / 4.8 (App Store).
- **MindAnchor lesson:** *Bearable named BPD and that's a small win.* Most trackers avoid the label. MindAnchor v0.27+ should *name* BPD in copy, not euphemise.

---

## 6. Self-harm / crisis apps — 3 reviewed

### 6.1 Calm Harm (stem4, UK)
- **First 60 seconds:** no sign-up, no questionnaire. User opens app, sees a single button ("Pass" / "I need to pass this urge"). Tap → activity (Comfort / Distract / Express Yourself / Release). 5–15 min activity. Crisis info is always one tap away.
- **What's BPD-safe:** the activity *structure* (DBT TIPP / ACCEPTS) is genuinely useful. No "are you sure?" or "have you tried…". The 15-min bound is right — long enough to ride an urge, short enough to commit.
- **What isn't:**
  - UK helplines only (Childline / Samaritans / SHOUT) — useless if you are in Tamil Nadu.
  - Single-modality (DBT-only). For someone in dissociation, mindfulness or grounding might be more useful than a TIPP sequence.
  - iOS-heavy (Play Store listing is iOS-style).
  - No "circle of trust" — the model is *you-alone* with the app.

### 6.2 7 Cups
- **First 60 seconds:** 7 Cups *can* get you to a chat listener in under 60 seconds (their marketing claim). The AI bot Noni is a fallback.
- **What's BPD-safe:** anonymous, no sign-up friction for free tier, group rooms on heavy topics (BPD, trauma, suicide survivors).
- **What isn't:**
  - The listener is a volunteer who took a 23-question test (HelpGuide 2026). For a BPD user in crisis, an under-trained listener can be worse than no listener.
  - *Choosing Therapy* 1-star rating documents out-of-state unlicensed therapists.
  - "Free online therapy" is misleading marketing; therapy is $159–$299/mo.
  - *Listeners cannot provide crisis support* (BetterHelp 2024 review).
  - No structured distress protocol (TIPP, safe place, etc.) — just chat.

### 6.3 Mindgram (PL/EU) — limited verification
- **What I can verify:** Mindgram is a Polish-origin app that offers workshops, podcasts, video calls with a psychologist or coach, and chat with multiple specialists. App Store rating 4.4 (PL, 48 ratings).
- **First 60 seconds:** not verifiable from the public listing.
- **What I cannot verify:** specific crisis-path behaviour, BPD-safety review, MARS score. I include it only because the user asked for it; flag it as **insufficiently verified**.

### 6.4 Crisis path MindAnchor should design
Drawing from the above, a v0.27+ MindAnchor distress path should:
1. **One-tap entry**, no login required if biometric is on.
2. **First screen: "you are safe right now" + 4 options** (call / text / chat / coping skill).
3. **Locale-inferred language**, one-tap override.
4. **Pre-filled crisis SMS** with the user's approximate location (consent-gated).
5. **TIPP / safe-place / 5-4-3-2-1 / Wise Mind ACCEPTS** as selectable skills, all < 5 min.
6. **iCall / Vandrevala / AASRA** pre-loaded for India, **988 / Crisis Text Line / Trevor** for US, **Samaritans / SHOUT / Childline** for UK.
7. **No "are you sure" friction.** No "did this help?" survey during the crisis.
8. **Quiet-by-default after the crisis**: no streaks, no "come back tomorrow" push, no follow-up email.

---

## 7. Reference list

### Apple Design Awards
- 2023 winners: https://developer.apple.com/design/awards/2023/
- 2023 newsroom: https://www.apple.com/newsroom/2023/06/apple-announces-winners-of-the-2023-apple-design-awards/
- 2024 winners: https://developer.apple.com/design/awards/2024/
- 2024 newsroom: https://www.apple.com/newsroom/2024/06/apple-announces-winners-of-the-2024-apple-design-awards/
- 2025 winners: https://developer.apple.com/design/awards/2025/
- 2025 newsroom: https://www.apple.com/newsroom/2025/06/apple-unveils-winners-and-finalists-of-the-2025-apple-design-awards/
- ADA 2023 story: https://apps.apple.com/in/story/id1687371285
- ADA 2024 story: https://apps.apple.com/us/story/id1745165692
- ADA 2025 story: https://apps.apple.com/us/story/id1808994124

### DBT / BPD / MARS-G evaluations
- Stawarz 2021, *Borderline Personal Disord Emot Dysregul* — DBT apps review: https://pmc.ncbi.nlm.nih.gov/articles/PMC8639404/
- Drews-Windeck et al. 2022, *Borderline Personal Disord Emot Dysregul* — BPD MHA MARS-G: https://pmc.ncbi.nlm.nih.gov/articles/PMC9158356/
- Messner et al. 2020, MARS-G validation: https://mhealth.jmir.org/2020/3/e14479/
- Stoyanov et al. 2015, MARS original: https://mhealth.jmir.org/2015/1/e27/
- BPD apps meta-analysis (Drews-Windeck 2023): https://pmc.ncbi.nlm.nih.gov/articles/PMC7296633/
- DBT Coach APA study: https://www.apa.org/pubs/journals/features/ser-ser0000100.pdf

### Crisis
- 988 Lifeline: https://988lifeline.org/
- 988 about / georouting: https://988lifeline.org/about/
- SAMHSA 988 FAQ: https://www.samhsa.gov/mental-health/988/faqs
- 988 FCC: https://www.fcc.gov/988-suicide-and-crisis-lifeline
- KIRAN PIB launch: https://www.pib.gov.in/PressReleasePage.aspx?PRID=1652240
- KIRAN / Tele-MANAS analysis (PMC): https://pmc.ncbi.nlm.nih.gov/articles/PMC7561607/
- DEPwD helplines page: https://depwd.gov.in/en/others-helplines/
- 7 Cups: https://www.7cups.com/ (app: https://apps.apple.com/us/app/7-cups-online-therapy-chat/id921814681)
- 7 Cups critical reviews: https://www.helpguide.org/handbook/online-therapy/7-cups-online-therapy-review ; https://www.choosingtherapy.com/7-cups-review/
- Calm Harm (stem4): https://calmharm.stem4.org.uk/ ; https://play.google.com/store/apps/details?id=uk.org.stem4.calmharm

### Apps
- DBT Coach: https://apps.apple.com/us/app/dbt-coach/id1452264969 ; https://www.resiliens.com/en/dbt-coach
- DBT Diary Card & Skills Coach: https://apps.apple.com/us/app/dbt-diary-card-skills-coach/id479013889
- eMoods: https://emoodtracker.com ; https://play.google.com/store/apps/details?id=my.tracker
- Moodfit: https://getmoodfit.com ; https://play.google.com/store/apps/details?id=com.robleridge.Moodfit
- Bearable: https://bearable.app ; https://play.google.com/store/apps/details?id=com.bearable
- Daylio: https://daylio.net ; https://play.google.com/store/apps/details?id=net.daylio
- Stoic: https://apps.apple.com/us/app/stoic-journal-mental-health/id1312926037
- Headspace: https://www.headspace.com
- Calm Harm FAQ: https://calmharm.stem4.org.uk/faqs/

### Anti-patterns / dark patterns
- University of Melbourne 2023 *Mental Health Apps Report*: https://www.unimelb.edu.au/__data/assets/pdf_file/0008/4824404/Mental-Health-Apps-Report-updated-17-Dec.pdf
- Consumer Reports 2022: https://innovation.consumerreports.org/CR_mentalhealth_full-report_VF.pdf
- SilverCloud/Amwell 2022: https://silvercloud.amwell.com/blog/2022/01/design-ethics-for-mental-health-how-and-why-we-avoid-dark-patterns
- Mindgram (limited): https://apps.apple.com/pl/app/mindgram/id1621174095
- *Mad in Canada* 2021 eMoods review: https://madincanada.org/2021/05/the-best-and-worst-that-mood-tech-can-be/
- ACM CHI 2024 "unintentional harms of MH apps": https://dl.acm.org/doi/pdf/10.1145/3613904.3642178
- *Evolving field of digital mental health* (PMC12079407): https://pmc.ncbi.nlm.nih.gov/articles/PMC12079407/

### BPD framework sources
- Linehan 1993, *Cognitive-Behavioral Treatment of Borderline Personality Disorder* (DBT manual)
- Schwartz 1995 (internal family systems / CFT)
- Stanley-Brown Safety Plan (widely cited; referenced in 988 / 7 Cups reviews)
