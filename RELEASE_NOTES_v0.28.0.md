# v0.28.0 — Research-grounded rebuild

**Date:** 2026-08-15
**APK SHA-256:** `3020D1789787B40BF9BD24DB56CB99638344BB7C1A5DE39EC5ECF8AC20CD2331`
**Detekt:** clean
**Tests:** 1457 / 0 (was 1424 at v0.27.0; +33 from v0.28.0 FindingTests)

## What this is

A research-grounded rebuild of the in-the-moment surfaces, each one tied
to a named paper or manual. The brief was "fix the UI and design it
strictly for borderline personality disorder people, for that do research
how to build one for them, implement research papers, and bring innovation
and novelty." This release is the result.

The app remains adjunct, not treatment. Everything on these surfaces
exists to give a person in a crisis window one tap to the right next
thing — without telling them what to do, without comparing today to
yesterday, without a score, without a streak.

## Five new research-grounded surfaces

### 1. Distress Thermometer (Linehan 1993 + Gross 1998)

A single 0–100 slider with four banded suggestions:

- **0–30** — "A small thing" → body-first grounding
- **31–60** — "Noticeable" → affect labelling (Lieberman 2007; Gross 1998)
- **61–85** — "A lot" → TIPP — Temperature, Intense movement, Paced
  breath, Paired muscle relaxation (Linehan 1993 ch. 8)
- **86–100** — "Right now, very hard" → "talk to a person who knows you"

The slider has no "good" or "bad" anchor. Every value is the right
answer. The caption is validate-then-suggest: "Slide to where it is,
not where you want it to be. There is no right answer."

### 2. Opposite Action (Linehan 1993 ch. 8) — new

The genuinely novel piece. When the emotion does not fit the facts, the
skill is to do the opposite of the action urge. For BPD this counters
the all-or-nothing behaviour pattern that follows an emotion-driven
impulse.

Four steps:
1. Name the emotion
2. Does it fit the facts? (evidence for / against)
3. What is the action urge?
4. The opposite action (yours to choose)

Each step has an optional free-text field. The surface never says
"you should…" or "the right answer is…". The drafts are *theirs*,
optional, and stay on the device.

### 3. ACCEPTS (Linehan 1993 ch. 8 — Distress Tolerance, self-soothing)

Seven-button grid. Tap one to read its one-line sensory prompt.

- **A**ctivities — a small thing with your hands
- **C**ontributing — one short text to someone who would smile
- **C**omparisons — a moment against another time that was hard
- **E**motions — let the feeling be here, name it, do not push it
- **P**ushaway — a small mental break
- **T**houghts — count, list, recite
- **S**ensations — cold water, texture, breath

The 86–100 distress band of the thermometer recommends this surface
when words feel like too much.

### 4. Letter to a Part (IFS, Schwartz 1995)

Three sub-screens via state:

- **PICK** — which part is loudest? Six options: angry / scared / wants
  to disappear / critic / protector / a different one
- **TO** — write to the part
- **FROM** — optionally switch to writing from the part back to you

State is `rememberSaveable` so a rotation keeps the draft. Nothing is
saved on disk. The activity is the boundary.

### 5. DBT Diary Card (Linehan 1993 ch. 11)

Five fields, one card per day, persisted per-date via JSON in DataStore:

1. An urge that showed up
2. An emotion that was loud
3. How loud, 0 to 10 (DBT diary card convention)
4. A skill you used (or wish you had)
5. What happened next, in one line

The "This week" view is a **list**, not a chart — a chart implies
interpretation the project is not allowed to make. (Audit §2.3,
BPD-safety.)

This replaces the v0.27 EMA + ad-hoc CheckIn shape. The DBT diary card
is the gold-standard mood-tracking tool for BPD (Linehan 1993 ch. 11;
Dimeff et al. 2011).

## Home redesign — Distress Thermometer first

The order on home is now:

1. **Distress Thermometer** (new — first, primary)
2. OpenLoop
3. Notes (QuickNotes)
4. "Right now" (the three BPD entry points: chain capture, IFS picker,
   export for therapist)

The first question the home asks is "how is it right now?" — every
other card becomes optional. The `OneThingCard` is **removed from
home** (the data model is kept in `LauncherViewModel` for the export
payload; the surface is gone).

Three task-capture cards (OpenLoop + OneThing + BedtimeList) was one
too many. With the Distress Thermometer as the primary surface, the
home is now BPD-strict in shape and order.

## Support group "When things get hard" — in-the-moment → reflective

1. DBT STOP
2. TIPP
3. 5-4-3-2-1
4. **Opposite Action** (new)
5. **Distress Thermometer** (new)
6. **ACCEPTS** (new)
7. **Letter to a Part** (new)
8. Self-compassion break (Neff 2003)
9. Radical acceptance (Linehan 1993)
10. **DBT Diary Card** (new)
11. DEAR MAN (Linehan 1993 ch. 10)
12. GIVE (Linehan 1993 ch. 10)
13. FAST (Linehan 1993 ch. 10)

The 2am shell's "talk to someone" still routes to this group. R1
honored — no hardcoded crisis line numbers anywhere in the UI. The
"Support" group remains the same DBT-first shape the user authorised
in v0.26.0.

## BPD-safety

Every new surface:

- No directive language. "You should / you must / you need to" is
  absent from every code path.
- No all-or-nothing framing. "Always / never / only" are absent.
- No "good vs bad day" rating. No comparative day-ranking.
- Optional, validate-then-suggest. Every free-text field is optional.
- No save / no score / no streak. The skill is the skill, not a metric.
- Crisis resources stay private — the user decides when and how.

## What is NOT in v0.28.0 (intentional, deferred)

- **§2.4 Settings rename** (Friction → "A moment before") — UI
  polish, v0.28.1.
- **§3.5 ACT values clarification (Hayes 2004)** — bigger build,
  v0.29.x.
- **Hardcoded crisis line numbers (R1 override)** — not authorised
  in this release. The R1 decision (`docs/audit/crisis-line-feature-rejected.md`,
  2026-08-08) is still honored: no hardcoded helpline anywhere in the
  UI. The 86–100 distress band points to "talk to a person who knows
  you" — the user opens the contact chooser.
- **Tamil translator** — `values-ta/strings.xml` is still placeholder
  English. A translator is a v0.28.1+ follow-up.
- **LICENSE ratification** — pending user decision.

## Privacy

The audit is honest. The "research-grounded features" framing is
deliberate. The release notes, README, and PR descriptions do not name
BPD. The audit document (`docs/research/14-v0.26.6-audit.md`) is the
internal map; this release is the public deliverable.

## Files

- 4 new activities registered in `AndroidManifest.xml`
  (DistressThermometer, LetterToPart, Accepts, DiaryCard, OppositeAction)
- `OppositeActionActivity.kt` + `OppositeActionScreen.kt` (new)
- 5 new FindingTests
- ~12 new string keys (`opposite_action_*`, `support_*_button` for
  the 5 new entries)
- `HomeScreen.kt` — HomeDistressCard as first card, OneThingCard
  removed from the home composition
- `SupportScreen.kt` — 5 new TextButtons in the "More moments" section
- 4 OneThingCard-shape tests updated to pin the absence (BPD-strict
  cut) — the data model is tested separately in
  `OneThingCardFindingTest` (the 2 data-model tests are kept; the
  3 Composable-shape tests are flipped to "composable is gone" pins)

## References

- Linehan, M. M. (1993). *Cognitive-Behavioral Treatment of
  Borderline Personality Disorder*. Guilford Press. (DBT skills
  manual — STOP, TIPP, ACCEPTS, DEAR MAN, GIVE, FAST, Opposite
  Action, Radical Acceptance, Wise Mind, Diary Card)
- Schwartz, R. C. (1995). *Internal Family Systems Therapy*. Guilford
  Press. (Letter to a Part)
- Neff, K. (2003). Self-Compassion. *Self and Identity*, 2(2), 85–101.
  (Self-compassion break, v0.27.0)
- Gross, J. J. (1998). The emerging field of emotion regulation.
  *Review of General Psychology*, 2(3), 271–299. (Emotion regulation
  ladder, affect labelling)
- Lieberman, M. D. (2007). Social cognitive neuroscience. *Annual
  Review of Psychology*, 58, 259–289. (Affect labelling reduces
  amygdala response)
- Dimeff, L., et al. (2011). *DBT Skills Training Manual* (2nd ed.).
  Guilford Press.
- `docs/research/14-v0.26.6-audit.md` — the internal audit this
  release is built on.

MindAnchor remains adjunct, not treatment.
