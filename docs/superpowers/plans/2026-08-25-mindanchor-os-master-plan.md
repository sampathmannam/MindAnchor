# MindAnchor OS — Master Plan (2026-08-25)

> From launcher to de-facto mental-health OS. Written after a full read of the
> repo at v0.69.0 (CONCEPT.md, PLAN.md, PHASE_4_STATUS.md, CLINICAL_REVIEW.md,
> RELEASING.md, the research/ index, and the actual code in
> `app/src/main/java/org/mindanchor/`). Every claim about what exists was
> verified by grep against the source, not taken from the docs.

---

## Part 1 — The OS question, answered honestly

Your founding document (docs/CONCEPT.md) is already titled "MindAnchor OS" and
already answered this in §2. The answer holds up in 2026, and here is the
sharper version:

**You do not need to build an operating system. You need to finish becoming
the OS's policy layer.** Android deliberately exposes the hooks that matter:

| Layer | Android hook | MindAnchor status |
|---|---|---|
| Home screen | `ROLE_HOME` | ✅ shipped |
| Notification delivery | `NotificationListenerService` | ✅ shipped (journal-then-cancel batching) |
| App-open interception | launcher-intercept + AccessibilityService | ✅ shipped (friction gate, AppWatchService) |
| Display | grayscale via ZenDeviceEffects / daltonizer | ✅ shipped |
| DND / interruptions | AutomaticZenRule | ✅ shipped (sunset) |
| Network | VpnService | ✅ shipped (Going Light / Castelo mode) |
| Sleep sensing | UsageStats screen events | ✅ shipped (SRI) |
| **App suspension / task pinning** | **Device Owner APIs** | ⚠️ scaffolded, not consolidated |
| Keyboard | own IME | ❌ (planned v2.0) |
| Browser | own default browser | ❌ |
| Quick Settings | TileService | ❌ |
| Notification shade, lock screen, SystemUI, boot | **ROM only** | ❌ honestly impossible at Tier 1 |

**What a Tier-2 ROM (AOSP fork) would actually buy you** — and its real cost:

- Buys: notification-shade replacement, lock-screen panic/grounding button,
  per-surface grayscale, system sounds, scroll-gesture detection in SystemUI,
  uninstall-proof commitment devices.
- Costs: monthly security-patch rebasing forever, per-device trees, OTA
  infrastructure, release signing infra, and a 1–2 person-year runway before
  the first usable build. GrapheneOS has a team. Siempo died with less scope.
- Verdict: **not now, and maybe never.** Every intervention with strong causal
  evidence (batching — Fitz 2019; app-open friction — PNAS 2023; internet
  fasting — Castelo 2025; sunset/SRI — UK Biobank; safety plan — Stanley 2018)
  is implementable at Tier 1, and you have implemented most of them. The ROM
  buys polish, not evidence. Keep Tier 2 as a research doc (task T-9.4), not
  a build target.

**The one big unclaimed middle rung: "OS Mode."** Your `admin/DeviceOwner.kt`
already calls `setPackagesSuspended`. A device-owner-provisioned MindAnchor is
allowed to suspend packages system-wide, pin tasks, and set user restrictions —
that is OS-grade policy power on stock Android, no ROM. It is scaffolded but
not consolidated into a coherent, opt-in, autonomy-respecting posture. That is
Phase 1 below, and it is the highest OS-feel-per-line-of-code work available.

---

## Part 2 — Scorecard: the CONCEPT.md vision vs. what the code actually contains

Legend: ✅ built · 🟡 partial · ❌ absent (verified by grep, 2026-08-25)

### 3.1 Attention & Notification Architecture
- ✅ Batched notifications 3×/day, journal-then-cancel, digest, active hours,
  retention pruning, breakthrough humans
- ✅ No badges / no red dots
- ❌ **Marketing-notification classifier** (nothing demotes marketing pings
  below ordinary machine pings — CONCEPT 3.1B)
- ❌ **Attention receipts** ("who bought your attention" weekly view —
  CONCEPT 3.1D; grep for receipt logic finds nothing; the insights/report
  packages do n-of-1 patterns, not interruption attribution)

### 3.2 Anti-Compulsion Launcher
- ✅ Intention prompt, breathing-paced gate, time-boxed sessions with
  extension counter, PreHome doomscroll pause, bandit-adaptive friction
  (the anti-habituation design from CONCEPT 6.2#2 — actually built)
- ✅ Grayscale, text-first launcher
- ❌ Infinite-scroll interruption (flagged Tier-2 in CONCEPT; a bounded
  version is possible via AppWatchService but is genuinely hard — deferred)

### 3.3 Circadian & Sleep
- ✅ Sunset mode, SRI as headline metric, sleep window estimation
- ✅ Bedtime lockout with slow override (Sleep Lock + 30s typed dwell)
- 🟡 Morning protection (PreHome intention exists; "no feeds for N minutes
  after wake" as an explicit toggle does not)
- ❌ **Digital CBT-I program** (strongest sleep evidence in the whole doc —
  Freeman 2017, Espie 2019 — and completely unbuilt; only a settings-screen
  string mentions CBT)

### 3.4 Mood-Aware / Digital Phenotyping
- ✅ Sleep regularity vs own baseline, deviation check-ins, WHO-5 pulse,
  notes/check-in EMA
- ❌ Typing dynamics (needs the IME — v2.0 track)
- ❌ Sociality signals (no call-log/contact metadata at all)
- ❌ GPS entropy (correctly skipped — privacy cost outweighs, per project law)

### 3.5 JITAI / Micro-Interventions
- ✅ Breathing protocols, TIPP crisis-survival card, BA weekly prompt,
  micro-CBT reframe via on-device LLM, safety plan + chosen contacts
  (R1 decision respected: no hardcoded crisis lines)
- 🟡 Panic/grounding reachability: exists in-app; lock screen is Tier-2, but
  a Quick Settings tile gets 80% of the way (T-1.4)

### 3.6 Social Connection — **the biggest evidence-backed unbuilt subsystem**
- ❌ People-first UI ("Talk to Mom" as first-class intent)
- ❌ Reconnection prompts (Liu et al. JPSP)
- ❌ Passive-vs-active consumption meter
- ❌ Call-over-text defaults (Kumar & Epley 2021)
- (Favorites exist in the launcher, but as app shortcuts, not people)

### 3.7 App Store with Evidence Bar
- ❌ Entirely. **Recommendation: never build the store** — it is a second
  product with moderation burden. Build the "attention facts" scorecard for
  *installed* apps instead (ACDP typology, CONCEPT 6.2#10) — T-7.1.

### 3.8 Calm Aesthetics
- ✅ Calm design language (research/06), warm wind-down
- ❌ Notification sound design (human-vs-machine sound classes)

### 3.9 Work/Life Boundary
- ❌ Context profiles (Work/Home/Sacred hours) — nothing exists
- ❌ Focus sessions with dock-the-phone ritual

### 3.10 Measurement, Safety & Ethics
- ✅ Outcome dashboard (n-of-1, GateLedger honest-fact reporting,
  de-escalation), never-interpret-scores invariant, clinician pack,
  log scrubber, HMAC-sealed stores
- ❌ Research mode (opt-in anonymized aggregate export — M6 scope)
- ❌ Vulnerable-population modes (adolescent, grief, recovery) — needs a
  clinician first; correctly deferred

### Built beyond the concept (credit where due)
Going Light VPN (Castelo mode — CONCEPT 6.2#6 ✅), NFC physical anchor
(6.2#7 ✅), on-device LLM letters + reframe (6.2#8 ✅), self-evaluating OS
via n-of-1 (6.2#9 ✅), COROS/Health Connect vitals bridge, voice-journal and
push-up-gate scaffolds, device-owner Sleep Lock, PreHome, healthy-defaults
walkthrough, full LLM security hardening (v0.69.0).

**Score: of the 10 "novel features nobody ships" in CONCEPT 6.2, seven are
built or mostly built.** What's missing clusters into exactly four themes:
(1) OS Mode consolidation, (2) the Social Connection subsystem, (3) CBT-I,
(4) notification intelligence (classifier + receipts).

---

## Part 3 — What the concept never thought of (new, evidence-anchored)

1. **OS Mode as a product posture, not a hidden adb trick.** A guided,
   reversible, self-chosen device-owner provisioning flow with a visible
   "what this grants / how to leave" contract. Suspension of doomscroll
   packages during sunset (survives every entry point — notifications, links,
   share sheet — which launcher-level gating cannot). Autonomy law respected:
   user-chosen list, user-chosen windows, always an escape hatch with a slow
   override, never imposed. (Commitment-device literature already cited in
   CONCEPT 3.3.)
2. **Quick Settings tiles** — Sunset-now, Going-Light, Release-my-batch,
   Breathe. Cheap, huge OS-feel, zero new permissions.
3. **A wellbeing default browser** (minimal, reader-first, no feed
   amplification) completing the healthy-defaults walkthrough — currently it
   can only point at system settings; owning the browser role closes the loop
   the way owning HOME did. (Castelo mechanism, applied per-surface.)
4. **Notification sound classes** — distinct human-vs-machine sounds so the
   nervous system learns most buzzes are ignorable (CONCEPT 3.8 had the idea;
   it never became a task).
5. **The IME as OS surface #2** — fork an existing GPL keyboard (FlorisBoard
   is AGPL/GPL-compatible ecosystem — verify license before vendoring) for
   typing-rhythm metadata (BiAffect pattern: how you type, never what) and
   because search-driven doomscrolling starts in the keyboard. This is the
   single biggest lift on the list; it is last for a reason.
6. **Tier-2 decision memo as a research artifact** — a one-time
   docs/research/ entry stating exactly what a ROM buys, costs, and the
   trigger conditions under which the project would revisit (e.g. >10k users
   + a second maintainer + funding). Kills the recurring "should we build an
   OS?" question with a documented decision.

---

## Part 4 — What genuinely requires a ROM (do not attempt at Tier 1)

Notification shade replacement · lock-screen replacement · Quick Settings
panel redesign (tiles yes, panel no) · per-surface grayscale · SystemUI
sounds · boot experience · uninstall-proof self-protection · OS-level scroll
gesture detection. Anyone who pitches these as launcher features is wrong;
route them to the Tier-2 memo.

---

## Part 5 — The end-to-end roadmap (v0.70 → v1.0)

Ordering logic: evidence strength × OS-feel × buildability, respecting the
project's standing gates (clinical-review sign-off R2/R3/R4/R6 pending; G-36
two-week live test pending; signing key pending — all user actions, listed in
Phase 9).

Every task below inherits the **project laws** (they are non-negotiable and
CI-enforced): evidence citation in the PR description + measurement hook +
per-feature toggle, default OFF (opt-out-by-silence) · no shame copy, no
streaks, no red badges · zero network calls (`NetworkCallsForbiddenTest`) ·
clinical-review wordlist gate (`ClinicalReviewWordlistTest`) · detekt + lint +
unit tests green · degraded mode for every denied permission.

Task sizing: S = ≤1 day · M = 2–4 days · L = 1–2 weeks.
Executor: **MM** = MiniMax-suitable (mechanical, pattern-mirroring,
verifiable) · **CL** = Claude/you (architecture, safety-adjacent, native,
or judgment-heavy). MiniMax tasks get a full spec (G-28-style) generated
per task before handoff — see Part 6.

### Phase 1 — OS Mode (v0.70) — "the launcher becomes the policy layer"
- **T-1.1 (L, CL)** OS Mode provisioning flow: Settings surface explaining
  the device-owner grant (`adb shell dpm set-device-owner ...`), what it
  enables, how to revoke; state machine around the existing
  `DeviceOwner.isDeviceOwner()`. The *explanatory copy* runs the
  clinical-wordlist gate.
- **T-1.2 (M, CL)** Sunset package suspension: when OS Mode is active and the
  sunset window opens, `setPackagesSuspended` on the user's chosen doomscroll
  list (reuse `DoomscrollList`); unsuspend at window close and on every
  failure path (crash-safe re-entry: suspension state must be re-derived from
  the window on process start, never persisted as the source of truth).
  Escape hatch: the existing 30s typed dwell unlocks early.
- **T-1.3 (M, MM)** OS Mode status card on Settings → About: current grant
  state, suspended-now list, next window, revoke instructions.
- **T-1.4 (M, MM)** Quick Settings tiles ×4 (TileService): Sunset-now,
  Going-Light toggle, Release-my-batch-now, Breathe (opens the breathing
  surface). No new permissions.
- **T-1.5 (S, MM)** PreHome "morning protection" toggle: feeds gated for
  user-set N minutes after first unlock (reuses friction machinery; CONCEPT
  3.3 morning protection).

### Phase 2 — Social Connection subsystem (v0.71) — biggest unbuilt evidence block
- **T-2.1 (L, CL)** People-first favorites: replace/augment app favorites
  with *person* favorites (contact + preferred channel); "Talk to Mom" as a
  home-surface intent that launches the right app's conversation. Contacts
  permission with full degraded mode. (Verduyn 2017; Holt-Lunstad 2010.)
- **T-2.2 (M, CL)** Reconnection prompts: on-device cadence detection from
  call-log metadata (timestamps + contact only, never content; READ_CALL_LOG
  is F-Droid-fine, Play-hostile — document in the Play-declarations section
  of RELEASING.md). "You and Priya used to talk weekly; it's been 6 weeks."
  Gentle, dismissible, never repeated after dismissal for that contact-month.
  (Liu et al. JPSP.) Copy runs the clinical gate.
- **T-2.3 (M, MM)** Passive-vs-active meter: per social app, foreground time
  (existing usage sensing) split by input activity heuristic; weekly ratio
  in the report, framed as reflection. (Verduyn 2017.)
- **T-2.4 (S, MM)** Call-over-text nudge: when composing to a favorite is
  detected as the launch intent, offer "call instead?" once per contact-day.
  (Kumar & Epley 2021.)

### Phase 3 — Notification intelligence (v0.72)
- **T-3.1 (M, MM)** Attention receipts: weekly "who interrupted you"
  attribution from the existing held-notification journal (app × count ×
  human/machine tier), demoted-supporting-cast framing per CONCEPT 6.3.
  (Michie 2009, self-monitoring.)
- **T-3.2 (M, CL)** Marketing classifier v1: deterministic heuristics first
  (sender==app + category extras + promo-keyword list, on-device, auditable);
  marketing lands silent in digest, never buzzes. A tiny on-device model is
  explicitly out of scope until the heuristic's miss-rate is measured (test-
  before-select). (Mehrotra; Fitz 2019.)
- **T-3.3 (S, MM)** Notification sound classes: two bundled sounds
  (human/machine), applied to the digest and breakthrough channels; <65dB
  design note in-repo. (CONCEPT 3.8.)

### Phase 4 — Circadian completion: CBT-I (v0.73) — heaviest clinical gate
- **T-4.1 (L, CL)** Sleep diary auto-draft from existing screen-state sleep
  windows + one-tap morning confirm (Espie 2019 diary structure).
- **T-4.2 (L, CL)** Stimulus-control + sleep-restriction guidance modules:
  content surfaces with strict "wellness, not medical device" framing;
  **every word passes the clinical gate and joins the R-row sign-off list**
  before shipping to anyone. (Freeman 2017; Scott 2021.)
- **T-4.3 (S, MM)** Wind-down integration: CBT-I wind-down slot links the
  existing sunset/breathing surfaces.

### Phase 5 — Work/Life boundary engine (v0.74)
- **T-5.1 (M, CL)** Context profiles: named profiles (Work/Home/Sacred)
  bundling launcher favorites, batch schedule, friction list, sunset window;
  manual switch first (automation later, if ever). (Barber & Santuzzi 2015.)
- **T-5.2 (S, MM)** Focus session: Pomodoro-style block wiring existing
  suppression + a "dock the phone" ritual prompt (Ward 2017).

### Phase 6 — Finish the scaffolds (v0.75)
- **T-6.1 (M, MM)** G-28 whisper.cpp vendoring + JNI wiring — **spec already
  written** (MINIMAX_TASK_G28_whisper_cmake.md, delivered 2026-08-25).
- **T-6.2 (M, CL)** Voice-journal consent flow + AudioRecord capture +
  model-download consent UI (the half deliberately excluded from T-6.1).
- **T-6.3 (M, CL)** G-6 push-up gate camera pipeline — **decision required
  first: MediaPipe Pose vs custom TFLite.** Recommendation: MediaPipe
  (Apache-2.0, F-Droid-compatible, maintained); verify no Play-Services
  transitive dep before vendoring — that check is the task's step 1.

### Phase 7 — Attention facts (v0.76)
- **T-7.1 (M, MM)** ACDP scorecards: static in-repo dataset mapping ~50
  common apps to the CHI 2023 ACDP typology (infinite scroll, autoplay,
  variable reward…); shown on app long-press and in the friction picker
  ("this app uses 4 of 11 capture patterns"). Data file is auditable,
  PR-updatable, no network.

### Phase 8 — OS surface #2: the keyboard (v2.0 track, unscheduled)
- **T-8.1 (CL, research-first)** IME evaluation memo: license audit of
  FlorisBoard/OpenBoard forks, typing-rhythm-metadata feasibility (BiAffect),
  maintenance cost. **No code until the memo passes the test-before-select
  bar.** This is deliberately the last build item: biggest lift, and the
  evidence (typing dynamics → mood) is per-user correlational, weaker than
  everything above it.

### Phase 9 — Ship it for real (M6, mostly user actions)
- **T-9.1 (user)** Generate the signing key per RELEASING.md §1; add the four
  CI secrets. Everything before this is debug-signed and Play-Protect-hostile.
- **T-9.2 (user + clinician)** Clinical sign-off on R2/R3/R4/R6 — the hard
  gate for real users; grows with every Phase-2/4 copy task above.
- **T-9.3 (user)** G-36 two-week live batching log on a real device
  (docs/qa/real-2-week-log.md — currently does not exist).
- **T-9.4 (S, MM)** Tier-2 ROM decision memo (Part 3, item 6).
- **T-9.5 (user)** F-Droid submission (fdroiddata merge request).
- **T-9.6 (M, CL)** Research mode: opt-in anonymized aggregate export with
  pre-registered hypothesis, per CONCEPT 3.10 and PLAN M6.

### Explicitly rejected (so they stay rejected)
App store with evidence bar (separate product) · blanket blocking · streaks/
gamification · chatbot-as-therapist · cross-person mood models (Müller 2021)
· GPS sensing · blue-light-filter claims · any Tier-2 build work.

---

## Part 6 — MiniMax orchestration protocol

The division of labor that makes a slower model reliable:

1. **Claude owns**: this plan, per-task specs, architecture/safety/native
   tasks (CL), all review and merge decisions.
2. **MiniMax owns**: MM tasks only, exactly one at a time, each from a full
   spec in the G-28 format — which is the contract format:
   - Context block: the 3–4 project laws that CI will enforce on this task
   - "What's already built (don't redo)" with exact file paths
   - "What's actually missing" including any latent bugs found while
     writing the spec (the spec-writing pass doubles as a pre-review)
   - Numbered steps with current code quoted inline
   - Verification: the exact `./gradlew` commands; "done" = green, not
     "compiles"
   - "What NOT to do" — scope fence, no version bumps, no PRs, push branch
     and report SHAs
3. **Per-task loop**: Claude writes spec → MiniMax executes on a feature
   branch → MiniMax reports branch + SHAs + verification output → Claude
   reviews the diff (not the report) → fix-ups or merge → next task.
4. **Never give MiniMax**: anything touching clinical wording, DeviceOwner/
   security surfaces, native CMake beyond an existing pattern, or any task
   whose acceptance criteria can't be expressed as commands it can run.

Next spec to generate on request: **T-1.3, T-1.4, or T-1.5** (Phase 1 MM
tasks — but note T-1.3 depends on T-1.1/T-1.2 landing first, so **T-1.4
(QS tiles) or T-1.5 (morning protection) are the correct first handoffs**;
they depend on nothing in flight).
