# MindAnchor v0.56.0+ end-to-end test fixtures

Six self-contained Kotlin fixtures for rigorous UI testing of the
launcher. Each fixture is a Kotlin `object` with `@JvmStatic` methods
that the test harness can import and write into the app's real
DataStores and Room database. The `NOW_IST` anchor is
**2026-08-19 14:30 IST** (1,787,130,000,000 ms) — every timestamp
in every fixture is an offset from this anchor so the fixtures stay
stable across real-clock advances.

## Schema assumptions (read this first)

The fixtures use the real MindAnchor data classes from the main
source set, plus two *assumed* shapes (`UserProfile`, `AppEvent`)
that the main source set does not have. See
`FixturesSchema.kt` for the full mapping.

| Concept | Real class | Path |
| --- | --- | --- |
| Note | `org.mindanchor.model.Note` | `app/src/main/java/org/mindanchor/model/Note.kt` |
| Note kind | `org.mindanchor.model.NoteType` | `…/model/NoteType.kt` (GENERAL / TASK / REMINDER / JOURNAL) |
| Mood log | `org.mindanchor.model.CheckIn` | `…/model/CheckIn.kt` (rating 1–5, optional reflection) |
| Wellness | `org.mindanchor.vitals.WellnessLedger.Entry` | `…/vitals/WellnessHistoryStore.kt` |
| Signal | `org.mindanchor.vitals.WellnessSignal` | `…/vitals/WellnessSignals.kt` (HRV, RHR, STEPS, SLEEP_MINUTES, MINDFULNESS_MINUTES) |
| BPD profile | `org.mindanchor.data.BpdProfile` | `…/data/BpdProfile.kt` |
| User profile | **assumed** `UserProfile` | `fixtures/FixturesSchema.kt` |
| App watch event | **assumed** `AppEvent` | `fixtures/FixturesSchema.kt` |

There is **no** `NoteEntity` in MindAnchor — the user prompt's
"NoteEntity with kind=MOOD" was a misread. The actual mood signal
is `CheckIn` (rating 1–5); the launcher does not store mood in
notes and does not interpret it. There are also no `EXERCISE`,
`CALORIES`, or `HR` wellness signals — only the five in
`WellnessSignal.ORDERED`.

## Routing settings into the right DataStore

The settings map is flat, but MindAnchor has many DataStores. The
`FixturesSchema.KEY_TO_DATASTORE` map routes each key. The
`FrictionPrefs`, `AppearancePrefs`, `LauncherPrefs`, `BpdProfilePrefs`,
`SetupPrefs` etc. each own a `preferencesDataStore(name = "…")`
and the harness must write to the right one. The keys used here are
the actual preference-key constants those classes use.

## The six fixtures

### 1. `FixtureSparseUser` — 3 notes, 0 mood, no data sources

The "just-installed, used once" shape.

| | count |
| --- | --- |
| Notes | 3 (1 pinned) |
| Check-ins | 0 |
| Wellness entries | 0 |
| App events | 0 |
| Date range | 22h ago → 90 min ago |
| Pinned ratio | 1 / 3 (33%) |
| Mood distribution | n/a |
| Quirk | wizard not complete, `one_thing` = "Add a data source" |

**Test scenarios**

- Open home → home card shows the "one thing" CTA but no pinned
  notes, no recent notes row, no wellness card content.
- Open Notes tab → 3 rows, newest first; the pinned reminder is at
  the top of the pinned section (1 pinned, 2 unpinned).
- Open Settings → "Connected sources" shows 4 ✗; "BPD profile" is
  off; wizard shows step 1 of onboarding if `welcome_seen` is
  false.
- Long-press the pinned note → unpin → it moves down; the home
  card's pinned section disappears.
- Open Wellness → "Still building a picture" copy on every signal;
  no number rendered.

### 2. `FixtureHeavyUser` — 200 notes, 60 days, full wellness

The "real user, two months in" shape.

| | count |
| --- | --- |
| Notes | 200 (≈ 18 pinned) |
| Check-ins | 60 (one/day) |
| Wellness entries | 300 (60 days × 5 signals) |
| App events | 5 |
| Date range | 60 days ago → today |
| Pinned ratio | ≈ 9% (concentrated on the last 7 days) |
| Mood distribution | 3–4 typical, spike 1 on day 32, spike 5 on day 18, spike 2 on day 41 |
| Quirk | Sleep-led wellness correlation so the baseline is meaningful |

**Test scenarios**

- Open Notes → "All" pill = 200. "Today" pill ≈ 5. "Yesterday"
  pill ≈ 4. Tap "Pinned" → ≈ 18. Pinned section is *not* collapsed
  (under the cap).
- Pin a new note (long-press) → it appears at the top of the
  pinned section; total pinned count increments by 1.
- Open Wellness → all five signals render with a baseline number
  (60 days >> 14-day floor); HRV z-score may show a brief dip
  around day 32 (the sleep dip).
- Open Settings → "Connected sources" shows Health Connect ✓,
  Polar ✓, Coros ✗, PPG ✗. BPD profile off.
- Add a new note via the home card → it appears at the top of
  "Today" pill; type chip shows "GENERAL".
- Mark a TASK as done → row renders with strikethrough; it
  disappears from any "Pending" filter.

### 3. `FixtureBpdProfileUser` — BPD profile, 30 notes, 15 mood logs, rapid swings

The "BPD-acknowledged, hasn't filled the safety plan" shape.

| | count |
| --- | --- |
| Notes | 30 (1 pinned — the therapy appointment) |
| Check-ins | 15 (with rapid 1↔5 swings) |
| Wellness entries | 70 (14 days × 5 signals — at the baseline floor) |
| App events | 2 (one blocked by the late-night gate) |
| Date range | 7 days ago → today |
| Pinned ratio | 1 / 30 (3%) |
| Mood distribution | 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5 |
| Quirk | journals dominate; the long-message / splitting / late-night language is on purpose |

**Test scenarios**

- Open Settings → BPD profile card shows all five flags as
  "checked"; the BPD-aware copy is visible ("you can switch any of
  these off again at any time").
- Open Check-in history → the rating chart shows the zig-zag
  (e.g. day -1: 4, 2, 1; day -2: 3, 5, 2). The visual must render
  every rating, not collapse adjacent days.
- Open Diary card → the BPD-aware prompt variant renders; safety
  plan section is visible but empty (the user has acknowledged BPD
  but not filled the plan yet).
- Open Notes → journals dominate; one long JOURNAL body is on
  the page (the "long message I sent" note); the single pin is the
  therapy appointment reminder.
- Wellness → 14 days = at the floor; surface should show the
  baseline number for at least HRV / RHR / STEPS / SLEEP_MINUTES
  (the four signals with non-zero variance), and a dash for
  MINDFULNESS_MINUTES where the variance is too tight.

### 4. `FixturePowerUser` — 500 notes, 90 days, all 4 sources

The "I have used this for a quarter" stress shape.

| | count |
| --- | --- |
| Notes | 500 (≈ 45 pinned) |
| Check-ins | 90 (one/day) |
| Wellness entries | 450 (90 days × 5 signals) |
| App events | 9 |
| Date range | 90 days ago → today |
| Pinned ratio | ≈ 9% |
| Mood distribution | 2 dips on day-12, 5 spikes on day-25 |
| Quirk | pinned count hits the cap; all four sources connected; friction + going-light on |

**Test scenarios**

- Open Notes → 500 rows; "All" pill shows 500; the LazyColumn
  scrolls without jank. Pinned section *hits* the cap and
  truncates the older pins (the test should see the "show all
  pinned" overflow affordance if the launcher has one).
- Open Wellness → 90 days is the deepest baseline the launcher
  can use; HRV / RHR / SLEEP_MINUTES / STEPS all render with
  numbers. MINDFULNESS_MINUTES has only 60% hit-rate so the
  variance is wide; baseline number is reported.
- Open Settings → all four sources ✓ (Health Connect, Polar, Coros,
  PPG). Wizard complete. Friction enabled. Going-light enabled.
- Open Friction / Going-light → schedule is visible; blocked app
  list contains 2–3 packages; the late-night gate blocked one
  WhatsApp launch (the "1 attempt blocked" counter is +1).
- Add a new note → it appears at the top of "Today"; list view
  scrolls smoothly back to the top after the new row is inserted.
- Check-in prompt rate-limit → the user has accepted 90 check-ins
  over 90 days, so today is the 90th day; the rate-limit state
  should be "fresh today" (zero accepted yet today, no
  auto-pause).

### 5. `FixtureEmptyState` — nothing

The "literally just installed, never opened" shape.

| | count |
| --- | --- |
| Notes | 0 |
| Check-ins | 0 |
| Wellness entries | 0 |
| App events | 0 |
| Date range | n/a |
| Pinned ratio | n/a |
| Mood distribution | n/a |
| Quirk | wizard not complete, `welcome_seen` false, profile empty |

**Test scenarios**

- First launch → onboarding wizard step 1 is the visible screen
  (welcome_seen = false). The wizard stays until the user advances
  it.
- Bypass the wizard in the test (set welcome_seen = true at the
  start of the test) → home card shows "Add a data source to
  start the wellness surface" with no other content.
- Open Notes tab → "No notes yet" empty state.
- Open Wellness → "Still building a picture" on every signal.
- Open Settings → defaults; no sources connected; no goals set.
- Tap the "one thing" CTA → should advance the wizard or open
  the source setup, depending on the version.

### 6. `FixtureTimeBoundTest` — 30 notes in 7 days, day-boundary edge case

The "concentrated week" shape with deliberate midnight-boundary
edge cases.

| | count |
| --- | --- |
| Notes | 30 (5 pinned) |
| Check-ins | 7 (one/day) |
| Wellness entries | 14 (7 days × 2 signals — under the baseline floor) |
| App events | 2 |
| Date range | 6 days ago → today |
| Pinned ratio | 5 / 30 (17%) |
| Mood distribution | 1, 2, 3, 3, 4, 4, 5 |
| Quirk | yesterday 23:30 + today 00:30 are 60 min apart and must be in different day groups |

**Test scenarios**

- Open Notes → "All" = 30. "Today" = 9. "Yesterday" = 6. "Earlier"
  = 15.
- Scroll to the top of the list → the newest note is the "Read 20
  pages before bed." GENERAL at today 22:10 IST.
- Scroll down → after the 9 today-rows, the sticky day header
  changes to "Yesterday". The first row in yesterday is the
  "long anxious evening" JOURNAL at yesterday 23:30 IST.
- The first row of "Today" (00:30 IST) and the last row of
  "Yesterday" (23:30 IST) are 60 minutes apart in wall clock and
  MUST be in different day groups. A test that bucketing by
  UTC instead of IST will collapse them into one group; the test
  asserts they are in different groups.
- Open the row "woke up, could not get back to sleep…" (today
  00:30 IST) → it renders as a JOURNAL chip with the "earlier
  today" header; tapping it opens the note detail with the full
  body and the updated-at timestamp.
- Open "Earlier" pill → 15 rows across 5 distinct day headers
  (2d, 3d, 4d, 5d, 6d), each with the right count.
- Wellness → 7 days, only 2 signals populated (SLEEP_MINUTES +
  STEPS) — surface should say "still building a picture" because
  the floor is 14 days.

## How the harness writes a fixture

The `TestHarness` (the orchestrator that the v0.56.0 testing pass
adds) reads each fixture and:

1. For each `(key, value)` in `settings()`, looks up the target
   DataStore in `FixturesSchema.KEY_TO_DATASTORE` and calls
   `dataStore.edit { it[key] = value }`.
2. For each `Note` in `notes()`, calls `NotesPrefs.add(note)` —
   or, for the larger fixtures, writes the whole list in one go
   via the sealed codec so the ID generator seeds correctly.
3. For each `CheckIn` in `checkIns()`, calls
   `CheckInPrefs.add(checkIn)`.
4. For each `WellnessLedger.Entry` in `wellness()`, writes to the
   `wellness` DataStore directly using `WellnessLedger.encode`.
5. For each `AppEvent` in `appEvents()`, writes to the friction
   per-app session-length ledger (or the planned event log; if the
   event log doesn't exist yet, the harness records them in a
   test-only DataStore the launcher's debug build reads from).
6. For `BpdProfile` users, calls
   `BpdProfilePrefs.update(bpdProfile())`.
7. Sets `UserProfile` into the test-only profile DataStore; the
   launcher reads this in debug builds only.

The fixtures are deterministic — every `Random` call uses a fixed
seed — so the same fixture produces the same data on every run.
