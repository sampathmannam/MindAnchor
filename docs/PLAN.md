# MindAnchor — Build Plan

> From concept to shipping. Companion to [CONCEPT.md](CONCEPT.md) and the SOTA surveys in
> [research/](research/). Sections marked *(pending)* will be completed from the Android
> technical-feasibility report (research/05).

## 1. Product definition (v1)

**One sentence:** an open-source Android launcher + notification layer that batches
machine notifications, adds self-chosen friction to compulsive apps, protects sleep
regularity, and measures whether it's actually helping *you*.

**v1 feature set (locked — everything else is post-v1):**

| # | Feature | Evidence anchor | Research doc |
|---|---------|-----------------|--------------|
| F1 | Sender-tiered notification batching (3×/day default; designated humans instant) | Fitz 2019 RCT; Dekker 2024 (why not mute) | 01 §5, 02 §3 |
| F2 | Text-first minimal launcher (search-to-open, no grid, no badges) | MinimalistPhone 2025; grayscale/cue-removal line | 02 §6 |
| F3 | App-open friction: intention prompt + breathing-paced delay + time-boxed sessions, per-user app list | one sec PNAS 2023; CHI 2024 longitudinal | 02 §2 |
| F4 | Sunset mode: wind-down hour → grayscale, feeds gated, batch-until-morning, humans + alarm through | Carter 2016; SRI evidence; Castelo 2025 mechanism | 01 §4, 02 §4 |
| F5 | Sleep-regularity tracking (SRI from screen on/off + first unlock) as the headline metric | UK Biobank SRI 2024 | 01 §4 |
| F6 | Goal-elicitation onboarding that turns features on/off per user | ReDD CHI 2024; "Going Light" CHI 2026 | 02 §1, §6 |
| F7 | n-of-1 measurement: WHO-5/PSS-4 pulse every 2 weeks, per-feature honest trend view, de-escalation if not helping | TOCHI 2023 meta (dashboards weakest); Linardon 2024 | 01 §3, 02 §2 |

**Explicit non-goals for v1:** chatbots/LLM anything, mood inference, wearables,
cloud/backend, accounts, iOS, custom ROM, app store, crisis detection (crisis *resources*
are a static, always-reachable screen — routing only, no detection).

**Post-v1 roadmap (ordered):** v1.1 Castelo mode (scheduled internet-content fasting) ·
v1.2 adaptive anti-habituation friction (bandit-timed) · v1.3 Health Connect trends +
gentler-morning adaptation · v1.4 on-device LLM notification digest (flagship devices) ·
v1.5 NFC-tag physical friction · v2.0 keystroke-metadata baseline (own IME fork).

## 2. Principles that constrain the build

1. **Zero backend, zero accounts, zero analytics.** No `INTERNET` permission in v1 if
   achievable; if a webview/crisis-links need it, network access is confined to an
   isolated process and documented.
2. **Every feature is a toggle.** Onboarding turns things on; nothing is imposed
   ("Going Light": imposed minimalism fails).
3. **Degraded-mode first.** Every feature must have a fallback when a permission is
   denied (design per-permission fallbacks up front).
4. **Evidence gate for merges.** New intervention features require a citation in the PR
   description + a measurement hook.
5. **Do-no-harm defaults.** No blanket mute, no shame copy, no streaks, no red badges.
   De-escalation path when a user's own data shows a feature isn't helping.

## 3. Milestones

**M0 — Scaffold (week 1–2).** Kotlin + Jetpack Compose single-module app; Room DB;
CI (GitHub Actions: build + lint + unit tests); GPLv3 LICENSE; CONTRIBUTING.md with the
evidence-gate rule; F-Droid-compatible build (no proprietary deps); fastlane metadata.
*Exit: installable APK showing an empty home screen, green CI.*

**M1 — Launcher core (week 2–5).** HOME role; app list via LauncherApps; search-first UI;
favorites (max 6); app hiding/renaming; no badges anywhere; settings screen.
*Exit: daily-drivable as default launcher.*

**M2 — Notification batcher (week 4–8).** NotificationListenerService; sender
classification (human contact vs machine); hold-and-release scheduler (default 08:00 /
12:30 / 18:00); digest UI grouped by app; breakthrough list editor; delivery-reliability
safeguards. *(exact mechanics pending research/05)*
*Exit: F1 works for 2 weeks of self-use without losing a single notification — this is
the make-or-break milestone; batching bugs destroy trust permanently.*

**M3 — Friction engine (week 7–10).** Per-app intention prompt + breathing-paced open
(haptic-guided); time-boxed sessions with graceful exit; session-extension counter.
Foreground-app detection strategy per research/05. *Exit: F3 on user-chosen apps.*

**M4 — Sunset mode + SRI (week 9–12).** Scheduled mode engine (DND integration,
grayscale approach per research/05, feed gating via F3 machinery); screen-event logging →
sleep-window estimation → SRI computation and trend display.
*Exit: F4 + F5 measurable on own device.*

**M5 — Onboarding + measurement (week 11–14).** ReDD-style goal elicitation → feature
config; WHO-5/PSS-4 pulse scheduling; per-feature trend view; de-escalation suggestions;
crisis-resources screen (region-aware static list: 988 US, Tele-MANAS 14416 India, etc.).
*Exit: v1.0 feature-complete.*

**M6 — Beta + evaluation (week 14+).** F-Droid submission; GitHub release APK; 20–50
person beta with opt-in anonymized aggregate export (local-first, explicit consent);
pre-registered hypothesis: "batching + friction → PSS-4 improvement at 4 weeks vs.
own baseline." *Exit: public v1.0 + first honest data.*

Timeline assumes ~1 focused developer with AI assistance; treat weeks as sequence, not
promises.

## 4. Technical architecture *(pending — filled from research/05)*

- Module layout, permission manifest, min SDK: TBD.
- Notification batching mechanics (snooze vs cancel-repost): TBD.
- App-gating approach (UsageStats polling vs AccessibilityService) + store-policy stance: TBD.
- Grayscale mechanism: TBD.
- Scheduling reliability (AlarmManager/WorkManager/Doze): TBD.
- Reuse decisions (Mindful, Siempo, Olauncher/mLauncher, Bundel, Kvaesitso): TBD.

## 5. Risks

| Risk | Mitigation |
|------|------------|
| Notification loss in batcher | M2 exit criterion; store-and-forward journal; panic "release everything now" switch |
| OEM battery killers kill the listener | dontkillmyapp.com guidance in onboarding; watchdog re-bind; degraded mode |
| Play Store policy vs AccessibilityService | F-Droid-first; Play build may ship degraded friction (per research/05) |
| Habituation erodes friction | v1.2 adaptive friction; vary copy; rest days |
| Overclaiming health benefits | "wellness, not medical device" framing everywhere; honest effect-size language; n-of-1 data over marketing |
| Solo-maintainer burnout (Siempo/Blloc died) | ruthless v1 scope; boring tech; evidence gate keeps scope creep out |

## 6. Repo layout (target)

```
MindAnchor/
├── app/                  # single Android app module (Kotlin, Compose)
│   └── src/main/java/org/mindanchor/
│       ├── launcher/     # F2 home screen
│       ├── notifications/# F1 listener + scheduler + digest
│       ├── friction/     # F3 gate engine
│       ├── modes/        # F4 sunset/scheduler
│       ├── metrics/      # F5 SRI + F7 pulses (Room)
│       └── onboarding/   # F6
├── docs/                 # CONCEPT, PLAN, research/
├── fastlane/             # F-Droid metadata
└── .github/workflows/    # CI
```

## 7. Definition of "research-backed" (project law)

A feature may claim a study only if: the study is linked in docs/research/; the feature
implements the *studied mechanism* (not a vibe of it); dosage/defaults match the study
where possible (e.g., 3×/day batches); and the feature's own toggle-off exists. Deviations
are documented inline in the code.
