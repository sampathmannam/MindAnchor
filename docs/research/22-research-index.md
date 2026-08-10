# MindAnchor Research Index

> **Status:** This is a *living* index. Every claim about a feature is paired with a citation that has been verified against PubMed, the publisher's site, or a primary source. Claims without a verified citation are explicitly marked **UNANCHORED** and excluded from the index until they can be verified.

> **Rule:** No code is annotated with a paper that is not in this index. If you find a paper the launcher cites that is not in this index, file a doc issue and treat the citation as unverified until corrected.

---

## Verified citations

The following papers and book chapters have been verified by direct lookup against PubMed, the publisher's site, or a primary academic source. The format is `Author(s) (Year) — Journal/Publisher vol(issue):pages — DOI/ISBN — What this paper says that justifies the design`.

### Robust statistics — anomaly detection

- **Iglewicz, B., & Hoaglin, D. C. (1993) — *How to Detect and Handle Outliers*, ASQC Basic References in Quality Control: Statistical Techniques, Volume 16 — ISBN 0-87389-247-X.** The launcher's wellness math uses median + median absolute deviation (MAD) + the 0.6745 normaliser. The book is the canonical reference for the modified z-score. The book's recommended outlier threshold is 3.5, which is *not* what the launcher's direction bands use; see `WellnessSignals.kt` KDoc for the distinction.
- **Jacobson, N. C., Weingarden, H., & Wilhelm, S. (2019) — *J. Nerv. Ment. Dis.* 207(11):893-6 — DOI 10.1097/NMD.0000000000001042.** mindLAMP team's digital-phenotyping work, the closest published reference for the launcher's per-person anomaly cut-offs in the 2.0-2.5 robust-z range.

### Sleep and circadian

- **Scullin, M. K., Krueger, M. L., Ballard, H. K., Pruett, N., & Bliwise, D. L. (2018) — *J. Exp. Psychol. Gen.* 147(1):139-146 — DOI 10.1037/xge0000374.** "The effects of bedtime writing on difficulty falling asleep." 57 healthy young adults; the to-do-list condition fell asleep significantly faster than the completed-list condition; the more specific the to-do list, the faster the sleep onset. This is the empirical basis for the launcher's bedtime list capture (specific items, 5-minute prompt at quiet hours).
- **Roenneberg, T., Kuehnle, T., Juda, M., Kantermann, T., Allebrandt, K., Gordijn, M., & Merrow, M. (2007) — *Sleep Med. Rev.* 11(6):429-438 — DOI 10.1016/j.smrv.2007.07.005.** "Epidemiology of the human circadian clock." Introduces the Munich Chronotype Questionnaire (MCTQ) and shows chronotype is both age- and sex-dependent. Basis for the launcher's framing of "somebody else's bedtime" in the sunset explainer.
- **Åkerstedt, T. (2003) — *Occup. Med.* 53(2):89-94 — DOI 10.1093/occmed/kqg046.** "Shift work and disturbed sleep/wakefulness." Difficulty initiating sleep, shortened sleep, and somnolence during work hours are the principal acute symptoms of shift work. Cited from the shift-worker persona baseline and from the launcher's framing of quiet hours for non-day-shift users.
- **Windred, D. P., Burns, A. C., Lane, J. M., Saxena, R., Rutter, M. K., Cain, S. W., & Phillips, A. J. K. (2024) — *SLEEP* 47(1):zsad285 — DOI 10.1093/sleep/zsad285.** "Sleep regularity is a stronger predictor of mortality risk than sleep duration: a prospective cohort study." N=60,977 UK Biobank participants, >10 million hours of wrist accelerometry. Higher sleep regularity (Sleep Regularity Index) was associated with 20-48% lower all-cause mortality and remained a stronger predictor than sleep duration after adjustment. Basis for the launcher's "regularity beats duration" framing in the open-loop and bedtime surfaces — the wind-down is about the same window every night, not a fixed bedtime.

### Mind, attention, and emotion

- **Killingsworth, M. A., & Gilbert, D. T. (2010) — *Science* 330(6006):932 — DOI 10.1126/science.1192439.** "A wandering mind is an unhappy mind." 46.9% of samples involved mind-wandering; people were less happy when their minds were wandering than when they were not, in every activity category. Mind-wandering explained 10.8% of within-person variance and 17.7% of between-person variance in happiness. Basis for the open-loop capture (park a wandering thought, get it back the next morning).
- **Masicampo, E. J., & Baumeister, R. F. (2011) — *J. Pers. Soc. Psychol.* 101(4):667-683 — DOI 10.1037/a0024192.** "Consider it done! Plan making can eliminate the cognitive effects of unfulfilled goals." Five experiments: an unfulfilled goal intrudes on subsequent cognition, but forming a *specific plan* for the goal removes the intrusion about as effectively as fulfilling it does. The Zeigarnik "open loop" does not need to be closed — it needs to be anchored. Basis for the launcher's open-loop "write one line about what is still open, hand it back in the morning" prompt; for the bedtime-list specificity heuristic (Scullin 2018's "specific" finding rests on the same planning-anchors-closure mechanism); and for the if-then plan affordance in the friction gate.

### Habits and behaviour change

- **Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010) — *Eur. J. Soc. Psychol.* 40(6):998-1009 — DOI 10.1002/ejsp.674.** "How are habits formed: Modelling habit formation in the real world." 96 volunteers; time to 95% of automaticity asymptote: median 66 days, range 18-254 days; missing one day did not materially affect the process. Basis for the friction-gate framing (the pause is the first rep; the rest is automaticity).
- **Gollwitzer, P. M. (1999) — *American Psychologist* 54(7):493-503 — DOI 10.1037/0003-066X.54.7.493.** "Implementation intentions: Strong effects of simple plans." The if-then format ("when situation X arises, I will do Y") delegates goal-directed control to anticipated situational cues. The meta-analysis (Gollwitzer & Sheeran 2006, *Adv. Exp. Soc. Psychol.* 38:69-119) reports d=0.65 across 94 studies and >8,000 participants. Basis for the launcher's pre-filled if-then plan affordance in the friction gate (the user's own cue and response, surfaced at the moment of friction).

### Self-compassion

- **Neff, K. D. (2003) — *Self and Identity* 2(2):223-250 — DOI 10.1080/15298860309027.** "The Development and Validation of a Scale to Measure Self-Compassion." Three components: self-kindness, common humanity, mindful awareness. Basis for the self-compassion micro-moments editor: a 1-3 sentence prompt inviting the user to try a kind framing of whatever they just check-ed in with.
- **Linardon, J. (2020) — *Behavior Therapy* 51(4):646-658 — DOI 10.1016/j.beth.2019.10.002.** "Can acceptance, mindfulness, and self-compassion be learned by smartphone apps? A systematic and meta-analytic review of randomized controlled trials." 27 RCTs of smartphone apps. Distress g = -0.32 (95% CI -0.48 to -0.16); self-compassion g = 0.31 (95% CI 0.07-0.56, k=9). The self-compassion finding is "small, not particularly robust in certain sensitivity analyses" (Linardon's own qualifier). Basis for the launcher's claim that app-delivered self-compassion prompts are at most a small nudge, and that the prompts should be the user's own words rather than scripted content.
- **Liu, C., Chen, H., Zhang, A., Gong, X., Wu, K., Liu, C.-Y., & Chiou, W.-K. (2023) — *Psicologia: Reflexão e Crítica* 36:32 — DOI 10.1186/s41155-023-00276-w.** "The effects of short video app-guided loving-kindness meditation on college students' mindfulness, self-compassion, positive psychological capital, and suicide ideation." 4-week app-guided LKM; significant increase in self-compassion, significant decrease in suicide ideation. Basis for the launcher's view that a short app-delivered self-compassion practice is a small but real lever on affect.

### Breath and physiology

- **Balban, M. Y., Neri, E., Kogon, M. M., Weed, L., Nouriani, B., Jo, B., Holl, G., Zeitzer, J. M., Spiegel, D., & Huberman, A. D. (2023) — *Cell Reports Medicine* 4(1):100895 — DOI 10.1016/j.xcrm.2022.100895.** "Brief structured respiration practices enhance mood and reduce physiological arousal." RCT, N=108, 28 days of 5-min daily practice. Cyclic physiological sighing (double inhale + extended exhale) beat mindfulness meditation on positive affect and on resting respiratory rate. Basis for the launcher's "physiological sigh" 2s+1s+6s breathing pause; the long exhale is the active ingredient. The single-cycle version is a *trigger*; the Balban effect is for a 5-min practice.
- **Bernardi, L., Gabutti, A., Porta, C., & Spicuzza, L. (2001) — *J. Hypertens.* 19(12):2221-2229 — DOI 10.1097/00004872-200112000-00016.** "Slow breathing reduces chemoreflex response to hypoxia and hypercapnia, and increases baroreflex sensitivity." Slow breathing at 6 breaths/min depressed hypoxic and hypercapnic chemoreflex responses (P<0.01) and increased baroreflex sensitivity. The cleanest primary reference for the slow-exhale / parasympathetic-drive claim that grounds the launcher's long-exhale phase. (Note: a 2018 *J. Physiol.* paper in older KDoc was unverifiable; the verified reference is the 2001 *J. Hypertens.* paper.)

### Friction and self-nudge

- **Grüning, D. J., Riedel, F., & Lorenz-Spreen, P. (2023) — *Proc. Natl. Acad. Sci. U.S.A.* 120(8):e2213114120 — DOI 10.1073/pnas.2213114120.** "Directing smartphone use through the self-nudge app one sec." N=280 field experiment, 6 weeks; a 10-second delay plus a "dismiss this attempt" affordance led to 36% of target-app opens being abandoned, 37% fewer target-app opens in week 6 vs week 1, and a 57% overall reduction in target-app consumption. The *option to dismiss* had the strongest effect, not the delay itself. Basis for the launcher's friction-gate framing: the gate is the option to dismiss, not the wait, and "never mind" is the most important button on the screen.
- **Gollwitzer, P. M., & Sheeran, P. (2006) — *Adv. Exp. Soc. Psychol.* 38:69-119 — DOI 10.1016/S0065-2601(06)38002-1.** "Implementation intentions and goal achievement: A meta-analysis of effects and processes." d=0.65 across 94 studies, >8,000 participants. The meta-analytic source behind the Gollwitzer 1999 reference.

### Expressive writing

- **Pennebaker, J. W. (1997) — *Opening up: The healing power of expressing emotions*. New York: Guilford Press. ISBN 1-57230-238-0.** Trade book summarising the expressive-writing programme. The empirical paper is Pennebaker, J. W. (1997) "Writing about emotional experiences as a therapeutic process," *Psychol. Sci.* 8(3):162-166. Basis for the future "expressive writing prompt at the end of a check-in" feature (WP-8 in the 10/10 roadmap).

---

## UNANCHORED — verified-not-yet

The following claims appeared in earlier planning notes but could not be verified against a primary source. They are excluded from code-level citations until verified. If you can supply a verifiable primary source for any of these, please open a doc issue and add it to the index.

- "Wilson 2014, 17% of mind wandering is unpleasant" — the closest verified paper is Killingsworth & Gilbert 2010 above, which reports 26.5% unpleasant, 42.5% pleasant, 31% neutral. The 17% figure does not match this paper and may be a misattribution. **DO NOT CITE.**
- "Bernardi 2018, J. Physiol. 596(8):1449-1464 — slow exhale parasympathetic" — searched; no paper matches. The slow-exhale / parasympathetic mechanism is well-supported, but the verified reference is **Bernardi et al. 2001, *J. Hypertens.* 19(12):2221-2229** (see Verified citations). The "Bernardi 2018" citation that previously appeared in `BreathingProtocol.kt` and `FrictionGate.kt` was removed and replaced with the 2001 reference. **DO NOT CITE.**
- "Wittmann 2006, social jetlag" — original source not yet verified.
- "Kecklund 2016, shift work review" — original source not yet verified.
- "Baglioni 2016, insomnia" — original source not yet verified.
- "Harvey 2002, GAD + insomnia" — original source not yet verified.
- "Carney 2010, HRV + worry" — original source not yet verified.
- "aan het Rot 2012, behavioral activation" — original source not yet verified.
- "Dimidjian 2006, behavioral activation" — original source not yet verified.
- "Tang 2015, mindfulness and brain networks" — original source not yet verified.
- "Mark et al. 2016, workplace interruption cost" — original source not yet verified.
- "Pielot & Rello 2017, notification volume" — original source not yet verified.
- "Windt 2016, circadian preferences and chronotype" — original source not yet verified.
- "Walker 2017, *Why We Sleep*" — book exists; specific claims would need page-level verification.
- "Task Force 1996, HRV standards" — likely real but specific citation not yet verified.
- "Shaffer & Ginsberg 2017, HRV overview" — likely real but specific citation not yet verified.
- "Berry 2017, AASM sleep stages" — likely real but specific citation not yet verified.
- "Smeekes 2020, self-compassion meta-analysis" — original source not yet verified.
- "Smyth 1998, written emotional expression" — original source not yet verified.

The plan in `22-10-of-10-roadmap.md` and the v0.20.9 release notes mention some of these as targets for future features (e.g., Walker 2017 for sleep window optimization, Baglioni 2016 for insomnia). They are listed in the plan as *to-verify*. They will be added to this index only after a verifiable primary source is found.

---

## Index by feature

| Feature | File | Citation |
|---|---|---|
| Robust z-score method (median + MAD + 0.6745) | `vitals/WellnessSignals.kt` | Iglewicz & Hoaglin 1993 |
| Direction band cut-offs (1.0, 2.0) | `vitals/WellnessSignals.kt` | Jacobson 2019 (closest published reference); design choice |
| Bedtime list (specific items, 5-min prompt) | `sleep/BedtimeList.kt` | Scullin et al. 2018 |
| Open-loop capture (one-line, handed back in morning) | `friction/OpenLoop.kt` | Killingsworth & Gilbert 2010 (mind-wandering); Masicampo & Baumeister 2011 (planning releases the Zeigarnik loop) |
| Open-loop "regularity beats duration" framing | `friction/OpenLoop.kt` | Windred et al. 2024 |
| Sunset mode framing ("somebody else's bedtime") | `data/SunsetPrefs.kt` | Roenneberg et al. 2007 (chronotype); Åkerstedt 2003 (shift work) |
| Sunset mode window implementation | `sunset/SunsetController.kt` | Roenneberg et al. 2007; Åkerstedt 2003 |
| Friction gate (pause → rep → automaticity) | `friction/FrictionGate.kt` | Lally et al. 2010 (habit formation) |
| Friction gate (one-sec-style "dismiss this attempt" affordance) | `friction/FrictionGate.kt` | Grüning et al. 2023 (one sec app, 36% abandonment) |
| Friction gate (if-then plan pre-fill) | `friction/FrictionGate.kt` | Gollwitzer 1999 (implementation intentions); Gollwitzer & Sheeran 2006 (meta-analysis) |
| Physiological-sigh breath cycle (2s + 1s + 6s) | `friction/BreathingProtocol.kt` | Balban et al. 2023 (cyclic sighing won on affect) |
| Slow-exhale parasympathetic drive (mechanism) | `friction/BreathingProtocol.kt` | Bernardi et al. 2001 (slow breathing, baroreflex) |
| Self-compassion micro-moments (Neff framing) | `friction/CompassionMoment.kt` | Neff 2003 |
| Self-compassion micro-moments (app-delivered effect size) | `friction/CompassionMoment.kt` | Linardon 2020 (g=0.31 self-compassion, k=9) |
| Self-compassion micro-moments (loving-kindness app) | `friction/CompassionMoment.kt` | Liu et al. 2023 |
| (Future) Expressive writing prompt | TBD | Pennebaker 1997 |
| (Future) Sleep window optimizer | TBD | Windred et al. 2024 (regularity); Scullin 2018 (specificity) |

---

## Update protocol

When adding a new feature:

1. Find a verifiable primary source. Use PubMed, the publisher's site, or DOI lookup. Do not trust a "fact" from a blog or a popular-science summary.
2. If the source is verified, add it to **Verified citations** above with author, year, journal/publisher, vol(issue), pages, DOI/ISBN, and a one-line "what this paper says that justifies the design".
3. If the source cannot be verified, add it to **UNANCHORED** with a note. Do not cite it in code.
4. If the feature has no verifiable paper, the design choice is unanchored. Document it in `22-10-of-10-roadmap.md` as a "future research target", not in this index.
5. Every code change that adds a citation must update this file in the same commit. The doc and the code are one deliverable.
