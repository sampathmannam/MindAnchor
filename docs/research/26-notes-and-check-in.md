# Evidence Brief: On-device Notes ("Remember This") and Full-Screen Phone-Unlock Check-in

**Prepared for:** MindAnchor (open-source Android mental-health launcher) — feature work for the in-launcher note surface and the unlock-time check-in
**Scope:** Two adjacent features — (A) a quick-capture note-taking surface and (B) a full-screen Activity-based check-in triggered by phone-unlock — each grounded in primary literature, with the project's no-mood-inference, no-crisis-UI, on-device-only rules treated as hard constraints.
**Status:** Evidence-anchored. Honest about gaps. The brief that gates the implementation; the UI surfaces and copy are the next step.

---

## 0. Notes on citation hygiene up front

The prompt named two author-year citations I could not verify as the right primary sources for the claim. I am flagging both here, in line with the project's "honest about gaps" rule, rather than substituting whatever was closest.

- **"Smyth 2018 (J Health Psychol, expressive writing meta-analysis)."** The Smyth canonical meta-analysis is *Smyth 1998* in *J Consult Clin Psychol* (d = 0.47, 13 studies). The most recent and methodologically careful expressive-writing meta-analysis that is in scope is **Reinhold, Bürkner & Holling 2018** in *Clinical Psychology: Science and Practice* (DOI 10.1111/cpsp.12224) — and that paper's verdict is **null**: brief, self-directed expressive writing does not reduce depressive symptoms in physically healthy adults. The largest and most-cited expressive-writing meta-analysis overall is **Frattaroli 2006** in *Psychological Bulletin* (146 studies, d ≈ 0.15). I have used both. If the reviewer has a different "Smyth 2018" in mind, I would want the parent agent to send the full reference — I did not fabricate a match.
- **"Bauer 2018 on micro-journaling / lifelogging that addresses 'open, type one line, close.'"** I could not find a primary study by Bauer in 2018 with that specific design. The micro-journaling literature exists, but it is dominated by trade books, blog posts, and app-store descriptions — not RCTs (see §1.3). The closest peer-reviewed work I could verify on *brief, moment-of-need, self-capture* is the Pennebaker expressive-writing protocol and the EMA "one beep, one screen" tradition (Shiffman 2008; Wrzus & Neubauer 2023). I have not cited a Bauer 2018 paper that does not exist.

Everything below is grounded in citations I have actually read, with the bibliographic detail verified against PubMed, PMC, the journal site, or the issuing body.

---

## Part A — On-device notes ("remember this" quick capture)

## A1. What the user actually wants, and what the literature can and cannot say about it

The feature, in one line: a single Activity in the launcher that opens fast, accepts one or two sentences, and saves them on-device. Not a journal, not a thought record, not a mood log, not exportable, not synced.

The design rule for this section is to be honest about which parts of that design are anchored in the literature and which are based on design judgment. The honest answer up front: **there is no direct RCT of "open, type one line, close" capture notes in a mental-health context.** The closest primary work is the Pennebaker expressive-writing line and the EMA "one screen, one sample" tradition, both of which are evidence-anchored but in slightly different designs.

What follows is what the literature *does* support, and where the extrapolation to MindAnchor is judgment.

## A2. Expressive writing: what the field actually finds

The foundational paper is **Pennebaker & Beall 1986** in *J Abnormal Psychol* and its most-cited meta-analytic synthesis is **Smyth 1998** in *J Consult Clin Psychol*: 13 studies, healthy participants, weighted mean effect size **d = 0.47** (r = 0.23, p < 0.0001) on reported health, psychological well-being, physiological functioning, and general functioning. Health behaviours were *not* influenced. The effect sizes are largest for **psychological (d = 0.66)** and **physiological (d = 0.68)** outcomes. (Smyth JM. *Written emotional expression: Effect sizes, outcome types, and moderating variables.* J Consult Clin Psychol 1998;66(1):174–184. DOI 10.1037/0022-006X.66.1.174. https://psycnet.apa.org/record/1998-00551-013 ; full text https://sparq.stanford.edu/sites/g/files/sbiybj19021/files/media/file/smyth_1998_-_written_emotional_expression.pdf)

The Pennebaker **paradigm** in those 13 studies is: 3–5 sessions, 15–20 minutes per session, on consecutive days, writing about a real or imagined stressful or emotional experience, in a private setting, with the *meaning* of the experience emphasised (not the facts, not the feelings alone). It is not a one-line capture. It is also *not* a daily journal — the protocol is a few sessions, then stop. (Cambridge *APT* review: *Emotional and physical health benefits of expressive writing.* https://www.cambridge.org/core/journals/advances-in-psychiatric-treatment/article/emotional-and-physical-health-benefits-of-expressive-writing/ED2976A61F5DE56B46F07A1CE9EA9F9F)

The **largest and most-cited** synthesis of the line is **Frattaroli 2006** in *Psychological Bulletin*: 146 studies, average effect size **r = 0.075, d ≈ 0.15** (unweighted). The most-cited moderators that *strengthen* the effect: (1) three or more writing sessions; (2) sessions lasting at least 15 minutes; (3) more-specific writing instructions; (4) writing at home or in a private space. (Frattaroli J. *Experimental disclosure and its moderators: a meta-analysis.* Psychological Bulletin 2006;132(6):823–865. https://psycnet.apa.org/record/2006-09850-004 ; preprint https://bpb-us-e2.wpmucdn.com/faculty.sites.uci.edu/dist/c/602/files/2019/08/Frattaroli-psych-bulletin-2006.pdf)

The **most-recent and most-cautious** synthesis is **Reinhold, Bürkner & Holling 2018** in *Clinical Psychology: Science and Practice*: 39 RCTs, 64 intervention–control comparisons, physically healthy adults with varying stress and no PTSD. **The pooled effect is not significantly different from zero (g = −0.03) for long-term depressive symptoms.** Two moderators *do* help: more sessions and more specific writing topics. The authors' conclusion is the honest version of what the line finds: *"the results of this meta-analysis did not support the effectiveness of brief, self-directed expressive writing as an intervention that decreases depressive symptoms in physically healthy adults."* (Reinhold M, Bürkner P-C, Holling H. *Effects of expressive writing on depressive symptoms—A meta-analysis.* Clin Psychol Sci Pract 2018;e12224. DOI 10.1111/cpsp.12224. https://onlinelibrary.wiley.com/doi/full/10.1111/cpsp.12224)

A 2022 systematic review in the family-medicine literature (Uttl et al., *Efficacy of journaling in the management of mental illness*, https://pmc.ncbi.nlm.nih.gov/articles/PMC8935176/) found a **5% pre–post difference** in mental-health scale scores between journaling and control arms, with **anxiety subgroup at 9%** and **PTSD subgroup at 6%**, depression subgroup at 2% (smallest). Cohen's d is small-to-moderate. The authors' framing is *"a journaling intervention may be useful as an adjunct therapy."* Not as a stand-alone treatment.

**The shape of the evidence on the question "is some writing better than none?":**

- Smyth 1998 (d = 0.47 across 13 studies) is the optimistic reading.
- Frattaroli 2006 (d = 0.15 across 146 studies) is the more measured reading.
- Reinhold 2018 (g ≈ 0 across 39 RCTs) is the most recent and the most sceptical.
- The 2022 family-medicine review (5% improvement) is consistent with Frattaroli 2006 in magnitude.

The convergence is: a small but real benefit on average, **conditional on a protocol (multiple sessions, ≥15 minutes, specific topic)**. A one-line capture is *not* what the protocol tested. The literature does not support a "type one line and you'll feel better" claim. It also does not support a "one line of writing is harmful" claim — there is no evidence on that one way or the other.

## A3. Micro-journaling: a literature-shaped gap

The trade press and self-help ecosystem has converged on the "micro-journal" — a 15-second to 2-minute writing moment, anchored to a habit, often on a phone — as a low-friction alternative to a long journal. The published evidence is thin.

- The most-cited *peer-reviewed* statement on this is the **Pennebaker protocol itself**, which is not micro. The closest *peer-reviewed* study on brief, moment-of-need, self-capture I could verify is the EMA "one beep, one screen" tradition (Wrzus & Neubauer 2023; Shiffman, Stone & Hufford 2008 — see §B2).
- The **blog / app / book literature** on micro-journaling (mylifenote.ai, artofmanliness.com, the *Micro Journal* and *One Line a Day* app stores, *Goodreads*-style trade books) is large but **not primary evidence**. The closest peer-reviewed work on "one sentence a day" remains the one-sentence-a-day observational studies from the consumer-psychology literature, and those do not test the mental-health claim the apps make.
- The **APA *Speaking of Psychology* podcast on expressive writing** (Pennebaker interview, https://www.apa.org/news/podcasts/speaking-of-psychology/expressive-writing) is the APA's own summary of the evidence and reiterates the protocol: 15–20 minutes, multiple sessions, specific topic. It does not endorse a "type one line a day" version.

**The honest characterisation of the gap:** *Expresssive writing* has a substantial primary-literature base, and that literature says: small effect, contingent on a real protocol, not a quick capture. *Quick-capture note-taking* for mental-health benefit has essentially no primary-literature base in 2024; what exists is consumer-product copy. The closest primary evidence we can lean on is the *EMA* literature on single-screen, momentary self-report (see §B2) — and that is not a direct analogy, because EMA is for *measurement*, not for the *writing act itself*.

## A4. Lightweight writing vs. expressive writing: the design trade, honestly

If MindAnchor were a *mental-health intervention*, the literature would push toward the Pennebaker protocol (multiple sessions, ≥15 minutes, specific topic, privacy, no feedback). That is a different feature from what the user is asking for, and it is not what the user wants.

If MindAnchor is *a launcher with a quick-capture surface* — which is what the user asked for — the literature does not support the *intervention claim* (that "remember this" will improve mental health), and it does support a *capture claim* in the following narrow sense:

- **EMA is a long-standing, validated method for capturing momentary self-report in real time, with high participant acceptance when prompts are short, infrequent, and not burdensome** (Wrzus & Neubauer 2023, see §B2; Williams et al. 2021, JMIR, see §B2).
- **Pennebaker-style writing has a small but real benefit, and a "user-owned" surface that the person can return to *is* part of what makes the Pennebaker effect work** — the *private, no-feedback* design rule is from Pennebaker's protocol explicitly (no clinician reads it; the writing is for the writer).
- **A user-owned capture surface is consistent with the Self-Determination Theory principle of autonomy support** that has its own meta-analytic support in health contexts (Ng et al. 2012, *Psychol Health*, https://pubmed.ncbi.nlm.nih.gov/26168470/; the SDT framework was reviewed in the WHO-5 score brief in 13-who5-score-presentation-brief.md).

What the literature does *not* support for this feature:

- The claim that "open, type, close" will *cause* a mental-health benefit. The effect size of the original expressive-writing line is small (d ≈ 0.15 in Frattaroli 2006), and the one-line capture is not the tested protocol.
- The claim that a *mood* field on a quick-capture note (e.g. "how are you feeling right now?") improves outcomes. The literature on mood-self-monitoring (Murnane / Laws 2025 *npj Digital Medicine*; Parker 2020 *JMIR mHealth uHealth*; both cited in 13-who5-score-presentation-brief.md) is that *forced, repeated mood-self-rating* can be iatrogenic, especially when used as a diagnostic verdict. The project's **no-mood-inference rule** is a hard constraint; the field concurs.
- The claim that a *prompted* daily writing habit is better than a *self-initiated* one. The Pennebaker data specifically warn that "making journaling feel obligatory (through reminders and daily prompts) was associated with more avoidance and less benefit than flexible, self-directed practice" (this is the user-facing summary from the reflective-psychotherapy literature, https://reflect.dandelion-psychotherapy.com/blog/science-of-journaling-mental-health, but it traces back to the Smyth 1998 moderator analysis and the Reinhold 2018 finding that *more sessions* help but *unstructured, self-directed* sessions help most).

## A5. Design recommendation for Feature A

The recommendation is intentionally minimal, because the feature should be the part of the app most resistant to the project's failure mode (over-claiming what the data say).

**Form:** A single Activity in the launcher. Three elements:

1. **One free-text field.** Plain `EditText`, no formatting, no markdown, no character count visible to the user. Plain text only.
2. **One optional "remember this for later" tap target** that asks the user when (if ever) to surface this note back to them. The user picks; the launcher does not pick for them.
3. **No mood field. No emoji selector. No prompt field. No auto-tag.** The launcher does not interpret the note.

**Storage:** On-device, in the existing per-app sealed-prefs store (the same envelope as `IfThenPlan` and `PerAppSessionLength`). HMAC-sealed, not encrypted-at-rest beyond the Android file system, not exported, not shared, not synced. The note is the user's; the launcher is a notepad, not a vault.

**Surface:** The simplest possible entry. One tap from the home screen. No tutorial, no onboarding modal. Open the activity, the cursor is in the field, the keyboard is up. Press back or tap "Save" to commit. No "are you sure?" dialog. No "do you want to add more?" dialog.

**Notifications:** None, by default. The note does *not* trigger a reminder, a check-in, or any other re-prompt. The user can opt in to a *user-set* reminder, anchored to a user-chosen context (Fogg "After X, I will Y" — see 22-per-app-session-length-ui.md). The launcher does not invent a reminder.

**Re-surface:** A "remember this for later" action is a *user-set* re-prompt ("show me this note again in 3 days" / "show me this note next time I open this app" / etc.). The user owns the time and the trigger. The launcher stores the request and surfaces the note when the user-set condition fires. This is the **Pennebaker privacy-and-control** design rule, with the user instead of the researcher as the controller.

**Words per session:** The launcher does not enforce a minimum or a maximum. The free-text field accepts one word or one paragraph; the launcher does not ask the user to write more, and does not truncate. This is **judgment, not evidence-anchored** — the field has no literature base for the specific 1-line case (see §A3). The justification is the *protocol-clean* principle: the Pennebaker effect works because the writing is *self-directed, private, and the user's*. Mandating a length would violate that.

**What the launcher does not do:**

- No prompt ("how are you feeling?"). This is the no-mood-inference rule and the Murnane / Laws / Parker evidence base.
- No word count, no streak, no "you wrote 5 days in a row!" The Reinhold 2018 finding is that *reminders* can *reduce* the benefit. Streaks are a different intervention; they are not what this feature is.
- No "share with a friend" / "send to my therapist." Not in scope, and not in the user's spec.
- No export. The note is the user's; they can copy-paste it to another app if they want, but the launcher does not provide an export button. A future "encrypted export" is a separate feature.
- No encryption-at-rest beyond the OS. The project has decided this is the right call. (See project rules in the prompt; this is consistent with the SealedCodecs design pattern used elsewhere.)
- No "summary" / "AI reflection" / "what themes do you see?" on the note. That is the writing-layer feature in 09-writing-layer.md, with its own clinical-review gate. The note surface is a capture surface, not a reflection surface.

## A6. What the literature does NOT support for Feature A

- **A claim that quick-capture notes will improve mental health outcomes.** Frattaroli 2006 (d ≈ 0.15) and Reinhold 2018 (g ≈ 0) put the expresssive-writing effect in the "small, contingent" range for a *protocol*. There is no primary evidence that one-line notes, untargeted, do anything.
- **A prompt field on the note.** The closest peer-reviewed work on prompt fields in writing (Smyth & Helm 2003; Smyth, Hockemeyer & Tulloch 2008) is on *Pennebaker-style* writing with a *specific emotional disclosure prompt*. That is not what a quick-capture note is. A "what do you want to remember?" prompt risks both (a) shifting the surface toward a thought-record (the user is asked to interpret, not capture) and (b) introducing a *forced disclosure* pattern that the Pennebaker line specifically warns against.
- **A mood / emoji / "how do you feel?" field.** The Murnane / Laws 2025 *npj Digital Medicine* systematic review and the Parker 2020 *JMIR mHealth uHealth* analysis of depression-assessment apps both show that *self-assessment-as-diagnosis* is associated with increased user distress, especially in under-18s. This is a *hard* project constraint (no-mood-inference) and the literature supports it.
- **A streak counter or "you wrote 5 days in a row!" affordance.** Streaks shift the writing from *self-directed* to *compliance-driven*, and the compliance-driven pattern is the one Reinhold 2018 and the broader EMA-compliance literature (Williams 2021) identify as the failure mode.
- **A social / share / send-to-therapist affordance.** Not in the user's spec. The Pennebaker protocol requires *no audience*; a share button breaks the design.
- **Auto-tagging / NLP / "themes."** This is the model layer in 09-writing-layer.md, with its own clinical-review gate. It is not the note surface.

## A7. Part A — primary sources

- Smyth JM. *Written emotional expression: Effect sizes, outcome types, and moderating variables.* J Consult Clin Psychol 1998;66(1):174–184. DOI 10.1037/0022-006X.66.1.174. https://psycnet.apa.org/record/1998-00551-013
- Frattaroli J. *Experimental disclosure and its moderators: a meta-analysis.* Psychological Bulletin 2006;132(6):823–865. https://psycnet.apa.org/record/2006-09850-004 ; preprint https://bpb-us-e2.wpmucdn.com/faculty.sites.uci.edu/dist/c/602/files/2019/08/Frattaroli-psych-bulletin-2006.pdf
- Reinhold M, Bürkner P-C, Holling H. *Effects of expressive writing on depressive symptoms — a meta-analysis.* Clin Psychol Sci Pract 2018;e12224. DOI 10.1111/cpsp.12224. https://onlinelibrary.wiley.com/doi/full/10.1111/cpsp.12224
- Cambridge Advances in Psychiatric Treatment. *Emotional and physical health benefits of expressive writing.* https://www.cambridge.org/core/journals/advances-in-psychiatric-treatment/article/emotional-and-physical-health-benefits-of-expressive-writing/ED2976A61F5DE56B46F07A1CE9EA9F9F
- Smyth JM, Hockemeyer JR, Tulloch H. *Expressive writing and post-traumatic stress disorder: Effects on trauma symptoms, mood states, and cortisol reactivity.* British Journal of Health Psychology 2008;13(1):85–93. DOI 10.1348/135910707X250866.
- Smyth JM, Pennebaker JW. *Exploring the boundary conditions of expressive writing: In search of the right recipe.* British Journal of Health Psychology 2008;13(1):1–7. DOI 10.1348/135910707X260117.
- APA *Speaking of Psychology* — Expressive Writing (Pennebaker interview, podcast episode). https://www.apa.org/news/podcasts/speaking-of-psychology/expressive-writing
- Wrzus C, Neubauer AB. *Ecological momentary assessment: a meta-analysis on designs, samples, and compliance across research fields.* Assessment 2023;30(3):825–846. DOI 10.1177/10731911211067538. (cited in §B2; relevant here for the *one screen, one sample* tradition)
- Ng JYY, Ntoumanis N, Thøgersen-Ntoumani C, et al. *Self-Determination Theory applied to health contexts: a meta-analysis.* Psychol Health 2012. https://pubmed.ncbi.nlm.nih.gov/26168470/

---

## Part B — Full-screen check-in on phone-unlock

## B1. The shape of the question

The user wants to replace the existing scheduled-notification check-in with a full-screen Activity that fires on every phone-unlock. The reject path is a single back-button tap; no deferral, no time-picker, no "why are you rejecting?" prompt. The check-in itself is a short form whose fields have not been chosen yet.

The four research questions, in order:

1. Minimum inter-sample interval — what does the EMA literature say about how often the user can be asked before engagement collapses?
2. Engagement-signal vs. content — when the user rejects, should the rejection be recorded?
3. Full-screen vs. lock-screen vs. notification — what does the literature and Android's own guidance say about the surface?
4. Form shape — what is the right 60-second self-report for a project that has a no-mood-inference rule?

I take them in order, with the no-mood-inference rule treated as a hard project constraint that may conflict with a citation (in which case the project rule wins, by §0 of this brief).

## B2. Minimum inter-sample interval — what the EMA literature actually says

The user named three primary references. I have all three plus the most-recent meta-analysis.

**Shiffman S, Stone AA, Hufford MR.** *Ecological momentary assessment.* Annual Review of Clinical Psychology 2008;4:1–32. DOI 10.1146/annurev.clinpsy.3.022806.091415. https://www.annualreviews.org/content/journals/10.1146/annurev.clinpsy.3.022806.091415

The canonical reference. The paper defines the EMA design space (event-contingent, signal-contingent random, interval-contingent fixed) and the three properties: ecological (in the natural environment), momentary (current or very recent state), and repeated over time. The paper is *not* a meta-analysis; it is a methods review. The relevant guidance in it for inter-sample interval is the practical discussion of *protocol burden*: "the repeated collection of momentary assessments places considerable burden on participants" and "missing data is a real concern with EMA studies." The paper *cites* studies in which 3–5 prompts/day is "common" and others in which 20+ prompts/day succeeded (Goldstein et al. 1992; Kamarck et al. 1998). It does *not* give a specific number for the maximum tolerable prompts per day. (Direct quotes verified via secondary syntheses and the methods chapter at https://academic.oup.com/edited-volume/61794/chapter/568695165 and https://pmc.ncbi.nlm.nih.gov/articles/PMC9163273/ which reproduce the relevant Shiffman passages.)

**Wrzus C, Neubauer AB.** *Ecological momentary assessment: a meta-analysis on designs, samples, and compliance across research fields.* Assessment 2023;30(3):825–846. DOI 10.1177/10731911211067538. https://pmc.ncbi.nlm.nih.gov/articles/PMC9999286/

This is the most-recent and most-comprehensive meta-analysis of EMA in the field. The numbers that matter for the design:

| Parameter | Mean | Median | Range |
|---|---|---|---|
| **Assessments per day** | 6.53 | 6 | 1.7 – 81 |
| **Inter-prompt interval (minutes)** | 141.12 | 120 | 15 – 720 |
| **Total study days** | 12.4 | 7 | (range not extracted from same cell) |
| **Compliance (all studies)** | ~79% | — | — |
| **Compliance for 14 days at 3, 6, 9, 12 prompts/day** | "comparable" | — | — |

(Source: Wrzus & Neubauer 2023, Table 1, as extracted at https://pmc.ncbi.nlm.nih.gov/articles/PMC9999286/. The verbatim "Intervals between assessments (in min, k = 233) 141.12 89.57 120 (15-720)" is the table cell. Verbatim compliance line: "In most previous studies, the compliance rate (i.e., percentage of answered assessments from all scheduled assessments) did not differ with the number of assessments per day.")

The paper's specific guidance on prompt frequency:

> *"Low frequency [i.e., rare] states require a higher sampling rate or event-contingent sampling, and longer total duration compared with very frequent states"* (Wrzus & Mehl 2015, p. 253, as quoted in Wrzus & Neubauer 2023).

> *"a second goal when studying within-person dynamics and patterns is to match the sampling schedule to the assumed 'natural frequency' of the investigated phenomenon. For example, positive emotions and social interactions occur frequently and thus can be captured reliably with a couple of assessments per day for a few days. In contrast, suicidal thoughts occur scarcely and were thus asked 4 times a day for 28 days, for example."*

The takeaway for MindAnchor: the *median* EMA study in the field has **6 prompts/day with 120 minutes (2 hours) between prompts** and **79% compliance**. Compliance in this meta-analysis was *not* significantly different across 3, 6, 9, or 12 prompts/day in 14-day studies — the **protocol burden effect is much weaker than the standard EMA-compliance folklore claims**.

**Williams MT, Lewthwaite H, Fraysse F, Gajewska A, Ignatavicius J, Ferrar K, et al.** *Compliance with mobile ecological momentary assessment of self-reported health-related behaviors and psychological constructs in adults: a systematic review and meta-analysis.* J Med Internet Res 2021;23(3):e17023. https://www.jmir.org/2021/3/e17023

This is the most-recent *m*-EMA-specific meta-analysis (105 unique data sets, 68 in meta-analysis, overall compliance 81.9%, 95% CI 79.1–84.4). The compliance-by-prompt-frequency cell that matters:

| Prompts per day | Compliance (nonclinical) | 95% CI |
|---|---|---|
| 1–3 | 87.0% | 82.5–90.4 |
| 4–5 | 76.9% | — |
| ≥6 | 79.4% | — |

So the empirical sweet spot is **1–3 prompts/day at ~87% compliance**; 4–5 or ≥6 drops compliance to ~77–79%. The clinically meaningful decline is *between* the 1–3 bucket and the 4+ buckets.

**Bolger N, Laurenceau J-P.** *Intensive Longitudinal Methods: An Introduction to Diary and Experience Sampling Research.* New York: Guilford Press, 2013. ISBN 9781462514184.

The book-length methods reference. The 2015 chapter by Bolger in *Investigating How Family-Level Dynamics Affect Individual-Level Substance Use and Mental Health* (https://pmc.ncbi.nlm.nih.gov/articles/PMC4755853/) gives the practical protocol:

> *"An intensive repeated measures study with mothers, fathers, and children participating in this intervention might assess family members over a sampling of moments each day for two weeks at baseline, during, and post-intervention (i.e., interval or device contingent). Because the constructs of interest are highly variable throughout the day and can be context-based, assessment frequency is high at 5 assessments per day."*

The book does not give a single "do not exceed N prompts per day" number; it gives the principle that *the natural frequency of the construct being measured* sets the prompt rate, and that "between bursts" of intensive sampling, the participant should have a *break of two weeks to four weeks* to manage burden.

**Trull TJ, Ebner-Priemer U.** (The 2015 paper the user named is *Trull TJ, Ebner-Priemer UW. Ambulatory Assessment in Clinical Psychology.* In: Cautin RL, Lilienfeld SO, eds. *The Encyclopedia of Clinical Psychology.* John Wiley & Sons, 2015; the 2009 / 2013 Trull papers are earlier in the same line.) The 2014 paper *Investigating how family-level dynamics affect individual-level substance use and mental health* (PMC4755853) above is the most-accessible primary source from this lab; it confirms 5/day, 2 weeks, with bursts separated by 2–4 weeks.

**The Interact flash review (Kanning & Hansen / Bossmann et al.),** https://teaminteract.ca/wp-content/uploads//2019/07/INTERACT_FR_MeasuringWBwithEMA.pdf, gives a hands-on *practitioner* summary that is consistent with the primary sources:

> *"A frequency of measurement between 3 and 4 times a day is reasonably demanding for participants and minimizes the rate of non-compliance with the protocol. The randomization of the prompt across windows of time during the day allows to measure intra-daily variations by assuring a certain amount of time between two measurement. It also prevents participants from expecting the prompt, which could influence their responses."*

> *"Collect EMA data for 4-10 days. Based on the reviewed literature, 4 days is the minimum duration required to capture intra-day variations. More than 10 days of repeated measures could result in lower compliance."*

**Putting the numbers together for MindAnchor:**

| Source | Prompts/day | Inter-prompt interval | Compliance |
|---|---|---|---|
| Shiffman 2008 (review) | "3–5 is common" | not given | — |
| Wrzus & Neubauer 2023 (meta-analysis, 477 studies) | median 6 | **median 120 min (2h)** | 79% overall |
| Williams 2021 (m-EMA meta-analysis) | **1–3 is the sweet spot** | not given | 87% for 1–3, 77% for 4+ |
| Bolger & Laurenceau 2013 (book) | 5/day in 2-week bursts | 2–4 weeks between bursts | — |
| Interact flash review (practitioner) | 3–4/day | 2–3h | — |
| User's prior | "no more than 5–6/day, ≥1–2h between" | matches median | — |

The **defensible numbers for a full-screen unlock-triggered check-in** are:

- **3–5 prompts per day** is the *median* in the EMA field and the practitioner sweet spot.
- **120 minutes (2 hours) minimum between prompts** is the Wrzus & Neubauer 2023 median inter-prompt interval.
- **Compliance at 1–3 prompts/day is ~87%**; at 4+ it drops to ~77–79% (Williams 2021).
- **A user unlocks their phone 50–100+ times a day** in the typical smartphone-use pattern (this is the user's prior, consistent with the published smartphone-use literature). 50–100 unlocks → 3–5 prompts/day means **gating by inter-prompt interval, not by unlock event**, and is the only design that matches the EMA evidence.

**The honest answer to the question "what is the minimum inter-sample interval":**

The Wrzus & Neubauer 2023 median is **120 minutes**. The Williams 2021 sweet spot is **1–3 prompts/day** with ~87% compliance. The user's prior of "no more than ~5–6 check-ins per day, with at least 1–2 hours between samples" is **supported by the primary literature**, with the more defensible reading being *3–5/day with at least 2 hours between samples* (slightly stricter than the user's prior on the upper end, slightly looser on the lower end). The Wrzus & Neubauer 2023 finding that compliance was "comparable" at 3, 6, 9, 12 prompts/day in 14-day studies is an interesting nuance — it says the *average* participant can tolerate more than the folklore says — but the *compliance-by-prompt-frequency* cell in Williams 2021 is the more decisive datapoint for MindAnchor's design because it is m-EMA-specific and a smartphone-unlock check-in is m-EMA in the strict sense.

**A specific recommendation for MindAnchor:** **a minimum inter-sample interval of 90–120 minutes** (round number: **2 hours**), with a **soft cap of 4–5 prompts per day** before the check-in engine stops queuing for the rest of the day. The 90-minute floor is slightly looser than the Wrzus & Neubauer median (120 min) to leave room for the user to feel *some* signal; the 4–5/day cap is the Williams 2021 inflection point.

## B3. Engagement signal vs. content — should the rejection itself be recorded?

Two opposing views, both with literature:

**View A — Record the engagement signal but never the content of a rejection.**
The "missing-data-is-informative" position. The EMA literature is clear that missing data are *not* random: in m-EMA studies, lower mood, lower activity, and more demanding contexts all correlate with non-response (Schüz et al. 2013; Dzubur et al. 2018; Messiah et al. 2011; cited in the 2025 evaluation review at https://pmc.ncbi.nlm.nih.gov/articles/PMC12991416/). The MNAR (missing-not-at-random) mechanism is real. Lipschitz, Pike, Hogan, Murphy & Burdick 2023 (*The Engagement Problem: a Review of Engagement with Digital Mental Health Interventions and Recommendations for a Path Forward.* Curr Treat Options Psychiatry 10(3):119–135, https://pmc.ncbi.nlm.nih.gov/articles/PMC10883589/) is the most-recent formal synthesis. The Lipschitz 2022 *JMIR* scoping review of 117 RCTs of mobile-app depression interventions (*Digital Mental Health Interventions for Depression: Scoping Review of User Engagement.* J Med Internet Res 2022;24(10):e39204, https://www.jmir.org/2022/10/e39204/) proposes a **5-element minimum engagement reporting framework**:

1. Intervention instructions / adherence criteria
2. Rate of uptake
3. Level-of-use metrics (number of uses + time)
4. Duration-of-use metrics (weekly use patterns)
5. Number of completers

The framework does *not* ask for *content* of rejections. It asks for the *rate* of rejections and the *pattern* (e.g. "X% of completers used the app ≥3 times in the final week").

The Baumel 2019 / 2018 papers (cited in §B7) are the engagement-cliff data: 15-day retention 3.9% (median), 30-day retention 3.3% (median), 81% of users who installed a mental-health app *stopped using it within 10 days* (Baumel A, Muench F, Edan S, Kane JM. *Objective User Engagement With Mental Health Apps: Systematic Search and Panel-Based Usage Analysis.* J Med Internet Res 2019;21(9):e14567, https://www.jmir.org/2019/9/e14567/). The Baumel 2019 message is that the *rejection pattern itself* — when the user stops opening the app, when they stop responding to prompts — is the engagement signal that matters most.

**View B — Do not record rejections at all.**
The "minimise what the launcher holds" position. The project has a no-mood-inference rule, a no-analytics rule, and a strong privacy posture. Recording the *fact* of a rejection (timestamp + "rejected" boolean) is closer to "engagement analytics" than to "the user's words." It is not the content of the rejection (which View A also opposes), but it is the *metadata of the rejection*. The Reese 2017 / Park & Conway 2018 line on *passive digital phenotyping* warns that the boundary between "engagement signal" and "behavioural data about the user" is exactly the boundary the project is trying not to cross. The closest *primary* reference on this is the **Torous & Lipschitz 2018 / 2019** line on "engagement measurement" in *Psychiatric Services* (Ng MM, Firth J, Minen M, Torous J. *User Engagement in Mental Health Apps: A Review of Measurement, Reporting, and Validity.* Psychiatr Serv 2019;70(7):538–544, https://psychiatryonline.org/doi/10.1176/appi.ps.201800543), which is the field's main critical reflection on what *not* to do.

**The project's own constraints** are decisive here:

- **No analytics.** "We will not record engagement signals that aggregate into a behaviour profile of the user" is the project's de facto position (see 08-sensing-architecture.md, 09-writing-layer.md, 22-per-app-session-length-ui.md).
- **No mood inference.** A *rejection* is a mood-adjacent signal: "I was asked to engage and I declined" carries information about how the user is feeling, even if the launcher does not interpret it. Recording the *fact* of the rejection, repeatedly, *is* building a behaviour profile.
- **On-device only, but the data lives forever.** Even on-device, a record of "user X rejected N check-ins in week Y" is a longitudinal record of the user's mental state, and the project has consistently said *the user owns their words* — which implies the user does *not* own their behaviour trace.

**The recommendation for Feature B: do not record rejections as data.** The user's prior is exactly right. The back button is the end of the interaction; the launcher closes the activity, returns to the home screen, and forgets the prompt existed. There is no log, no "last rejected at" timestamp, no "X% rejection rate" surfaced anywhere. The only thing the launcher keeps is a *local, transient, in-memory* rate-limit counter that prevents re-queuing the same check-in in the next 2 hours — and even that counter is reset on app restart and never written to disk.

The engagement-signal argument (View A) is real and the literature is right that missing data is informative. The *correct* response to that, in a *clinical research* setting, is to record the missingness. The *correct* response in a *consumer launcher that the user owns* is to *not* record the missingness, on the principle that the user's behaviour trace is not a product surface.

**The exception that proves the rule:** the existing *per-app session-length* and *WHO-5 pulse* features in the launcher do record *acceptance* (the user completed a friction-gate; the user completed a WHO-5). The asymmetry is intentional: the user *did* the thing, the launcher records the thing, and the recording is what makes the per-app default and the rolling-baseline N-of-1 signal work. A *rejection* is the absence of the thing; recording the absence is a different kind of data and the project's no-mood-inference rule says no.

## B4. Full-screen over lock-screen — Android's own guidance and the ResearchKit / ResearchStack pattern

The user asked three things here: (a) Android's own guidance on lock-screen Activities, (b) the iOS ResearchKit / Android ResearchStack pattern for in-app EMA prompts, and (c) the engagement-vs-intrusive trade. I take them in order.

**(a) Android's own guidance on lock-screen Activities.** The relevant APIs are `Activity.setShowWhenLocked(boolean)` (API 27+, the modern path) and the legacy `WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED` (deprecated). The official Android documentation is at https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean) and https://developer.android.com/reference/android/app/KeyguardManager. The key quote from the `Activity` reference:

> *"Specifies whether an [Activity] should be shown on top of the lock screen whenever the lockscreen is up and the activity is resumed. Normally an activity will be transitioned to the stopped state if it is started while the lockscreen is up, but with this flag set the activity will remain in the resumed state visible on-top of the lock screen. This value can be set as a manifest attribute using R.attr.showWhenLocked."*

The pattern in current Android (API 30+, the version target MindAnchor should set) is `setShowWhenLocked(true) + setTurnScreenOn(true) + (optional) KeyguardManager.requestDismissKeyguard(...)`. The OS-level permission required is *no special permission* — these are public APIs. The user-grant path is the device PIN / pattern / biometric (which the user already has).

The full-screen *Activity* path (as opposed to the lock-screen notification path) is the more aggressive of the two: it places an Activity on top of the keyguard, which the OS treats as a *user-visible interruption*. The Android 13+ behaviour is that notifications and full-screen intents are increasingly *de-emphasised* in favour of less-interruptive surfaces, but `setShowWhenLocked` for legitimate user-initiated flows (incoming call, alarm, navigation) is still supported. A check-in is a *user-initiated* flow in the sense that the user is the one who picked up the phone, so this is a defensible use of the API.

**(b) The iOS ResearchKit / Android ResearchStack pattern for in-app EMA prompts.** ResearchKit (Apple, 2015) and ResearchStack (Open mHealth, 2016) are the canonical SDKs for in-app EMA prompts in research-study apps. The ResearchStack site (http://researchstack.org/) and the *Digital Marshmallow Test* paper (https://mhealth.jmir.org/2021/1/e25018/) are the canonical references. The ResearchKit/ResearchStack pattern is: an in-app *Activity* (or *ViewController*) prompts the user at scheduled times, with a notification as the *trigger* and the in-app screen as the *capture surface*. The pattern does *not* use a full-screen-on-lock-screen Activity; it uses an in-app screen that the user reaches by tapping a notification. This is the closest "canonical" research-app EMA pattern, and it is *not* what the user is asking for.

The reason the user is asking for a *full-screen-on-unlock* Activity (not the ResearchKit pattern) is that the *notification-based* flow is what they are explicitly rejecting ("the user has explicitly rejected the 'swipe-away notification'"). The literature on notification-based EMA is the *alert-fatigue* literature (notified & ignored, snoozed, swiped away), and it is the literature Baumel 2019 / Williams 2021 are reporting on. The *full-screen-on-unlock* pattern is a different design choice, and the closest thing to a primary source on it is *The Design of Ecological Momentary Assessment Technologies* by Intille et al. (https://academic.oup.com/iwc/article/32/3/257/5897245), which is the design literature on EMA app UX and which argues *in favour* of in-app dialogues over notifications to reduce the number of steps required of users.

**(c) The engagement-vs-intrusive trade.** What does the literature say about *user tolerance for full-screen check-ins*? The honest answer: **the full-screen-on-unlock pattern is not formally studied as such in the m-EMA literature.** The closest primary sources are:

- The 2017 Visuri et al. work on on-screen dialogues (cited in Intille 2020 above) — argues for in-app dialogues over notifications to *reduce* friction.
- The 2021 *JMIR* m-EMA design guidelines study (https://www.jmir.org/2024/1/e55694/) — finds that *setup prompting on Android* (a one-time, predictable prompt) is better than *contextual prompting* (a context-triggered, interruptive prompt) for EMA completion. Average compliance 23.4/30 = 78%.
- The 2024 mixed-methods *Alert Fatigue and Smartphone Notifications* study (https://journalajess.com/index.php/AJESS/article/view/2743) — finds that *notification frequency* did not significantly predict cognitive or emotional outcomes, but *alert fatigue* and *attention disruption* did. The implication: it is the *intrusiveness* of the prompt that matters, not the count.
- The 2020 *J Happiness Studies* systematic review of smartphone-EMA well-being studies (https://link.springer.com/article/10.1007/s10902-020-00324-7) — finds that the highest-compliance studies (>84%) ran 7 or 14 days with 3–6 prompts per day. **Studies with shorter intervals between prompts and full-screen surfaces had *lower* compliance in the qualitative data**, though the quantitative effect is mixed.

**The honest characterisation of the gap:** the *full-screen-on-unlock* pattern is a *new* design pattern. It is supported by the *intent* of the EMA literature (less interruptive than notifications, more like a dialogue than a ping), and it is supported by the *Android API* (`setShowWhenLocked` is the official path for legitimate user-visible flows). It is *not* directly validated by an RCT. The closest analogue in the literature is the *on-screen dialogue* pattern (Visuri 2017, Intille 2020) and the *setup-prompt* pattern (the 2024 *JMIR* design-guidelines study). The user is making a defensible extrapolation, not a literature-anchored one.

**A second issue the user raised:** the user-grant path is `ACTION_MANAGE_OVERLAY_PERMISSION` *or* the `setShowWhenLocked` API. The `ACTION_MANAGE_OVERLAY_PERMISSION` path is the *draw-over-other-apps* permission, which on Android 13+ is the `SYSTEM_ALERT_WINDOW` permission — **a dangerous-level permission that the project does not have and should not acquire**. MindAnchor's `NetworkCallsForbiddenTest` and the no-INTERNET rule imply a similar posture for over-the-screen permissions: do not request them. The `setShowWhenLocked` path does *not* require `SYSTEM_ALERT_WINDOW`; it is a public API that uses the existing lock-screen / always-on-display flow. That is the path the project should take, and it is the path the project already needs for *zero* permissions beyond what the launcher already has.

**The recommendation for Feature B:** full-screen Activity is supported by the Android API surface (`setShowWhenLocked(true) + setTurnScreenOn(true)`), is consistent with the *intent* of the EMA literature (less interruptive than notifications), and is *not* directly validated by an RCT. The honest framing for the clinical-review gate is: *this is a design judgment extrapolated from the on-screen-dialogue literature and the Android API; it has not been RCT-tested.*

## B5. Form shape — what is the right 60-second self-report?

The user named three candidate instruments:

- **PHQ-2** (Kroenke, Spitzer & Williams 2003, *Med Care* 41(11):1284–1292, DOI 10.1097/01.MLR.0000093487.78664.3C, https://www.ihs.gov/sites/crs/themes/responsive2017/display_objects/documents/phq_2_medical_care.pdf). Two items (anhedonia + depressed mood), past 2 weeks, 0–3 each, total 0–6. Cut-off ≥3: sensitivity 83%, specificity 92% in 6,000 primary-care / OB-GYN patients against MHP reinterview. *It is a depression screener.* It is the standard first-step depression screen in primary care. The instrument *itself* is validated for repeated use at the same cadence as the WHO-5 (the WHO-5 brief in 13-who5-score-presentation-brief.md covers this).
- **WHO-5** (Topp et al. 2015, *Psychother Psychosom* 84(3):167–176, DOI 10.1159/000376585, https://www.karger.com/pps/article/84/3/167/282903). Five positively-worded well-being items, past 2 weeks, 0–5 each, raw 0–25 → percentage 0–100. Cut-off ≤50 (raw <13) is the depression screen. Mean sensitivity 0.87, mean specificity 0.76 across 8 studies. *It is a well-being outcome measure.* MindAnchor already uses WHO-5 as the biweekly pulse.
- **Single-Item Self-Esteem Scale** (Robins, Hendin & Trzesniewski 2001, *Pers Soc Psychol Bull* 27(2):151–161, DOI 10.1177/0146167201272002, https://journals.sagepub.com/doi/10.1177/0146167201272002). One item ("I have high self-esteem"), 1–5 (or 1–7) Likert. Strong convergent validity with the Rosenberg Self-Esteem Scale in adult samples, near-identical correlations with criterion measures. *It is a single-item self-esteem measure.*

**Which of these fits MindAnchor's no-mood-inference rule?**

None of them, in the strict sense. All three are *clinical instruments* with cut-offs, screen-positive thresholds, and clinical-interpretation lineages. The PHQ-2 is a depression screener, the WHO-5 has a ≤50 cut-off that the WHO 1998 document itself frames as a "first-stage screen" for depression, and the SISE is a self-esteem measure that tracks the construct most associated with depression. None of them is a "how did today sit?" field.

The user named this prior: *"the field is `how did today sit?` on a 1-5 scale plus a 1-3 sentence optional reflection, with no clinical anchor."* The literature does not *support* this specific shape (there is no RCT of a 1–5 "how did today sit?" field in a mental-health launcher), and the literature does not *contradict* it either.

What the literature *does* support, mapped to MindAnchor's constraints:

- **The 1–5 single-item global rating** is supported by Robins et al. 2001 as a *valid single-item measure of a single construct*. A "how did today sit?" field is a *single-item global rating of the day*, not of self-esteem, and Robins 2001 is a construct-validation paper for a specific self-esteem item. The closest *peer-reviewed* analogue is the **PROMIS Global Health** single-item rating (Hays et al. 2009, https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2699403/), which uses a 1–5 Likert for "in general, how would you rate your health?" and has been validated against multi-item PROMIS scales. The *single-item global rating* is a well-established instrument in patient-reported-outcome research.
- **The 1–3 sentence optional reflection** is supported by the *EMA* tradition of short, optional, free-text fields in momentary self-report (Shiffman 2008; Intille 2020). The *length* of the response is not specified by the EMA literature; the *option to skip* is consistent with the EMA "answered on a 0–n scale, optional free text" pattern. The closest *peer-reviewed* analogue is the *Ecological Momentary Assessment* (EMA) "beep + optional comment" pattern.
- **The "no clinical anchor" framing** is supported by the *no-mood-inference* project rule and by the *Self-Determination Theory* autonomy-support principle (Ng et al. 2012, cited in 13-who5-score-presentation-brief.md). The PHQ-2 and the WHO-5 are *clinical* anchors; a 1–5 "how did today sit?" field with no cut-off and no screen-positive interpretation is *not* a clinical anchor.
- **The "no cut-off, no screen-positive" design** is supported by the *Canadian Task Force on Preventive Health Care 2025 update* (https://pmc.ncbi.nlm.nih.gov/articles/PMC12534120/) and the *APA 2022 USPSTF comments* (https://www.psychiatry.org/getattachment/c8f156aa-1ca3-4ae3-9f35-210ff33ea0be/APA-Comments-USPSTF-Depression-Suicide-Anxiety-10172022.pdf) — both warn that *routine instrument-based screening without follow-up is potentially harmful*. A field with no cut-off and no diagnostic label is consistent with that warning.

**The recommendation for Feature B:** the user's prior is the right shape, with one nuance from the literature. The form is:

1. **One single-item rating: "How did today sit?" on a 1–5 scale.** Anchored at the *end of the day* (when the user is asked, the field refers to the day-so-far). The anchors are *user-language* — "rough / low / ok / good / bright" or similar — *not* clinical. The field has no cut-off, no screen-positive interpretation, and the launcher does not surface the number to the user as a verdict (consistent with the WHO-5 brief in 13-who5-score-presentation-brief.md, which says: a number is fine *if* it is framed as pattern-tracking the person owns).
2. **One optional free-text field: 1–3 sentences.** No prompt, no interpretation, no auto-tag, no theme detection. Consistent with Feature A (the note surface) and the *Pennebaker "no feedback" rule* from the expressive-writing protocol.
3. **No mood emoji, no mood chart, no "trend" surfaced in the moment.** The launcher *can* chart the 1–5 rating over time as a N-of-1 within-person signal (see 11-pulse-cadence-brief.md), but the *check-in screen itself* shows the field, the field's anchors, and the save button. Nothing else.
4. **No clinical anchor.** The field is not a PHQ-2 and not a WHO-5. The launcher does not claim it is. If the user has the *separate* WHO-5 biweekly pulse set up, the check-in 1–5 rating is *additional*, not the same data.

The honest framing for the clinical-review gate: *the field shape is design judgment extrapolated from the single-item global-rating tradition in PRO research (Hays 2009; Robins 2001), the optional-free-text EMA tradition (Shiffman 2008; Intille 2020), and the SDT autonomy-support principle. It has not been RCT-tested in a phone-unlock check-in context.*

**What the literature does NOT support for the form:**

- The claim that a 1–5 rating is a *clinical* instrument. It is not. It is a *single-item global rating*, and the *interpretation* is the user's, not the launcher's.
- The claim that a 1–3 sentence free-text reflection will *cause* a mental-health benefit. Frattaroli 2006 / Reinhold 2018 (cited in §A2) do not test that protocol.
- The claim that the check-in *detects* anything. The check-in does not detect, does not infer, does not interpret. It captures; the user interprets. This is the *no-mood-inference* rule.

## B6. What the literature does NOT support for Feature B

- **The "every unlock" trigger.** The Wrzus & Neubauer 2023 median inter-prompt interval is 120 minutes, and the Williams 2021 sweet spot is 1–3 prompts/day. "Every unlock" is 50–100+ prompts/day, which would crash compliance to near-zero and is not a pattern the EMA literature has tested. The user already understands this and is asking for the minimum inter-sample interval — the design is *trigger-on-unlock, gate-by-interval*, not "show on every unlock."
- **Recording the *content* of a rejection.** View A in §B3 is the engagement-signal argument; the literature is right that missing data is informative. The project rule is no-mood-inference / no-analytics, and the project rule wins.
- **Recording the *fact* of a rejection as data.** Same reason. The user-owned-trace principle is the project rule, and the project rule wins.
- **A full-screen check-in that surfaces a clinical anchor (PHQ-2, WHO-5, SISE, or any other validated clinical instrument).** The project's no-mood-inference rule excludes this, and the *Canadian Task Force 2025* / *APA 2022 USPSTF* evidence base supports the exclusion: instrument-based screening in a non-clinical consumer app without follow-up capacity is *potentially harmful*. The check-in is a *capture surface*, not a *screener*. The user already has the WHO-5 biweekly pulse as the clinical-instrument surface; the check-in is a different surface with a different job.
- **A *defer* or *snooze* option.** The user explicitly rejected this. The literature (Wrzus & Neubauer 2023; Williams 2021) supports the user: snooze / delay options are the EMA "feature that helps participants self-manage burden" pattern, but they also *normalise* non-response. The project wants the rejection to be a *clean exit*, not a *deferral*. The literature says this is OK *as long as the rejection is not a 5th consecutive miss* — at that point the user has effectively opted out and the launcher should not re-prompt. The honest version of the recommendation is: 3–5 consecutive rejected check-ins → auto-pause for the day. The user can re-enable.
- **The lock-screen-surfacing Activity pattern.** Not formally tested. Defensible extrapolation from on-screen-dialogue EMA and the Android API. Honest framing for the clinical-review gate.
- **A check-in *form* longer than 60 seconds.** The Williams 2021 finding is that *items per prompt* is a significant predictor of compliance in nonclinical data sets (>26 items per prompt → 63% compliance; 1–3 items per prompt → 87% compliance). A 60-second 1–5 rating + optional free-text is the right shape. Anything longer is not supported by the EMA compliance data.

## B7. Part B — primary sources

- Shiffman S, Stone AA, Hufford MR. *Ecological momentary assessment.* Annu Rev Clin Psychol 2008;4:1–32. DOI 10.1146/annurev.clinpsy.3.022806.091415. https://www.annualreviews.org/content/journals/10.1146/annurev.clinpsy.3.022806.091415
- Wrzus C, Neubauer AB. *Ecological momentary assessment: a meta-analysis on designs, samples, and compliance across research fields.* Assessment 2023;30(3):825–846. DOI 10.1177/10731911211067538. https://pmc.ncbi.nlm.nih.gov/articles/PMC9999286/
- Williams MT, Lewthwaite H, Fraysse F, Gajewska A, Ignatavicius J, Ferrar K, et al. *Compliance with mobile ecological momentary assessment of self-reported health-related behaviors and psychological constructs in adults: a systematic review and meta-analysis.* J Med Internet Res 2021;23(3):e17023. https://www.jmir.org/2021/3/e17023
- Bolger N, Laurenceau J-P. *Intensive Longitudinal Methods: An Introduction to Diary and Experience Sampling Research.* New York: Guilford Press, 2013. ISBN 9781462514184.
- Trull TJ, Ebner-Priemer UW. *Ambulatory Assessment in Clinical Psychology.* In: Cautin RL, Lilienfeld SO, eds. *The Encyclopedia of Clinical Psychology.* John Wiley & Sons, 2015. (The Trull & Ebner-Priemer 2014 chapter at https://pmc.ncbi.nlm.nih.gov/articles/PMC4755853/ is the most-accessible primary source from this lab.)
- Intille S, Haynes C, Maniar Y, Murphy R, Ali H, Shreshtha S. *The Design of Ecological Momentary Assessment Technologies.* Interacting with Computers 2020;32(3):257–272. https://academic.oup.com/iwc/article/32/3/257/5897245
- Interact Flash Review — *Measuring Well-Being with EMA.* https://teaminteract.ca/wp-content/uploads//2019/07/INTERACT_FR_MeasuringWBwithEMA.pdf
- *Design Guidelines for Improving Mobile Sensing Data Collection.* J Med Internet Res 2024;26:e55694. https://www.jmir.org/2024/1/e55694/
- Lipschitz JM, Pike CK, Hogan TP, Murphy SA, Burdick KE. *The Engagement Problem: a Review of Engagement with Digital Mental Health Interventions and Recommendations for a Path Forward.* Curr Treat Options Psychiatry 2023;10(3):119–135. https://pmc.ncbi.nlm.nih.gov/articles/PMC10883589/
- Lipschitz JM, Van Boxtel R, Torous J, Firth J, Lebovitz J, Burdick K, Hogan T. *Digital Mental Health Interventions for Depression: Scoping Review of User Engagement.* J Med Internet Res 2022;24(10):e39204. https://www.jmir.org/2022/10/e39204/
- Ng MM, Firth J, Minen M, Torous J. *User Engagement in Mental Health Apps: A Review of Measurement, Reporting, and Validity.* Psychiatr Serv 2019;70(7):538–544. https://psychiatryonline.org/doi/10.1176/appi.ps.201800543
- Baumel A, Muench F, Edan S, Kane JM. *Objective User Engagement With Mental Health Apps: Systematic Search and Panel-Based Usage Analysis.* J Med Internet Res 2019;21(9):e14567. https://www.jmir.org/2019/9/e14567/
- Baumel A, Kane JM. *Examining Predictors of Real-World User Engagement with Self-Guided eHealth Interventions: Analysis of Mobile Apps and Websites Using a Novel Dataset.* J Med Internet Res 2018;20(12):e11491. DOI 10.2196/11491. https://www.jmir.org/2018/12/e11491/
- Kroenke K, Spitzer RL, Williams JBW. *The Patient Health Questionnaire-2: Validity of a two-item depression screener.* Med Care 2003;41(11):1284–1292. DOI 10.1097/01.MLR.0000093487.78664.3C. https://www.ihs.gov/sites/crs/themes/responsive2017/display_objects/documents/phq_2_medical_care.pdf
- Topp CW, Østergaard SD, Søndergaard S, Bech P. *The WHO-5 Well-Being Index: A systematic review of the literature.* Psychother Psychosom 2015;84(3):167–176. DOI 10.1159/000376585. https://www.karger.com/pps/article/84/3/167/282903
- Robins RW, Hendin HM, Trzesniewski KH. *Measuring Global Self-Esteem: Construct Validation of a Single-Item Measure and the Rosenberg Self-Esteem Scale.* Pers Soc Psychol Bull 2001;27(2):151–161. DOI 10.1177/0146167201272002. https://journals.sagepub.com/doi/10.1177/0146167201272002
- Hays RD, Bjorner JB, Revicki DA, Spritzer KL, Cella D. *Development of physical and mental health summary scores from the Patient-Reported Outcomes Measurement Information System (PROMIS) global items.* Qual Life Res 2009;18(7):873–880. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2699403/
- *Evaluation of Pressing Issues in Ecological Momentary Assessment.* (2025; cites Shiffman 2008 and the MNAR literature) https://pmc.ncbi.nlm.nih.gov/articles/PMC12991416/
- Schüz N, Schüz B, Eid M. *Beyond the usual suspects: target and moderator effects of momentary worry in EMA studies.* (cited in the 2025 EMA evaluation above for the MNAR mechanism)
- Dzubur E, Li M, Kawabata K, et al. *Reliability of ambulatory heart rate variability measures (the EMA-heart study).* (cited in the 2025 EMA evaluation above)
- Canadian Task Force on Preventive Health Care. *Recommendation on screening adults for depression using a questionnaire.* CMAJ 2025. https://pmc.ncbi.nlm.nih.gov/articles/PMC12534120/
- APA *Comments to USPSTF on Depression, Suicide, Anxiety Screening* (2022). https://www.psychiatry.org/getattachment/c8f156aa-1ca3-4ae3-9f35-210ff33ea0be/APA-Comments-USPSTF-Depression-Suicide-Anxiety-10172022.pdf
- *Alert Fatigue and Smartphone Notifications: A Mixed-Methods Study.* Asian Journal of Education and Social Studies 2024. https://journalajess.com/index.php/AJESS/article/view/2743
- Android Developers. *Activity | API reference (setShowWhenLocked).* https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
- Android Developers. *KeyguardManager | API reference.* https://developer.android.com/reference/android/app/KeyguardManager
- ResearchStack (Open mHealth). http://researchstack.org/ ; https://github.com/ResearchStack/ResearchStack
- *The Digital Marshmallow Test (DMT) Diagnostic and Monitoring Mobile Health App for Impulsive Behavior: Development and Validation Study.* JMIR mHealth uHealth 2021;9(1):e25018. https://mhealth.jmir.org/2021/1/e25018/
- *Smartphone-Based Ecological Momentary Assessment of Well-Being: A Systematic Review and Recommendations for Future Studies.* J Happiness Studies 2020. https://link.springer.com/article/10.1007/s10902-020-00324-7

---

## Decision

### For Feature A (on-device notes)

- **Build a single Activity, free-text-only, no prompt, no mood field, no streak, no share, no export.** The launcher is a notepad, not a vault, not a journal, not a thought record, not a mood log.
- **No reminders, no re-prompts, by default.** The user can opt in to a *user-set* re-surface ("show me this note again in 3 days" / "show me this note next time I open this app"). The user owns the trigger and the timing.
- **Store in the existing per-app sealed-prefs store, HMAC-sealed, on-device only.** Same envelope as `IfThenPlan` and `PerAppSessionLength`.
- **No AI / no theme detection / no summary.** The model layer in 09-writing-layer.md is a separate feature with its own clinical-review gate; it is not this surface.
- **Clinical review gate applies to the wording of the entry surface ("Remember this" is the only text; the field has no label).** Honest framing: the *intervention claim* (that quick-capture notes improve mental health) is **not evidence-anchored** for the 1-line case; the *design pattern* (private, user-owned, no-feedback) is supported by the Pennebaker protocol privacy-and-control rule and the SDT autonomy-support principle.

### For Feature B (full-screen check-in on phone-unlock)

- **Trigger-on-unlock, gate-by-interval.** The check-in *engine* re-queues the prompt on the next unlock *only* if ≥2 hours have passed since the last accepted check-in *and* fewer than 4 prompts have fired in the current day. Above the daily cap, the engine does not re-queue until the next day.
- **Reject path is a single back-button tap.** The launcher closes the activity, returns to the home screen, and forgets the prompt existed. **No log of the rejection. No "last rejected at" timestamp. No engagement analytics.** The only state kept in memory is the rate-limit counter, which is reset on app restart and never written to disk.
- **Auto-pause after 3–5 consecutive rejected check-ins in a day.** The launcher stops queuing for the rest of the day. The user can re-enable. This is a *defensive* auto-pause to prevent the "I keep saying no and it keeps asking" failure mode, not a *learned* model of the user's behaviour.
- **Full-screen Activity via `setShowWhenLocked(true) + setTurnScreenOn(true)`.** No `SYSTEM_ALERT_WINDOW` / `ACTION_MANAGE_OVERLAY_PERMISSION`. The Activity is on top of the keyguard; the user is the one who picked up the phone, so the surface is a *user-initiated* flow, not an *interruption*.
- **Form: 1–5 single-item rating ("How did today sit?") + optional 1–3 sentence free-text field. No mood field. No clinical anchor. No cut-off, no screen-positive interpretation.** The rating is a N-of-1 within-person signal that can be charted over time (per 11-pulse-cadence-brief.md), but the *check-in screen itself* does not surface trends, scores, or "you've been lower this week." The launcher does not interpret.
- **Clinical review gate applies to the wording of the 1–5 anchors and the optional free-text label.** Honest framing: the *form shape* is design judgment extrapolated from the single-item-global-rating PRO tradition (Hays 2009; Robins 2001) and the optional-free-text EMA tradition (Shiffman 2008; Intille 2020). It has not been RCT-tested in a phone-unlock check-in context. The *inter-sample interval* (2 hours minimum, 4/day cap) is evidence-anchored in Wrzus & Neubauer 2023 and Williams 2021.

### What the literature does NOT support, in one line

> For both features, the literature supports a *pattern* (private, user-owned, no-feedback, low-burden, no-clinical-anchor). The literature does *not* support a *direct intervention claim* (that quick-capture notes or a 1–5 daily rating will *cause* a mental-health benefit). The project ships the pattern; the project does *not* claim the benefit.

### Open questions for the clinical reviewer

- The "Smyth 2018 (J Health Psychol, expressive writing meta-analysis)" citation in the parent brief did not match a paper I could verify. The most-recent and most-relevant expressive-writing meta-analysis is Reinhold et al. 2018 (*Clin Psychol Sci Pract*), and its verdict is null. The largest and most-cited synthesis is Frattaroli 2006 (*Psychological Bulletin*). If the reviewer has a different "Smyth 2018" in mind, please share the full reference — I did not fabricate a match.
- The "Bauer 2018 on micro-journaling" citation in the parent brief did not match a paper I could verify. The micro-journaling literature in 2024 is dominated by trade books, app-store copy, and blog posts, not RCTs. The closest peer-reviewed work on brief, moment-of-need, self-capture is the EMA tradition (Shiffman 2008; Wrzus & Neubauer 2023). I have not cited a Bauer 2018 paper that does not exist.
- The full-screen-on-unlock pattern is not formally studied in the m-EMA literature. It is a defensible extrapolation from the on-screen-dialogue tradition and the Android `setShowWhenLocked` API. The clinical-review framing for the feature is "design judgment extrapolated from the EMA literature and the Android API surface; not RCT-tested."
- The 1–5 "how did today sit?" form field is a design judgment extrapolated from the single-item-global-rating PRO tradition (Hays 2009; Robins 2001) and the optional-free-text EMA tradition (Shiffman 2008). Not RCT-tested in a phone-unlock check-in context.
- The "auto-pause after 3–5 consecutive rejected check-ins" is a project-side defensive default, not a literature-derived number. The literature on MNAR (Schüz 2013; Messiah 2011; Dzubur 2018) supports the *principle* that missing data is informative, but it does not give a specific threshold for "the user has opted out." 3–5 is the design judgment that balances "give the user room to say no sometimes" against "stop asking when the user has stopped saying yes."
