# MindAnchor Pages (pages.md)

> **Status:** v0.62.7 (2026-08-20) | **Stack:** Kotlin + Compose
> **Approach:** Single-file surfaces. Each surface is a top-level Composable
> in its own file (mostly). Dependencies are usually a few shared Composables
> + the ViewModel + a few M3 primitives.

> **The user asked for a "Full app, 5 key surfaces" redesign:**
> 1. **Home** — clock, mood, notes, quick note composer
> 2. **Notes** — day-grouped notes, swipe actions, search
> 3. **Settings** — grouped sections, sources, this phone, etc.
> 4. **Mood / Check-in** — mood strip + quick mood log + optional reflection
> 5. **Quick note composer** — the 3-chip picker + textarea + save
>
> The Quick note composer is a *part* of Home (a card on it), not a
> standalone surface. For the redesign, the 5 surfaces are: Home, Notes,
> Settings, GroundMe / Panic / Breathing cluster (one "support flow"
> page), and the 2 AM "Now what?" shell (a single page that covers
> the night-time / crisis UX).

---

## 1. Home surface

**Entry:** `HomeScreen.kt` (the entire file, ~300K, but the actual
`Home` surface composable is the top-level `when` branch at
`surface = LauncherSurface.Home`)

**What it renders (in order, top to bottom):**
1. **Top: clock + greeting + date + save-status subtitle** (one Column)
2. **Mood strip** (one Row of 5 emoji + labels + Skip)
3. **Notes section** (header + empty state OR pinned notes)
4. **Quick note composer** (kind picker + pin toggle + time picker + textarea + save)
5. **Drawer handle / FAB** (Material 3 extended FAB → opens Drawer)

**Dependencies (one-level):**
- `CalmBackground` (slow-sky background, from `ui/CalmBackground.kt`)
- `SkyContent` (the design tokens, from `ui/SkyMath.kt`)
- `rememberMinuteTick()` (clock ticker, from `ui/Clock.kt`)
- `rememberSecondTick()` (per-second tick for save-status, from `ui/Clock.kt`)
- `QuickNotesCard` (the composer — see components.md)
- `MoodStrip` (mood row, inline in HomeScreen.kt)
- `NotesTab` (the Notes section, when surface is Notes)
- `SkyColorScheme` (M3 ColorScheme derived from sky)
- `KindTealBg`, `KindTealFg`, `KindIndigoBg`, `KindIndigoFg` (private tokens)
- `LauncherViewModel` (the source of truth for mood / notes / search)
- `SkyMath.kt` (sun position, dark theme boolean)

**Layout features:**
- Single `Column` for the home card content
- Outer `Box` for the `SnackbarHost` (F1 / F2 Undo)
- `Scaffold` for the bottom nav
- `enableEdgeToEdge()` at the Activity level (v0.55.0+)

---

## 2. Notes surface

**Entry:** `HomeScreen.kt` (`surface = LauncherSurface.Notes` branch)
**Could be split out** to its own file in a future refactor — currently
the `NotesTab` composable is defined inline.

**What it renders:**
1. **Top bar:** "Notes" title + add-note icon + overflow menu (sort, export)
2. **NotesDayStrip** (v0.53.0): horizontal row of date filter chips
3. **Search field** (filters notes by content, FTS4-backed)
4. **Day groups** (one per day the user has notes, sticky headers planned)
5. **Note rows** within each day group (swipe-to-pin / swipe-to-delete)
6. **OnThisDay section** (v0.53.0): past years' notes on this MM-DD
7. **Empty state** when no notes: "The first line you write lands here." with
   a small icon and a "Get started" button

**Dependencies:**
- `NotesTab.kt` (or inline; the swipe-to-dismiss pattern is in
  HomeScreen.kt currently)
- `SkyContent` + `SkyColorScheme` (same as Home)
- `LauncherViewModel` (the notes flow + the swipe action enum)
- `SnackbarHost` (for delete/pin Undo)

**Layout features:**
- `LazyColumn` with `items(items, key = { "note_${note.id}" })` (v0.54.0)
- `stickyHeader` planned for v0.55.1+
- Per-row `SwipeToDismissBox` (Material 3 1.3.x)

---

## 3. Settings surface

**Entry:** `SettingsScreen.kt` (the whole file is ~150K, the actual
`Settings` composable is around line 800)

**What it renders (top to bottom):**
1. **Top bar:** "← Back" + "Settings" title
2. **"Set MindAnchor as your home screen" CTA** (only shown if not yet
   the default launcher)
3. **Quiet** (batching, quiet hours, colour) — toggle + time picker
4. **When you want a breath** — 4 toggles (Long messages, Late-night,
   I split on people, A named person, I'm OK at night) + "Try the check"
   CTA with a sub-card explaining the before-you-send interstitial
5. **Sources** — three rows: Heart rhythm (with "Take a reading" CTA),
   Sleep rhythm (with "Grant usage access" CTA), Check-ins (with
   "Ask me how I am" toggle)
6. **Reading** — last night's look, the model behind it
7. **Your plan** — the people and words the user chose
8. **This phone** — home screen, hidden apps, goals, App version,
   data export
9. (About) — links to release notes, the project GitHub, etc.

**Dependencies:**
- `SettingsViewModel` (the source of truth for all settings state)
- `SkyContent` + `SkyColorScheme` (same as Home)
- `OutlinedButton` (for the "Take a reading" and "Grant usage access" CTAs — v0.62.6 change from TextButton)
- `SettingsCard` (a single Composable that wraps a label + sub-label + action)
- `Phi4ModelDownloadSection.kt` (the on-device LLM section, for the optional AI features)
- `PolarSection.kt`, `GoogleDriveBackupSettingsSection.kt`, `SmartwatchesSection.kt` (the integrations)

**Layout features:**
- Single `LazyColumn` with all sections in order
- Each section: title + subtitle + 1-3 cards
- v0.62.6 F4 fix: OutlinedButton (with teal pill border) for
  secondary CTAs, not TextButton (which made them invisible)

---

## 4. Mood / Check-in surface (cluster)

**Entry:** Inline in `HomeScreen.kt` (mood strip) + the "Open report" /
"See past" buttons on the home card. There is NO standalone mood
surface — mood is part of Home.

**What it renders:**
- The mood strip (5 emoji + labels + Skip) — the daily check-in trigger
- A long-press on a mood opens the reflection annotation dialog
  (v0.58.0, "How was that? (Optional)")
- The "See past" / "Open report" buttons link to:
  - `CheckInHistory` surface (all past moods)
  - `Report` surface (daily / weekly check-in report)

**For the redesign:** treat "mood + check-in" as a single surface
showing the daily check-in flow, with the mood strip as the entry
point, the reflection dialog as a follow-up, and a "past week" view
below the strip.

**Dependencies:**
- The mood strip composable (inline in HomeScreen.kt)
- `LauncherViewModel` (the mood log state)
- `Material3` `AlertDialog` (for the reflection dialog)
- `SkyContent` + `SkyColorScheme`

---

## 5. Quick note composer

**Entry:** The bottom of the home card. The composer is a `Card` within
the home `Column`, NOT a standalone surface.

**What it renders (top to bottom):**
1. **Kind picker** (3 FilterChips)
2. **Pin-to-home toggle** (Switch, with state-dependent subtitle)
3. **Time picker** (FlowRow of chips, only for Task / Reminder)
4. **OutlinedTextField** (3-5 line input)
5. **Save button** (OutlinedButton, hidden when no valid input)

**For the redesign:** redesign this as a STANDALONE surface / full-page
composable (not just a card on Home). The user types a quick note, picks
the kind, optionally a time, optionally pins, and taps Save. After save,
returns to wherever the user came from (Home, Notes, etc.).

**Dependencies:**
- `QuickNotesCard` (the existing composable — see components.md)
- `LauncherViewModel` (the `addQuickNote` / `addTaskNote` / `addReminderNote` callbacks)
- The `onAddQuickNote` lambda at line 1117 in HomeScreen.kt handles
  the `!bang` routing for v0.62.7
- `SkyContent` + `SkyColorScheme`

---

## 6. Bonus: NowWhatShell (2 AM shell)

**Entry:** Long-press on the home card OR a bang / drawer action.
**File:** `app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt`

**What it renders:**
- "It's late. What do you need right now?" — large, soft
- Three OutlinedButtons:
  1. "I want to ground" → GroundMe
  2. "I want to talk to someone" → named-person picker (from
     Settings → Your plan)
  3. "I want to sleep" → BreathingScreen with a slower cadence
- Subtle pulse animation on the buttons (v0.40.0 "flash-pulse")

**For the redesign:** this is a CRITICAL surface for the night-time /
crisis UX. It's worth a separate design pass — even though the user
didn't explicitly list it, the user's "redesign from scratch" brief
should consider whether this surface is approachable at 2 AM.

---

## Summary — the 5 main surfaces to redesign

| # | Surface | Source file | One-liner |
|---|---------|-------------|-----------|
| 1 | Home | `HomeScreen.kt` (Home branch) | The launcher — clock, mood, notes, composer |
| 2 | Notes | `HomeScreen.kt` (Notes branch) | Day-grouped notes, swipe actions |
| 3 | Settings | `SettingsScreen.kt` | All app configuration |
| 4 | Mood / Check-in | `HomeScreen.kt` (mood strip + report) | Daily check-in flow |
| 5 | Quick note composer | `HomeScreen.kt` (QuickNotesCard) | The composer card on Home |

Plus the bonus "NowWhatShell" 2 AM shell if the design pass has budget.
