# Clinical review pack

**Status: not yet reviewed by a clinician.** Nothing in this document
should be read as clinical endorsement. It exists so that a qualified
reviewer can spend their time judging decisions rather than reconstructing
them from source code.

MindAnchor is a phone launcher. It is not a medical device, makes no
diagnostic claim, and provides no therapy. It nonetheless holds a suicide
safety plan, the phone numbers a person would call at their worst, a
validated wellbeing questionnaire, and distress-tolerance skills drawn
from DBT. Those four things put it inside the blast radius of clinical
harm, which is why this document exists.

---

## 1. What a reviewer is being asked to judge

Six questions, in descending order of how much harm a wrong answer causes.

1. **Is the safety plan implementation faithful to Stanley & Brown, and is
   a partially-completed plan safe to leave in place?** The app lets
   someone save a plan with any subset of fields filled. A half-written
   plan may be worse than none if it creates false confidence.
2. **Is the crisis card correct, current, and correctly ordered?** Wrong
   numbers here are the single most dangerous defect the app can carry.
3. **Is the WHO-5 presented and scored in a way that does not mislead?**
   In particular: is showing a bare 0–100 score to the person who answered
   it appropriate, and is the low-score response proportionate?
4. **Are the DBT skills (STOP, TIPP, 5-4-3-2-1) safe to present without a
   therapist?** TIPP in particular involves physiological interventions.
5. **Is notification batching safe for someone whose relationships are
   volatile?** Delaying a message from a person in conflict may reduce
   reactivity, or may read as abandonment.
6. **Is the app's framing honest about what it is not?**

---

## 2. Clinical instruments used, and their provenance

| Instrument | Use here | Source | Licence |
|---|---|---|---|
| WHO-5 Well-Being Index | Optional self-check, scored 0–100 | WHO 1998; validated in Topp et al. 2015 | Public domain |
| Safety Planning Intervention | Structure of the safety plan screen | Stanley & Brown 2012 | Structure used; no proprietary text reproduced |
| DBT distress tolerance (STOP, TIPP) | Skill reminders | Linehan | Described in the app's own words |
| 5-4-3-2-1 grounding | Skill reminder | Widely used, no single owner | — |

Scoring is implemented in `WhoFive.kt`: five items each 0–5, raw sum
multiplied by four. A score is refused unless all five items are answered.
`WhoFiveTest` pins this.

---

## 3. Deliberate design decisions with clinical consequences

These are the choices most worth challenging. Each was made on reasoning
that is stated rather than assumed, so a reviewer can disagree with the
reasoning specifically.

**No streaks, no goals, no congratulation.** Behavioural reward loops are
the mechanism the rest of the phone already uses. Applying them to mood
risks turning a self-check into a performance, and a missed day into a
failure. The cost is lower engagement, accepted deliberately.

**The app never interprets a score.** A low WHO-5 produces an offer of
support, not an assessment. The app does not say "you may be depressed"
because it is not entitled to.

**Crisis contacts bypass batching unconditionally.** A chosen person is
never delayed, regardless of app or quiet hours
(`NotificationClassifier.shouldHold`). The listener now holds nothing at
all until that bypass list has loaded, so the race that could have
delayed such a message is closed.

**Nothing is enabled without being chosen.** Onboarding elicits goals and
enables nothing; the closing screen points at where each feature is
switched on. Imposed minimalism fails ("Going Light", CHI 2026);
self-endorsed structure does not.

**A failed crisis dial is reported, never swallowed.** If no dialer opens,
the screen says so and shows the number in plain text. Silence would leave
someone believing a call was placed.

**No data leaves the device, and this is structural.** There is no
`INTERNET` permission, and cloud backup and device-to-device transfer are
both refused. `PrivacyTest` asserts both against the installed app.

---

## 4. Risk register

| # | Risk | Current mitigation | Residual | Needs a clinician |
|---|---|---|---|---|
| R1 | Crisis number wrong, stale, or not valid in the user's country | Local line ordered first; full list never filtered; `findahelpline.com` always shown; unit-tested ordering | **Numbers verified only against author knowledge, not against operators** | Yes — verification |
| R2 | Partially-completed safety plan gives false confidence | Plan is optional and free-text; reader shows only completed sections | Unmeasured | Yes |
| R3 | WHO-5 score distresses the person who answered it | Low score triggers support offer, never an alarm or diagnosis | Unmeasured | Yes |
| R4 | TIPP presented without supervision (cold water, intense exercise) | Contraindications now shown with the skill: cardiac conditions, eating disorders, pregnancy, with the gentle two steps flagged as safe alone | Wording not clinically reviewed; list may be incomplete | Yes — confirm the list |
| R5 | Batching delays a message that mattered | Crisis contacts bypass; conversations and ongoing notifications excluded; journal preserves everything | Unmeasured | Yes |
| R6 | Someone in crisis cannot find support fast enough | Support is one tap from home, never behind a menu | Untested with real users | Yes |
| R7 | App is mistaken for treatment | About text states it is a wellness tool, not a medical device | Unmeasured | Yes |
| R8 | Safety plan readable by anyone holding the phone | Device lock only; no separate app lock | **Accepted gap** — a lock could also block access during crisis | Yes — genuine tension |

R8 is the one I would push hardest on: a genuine two-sided tension that
should be decided by someone qualified rather than by the author.

R4 was found while writing this register and has been partly closed — TIPP
now carries contraindications, having previously asked something physical
of the reader without saying who should not do it. The wording still needs
checking, and the list of conditions may be incomplete.

---

## 5. What has been verified, and how

- Unit tests cover scoring, notification classification, phone matching,
  crisis-line ordering, sleep and sunset maths, and contrast.
- Instrumented tests run on emulators at API 33 and 34 and cover the
  support screen, safety plan round-trip, onboarding, the pulse flow,
  large font scales, and the privacy guarantees.
- Contrast ratios were solved numerically; worst case is 4.56:1.

**What has not been verified:** no clinician has reviewed any of this, no
person with lived experience has tested it, and the crisis numbers have
not been checked against the operators. The app has also never been used
by anyone for a sustained period.

---

## 6. How to give feedback

Open an issue, or annotate this file directly. Findings that change
behaviour should land as a change to the risk register above, so the
reasoning stays attached to the decision rather than living in a thread.
