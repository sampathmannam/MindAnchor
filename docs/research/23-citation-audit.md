# Citation audit (v0.22.0)

> **Status:** This is the WP-1 deliverable. Every feature in the
> launcher is paired with a peer-reviewed citation. The
> 17 UNANCHORED citations that appeared in earlier planning
> notes (Wilson 2014, "Bernardi 2018", "Carney 2010", "aan het
> Rot 2012 BA", Tang 2015, Mark 2016, Pielot & Rello 2017,
> Windt 2016, Walker 2017, Task Force 1996, Shaffer & Ginsberg
> 2017, Berry 2017, Smeekes 2020, Smyth 1998, and "Wilson 2014
> 17%") have been either replaced with verified references or
> removed.

## How the audit was done

For each citation that appeared in code or in the v0.20.x planning
notes, this audit:

1. Searched PubMed, the publisher's site, or a primary academic
   source for the paper as cited (title, year, journal, DOI).
2. If the paper exists and is cited correctly: the entry moves
   to the **Verified citations** section of
   [`22-research-index.md`](22-research-index.md).
3. If the paper exists but the citation is wrong (year, journal,
   DOI): the KDoc is updated to the verified reference and the
   wrong citation is listed under **Misattributions flagged** in
   the index with a "use X instead" note.
4. If the paper does not exist (the cited title/year/journal does
   not match any real paper): the citation is **removed** and the
   entry is listed under **UNANCHORED** in the index with a note
   on what was searched.

## Verified citations (final list)

These are the citations currently used in the codebase, all
verified against PubMed, the publisher's site, or a primary source.

### Robust statistics — anomaly detection

- **Iglewicz, B., & Hoaglin, D. C. (1993)** — *How to Detect and
  Handle Outliers*, ASQC Basic References in Quality Control:
  Statistical Techniques, Volume 16 — ISBN 0-87389-247-X.
  Used in `vitals/WellnessSignals.kt`.
- **Jacobson, N. C., Weingarden, H., & Wilhelm, S. (2019)** —
  *J. Nerv. Ment. Dis.* 207(11):893-6 — DOI
  10.1097/NMD.0000000000001042.
  Used in `vitals/WellnessSignals.kt`.

### Sleep and circadian

- **Scullin, M. K., et al. (2018)** — *J. Exp. Psychol. Gen.*
  147(1):139-146 — DOI 10.1037/xge0000374.
  Used in `sleep/BedtimeList.kt`.
- **Roenneberg, T., et al. (2007)** — *Sleep Med. Rev.*
  11(6):429-438 — DOI 10.1016/j.smrv.2007.07.005.
  Used in `data/SunsetPrefs.kt`, `sunset/Chronotype.kt`.
- **Åkerstedt, T. (2003)** — *Occup. Med.* 53(2):89-94 — DOI
  10.1093/occmed/kqg046.
  Used in `data/SunsetPrefs.kt`, `sunset/Chronotype.kt`.
- **Windred, D. P., et al. (2024)** — *SLEEP* 47(1):zsad285 — DOI
  10.1093/sleep/zsad285.
  Used in `friction/OpenLoop.kt`, `sleep/SleepWindowOptimizer.kt`.
- **Wittmann, M., et al. (2006)** — *Chronobiology International*
  23(1-2):497-509, DOI 10.1080/07420520500545999.
  Used in `sunset/Chronotype.kt`.
- **Kecklund, G., & Axelsson, J. (2016)** — *British Medical
  Journal* 355:i5210 — DOI 10.1136/bmj.i5210.
  Used in `sunset/Chronotype.kt`.
- **Baglioni, C., et al. (2016)** — *Psychological Bulletin*
  142(9):969-990 — DOI 10.1037/bul0000053.
  Used in `sim/personas/InsomniacPersona.kt`.
- **Harvey, A. G. (2002)** — *Behaviour Research and Therapy*
  40(8):869-893 — DOI 10.1016/S0005-7967(01)00061-4.
  Used in `sim/personas/InsomniacPersona.kt`.

### Mind, attention, and emotion

- **Killingsworth, M. A., & Gilbert, D. T. (2010)** — *Science*
  330(6006):932 — DOI 10.1126/science.1192439.
  Used in `friction/OpenLoop.kt`.
- **Brosschot, J. F., Gerin, W., & Thayer, J. F. (2006)** —
  *J. Psychosom. Res.* 60(2):113-124 — DOI
  10.1016/j.jpsychores.2005.06.074.
  Used in `data/SunsetPrefs.kt`, `sim/personas/DepressionLowMotivationPersona.kt`.
- **Thayer, J. F., & Lane, R. D. (2000)** — *J. Affective
  Disorders* 61(3):201-216.
  Cited as the second reference for the
  perseverative-cognition → low HRV mechanism. (Replaces the
  unverified "Carney 2010" reference; see UNANCHORED below.)
- **Lally, P., et al. (2010)** — *Eur. J. Soc. Psychol.*
  40(6):998-1009 — DOI 10.1002/ejsp.674.
  Used in `friction/FrictionGate.kt`.
- **Neff, K. D. (2003)** — *Self and Identity* 2(2):223-250 — DOI
  10.1080/15298860309027.
  Used in `friction/CompassionMoment.kt`.
- **Pennebaker, J. W. (1997)** — *Opening up: The healing power
  of expressing emotions*, New York: Guilford Press — ISBN
  1-57230-238-0. With the empirical paper Pennebaker & Stone
  (1977), *J. Abnorm. Psychol.* 86(2):162-169, DOI
  10.1037/0021-843X.86.2.162.
  Used in `model/CheckIn.kt`.
- **Grüning, D. J., Riedel, F., & Lorenz-Spreen, P. (2023)** —
  *Proc. Natl. Acad. Sci. U.S.A.* 120(8):e2213114120 — DOI
  10.1073/pnas.2213114120.
  Used in `friction/FrictionGate.kt`.
- **Gollwitzer, P. M. (1999)** — *American Psychologist*
  54(7):493-503.
  Used in `friction/FrictionGate.kt`.
- **Gollwitzer, P. M., & Sheeran, P. (2006)** — *Adv. Exp. Soc.
  Psychol.* 38:69-119 — DOI 10.1016/S0065-2601(06)38002-1.
  Cited as the meta-analytic source for Gollwitzer 1999.
- **Balban, M. Y. S., et al. (2023)** — *Cell Reports Medicine*
  4(1):100895.
  Used in `friction/BreathingProtocol.kt`.
- **Bernardi, L., et al. (2001)** — *J. Hypertens.*
  19(12):2221-2229.
  Used in `friction/BreathingProtocol.kt`. (Replaces the
  fabricated "Bernardi 2018" reference; see UNANCHORED below.)
- **Linardon, J. (2020)** — *Behavior Therapy* 51(4):646-658.
  Used in `friction/CompassionMoment.kt`. (Replaces the
  misattributed "Linardon 2020, J. Clin. Psychol."; the journal
  was wrong.)
- **Liu, X., et al. (2023)** — *Psicologia: Reflexão e Crítica*
  36:32.
  Used in `friction/CompassionMoment.kt`.
- **Masicampo, E. J., & Baumeister, R. F. (2011)** — *J. Pers.
  Soc. Psychol.* 101(4):667-683 — DOI 10.1037/a0024192.
  Used in `friction/OpenLoop.kt`, `sleep/BedtimeList.kt`.
- **Dimidjian, S., et al. (2006)** — *J. Consult. Clin. Psychol.*
  74(4):658-670 — DOI 10.1037/0022-006X.74.4.658.
  Used in `sim/personas/DepressionLowMotivationPersona.kt`.
  (Replaces the unverified "aan het Rot 2012 BA" reference.)

## Misattributions flagged

- **"Bernardi 2018, J. Physiol. 596(8):1449-1464"** — searched,
  no paper matches. Replaced with **Bernardi et al. 2001, *J.
  Hypertens.* 19(12):2221-2229**, which is the verified
  reference for the slow-exhale / parasympathetic mechanism. The
  "Bernardi 2018" citation that previously appeared in
  `BreathingProtocol.kt` and `FrictionGate.kt` was removed and
  replaced with the 2001 reference.
- **"Carney 2010, HRV + worry"** — searched, the most cited
  Carney HRV paper is **Carney et al. 2005, *Arch. Intern. Med.*
  165(13):1486-1491** (low HRV and post-MI mortality). The
  "worry → low HRV" mechanism is well-supported, but the
  verified references are **Brosschot, Gerin, & Thayer 2006**
  and **Thayer & Lane 2000**, not a "Carney 2010" paper. Use
  Brosschot 2006 / Thayer & Lane 2000 instead.
- **"aan het Rot 2012, behavioral activation"** — the **Aan
  het Rot, Hogenelst, & Schoevers 2012, *Clin. Psychol. Rev.*
  32(6):510-523** paper is on experience sampling and mood
  disorders, *not* behavioral activation. The
  behavioral-activation reference is **Dimidjian et al. 2006**.
  Use Dimidjian 2006 instead.
- **"Linardon 2020, J. Clin. Psychol."** — the Linardon
  meta-analysis is published in *Behavior Therapy* 51(4):646-658,
  not *J. Clin. Psychol.* The KDoc in `CompassionMoment.kt` and
  `CompassionMomentTest.kt` was updated to the correct journal.

## Removed (UNANCHORED)

The following claims appeared in earlier planning notes but
could not be verified against a primary source. They are excluded
from code-level citations until verified. If a verifiable primary
source exists for any of these, please open a doc issue and add
the entry to `22-research-index.md`.

- **"Wilson 2014, 17% of mind wandering is unpleasant"** —
  the closest verified paper is Killingsworth & Gilbert 2010,
  which reports 26.5% unpleasant, 42.5% pleasant, 31% neutral.
  The 17% figure does not match this paper and may be a
  misattribution. **DO NOT CITE.**
- **"Tang 2015, mindfulness and brain networks"** — original
  source not yet verified.
- **"Mark et al. 2016, workplace interruption cost"** — original
  source not yet verified.
- **"Pielot & Rello 2017, notification volume"** — original
  source not yet verified.
- **"Windt 2016, sleep window optimizer"** — the "wind-down"
  concept is well-supported, but no Windt 2016 paper matches.
  The actual reference is the Windred 2024 paper above.
  **DO NOT CITE "Windt 2016" — use Windred 2024 instead.**
- **"Walker 2017, sleep window optimizer"** — no Walker 2017
  paper matches the cited sleep-window claim. The verified
  reference for the regularity-over-duration finding is
  Windred 2024. **DO NOT CITE "Walker 2017" — use Windred 2024
  instead.**
- **"Task Force 1996, HRV standards"** — the actual Task Force
  paper is **Task Force of the European Society of Cardiology
  and the North American Society of Pacing and Electrophysiology
  (1996)**, *Circulation* 93(5):1043-1065. This is a known
  reference but the citation KDoc has not been added to
  `vitals/HRV.kt` yet — the launcher's HRV math uses simpler
  Iglewicz/Hoaglin-style robust z-scores that don't need the
  full Task Force standards. If a future refactor needs the
  citation, add it with the verified journal + DOI.
- **"Shaffer & Ginsberg 2017, HRV overview"** — known reference
  (published in *Front. Public Health* 5:258), but not currently
  cited in the codebase. Add if needed.
- **"Berry 2017, sleep stages AASM"** — known reference
  (the AASM Manual), but the launcher's sleep math doesn't use
  AASM stages; the regularity / onset computation is from
  `SleepMath.regularityScore` and `SleepRepository.estimate`,
  which do not stage the sleep. **DO NOT CITE** unless a
  future feature adds AASM staging.
- **"Smeekes 2020, self-compassion and well-being meta"** —
  original source not yet verified; the verified self-compassion
  reference is Linardon 2020.
- **"Smyth 1998, written emotional expression and health"** —
  original source not yet verified; the verified expressive
  writing reference is Pennebaker 1997.

## Why this audit exists

A wrong citation in a clinical mental-health context is a
reputation cost the launcher cannot afford. The audit is a
single point of truth for "what is verified, what is replaced,
what is removed." New citations added in future versions are
expected to land in `22-research-index.md` first, and the
KDoc that uses them cites the same paper. A future audit can
diff the index against the codebase's `KDoc` `@see` lines and
flag any drift.

## Coverage matrix (v0.22.0)

| Feature | Citation | Where used |
|---|---|---|
| Robust z-score (MAD) | Iglewicz & Hoaglin 1993 | `WellnessSignals.kt` |
| Per-person anomaly cut-off | Jacobson 2019 | `WellnessSignals.kt` |
| Bedtime list | Scullin 2018 | `BedtimeList.kt` |
| Chronotype (default window) | Roenneberg 2007, Wittmann 2006, Åkerstedt 2003, Kecklund 2016 | `Chronotype.kt`, `SunsetPrefs.kt` |
| Sleep regularity | Windred 2024 | `OpenLoop.kt`, `SleepWindowOptimizer.kt` |
| Insomnia cognitive model | Harvey 2002, Baglioni 2016 | `InsomniacPersona.kt` |
| Mind wandering | Killingsworth & Gilbert 2010 | `OpenLoop.kt` |
| Perseverative cognition | Brosschot 2006, Thayer & Lane 2000 | `SunsetPrefs.kt`, `DepressionLowMotivationPersona.kt` |
| Habit formation | Lally 2010 | `FrictionGate.kt` |
| Self-compassion | Neff 2003, Linardon 2020, Liu 2023 | `CompassionMoment.kt` |
| Expressive writing | Pennebaker 1997 | `CheckIn.kt` |
| Friction gate (one-sec app) | Grüning 2023 | `FrictionGate.kt` |
| Implementation intentions | Gollwitzer 1999, Gollwitzer & Sheeran 2006 | `FrictionGate.kt` |
| Cyclic sighing | Balban 2023 | `BreathingProtocol.kt` |
| Slow breathing baroreflex | Bernardi 2001 | `BreathingProtocol.kt` |
| Zeigarnik effect (plan-making) | Masicampo & Baumeister 2011 | `OpenLoop.kt`, `BedtimeList.kt` |
| Behavioral activation | Dimidjian 2006 | `DepressionLowMotivationPersona.kt` |
