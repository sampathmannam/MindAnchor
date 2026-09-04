# MindAnchor Mental-Health Operating Layer — Product and Research Design

**Date:** 2026-08-28  
**Status:** Approved design; personal research prototype  
**Audience:** MindAnchor product, engineering, research, and future clinical reviewers

## 1. Executive decision

MindAnchor will not attempt to become an Android operating system or claim to replace mental-health care. It will become an **adaptive mental-health operating layer** built around an Android launcher.

The launcher is the stable control surface. Wearable signals, phone behavior, sleep, the daily Journal, and personal history feed a deterministic decision core. That core may adapt the launcher and automatically run approved protocols. A constrained AI may explain and guide those decisions but may not diagnose, invent interventions, or override safety rules.

The first complete experience will focus on needs commonly associated with borderline personality disorder, emotional escalation, anger, anxiety, depression, sleep disruption, and interpersonal distress. It will combine a transdiagnostic foundation with condition-specific modules. The autonomous version remains a personal prototype until independent clinical and safety review is available.

## 2. Product contract

MindAnchor follows this loop:

> Sense → estimate → explain → intervene → verify → learn

It must:

- Lead the interaction when reliable evidence indicates an intervention opportunity.
- Explain which observable signals caused an intervention.
- Preserve a stable home-screen structure while adapting support around it.
- Keep Phone, SMS, WhatsApp, and user-designated essential or duty applications available.
- Use personal baselines and multiple signals rather than universal thresholds.
- Treat every inferred mental-health state as uncertain and non-diagnostic.
- Run only versioned protocols from the approved evidence registry.
- Limit an episode to two autonomous protocol attempts.
- Retain an always-visible exit and fail open if the app crashes or loses certainty.

It must not:

- Diagnose psychiatric conditions from passive data.
- Recommend or change medication.
- Guarantee that distress, suicidality, anger, anxiety, or depression has resolved.
- Start exposure therapy based only on wearable signals.
- Block essential communication applications.
- Contact third parties through an automated safety plan.
- Allow an LLM to create clinical advice or protocols.

## 3. Intended users and scope

The primary users are adults already experiencing mental-health difficulties. MindAnchor is not positioned as a generic productivity launcher.

The initial prototype supports:

- Emotional and physiological escalation
- Anger and impulsive-action cooling
- Interpersonal distress
- Anxiety and rumination
- Depressive withdrawal and loss of routine
- Sleep debt and circadian disruption
- Daily reflection and personal pattern discovery

The product supports coping, self-management, and research. It is not a medical device, clinician, psychotherapy program, or emergency service.

## 4. System architecture

### 4.1 Signal layer

Inputs include:

- **Wearables:** COROS, Health Connect, heart rate, HRV when reliably available, sleep, activity, workouts, and SpO₂ trends.
- **Phone behavior:** screen rhythm, app-opening patterns, notifications, movement, time of day, and Sunset Mode state.
- **Life context:** daily Journal, quick Notes, intervention history, selected diagnosed conditions, research measures, and schedule context.

Missing or stale data is represented explicitly. Missing data is never treated as evidence of wellness or distress.

### 4.2 Personal baseline engine

The engine models the user’s normal ranges by time of day, day type, work schedule, exercise state, and recent sleep. It must distinguish at least:

- Active exercise or physical work
- Shift-work effects
- Sleep debt
- Possible illness or sensor unreliability
- Ordinary physiological variation
- Persistent unexplained activation

Baseline changes are versioned. The engine must not silently rewrite historical interpretations after an algorithm update.

### 4.3 State estimator

The estimator emits observable, probabilistic states such as:

- Steady
- Vulnerable
- Activated
- Not recovering
- Recovering

It may produce hypotheses such as “possible high arousal” or “possible depressive drift.” It may not emit diagnoses such as “panic attack,” “depressive episode,” or “BPD anger.”

Full-screen takeover requires corroborating signals, a persistence window, valid sensor freshness, and exclusion of exercise or physical activity.

### 4.4 Evidence protocol registry

Every protocol contains:

- Stable identifier and version
- Observable target state
- Intended population and exclusions
- Evidence sources and evidence strength
- Mechanism and expected outcome
- Eligibility and contraindication rules
- Fixed steps and permitted modalities
- Maximum duration
- Stop and cooldown rules
- Outcome window and success interpretation
- Clinical-review status
- User-facing explanation

The initial evidence hierarchy is:

1. Clinical guidelines and systematic reviews
2. Randomized or controlled trials
3. Validated treatment manuals
4. Mechanistic studies
5. Expert books consistent with stronger evidence

Blog-only, influencer, marketing, or AI-generated interventions are excluded.

### 4.5 Autopilot controller

The controller, not the LLM, decides whether to intervene. It is responsible for:

- State transitions
- Trigger confidence and persistence
- Countdown and takeover
- Protocol selection and sequencing
- App restrictions and protected-app bypass
- Pause/resume around essential communication
- Maximum attempts and cooldowns
- Restoration of the ordinary launcher

### 4.6 Constrained AI guide

The AI may:

- Explain why MindAnchor intervened
- Convert approved protocol text into calm, concise guidance
- Adjust language, pacing, and accessibility
- Retrieve relevant Journal context while marking inference as inference
- Answer ordinary product questions

The AI may not:

- Diagnose
- Select or modify a protocol independently
- Invent mental-health guidance
- Interpret raw sensors independently
- Override exclusions, stop rules, or protected applications
- Claim that physiological recovery proves emotional recovery

The deterministic local protocol path remains available when cloud AI or network access fails.

### 4.7 Experience surfaces

The OS-like experience comprises:

- Adaptive launcher
- Full-screen protocol player
- Phone speech and vibration
- Supported watch haptics and notifications
- Sunset and sleep-protection environment
- Journal and Patterns
- Morning research check-in
- Evidence and decision explanations
- Backup-health and restoration interface

## 5. Stable structure, adaptive support

The launcher’s structure remains predictable. Essential communication, search, time/status, MindAnchor home, exit, and settings do not move unexpectedly.

Adaptive behavior includes:

- **Anxiety/high arousal:** reduced stimulation and an approved regulation protocol.
- **Depressive drift:** one achievable activity, simplified choices, daylight/movement timing, and routine reconstruction.
- **Anger/interpersonal escalation:** cooling delay around nonessential communication and a DBT-derived protocol.
- **Sleep debt:** earlier Sunset Mode, grayscale, notification batching, and restrictions on stimulating apps.
- **Steady periods:** calm minimal launcher with no unnecessary interventions.

## 6. Autonomous operating states

### 6.1 Steady

Signals are near the personal baseline. MindAnchor senses passively and leaves ordinary app access unchanged.

### 6.2 Vulnerable

Sleep debt, rhythm disruption, inactivity, or Journal context raises vulnerability. MindAnchor quietly adapts the launcher but does not take over.

### 6.3 Activated

Multiple reliable, non-exercise signals remain outside baseline for the required persistence window. MindAnchor explains the evidence, shows a short countdown, starts the approved protocol, and temporarily blocks nonprotected apps.

### 6.4 Not recovering

The predefined physiological trend does not occur during the first outcome window. MindAnchor stops the ineffective protocol and may run one approved alternative.

### 6.5 Recovering

Signals show a sustained trend toward the individual baseline. MindAnchor completes guidance, restores restricted apps gradually, records the episode, and enters a cooldown.

After two protocol attempts, MindAnchor stops autonomous takeover and maintains only a quiet launcher environment. It does not repeat interventions indefinitely.

## 7. Evidence-backed feature systems

### 7.1 Body and context intelligence

- COROS and Health Connect ingestion
- Camera PPG when appropriate
- Personal baseline engine
- Exercise, illness, and sensor-quality exclusions
- Multisignal state estimation
- Transparent confidence and data freshness

### 7.2 BPD and emotional escalation

- DBT-derived STOP and distress-tolerance flows
- TIPP or other clinically appropriate physiological down-regulation only after evidence and contraindication review
- Opposite-action guidance where applicable
- Interpersonal cooling delay
- DEAR MAN and interpersonal-effectiveness support
- Compassionate recovery and nonjudgmental wording

Digital skills support is not represented as comprehensive DBT or psychotherapy.

### 7.3 Depression and functioning

- Behavioral-activation ladder
- Tiny values-linked actions
- Routine reconstruction
- Daylight and movement timing
- Withdrawal-pattern early warning
- Automatic simplification during vulnerable periods

Passive data may indicate a concerning pattern but cannot diagnose depression.

### 7.4 Anxiety and rumination

- Panic-safe, evidence-reviewed breathing
- Grounding
- Worry postponement
- Cognitive defusion
- Planned exposure support only as an intentional module, never as a passive wearable trigger

### 7.5 Sleep and circadian protection

- Shift-aware sleep debt
- Sleep regularity and timing
- Adaptive Sunset Mode
- Stimulus-control rules
- Reduced evening stimulation
- Morning recovery mode

The design emphasizes evidence-based sleep and CBT-I principles rather than generic sleep-hygiene claims.

### 7.6 Journal, memory, and meaning

The Journal is separate from quick Notes. It has three primary destinations:

- **Today:** a calm daily writing ritual.
- **Entries:** chronological timeline, calendar, search, and rich entries.
- **Patterns:** transparent contextual analysis separated from original writing.

Entries may contain text, photos, audio, activities, and state-of-mind attachments. The original entry remains immutable relative to AI interpretation. Context extraction happens after save and produces tentative structured facts and inferences.

Examples:

- Known fact: “The entry says an argument occurred.”
- Inference: “Interpersonal stress may be contributing.”
- Prohibited conclusion: “The other person intended harm” or “this proves a diagnosis.”

Journal context may influence future state estimates and protocol selection through approved rules. Strong distress language may contribute to an intervention only when combined with eligible context and protocol rules.

### 7.7 Mental-health OS controls

- Stable adaptive launcher
- Notification batching
- App friction and protocol takeover
- Protected essential/duty application list
- Pause/resume around protected communication
- Phone voice, screen pacing, vibration, and supported watch haptics

### 7.8 Evidence and safety governance

- Evidence cards visible to the user
- Versioned protocol and decision rules
- Contraindications and exclusions
- Confidence explanations
- N-of-1 outcome tracking
- Explicit “research-derived, not clinician-reviewed” status

## 8. Journal and contextual data model

Conceptual entities include:

- `JournalEntry`: original content, timestamp, attachments, source device, backup state
- `ContextFact`: directly extractable statement with source span and confidence
- `ContextInference`: tentative interpretation with model/rule version
- `FeatureWindow`: wearable and phone features for a defined time window
- `PersonalBaseline`: expected feature distributions and scope
- `StateEstimate`: state, confidence, evidence, exclusions, and expiry
- `ProtocolDefinition`: versioned evidence contract
- `InterventionEpisode`: trigger, steps, pauses, exits, and outcome windows
- `ResearchObservation`: objective and subjective measures
- `BackupEvent`: encrypted incremental change with integrity metadata

Original user content, extracted facts, and model inferences remain separate so that an inference can be deleted or recomputed without altering authorship.

## 9. Data continuity and phone replacement

Nightly backup alone is insufficient because the phone may be lost or destroyed at any time.

MindAnchor uses:

1. **Incremental encrypted sync** after important changes when network policy allows.
2. **Nightly encrypted full snapshots** to the existing Google Drive backup target for completeness and repair.
3. **Recovery material outside the phone**, using a passphrase or recovery key in addition to account authentication.
4. **Versioned snapshots and integrity checks** to prevent silent corruption from becoming the only copy.
5. **A replacement-phone flow:** install, authenticate, unlock backup, restore, re-pair wearable, verify continuity.

Restored data includes settings, Journal, quick Notes, protected apps, personal baselines, intervention history, evidence versions, morning measures, and research ledger.

High-frequency raw sensor data is retained only for predefined research windows. Derived daily features are retained long term. Retention policy must be documented before collection begins.

Cloud use is limited to personalized MindAnchor functions and encrypted backup. Personal data may not be sold, used for advertising, or used to train external AI models.

## 10. Scientific N-of-1 research design

### 10.1 Research position

MindAnchor is initially a personal hobby/research prototype. Research claims must concern specific interventions and predefined outcomes, not the effectiveness of the entire “mental-health OS.”

The recommended first question is:

> For one adult using MindAnchor, does an adaptive, sleep-aware evening launcher intervention improve sleep regularity and next-day functioning compared with baseline?

This question is narrower, safer, and more measurable than claiming improvement in BPD, anxiety, anger, and depression simultaneously.

### 10.2 Study stages

1. Observational baseline
2. Preregistered protocol and analysis
3. Planned single-case experimental phases
4. Immutable research ledger
5. Reproducible analysis
6. Transparent report following N-of-1 or single-case reporting standards

### 10.3 Morning research measure

A check-in appears once after the first morning unlock and remains separate from autonomous interventions. It targets completion in under 30 seconds and records:

- Mood
- Anxiety/tension
- Anger or urge to react
- Energy and ability to function
- Perceived sleep quality

Wearable and phone data are objective outcomes. The morning measure supplies independent subjective outcomes. Validated weekly measures may be added only after confirming validation, licensing, population fit, scoring, burden, and interpretation.

### 10.4 Confounders and provenance

The research ledger records:

- Shift or duty schedule
- Exercise
- Illness
- Caffeine where feasible
- Medication changes without giving medication advice
- Major life events where voluntarily journaled
- Sensor gaps and device changes
- App, protocol, rule, and model versions
- Data transformations and missing-data handling

Algorithm changes start a new study phase. Historical decisions are never silently reinterpreted.

### 10.5 Reporting and ethics

The report should follow applicable N-of-1 and single-case standards, including CENT and SCRIBE where relevant. Publication requirements, consent language, ethics review, and journal policies must be checked before submission. A self-experiment does not automatically remove ethics or privacy obligations.

The initial report may be a preregistration, technical report, or preprint. Public clinical-efficacy claims remain prohibited without appropriate study design and review.

## 11. Failure behavior

| Failure or uncertainty | Required behavior |
|---|---|
| Watch disconnected or data stale | Disable full takeover; preserve calm launcher and phone-only features |
| Heart-rate spike or motion artifact | Require persistence and corroboration; reject implausible changes |
| Signals conflict | Remain Steady or Vulnerable; do not force a protocol |
| AI or network unavailable | Use deterministic local protocols and fixed wording |
| First protocol ineffective | Run at most one approved alternative, then stop takeover |
| Protected communication arrives | Open immediately; pause and preserve protocol state |
| Incremental backup fails | Queue encrypted events, retry with backoff, retain verified snapshots |
| Phone destroyed between syncs | Restore through last acknowledged event and disclose the unsynced interval |
| App crashes during takeover | Fail open, restore access, record the episode, and prevent immediate retriggering |
| Algorithm changes during study | Create a new version and study phase; never rewrite history |

## 12. Verification strategy

### 12.1 Software correctness

- Unit tests for baseline calculations and decision tables
- Protocol state-machine and stopping-rule tests
- Backup encryption, corruption, rollback, and restore tests
- AI boundary and malformed-output tests
- Launcher, permission, protected-app, and process-death instrumentation tests

### 12.2 Behavioral and sensor simulation

Synthetic scenarios cover:

- Shift work
- Exercise and physical activity
- Illness-like physiology
- High-arousal episodes
- Low activity and rhythm disruption
- Missing and noisy sensors
- Repeated false positives
- Protected-duty interruptions
- Cloud and phone replacement failure

### 12.3 Scientific reproducibility

- Frozen data dictionary
- Versioned raw-to-feature transformations
- Preregistered outcomes and analysis
- Explicit missing-data policy
- Immutable protocol and model provenance
- Reproducible analysis output
- Adverse-event and unintended-effect log

Autonomous takeover remains disabled until exercise suppression, protected-app access, false-positive simulations, process-death recovery, offline operation, and replacement-phone restoration pass.

## 13. Reliability-first reverse roadmap

MindAnchor must be developed backward from a dependable real-world outcome. It must not begin by building the complete architecture and attempt production hardening afterward.

The delivery sequence is:

1. Production reliability
2. One smallest useful vertical loop
3. Passive observation
4. Advisory intervention
5. Limited low-risk automation
6. High-control automation

Every stage must be useful on a real phone and independently releasable. A later stage cannot begin because its architecture is elegant; it begins only when the preceding stage meets its operational acceptance criteria.

### 13.1 Program 0: production spine and continuity proof

The first implementation program contains no new autonomous mental-health control. It establishes:

- Stable personal release signing and reproducible builds
- Tested upgrade and database-migration paths
- Offline startup and operation
- Process-death and crash recovery
- Feature flags and local kill switches
- Backup-health visibility
- Incremental encrypted Google Drive sync
- Nightly full Google Drive snapshots
- Recovery-key workflow
- Replacement-phone restoration
- Safe schema rollback or forward-repair strategy
- Battery, permission, and background-work verification

Its smallest useful vertical loop is:

> Write Journal entry → preserve original → extract research context → create morning measure → incrementally back up → export structured record → uninstall or lose the test installation → restore on another phone → verify identical history and integrity.

Program 0 is complete only when this loop passes repeatedly on real Android hardware. A successful backup is not evidence of reliability; a successful restore is.

### 13.2 Program 1: scientific foundation

- Evidence protocol registry
- Immutable research ledger
- Morning research measure
- Frozen data dictionary
- Versioned exports
- Protocol, model, rule, and app provenance

### 13.3 Program 2: passive intelligence

- Signal freshness and missingness
- Feature windows
- Personal baselines
- Exercise and physical-activity suppression
- Non-diagnostic state estimates
- Patterns and explanations without intervention

This program must run during normal daily use long enough to quantify data loss, battery cost, sensor noise, and false-positive candidates.

### 13.4 Program 3: advisory intervention

- MindAnchor explains a detected opportunity
- The user manually starts the suggested protocol
- Delivery and outcome windows are recorded
- Detection quality and intervention burden are evaluated

No app blocking or forced takeover occurs in this program.

### 13.5 Program 4: limited low-risk automation

The first automated subsystem is the sleep-aware evening launcher:

- Adaptive Sunset Mode
- Notification quieting
- Grayscale and stimulation reduction
- Protected-app preservation
- Kill switch and immediate rollback

It graduates from passive observation to recommendation and only then to automation.

### 13.6 Program 5: autonomous protocol engine

- Five-state autonomous controller
- Full-screen protocol player
- Multimodal guidance
- Protected-app pause/resume
- Outcome verification
- One alternative protocol
- Cooldowns and fail-open recovery

Autonomous takeover remains disabled until the release gate in Section 12 passes.

### 13.7 Program 6: condition modules

- Evidence-reviewed BPD and emotion-regulation skills
- Depression and behavioral activation
- Anxiety and rumination
- Sleep and circadian support

Each module is its own protocol-evidence and validation project. They are not implemented as one broad “mental health” feature bundle.

### 13.8 Program 7: research execution and review

- Observational baseline
- Preregistration
- Planned single-case phases
- Reproducible analysis
- CENT/SCRIBE-aligned reporting
- Independent clinical, safety, privacy, and publication review before public autonomous use

The recommended first implementation plan is **Program 0: production spine and continuity proof**. The architecture may evolve only as required to make that vertical loop dependable.

## 14. Initial evidence anchors

These sources justify the architecture but do not replace protocol-specific evidence extraction:

- Lindsay JAB et al. “Digital Interventions for Symptoms of Borderline Personality Disorder: Systematic Review and Meta-Analysis.” *Journal of Medical Internet Research* (2024). <https://doi.org/10.2196/54941>
- Stoffers-Winterling JM et al. “Psychotherapies for borderline personality disorder: a focused systematic review and meta-analysis.” *British Journal of Psychiatry* (2022). <https://doi.org/10.1192/bjp.2021.204>
- von Lützow U et al. “Effectiveness of just-in-time adaptive interventions for improving mental health and psychological well-being: a systematic review and meta-analysis.” *BMJ Mental Health* (2025). <https://doi.org/10.1136/bmjment-2025-301641>
- Ter Harmsel JF et al. “Biocueing and ambulatory biofeedback to enhance emotion regulation.” *International Journal of Psychophysiology* (2021). <https://doi.org/10.1016/j.ijpsycho.2020.11.009>
- Shikha S et al. “A Systematic Review on Physiology-based Anxiety Detection using Machine Learning.” *Biomedical Physics & Engineering Express* (2025). <https://doi.org/10.1088/2057-1976/add5fc>
- Kroenke K et al. “The PHQ-9: Validity of a Brief Depression Severity Measure.” *Journal of General Internal Medicine* (2001). <https://pubmed.ncbi.nlm.nih.gov/11556941/>
- Vohra S et al. “CONSORT extension for reporting N-of-1 trials (CENT) 2015 Statement.” *BMJ* (2015). <https://doi.org/10.1136/bmj.h1738>
- Tate RL et al. “The Single-Case Reporting Guideline In BEhavioural Interventions (SCRIBE) 2016 Statement.” *Archives of Scientific Psychology* (2016). <https://doi.org/10.1037/arc0000026>
- Apple. “Get started with Journal on iPhone.” *iPhone User Guide* (accessed 2026-08-28). <https://support.apple.com/guide/iphone/get-started-iph0e5ca7dd3/ios>

## 15. Success criteria

The design is successful when:

- Every autonomous decision is explainable and versioned.
- No protocol can run without a complete evidence contract.
- Essential communication always remains accessible.
- Missing or conflicting data reduces autonomy.
- Journal authorship remains distinct from AI inference.
- A replacement phone can restore the MindAnchor experience and research history.
- Objective and subjective outcomes are independently measurable.
- The first N-of-1 question can be preregistered and reproduced.
- The prototype makes no unsupported diagnostic or treatment claims.
- Each released stage is useful and reliable on real hardware before the next stage begins.
- Backup reliability is demonstrated by repeated replacement-phone restores, not upload success alone.
- Passive and advisory operation precede any high-control autonomous feature.
