# 22 — Per-app session-length UI (F3 time-box)

## Why this brief

The senior-architect review noted that the project
already supports per-app time-box behavior through
`IfThenPlan.defaultMinutes` (Gollwitzer 1999
implementation intentions, v0.20.0). The
[IntentionPrompt] in `FrictionGate` reads
`ifThenPlan.defaultMinutes` and pre-selects the
corresponding 5/10/20 button. The user-facing UI for
*setting* a per-app default — the F3 time-box
surface — does not exist as a standalone settings
flow.

## Data model (already in place)

`FrictionPrefs.ifThenPlans` is a `Map<String,
IfThenPlan>` keyed by package name. Each plan is
`IfThenPlan(cue, action, defaultMinutes)`. The
`cue` and `action` are user-authored (the if-then
structure). The `defaultMinutes` is the per-app
time-box: when the plan is on file, the
[IntentionPrompt] pre-selects the 5/10/20 button
that matches.

## What this brief is

This brief documents the *existing* data model and
the *gap* between the data model and the user-facing
UI. The gap is real — a user cannot, today, edit the
`defaultMinutes` of an existing plan from the
settings screen. The plan's `cue` and `action` are
editable (this is the per-app plan UI), but the
`defaultMinutes` field is not exposed.

## What this PR does NOT ship

The actual per-app time-box settings UI. The full
UI is a multi-hour feature on its own: a per-app
list, a per-app minute picker (with sane defaults
like 5/10/20/45), and the data-plumbing change to
persist the per-app choice. The project owner's
design record (`docs/PLAN.md`) is the right place to
decide the shape; this brief does not pre-empt that
decision.

## Why this is a *medium* item, not a *high* item

The F3 feature is in the senior-architect review's
medium-priority list because the *behavior* the
review is concerned about is in place (the
[IntentionPrompt] pre-selects the right button),
even though the *UI for editing* is not. A user
who writes a plan with `defaultMinutes = 5` gets
the 5-minute pre-select. A user who wants a
different default can write a different plan. The
gap is *discoverability* of the existing feature,
not its existence.

## What the follow-up commit would ship

1. A new surface in `SettingsScreen` for the
   per-app plan list, with a minute picker for
   each plan.
2. The data-plumbing change: a new
   `FrictionPrefs.setIfThenDefaultMinutes(pkg,
   minutes)` (or similar) that updates the
   `defaultMinutes` field of an existing plan
   without changing the `cue` or `action`.
3. A clinical-review pass on the new strings
   (the per-app minute picker labels and the
   "what is the default" prompt).

## Primary research

- Gollwitzer PM. *Implementation intentions: strong
  effects of simple plans.* American Psychologist
  1999;54(7):493-503. (d = 0.65 for goal attainment)
- The project's SOTA-IMPROVEMENT-REPORT.md,
  v0.20.0 §"v1.2.a — FrictionBandit" and
  §"v1.2.b — IfThenPlan" (the data layer is in
  v0.20.0; the UI surface is the gap)

## Verification

None. This PR is a brief only. The next PR (the
UI) will be its own evidence trail.
