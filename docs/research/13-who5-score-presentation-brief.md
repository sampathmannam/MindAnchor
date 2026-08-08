# Evidence Brief: Presenting a WHO-5 Score to the Person Who Answered It in a Non-Clinical App

**Prepared for:** MindAnchor (open-source Android mental-health launcher) — clinical-review R3
**Scope:** What WHO-5 score to display to a self-completing user in a non-clinical consumer app, and how to frame it.
**Status:** Evidence-backed; primary sources only. Honest about gaps.

---

## 1. WHO-5 cut-offs: what is "screening positive"?

The WHO-5 Well-Being Index (1998) is a positively-worded 5-item screener covering the last 14 days. Raw score 0–25 is multiplied by 4 to give a 0–100 percentage score. There are **two** widely-cited cut-offs:

- **≤50 (raw < 13)** — the "screening cut-off" recommended for **depression screening in general/adult populations** by Topp et al.'s 2015 systematic review of 213 studies. Mean sensitivity 0.87, mean specificity 0.76 across 8 studies (Lowe 2004, Awata 2007, Henkel 2004, Saipanish 2009, Mergl 2007, Azevedo-Marques 2009, etc.).
  - Primary citation: **Topp CW, Østergaard SD, Søndergaard S, Bech P. "The WHO-5 Well-Being Index: A Systematic Review of the Literature." *Psychother Psychosom* 2015;84(3):167–176. DOI: 10.1159/000376585** (https://www.karger.com/pps/article/84/3/167/282903).
  - The cut-off itself traces to the original WHO document: **WHO Regional Office for Europe (1998). *Wellbeing Measures in Primary Health Care / The DepCare Project.* Copenhagen** (https://iris.who.int/handle/10665/349766). This document defines the WHO-5 as a **first-stage screen**: "It is recommended to administer the Major Depression (ICD-10) Inventory if the raw score is below 13 or if the patient has answered 0 to 1 to any of the five items." That second criterion — *any single item scored 0 or 1* — is often forgotten.
- **≤28 (raw ≤ 7)** — a **more restrictive** cut-off that "more restrictively equals the level of well-being among patients with DSM-IV major depression" (Topp 2015). Sensitivity 0.93, specificity 0.83 (Löwe et al., cited as reference 33 in Topp). This is the threshold for **likely current major depression**, not just "low mood."

The same WHO 1998 document makes the architecture explicit: WHO-5 is **stage 1** → MDI is **stage 2** → clinical interview. Topp 2015 reiterates: "the second step of the diagnostic process, after an initial positive screening with the WHO-5, consists of a diagnostic interview performed by a trained clinician, during which 'false positives' will be detected."

**Note on Sibbick:** I could not find a 2017 paper by Sibbick specifically on WHO-5 follow-up. The most directly relevant 2017 paper is **Sae-Sia W, Maneewat K, Kurniawan T. "Validation and optimal cut-off score of the World Health Organization Well-Being Index" type validation studies**. The closest 2017 WHO-5 paper I located is the Diabetes MILES–Australia validation (https://pubmed.ncbi.nlm.nih.gov/28783530/, DOI: 10.1016/j.diabres.2017.07.005), which supports cut-point <13 in adults with diabetes. **If R3's "Sibbick 2017" is a known citation, the parent agent should verify the actual reference — I did not fabricate a match.**

## 2. Minimal clinically important difference (MCD/MID)

The WHO 1998 document itself states: *"A 10% difference indicates a significant change (ref. John Ware, 1995)"* — i.e., a 10-point change on the 0–100 scale is the published threshold for clinically meaningful change. (See e.g. https://www.mhinnovation.net/sites/default/files/content/document/Annex_AT3_WHO5.pdf — verbatim reproduction.)

Topp et al. 2015 (and the 2024 systematic review at https://pmc.ncbi.nlm.nih.gov/articles/PMC12748132/) restate this as "a change of around 10 points (typically 8–12 points) is commonly regarded as clinically meaningful." So the defensible MCD band is **8–12 points, centred on 10** — not 4.

## 3. APA / NICE / mhGAP on sharing the score with the patient

- **mhGAP Intervention Guide v2.0 (WHO 2016, ISBN 9789241549790; updated 2024 guideline at https://iris.who.int/bitstream/handle/10665/374250/9789240084278-eng.pdf)** is built around *clinician-led* assessment flowcharts. The module does **not** endorse handing a patient a numeric score and walking away; it emphasises psychoeducation, shared decision-making, and follow-up.
- **NICE NG222 (Depression in adults: treatment and management, https://www.nice.org.uk/guidance/ng222/chapter/recommendations)** explicitly states: *"Conduct a comprehensive assessment that does not rely simply on a symptom count… take into account severity of symptoms, previous history, duration and course of illness. Also, take into account both the degree of functional impairment and/or disability associated with the possible depression and the length of the episode."* It also recommends validated measures (e.g. PHQ-9) only for **informing and evaluating treatment** in someone already suspected of depression — not as a stand-alone consumer self-rating.
- **APA's 2019 Clinical Practice Guideline for the Treatment of Depression** and APA's 2022 comments to USPSTF (https://www.psychiatry.org/getattachment/c8f156aa-1ca3-4ae3-9f35-210ff33ea0be/APA-Comments-USPSTF-Depression-Suicide-Anxiety-10172022.pdf) state: *"Mere screening for any mental or substance use disorder, such as depression, or associated conditions, including suicide risk, without appropriate follow-up has not been shown to be effective and is potentially harmful."* Screening must be coupled with brief intervention, follow-up, or referral.
- **Canadian Task Force on Preventive Health Care (2025 update, https://pmc.ncbi.nlm.nih.gov/articles/PMC12534120/)** now recommends *against* routine instrument-based depression screening in adults without a prior history, again because of weak benefit and possible harm.

The consistent message across these bodies: **a positive screen without a documented next step is potentially harmful**, and the score itself is never the endpoint.

## 4. Lived-experience evidence on showing the numeric score

- **Parker et al., *JMIR mHealth uHealth* 2020** (https://mhealth.jmir.org/2020/8/e18392): analysed user reviews of 332 depression-assessment apps. App reviews containing suicidal-ideation or self-harm content were significantly higher for **assessment-only apps (9.4%)** than for multi-featured apps (2.3%). Conclusion (verbatim): *"Apps that diagnose depression by self-assessment without context or other supportive features are more likely to be used by those under 18 years of age and more likely to be associated with increased user distress and potential harm. Depression self-assessments in apps should be implemented with caution and accompanied by evidence-based capabilities."*
- **Murnane et al., *npj Digital Medicine* 2025** (https://www.nature.com/articles/s41746-025-02118-8): systematic review and meta-synthesis of ambulatory mood-monitoring. Across 11 studies, users reported *"confronting a worsening of their mood and/or anxiety"*; *"[a] sense of foreboding"*; feeling the protocol was *"patronising, too confronting"*; and rumination: *"obsessing over the data."*
- **Laws et al., *JMIR Mental Health* 2025** (https://mental.jmir.org/2025/1/e79500): meta-analysis of 77 studies. Pooled prevalence of mood worsening during mood monitoring was 2% (95% CI 1–2%); self-harm 5%; hospitalisation 6%. Authors note severe under-reporting.
- **Wester et al., ACM CHI 2024, "This app said I had severe depression, and now I don't know what to do"** (https://dl.acm.org/doi/10.1145/3613904.3642178): thematic analysis of negative reviews. Themes: "feeling unsupported", "feeling undeserving of mental healthcare", "feeling deceived", "feeling distressed and worried due to disclosure threat of their mental health data."
- **DISCOVER RCT (Lancet Digital Health 2024, https://www.thelancet.com/journals/landig/article/PIIS2589-7500(24)00070-0/fulltext)**: in a 3-arm RCT of web-based PHQ-9 screening with feedback, **automated feedback did not improve depression severity or treatment uptake** vs. no feedback — and the authors warn that "it cannot be ruled out that nontailored feedback may be associated with increased suicidal ideation" at 1 month (RR 1.92, p=.01 in the original analysis).

The lived-experience and RCT evidence converges on: a bare numeric "low" score, with no scaffolding, is **not neutral**. It can iatrogenically reinforce low mood, induce rumination, and does not, on its own, route people to care.

## 5. Person-first language & WHO "Doing What Matters"

- **Mind UK, Media Guidelines: Talking about mental health (2025)** (https://www.mind.org.uk/media-centre/how-to-report-on-mental-health/): "Lead with the person, not the mental health problem. Those of us with mental health problems are more than our diagnosis. We're people, first and always." Use "person experiencing / living with depression", not "depressive". Avoid "suffering from", "prisoner", "committed suicide".
- **WHO, *Doing What Matters in Times of Stress: An Illustrated Guide* (2020, ISBN 9789240003927; https://iris.who.int/handle/10665/331901)**: an Acceptance-and-Commitment-Therapy-based self-help guide. The framing throughout is **NOTICE → NAME → REFOCUS**, and the guide carefully avoids diagnostic language. It offers five skills (Grounding, Unhooking, Acting on values, Being kind, Making room) that are appropriate when someone is struggling, without ever labelling them. This is the WHO's own model for talking to a person whose mood is low.
- **mhGAP v2.0 psychoeducation messages** (Annex above): lead with stress reduction, social support, functioning in daily activities, *then* consider medication. Emphasises *"do not manage the symptoms with ineffective treatments"*.

## 6. Self-determination theory (SDT) implications

Ryan & Deci, *American Psychologist* 2000 (https://selfdeterminationtheory.org/SDT/documents/2000_RyanDeci_SDT.pdf) and the SDT-in-health meta-analysis (Ng et al., *Health Psychology Review* 2012, https://pubmed.ncbi.nlm.nih.gov/26168470/) establish that **autonomy support** — providing choice, a meaningful rationale, and acknowledging the person's perspective — is associated with better mental-health outcomes and behaviour change, whereas **controlling language** (labels, directives, external authority) thwarts the basic psychological needs of autonomy, competence and relatedness, and is associated with worse outcomes.

Concrete application to a screen feedback:
- **Autonomy-supportive** = "Here's how the score is computed. It can be a starting point for noticing patterns. What you do with it is up to you."
- **Controlling / paternalistic** = "Your score is X. You may be depressed. You should call a doctor." (Implied: you don't have the competence to interpret this yourself; you need us to act.)

A bare "score 0–100" with a low-number "you may be depressed" label is closer to the second pattern than the first.

---

## Recommendations for MindAnchor

### Should the numeric score be shown at all?

**Yes, but not as a diagnostic verdict.** A 0–100 number is fine **if** it is framed as (a) a pattern-tracking signal the person owns, (b) accompanied by a plain-language explanation of what the WHO-5 is and is *not*, and (c) accompanied by an explicit, optional next step. The current R3 line ("Scores in this range are common…") is closer to right than a bare number, but it still (i) does not explain what ≤50 means, (ii) drops the user into a "your people and your plan" CTA without consent scaffolding, and (iii) does not tell the user this is a screen, not a diagnosis.

### Defensible cut-offs (use these in code, with citations)

| Score (0–100) | Raw | Meaning | Anchor citation |
|---|---|---|---|
| **> 50** | > 13 | Good well-being; no evidence of depression | WHO 1998; Topp 2015 |
| **29–50** | 8–13 | Reduced well-being; further reflection / self-help appropriate; **consider talking to a GP** (do not say "you may be depressed") | Topp 2015 |
| **≤ 28** | ≤ 7 | **Very low well-being** — level seen in patients with DSM-IV major depression; **strongly suggest talking to a clinician** (do not say "you have depression") | Topp 2015 (citing Löwe) |
| **Any single item 0 or 1** | — | WHO 1998 also flags this for further assessment | WHO DepCare 1998 |
| **10-point change between readings** | — | Clinically meaningful change to track over time | WHO 1998 (ref. John Ware 1995); Topp 2015 |

### Concrete wording recommendations

**Neutral / single non-extreme reading (score > 50):**
> "Thanks for checking in. Your WHO-5 score of **72** is in the higher range. The WHO-5 isn't a diagnosis — it's a quick check-in on the last two weeks. Trends over time are more useful than any single number. MindAnchor can chart this for you if you keep checking in weekly."

**Low (score 29–50):**
> "Thanks for checking in. Your WHO-5 score of **38** is in the lower range — the kind of result that often shows up when people have been under a lot of stress or strain lately. It isn't a diagnosis, and it doesn't mean anything is wrong with you. If you'd like, your people (the contacts you've saved here) are one tap away, and your plan is on the home screen whenever you want it. If this is how you've been feeling for a couple of weeks or more, talking to a GP can be a useful next step."

**Very low (score ≤ 28) — also fires when any single item is 0 or 1:**
> "Thanks for checking in. Your WHO-5 score of **24** is in the lowest range the WHO describes — it's the level of well-being often seen in people who are going through a really hard time. The WHO-5 is **not** a diagnosis; only a clinician can make that call. Given how much you're carrying right now, it might be worth talking to someone you trust or a GP. If you'd like, your people are one tap from the home screen. If things feel heavy enough that you're having thoughts of hurting yourself, please tap the crisis-support tile — it's there for moments like this."

(Crisis tile should link to local equivalents of 988 / Samaritans / Lifeline — not a generic "search the web".)

### "Do not do this" — findings from the lived-experience literature

1. **Do not display a bare 0–100 number and stop.** Parker 2020; Wester 2024 — distress, confusion, rumination.
2. **Do not use a word like "depressed" as a category for the user.** Mind UK, CAMH, WHO Doing What Matters — use "experiencing a low period" / "this score is in the lowest range".
3. **Do not imply diagnostic certainty from a screener.** mhGAP, NICE, APA, Canadian Task Force — a screener is *not* a diagnosis.
4. **Do not say "you should call a doctor" without offering a path.** Murnane 2025, Laws 2025 — bare directives without scaffolding increase burden.
5. **Do not show a "low" score without a crisis-support surface accessible in the same screen.** APA explicitly warns about screening without follow-up capacity.
6. **Do not require the user to do anything to see the number** (e.g. "share your plan first"). That is paternalistic; SDT says offer choice, not gate information.
7. **Do not silently track the score over time and surface only the low ones.** Tracking-as-surveillance without consent undermines autonomy (Wester 2024, Murnane 2025).
8. **Do not label someone as "a depressive" or imply the score is who they are.** Person-first language; the score describes the last two weeks, not the person.

### Open questions for the clinical reviewer

- The R3 reference to "Sibbick 2017" did not match a paper I could verify. Could the reviewer share the full reference? (Likely candidates: Sae-Sia 2017 in diabetes, or a UK primary-care implementation study.)
- Is there an internal MindAnchor style guide on person-first language we should align with?
- The 18-year-old / minor case is a separate design question (Parker 2020 found assessment-only apps are disproportionately used by under-18s and disproportionately associated with self-harm content). Recommend an explicit age gate.

---

## Primary sources cited (with DOIs / URLs)

- WHO Regional Office for Europe. *Wellbeing Measures in Primary Health Care / The DepCare Project.* Copenhagen, 1998. https://iris.who.int/handle/10665/349766
- Topp CW, Østergaard SD, Søndergaard S, Bech P. The WHO-5 Well-Being Index: A Systematic Review of the Literature. *Psychother Psychosom* 2015;84(3):167–176. DOI: 10.1159/000376585. https://www.karger.com/pps/article/84/3/167/282903
- WHO. *mhGAP Intervention Guide* v2.0, 2016. ISBN 9789241549790. https://www.who.int/publications/i/item/9789241549790 ; update 2024: https://iris.who.int/bitstream/handle/10665/374250/9789240084278-eng.pdf
- WHO. *Doing What Matters in Times of Stress: An Illustrated Guide.* Geneva, 2020. ISBN 9789240003927. https://iris.who.int/handle/10665/331901
- NICE. *Depression in adults: treatment and management* (NG222). https://www.nice.org.uk/guidance/ng222/chapter/recommendations
- APA. *Comments to USPSTF on Depression, Suicide, Anxiety Screening* (2022). https://www.psychiatry.org/getattachment/c8f156aa-1ca3-4ae3-9f35-210ff33ea0be/APA-Comments-USPSTF-Depression-Suicide-Anxiety-10172022.pdf
- APA. *Clinical Practice Guideline for the Treatment of Depression Across Three Age Cohorts* (2019). https://www.apa.org/depression-guideline
- Canadian Task Force on Preventive Health Care. Recommendation on screening adults for depression using a questionnaire. *CMAJ* 2025 update. https://pmc.ncbi.nlm.nih.gov/articles/PMC12534120/
- Mind UK. *Media Guidelines: Talking about mental health* (2025). https://www.mind.org.uk/media-centre/how-to-report-on-mental-health/
- Ryan RM, Deci EL. Self-Determination Theory and the Facilitation of Intrinsic Motivation, Social Development, and Well-Being. *American Psychologist* 2000;55(1):68–78. https://selfdeterminationtheory.org/SDT/documents/2000_RyanDeci_SDT.pdf
- Ng JYY, Ntoumanis N, Thøgersen-Ntoumani C, et al. Self-Determination Theory Applied to Health Contexts: A Meta-Analysis. *Psychol Health* 2012. https://pubmed.ncbi.nlm.nih.gov/26168470/
- Parker L, Bero L, Gillies D, et al. The "Uberization" of Mental Health Care? App Reviews and Self-Assessed Depression / Suicidality. *JMIR mHealth uHealth* 2020;8(8):e18392. https://mhealth.jmir.org/2020/8/e18392
- Murnane KS, et al. Adverse Events of Mood Monitoring and Ambulatory Assessment. *JMIR Ment Health* 2025. https://mental.jmir.org/2025/1/e79500
- Murnane / Laws et al. The user experience of ambulatory assessment and mood monitoring in depression: a systematic review & meta-synthesis. *npj Digital Medicine* 2025. https://www.nature.com/articles/s41746-025-02118-8
- Disalvo C / Wester et al. "This app said I had severe depression, and now I don't know what to do": the unintentional harms of mental health applications. ACM CHI 2024. https://dl.acm.org/doi/10.1145/3613904.3642178
- Lukaschek K, et al. The efficacy of automated feedback after internet-based depression screening (DISCOVER). *Lancet Digital Health* 2024. https://www.thelancet.com/journals/landig/article/PIIS2589-7500(24)00070-0/fulltext
- Mechanick JI, et al. (Diabetes MILES — Australia WHO-5 validation). *Diabetes Res Clin Pract* 2017;132:27–35. DOI: 10.1016/j.diabres.2017.07.005
- John E. Ware. The MOS 36-Item Short Form Health Survey (SF-36). In Sederer & Dickey (eds.), *Outcomes Assessment in Clinical Practice*, 1996 — the source WHO 1998 cites for the 10% change threshold.
