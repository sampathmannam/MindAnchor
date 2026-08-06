# Contributing to MindAnchor

Thanks for helping build a calmer phone.

## The evidence gate (project law)

MindAnchor's identity is that every intervention is research-backed. Concretely:

1. **A PR adding or changing an intervention feature must cite at least one
   peer-reviewed study** in its description, and the study must be added to
   `docs/research/` if not already there.
2. The feature must implement the **studied mechanism**, not a vibe of it, and
   defaults should match the study's dosage where possible (e.g., notification
   batches default to 3×/day per Fitz et al. 2019).
3. Every intervention ships with a **toggle** and, where feasible, a measurement hook.
4. Features the evidence argues against will be declined regardless of polish:
   blanket notification muting, streaks/points/leaderboards, shame-framed statistics,
   red badges, and anything resembling unsupervised AI therapy.

Non-intervention contributions (bug fixes, refactors, translations, accessibility,
docs) need no citations — just tests where behavior changes.

## Ground rules

- **Zero backend stays zero.** No analytics, no telemetry, no network calls in core.
  PRs adding an `INTERNET`-dependent feature need a documented, isolated justification.
- **Privacy is structural.** Notification content, usage events and wellbeing pulses
  never leave the device. Don't log content — metadata only, and only locally.
- **Calm design language.** No red accents outside genuine emergencies, no autoplaying
  motion, no manufactured urgency. Copy is warm and non-judgmental — we never shame
  the user about their usage.
- **Kotlin + Jetpack Compose**, single app module for now. Match existing style;
  keep dependencies boring and FOSS (F-Droid inclusion is a hard requirement, so no
  proprietary libraries or Google Play Services).

## Practical notes

- Build with `./gradlew build` (JDK 21). CI runs build + lint + unit tests on every PR.
- Milestones and scope live in [docs/PLAN.md](docs/PLAN.md); please check an issue or
  the plan before starting significant work so we protect the v1 scope.
