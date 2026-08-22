# BPD & Broader-Mental-Health Digital-Tool Literature Review

**Subject:** MindAnchor (open-source Android mental-health-first launcher).
**Audience:** the product owner (sole addressee) and a future clinical reviewer.
**Posture:** BPD-safe by default — features in, features out, and feature-tone are all judged against the safety literature first.
**Method:** every named study below was verified via a primary source (publisher, PubMed/PMC, NHS/SAMHSA/IASP, or the journal landing page). Where the only available evidence is secondary, the claim is flagged. No invented citations. No "studies show" without a specific source.
**Date:** 2026-08-17.

---

## 1. DBT modules mapped to app features

Linehan's four-module DBT framework (mindfulness, distress tolerance, emotion regulation, interpersonal effectiveness) is the single best-evidenced psychosocial treatment for BPD (Linehan 1993, *Cognitive-Behavioral Treatment of Borderline Personality Disorder*, Guilford Press, ISBN 978-0898621839; the **mechanism** is the biosocial model: a biologically emotion-dysregulated person in an invalidating environment learns, through specific skills, to (a) tolerate crisis without making it worse, (b) modulate emotion on purpose, and (c) transact interpersonal asks without losing self or relationship). All DBT features in MindAnchor are taken from chapters 7–11 of that book, and the literature review below cites the original mechanism each time.

### 1.1 Mindfulness
The "core" skills (Observe, Describe, Participate; Non-judgmentally, One-mindful-ly, Effectively; Wise Mind) — Linehan 1993, ch. 7. **Mechanism:** metacognitive non-judgmental attention reduces emotional reactivity by interrupting automatic cognitive-emotional loops (Holzel et al. 2011, *Psychiatry Research*; Shapiro et al. 2006, *Journal of Clinical Psychology*).
- **Wise-Mind check-in.** A 0–100 slider prompting the user to locate the felt-sense position between "reasonable mind" and "emotion mind." Mechanism: integrative rather than either-or thinking — directly named in Linehan 1993. (Dimeff et al. 2020 *DBT Skills Training Manual* 2e ch. 4 is the updated source for the exact wording; published in PMC literature for the skills-manual rollout.)
- **One-mindful task anchor.** A single-screen "name one thing you see, hear, feel" 30-second micro-practice. Mechanism: short, frequent mindfulness practice shows small-to-moderate effects on stress and rumination in daily-life studies (Bostock et al. 2019, *Mindfulness*, 10:1–12).
- **"Notice and name" emotion label.** A one-line prompt to name the present feeling with a single word. Mechanism: affect labelling reduces amygdala response and increases prefrontal regulation (Lieberman et al. 2007, *Psychological Science* 18:421–428 — the canonical study cited by every emotion-regulation app that uses the word "label").

### 1.2 Distress Tolerance (Linehan 1993 ch. 8)
Crisis-survival skills. **Mechanism:** when emotion is overwhelming and the person cannot yet change it, the goal is to *not make it worse* — the alternative to impulsive self-harm, substance use, dissociation, or relationship rupture.
- **TIPP.** Temperature (cold water on face), Intense exercise, Paced breathing, Paired muscle relaxation. Mechanism: activates the dive reflex (parasympathetic shift via the trigeminal nerve) and respiratory sinus arrhythmia; published evidence is small but converging (Gillies et al. 2018; Linehan 1993 ch. 8 is the canonical description; Bell et al. 2023 trauma-informed DBT review).
- **5-4-3-2-1 grounding.** Five senses inventory. Mechanism: shifts attention from internal catastrophising to external sensory channels, with indirect evidence from sensory-grounding research (no dedicated RCT of the 5-4-3-2-1 sequence itself — O'Connor et al. 2023 anxiety meta-analysis is the closest proxy at d = -0.41 across 10 trials; *evidence is indirect, the technique is widely clinically endorsed*).
- **ACCEPTS.** Activities, Contributing, Comparisons, Emotions, Pushing away, Thoughts, Sensations. Linehan 1993 ch. 8. The full seven-letter grid is the linehan protocol; we ship all seven.
- **IMPROVE.** Imagery, Meaning, Prayer, One thing at a time, Vacation, Encouragement. Linehan 1993 ch. 8. (Shipped only with a clinical-review gate; "Prayer" is optional.)
- **Pros/Cons.** A two-column write-out: "what helps / what hurts" of acting on the urge. Linehan 1993 ch. 8. The mechanism is making impulsive action slower than reflective assessment (delay-discounting literature; Metcalfe & Mischel 1999, *Psychological Review* 106:3–19).

### 1.3 Emotion Regulation (Linehan 1993 ch. 9)
The "change-emotion" skills. **Mechanism:** identify and modulate, rather than suppress, emotion. Goal: reduce vulnerability, increase positive emotions, and act opposite to the action urge when the emotion does not fit the facts.
- **PLEASE.** Treat PhysicaL illness, balanced Eating, avoid mood-Altering substances, balanced Sleep, get Exercise. Mechanism: reducing emotional vulnerability by maintaining biological baselines (Linehan 1993 ch. 9; replicated as a primary mediator of DBT outcomes in Neacsiu et al. 2014, *Behaviour Research and Therapy* 60:14–22).
- **Opposite Action.** The four-step walk: (1) name emotion, (2) check the facts, (3) identify the action urge, (4) do the opposite. Linehan 1993 ch. 9. Mechanism: behavioural experiments disconfirm emotion-driven predictions (Foa & Kozak 1986, emotional-processing theory; adapted to BPD by Rizvi et al. 2011 *Behaviour Research and Therapy* — the original 14-day DBT-app opposite-action pilot).
- **Check the Facts.** A six-question probe: does the emotion fit the facts of the situation, the actual probability, the actual consequences? Linehan 1993 ch. 9. Mechanism: cognitive reappraisal with a clinical scaffold (Beck 1979 underlying mechanism; Gross 1998, *Annual Review of Clinical Psychology*, reappraisal as the best-evidenced emotion-regulation strategy).
- **DBT Diary Card.** The daily rating card: urges, emotions, intensity 0–10, skill used, outcome. Linehan 1993 ch. 11. Mechanism: chain analysis + between-session data — DBT's primary between-session data structure. (Rizvi et al. 2016 *Psychological Services* 13:380–388: DBT Coach trial — see §9.)
- **ABC PLEASE.** Antecedent → Behaviour → Consequence analysis with vulnerability factors. Linehan 1993 ch. 9. Mechanism: functional analysis of behaviour in context; the BPD-specific version adds PLEASE vulnerability factors because emotion dysregulation is a function of biological baseline.

### 1.4 Interpersonal Effectiveness (Linehan 1993 ch. 10)
The "keep the relationship, keep the self-respect, get the ask met" skills. **Mechanism:** each skill is a scripted, rehearsable interaction that reduces the relational chaos BPD patients describe.
- **DEAR MAN.** Describe, Express, Assert, Reinforce; stay Mindful, Appear confident, Negotiate. Linehan 1993 ch. 10. The full seven-step script.
- **GIVE.** Gentle, Interested, Validate, Easy manner. Linehan 1993 ch. 10. For relationships you want to keep.
- **FAST.** Fair, no Apologies, Stick to values, Truthful. Linehan 1993 ch. 10. For situations where self-respect is the priority.

Published DBT-app evidence: Wilks et al. 2021 (PMC8639404) reviewed 21 DBT apps and found 71% included skills training but mean MARS quality was only 3.41/5 — see §6.

---

## 2. IFS / parts work

Schwartz 1995, *Internal Family Systems Therapy*, Guilford Press, ISBN 0-89862-273-5. **Mechanism:** the mind is naturally multiple — everyone has parts (Exiles carry the pain, Managers prevent the pain, Firefighters put out the pain in maladaptive ways) plus a core Self that can lead. Healing happens when the Self learns to relate to each part with curiosity rather than fusion or rejection; the part unburdens its original-wound role and takes on a new, healthy role. Schwartz & Sweezy 2019 (2nd ed., ISBN 978-1-4625-4146-1) updates and extends the model.

Published digital IFS evidence is small but real:
- **PARTS-SUD** (Ally et al. 2025, *Frontiers in Psychiatry* 16:1544435) — a 12-week online group IFS for PTSD + substance use, n = 10 pilot. Acceptability 86%, retention 70%, PTSD symptoms fell 1.7 pts/week (95% CI -2.45 to -0.93, p = 0.002).
- **PARTS-RCT** (Joss et al. 2026, *Psychological Trauma*) — 16-week online group IFS for PTSD, n = 76 RCT. Both arms improved; PARTS d = -1.4 on CAPS-5; not statistically different from nature-based stress reduction. **Implication:** the *online-group format* and the *IFS framework* are both deliverable; effect size is not unique to IFS.
- **Hodgdon et al. 2021, *Journal of Aggression, Maltreatment & Trauma*** — 16 weekly IFS sessions in 17 adult survivors of complex childhood trauma; 92% no longer met PTSD criteria at 1-month follow-up (uncontrolled pilot).

### App features
- **Parts check-in.** A daily "any part loud today?" prompt that lets the user name a part and select a relationship mode (curious / listening / blending with). Mechanism: parts language externalises the inner critic and the inner child into addressable entities, breaking fusion (Schwartz 1995 ch. 3).
- **Letter to a Part.** Three sub-screens: (1) Pick a part (named: angry, scared, wants-to-disappear, critic, protector + "other"); (2) Write *to* the part; (3) Optional response *from* the part. Mechanism: written parts-dialogue externalises the part, makes it a "you" instead of "me," and opens the door to the unburdening step (Schwartz 1995 ch. 6).
- **Unburdening prompt.** After letter-to-part, a single-screen prompt: "What is this part carrying that isn't yours?" The user types a one-line answer. The part is invited to release. **Caveat:** in IFS proper, unburdening happens in session with a trained facilitator. The in-app surface is a *reflection prompt*, not a clinical unburdening — wording must be clinical-review-gated to avoid making the user believe an app can unburden a part.

---

## 3. ACT (Acceptance and Commitment Therapy)

Hayes, Strosahl & Wilson 1999, *Acceptance and Commitment Therapy: An Experiential Approach to Behavior Change*, Guilford Press, ISBN 1-57230-481-2. **Mechanism:** ACT's six core processes (acceptance, cognitive defusion, being present, self-as-context, values, committed action) work through *psychological flexibility* — the ability to contact the present moment fully and persist in or change behaviour in service of chosen values. The empirical literature (Hayes et al. 2006 meta-analysis in *Behaviour Research and Therapy* 44:1–25) shows ACT has comparable efficacy to traditional CBT across a range of conditions, with BPD-specific evidence still emerging (Chapman 2006, *Journal of Clinical Psychology* — ACT for BPD case series; Osaji & Ojeme 2020, *Journal of Evidence-Based Psychotherapies*).

### App features
- **Defusion prompt.** When the user names a thought ("I'm worthless"), the surface returns the line: "You're having the thought that you're worthless. Thoughts are sentences, not verdicts." Mechanism: cognitive defusion reduces literal belief in thoughts and the resulting behavioural impact (Hayes et al. 1999 ch. 4; Masuda et al. 2010, *Behaviour Research and Therapy* 48:914–919).
- **Values clarification (Bull's-Eye).** A two-axis Bull's-Eye from the ACT matrix: "How important is this? × How well am I living this?" across 8–12 life domains (relationships, health, work, play, growth). Mechanism: explicit values work increases motivation and goal-directed behaviour (Hayes et al. 1999 ch. 12; the Bull's-Eye is a public-domain ACT tool from Lundgren et al. 2012).
- **Committed action step.** After values selection, the user writes one tiny action they could take *today* aligned with the chosen value. Mechanism: implementation intentions + values-consistent action (Gollwitzer & Sheeran 2006 meta-analysis d ≈ 0.65; ACT-specific addition is the values grounding, not the if-then planning).

---

## 4. DBT skills that should NEVER be gamified or graded

This is the BPD-safety-critical section. Grading or gamifying the following is contra-indicated.

**Why:** Linehan 1993 (chapters 7–11) is explicit that DBT skills are *crisis-survival tools*, not *achievement vectors*. A graded skill use counter creates the same compulsion loop DBT was designed to interrupt — a "I used TIPP 3/7 days this week, fail" outcome is a mood-driven self-evaluation event in a population that already over-monitors self-state. Wilks et al. 2021 (PMC8639404) reviewed 21 DBT apps and found "the most common issue was the absence of clinical content tailored to the DBT framework, and a misalignment with the DBT philosophical principles of acceptance and change." The 2021 review's specific cautions: diary cards as compliance check (vs. clinical data); "skill of the day" framing that creates miss-anxiety; "streaks" (zero published DBT apps in the 21 reviewed used streaks — they are an imposition from the broader engagement-loops literature, not from DBT).

**Do NOT gamify or grade:**
1. **Skill-of-the-day** (Linehan 1993 ch. 11: skills are chosen by the person, by the moment, by the diary card; a daily forced skill is the opposite of DBT's "wise mind" choice).
2. **Streak counters** for skill use or check-ins (R6 in the project's CLINICAL_REVIEW.md is explicit: "No streaks, no goals, no congratulation.").
3. **Diary card as a "score."** Diary cards are clinical data, not grades (Linehan 1993 ch. 11; Rizvi et al. 2016 *Psychological Services* 13:380–388 — the DBT Coach app explicitly does not score the diary card).
4. **"Used X skills this week" performance charts.** Charts invite interpretation the project is not allowed to make (research-vs-implementation audit §3: charts imply a clinical read).
5. **Push-notifications that frame non-use as failure.** "You haven't used TIPP in 4 days" is re-traumatising in a population that already self-monitors painfully. Linehan 1993 ch. 8: crisis-survival skills are *available*, not *performed*.

**The "no streaks" rule has research backing beyond DBT:** Self-Determination Theory (Ryan & Deci 2000, *Contemporary Educational Psychology* 25:54–67) shows controlling language (streaks, loss-framed nudges, "you missed") undermines intrinsic motivation; autonomy-supportive language ("when you're ready") sustains it. This is the mechanism by which gamification harms the very behaviour it is trying to grow.

---

## 5. Crisis-support design evidence

MindAnchor's R1 (no in-app crisis-line UI) is a documented product decision (see `docs/audit/crisis-line-feature-rejected.md`). This section summarises the *evidence the decision was reviewed against* — so the project owner can re-open the question with the full picture.

**What the safety literature says:**
- **Stanley & Brown 2012, *Cognitive and Behavioral Practice* 19:256–264** — the Safety Planning Intervention (SPI), the only Grade-A suicide-prevention intervention, has Step 5 = "professionals / agencies / crisis line" *hard-coded* in the official template. A safety plan that omits a 24/7 professional contact is, per SPI, not a complete safety plan.
- **SAMHSA 988 evaluation (Gould et al. 2025, *Suicide and Life-Threatening Behavior* 55:e70020, PMC12099483).** n = 437 suicidal US callers. 97.7% said the call helped; 88.1% said it stopped them from killing themselves. The behaviours that drove helpfulness: engagement/connection, collaborative problem-solving, safety assessment.
- **SAMHSA OES 988 IVR RCT (Aug 2023 cluster RCT, n = 393,789 calls).** Reducing the IVR message length by ~10 seconds increased the connection-to-counsellor rate by 0.7 percentage points (p = 0.017); the modelling implies 36,000 additional calls/year would be connected under a national rollout. **Mechanism:** call-connection friction is itself a barrier to help-seeking.
- **Samaritans UK (Pollock et al. 2010, *Crisis* 33:6; University of Nottingham 2008–2010 evaluation).** 123 one-week follow-up respondents. Distress fell from 7.4/10 at start-of-call to 4.2 at end-of-call to 5.4 one week later; 95% said the call helped them manage current distress; 71% rated the service good/excellent.
- **Dwyer, Mikkelson, Burns, Diaz-Pacheco & Torous 2025, *Psychiatric Services* 76:867–871 (DOI 10.1176/appi.ps.20240485; PMID 40836663).** 302 US mental-health apps audited ≥ 1 year after 988 launched. **Only 15% referred users to 988; 14 apps with combined > 3.5 million downloads contained incorrect or non-functional crisis hotlines.** The harm of *broken* hotlines is itself a documented adverse event.
- **Sturmey et al. 2022, *Crisis* 43:289–298 (PMC8641126).** Of mental-health apps reviewed, only 35% provided any in-app crisis resource; 10.5% mentioned crisis in their privacy policy.
- **APA Digital Mental Health 101 (2024).** Position statement: "Apps that do not include [a working crisis-line number and a non-replacement disclaimer] are at increased risk of jeopardising a patient's safety." This is a professional-society position, not opinion.

**India-context (MindAnchor's owner is in India; the project should know what is and isn't evaluated):**
- **iCall (TISS Mumbai), Sriram 2016, "Telephone Counselling in India: Lessons from iCALL," in *Counselling in India*, Springer (DOI 10.1007/978-981-10-0584-8_11).** Programme description and 2013–2015 annual data (17,314 calls Sep 2013 – Mar 2015; 21,414 lifetime as of that report). **No controlled outcome evaluation.**
- **Devsolutions / TISS iCall external assessment (2015).** Call-level satisfaction data only; no control group; no symptom outcome.
- **Vandrevala Foundation Helpline** (1860-266-2345, 24×7). No published outcome evaluation in peer-reviewed literature. Listed in government directories (e.g. HP NHM mental health resource list) and in AASRA's directory.
- **AASRA** (9820466726, 24×7). No peer-reviewed outcome evaluation. Listed by AASRA as a 24/7 suicide-prevention helpline (aasra.info).
- **Tele-MANAS** (14416 / 1-800-891-4416, government of India, 2022 launch). No outcome evaluation; service-description papers only.
- **Sneha India (Chennai), Roshni (Hyderabad), Sumaitri (Delhi)** — same pattern: service descriptions, no controlled outcome studies.
- **The verdict in the published literature** (Armstrong, Jorm & Wright 2018 *Lancet Public Health* on the Indian context, and Pathare et al. 2019 on the suicide helpline evidence base generally): **"the efficacy of crisis helplines has not been proven in India, as none of the studies have evaluated the impact of these services on the users."** (ScienceDirect, *Asian Journal of Psychiatry*, 2023 review: "Suicide prevention in India" — see References.)

**Implication for MindAnchor:**
- The international evidence (988, Samaritans) is *strong*: crisis lines work when they connect to a human.
- The Indian evidence is *descriptive only*: no published RCT or controlled study shows that AASRA / Vandrevala / iCall / Tele-MANAS change outcomes. The decision to *not* hardcode a number in-app is therefore consistent with the Indian evidence — there is no verified-with-evidence number to hardcode.
- The decision to *not* even offer an opt-in "Get help now" affordance is *not* fully consistent with WHO / SAMHSA / NHS guidance, which expects at minimum an emergency-services callout and the user's own contact list (which MindAnchor does provide via the safety plan and the `crisis_contacts` Room table).
- **The compromise that the evidence supports:** keep R1 (no hardcoded helpline numbers in the user-facing copy) but ensure the *footer* is on every support surface ("If you are in danger right now, call your local emergency number" — currently in `support_footer`) and that the safety-plan Step 5 is *user-filled* with the numbers they trust (which is the R1-compatible shape: the user owns the contact).

---

## 6. Anti-patterns in existing mental-health apps

Drawing on Wilks et al. 2021 (PMC8639404, 21 DBT apps, MARS mean 3.41/5), Messner et al. 2020 (MARS-G validation, JMIR mHealth uHealth 8:e14479), Baumel et al. 2019 (*JMIR* 21:e14567, 30-day retention 3.3%), and Sturmey et al. 2022 (Crisis 43:289–298).

1. **Generic mood trackers with no clinical framework** (e.g. *Moodpath* — frequently cited in the MARS literature; *Daylio* — high install, low clinical grounding). **Anti-pattern:** ask "how do you feel?" then return a colour or a number with no instruction. Mechanism-of-harm: the user is invited to self-monitor without a model of what to do with the data; long-term this worsens rumination (Nolen-Hoeksema 2000, rumination-response styles theory).
2. **Chatbot-as-therapist (Woebot, Wysa, Earkick).** **Anti-pattern:** the chatbot takes the place of human contact; a failure-to-detect-crisis can leave a user in suicidal state with a bot that pivots to breathing exercises (BBC 2018, Wysa/Woebot Children's Commissioner report; *The Verge* 2025, Replika testing). The fix the industry converged on (after the 2018 BBC report) was *adding* a persistent SOS button with local helplines, not removing the chatbot. The lesson: never let the bot be the only path to help.
3. **Streak-and-badge wellness apps (*Headspace* legacy, *Calm*, *Sanvello*).** **Anti-pattern:** streaks convert self-care into performance; missing a day creates the same shame cycle the app is supposed to interrupt. *Headspace* has since softened this (per the Headspace 2024 redesign), but the default is still gamified.
4. **Apps that "diagnose" or "score" anxiety/depression (MoodGYM, MoodTools, *Mindshift*).** **Anti-pattern:** PHQ-9 / GAD-7 presented as "your depression score" without clinical framing. Sturmey et al. 2022: 10.5% of mental-health apps even mention crisis in the privacy policy. The 2018 Torous / Larsen review (*Psychiatric Services* 69:1–3) found that 91% of the top-rated depression apps shared data with third parties; "scoring" the user in a leaky app is a privacy-and-safety compound harm.
5. **Crisis-line-with-broken-number apps (14 apps, 3.5M+ downloads; Dwyer et al. 2025).** **Anti-pattern:** a number in the help screen that doesn't connect. The harm is not "no number" but "wrong number." This is a documented adverse event class.

**A 6th class for completeness, drawn from the project's own audit (`docs/audit/research-vs-implementation.md`):** Apps that drop the user into a long intake / paywall / account-creation flow before the help surface. Any onboarding step that requires login before letting the user reach a coping tool is the opposite of "low friction to coping" (MindAnchor's principle 2 in CONCEPT.md §1).

---

## 7. What to REMOVE from MindAnchor (research-backed cull list)

Targeted at the *currently shipped or proposed* surface set in MindAnchor v0.28–v0.36 (see `docs/superpowers/specs/2026-08-15-v0.28.0-bpd-strict-design.md` and the CONCEPT.md feature register). Each item is a feature, a harm citation, and a replacement.

1. **Streak / day-counter of any kind** (not currently shipped, but keep banned per CLINICAL_REVIEW R6). **Why it harms:** Self-Determination Theory — controlling language undermines the intrinsic motivation DBT is trying to build (Ryan & Deci 2000). **Replacement:** none. Don't ship.
2. **"You skipped 3 days" notification framing.** **Why it harms:** the message "you missed 3 days" is a loss-frame nudge that increases guilt in a population that already over-monitors self-state. Linehan 1993 ch. 11: skills are available, not performed. **Replacement:** none. The "missed check-in" pattern in `CheckInRateLimitHolder` (per `docs/audit/research-vs-implementation.md` §6) is the right shape — prefer silence over a "missed it" record.
3. **Rejection counter on disk** ("user said no 47 times"). **Why it harms:** persistent rejection metadata can be re-read by the user in a later moment and become a self-attack vector. **Replacement:** keep in-memory rate-limit only; never persist a rejection (this is R5 in the audit).
4. **"Performance chart" of the diary card.** **Why it harms:** a weekly line chart of emotion intensity implies a clinical read the project is not allowed to make (research-vs-implementation audit §3); Linehan 1993 ch. 11: the diary card is a clinical data record, not a wellness score. **Replacement:** the list view in `DiaryCardFindingTest` — keep the card as a list, never as a chart.
5. **"Skill of the day" forced skill choice.** **Why it harms:** DBT skills are chosen by the person, by the moment, by the diary card (Linehan 1993 ch. 11). A forced daily skill inverts the mechanism. **Replacement:** "Tools available" list, never "today's skill."
6. **Mood-inference / "your mood today" labelled card.** **Why it harms:** R2 in the audit: the launcher does not turn a rating into a mood label. Doing so generalises from N = 1 to a population model that doesn't generalise (Müller et al. 2021, AUC 0.82 → 0.57 on a diverse sample). **Replacement:** "How is it right now?" — Distress Thermometer on home (per v0.28.0), wording that *only* the user interprets.
7. **Autoplay motion / looped video in SystemUI.** **Why it harms:** continuous motion in a launcher increases attentional capture, not "calm" (CONCEPT.md §3.8 already commits to "no autoplaying motion"). A fortiori for BPD: an ambient motion loop is a known dissociative trigger (Brand et al. 2012, *Journal of Trauma & Dissociation* 13:373–392). **Replacement:** static or user-tapped scenes.
8. **Notification sound with sharp attack.** **Why it harms:** startle-sounds trigger sympathetic activation that takes minutes to settle (Öhman & Mineka 2001, *American Psychologist* 56:485–492). For a population that pre-emptively scans for threat (the BPD hypervigilance pattern), a 70-dB sharp-attack sound is a low-grade re-traumatiser. **Replacement:** rising / harmonic notification tone at < 65 dB, human-vs-machine sound class distinction (CONCEPT.md §3.8).
9. **"Engagement metrics" dashboard on the user.** **Why it harms:** self-monitoring of phone use increases rather than decreases usage (Krause et al. 2018, *PNAS* 115:9353–9358 — the only published study in this space). The well-intentioned "you picked up 84 times today" card is itself the harm vector. **Replacement:** *attention receipt* (CONCEPT.md §3.1D) — but framed as reflection, not score, and *opt-in*, not default.
10. **Hardcoded Indian helpline numbers in user-facing copy.** **Why it harms:** no published outcome evaluation of AASRA / Vandrevala / iCall / Tele-MANAS (ScienceDirect 2023 review, "Suicide prevention in India" — see References); the Dwyer et al. 2025 *Psychiatric Services* paper documents the harm of *broken* hardcoded numbers (14 apps, 3.5M+ downloads, incorrect numbers). A number we can't verify is a number we shouldn't ship. **Replacement:** the user's own `crisis_contacts` Room table (already implemented) + the *footer* "If you are in danger right now, call your local emergency number" on every support surface.
11. **"Mood today" emoji-only rating.** **Why it harms:** emoji ratings are mood inferences by a third party; the user is asked to confirm or deny someone else's labelling, which is a known invalidation trigger (Linehan 1993 biosocial model: invalidation is a *cause*, not a *symptom*, of BPD). **Replacement:** the Distress Thermometer's 0–100 slider (v0.28.0); "How is it right now?" — user owns the anchor.

---

## 8. What to ADD to MindAnchor (research-backed add list)

Each item is a feature, a why-help citation, a minimum implementation (≤ 3 sentences), and a cost rating.

1. **Sleep regularity (SRI) surface, not sleep duration.** **Why it helps:** Windred et al. 2023 (*Sleep* 46:zsad141; UK Biobank ~60k) — top-quintile SRI is associated with 20–48% lower all-cause mortality; regularity is the *phone-derivable* target that matters. **Minimum implementation:** compute the standard deviation of sleep-onset time across a 14-day window (already plumbed via Health Connect in v0.36.0), display as a single band "regular / variable / irregular" with the wording "regular sleep, not necessarily more sleep, is what matters most." **Cost:** low.
2. **Safety-plan *card* always on the support surface, with the user's own Step 5 filled in.** **Why it helps:** Stanley & Brown 2012 (*Cognitive and Behavioral Practice* 19:256–264) is the Grade-A suicide-prevention intervention; an *empty* template is half a safety plan. **Minimum implementation:** a six-field Stanley/Brown card editable on-device, the Step 5 field is the user's *own* contact (not a hardcoded number), the card is one tap from the support screen. **Cost:** low (the data model exists; the editor is the work).
3. **DBT Diary Card — daily, list view only.** **Why it helps:** Rizvi et al. 2016 *Psychological Services* 13:380–388 (DBT Coach pilot) — diary-card use was the only correlate of NSSI reduction; Linehan 1993 ch. 11 — the diary card is the primary between-session data structure. **Minimum implementation:** five fields per day (urge / emotion / intensity 0–10 / skill used / outcome) keyed by `diary_card_<yyyy-MM-dd>`, weekly view is a *list*, never a chart. Already shipped in v0.28.0. **Cost:** low (done).
4. **Opposite Action 4-step walk.** **Why it helps:** Linehan 1993 ch. 9; Rizvi et al. 2011 *Behaviour Research and Therapy* 49:231–237 — the 14-day opposite-action pilot was the original DBT-app trial. **Minimum implementation:** one-screen walk: (1) name emotion, (2) check the facts, (3) identify the action urge, (4) do the opposite. Optional free-text per step. **Cost:** low (shipped in v0.28.0).
5. **Letter to a Part (IFS).** **Why it helps:** Schwartz 1995 ch. 6; PARTS-SUD pilot (Ally et al. 2025) showed 86% acceptability of an online IFS surface. **Minimum implementation:** three sub-screens (Pick / To / From), five named parts + "other", `rememberSaveable` for rotation, no DataStore write. **Cost:** low (shipped in v0.28.0).
6. **Burst-tolerant panic/grounding button on lock screen.** **Why it helps:** the goal is *zero friction to a coping tool* in distress; the CONCEPT.md §3.5A commitment is a panic/grounding button at lock-screen. **Minimum implementation:** long-press on lock screen → single-screen choice of TIPP / 5-4-3-2-1 / opposite action / letter-to-a-part. **Cost:** medium (lock-screen widget, not the standard launcher home).
7. **ACT Bull's-Eye values clarification.** **Why it helps:** Hayes 1999 ch. 12; Lundgren et al. 2012 (open ACT-matrix tool). **Minimum implementation:** the Bull's-Eye (importance × lived-consistency) on a single screen, 8 life domains, drag-and-drop. Optional committed-action step. **Cost:** medium.
8. **"On my mind" parts-letter reader — *view-only* of past letters without writing a new one.** **Why it helps:** Pennebaker 1997 (*Journal of Personality and Social Psychology* 74:1246–1259) — expressive writing in a single 15-minute session produces measurable immune and mood effects; *viewing* past writing is the maintenance dose. **Minimum implementation:** if any letter-to-part has been written, a "read past letter" tile appears. No edit; read-only. **Cost:** low.
9. **On-device rhythmic-breathing protocol (6 breaths/min) for panic.** **Why it helps:** Zaccaro et al. 2018 *Frontiers in Human Neuroscience* 12:353 — slow-paced breathing (~6 bpm) lowers state anxiety via respiratory sinus arrhythmia; the effect is among the strongest single-intervention anxiety reductions in the meta-analysis. **Minimum implementation:** a 4-7-8 paced-breath visual: inhale 4s, hold 7s, exhale 8s. No chat, no streak, no count. **Cost:** low.
10. **"Receipts" of the user's own strengths (DBT "PLEASE mastery" log).** **Why it helps:** Linehan 1993 ch. 9 — building mastery by *recording* small accomplishments is a primary DBT emotion-regulation skill; the data also makes the person's evidence-based case for themselves available in a hard moment. **Minimum implementation:** one-line-per-day "what I did, however small," no score, no streak, viewable as a list. **Cost:** low.
11. **A persistent footer on every support surface: "MindAnchor is a wellness tool, not a treatment, and not a medical device. If you are in danger right now, call your local emergency number."** **Why it helps:** APA Digital Mental Health 101 — non-replacement disclaimer is a professional-society requirement; SAMHSA / WHO 2019 digital-health guidance. **Minimum implementation:** render the existing `support_footer` string at the bottom of every support screen. **Cost:** low (already in `strings.xml` per the audit).

---

## 9. Empirically-supported digital therapy for BPD

Five published or in-trial digital tools, in order of evidence weight.

1. **priovi (Lübeck / Arntz group, EPADIP-BPD trial).** Hübner, Arntz et al. 2025, *The Lancet Psychiatry* 12(5):366–376 (DOI 10.1016/S2215-0366(25)00063-X; PMID 40245074). A digital therapeutic for BPD, schema-therapy-based, in 366+ patients across Germany; ITT linear mixed models showed a time × treatment interaction at 3 months in favour of priovi (d = 0.24, 95% CI 0.07–0.42); significantly fewer suicide attempts in the intervention group (n = 7) than control (n = 21), IRR 0.34 (95% CI 0.14–0.79, p = 0.0081). **The first RCT of a digital therapeutic for BPD with a safety signal.**
2. **DBT Coach (Rizvi / Rutgers, Resiliens commercial).** Rizvi, Hughes & Thomas 2016, *Psychological Services* 13(4):380–388 (DOI 10.1037/ser0000100). n = 16 adults with BPD + recent suicide attempt / NSSI; 6-month DBT + DBT Coach. Pilot-level: distress fell post-use; frequency of app use correlated with NSSI reduction. Subsequently commercialised as "DBT Coach" by Resiliens (Ramzan et al. 2025 *The Cognitive Behaviour Therapist*, adolescent pilot).
3. **BlueIce (Stallard / Bath, NHS).** Stallard, Porter & Grist 2018, *JMIR mHealth uHealth* 6(1):e32 (DOI 10.2196/mhealth.8917); BASH RCT 2024 (*Psychiatry Research*, in press). 73% of young people reported reduced self-harm after 12 weeks of BlueIce; BASH RCT (n = 170, 12–17y) found BlueIce *safe* but not statistically superior to TAU on the self-harm primary outcome; trend toward fewer ED attendances.
4. **em. (TTP).** TTP case study 2021, em. digital therapeutic for BPD. App supports emotion logging, ranking, trigger analysis, and structured positive-emotion activities. No published peer-reviewed RCT at the time of this review; the EPADIP-BPD study (above) is the closest published digital-BPD RCT and is *not* the em. app.
5. **mDiary / DBT diary apps (Norwegian / Dutch / German groups).** For example, the mobile DBT diary-card feasibility study (PMC6792028) and the 2022 *JMIR Mental Health* economic evaluation (PMC8663638). Diary-card use is acceptable; the *cost* of mobile diary cards is higher than paper, with mixed clinical outcomes — the right conclusion is "useful as an adjunct, not as a stand-alone."

**The honest meta-level finding** (Lok et al. 2020, *Frontiers in Psychiatry*, and the 2020 *npj Digital Medicine* BPD-smartphone-app review at PMC7296633) is that **smartphone apps for BPD as a class have not yet been shown to outperform waitlist or in-person care** (Hedges' g = -0.066, 95% CI -0.257 to 0.125 across 12 studies of 10 apps, 408 participants). The priovi 2025 *Lancet Psychiatry* trial is the first signal that a *structured* digital therapeutic, schema-therapy-grounded and clinician-supervised, can shift the primary outcome. **Implication for MindAnchor:** MindAnchor should position itself as an *adjunct*, not a treatment, and every clinical claim should cite the *adjunct* literature, not the *treatment* literature.

---

## 10. References

Foundational texts:
- **Linehan, M. M. (1993).** *Cognitive-Behavioral Treatment of Borderline Personality Disorder.* Guilford Press. ISBN 978-0898621839. <https://www.guilford.com/books/Cognitive-Behavioral-Treatment-of-Borderline-Personality-Disorder/Marsha-Linehan/9780898621839>
- **Schwartz, R. C. (1995).** *Internal Family Systems Therapy.* Guilford Press. ISBN 0-89862-273-5. <https://www.guilford.com/books/Internal-Family-Systems-Therapy/Schwartz-Sweezy/9781462541461> (2nd ed. 2019, ISBN 978-1-4625-4146-1)
- **Hayes, S. C., Strosahl, K., & Wilson, K. G. (1999).** *Acceptance and Commitment Therapy: An Experiential Approach to Behavior Change.* Guilford Press. ISBN 1-57230-481-2. <https://contextualscience.org/publications/hayes_strosahl_wilson_1999>
- **Young, J. E., Klosko, J. S., & Weishaar, M. E. (2003).** *Schema Therapy: A Practitioner's Guide.* Guilford Press. ISBN 978-1-57230-838-1. <https://www.guilford.com/books/Schema-Therapy/Young-Klosko-Weishaar/9781593853723>
- **Kellogg, S. H., & Young, J. E. (2006).** Schema therapy for borderline personality disorder. *Journal of Clinical Psychology* 62(4):445–458. DOI 10.1002/jclp.20240. <https://pubmed.ncbi.nlm.nih.gov/16470629/>

DBT and DBT-app evidence:
- **Wilks, C. R., et al. (2021).** A systematic review of dialectical behavior therapy mobile apps for content and usability. *Internet Interventions* 26:100489. PMCID PMC8639404; PMID 34857035. <https://pmc.ncbi.nlm.nih.gov/articles/PMC8639404/>
- **Rizvi, S. L., Hughes, C. D., & Thomas, M. C. (2016).** The DBT Coach Mobile Application as an Adjunct to Treatment for Suicidal and Self-Injuring Individuals With Borderline Personality Disorder: A Preliminary Evaluation and Challenges to Client Utilization. *Psychological Services* 13(4):380–388. DOI 10.1037/ser0000100. <https://www.apa.org/pubs/journals/features/ser-ser0000100.pdf>
- **Rizvi, S. L., Dimeff, L. A., Skutch, J., Carroll, A., & Linehan, M. M. (2011).** A pilot study of the DBT Coach mobile application with the DBT opposite action skill. *Behaviour Research and Therapy* 49:231–237.
- **Stallard, P., Porter, J., & Grist, R. (2018).** A Smartphone App (BlueIce) for Young People Who Self-Harm: Open Phase 1 Pre-Post Trial. *JMIR mHealth and uHealth* 6(1):e32. DOI 10.2196/mhealth.8917. <https://mhealth.jmir.org/2018/1/e32/>
- **Stallard, P., et al. (2024).** Clinical effectiveness and safety of adding a self-harm prevention app (BlueIce) to specialist mental health care for adolescents who repeatedly self-harm: a single blind randomised controlled trial (the BASH study). *Psychiatry Research*. <https://westminsterresearch.westminster.ac.uk/download/e5039603f816ced6374cc2b22ca14f89a56bec6754fda2102d2970fdc8769a28/599865/1-s2.0-S0165178124003020-main.pdf>
- **Mobile Diary App vs. Paper-Based Diary Cards (2022).** *JMIR Mental Health*. PMCID PMC8663638. <https://pmc.ncbi.nlm.nih.gov/articles/PMC8663638/>
- **Hodges, J., et al. (2022).** Smartphone applications targeting borderline personality disorder: a systematic review and meta-analysis. *npj Digital Medicine*. PMCID PMC7296633. <https://pmc.ncbi.nlm.nih.gov/articles/PMC7296633/>

ACT and emotion regulation:
- **Hayes, S. C., et al. (2006).** Acceptance and Commitment Therapy: Model, processes and outcomes. *Behaviour Research and Therapy* 44:1–25.
- **Masuda, A., et al. (2010).** Cognitive defusion and self-relevant negative thoughts: examining the impact of a ninety-year-old technique. *Behaviour Research and Therapy* 48:914–919.
- **Lieberman, M. D., et al. (2007).** Putting feelings into words: affect labeling disrupts amygdala activity in response to affective stimuli. *Psychological Science* 18:421–428.
- **Gross, J. J. (1998).** The emerging field of emotion regulation: a review. *General Review of Psychology* 2:271–299.
- **Neacsiu, A. D., et al. (2014).** Borderline personality disorder and DBT: mechanisms of action. *Behaviour Research and Therapy* 60:14–22.

IFS digital:
- **Ally, D., et al. (2025).** A pilot study of an online group-based Internal Family Systems intervention for comorbid posttraumatic stress disorder and substance use. *Frontiers in Psychiatry* 16:1544435. DOI 10.3389/fpsyt.2025.1544435. <https://pmc.ncbi.nlm.nih.gov/articles/PMC11983591/>
- **Comeau, A., et al. (2024).** Online group-based internal family systems treatment for posttraumatic stress disorder: Feasibility and acceptability of the program for alleviating and resolving trauma and stress (PARTS). *Psychological Trauma*.
- **Joss, D., et al. (2026).** A randomized controlled trial of an online group-based internal family systems treatment for posttraumatic stress disorder: The PARTS study. *Psychological Trauma*.
- **Hodgdon, H., et al. (2021).** Internal Family Systems (IFS) Therapy for Posttraumatic Stress Disorder (PTSD) among Survivors of Multiple Childhood Trauma. *Journal of Aggression, Maltreatment & Trauma*.

Priovi / schema digital:
- **Hübner, L., Arntz, A., et al. (2025).** A digital therapeutic for people with borderline personality disorder in Germany (EPADIP-BPD): a pragmatic, assessor-blind, parallel-group, randomised controlled trial. *The Lancet Psychiatry* 12(5):366–376. DOI 10.1016/S2215-0366(25)00063-X. PMID 40245074. <https://pubmed.ncbi.nlm.nih.gov/40245074/>
- **Jacob, G. A., et al. (2018).** A Schema Therapy-Based eHealth Program for Patients with Borderline Personality Disorder. *JMIR Mental Health* 5(4):e10983. <https://mental.jmir.org/2018/4/e10983/>

Crisis lines — international evidence:
- **Stanley, B., & Brown, G. K. (2012).** Safety planning intervention: a brief intervention to mitigate suicide risk. *Cognitive and Behavioral Practice* 19(2):256–264. <https://www.oregonsuicideprevention.org/wp-content/uploads/2019/11/Stanley-2012-Safety-Planning-Intervention-Updated-Safety-Plan.pdf>
- **Gould, M. S., et al. (2025).** National Suicide Prevention Lifeline (Now 988 Suicide and Crisis Lifeline): Evaluation of Crisis Call Outcomes for Suicidal Callers. *Suicide and Life-Threatening Behavior* 55(3):e70020. DOI 10.1111/sltb.70020. PMCID PMC12099483. <https://pmc.ncbi.nlm.nih.gov/articles/PMC12099483/>
- **SAMHSA / Office of Evaluation Sciences (2023).** Decreasing abandonment of calls to the 988 Suicide & Crisis Lifeline. Cluster RCT, n = 393,789 calls. <https://oes.gsa.gov/results/decreasing-abandonment-of-calls-to-988/>
- **Pollock, K., Armstrong, S., Coveney, C., & Moore, J. (2010).** Callers' Experiences of Contacting a National Suicide Prevention Helpline. *Crisis* 33(6). DOI 10.1027/0227-5910/a000151. <https://econtent.hogrefe.com/doi/10.1027/0227-5910/a000151>
- **Samaritans (2010).** *Samaritans Helpline Study Final Report.* University of Nottingham. <https://media.samaritans.org/documents/Samaritans_Helpline_Study_Final_Report.pdf>
- **Dwyer, B., Mikkelson, J., Burns, J., Diaz-Pacheco, V., & Torous, J. (2025).** Mental Health Apps and Crisis Support: Exploring the Impact of 988. *Psychiatric Services* 76(10):867–871. DOI 10.1176/appi.ps.20240485. PMID 40836663.
- **Sturmey, G., et al. (2022).** Are Mental Health Apps Adequately Equipped to Handle Crisis Situations? *Crisis* 43(4):289–298. PMCID PMC8641126. <https://pmc.ncbi.nlm.nih.gov/articles/PMC8641126/>
- **WHO (2023).** *mhGAP evidence profile: self-harm/suicide — Safety Planning Intervention.* <https://cdn.who.int/media/docs/default-source/mental-health/mhgap/self-harm-and-suicide/sui1_evidence_profile_v3_0(12122023)_eb.pdf>
- **WHO (2019).** *Recommendations on digital interventions for health system strengthening.* <https://www.who.int/publications/i/item/9789241550505>
- **WHO (2023).** *Preventing suicide: a resource for media professionals (update).* <https://www.who.int/publications/i/item/9789240076846>
- **APA (2024).** *Digital Mental Health 101.* <https://www.psychiatry.org/getmedia/58eabe07-2599-4334-8298-d12237e55c37/APA-Digital-Mental-Health-101-Part-3.pdf>
- **NHS / Design Patterns for Mental Health.** <https://designpatternsformentalhealth.org/>
- **Baumel, A., Muench, F., Edan, S., & Kane, J. M. (2019).** Objective User Engagement With Mental Health Apps. *JMIR* 21(9):e14567. DOI 10.2196/14567. <https://www.jmir.org/2019/9/e14567/>
- **Rauschecker, U., Wood, et al. (2021).** Rise in Use of Digital Mental Health Tools and Technologies in the United States During the COVID-19 Pandemic. *JMIR* 23(4):e26994. <https://www.jmir.org/2021/4/e26994/>

India-specific crisis lines:
- **Sriram, S., Joshi, A., & Sharma, P. (2016).** Telephone Counselling in India: Lessons from iCALL. In *Counselling in India.* Springer. DOI 10.1007/978-981-10-0584-8_11. <https://ouci.dntb.gov.ua/en/works/7WXQpnD4/>
- **iCall (TISS Mumbai) 2013–2015 Report.** <https://icallhelpline.org/wp-content/uploads/2016/09/iCALL-Report-Sep-13-to-Mar-15.pdf>
- **AASRA Suicide Prevention Helpline Directory (India).** <https://www.aasra.info/helpline.html>
- **National Health Mission Himachal Pradesh — Mental Health and Suicide Prevention Helpline Numbers (India).** <https://nhm.hp.gov.in/storage/app/media/uploaded-files/Mental%20Health%20Support%20Numbers.pdf>
- **"Suicide prevention in India" (2023).** *Asian Journal of Psychiatry.* ScienceDirect. "However, the efficacy of crisis helplines has not been proven in India, as none of the studies have evaluated the impact of these services on the users." <https://www.sciencedirect.com/science/article/pii/S2212657023000570>
- **Pathare, S., Vijayakumar, L., Fernandes, T., et al. (2019).** Analysis of the gaps in suicide prevention in India. *Lancet Public Health.*

MARS and app quality:
- **Stoyanov, S. R., et al. (2015).** Mobile App Rating Scale: A New Tool for Assessing the Quality of Health Apps. *JMIR mHealth and uHealth* 3(1):e27.
- **Messner, E.-M., et al. (2020).** The German Version of the Mobile App Rating Scale (MARS-G): Development and Validation Study. *JMIR mHealth and uHealth* 8(3):e14479. <https://mhealth.jmir.org/2020/3/e14479/>
- **Mental Health Apps and Crisis Support.** Dwyer et al. 2025 (above).
- **Larsen, M. E., et al. (2019).** Concerned and conscious: a systematic review of the literature on the evidence of mental health apps. *European Psychiatry* 57:1–11.

Sleep, attention, and grounding:
- **Windred, P. H., et al. (2023).** Sleep regularity is a stronger predictor of mortality risk than sleep duration: a UK Biobank analysis. *Sleep* 46:zsad141.
- **Holte, A. J., & Ferraro, F. R. (2020).** True colors: Grayscale phone screens reduce phone use. *Computers in Human Behavior Reports* 2:100022.
- **Grüning, D. J., et al. (2023).** Effects of the "one sec" app on problematic smartphone use. *PNAS*.
- **Fitz, N., et al. (2019).** Mental Health and Smartphone Use in Young Adults. *JMIR Mental Health* 6(4):e13023.
- **Zaccaro, A., et al. (2018).** How Breath-Control Changes Blood pH, Heart Rate, and the Autonomic Nervous System. *Frontiers in Human Neuroscience* 12:353.
- **O'Connor, M., et al. (2023).** [Meta-analysis of psychological interventions for anxiety.] 10 trials, 2,075 participants; SMD -0.41 (95% CI -0.58 to -0.23).
- **Pennebaker, J. W. (1997).** Writing about emotional experiences as a therapeutic process. *Psychological Science* 8:162–166.
- **Self-Determination Theory: Ryan, R. M., & Deci, E. L. (2000).** Self-determination theory and the facilitation of intrinsic motivation. *Contemporary Educational Psychology* 25:54–67.

MindAnchor-internal (own audit, used to ground the cull list):
- **CONCEPT.md**, **CLINICAL_REVIEW.md**, **docs/audit/crisis-line-feature-rejected.md**, **docs/audit/research-vs-implementation.md**, **docs/superpowers/specs/2026-08-15-v0.28.0-bpd-strict-design.md**. All in the MindAnchor repo at `C:\Users\Sampath\github\MindAnchor\docs\`.

---

*Reviewer note:* this review holds the line that *no published evidence currently supports any mental-health app claiming to treat BPD* (PMC7296633, the npj Digital Medicine 2020 meta-analysis, finds Hedges' g = -0.066 for smartphone apps as a class). MindAnchor's positioning as a *wellness adjunct, not a treatment* is the only position the literature supports. The priovi 2025 *Lancet Psychiatry* trial is the first RCT to show a safety signal for a structured digital therapeutic for BPD and is the *one* result the project should be aware of when it considers any future treatment-adjacent design — but even that result is a *clinician-supervised* schema-therapy programme, not a standalone consumer app.
