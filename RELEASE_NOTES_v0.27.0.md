# v0.27.0 — DBT Module 4 + Neff self-compassion + Linehan radical acceptance

**Tag:** `v0.27.0`
**versionCode:** 49 → 50
**versionName:** `0.26.6` → `0.27.0`
**Branch:** `work/v0.21.0-10of10`

## What this release does

Three new evidence-grounded "right now" surfaces in SupportActivity, plus two audit-driven copy fixes. Each is a small Composable reached from a button in the existing Support screen.

### 1. Self-compassion break (Neff 2003)

A 3-sentence, 45-second timer-driven exercise:
1. "This is a moment of suffering."
2. "Suffering is part of being human."
3. "May I be kind to myself."

The break is a single-screen Composable. Each sentence is shown one at a time, large, centred, with a thin linear progress indicator. The Done button dismisses at any time. No log, no score, no history. **BPD-safe:** validate-then-suggest framing, no directive language, no all-or-nothing, no good-vs-bad day.

Activity: `org.mindanchor.support.SelfCompassionActivity`
Composable: `SelfCompassionScreen`

### 2. Radical acceptance (Linehan 1993, DBT Distress Tolerance Module 2)

A 4-sentence, 40-second timer-driven exercise for situations that cannot be changed:
1. "Reality is what it is."
2. "The pain is part of the pain."
3. "Accepting reduces suffering."
4. "Refusing acceptance increases suffering."

Same shape as the self-compassion break — single-screen, line-at-a-time, progress indicator, Done button.

Activity: `org.mindanchor.support.RadicalAcceptanceActivity`
Composable: `RadicalAcceptanceScreen`

### 3. DBT Module 4 — Interpersonal Effectiveness (Linehan 1993 ch. 10, McKay et al. 2007)

A 4-screen activity (menu + 3 script sub-screens) for the three DBT interpersonal skills:
- **DEAR MAN** — Describe / Express / Assert / Reinforce / (stay) Mindful / Appear confident / Negotiate. For asking for what you want.
- **GIVE** — Gentle / (act) Interested / Validate / Easy manner. For keeping a relationship OK.
- **FAST** — Fair / (no) Apologies / Stick to values / Truthful. For keeping your self-respect.

Each script has the acronym, the line-by-line gloss, and an **optional** "draft of what you might say" text field. The draft is *theirs* and stays on the device. The surface never says "send this" or "you should...".

Activity: `org.mindanchor.support.InterpersonalActivity`
Composable: `InterpersonalScreen` (menu + 3 sub-screens via state var)

### 4. §2.2 audit fix — BpdProfile labels shortened

The BPD profile checkboxes (in Settings → PAUSES) had a 10-word "I want a named person to call when it gets bad" label. Now: "A named person to call". The explainer went from a multi-sentence question to one validating sentence: "Anything you turn on is opt-in. The launcher's defaults are the safest."

### 5. §2.6 audit fix — Letters thumbs-down copy

Pre-v0.27.0: "Tell us what was off" (correction-first, can split into self-criticism for BPD).
v0.27.0: "That is helpful. What would feel more like you?" (validation-first, soft open question).
The hint also changed: "Optional. No one sees this but you."

## What v0.27.0 does NOT do (deferred)

Per `docs/research/14-v0.26.6-audit.md`:

- **§2.1 crisis line numbers in 2am shell** — blocked on the user's R1 decision from 2026-08-08 (no hardcoded helpline). Honoring the prior decision until told otherwise.
- **§2.3 EMA / CheckIn history audit** — verified already list-shaped (the KDoc on `CheckInHistoryScreen.kt` says "Why a list view, not a chart"). No fix needed.
- **§2.4 Settings rename** ("Friction" → "A moment before", etc.) — UI polish, not research-grounded. v0.27.1.
- **§2.5 DBT diary card** (replaces EMA + CheckIn) — bigger build with stateful data model. v0.28.0.
- **#5 ACT values clarification** (Hayes 2004) — bigger build. v0.28.0.

## What changed

| File | Change |
| --- | --- |
| `app/src/main/java/org/mindanchor/support/SelfCompassionActivity.kt` | **NEW** — single-Activity host for the break |
| `app/src/main/java/org/mindanchor/support/SelfCompassionScreen.kt` | **NEW** — 3-line timer Composable |
| `app/src/main/java/org/mindanchor/support/RadicalAcceptanceActivity.kt` | **NEW** — single-Activity host for the exercise |
| `app/src/main/java/org/mindanchor/support/RadicalAcceptanceScreen.kt` | **NEW** — 4-line timer Composable |
| `app/src/main/java/org/mindanchor/support/InterpersonalActivity.kt` | **NEW** — single-Activity host for the 3 DBT skills |
| `app/src/main/java/org/mindanchor/support/InterpersonalScreen.kt` | **NEW** — menu + 3 script sub-screens |
| `app/src/main/java/org/mindanchor/support/SupportScreen.kt` | Added "More moments" section with 3 TextButtons launching the new activities |
| `app/src/main/AndroidManifest.xml` | Registered 3 new activities (non-exported) |
| `app/src/main/res/values/strings.xml` + `values-ta/strings.xml` | Added ~50 new keys (titles, captions, lines, acronyms) |
| `app/src/test/java/org/mindanchor/support/SelfCompassionFindingTest.kt` | **NEW** — 3 FindingTests |
| `app/src/test/java/org/mindanchor/support/RadicalAcceptanceFindingTest.kt` | **NEW** — 3 FindingTests |
| `app/src/test/java/org/mindanchor/support/InterpersonalFindingTest.kt` | **NEW** — 5 FindingTests (menu, line counts, optional-draft, all-strings) |
| `app/src/test/java/org/mindanchor/letters/LetterThumbsDownFindingTest.kt` | Added 1 test (validation-first copy) |
| `app/build.gradle.kts` | `versionCode` 49 → 50, `versionName` "0.26.6" → "0.27.0" |

## Tests

- 1424 tests, 0 failed, 0 ignored (was 1412 in v0.26.5; +12 from new tests)
- detekt clean

## End-to-end on phone (Motorola ZD2232FCR5, Android 17, 03:54 IST)

1. App launches, no FATAL. versionCode=50, versionName=0.27.0.
2. Home renders 3:54 + "Here, now." + Notes + OneThing + Right now. No BedtimeList (v0.26.6 cut), no "Wellbeing pulse" (v0.26.6 cut).
3. Tap support (bottom-left) → SupportActivity opens with the existing 3 DBT skills (STOP / TIPP / 5-4-3-2-1).
4. Scroll down → new "More moments" section with 3 buttons (Self-compassion break / Radical acceptance / Interpersonal skills).
5. Tap "Self-compassion break" → SelfCompassionActivity opens. Timer cycles through 3 sentences (15s each). Done dismisses. No FATAL.
6. Tap "Radical acceptance" → RadicalAcceptanceActivity opens. Timer cycles through 4 sentences (10s each). Done dismisses. No FATAL.
7. Tap "Interpersonal skills" → InterpersonalActivity opens with the menu (DEAR MAN / GIVE / FAST / back). Tap "DEAR MAN" → script screen renders all 7 steps + optional draft field + Done. No FATAL.

APK SHA-256: `CB5022A4A0EC2C4B41C556F9EED77AF5F8202D15C1DE25A65FCAE8334510405A`

## Why §2.1 (crisis lines) was NOT done

The audit `docs/research/14-v0.26.6-audit.md` recommended adding iCall / Vandrevala / AASRA numbers to the 2am shell "I want to talk to someone" outcome. This is a meaningful change from the project's existing R1 decision (2026-08-08, `docs/audit/crisis-line-feature-rejected.md`) which explicitly rejected hardcoded helpline numbers in the app on the rationale that "prominent hotline numbers can frighten people and clutter a screen meant to feel calm."

v0.27.0 honours the R1 decision and does not add the numbers. **The R1 decision is the project owner's to revisit, not the assistant's to override.** If the user wants v0.27.1 to ship the crisis-line addition (and override R1), that is one focused small change.

## Research citations

- Neff 2003, "Self-compassion: an alternative conceptualization of a healthy attitude toward oneself", *Self and Identity*
- Neff & Germer 2013, "A pilot study and randomized controlled trial of the Mindful Self-Compassion program", *J Clin Psychol*
- Linehan 1993, *Cognitive-Behavioral Treatment of Borderline Personality Disorder*, ch. 8 (Distress Tolerance) and ch. 10 (Interpersonal Effectiveness)
- McKay, Wood & Brantley 2007, *The DBT Skills Workbook*
- Dimeff et al. 2011, *DBT Skills Training Manual* (2nd ed.)
