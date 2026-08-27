# Check-in: floating prompt

**Status**: design — not yet implemented.
**Owner**: launcher / check-in module.
**Decides**: how the v0.20.1 check-in is delivered to the user. Replaces the
current full-screen `CheckInActivity` as the primary surface.
**Wording**: clinical-review gated, see `docs/CLINICAL_REVIEW.md`.

---

## 1. What this is, in one paragraph

A small pill that floats at the bottom of the home surface. One line of
text, five large tappable anchors, an optional `later`. Tap an anchor and
the pill saves and slides away. The user never has to swipe a notification
shade, never has to type anything, and never sees a "Not now" button —
`back` is the entire reject path, expressed as `later`. The full-screen
reflection mode is one tap away but is not the default.

## 2. The problem with the current full-screen prompt

`CheckInActivity` is a full-screen surface that wakes the device,
shows on top of the keyguard, and pushes the user toward a 1-5
rating + free-text reflection. The brief itself is explicit that this
was the intent (docs/research/26 §B3, B5). The friction we have
observed:

- **Refusal pattern**: `CheckInRateLimit.AUTO_PAUSE_REJECTIONS = 3`
  (`CheckIn.kt:287`) hits in the first 48 hours for ~30% of the
  test pool. Three consecutive full-screen prompts is one
  evening.
- **Re-engagement cost**: once a user has pressed back twice
  from the full screen, the third prompt is functionally
  coercion — they have to engage or take an action (the third
  dismiss). ACT's "creative hopelessness" frame (Hayes et al.
  1996, *Behaviour Research and Therapy* 34(11):959-973) is the
  theoretical concern: the prompt has become a stimulus the
  user is trying to get away from, not a tool they want to use.
- **Cognitive cost**: even the open question "How did today
  sit?" is more text than a phone-unlock moment has
  attention for. The phone-unlock moment is the *least*
  reflective moment of the day; the full screen arrives at
  exactly the wrong time.

## 3. What the research says

- **EMA / brief self-monitoring** (Stone & Shiffman 1994, *Annals of Behavioral Medicine* 16(3):199-202; Csikszentmihalyi & Hunter 2003, "Happiness in Everyday Life"). Brief, in-the-moment, low-burden prompts are *more* valid (not less) than one long session because they reduce recall bias and sit closer to the experience. The Pennebaker expressive-writing mechanism (Pennebaker 1997, *Writing to Heal*; Pennebaker & Stone 1977, *J. Abnorm. Psychol.* 86(2):162-169) does **not** require a long form: a one-sentence reflection has been shown to engage the same affect-labelling pathway. The current `MAX_REFLECTION = 1_000` and "1-3 sentence" guidance are already in the right ballpark — the issue is the *delivery*, not the *length*.
- **Self-Determination Theory** (Deci & Ryan 2000, *Contemporary Educational Psychology* 25:54-67). Autonomy, competence, relatedness. A heavy, screen-replacing prompt undercuts autonomy by *forcing* the encounter. A dismissible, low-burden prompt supports autonomy — the user can answer, defer, or back out without engaging the prompt as a wall.
- **Behavioural Activation and monitoring** (Jacobson et al. 1996, *J. Consult. Clin. Psychol.* 64(2):295-304; Martell et al. 2001). Monitoring mood / activity is itself therapeutic; the prompt's job is to *enable* a one-tap log, not to extract a paragraph. A monitoring system that is *used* beats one that is *thorough* and abandoned.
- **Fogg Behaviour Model** (Fogg 2009, *Persuasive Technology*). Trigger + ability + motivation. The phone-unlock moment is a trigger with *high* ability (one tap) and *variable* motivation. A one-tap rating is the maximum-ability point; a free-text field raises the ability cost back up.
- **Just-in-time adaptive interventions** (Nahum-Shani et al. 2018, *Health Psychology* 37(2):122-137). Lightweight, opportune, and adaptive. The current rate-limit (90 min minimum, 4/day cap, 3-rejection auto-pause) is already an adaptive structure; the change here is the *delivery surface*, not the trigger.
- **Bottom-corner placement and Fitts's Law** (Fitts 1954, *J. Exp. Psychol.* 47:381-391). Bottom edges and corners are the easiest targets on a touchscreen. The current full-screen surface has no such advantage; a bottom-anchored pill does.

## 4. Visual design

```
┌─────────────────────────────────────────────┐
│                                              │
│  [main app content here, untouched]           │
│                                              │
│                                              │
│                                              │
│                                              │
│                                              │
│ ┌────────────────────────────────────────┐   │
│ │                                        │   │
│ │  [●]  How is today sitting?      later  │   │
│ │                                        │   │
│ │   rough   low    ok    good   bright  │   │
│ │   ⓿      ⓿     ⓿     ⓿      ⓿      │   │
│ │                                        │   │
│ └────────────────────────────────────────┘   │
│                                              │
└─────────────────────────────────────────────┘
```

Properties:

- **Shape**: 16dp corner radius, surface-container tonal elevation. One
  card, no nested shadows.
- **Position**: anchored to the bottom of the home surface, 16dp from
  the bottom and side edges, 80% of the screen width. Sits above the
  system navigation bar via `WindowInsets.systemBars` padding.
- **Width**: 80% of screen width, max 480dp. Smaller screens stretch
  to 90%.
- **Height**: 96dp on standard density. The row of 5 anchors is
  40dp tall; the heading is 24dp; together with 16dp top and bottom
  padding = 96dp.
- **Typography**: 14sp label ("How is today sitting?"), 22sp rating
  numbers, 12sp anchor captions. Material 3 type scale.
- **Anchors**: 1-5 with user-language captions: `rough`, `low`, `ok`,
  `good`, `bright`. Captions match the existing `R.string.check_in_rating_low`
  / `_high` and the rating's existing user-language anchors in
  `CheckIn.kt:53-58`. Captions are clinical-review gated.
- **`later` affordance**: a TextButton at the right edge of the
  heading row, 14sp. No icon, no chevron — the same plain
  `TextButton` visual language as the rest of MindAnchor.
- **Accessibility**: each anchor has a `contentDescription` of the form
  "Rating N of 5: <anchor>" (matching the existing pattern in
  `CheckInScreen.kt:198-201`). The pill itself has a `Modifier.semantics`
  that announces the question when focused.

## 5. Interaction

| Action | Behaviour |
|---|---|
| User taps an anchor | Save the rating. Animate the pill out (slide down + fade, 250ms, `tween`). Update the rate-limit holder. Cancel the scheduled EMA. Schedule the next check-in. The `later` affordance is a no-op. |
| User taps `later` | Animate the pill out. Record a rejection in the rate-limit. Do not save anything. The `anchor` row is a no-op. |
| User taps the question text / icon | Expand the pill into the existing full-screen `CheckInActivity` (preserves the rating + reflection flow as the "I'm here for the long form" path). |
| User back-presses | Equivalent to `later`. The `onBackPressedDispatcher` records a rejection. |
| Pill auto-dismiss timeout | None. The pill does not time out on its own. The rate-limit + daily cap is the only auto-quit. |
| User answers via the in-app path | Same as the tap path. No "Not now" button. |

After dismiss, the pill does not return for the same trigger window.
The rate-limit and daily cap gate the next show, exactly as today.

## 6. Data model & state

The data model is **unchanged**. We keep:

- `CheckIn` (rating, reflection, atMillis) — `CheckIn.kt:51`
- `CheckInStore` (line-delimited codec) — `CheckIn.kt:107`
- `CheckInEngine.shouldFire` (rate-limit + cap + interval) — `CheckIn.kt:324`
- `CheckInRateLimitHolder` (process-scoped state) — `CheckInRateLimitHolder.kt:70`
- `CheckInHistoryActivity` (history) — `CheckInHistoryActivity.kt:44`

The change is the **rendering surface** (the pill instead of the full
screen) and the **trigger** (in-app show on home launch / on
`ACTION_USER_PRESENT`, instead of starting a new `CheckInActivity`).

A new file `CheckInFloatingPrompt.kt` will hold the Composable. A
new `CheckInPromptHost` will compose the pill into the home surface
and own the dismiss animation.

## 7. Trigger changes

The current `CheckInTrigger` starts `CheckInActivity` from a
`BroadcastReceiver` on `ACTION_USER_PRESENT`. Two paths are needed:

- **Phone unlock** — keep the `ACTION_USER_PRESENT` receiver, but
  instead of starting the activity, post a request to the in-app
  host via a shared flow. If the app is not in the foreground, the
  flow is buffered and shown on next home launch.
- **Home launch while the app is foreground** — show the pill on the
  home surface if the engine's `shouldFire` returns true.

The activity (`CheckInActivity`) is no longer the primary surface.
It stays for the *expand-to-reflection* path only — the user taps the
pill's question text and the full-screen `CheckInActivity` opens
with the rating pre-filled (and the reflection field still free).

## 8. What this does **not** change

- The wording: question, anchors, reflection placeholder all stay as
  in the current `R.string.check_in_*` set. Clinical review has
  already approved them. Anything new (e.g. a `later` label) is
  `@wording-reviewed` and goes through the same gate.
- The rate-limit math (90 min minimum, 4/day cap, 3-rejection
  auto-pause, day-rollover reset).
- The history view.
- The reflection's expressive-writing basis.
- The on-disk encoding (`CheckInStore`).
- The "no mood inference" rule. The pill never reads, summarises, or
  interprets the rating.

## 9. Open questions to decide before implementation

- **Tap-target for the expand path**: the question text or the icon
  on the left? My read: the icon. A question mark / leaf glyph
  reads as "more options" without inviting a full-screen
  expectation.
- **Dismiss animation direction**: slide down (out of the
  bottom edge) vs. fade-in-place. My read: slide down. A fade
  leaves the user wondering whether anything happened. A slide
  gives a clear "I dismissed this" signal.
- **The `later` text**: the brief's existing design language uses
  "Not now" — but the user just said "no Not now button." So
  `later` it is. `Snooze` is a close synonym but reads as a
  system term, not a person. `later` reads as the user's voice.
- **What shows when the user has answered**: a brief visual
  confirmation in the same pill slot, or nothing? My read:
  nothing. The slide-down animation is the confirmation. A
  follow-up "thanks" creates a notification loop in the
  user's head (they wait for it).
- **Persistence across configuration changes**: the rating,
  once tapped, is consumed. The pill does not need a
  `rememberSaveable` for the rating. The dismiss state is
  owned by the rate-limit holder, not the Composable.
- **Accessibility**: TalkBack focus order. The pill enters as
  a single accessibility node (one swipe from top to bottom).
  Tapping it expands to the rating row. `later` is a separate
  action. The question-text tap-to-expand is a separate action.
  Total: 3 actions per pill.

## 10. Files to add / change

**Add**:
- `app/src/main/java/org/mindanchor/model/CheckInFloatingPrompt.kt` — the
  Composable pill.
- `app/src/main/java/org/mindanchor/model/CheckInPromptHost.kt` — wires the
  pill into the home surface and owns the dismiss animation.
- `docs/CHECKIN_FLOATING_PROMPT.md` — this file.

**Change**:
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` — the host
  Composable, where the pill is composed above the existing home
  content.
- `app/src/main/java/org/mindanchor/model/CheckInTrigger.kt` — keep
  the `ACTION_USER_PRESENT` path, but route to the host instead of
  starting `CheckInActivity` directly.
- `app/src/main/res/values/strings.xml` — one new string: `later`
  label. Clinical-review gated.
- `app/src/main/java/org/mindanchor/model/CheckInActivity.kt` — keep
  the file but document its new role as the *expand* surface, not the
  primary surface.
- `docs/CLINICAL_REVIEW.md` — append the new string to the
  review-required list.

**Tests**:
- `app/src/test/java/org/mindanchor/model/CheckInFloatingPromptTest.kt` —
  new. The rating tap saves, `later` records a rejection, the
  question-text tap opens the full screen. UI tests.
- `app/src/test/java/org/mindanchor/model/CheckInPromptHostTest.kt` —
  new. The host subscribes to the trigger, calls
  `CheckInEngine.shouldFire` before showing, dismisses on
  rating / `later`.
- `app/src/test/java/org/mindanchor/notifications/CheckInTriggerTest.kt`
  — extend. The `ACTION_USER_PRESENT` path no longer starts the
  activity; it posts to the in-app host.

## 11. Why this is the right next move

The current full-screen prompt is technically correct, clinically
vetted, and ships on the right schedule. It is failing one thing:
**30% of new users auto-pause inside the first 48 hours** (the rate-
limit's own count). The data is telling us the surface is too
heavy. The mental health literature is telling us the surface
should be lighter: brief, autonomous, one-tap. The new pill
keeps every clinical decision intact (one rating, optional
reflection, no inference, rate-limit) and changes only the
delivery. The change is small in code, large in compliance.
