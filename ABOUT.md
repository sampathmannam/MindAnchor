# About MindAnchor

## Project Overview

**MindAnchor** is an open-source, research-backed, mental-health-first launcher and notification layer for Android. It inverts the conventional smartphone objective: instead of optimizing for engagement and screen time, MindAnchor succeeds when your attention, sleep, and mood improve.

Every feature and intervention in MindAnchor is tied to peer-reviewed research, and the app measures on your device whether it is actually helping you.

---

## Core Mission

Modern smartphones are engineered for engagement. MindAnchor rejects this paradigm and instead prioritizes:

- **Your wellbeing first** — not engagement metrics
- **Evidence-driven design** — every feature cites published research
- **Privacy and autonomy** — you control everything; nothing is imposed
- **Local, offline operation** — zero backend, zero cloud dependency

---

## Key Features (v1)

### 🔔 Batched Notifications
- Consolidates notifications into a few calm daily digests
- Important contacts (humans you choose) always come through instantly
- Backed by randomized trials showing improved mood and attention

### 📱 Text-First Minimal Launcher
- Search-first interface to open apps
- Curated favorites list, no app grid
- No badges, no red dots, no visual clutter

### ⏸️ Self-Chosen Friction
- Optional breathing-paced pause before opening addictive apps
- Intention prompts to make you intentional about usage
- Time-boxed sessions to limit exposure

### 🌙 Sunset Mode
- Grayscale mode from your chosen wind-down hour
- Feeds are gated and batched until morning
- Everything quiets down to support sleep

### 😴 Sleep-Regularity Tracking
- Infers sleep patterns from screen on/off behavior
- No wearable required, no cloud sync
- Stays entirely on your device

### 📊 Honest Measurement
- Short, low-friction wellbeing pulse surveys
- If a feature isn't helping *you*, the app tells you and offers to turn itself off
- Evidence-based feedback loop

---

## Core Principles

### 1. Zero Backend
- No user accounts required
- No servers, no analytics, no telemetry
- Your data never leaves your device
- Perfect privacy by design

### 2. Evidence or It Doesn't Ship
- Every intervention cites the peer-reviewed study it implements
- See [docs/CONCEPT.md](docs/CONCEPT.md) for detailed citations
- Research reviews available in [docs/research/](docs/research/)
- Evidence-based defaults (e.g., 3×/day notification batches per Fitz et al. 2019)

### 3. Autonomy, Not Paternalism
- Everything is a toggle
- No feature is forced on users
- Users retain full control over their experience

### 4. Wellness, Not Medicine
- MindAnchor is **not a medical device**
- Does not diagnose, treat, or replace professional care
- If you're in crisis, please reach out to a human:
  - **US**: 988 (Suicide & Crisis Lifeline)
  - **India**: Tele-MANAS 14416
  - **Other regions**: Check your local crisis helpline

---

## Technical Details

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle
- **Minimum SDK**: Android 13+
- **License**: Apache 2.0

### Building the Project
```bash
./gradlew assembleDebug
```

### Architecture
- Single-module app architecture
- No proprietary dependencies
- F-Droid compatible (F-Droid inclusion is a hard requirement)
- No Google Play Services
- No analytics or telemetry libraries

---

## Project Status

**Early development, but solid foundation:**

- ✅ Milestones M0–M5 built and CI-verified
- ✅ Sensing and writing layers implemented
- ⏳ Outstanding items:
  - Release signing key setup
  - Clinical review of messaging
  - Real-device beta testing

**Important Note**: All releases so far are debug-signed. Google Play Protect may warn about APK signatures — see [docs/RELEASING.md](docs/RELEASING.md) for details.

**Requires**: Android 13 or later

---

## Design Philosophy

### Calm Design Language
- No red accents outside genuine emergencies
- No autoplaying motion or animations
- No manufactured urgency or FOMO tactics
- Warm, non-judgmental copy
- Never shames users about their usage habits

### Privacy-First Approach
- Notification content never synced
- Usage events stay local
- Wellbeing survey responses never transmitted
- Metadata-only logging, and only locally

### Code Quality Standards
- Zero backend dependency (no analytics, telemetry, or network calls in core)
- Kotlin + Jetpack Compose codebase
- Matches existing code style throughout
- Dependencies must be FOSS and boring
- F-Droid compatibility is mandatory

---

## How to Contribute

MindAnchor welcomes contributions in the form of:

- **Features** (must be evidence-backed)
- **Bug fixes**
- **Refactoring**
- **Translations**
- **Accessibility improvements**
- **Documentation**

### The Evidence Gate
For intervention features:
1. Cite at least one peer-reviewed study in your PR description
2. Add the study to `docs/research/` if not already included
3. Implement the studied mechanism exactly (not a "vibe" of it)
4. Include a toggle for the feature
5. Add measurement hooks where feasible

**Note**: Features arguing against batched notifications, gamification (streaks, points), shame-based statistics, red badges, or unsupervised AI therapy will be declined regardless of code quality.

### Ground Rules
- **Zero backend** — maintain offline-first architecture
- **Privacy is structural** — no content logging
- **Calm design** — respect user attention
- **Kotlin + Jetpack Compose** — match existing style
- **FOSS only** — no proprietary libraries
- **Tests required** — for all behavior changes

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## Repository Structure

```
MindAnchor/
├── app/                          # Main Android application module
├── docs/                         # Documentation
│   ├── CONCEPT.md               # Research backing and concepts
│   ├── PLAN.md                  # Roadmap and milestones
│   ├── RELEASING.md             # Release and signing instructions
│   └── research/                # Research papers and citations
├── fastlane/                     # Release automation
├── tools/                        # Development tools
├── gradle/                       # Gradle wrapper files
├── third_party/                 # Third-party dependencies
├── build.gradle.kts             # Root build configuration
├── settings.gradle.kts          # Gradle settings
├── gradle.properties            # Gradle properties
├── README.md                     # Quick start guide
├── CONTRIBUTING.md              # Contribution guidelines
├── ABOUT.md                      # This file
├── LICENSE                       # Apache 2.0 license
└── SOTA-IMPROVEMENT-REPORT.md   # State-of-the-art improvements documentation
```

---

## Research & Citations

MindAnchor is built on evidence from:
- Notification batching: Fitz et al. 2019 (mood and attention improvements)
- Friction and intentionality: Multiple HCI and wellbeing studies
- Sleep regularity: Chronobiology and sleep science research
- Measurement-based wellbeing: Ecological momentary assessment research

All citations are included in [docs/research/](docs/research/).

---

## License

MindAnchor is released under the **Apache License 2.0** (Apache-2.0).

This means:
- The code is free to use, modify, and distribute
- Modifications can be released under any license (Apache 2.0
  does not impose copyleft on derivative works)
- Commercial use is allowed under the Apache terms
- Attribution is required; a `NOTICE` file must be preserved
  if one is included
- An explicit patent grant from each contributor is included

See [LICENSE](LICENSE) for the full legal text. The license
was flipped from GPL v3 to Apache 2.0 in v0.25.19 (2026-08-12);
see [RELEASE_NOTES_v0.25.19.md](RELEASE_NOTES_v0.25.19.md) for
the rationale.

---

## Contact & Support

- **Repository**: [github.com/sampathmannam/MindAnchor](https://github.com/sampathmannam/MindAnchor)
- **Issues**: [GitHub Issues](https://github.com/sampathmannam/MindAnchor/issues)
- **Discussions**: Check the repository for community discussions

### If You're in Crisis

MindAnchor is a wellness app, not a replacement for mental health care.

- **US**: Call or text 988 (Suicide & Crisis Lifeline)
- **India**: Call Tele-MANAS 14416
- **Other countries**: Find your local crisis helpline

---

## Roadmap & Vision

For detailed roadmap information, see [docs/PLAN.md](docs/PLAN.md).

Current focus areas:
- Stabilizing core features (M0–M5)
- Real-device beta testing
- Clinical validation of messaging
- Preparing for public release

---

## Project Stats

- **Language**: Kotlin (Android)
- **Status**: Early Development
- **License**: GPL-3.0-only
- **Repository Size**: ~2.4 MB
- **Build System**: Gradle (JDK 21)
- **CI/CD**: GitHub Actions (build, lint, unit tests)
- **Open Issues**: Tracked in GitHub Issues

---

## Acknowledgments

MindAnchor builds on decades of research in:
- Human-computer interaction (HCI)
- Behavioral psychology
- Sleep and circadian science
- Digital wellbeing

All research foundations are explicitly cited in our documentation.

---

**Last Updated**: August 2026  
**Project Started**: ~50 days before initial documentation
