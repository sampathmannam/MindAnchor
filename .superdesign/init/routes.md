# MindAnchor Routes (routes.md)

> **Status:** v0.62.7 (2026-08-20) | **Stack:** Kotlin + Compose | **Routing:** in-app `when (surface)` enum, NOT Compose Navigation graph

---

## The mental model — there is no "home screen" like other apps

MindAnchor is the user's **launcher** (replaces the Android home screen).
"Opening an app" means tapping an app card on the home card. The home card
also shows mood, notes, and a quick-note composer. **There is no bottom tab bar
for in-app navigation** — surfaces are reached via the Drawer, bang commands
(`!ground`, `!panic`, `!breathe`, `!mood`, `!note`, `!task`, `!settings`),
or direct deep links from a Quick note save.

## Surface enum

`private enum class LauncherSurface` in `HomeScreen.kt:586`. One source of
truth for every full-screen surface the launcher can render.

| Surface | What it shows | Reached from |
|---------|---------------|--------------|
| `Home` | Default landing — clock, mood strip, notes, Quick note composer, recent pinned notes, drawer handle | App launch, back from any sub-surface, `!mood`, `!task` |
| `Notes` | Full Notes tab with day grouping, swipe-to-pin / swipe-to-delete, search, date filter chips, `OnThisDay` | `!note` bang, "Open all" on home, drawer |
| `Settings` | Grouped sections (Set as home, Quiet, When you want a breath, Sources, Reading, Your plan, This phone) | `!settings` bang, drawer, bottom nav |
| `GroundMe` | "Three things you can do in the next two minutes" picker (Breathe, Hold something cold, Name what is around you) | `!ground` bang, Drawer → Ground me, "Ground me" button on home |
| `Panic` | `DistressThermometerScreen` — "How is it right now?" slider 0-10, "Noticeable" → "Severe", reflection prompt, Done | `!panic` bang, Drawer → I might do something I regret, home long-press |
| `Breathing` | `BreathingScreen` — soft 4-7-8 inhale/hold/exhale cycle with phase transitions | `!breathe` bang, Drawer → Breathe, Ground me → Breathe slowly |
| `BeforeYouSend` | Interstitial "before you send" demo | Drawer → I might do something I regret → "I want to send anyway" |
| `Report` | Daily / weekly check-in report | Home card → "Open report" |
| `CheckInHistory` | All past check-ins | Home → "See past" |
| `Letter` | Letter to the part of you that is struggling | Drawer → Letter to part |
| `GetThrough` | Sub-menu: 3 reflective actions for hard moments | Drawer → Get through this |
| `DiaryCard` | Single screen — diary card | Drawer → Diary card |
| `Interpersonal` | Single screen — interpersonal effectiveness | Drawer |
| `OppositeAction` | Single screen — opposite action DBT skill | Drawer |
| `RadicalAcceptance` | Single screen — radical acceptance | Drawer |
| `SelfCompassion` | Single screen — self-compassion break | Drawer |
| `Values` | Single screen — values clarification | Drawer |
| `Receipts` | "Receipts" — small wins the user has noticed | Drawer |
| `Accepts` | Single screen — acceptance | Drawer |
| `NowWhat` | 2 AM shell — "I want to [ground / talk to someone / sleep]" | Long-press home / drawer |
| `Support` | Single screen — support menu | Drawer |
| `Picker` | App picker (long-press an app card) | Long-press app card |

> 25 surfaces. 5 are user-facing daily (Home, Notes, Settings, GetThrough,
> Letter). The rest are clinical / reflective DBT-style skills reached via the
> Drawer or bangs.

## Bang command surface map

The same bang works from TWO entry points: the search bar (top-of-home) and
the Quick note composer (post-v0.62.7). Both dispatch through the same
surface table.

| Bang | Surface |
|------|---------|
| `!ground` | GroundMe |
| `!panic` | Panic (DistressThermometerScreen) |
| `!breathe` | Breathing |
| `!note` | Notes |
| `!task` | Home (task picker) |
| `!settings` | Settings |
| `!mood` | Home (mood card) |

The Quick note composer strips the bang and routes; the search bar passes
through a `BangCommand` enum and `LaunchedEffect(bang)`. The two entry points
share the same surface table to stay in sync.

## Back navigation
- Hardware BACK from any sub-surface returns to `Home` (single source of truth)
- Drawer is the only way to reach any surface other than Home, Notes, Settings
  (drawer is also reachable from the bottom-nav "search" button which is more
  of a search bar than a nav).
- No deep links are user-discoverable, but the code supports `bsa://` URIs
  internally.
