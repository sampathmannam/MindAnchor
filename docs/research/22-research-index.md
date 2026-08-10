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

### Mind, attention, and emotion

- **Killingsworth, M. A., & Gilbert, D. T. (2010) — *Science* 330(6006):932 — DOI 10.1126/science.1192439.** "A wandering mind is an unhappy mind." 46.9% of samples involved mind-wandering; people were less happy when their minds were wandering than when they were not, in every activity category. Mind-wandering explained 10.8% of within-person variance and 17.7% of between-person variance in happiness. Basis for the open-loop capture (park a wandering thought, get it back the next morning).

### Habits and behaviour change

- **Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010) — *Eur. J. Soc. Psychol.* 40(6):998-1009 — DOI 10.1002/ejsp.674.** "How are habits formed: Modelling habit formation in the real world." 96 volunteers; time to 95% of automaticity asymptote: median 66 days, range 18-254 days; missing one day did not materially affect the process. Basis for the friction-gate framing (the pause is the first rep; the rest is automaticity).

### Self-compassion

- **Neff, K. D. (2003) — *Self and Identity* 2(2):223-250 — DOI 10.1080/15298860309027.** "The Development and Validation of a Scale to Measure Self-Compassion." Three components: self-kindness, common humanity, mindful awareness. Basis for the self-compassion micro-moments editor: a 1-3 sentence prompt inviting the user to try a kind framing of whatever they just check-ed in with.

### Expressive writing

- **Pennebaker, J. W. (1997) — *Opening up: The healing power of expressing emotions*. New York: Guilford Press. ISBN 1-57230-238-0.** Trade book summarising the expressive-writing programme. The empirical paper is Pennebaker, J. W. (1997) "Writing about emotional experiences as a therapeutic process," *Psychol. Sci.* 8(3):162-166. Basis for the future "expressive writing prompt at the end of a check-in" feature (WP-8 in the 10/10 roadmap).

---

## UNANCHORED — verified-not-yet

The following claims appeared in earlier planning notes but could not be verified against a primary source. They are excluded from code-level citations until verified. If you can supply a verifiable primary source for any of these, please open a doc issue and add it to the index.

- "Wilson 2014, 17% of mind wandering is unpleasant" — the closest verified paper is Killingsworth & Gilbert 2010 above, which reports 26.5% unpleasant, 42.5% pleasant, 31% neutral. The 17% figure does not match this paper and may be a misattribution. **DO NOT CITE.**
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
| Bedtime list (specific items, 5-min prompt) | `sleep/BedtimeList.kt` (or wherever it lives) | Scullin et al. 2018 |
| Open-loop capture | `friction/OpenLoop.kt` (or wherever) | Killingsworth & Gilbert 2010 |
| Sunset mode framing | `data/SunsetPrefs.kt` (or wherever) | Roenneberg et al. 2007; Åkerstedt 2003 |
| Friction gate (pause → rep → automaticity) | `friction/FrictionGate.kt` (or wherever) | Lally et al. 2010 |
| Self-compassion micro-moments | `digest/` (or wherever) | Neff 2003 |
| (Future) Expressive writing prompt | TBD | Pennebaker 1997 |
| (Future) Per-person anomaly cut-offs reference | TBD | Jacobson 2019 |

File-to-citation map will be filled in as the KDoc annotations are added in WP-1.

---

## Update protocol

When adding a new feature:

1. Find a verifiable primary source. Use PubMed, the publisher's site, or DOI lookup. Do not trust a "fact" from a blog or a popular-science summary.
2. If the source is verified, add it to **Verified citations** above with author, year, journal/publisher, vol(issue), pages, DOI/ISBN, and a one-line "what this paper says that justifies the design".
3. If the source cannot be verified, add it to **UNANCHORED** with a note. Do not cite it in code.
4. If the feature has no verifiable paper, the design choice is unanchored. Document it in `22-10-of-10-roadmap.md` as a "future research target", not in this index.
5. Every code change that adds a citation must update this file in the same commit. The doc and the code are one deliverable.
