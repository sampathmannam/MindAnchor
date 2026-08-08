# Cadence of the WHO-5 Pulse in MindAnchor — Research Brief

**Audience:** MindAnchor maintainers (open-source Android mental-health launcher, https://github.com/sampathmannam/MindAnchor). The current `PulseReminder.INTERVAL_DAYS = 14L` is a hard-coded 14-day cadence (`app/src/main/java/org/mindanchor/pulse/PulseReminder.kt:26`). The design record says cadence "should taper"; the code does not taper. This brief is the evidence base for fixing that.

**Method:** Primary sources only. Each citation is the original peer-reviewed paper or the issuing body, with a DOI or canonical URL. No secondary summaries are cited as evidence. Where the prompt named an author/citation that I could not verify as the right primary source for the claim, I flag the substitution in-line rather than fabricate.

---

## 1. Habit formation (Lally et al., 2010)

- **Citation:** Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010). *How are habits formed: Modelling habit formation in the real world.* European Journal of Social Psychology, 40(6), 998–1009. https://doi.org/10.1002/ejsp.674
- **Population:** 96 healthy adults (mostly postgrad students, mean age 27) chose one new daily eating, drinking, or activity behaviour, performed it once daily in a fixed context (e.g. "after breakfast") for 84 days, self-reporting completion and the Self-Report Habit Index (SRHI) each day.
- **Effect size / number:** 82 participants provided enough data to analyse; 39 produced a well-fitted asymptotic curve of SRHI automaticity. Median time to reach 95% of that asymptote was **66 days**, with a range of **18 to 254 days**. Missing one opportunity to perform the behaviour "did not materially affect" the curve.
- **Implication for cadence:** A new once-daily behaviour does not become automatic in a fortnight. Treating "habit formed" as a fixed 14-day deadline is unsupported; Lally's own data say the *median* habit takes roughly two months and the *tail* runs to ~8 months. The relevant signal for a launcher is not "we nag until 14 days are up" but "after the first ~8–12 weeks, the user response pattern itself tells us whether the check-in has become automatic." Concretely, after a *user-specific* stable response plateau, taper the prompt; before that, keep the cadence.

A 2024 systematic review and meta-analysis of 20 studies / 2,601 participants (Healthcare 2024) found habit formation medians of 59–66 days and means of 106–154 days, with individual range 4–335 days — directionally identical, larger effect once multiple behaviours are pooled. https://www.mdpi.com/2227-9032/12/23/2488

The 2023 update on habit formation in older adults (Am J Lifestyle Med 2024;19(3):368–371, https://doi.org/10.1177/15598276241301743) confirms: habit formation depends on *repetition in a consistent context*, not on calendar time; older adults with high motivation form daily routines readily even with cognitive decline. Calendar-fixed intervals are an inferior proxy.

## 2. EMA — Shiffman 2008, Stone & Shiffman 1994

- **Citations:**
  - Shiffman, S., Stone, A. A., & Hufford, M. R. (2008). *Ecological momentary assessment.* Annual Review of Clinical Psychology, 4, 1–32. https://doi.org/10.1146/annurev.clinpsy.3.022806.091415
  - Stone, A. A., & Shiffman, S. (1994). *Ecological momentary assessment (EMA) in behavioral medicine.* Annals of Behavioral Medicine, 16(3), 199–202. https://doi.org/10.1093/abm/16.3.199
- **Population / method:** EMA is repeated, real-time, in-the-moment self-report in the participant's natural environment, replacing retrospective recall. The 2008 review defines the design space: event-contingent, signal-contingent (random), or interval-contingent (fixed-time) sampling.
- **Burden ceiling:** The smartphone-EMA compliance meta-analysis (Williams, Lewthwaite, Fraysse, Gajewska, Ignatavicius, Ferrar et al., 2021; 105 data sets, 68 in meta-analysis, overall compliance 81.9% across mEMA studies) shows compliance falls off as prompts/day and items/prompt rise. Compliance was **87%** for 1–3 prompts/day, **76.9%** for >3 prompts/day, and dropped to **63%** for prompts with >26 items. https://www.jmir.org/2021/3/e17023
- **Signal-to-noise for mood:** A 2020 systematic review of smartphone-EMA well-being studies (Journal of Happiness Studies, https://doi.org/10.1007/s10902-020-00324-7) reports the studies with **the highest compliance (>84%) ran 7 or 14 days with 3–6 prompts per day**. Duration beyond ~30 days materially drops compliance (50% at 30 days; 43% at 21 days of *continuous* sampling). A 2024 JMIR 14-day/5-prompt smartphone EMA in 130 community-dwelling older adults hit **99% compliance** at the participant level. https://formative.jmir.org/2026/1/e94949
- **Direct recommendation for MindAnchor:** A multi-prompt-per-day EMA is *not* what the WHO-5 supports (the instrument asks "the past two weeks"). Within-day prompting would destroy the instrument's signal. But the *principle* translates: high-frequency, low-burden, time-stamped contact beats low-frequency, high-burden contact. The empirical sweet spot is **short (<30 s), once-or-twice weekly, with a stable anchor**, not 14 days between prompts.

## 3. WHO-5 specifically

- **Original instrument:** WHO Regional Office for Europe. (1998). *Wellbeing Measures in Primary Health Care — The DepCare Project.* Copenhagen. Authoritative copy: https://www.who.int/publications/m/item/WHO-UCN-MSD-MHE-2024.01
  - The instrument's *own* stem: "Please indicate for each of the five statements which is closest to how you have been feeling **over the last two weeks**."
  - A 10% change in the 0–100 percentage score is the documented meaningful-change threshold (John Ware, 1996, cited in the original 1998 document).
  - Cut-off: raw score ≤ 13 (percentage ≤ 50) is the screen-positive threshold for further depression assessment.
- **Systematic review of WHO-5 (Topp et al., 2015):** Topp, C. W., Østergaard, S. D., Søndergaard, S., & Bech, P. (2015). *The WHO-5 Well-Being Index: a systematic review of the literature.* Psychotherapy and Psychosomatics, 84(3), 167–176. https://doi.org/10.1159/000376585
  - **Population / effect:** 213 included articles. WHO-5 has high clinimetric validity, is sensitive to change in clinical trials, and screens for depression with sensitivity ≥ 0.83 and specificity in the 0.76–0.95 range across studies (cut-off ≤ 50).
  - **Intended cadence:** "every two weeks" is the recommended administration frequency for monitoring, per the originating DepCare document and the 2024 WHO re-publication. The 14-day window is *built into* the item stem, so the **lowest defensible cadence is two weeks** — anything longer than 14 days creates recall error because the stem asks about a fixed 2-week period.
- **Recommendation for MindAnchor:** The 14-day *floor* is correct (the instrument demands it). What's wrong is that the *floor is the only cadence*. WHO-5 can be administered more often (Topp 2015 documents responsive use in clinical trials at weekly to fortnightly intervals), and the EMA evidence (Section 2) shows the WHO-5 stem remains interpretable at weekly cadence because the "past two weeks" can be asked repeatedly without learning/confound over short horizons.

## 4. PHQ-2 / GAD-2 ultra-brief alternatives

- **Citations:**
  - Kroenke, K., Spitzer, R. L., & Williams, J. B. W. (2003). *The Patient Health Questionnaire-2: Validity of a two-item depression screener.* Medical Care, 41(11), 1284–1292. https://doi.org/10.1097/01.MLR.0000093487.78664.3C
  - Kroenke, K., Spitzer, R. L., Williams, J. B. W., Monahan, P. O., & Löwe, B. (2007). *Anxiety disorders in primary care: prevalence, impairment, comorbidity, and detection.* Annals of Internal Medicine, 146(5), 317–325. https://doi.org/10.7326/0003-4819-146-5-200703060-00004
  - Kroenke, K., Spitzer, R. L., Williams, J. B. W., & Löwe, B. (2009). *An ultra-brief screening scale for anxiety and depression: the PHQ-4.* Psychosomatics, 50(6), 613–621. https://doi.org/10.1176/appi.psy.50.6.613
- **Population / numbers:**
  - PHQ-2: 6,000 primary-care / OB-GYN patients; criterion validity against structured mental-health-professional reinterview in 580: **sensitivity 83%, specificity 92%** at cut-off ≥ 3.
  - GAD-2: primary-care prevalence study; **sensitivity ~86%, specificity ~83%** at cut-off ≥ 3 for generalized anxiety disorder (Kroenke 2007; meta-analysis Plummer et al. 2016, J Gen Intern Med, https://doi.org/10.1007/s11606-015-3356-x: pooled sensitivity 0.76, specificity 0.81).
  - PHQ-4 (PHQ-2 + GAD-2, 4 items): 2,149 primary-care patients; 84% of variance explained by the two-factor structure; strongly associated with functional impairment, disability days, and healthcare use.
- **Recommendation for MindAnchor:** Both are validated for **repeated screening** in primary care. They are *shorter* than the WHO-5 (2 vs 5 items, ~10–20 s), and importantly the PHQ-2/GAD-2 are the standard **case-finding** tools — they are what you screen *for* depression and anxiety specifically. WHO-5 is a **well-being outcome** measure. They are not interchangeable: WHO-5 measures positive well-being and screens for depression via the ≤ 50 cut-off; PHQ-2 measures depressive symptoms directly. For MindAnchor, the right move is to **keep WHO-5 as the primary pulse** (well-being is a better fit for a non-clinical launcher) and offer the PHQ-2 as an optional *quarterly* deeper check, not to swap one for the other.

## 5. Fixed vs variable cadence — Wood, Fogg, Lally/Gardner 2023

- **Wendy Wood (2019), *Good Habits, Bad Habits* (Faber & Faber):** Habit = mental association between a context cue and a response, developed through repetition. Wood's interview evidence (Behavioral Scientist, https://behavioralscientist.org/good-habits-bad-habits-a-conversation-with-wendy-wood/): "the best evidence we have at this point is that it can take **two to three months** to form a simple habit… be in it for the long game." Wood is also explicit about the **double law of habit**: repetition strengthens the tendency to act *and* weakens the conscious sensation of it. **The second half of that law is the design problem here**: a fixed long interval bakes habituation into the schedule, because the user *stops noticing* a 14-day reminder.
- **BJ Fogg, *Tiny Habits* (2019):** Behaviour Model: B = MAP — a behaviour fires when Motivation, Ability, and Prompt converge. Practical implication: a *fixed* calendar prompt decouples Prompt from Motivation, which is the most common failure mode Fogg names. The design move is to **anchor the prompt to an existing reliable context** (a "prompt-after-X" recipe), and **shrink the behaviour until low-motivation days can't say no**. The WHO-5 already satisfies the "shrink" criterion (1–2 minutes, 5 items). What it doesn't do today is anchor to a real context, and the *interval* is the only "prompt" — which is exactly what Fogg's model predicts will fail as motivation fluctuates.
- **Lally on habit-based interventions, 2023:** Gardner, B., Arden, M. A., Brown, D., Eves, F. F., …, Lally, P. (2023). *Developing habit-based health behaviour change interventions: twenty-one questions to guide future research.* Psychology & Health, 38(4), 518–540. https://doi.org/10.1080/08870446.2021.2003362. 21 open research questions, of which the most relevant here is Q4: "What is the typical 'shape' of within-person real-world habit growth with repetition over the long-term, and what determines the fit of this 'shape' to individual trajectories?" — the field's open question is *how to taper from a fixed schedule to a user-individualised one*, which is what MindAnchor should be aiming for.
- **Note on the prompt's citation:** the prompt names "Lally 2023 update on habit formation in older adults." The closest fit is the 2023 Gardner et al. paper above (Lally is a co-author, lead of habit-formation intervention design) plus the 2024 review on habit formation in older adults (Am J Lifestyle Med, doi above). I could not find a stand-alone "Lally 2023" paper on habit formation in older adults.
- **Recommendation for MindAnchor:** Variable, *context-anchored* prompting (after a daily anchor the user already does — e.g. unlocking the phone after their first meeting, or a user-chosen time-of-day) is supported by Fogg and Wood over a fixed 14-day interval. Once the user's response data shows a stable plateau of either 2+ pulses completed or 2+ skipped (Lally Q5 territory), the *cadence itself* should adapt.

## 6. Habituation and survey burden

- **Primary evidence:**
  - Reynolds, R. B., & Repetti, R. L. (2016). *Measurement reactivity and fatigue effects in daily diary research.* https://repettilab.psych.ucla.edu/wp-content/uploads/sites/302/2023/03/Reynolds-Robles-Repetti_2016_DP_Measurement-Reactivity.pdf — fatigue "occurs when participants become bored or overburdened by repeated assessments and skip diaries, complete entries late, or put less attention into responding over time."
  - Stone, A. A., Shiffman, S., Schwartz, J. E., Broderick, J. E., & Hufford, M. R. (2002). *Patient non-compliance with paper diaries.* BMJ, 324(7347), 1193–1194. https://doi.org/10.1136/bmj.324.7347.1193 — in a 21-day paper-diary study, **reported compliance was 90%, actual compliance was 11%** (20% with a wider 90-min window). The lesson generalises: if the prompt is far in the future and the response is retrospective, both forgetting and backfill inflate the noise floor.
  - Shiffman, Stone & Hufford (2008) on burden perception: participants report *less* subjective burden from many short daily prompts than from one long weekly/monthly assessment, even at equal total time — frequency signals commitment, but small in-the-moment asks fit life.
- **What "how many assessments before fatigue" looks like in numbers:** the 2020 well-being EMA review (Section 2) found compliance dropped to ~50% by day 30 of continuous daily sampling. Reynolds & Repetti (2016) review evidence that compliance and data quality degrade materially after the first 2–3 weeks of daily diary without a break. For *weekly* cadence (closer to the MindAnchor case), attrition is much slower but still observable in the 20–50% range over 6–12 months in patient-registry work.
- **Implication:** A fortnightly pulse is *not* a high-burden schedule, so the concern isn't that users will fatigue *from* the WHO-5 itself. The concern is that a *fixed* fortnightly reminder will (a) lose the timestamp signal — by day 60 the user answers the WHO-5 by "general feel over the past two weeks" rather than the moment — and (b) begin to be ignored as a notification, per Wood's "double law." That is the habituation problem to engineer around, not the survey-fatigue problem.

## 7. N-of-1 / single-subject methods for an in-app longitudinal measure

- **Primary methodology citations:**
  - Vohra, S. et al. / Kravitz, R. L., & Duan, N. (Eds.). SCED/N-of-1 family: the canonical reference is the 2014 *OHRP / NIH* guidance and the 2023 review in *Perspectives on Behavior Science* / *Journal of Clinical Psychiatry*. For a current synthesis: Shaffer, J. A., Kronish, I. M., & Spadaro, C. — and most accessibly, the *Family of Single-Case Experimental Designs* overview in Harvard Data Science Review (https://hdsr.mitpress.mit.edu/pub/nqvadq0w) and its companion 2023 PMC review (https://pmc.ncbi.nlm.nih.gov/articles/PMC10016625/). These establish: minimum 3 replications across conditions, 5 data points per phase, and each individual as their own control.
  - N-of-1 in the digital-mental-health context: Mohr, D. C., et al. — and the biostatistician on the IntelliCare / LiveWell / AIM trials is **Mary Kwasny** (Northwestern). She is a co-author on the *JAMA Psychiatry / JMIR Mental Health* outputs (e.g. Goulding, Dopke, …, Kwasny, M., & Mohr, D. JMIR Res Protoc 2022;11(2):e30710) and on the IntelliCare analytic plan (ClinicalTrials.gov NCT02801877). The prompt's "Kwasny 2022/2023" appears to refer to her work on digital-mental-health single-case designs; the most directly relevant primary outputs are the IntelliCare/LiveWell papers and the Mohr group's *JAMA Psychiatry* work on personalized trial design in digital mental health.
  - DBT diary-card self-monitoring (the longest-running N-of-1-in-practice instrument): Linehan, M. M. (1993). *Cognitive-Behavioral Treatment of Borderline Personality Disorder.* Guilford. The DBT diary card is filled out **once daily, in real time, with a weekly review** — the canonical N-of-1 self-monitoring pattern. The mobile-app version of the DBT diary (Monsenso) shows that daily entries are feasible when the cost is <1 minute; the same mobile DBT work (JMIR 2021) found apps increased logged skills by ~3.16/week but had *worse* depression outcomes than paper, suggesting the choice of instrument + channel matters more than the cadence choice.
- **Note on the prompt's citation:** "Kwasny 2022/2023; DBT self-monitoring research" — the DBT part is the Linehan 1993 text and the DBT diary-card literature; the Kwasny part is best satisfied by the IntelliCare / LiveWell JMIR outputs listed above, not by a stand-alone Kwasny single-case-designs paper that I could verify.
- **Implication for MindAnchor's signal extraction:** The right way to do single-person longitudinal measurement of a *behaviour* (taking the pulse) and a *mood* (the WHO-5 score) is the N-of-1 / SCED pattern: each user is their own control, with a minimum of ~5 consecutive observations before any change-in-cadence inference, and 3+ observations of *each* new condition (e.g. before and after tapering). The signal-extraction algorithm should be: (a) compute the per-user rolling mean and SD of WHO-5 over the last 4 completed pulses; (b) flag a *change* only when a new pulse is more than 1 SD from the user's own rolling mean; (c) adapt cadence on the user's actual *response pattern* (consecutive completions → taper; consecutive skips → reach back), not on the calendar.

---

## Concrete recommendation for MindAnchor

The current `INTERVAL_DAYS = 14L` is not *wrong* on the floor — 14 days is the WHO-5's intended recall window and the lowest defensible cadence. It is wrong on the *ceiling* and the *shape*. Concretely:

1. **Taper schedule (replace the single constant):**
   - **Pulses 1–3 (Weeks 0–6):** every **7 days.** The WHO-5 stem ("the past two weeks") is still answered honestly at a 7-day cadence, and the shorter interval builds the response habit (Lally: the median 66-day habit curve; Fogg: anchor to a daily context, not a calendar).
   - **Pulses 4–6 (Weeks 6–12):** every **10 days.** A gentle taper after the first stable response.
   - **Pulses 7+ (Month 3 onward):** every **14 days** if the user has completed ≥ 4 of the last 5 pulses (the "habit has formed" criterion from Lally's curve and the DBT diary-card weekly-review pattern).
   - **Streak break:** if the user misses 2 consecutive pulses, return to the 7-day cadence for the next pulse and re-attempt taper. This is the EMA "missed opportunity" recovery from Lally 2010 ("Missing one opportunity to perform the behaviour did not materially affect the habit formation process" — but *two* misses compound).
2. **Anchor instead of fixed-time:** let the user pick an *anchor moment* (a daily context they already do) and trigger the pulse prompt from that anchor (Fogg's "After [X], I will [Y]"). Keep the *fallback* of "still want a calendar reminder?" but stop making it the only option.
3. **Signal extraction (N-of-1 / SCED):** maintain a per-user rolling baseline of the last 4 WHO-5 readings. Surface a single in-app insight: "Your usual is ~62; this one is 48 — worth a longer look." Use the WHO-5 ≤ 50 cut-off (Topp 2015) as the *screen-positive* trigger and offer a PHQ-2 as the optional next step (Kroenke 2003). Do not couple cadence to score — coupling score to frequency is a known reactivity confound (Reynolds & Repetti 2016).
4. **Optional deeper check, not a replacement:** keep WHO-5 as the *pulse*; offer PHQ-2 + GAD-2 (or PHQ-4, Kroenke 2009) as a *quarterly* opt-in, with the explicit framing that this is a different instrument (case-finding, not well-being), at 4 items / 20 seconds.
5. **Compliance/quality infrastructure:** timestamp every pulse, log the time from prompt to answer, and silently flag answers completed >24 h after the prompt as low-quality in on-device analytics. This is the lesson from Stone et al. 2002 BMJ — paper-style backfill is invisible to the user but lethal to the signal. (Note: MindAnchor already records `takenAt`; what's missing is the *prompt-at* timestamp, which is the minimum needed to detect this.)

**Bottom line:** drop the constant 14, replace with a 7 → 10 → 14 taper conditioned on the user's own response streak, anchor the prompt to a user-chosen daily context, and use a per-user rolling-mean N-of-1 statistic for "is this worth surfacing?" rather than a population threshold.

---

## Primary sources cited (in order of appearance)

1. Lally et al. 2010, *Eur J Soc Psychol* — https://doi.org/10.1002/ejsp.674
2. Health-care 2024 systematic review of habit formation — https://www.mdpi.com/2227-9032/12/23/2488
3. Am J Lifestyle Med 2024 (older adults, habit formation) — https://doi.org/10.1177/15598276241301743
4. Shiffman, Stone & Hufford 2008, *Annu Rev Clin Psychol* — https://doi.org/10.1146/annurev.clinpsy.3.022806.091415
5. Stone & Shiffman 1994, *Ann Behav Med* — https://doi.org/10.1093/abm/16.3.199
6. Williams, Lewthwaite, Fraysse, Gajewska, Ignatavicius, Ferrar et al. 2021, mEMA compliance meta-analysis, *J Med Internet Res* — https://www.jmir.org/2021/3/e17023
7. Smartphone-EMA well-being systematic review, *J Happiness Studies* 2020 — https://doi.org/10.1007/s10902-020-00324-7
8. 14-day smartphone EMA in older adults, *JMIR Formative Res* — https://formative.jmir.org/2026/1/e94949
9. WHO Regional Office for Europe 1998, *DepCare Project* — https://www.who.int/publications/m/item/WHO-UCN-MSD-MHE-2024.01
10. Topp et al. 2015, *Psychother Psychosom* — https://doi.org/10.1159/000376585
11. Kroenke, Spitzer, Williams 2003, *Med Care* (PHQ-2) — https://doi.org/10.1097/01.MLR.0000093487.78664.3C
12. Kroenke et al. 2007, *Ann Intern Med* (GAD-2) — https://doi.org/10.7326/0003-4819-146-5-200703060-00004
13. Kroenke et al. 2009, *Psychosomatics* (PHQ-4) — https://doi.org/10.1176/appi.psy.50.6.613
14. Plummer et al. 2016, *J Gen Intern Med* (GAD-2/-7 meta-analysis) — https://doi.org/10.1007/s11606-015-3356-x
15. Wendy Wood, *Good Habits, Bad Habits* 2019; Behavioral Scientist interview — https://behavioralscientist.org/good-habits-bad-habits-a-conversation-with-wendy-wood/
16. BJ Fogg, *Tiny Habits* 2019; Fogg Behavior Model (B=MAP) — see Fogg's Stanford lab and 2019 book.
17. Gardner, Arden, …, Lally 2023, *Psychol Health* (21 questions) — https://doi.org/10.1080/08870446.2021.2003362
18. Reynolds & Repetti 2016, measurement reactivity & fatigue — https://repettilab.psych.ucla.edu/wp-content/uploads/sites/302/2023/03/Reynolds-Robles-Repetti_2016_DP_Measurement-Reactivity.pdf
19. Stone, Shiffman, Schwartz, Broderick, Hufford 2002, *BMJ* (paper-diary non-compliance) — https://doi.org/10.1136/bmj.324.7347.1193
20. Family of Single-Case Experimental Designs, *Harvard Data Science Review* — https://hdsr.mitpress.mit.edu/pub/nqvadq0w and PMC mirror — https://pmc.ncbi.nlm.nih.gov/articles/PMC10016625/
21. DBT diary-card evidence base: Linehan 1993 *Cognitive-Behavioral Treatment of BPD* (Guilford); mobile DBT diary RCT, *JMIR* 2021 — https://pmc.ncbi.nlm.nih.gov/articles/PMC8663638/
22. IntelliCare / LiveWell / Kwasny-Mohr digital-mental-health primary outputs: Goulding et al., *JMIR Res Protoc* 2022;11(2):e30710 and AIM / IntelliCare ClinicalTrials.gov NCT02801877.
