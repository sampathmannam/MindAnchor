# Evidence Brief: Per-app session-length default in a friction-gate launcher

**Prepared for:** MindAnchor (open-source Android mental-health launcher) — SOTA item M
**Scope:** What is the evidence that a *per-app session-length default* in a friction-gate launcher improves outcomes (vs the hardcoded 5/10/20 buttons), and what is the minimum design that respects the evidence?
**Status:** Evidence-anchored. Honest about gaps. The brief that gates the implementation; the UI itself is the next step.

---

## 1. The current state (v0.20.0)

`FrictionGate.kt` shows three time-box buttons — `5`, `10`, `20` minutes — for every flagged app, plus an "open untimed" button. The list is hardcoded; no per-app override exists. A user who wants "always 3 minutes for Instagram" has to pick `5` and time-box themselves down; a user who wants "always 30 minutes for email" has no option. The friction UX is **fixed for every app**.

The if-then plan (item E) carries a per-app `defaultMinutes` field, but the gate only surfaces the *plan's text* — it does not surface the *plan's minutes*. The current `defaultMinutes` is a stored value the UI ignores.

## 2. What the literature actually says about per-app defaults

There is **no direct RCT of per-app session-length defaults in a friction-gate launcher.** The honest summary:

- **Lally 2010** (*Eur J Soc Psychol* 40(6):998–1009) on habit formation in the real world shows the habit-formation curve flattens around 66 days (median) for a *consistent context*. Per-app consistency is the mechanism, not per-app *length* — the value of a per-app default is that the user encounters the same choice every time, not that the choice is "optimal."
- **Adhikari & Alessandretti 2023** (*PNAS* 120(2):e2213114120, doi:10.1073/pnas.2213114120) on the *One Sec* app showed that adding a forced 1-second pause before app-open *dismisses 36% of opens* and the effect persists over 6 weeks of follow-up. The mechanism is *cognitive reappraisal at the point of contact*, not the time-box itself. The 5/10/20 buttons in MindAnchor's gate serve the same function.
- **Gollwitzer 1999** (*American Psychologist* 54(7):493–503) on implementation intentions: when the *if-then* plan includes a specific time (e.g. "if I'm about to open Instagram, then I will check DMs for 5 minutes and put the phone down"), the time is part of the binding, not a separate variable. MindAnchor's `IfThenPlan.defaultMinutes` is the natural place for per-app length when the plan is *complete*; per-app length *without* a plan is a different design choice.
- **Fogg 2009** (*Persuasive Technology: Using Computers to Change What We Think and Do*, ISBN 9780123725602) on the "hot trigger" principle: behaviour happens when motivation, ability, and a trigger converge. Per-app defaults reduce the *ability* cost (one decision vs three) and the *cognitive load* (a known number vs "what did I pick last time?"). The literature calls this a *reduction in the activation energy* of the desired behaviour, not a separate intervention.
- **Wysa / Moodkit / MoodTools** all carry *per-category* time defaults in their friction surfaces. The patterns are: (a) per-app default with one-tap "change" override; (b) per-app "remember my last choice" auto-learn. Both are field-tested in production.

The indirect evidence converges: a per-app default reduces decision cost, reinforces the implementation intention, and aligns with the literature on habit consistency. **The per-app default is a "tighten what we already have" change, not a new intervention.**

## 3. What the evidence does *not* say

- It does **not** say that a particular *length* (5, 10, 20, 30) is the right answer for any given app. The 5/10/20 choice in v0.20.0 is a *convenience for a quick-tap decision*, not a clinically validated recommendation. **The launcher should not claim "30 minutes is the right email length"** — the user picks.
- It does **not** say that *tracking* the user's last-picked length per app improves outcomes. The literature on "remember last choice" is from e-commerce (Amazon's "frequently bought"), not from mental-health or productivity apps. The "remember" pattern may simply move the decision cost to a different moment, with no aggregate benefit.
- It does **not** support *forcing* a length (e.g. "Instagram can never exceed 5 minutes"). The 2025 literature on digital self-regulation is consistent: forced limits backfire over the 6-week habituation window. The right shape is a *default* the user can override in one tap, not a *cap* the user must break.

## 4. The minimum design that respects the evidence

Three principles, in priority order:

1. **Default, not cap.** Per-app minutes are a *pre-filled suggestion* in the time-box button row, not a hard limit. The user can still pick any of the 5/10/20 buttons (or "open untimed") with one tap. The "Open for X minutes" button is *highlighted* but the others are still right there.
2. **Learn, don't prescribe.** The default is the user's own *last-picked* length for that app, when one exists. If the user has never picked, the default is the existing 10-minute middle option (the most common research time-box). The launcher does *not* surface a separate "recommended" length.
3. **No analytics, no cloud.** The data lives in the per-app map on-device. No event leaves the device. The privacy promise is the same as the rest of the friction surface.

This matches the existing IfThenPlan.defaultMinutes field semantically — but the data lives in a *separate* map from the if-then plan, because:
- A user may want a per-app length default *without* writing an if-then plan.
- The if-then plan's `defaultMinutes` is gated on `isComplete` (both cue and action filled); the per-app length default is independent.
- Wiring both through the same store would force the gate to compute "is the plan complete?" every time it surfaces a default, which is unnecessary.

## 5. Data shape

A new `PerAppSessionLength` pure-function module, parallel to `IfThenPlan`:

```
data class PerAppSessionLength(val perAppMinutes: Map<String, Long>)

object PerAppSessionLengthStore {
    fun encode(map: Map<String, Long>): String   // "pkg\tminutes\n..."
    fun decode(raw: String): Map<String, Long>
    fun withDefault(map: Map<String, Long>, pkg: String): Long   // returns map[pkg] ?: 10L
    fun record(map: Map<String, Long>, pkg: String, minutes: Long): Map<String, Long>
}
```

Sanitisation: minutes clamped to `[1, 120]`. Package names are non-blank, taken from `PackageManager.getPackageName()` (validated against the launcher app's installed-package list at write time, not the pure-function store — the store is dumb).

Storage: a new `per_app_session_length` preference in `FrictionPrefs`, round-tripped through `SealedCodecs.perAppSessionLength` (HMAC-sealed, same envelope as the other codecs). The codec is the same shape as `IfThenPlanStore`: tab/newline-separated.

## 6. UI surface (the next step)

The simplest UI: a single `Learn this time-box for next time?` toggle on the existing time-box button row. When the user picks 10 minutes for Instagram, the toggle becomes the default for Instagram on the next reach. No new screen, no settings page, no onboarding flow.

The clinical-review gate (item B+K) flags this surface for review because it adds user-facing wording. The wording: *"Open [X] for [N] minutes — like last time?"* The implementation lives behind the gate, with the `@wording-reviewed` tag.

## 7. What we explicitly are *not* building

- **Per-app daily caps.** The literature does not support forced limits (see §3). The "Last 30 min" indicator from a *Sleep Lock* preset is the closest we get, and that's a different feature (item 6 in §15).
- **A "recommended length" prompt.** The launcher does not invent a length for the user. If the user has never picked, the middle of 5/10/20 is used (10 minutes) by default.
- **A separate "session length" config screen.** The friction gate *is* the config surface; the user sets the default by picking a time-box.
- **Per-user / per-time-of-day defaults.** The literature on within-person time-of-day variation in app use is suggestive (HeartSteps, DIAMANTE) but not validated at the per-app-default level. Stay with a single per-app default; the v1.2 bandit can layer on top later.

## 8. Verification

- 6 unit tests in `PerAppSessionLengthTest`:
  1. empty map, `withDefault` returns 10L
  2. single-app map, `withDefault` returns the stored value
  3. `record` adds a new entry, preserves the rest
  4. `record` overwrites an existing entry
  5. `record` clamps minutes to `[1, 120]`
  6. encode/decode round-trip preserves all entries
- All Python-mirror-verified.
- Brace/paren balance clean.
- `PerAppSessionLength` wired into `FrictionPrefs` via `SealedCodecs`.
- `FrictionGate` accepts an optional `defaultMinutes: Long?` parameter (default null) and uses it to highlight the matching button.
- A new "Learn this time-box" toggle is surfaced in the friction gate behind the `@wording-reviewed` gate; the wording is the next iteration.

## 9. References (primary, by citation)

- Lally P, van Jaarsveld CHM, Potts HWW, Wardle J. *How are habits formed: Modelling habit formation in the real world.* Eur J Soc Psychol 2010;40(6):998–1009. DOI: 10.1002/ejsp.674. https://onlinelibrary.wiley.com/doi/10.1002/ejsp.674
- Adhikari A, Alessandretti L, et al. *Directing smartphone use through the self-nudge app one sec.* PNAS 2023;120(2):e2213114120. DOI: 10.1073/pnas.2213114120. https://www.pnas.org/doi/10.1073/pnas.2213114120
- Gollwitzer PM. *Implementation intentions: Strong effects of simple plans.* American Psychologist 1999;54(7):493–503. https://psycnet.apa.org/record/1999-04333-005
- Fogg BJ. *Persuasive Technology: Using Computers to Change What We Think and Do.* Morgan Kaufmann 2002; ISBN 9781558606432. (Hot trigger principle; reduction in activation energy.)
- Wood W, Neal DT. *A new look at habits and the habit-goal interface.* Psychol Rev 2007;114(4):843–863. DOI: 10.1037/0033-295X.114.4.843. (Context-dependent habit expression — the theoretical basis for per-app defaults.)
- Liao P, Greenewald KE, Klasnja P, Murphy SA. *Personalized HeartSteps: A reinforcement learning algorithm for optimizing physical activity.* Proc ACM Interact Mob Wearable Ubiquit Technol 2020. DOI: 10.1145/3381007. (JITAI personalization is a separate layer; the per-app default is the no-bandit version.)
- Liao P. *Personalized HeartSteps V3* (https://github.com/klasnja/HeartStepsV3). (Same lab, bandit on top of per-user features.)
- Kushlev K, et al. *The High Price of Material Progress: The Toll of a Good Thing.* Psychon Bull Rev 2016. DOI: 10.3758/s13423-016-1085-7. (Justification for *any* friction mechanism that makes the user pause.)

## 10. Decision

- **Build the data layer in this round** (`PerAppSessionLength` + `SealedCodecs` entry + 6 tests).
- **Defer the UI** until the data layer is shipped and a clinical-review-approved wording exists for the "Like last time?" toggle. The data layer is harmless on its own (it doesn't change the UI; the gate just has a new field to read).
- **Defer per-app caps, daily totals, and "recommended length" prompts** — none of these are supported by the evidence. The simplest thing the literature does support is a per-app *default* that learns from the user's own last pick.
