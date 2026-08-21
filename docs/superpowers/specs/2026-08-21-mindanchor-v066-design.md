# MindAnchor v0.66.0 — DBT-grounded Journal

**Date**: 2026-08-21
**Status**: Approved (2026-08-21)
**Owner**: Mavis (for Sampath M)
**Branch target**: `work/v0.21.0-10of10` (continue on v0.65.0 branch)
**Predecessor**: v0.65.0 (BPD-first journal, rejected by user)
**Source research**: `C:\Users\Sampath\.minimax\workspace\research\findings\` (100 files, 5 categories) + `_AGGREGATE.md`

---

## 1. Problem

v0.65.0 (BPD-first journal) was rejected by the user. The journal is technically correct (no counters, no time pressure, validate-then-suggest, crisis lines, DataStore) but does not deliver a **clinical-grade DBT experience** that the user expected after 100 findings of research on DBT-grounded MH apps.

The research points to a clear gap: Wysa (the consumer leader after Woebot's June 2025 shutdown) ships a chatbot with DBT *content* but **no DBT diary card, no DEAR MAN, no Wise Mind**. Headspace + Calm ship meditation, not DBT. The DBT-grounded v0.66.0 is the **only personal-R&D DBT diary card app in India**, and the **only DBT-journal app anywhere that ships bridge-to-therapist** as a first-class export.

## 2. Goals (v0.66.0)

1. **DBT-grounded Today + Diary Card** as the primary value.
2. **3 surfaces**: Today / Skills / Crisis.
3. **5 skills** in the library (TIPP, DEAR MAN, S.T.O.P., 3-Min Breathing Space, Wise Mind).
4. **4 India crisis lines** on the Crisis surface: iCall 9152987821, Vandrevala 1860-2662-362, AASRA 9820466726, Tele-MANAS 14416.
5. **Bridge-to-therapist PDF export** of the diary card.
6. **Preserve all v0.65.0 BPD-safe defaults** (no counters, no streaks, no time pressure, validate-then-suggest, on-device, N-of-1).

## 3. Non-goals (v0.66.0)

- Health Connect / HealthKit (wearables) — deferred to v0.67.0.
- Voice journaling (full STT) — deferred to v0.67.0.
- Tamil / Hindi i18n (requires clinician forward+back translation) — deferred to v0.67.0+ research.
- AI / LLM companion — never before BPD safety is measured. (Out of v0.66.0–v0.68.0.)
- Community / peer support — never.
- Streak / leaderboard / counter — never.
- Auto-triggered crisis escalation — never.

## 4. Architecture

### 4.1 Surfaces (top-level navigation)

**Today** (default surface)
- Single screen, time-of-day aware.
- Components: date / 14-day N-of-1 strip → mood (5 chips OR opt-in 2D Affect-Grid) → journal composer → skill-of-the-day card → diary card expander → crisis footer.
- Tap "I need help" on the strip → routes to Crisis.
- Tap any skill card → routes to Skills detail.

**Skills**
- Library of 5 skill cards. Single-tap to start, 60–90 sec each.
- Each card: title, "When to use", "How to do it" (verbatim protocol instructions), "Done" button.

**Crisis**
- Top: "I need to ground" panic button → S.T.O.P. or TIPP 60-sec card.
- Middle: Stanley-Brown 6-step Safety Plan (user-authored, on-device, revisitable).
- Bottom: 4 India crisis lines. Tap to dial. Long-press to see name + hours.

### 4.2 Settings (sub-screen, gear icon top-right of Today)

- Voice-first toggle (3 paths: crisis, check-in, skill selection) — default OFF.
- Diary card opt-in (opt-in for 2D Affect-Grid mood, opt-in for body map) — default OFF.
- "Share with therapist" PDF toggle — default OFF.
- v0.65.0 Settings items preserved (data export, version, etc.).

### 4.3 Data flow

```
JournalRoot (Compose state holder)
  ├─ JournalPrefs (DataStore: todayEntry — preserved from v0.65.0)
  ├─ DiaryCardPrefs (NEW DataStore: diaryCard[date] → 4 modules)
  ├─ SafetyPlanPrefs (NEW DataStore: 6-step plan → user text)
  └─ SkillsPrefs (NEW DataStore: lastSkillUsed[date], time-of-day patterns)

Each Prefs is the single source of truth.
No local mutableStateOf mirror.
collectAsStateWithLifecycle for reads.
Setter goes through DataStore, re-emits, distinctUntilChanged no-ops.
```

### 4.4 DBT Diary Card (4 modules)

Per Neacsiu 2010 + Simon 2022 JAMA + 4-7-feb-2026-bpd-digital research:

| Module | Input | Frequency | BPD safety |
|---|---|---|---|
| Urges (NSSI / suicidal / dissociation) | 0-5 slider | Always paired with skill-of-the-day | NEVER shown without skill pairing (avoids Simon 2022 standalone harm) |
| Emotions | 5 chips (default) OR 2D Affect-Grid (opt-in) | Per check-in | Affect-Grid capped at opt-in |
| Skill used | Today's "skill of the day" preselected + alt | After skill | Pure recording, no score |
| Bridge to therapist | One-tap PDF export | User-initiated, just-before-session | Active gesture = consent, default OFF |

**Bridge-to-therapist PDF** contains:
- Date range (user-selected, default last 14 days)
- Mood trend (N-of-1 own median + MAD + direction bands only)
- Skills used (count, names, dates)
- Journal snippets (user-selected, not auto-included)
- Crisis-line disclosure: "iCall, Vandrevala, AASRA, Tele-MANAS used during this period: N times"
- "This is a personal R&D tool, not a substitute for therapy" disclosure on first page.

PDF is generated on-device, never transmitted.

### 4.5 Skills library (5 skills)

| Skill | When | How (summary) | Time |
|---|---|---|---|
| **TIPP** | Crisis (extreme distress) | Temperature (cold water on face) → Intense exercise → Paced breathing → Paired muscle relaxation | 60 sec |
| **DEAR MAN** | Interpersonal | Describe → Express → Assert → Reinforce → Mindful → Appear confident → Negotiate | 90 sec |
| **S.T.O.P.** | In-the-moment | Stop → Take 3 breaths → Observe → Proceed mindfully | 60 sec |
| **3-Min Breathing Space** | Daily practice | Awareness (1 min) → Breath (1 min) → Body (1 min) | 180 sec |
| **Wise Mind** | Decision point | Emotion mind + Reasonable mind → Wise mind (the middle path) | 60 sec |

Each card has: title, "When to use" (1 line), "How to do it" (the protocol instructions, 4-6 lines), "Done" button. No "you've done this N times." No streak. No master points.

### 4.6 Stanley-Brown 6-step Safety Plan

User-authored. On-device. Revisable.

| Step | What user writes |
|---|---|
| 1. Warning signs | "When I notice X, I know my plan should start." |
| 2. Internal coping | "Things I can do alone: TIPP, walk, breathing." |
| 3. Social distractions | "People/places that take my mind off: friend X, café Y." |
| 4. People to ask for help | "Names + numbers of people I can ask." |
| 5. Professionals / agencies | **India hard-coded**: iCall 9152987821, Vandrevala 1860-2662-362, AASRA 9820466726, Tele-MANAS 14416. Tap to dial. |
| 6. Lethal means restriction | "How I can make my environment safer." |

**Trigger philosophy**: passive offer only. Never auto-display from journal sentiment detection. The user opens the Crisis surface, then chooses to view / edit the plan. The Nuij 2020 meta-analysis shows self-guided SP *worse* than clinician-guided CRP at the same use frequency — so we position as bridge-to-clinician, never substitute.

### 4.7 Crisis lines (India-first, 4 lines)

| Line | Number | Hours | Langs |
|---|---|---|---|
| iCall (TISS Mumbai) | 9152987821 | Mon–Sat 8am–10pm | English, Hindi, Marathi, Tamil, + |
| Vandrevala (Mint Tree) | 1860-2662-362 / 1800-2333-330 | 24/7 | 12+ langs |
| AASRA (Samaritans Mumbai) | 9820466726 | 24/7 | English, Hindi |
| Tele-MANAS (Govt of India) | 14416 | 24/7 | 20 langs |

Tap → ACTION_DIAL (not ACTION_CALL). Long-press → toast with name + hours. Persistent footer on Today + Skills; primary block on Crisis.

### 4.8 Mood input

- **Default**: 5 chips (Crushed / Heavy / Steady / Light / Bright) — preserved from v0.65.0.
- **Opt-in (Settings toggle)**: 2D Affect-Grid (valence × arousal) — backed by HDRPS+ 2025 evidence (78% accuracy, 80.3% day-5 retention).
- **No body-map, no emoji-game, no VAS slider** in v0.66.0. Defer to v0.67.0+ per 4-7 voice journaling research.

### 4.9 N-of-1 14-day strip

Preserved from v0.65.0. Surface at the top of Today.

- "you've logged 9 of 14 days" — count, not streak.
- "your mood 14-day direction: stable" — direction bands only.
- "your most-used skill: TIPP (4 times in 14 days)" — name + count, no achievement.
- No "Don't break your streak!" — never.

## 5. Engineering changes

### 5.1 New files

- `app/src/main/java/org/mindanchor/journal/diary/DiaryCardPrefs.kt` — DataStore for diary card entries
- `app/src/main/java/org/mindanchor/journal/diary/DiaryCardEntry.kt` — sealed class with 4 module values
- `app/src/main/java/org/mindanchor/journal/skills/SkillsPrefs.kt` — DataStore for last-used skill + time-of-day patterns
- `app/src/main/java/org/mindanchor/journal/skills/SkillsLibrary.kt` — static metadata for 5 skills
- `app/src/main/java/org/mindanchor/journal/crisis/SafetyPlanPrefs.kt` — DataStore for 6-step plan
- `app/src/main/java/org/mindanchor/journal/crisis/SafetyPlanEntry.kt` — data class for 6 steps
- `app/src/main/java/org/mindanchor/journal/crisis/TherapistExport.kt` — on-device PDF generation
- `app/src/main/java/org/mindanchor/journal/skills/SkillOfTheDay.kt` — time-of-day logic

### 5.2 Refactored files

- `JournalRoot.kt` — add `crisis_dial(tel:)` for Tele-MANAS 14416 + state for diary card + safety plan
- `JournalToday.kt` — single screen refactor (5 components in 1 screen)
- `JournalCrisis.kt` — refactor: panic button + 6-step plan + 4 crisis lines
- `JournalSettings.kt` — add: voice-first toggle, diary card opt-in, "share with therapist" PDF toggle

### 5.3 Deprecated (kept for rollback, not in v0.66.0 nav)

- `JournalMood.kt` — merge into Today
- `JournalArchive.kt` — move "On This Day" into Today
- `JournalQuickNote.kt` — merge !mood / !ground / !breathe into Skills + Today

### 5.4 Build config

- `app/build.gradle.kts` — versionCode 87→88, versionName "0.65.0"→"0.66.0"
- No new dependencies (PDF generation uses Android's PdfDocument API; on-device only)

## 6. Risks (BPD-safety)

| Risk | Evidence | Mitigation |
|---|---|---|
| Standalone diary card harm (Simon 2022 JAMA HR 1.29 self-harm) | peer-reviewed | Bridge-to-therapist framing + skill pairing + "not a substitute" disclosure + India crisis resources always visible |
| DEAR MAN weaponised | BPD interpersonal sensitivity | Skill text: "for *you* to communicate your needs, not to win an argument" |
| Affect-Grid over-reporting on bad days | patient-reported | Opt-in only, default to 3-tile chip |
| N-of-1 pattern over-triggering | BPD shame research | "Pattern, not diagnosis" tooltip; never name a condition |
| PDF share to therapist (privacy) | Kamrass 2026 | Active gesture = consent, default OFF, on-device only |
| Crisis line clutter | UX research | 4 lines, persistent footer only on Today + Skills; primary block on Crisis |

## 7. Out-of-scope reminder (do NOT build)

- Health Connect / HealthKit → v0.67.0
- Voice journaling (full STT) → v0.67.0
- Tamil / Hindi i18n → v0.67.0+ research
- AI / LLM companion → v0.68.0+, never before BPD safety is measured
- Community / peer support → never
- Streak / leaderboard / counter → never
- Auto-triggered crisis escalation → never
- LLM crisis detection → never before validated against BPD-specific dataset

## 8. Ship criteria (measure before claim)

### 8.1 Build

- `gradlew assembleDebug` succeeds in <90s.
- `gradlew test` passes (existing v0.65.0 tests + new DiaryCardPrefs / SafetyPlanPrefs / SkillsPrefs unit tests).
- `gradlew lint` no new errors.

### 8.2 Manual drive-verify (per v0.65.0 pattern)

- **Emulator (1080x2400)**:
  - Today surface: mood 5 chips, journal 60s, skill-of-the-day, diary card 4 modules
  - Skills: 5 cards, each opens, "Done" dismisses
  - Crisis: panic button → 60s S.T.O.P. card; 6-step plan walkthrough; 4 crisis lines visible
  - Settings: voice-first toggle, diary card opt-in, therapist export toggle
- **Real phone (1264x2780, ZD2232FCR5)**:
  - Long-press iCall (915, 1235) → ACTION_DIAL opens with (915) 298-7821
  - Long-press Tele-MANAS → ACTION_DIAL opens with 14416
  - Diary card save + reload
  - Therapist PDF export (3-day range) → file saved to device
- **uiautomator dump** confirms:
  - No streak counter anywhere
  - No leaderboard
  - No "!" affordances
  - Validate-then-suggest copy throughout
  - ACTION_DIAL (not ACTION_CALL) on every crisis tap
  - 14-day N-of-1 strip shows count, not streak
  - Crisis lines visible on Today + Skills + Crisis surfaces

### 8.3 Screenshot evidence

- Today surface (with skill-of-the-day card + diary card expander)
- Skills library (5 cards visible)
- Crisis surface (panic button + 6-step plan + 4 crisis lines)
- Settings (3 new toggles)
- Bridge-to-therapist PDF preview

## 9. Dependencies

- Android Studio Hedgehog or later
- JDK 17
- Gradle 8.14.3 (preserved)
- Kotlin 2.0.21 (preserved)
- No new external dependencies — all native APIs (DataStore, PdfDocument, ACTION_DIAL)

## 10. Open questions

None blocking. Resolved at brainstorming:
- Skills count: 5 (TIPP, DEAR MAN, S.T.O.P., 3-Min Breathing Space, Wise Mind)
- Crisis lines: 4 (iCall, Vandrevala, AASRA, Tele-MANAS 14416)
- Surfaces: 3 (Today, Skills, Crisis)
- DBT diary card: 4 modules (urges, emotions, skill, bridge-to-therapist)

## 11. References (most-cited research files)

- `research/findings/2-1-dbt.md` — DBT mobile fit, Pocket Skills (ER > DT in 5-min mobile slots)
- `research/findings/2-16-safety-planning.md` — Stanley-Brown 6-step, Nuij 2020 meta
- `research/findings/2-17-dbt-diary-card.md` — Simon 2022 JAMA standalone harm signal
- `research/findings/2-19-crisis-text-line.md` — CTL 4-rung risk ladder
- `research/findings/3-2-crisis-surfacing.md` — Apps 72% have no hotline; India numbers converged
- `research/findings/3-3-streaks.md` — Don't ship streaks; Loop's exponential-smoothed
- `research/findings/3-5-onboarding.md` — TTV <10-15 min
- `research/findings/3-18-journal-prompts.md` — Pennebaker + Kross self-distancing
- `research/findings/4-9-bpd-digital.md` — Ilagan 2020 meta; BPD digital evidence base
- `research/findings/5-9-bpd-failures.md` — Vulnerability-amplifying loop, sycophancy
- `research/findings/5-11-liability.md` — Kamrass 2026, Garcia 2025
- `research/findings/5-4-regulation.md` — Lock intended-purpose in 5 places

---

## Approval record

- 2026-08-21: User approved (3 decisions: DBT-grounded Today + Diary Card, 3 surfaces, design-now/ship-next)
- 2026-08-21: User chose 5 skills (not 10) and Tele-MANAS 14416 as 4th crisis line
- 2026-08-21: Spec written to `docs/superpowers/specs/2026-08-21-mindanchor-v066-design.md`
