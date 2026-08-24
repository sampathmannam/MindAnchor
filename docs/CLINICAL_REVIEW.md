# Clinical review pack

**Status: not yet reviewed by a clinician.** Nothing in this document
should be read as clinical endorsement. It exists so that a qualified
reviewer can spend their time judging decisions rather than reconstructing
them from source code.

MindAnchor is a phone launcher. It is not a medical device, makes no
diagnostic claim, and provides no therapy. It nonetheless holds a suicide
safety plan, the phone numbers a person has chosen as the ones they would
call at their worst, a validated wellbeing questionnaire, and
distress-tolerance skills drawn from DBT. Those four things put it inside the blast radius of clinical
harm, which is why this document exists.

---

## 1. What a reviewer is being asked to judge

Six questions, in descending order of how much harm a wrong answer causes.

1. **Is the safety plan implementation faithful to Stanley & Brown, and is
   a partially-completed plan safe to leave in place?** The app lets
   someone save a plan with any subset of fields filled. A half-written
   plan may be worse than none if it creates false confidence.
2. **Is it acceptable that the app offers no crisis line at all?** This
   was a deliberate product decision (see R1). It is the question most
   worth a second opinion.
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

**A failed call is reported, never swallowed.** If no dialer opens, the
screen says so and shows the number in plain text. Silence would leave
someone believing a call was placed.

**No data leaves the device, and this is structural.** There is no
`INTERNET` permission, and cloud backup and device-to-device transfer are
both refused. `PrivacyTest` asserts both against the installed app.

---

## 4. Risk register

| # | Risk | Current mitigation | Residual | Needs a clinician |
|---|---|---|---|---|
| R1 | **No crisis line is offered anywhere in the app.** Hardcoded helplines were removed by product decision, on the grounds that they frighten people and add complexity. A person in acute crisis with no contact saved has no route to help from this app | The safety plan and the person's own chosen contacts remain; the app states it is no substitute for talking to someone | **Re-confirmed 2026-08-08.** `docs/audit/crisis-line-feature-rejected.md` records a full evidence review of the R1 decision (Stanley & Brown 2012 SPI Step 5; WHO mhGAP 2023; SAMHSA 988; APA Digital Mental Health 101; Dwyer 2025 *Psychiatr Serv* 76:867–871; NHS Design Patterns for Mental Health) followed by a prototype of a calm, country-aware, opt-in "Get help now" entry on the support screen. The project owner reviewed the prototype and **chose not to ship it** — even an opt-in card is a surface that fires when someone is having a hard time, and that was the kind of surface the project did not want. The R1 decision is therefore stronger than the original "frighten people" rationale alone: no in-app crisis-line UI of any kind, opt-in or otherwise. The safety plan and the user's own contacts remain the only routes; the app's footer still says "if you are in danger right now, call your local emergency number." The decision and the evidence base it was reviewed against are both on file. | Yes — this remains the first thing to review |
| R2 | Partially-completed safety plan gives false confidence | Plan is optional and free-text; reader shows only completed sections | Unmeasured | Yes |
| R3 | WHO-5 score distresses the person who answered it | Low score triggers support offer, never an alarm or diagnosis | Unmeasured | Yes |
| R4 | TIPP presented without supervision (cold water, intense exercise) | Contraindications now shown with the skill: cardiac conditions, eating disorders, pregnancy, with the gentle two steps flagged as safe alone | Wording not clinically reviewed; list may be incomplete | Yes — confirm the list |
| R5 | Batching delays a message that mattered | Crisis contacts bypass; conversations and ongoing notifications excluded; journal preserves everything | Unmeasured | Yes |
| R6 | Someone in crisis cannot find support fast enough | Support is one tap from home, never behind a menu | Untested with real users | Yes |
| R7 | App is mistaken for treatment | About text states it is a wellness tool, not a medical device | Unmeasured | Yes |
| R8 | Safety plan readable by anyone holding the phone | Device lock only; no separate app lock | **Accepted gap** — a lock could also block access during crisis | Yes — genuine tension |

R1 has now been reviewed against the full primary safety literature
(see `docs/audit/crisis-line-feature-rejected.md`), and the project
owner re-decided it on 2026-08-08 in the *stronger* direction:
not just "no hardcoded crisis line" but "no in-app crisis-line UI
of any kind, opt-in or otherwise." The reasoning the owner gave
was that even an opt-in card on the support screen is a surface
that fires when someone is having a hard time, and that was the
kind of surface the project did not want. The reviewer is still
the right person to push on this — the evidence in the brief
remains the evidence, and the trade (a safety plan missing
SPI Step 5 unless the person fills it in) is a real cost — but
the project owner's preference is now on file as stronger than
the original "frighten people" rationale alone.

R8 remains a genuine two-sided tension that should also be decided by
someone qualified rather than by the author.

R4 was found while writing this register and has been partly closed — TIPP
now carries contraindications, having previously asked something physical
of the reader without saying who should not do it. The wording still needs
checking, and the list of conditions may be incomplete.

---

## 5. What has been verified, and how

- Unit tests cover scoring, notification classification, phone matching,
  sleep and sunset maths, and contrast.
- Instrumented tests run on emulators at API 33 and 34 and cover the
  support screen, safety plan round-trip, onboarding, the pulse flow,
  large font scales, and the privacy guarantees. 43 tests, green on both.
- Contrast was re-solved after rendering the palette and measuring every
  text position at every minute of the day, in both themes. The worst case
  is 4.57:1. The previous figure in this document, 4.56:1, was wrong: the
  solver had never checked the band the clock sits on, where the true
  worst case was 3.77:1.

**What has not been verified:** no clinician has reviewed any of this, and
no person with lived experience has tested it. Nobody has used the app for
a sustained period, and until very recently nobody had seen it render at
all — the screens have been checked by rendering the palette code
directly rather than by looking at a running phone.

---

## 6. How to give feedback

Open an issue, or annotate this file directly. Findings that change
behaviour should land as a change to the risk register above, so the
reasoning stays attached to the decision rather than living in a thread.

---

## 7. Plain-language data-flow diagram (Settings → About)

**Status:** Phase 0 doc-track. The actual Settings row ships with the
Phase 0 PR that surfaces this section in `app/src/main/java/.../settings/SettingsScreen.kt`.
The privacy promise is structural and is enforced by `PrivacyTest` and
`NetworkCallsForbiddenTest` in the unit suite.

### What the app holds on the device

- The suicide safety plan and the phone numbers of the people you would call at your worst.
- The text of the notifications you have read or chosen to read.
- The mood history (your self-reported check-ins) and the WHO-5 Well-Being Index responses.
- Letters you have written, notes you have saved, open cognitive loops you have parked.
- Wearable data the launcher reads from Health Connect (heart rate, sleep, HRV, steps, mindfulness minutes) — **read only**, never written back.
- The local-only decisions the launcher has made for you (per-app session lengths, if-then plans, batched-notification schedule, Going Light windows).

### Where the data goes

- **The phone.** Every byte of the above lives on the device, in the app's private storage, encrypted with the Android Keystore. Backup is **off** (`allowBackup="false"` in the manifest, plus the rules-file gate at `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`). Device-to-device transfer is refused.
- **The VPN interface.** Going Light runs a local VpnService that captures loopback traffic and decides forward-or-drop per packet, locally. The VPN never tunnels anywhere; the loopback interface is the only place the captured packet goes. `NetworkCallsForbiddenTest` enforces that no outbound network call ever leaves the app.
- **The screen.** Everything you see is rendered from local data. There is no remote dashboard.

### Where the data does **not** go

- The phone's network. The `INTERNET` permission is held only because the VpnService API requires it; the runtime telemetry confirms zero outbound bytes.
- A cloud backup. Explicitly disabled in the manifest and the rules files.
- An analytics service. There is no analytics integration. There is no telemetry collection.
- A device-to-device transfer. The cloud-sync and local-transfer flags are both `false`.
- An LLM service. The on-device LLM (Phi-4) runs entirely on the device; the Groq cloud fallback exists in code but is disabled by the same network-call test.

### What you can do

- **Delete everything.** Settings → About → "Delete all my data" wipes the app's private storage and the wearable cache. The launcher reverts to the home screen; the system retains the launcher install.
- **Export the on-device log.** Settings → About → "Share diagnostic log" produces a redacted text file. The redaction is a regex pass that strips phone numbers, e-mail addresses, and any string that matches the `body` field of a held notification; the rest is the app's own internal log.

### What you should know

- This app is a **wellness tool**, not a medical device. The WHO-5 and the WHO-5 bands are not diagnoses; they are facts the launcher can show you about how your self-report has changed over time. The app never interprets a score as a diagnosis.
- The friction gate is **opt-in per app**. The launcher never blocks an app you have not asked it to gate.
- The notifications the launcher holds are **held, not deleted**. The journal entry persists; the launcher shows the entry in the digest and never posts a copy of the original.
- The Going Light VPN is **fail-closed**. If the per-packet decision function throws or the VPN loses its config, the launcher blocks traffic rather than lets it through.

