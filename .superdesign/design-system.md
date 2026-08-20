# MindAnchor — BPD-First Journal Design System (v0.64.0)

> **Status:** v0.64.0 (2026-08-20) | **Direction:** BPD-First Journal
> **Brief:** A BPD-safe mental-health journal. No counters, no streaks, no urgency, no time pressure, no "fix" language. Validates first, then offers. Always-optional. Exit-anywhere. Crisis resources visible, not buried.
> **Stack:** Kotlin + Jetpack Compose + Material 3
> **Tagline:** "A quiet place to put what you're carrying."

---

## What stays the same (the voice)

- **Calm, slow, warm.** The journal aesthetic doesn't change at the texture level — paper card, hairline border, slow sky background, Crimson Pro serif body, terracotta accent.
- **Mental health first, but not clinical.** The app does not promise to fix anything. It holds what you bring to it.
- **The slow-sky gradient background** is the signature. KEEP IT — every MindAnchor surface has it.
- **Crisis vocabulary stays grounded:** "Crushed / Heavy / Steady / Light / Bright" rather than "Sad / OK / Happy".
- **No material-icons-extended** (project rule). Custom-drawn icons via Canvas.

## What CHANGES for v0.64.0 (BPD-first)

### 1. REMOVE all counters
- **No entry number** (was "Entry No. 412") — entry number is a counter, can shame.
- **No "Saved Xs ago"** ticking badge — ticking time = pressure.
- **No "X days in a row" / streaks** — streak-loss is a known BPD trigger (Linehan 1993 DBT crisis-survival skill #5: "IMPROVE the moment" — opposite of streak mechanics).
- **No "X notes" count** in Archive.
- **No date headers** like "This week / This month" — these implicitly rank.
- The entry number was a journal ritual, but a counter is a counter. Replace with: nothing. Or: the date alone.

### 2. REMOVE all time pressure
- **No clock on the home surface** (was implicit in v0.62.x).
- **No "today is almost over"**.
- **No "you haven't written in 3 days"** — never tell the user how long they've been away.
- **No notification urgency** (was already off, but reinforce).

### 3. Validate-then-suggest (DBT "DEAR MAN" + Linehan 1993)
- When the user opens a note, validate first. "Thanks for writing that." Then offer. "Anything else?" never "You should write more."
- Mood chip on selection: validate the choice, not rank it. "Steady it is. Not every day has to be Bright."
- Empty state language: "Nothing here yet. Or not. Either is fine."

### 4. One thing at a time (cognitive load = BPD destabilizer)
- Today shows **at most** one prompt + one input + one entry. No kind picker, no mood row, no task list, no reminder chips stacked.
- Mood screen: 5 states, no history view, no "mood graph". History is hidden behind a "Want to see your past?" toggle (off by default).
- Settings: 4-6 rows max visible. No nested settings pages.

### 5. Exit-anywhere
- Every screen has an obvious way out. Back gesture always works. No "are you sure?" walls. No trapped states.
- Long-press to delete (with Undo snackbar) — no confirmation dialog.
- Auto-save on blur — no "Save" button on Quick note. Closing is saving.

### 6. Always-optional
- Every action can be skipped. "Or don't" is baked in.
- Skip on mood screen is one of the 5 states, visually equal.
- The empty state is a valid state, not a failure.

### 7. Crisis resources VISIBLE, not buried
- A persistent small line on Today: "Need to talk? iCall 9152987821 · Vandrevala 1860-2662-362 · AASRA 9820466726"
- Settings has a "If you're in crisis" section at the top, NOT the bottom.
- Crisis line text is the same size as body text. Not a banner. Not a popup. Just there.

### 8. Soft language (no "!" anywhere)
- Drop the bang command hint character "!" — it's pressure, even small.
- Bang commands still work (typed in Quick note), but no discoverable "!" UI affordance.
- Footer icons unbranded, unlabelled — just the icon.

### 9. Slow, gentler motion
- Reduce animation durations to 800-1000ms (was 300-500ms).
- No bounce, no spring. Linear or ease-in-out only.
- No "shake" or "bounce" feedback on errors — silent failure is better.

### 10. No "fix" promise
- Tagline: "A quiet place to put what you're carrying." not "Journal your way to better mental health."
- No "you're doing great!" anywhere.
- No progress bars, no "level up", no streaks.

## Design tokens (current baseline — same as v0.63.0)

### Colors
- **Time-of-day sky (signature, do not remove):**
  - Dawn: `#F5D5BD` → `#E6C4A7` (warm peach)
  - Day: `#9DBCC9` → `#DCE0DF` (cool blue-grey)
  - Dusk: `#8AA1C0` → `#3B5278` (deep blue)
  - Night: `#1B2845` → `#0F1830` (almost black)
- **Accents:** teal-700 `#0F766E`, teal-200 `#B2DFD8`, teal-800 `#115E59`
- **Paper card:** `#F8F5F0` + hairline `#DCD7CC`
- **Terracotta accent:** `#8B5A44` (replaces "ExclamationAccent")
- **Errors:** deep rust `#8B4A4A` (NOT bright red)
- **Crisis line color:** same as `textSecondary` (visually equal, not a banner)

### Typography
- **Fonts:** Crimson Pro (serif body), Plus Jakarta Sans (sans labels), via Google Fonts provider
- **Material 3 type scale** with restraint — body and labels dominate, no display-size headlines
- **Quiet hierarchy**: more whitespace, fewer weights, single column

### Spacing & shape
- 8dp baseline grid (more generous padding — min 24dp on entry cards)
- Corners: small 4dp, medium 8dp, large 16dp
- Touch targets: minimum 48dp

### Iconography
- No material-icons-extended imports. Custom-drawn icons via Canvas.
- 12 hand-drawn icons at 1.5dp stroke
- **No labelled icons in the footer** — just the icon glyph, no text

### Motion
- No third-party animation libs
- **Animations 800-1000ms** (slower than v0.63.0's 500ms cap)
- Easing: `LinearEasing` or `FastOutSlowInEasing` only
- No bounce, no spring, no shake
- **No error shake** — silent failure preferred

## The 5 surfaces for v0.64.0

### 1. Today (the journal home)
- Empty by default. If a note exists, show it centered, large text, generous padding.
- Below the note (if present): small acknowledgement "Thanks for writing that." NOT a counter.
- One input below: "Anything else?" (or empty if no note). No "Continue writing..." which is pressuring.
- No kind picker. No mood row. No task list. No reminder chips. **One thing.**
- Persistent crisis line in footer: "Need to talk? iCall 9152987821 · Vandrevala 1860-2662-362 · AASRA 9820466726"
- 3-icon footer: search · archive · settings. Icons unlabelled.

### 2. Archive
- Plain list of entries. No count. No date grouping. No "This week".
- Each entry: text only, single line of date+time above.
- Tap to expand. Long-press to delete (with Undo snackbar, no confirmation).
- Empty: "Nothing here yet. Or not."

### 3. Settings
- "Pause all" toggle at top (off by default but discoverable)
- "If you're in crisis" section: iCall 9152987821, Vandrevala 1860-2662-362, AASRA 9820466726 — tappable, no banner
- "Quiet hours" pre-set 22:00-08:00, user can change
- "Sources" (Health Connect, watch, etc.) — collapsed by default
- "About" (version, build)
- No "personalize", "goals", "achievements" sections

### 4. Mood
- 5 named states, presented as a single row. Skip is one of the 5.
- No "track your mood" framing. Just "If you'd like to name it, here's where."
- No history view by default. "Want to see your past moods? [Yes / No]" — off.
- On selection: "Steady it is. Not every day has to be Bright." (validate)
- Mood tints are very pastel — gentle visual, not loud.

### 5. Quick note
- Single line input. "What's here when you want it."
- No kind picker. No "Save" button. Auto-saves on blur.
- No "Pinned" affordance.
- Empty: empty.

## What to AVOID in drafts

- **Anything that looks like a productivity app** (clock + weather + todo + calendar in a grid)
- **Streak counters, points, levels, badges**
- **"Saved Xs ago" / "X days" / "Entry No. N"** counters
- **"!"** characters as UI affordance
- **Multiple simultaneous asks** on a single screen
- **Bright saturated colors** as accents
- **Emoji as the primary affordance** (use sparingly; consider text-only)
- **Multiple bottom tabs** (3 max, unlabelled)
- **"Get started!" / "Sign up!" CTAs**
- **Any red / red-orange / red-pink** as an alert color (use the deep rust `#8B4A4A`)
- **Date headers** like "This week" or "Recent" or "Older" — implicit ranking
- **Animated counters or ticking numbers** anywhere
- **"Are you sure?"** confirmation dialogs
- **"You're doing great!" / "Keep it up!"** gamification language

## What to INCLUDE in drafts

- Slow-sky background on every surface
- Paper card surface (`#F8F5F0` + `#DCD7CC` hairline)
- Crimson Pro serif body, Plus Jakarta Sans labels
- Generous whitespace (min 24dp padding on entry cards)
- A way to record a note (single, optional, auto-save)
- A way to see past entries (list, no counts)
- A way to name a mood (5 states, skip = 5th option, no history)
- A way to access Settings (3-icon footer)
- Crisis resources visible: iCall 9152987821, Vandrevala 1860-2662-362, AASRA 9820466726
- Snackbar Undo for destructive actions
- Bang commands still work (`!ground`, `!breathe`, etc.) but not advertised in UI

## Tone for the redesign drafts

When generating, describe each draft as:
- "A BPD-safe mental-health journal — not a productivity app, not a self-improvement tool"
- "Calm, slow, warm — like the existing MindAnchor, but more restrained"
- "The signature is the slow-sky gradient — every surface sits on it"
- "No counters, no streaks, no time pressure, no 'fix' promise"
- "Typography is the primary tool — not color, not iconography, not animation"
- "Crisis resources are equal citizens, not banners"

## Research backing

BPD-safe design principles are grounded in:
- **Linehan 1993** — DBT (Dialectical Behavior Therapy) skills: "DEAR MAN" (interpersonal), "IMPROVE the moment" (crisis survival), "PLEASE" (emotion regulation). All emphasise validate-then-act, no judgment, optional steps.
- **Schwartz 1995** — Internal Family Systems: no part is bad, all parts welcome. (Empty state as "all parts welcome to come and go")
- **BPD-affective instability research** (Koenigsberg 2002) — reduce emotional triggers; use neutral language; avoid rapid state changes.
- **BPD-interpersonal sensitivity research** (Lazarus 2014) — avoid social comparison; the app is one-on-one, never social.
- **BPD-impulsivity research** (Links 1999) — don't encourage sudden actions; let the user slow down; one decision at a time.
- **Crisis-line numbers** (verified): iCall 9152987821 (TISS), Vandrevala 1860-2662-362 (24/7 multilingual), AASRA 9820466726 (24/7).
