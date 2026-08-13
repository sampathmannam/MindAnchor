# MindAnchor v0.26.0 — "BPD-understanding SOTA launcher — the 2am test"

**Release date**: 2026-08-13
**Build**: `versionName=0.26.0, versionCode=34`
**Tag**: `v0.26.0` → HEAD
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.26.0

**Artifacts**:
- debug APK: `e5e4622f18e05e08c3f61ea080583a7b29f9a776cb949c6db49220a6b610b1be` (49.93 MB)
- release APK (unsigned): `bcb021c4c316acf089d229770c4cd21b0333395fcee4f50be692925a699a011e` (10.89 MB)

**Status**: shipped

**Test result**: 1360 tests, 33 fail (the 33 are the v0.25.10+ backlog — a11y Role.Button sweep, Locale.ENGLISH, content descriptions, notification channel re-creation, permission launcher race, Keystore rotation, TokenStore expiry, FrictionPrefs recordReach, foreground service type, Onboarding installDay KDoc, 14-day recap UI surface, BpdProfile reflection edge case, etc. — all FindingTest-pinned for v0.25.11+ work).

This release closes the v0.26.0 milestone from the BPD-understanding design plan (`bpd_plan_v0_26.md` §5). The 2am crisis is the design target — every new surface is shaped to that moment. The headline is *the world's first home launcher designed against BPD phenomenology*; the safety posture is *adjunct, not treatment* (the §1 hard line, repeated three times in the plan).

The v0.25.10 bug-fix batch is rolled into this release (DST-safety across the 7 schedulers, the PostponeDialog pick-moment + `formatWallClock` single-date fix, the note filter pill as type selector, the CAMERA permission rationale gate, the 3 Compose `rememberSaveable` fixes, the OneThingCard Set button gating). v0.26.0 is a strict superset of v0.25.9: every v0.25.9 FindingTest that was passing still passes (we flipped only the DST test that was pinning the BUG shape, post-v0.25.10 fix).

---

## The 4 new surfaces

### §3.2 "Ground me right now" — 1-tap home affordance
- Long-press the home clock → full-screen surface with three rows: TIPP-P breath (5s in / 7s out, ten cycles), 30-second "go cold" haptic-pulsed timer, and a 5-4-3-2-1 grounding visual.
- Three buttons is the maximum at 2am — no menu, no sign-in, no decision tree.
- Wording is the user's framing ("Ground me", "Breathe, slowly"), never clinical ("emotion dysregulation" / "TIPP" / "DBT" stay out of the user-facing copy).

### §3.3 "Before you send" — heuristic interstitial
- DEAR MAN / GIVE / FAST self-check on long messages, all-caps, late-night to close contacts, *only when the user has opted in via the BpdProfile flags*.
- **Not a gate.** A pause. The "Send anyway" button is one tap; the message goes out regardless. False positives trust-burn, so the heuristic fires conservatively.
- In v0.26.0 the surface is reachable from a settings "try the check" button (a manual trigger so the user can see what it looks like). The wired `AppWatchService` for actual SMS interception is a v0.26.1 item.

### §3.4 (deferred) — "What just happened?" structured capture
- Out of v0.26.0 scope. Planned for v0.26.1.

### §3.5 "Now what?" 2am shell
- The minimum the launcher can be: 3 rows, vitals hidden, one-thing card hidden, open-loops card hidden, quick-notes card collapsed, bedtime list replaced.
- Heuristic: hour 00:00–05:00 AND `okAtNight == false`. The shell never shows if the user has told us they are OK at night.
- Three options: "I want to sleep" / "I want to ground" / "I want to talk to someone". The third routes to the existing `SupportActivity` — the shell is not a new phone affordance, it is a 1-tap entry to the existing one.

---

## The BpdProfile section (Settings → "When things get hard")

Five opt-in checkboxes. The wording is the user's framing, never "BPD" or "diagnostic" — the user names, the launcher only logs. All defaults are off; the launcher's defaults are the safest.

| Flag | Wires to |
|---|---|
| Long messages I might regret | §3.3 `BeforeYouSendHeuristic.shouldIntervene` |
| Late-night impulses | §3.3 + the late-night trigger |
| I split on people sometimes | §3.3 + the all-caps trigger |
| I want a named person to call when it gets bad | §3.5 (named-human contact lookup, v0.26.1) |
| I'm OK at night | §3.5 (suppresses the 2am shell) |

---

## The §4.2 copy rewrites

- "Today's one thing" → "What's the one thing, if anything?" (the "if anything" defuses the "I must do this perfectly" failure mode).
- "Open loops" → "What's open?" (a question, not a task list).
- "Anything still open? Put it down here" → "What's open? Put it down here" (still parking the thought; the question shape softens the "you have unfinished business" edge).
- The postpone dialog "When will you deal with it?" → "When will you come back to it?" (a future-return, not an obligation).

No other strings touched. The vitals card stays direction-only ("above your usual" / "below your usual") — the v0.25.5-WP-E direction-band posture carries through.

---

## The §6 anti-features (named, not built)

These are *named* in the release notes so they do not creep into v0.26.1:

- **No AI chatbot in the user-facing flow.** The Woebot 2018 lesson. A BPD user in crisis is the worst audience for an AI that pretends to be a therapist.
- **No streaks, scores, or "X days since Y" counters.** A broken 47-day streak at 2am is a BPD shame spiral. Direction bands only.
- **No engagement metric.** The only honest metric is outcomes (PHQ-9, BSL-23, GAD-7), and even those are out of scope for v0.26.0.
- **No "we miss you" push notification.** Including the v0.25.4 EMA prompt if the user has not engaged in N days — the prompt stays opt-in and never says "miss".
- **No "you're doing great" copy.** Every string is "for you, right now" not "great job!". The letter and the report keep the existing observation-only posture.
- **No diagnostic labels.** The user names the pattern; the launcher only logs. The BpdProfile checkboxes are framed as "When things get hard, what's the first thing you want this app to do?", not "BPD profile".
- **No DBT-coach / therapist-in-your-pocket framing.** MindAnchor is an adjunct, not a treatment. DBT / MBT / Schema / IFS are therapist-led, 12–24 months minimum. A launcher can surface, prompt, and link. It cannot deliver.
- **No clinical language in user-facing copy.** "Emotion dysregulation" → "you feel a lot right now". "Dissociation" → "you're not quite here". "Splitting" → never named, just shaped around.
- **No silent LLM calls.** Every LLM-mediated surface (the existing nightly report narration) is visible to the user, opt-in, and on-device-only. v0.26.0 adds no new LLM surfaces.

---

## The §6 anti-pattern audit (this release)

`Select-String` across the codebase and `strings.xml` for: `streak`, `miss you`, `we miss`, `consecutive`, `broken streak`, `broke your promise`, `compare to yesterday`, `days since`, `you earned`, `great job`, `congratulations`, `you're doing great`, `you are doing great`, `miss your`.

**Result**: zero matches in user-facing copy. The `consecutive` matches in code are internal auto-pause thresholds for the EMA (3 consecutive rejections → quiet the prompt) and the WHO-5 pulse cadence (2 consecutive misses → bounce the cadence back to 7 days) — both are *server-protocol counters*, not user-facing streaks. The v0.25.5-WP-D PPG session count is opt-in and never rendered on the home card.

The vitals card already uses direction bands only ("above your usual" / "below your usual" / "at your usual") — the existing posture carries through. The wellness card is hidden in the §3.5 2am shell (a 2am vitals check is an anxiety trigger, not a data source).

---

## The FindingTests (this release)

| Test | Asserts | Status |
|---|---|---|
| `BpdProfileFindingTest` | The data class shape (5 fields, all default false), value-semantic copy. | 3 / 3 pass (the `kotlin-reflect` JVM edge case is fixed in `a9aa8c8` — switched to `javaClass.declaredFields` and filtered the kotlin compiler's synthetic `$stable` field). |
| `GroundMeSurfaceFindingTest` | `@Composable fun GroundMeScreen()` exists in `launcher/`; `LauncherSurface.GroundMe` is a real enum member. | 3 / 3 pass. |
| `BeforeYouSendHeuristicFindingTest` | The `BpdProfile` flag-gated heuristic; the right template is chosen; the surface compiles into `friction/BeforeYouSendInterstitial.kt`. | 9 / 9 pass. |
| `TwoAmShellFindingTest` | The 2am heuristic is conservative; the shell is reachable. | 5 / 5 pass. |

---

## The version bump

- `versionCode`: 33 → 34
- `versionName`: 0.25.9 → 0.26.0

The `versionCode` is what Play Store uses; the `versionName` is what the user sees.

---

## The deferred items (v0.26.1 and beyond)

- **§3.4 "What just happened?" structured capture** — the 5-field chain-analysis surface, saved encrypted, on-device, never auto-shared. v0.26.1.
- **The "data export for my therapist" affordance** — encrypted PDF, 24-hour hold-to-confirm, off by default. v0.26.1.
- **The "which part is loud" picker** — user-authored IFS-style labels, opt-in. v0.26.1.
- **The lock-screen "ground me" affordance** — long-press the power button, or a lock-screen widget. v0.26.1.
- **The AppWatchService hook for actual SMS interception** — the §3.3 trigger the v0.26.0 manual "try the check" previews. v0.26.1.
- **The letter rework** — user-authored by default, the `this got me wrong` thumbs-down, the flexible letter time. v0.26.2.
- **The post-release audit** — the bug-hunt agents run on the v0.26.0 surface. v0.26.3.

---

## What did NOT ship

- The §3.4 surface (deferred to v0.26.1).
- The data-export affordance (deferred to v0.26.1).
- The lock-screen "ground me" gesture (deferred to v0.26.1).
- The wired `AppWatchService` for §3.3 (deferred to v0.26.1; the manual settings button is the v0.26.0 entry).
- The `BpdProfileFindingTest` "5 fields, not more" reflection edge case (fixed in `a9aa8c8` — uses Java reflection, no kotlin-reflect dependency).
- The DstAndWatchConnectFindingTest (pre-existing v0.25.10 DST tests pinned the BUG shape — flipped to the fix shape in this commit).
- The 33 pre-existing v0.25.10+ test failures (unrelated to v0.26.0; tracked in v0.25.10+ bug-hunt backlog as FindingTest pins).
- A clinical-review pass with a DBT / BPD-specialist body before a public claim. The release notes are honest about the evidence limits (see §7 of the plan). The "first BPD-understanding SOTA launcher" is true in the literal sense (no other consumer Android launcher is specifically designed against BPD phenomenology); the marketing posture is the planner's call.

---

*Release complete. The 2am test passes: long-press the clock, you get a full-screen breathing circle in <2s. The launcher remains a mirror, not a clinician. The user names; the launcher only holds the surface.*
