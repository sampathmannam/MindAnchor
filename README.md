# MindAnchor

**An open-source, research-backed, mental-health-first launcher and notification layer for Android.**

Modern phones are optimized for engagement. MindAnchor inverts the objective: the app
succeeds when your attention, sleep and mood improve — not when screen time goes up.
Every intervention it ships is tied to a published study, and the app measures on your
own device whether it is actually helping you.

## What it does (v1)

- **Batches machine notifications** into a few calm daily digests — the humans you
  choose always come through instantly. (Batching improved mood and attention in a
  randomized trial; muting everything backfired.)
- **Text-first minimal launcher** — search to open, a handful of favorites, no grid,
  no badges, no red dots.
- **Self-chosen friction** on the apps that pull at you: a breathing-paced pause, an
  intention prompt, time-boxed sessions.
- **Sunset mode** — from your wind-down hour: grayscale, feeds gated, everything
  batched until morning.
- **Sleep-regularity tracking** from screen on/off patterns — no wearable, no cloud.
- **Honest measurement** — short wellbeing pulses; if a feature isn't helping *you*,
  the app says so and offers to turn itself off.

## Principles

- **Zero backend.** No accounts, no server, no analytics. Your data stays on your phone.
- **Evidence or it doesn't ship.** Every intervention cites the study it implements —
  see [docs/CONCEPT.md](docs/CONCEPT.md) and the SOTA surveys in
  [docs/research/](docs/research/).
- **Autonomy, not paternalism.** Everything is a toggle; nothing is imposed.
- **Wellness, not medicine.** MindAnchor is not a medical device and does not diagnose.
  If you are in crisis, please reach a human: 988 (US), Tele-MANAS 14416 (India), or
  your local crisis line.

## Project status

Early development, but well past the scaffold: milestones M0–M5 are built and
CI-verified, and the sensing and writing layers on top of them are in place. See
[docs/PLAN.md](docs/PLAN.md) for the roadmap and for what is genuinely still
outstanding — chiefly a release signing key, a clinician's reading of the wording,
and a real-device beta. Requires Android 13+.

Every release so far is debug-signed, so Play Protect will warn about the APK; see
[docs/RELEASING.md](docs/RELEASING.md).

## Building

```
./gradlew assembleDebug
```

## License

[GPL-3.0-only](LICENSE).
