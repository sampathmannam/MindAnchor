# MindAnchor Program 1: Scientific Foundation — Approved-Scope Design

**Status:** approved scope, 2026-08-29. Derived from
[`2026-08-28-mindanchor-mental-health-os-design.md`](2026-08-28-mindanchor-mental-health-os-design.md)
§4.4, §10, §12.3, and §13.2, and built on the completed Program 0 spine
([`../plans/2026-08-28-program-0-continuity-proof.md`](../plans/2026-08-28-program-0-continuity-proof.md)).

## 1. What Program 1 is

Program 1 builds the **record-keeping substrate** that a later N-of-1 study
needs, and nothing else. It adds no sensing, no inference, no intervention,
and no clinical interpretation.

Concretely it delivers six things:

1. An **evidence protocol registry** — a versioned catalogue in which no
   protocol can exist without a complete evidence contract (§4.4).
2. An **immutable, append-only research ledger** — hash-chained, blocked
   against `UPDATE`/`DELETE` at the database level, carrying confounders
   and provenance (§10.4).
3. The **morning research measure**, preserved exactly as Program 0
   shipped it and integrated into the new substrate (§10.3).
4. A **frozen machine-readable data dictionary** (§12.3).
5. **Versioned research exports** that carry their own dictionary and are
   independently verifiable (§12.3).
6. **Complete provenance** — protocol, model, rule, app, transformation,
   missing-data, sensor-gap, device-change, and study-phase (§10.4, §12.3).

Program 1's smallest useful vertical loop is:

> Register the evidence catalogue → open the first study phase → write a
> Journal entry, a morning measure, and a research-log event → have every
> one of them attributed to a phase and chained in the ledger → back the
> new rows up inside the Program 0 encrypted checkpoint → restore them on
> a replacement phone with the same content hash → export a versioned,
> self-describing, independently verifiable research file.

## 2. What Program 1 explicitly does not do

These are hard boundaries, not deferrals of convenience.

| Excluded | Where it belongs |
| --- | --- |
| Signal freshness, feature windows, personal baselines, exercise suppression, state estimates | Program 2 |
| Wearable ingestion or interpretation of any kind | Program 2 |
| Explaining a detected opportunity, or suggesting a protocol | Program 3 |
| Delivering, playing, timing, or automating a protocol | Programs 4–5 |
| Condition-specific skill modules | Program 6 |
| Preregistration, analysis execution, CENT/SCRIBE report generation | Program 7 |

And these are never in scope for MindAnchor at all: diagnosis, clinical
prediction, efficacy claims, autonomous control, medication advice, crisis
prediction, clinical scoring or thresholding, and semantic interpretation
of journal text.

**Journal authorship is untouched.** `JournalEntry.body`,
`StructuralContextExtractor`, and the `journal_entries` / `journal_context`
tables are not modified by Program 1. Journal context stays structural only
(entry kind, local date, word count, user-authored title).

**Program 0 behaviour is preserved.** Phone, SMS, WhatsApp and
always-open apps keep exactly the availability Program 0 gave them.
Restoring a Program 0 (snapshot format version 1) checkpoint onto a
Program 1 build must still verify — §10 below is the mechanism that makes
that true rather than hoped for.

## 3. Architecture

Six collaborating pieces, all local, all offline, none of them networked:

```
EvidenceProtocolCatalog ──► EvidenceProtocolRegistry ──┐
                                                       │  version vector
ProvenanceVersions ────────────────────────────────────┤
TransformationRegistry ────────────────────────────────┤
MissingDataPolicy ─────────────────────────────────────┘
                            │
                            ▼
                  ResearchProvenanceCoordinator
                    (opens study phases,
                     writes provenance events)
                            │
                            ▼
   ResearchLedgerRepository ──► research_ledger_events  (append-only, chained)
                            └─► study_phases            (append-only)
                                        │
                                        ▼
              ContinuitySnapshot v2 ──► encrypted checkpoint ──► restore
                                        │
                                        ▼
                    ResearchExport v2 (+ frozen data dictionary)
```

New Kotlin lives in `org.mindanchor.research` (flat, like
`org.mindanchor.continuity`); the two new Room entities and their DAO live
in `org.mindanchor.data.db` beside `JournalEntities.kt`.

## 4. Evidence protocol registry

### 4.1 The contract

`EvidenceProtocol` carries every field §4.4 requires, and the registry
refuses to register a protocol that is missing any of them:

| Field | Type |
| --- | --- |
| `id` | stable slug, e.g. `cyclic-sighing` |
| `version` | integer, 1-based |
| `targetState` | observable target state, plain language |
| `intendedPopulation` | who it is for |
| `exclusions` | non-empty list of "not established for" statements |
| `evidenceSources` | non-empty list of `EvidenceSource` |
| `mechanism` | the proposed mechanism |
| `expectedOutcome` | what is expected to change |
| `eligibilityRules` | non-empty list |
| `contraindicationRules` | non-empty list |
| `steps` | non-empty ordered list of `ProtocolStep` |
| `permittedModalities` | non-empty set of `Modality` |
| `maxDurationSeconds` | positive |
| `stopRules` | non-empty set of `StopRule` |
| `cooldownSeconds` | non-negative |
| `outcomeWindowSeconds` | positive |
| `successInterpretation` | non-clinical, self-referenced |
| `clinicalReviewStatus` | `ClinicalReviewStatus` |
| `userFacingExplanation` | plain language, no efficacy promise |

`EvidenceSource` is `(title, citation, reference, strength, sourceType)`.
`title` is held apart from the prose `citation` so a test can check it
against `docs/research/22-research-index.md`, this repository's record of
what has actually been verified. That check exists because a fabricated
paper title once passed a whole suite that only asserted DOIs and enum
values.

### 4.2 Evidence hierarchy and exclusions

`EvidenceStrength`, strongest first, exactly as §4.4 orders it:

1. `CLINICAL_GUIDELINE_OR_SYSTEMATIC_REVIEW`
2. `RANDOMIZED_OR_CONTROLLED_TRIAL`
3. `VALIDATED_TREATMENT_MANUAL`
4. `MECHANISTIC_STUDY`
5. `EXPERT_BOOK_CONSISTENT_WITH_STRONGER_EVIDENCE`

`EvidenceSourceType` additionally names what is *excluded*: `BLOG`,
`INFLUENCER`, `MARKETING`, `AI_GENERATED`. A protocol carrying any
excluded source type fails validation. This is enforced by a validation
rule with its own test, not by convention.

### 4.3 Versioning and immutability

A protocol definition is content-hashed (`definitionSha256`). The catalogue
as a whole has a `catalogSha256`. Both are frozen by tests. Changing any
field of a registered protocol **without bumping its `version`** fails the
build. Bumping the version produces a *new* record — the old
`(id, version, definitionSha256)` triple stays in the ledger forever — and
starts a new study phase, because the catalogue hash is part of the version
vector (§6).

### 4.4 Seeding policy

The hard rule: **only citations already verified in this repository, or
primary/authoritative sources. Never fabricate evidence. If a source cannot
be verified, implement the registry capability without seeding that
protocol.**

The repository's own audit,
[`docs/research/23-citation-audit.md`](../../research/23-citation-audit.md),
is the source of truth for what is verified.

**Seeded (1 protocol):**

- `cyclic-sighing` v1 — the five-minute cyclic-sighing (physiological sigh)
  breathing practice.
  - Outcome evidence: **Balban MY et al. (2023)**, *Cell Reports Medicine*
    4(1):100895, DOI [10.1016/j.xcrm.2022.100895](https://doi.org/10.1016/j.xcrm.2022.100895)
    — randomised controlled comparison, 5 minutes/day for 28 days.
    Strength `RANDOMIZED_OR_CONTROLLED_TRIAL`. Already cited in
    `friction/BreathingProtocol.kt` and listed as verified in the audit.
  - Mechanism evidence: **Bernardi L et al. (2001)**, *J. Hypertens.*
    19(12):2221-2229, DOI [10.1097/00004872-200112000-00016](https://doi.org/10.1097/00004872-200112000-00016)
    — slow breathing, chemoreflex and baroreflex sensitivity. Strength
    `MECHANISTIC_STUDY`. Also already cited and audited.
  - **Two kinds of number, never conflated.** `maxDurationSeconds = 300`
    is the trialled dose (`22-research-index.md`: "5 min/day x 28 days").
    The step durations are `friction/BreathingProtocol.kt`'s own constants
    (2 s nasal inhale, 1 s sip inhale, 6 s mouth exhale) and are **not**
    the trialled durations: `12-breathing-protocols-comparison.md` records
    the trialled cycle as roughly 3–4 s + 1–2 s + 6–10 s, about 10–20 s in
    total, so MindAnchor's cycle sits at or below the low end of every one
    of those ranges. `cooldownSeconds`, `outcomeWindowSeconds`,
    `stopRules`, `exclusions` and `contraindicationRules` are
    **conservative operational defaults, not findings** — no trial reports
    a cooldown or a stop rule, and §4.4 requires them anyway. The KDoc
    says which is which, because presenting the second kind as the first
    would be the same dishonesty as a fabricated citation.
  - `clinicalReviewStatus = NOT_REVIEWED`, because
    [`docs/CLINICAL_REVIEW.md`](../../CLINICAL_REVIEW.md) still reads
    "not yet reviewed by a clinician". The registry records the truth; it
    does not upgrade it.

**Deliberately not seeded, with reasons:**

| Candidate | Why not |
| --- | --- |
| Symmetric slow-paced breathing (6 breaths/min) as an *outcome* protocol | The audit records the outcome literature as mixed and the cited review as unverified. Bernardi 2001 supports the *mechanism* only. Seeding it would be an efficacy claim the repository cannot support. |
| Self-compassion micro-moment (Neff 2003, Linardon 2020, Liu 2023) | The app's implementation is a rotation of the *user's own* phrases. It has no fixed steps and no fixed modality, so it cannot satisfy §4.4's contract without inventing a protocol the evidence does not describe. |
| Behavioural activation (Dimidjian 2006) | A real RCT, but its steps and dose are defined nowhere in this repository, so the substantive half of a §4.4 contract could only be filled in by inventing the protocol itself. Its cooldown and stop rules would be conservative defaults exactly as cyclic sighing's are; that was never the disqualifier. §13.7 makes it its own protocol-evidence project (Program 6). |
| The friction gate's single breathing cycle | The gate plays one 9-second cycle. `BreathingProtocol`'s own KDoc already states this is "a *trigger*, not a dose" and is not the Balban dose. It is deliberately not registered as an evidence protocol. |

One complete, honestly-evidenced protocol plus the rejection tests proves
the registry. A registry padded with protocols the evidence does not
support would prove the opposite.

## 5. Immutable research ledger

### 5.1 Row shape

`research_ledger_events`:

| Column | Meaning |
| --- | --- |
| `id` | the event hash — content-addressed, which is what makes restore duplicate-free |
| `sequence` | 1-based, strictly increasing, contiguous |
| `kind` | `LedgerEventKind` name |
| `occurredAt` | when the recorded thing happened |
| `recordedAt` | when the row was written; never rewritten |
| `localDate` | local date of `occurredAt`, the join key for daily analysis |
| `studyPhaseId` | the phase in effect at `recordedAt` |
| `sourceDeviceId` | which phone wrote it |
| `note` | the person's own words, verbatim, at most 500 characters, never interpreted |
| `payloadJson` | canonical JSON for system-recorded events |
| `previousEventHash` | empty string at sequence 1 |
| `eventHash` | SHA-256 over the canonical event plus `previousEventHash` |

### 5.2 Event kinds

Self-reported confounders (§10.4) — user-entered, never inferred:

`SHIFT_SCHEDULE`, `EXERCISE`, `ILLNESS`, `CAFFEINE`, `MEDICATION_CHANGE`,
`LIFE_EVENT`, `ADVERSE_OR_UNINTENDED_EFFECT`.

System-recorded provenance (§10.4, §12.3):

`STUDY_PHASE_STARTED`, `PROTOCOL_VERSION_REGISTERED`,
`APP_VERSION_CHANGE`, `RULE_VERSION_CHANGE`, `MODEL_VERSION_CHANGE`,
`TRANSFORMATION_VERSION_CHANGE`, `MISSING_DATA_POLICY_CHANGE`,
`INSTRUMENT_VERSION_CHANGE`, `DICTIONARY_VERSION_CHANGE`,
`DEVICE_CHANGE`, `SENSOR_GAP`.

`MEDICATION_CHANGE` records only *that* a change happened plus the
person's own note. Nothing in MindAnchor reads, advises on, or reacts to
it. The entry surface says so in plain words.

`SENSOR_GAP` is **capability without a seeded detector**: Program 1 has no
sensors, so no production code path records one. The kind is defined,
serialised, hashed, backed up, restored, exported and tested, so Program 2
can record gaps without a schema change and without reinterpreting history.
This is the same discipline §4.4's seeding policy applies to protocols.

### 5.3 Append-only, enforced three ways

1. **API**: `ResearchDao` exposes only `@Insert(onConflict = IGNORE)` and
   read queries. A JVM test asserts by reflection that the DAO declares no
   `@Update`, no `@Delete`, and no `UPDATE`/`DELETE` query string.
2. **Database**: `MIGRATION_6_7` installs `BEFORE UPDATE` and
   `BEFORE DELETE` triggers on both new tables that `RAISE(ABORT, …)`. An
   instrumented test proves a raw `UPDATE` and a raw `DELETE` are both
   rejected.
3. **Chain**: each event links to the previous event's hash, so an edit
   that somehow bypassed 1 and 2 is still detectable. `LedgerChain.verify`
   returns a typed verdict; the export carries it.

### 5.4 Why a hash chain, and exactly what it proves

The ledger is the object a future research report rests on. "Append-only
because we only call insert" is a claim; "append-only because sequence
`n`'s hash covers sequence `n−1`'s hash" is a property a reader can check
against a file we handed them. It costs one SHA-256 per row.

Scoped honestly, because an overstated provenance claim is worse than
none:

| Tampering | Detected? |
| --- | --- |
| Edit, delete, reorder, or insert anywhere in the interior | Yes, by the chain alone |
| Accidental corruption | Yes, by the chain alone |
| Truncating the newest events | Only against a count recorded outside the ledger |
| Re-linking the whole file from scratch | No — see below |

Tail truncation is the gap that matters most here: drop the last *k*
rows and what remains is a shorter but perfectly self-consistent chain.
In a self-experiment the subject is also the custodian, so "quietly drop
the last few rows" is the likeliest direction. The count has to be
recorded somewhere the chain is not.

`ContinuityPrefs` holds a **high-water mark**: the largest ledger this
device has ever held, raised after any transaction that grew the ledger —
a research-log entry, and also a study phase opened by a Journal save or
a morning measure — and never lowered. The export compares against it,
and reports `LedgerIntegrity.BROKEN` when the ledger has *shrunk* below
it. Only shrinking is evidence — a mark that is behind means a write that
did not refresh it, or a ledger restored onto a phone that has not
written since, so a missed refresh weakens detection and can never raise
a false alarm.

The mark is also **carried in the export**, as `ledgerHighWaterCount`.
Without it a recipient could only take the app's verdict on trust: every
other integrity field in the file is computed from the list the file
itself contains. With it they can reproduce the verdict, and tell the two
failures apart — a chain that verifies while the count sits below the
mark is a truncation, not a corrupted chain, and those want different
responses. A zero means the device had no mark to report and is evidence
of nothing.

The anchor the export *carries* (`ledgerHeadHash`, `ledgerEventCount`) is
derived from the very list the file contains, so on its own it tells a
recipient only whether the file changed after they received it. That is
worth carrying, and it is not the same guarantee.

A custodian who re-links every event is not detectable by any
self-contained file. A recipient who wants that guarantee records the
head hash at handover; the chain then tells them whether the file they
hold is the file they were given.

### 5.5 Appending is a read-modify-write

Read the head, link, insert. Two concurrent appends that both read the
same head either land two rows at one sequence — permanently `BROKEN`,
and §5.3's triggers mean it cannot be repaired — or, for byte-identical
content, silently collapse to one row under `INSERT OR IGNORE`.
`ResearchLedgerRepository` runs the whole sequence inside a single Room
transaction, and `LedgerChain`'s KDoc states the requirement so a second
caller cannot arrive without seeing it. `LedgerChain.link` additionally
refuses a sequence below 1 or a note over `MAX_LEDGER_NOTE_LENGTH`,
because a row that violates either can never be deleted.

## 6. Study phases and the provenance version vector

### 6.1 The vector

`ProvenanceVersions` composes everything that could change how a record is
produced or interpreted:

| Component | Program 1 value | Source |
| --- | --- | --- |
| `appVersionCode` / `appVersionName` | from `PackageInfo` | build |
| `protocolCatalogSha256` | catalogue content hash | §4.3 |
| `ruleSetVersion` | `rule-set-none-v1` | Program 1 ships no decision rules |
| `modelSetVersion` | `model-set-none-v1` | Program 1 ships no models |
| `transformationSetVersion` | hash of the transformation registry | §7 |
| `missingDataPolicyVersion` | `missing-data-v2` | §7 |
| `instrumentVersion` | `morning-v1` | `MorningMeasure.INSTRUMENT_VERSION` |
| `dictionaryVersion` | `mindanchor-research-v2` | §8 |
| `sourceDeviceId` | `DeviceIdentityStore.id()` | Program 0 |

`rule-set-none-v1` and `model-set-none-v1` are not placeholders. They are
honest statements that this build ships no rules and no models. When
Program 2 adds the first rule set, the constant changes, a new study phase
opens automatically, and the test that pins this behaviour proves it did.

### 6.2 Phase rules

`study_phases` is append-only and has **no `endedAt`**: a phase runs until
the next phase starts. Writing an end timestamp onto a historical row
would be a mutation of history, which §10.4 forbids.

- Phase 0 opens the first time any research record is written.
- Any difference in the vector opens a new phase, with `reason` naming the
  first differing component.
- The phase in effect at time *T* is the last phase with
  `startedAt <= T`. Records are attributed by timestamp, which is why the
  `morning_measures` and `journal_entries` tables need no new column and
  are left exactly as Program 0 shipped them.

### 6.3 Where phases are opened

`ResearchProvenanceCoordinator.ensureCurrentPhase(now)` is called
immediately before a research record is written — from
`ResearchLedgerRepository.record` and `MorningMeasureRepository.save`
inside the same transaction as the write, and from
`JournalRepository.create` **before** the entry, fail-soft.

Before rather than after, because `phaseAt` is inclusive of `startedAt`:
running it first with the entry's own `now` guarantees the entry falls
inside a phase, where running it after would leave an entry timestamped
before the phase that claims to cover it. Fail-soft, because a person must
never lose their words to a provenance failure — the exception is logged
and swallowed, and an entry with no phase is honest (it simply predates
any recorded phase) in a way a mis-attributed one would not be. An
instrumented test proves the entry still saves when every provenance call
refuses.

It is deliberately **not** called from app startup. Two reasons:

1. Program 0's rule that ordinary offline startup performs no work it does
   not have to.
2. A replacement phone must have an empty ledger when it restores. Opening
   phase 0 at startup would write local rows before the restore, the
   restore preflight would block, and the re-captured content hash would
   no longer match the backup. Opening phase 0 lazily means the correct
   sequence — install, restore, *then* the first local write appends a
   `DEVICE_CHANGE` phase onto the restored chain — happens by
   construction.

`DEVICE_CHANGE` therefore ties Program 1 provenance directly to Program 0's
continuity story: the first write after a replacement-phone restore records,
permanently and in-chain, that the history moved to a new device.

## 7. Transformations and missing data

### 7.1 Transformation registry

`TransformationRegistry` lists the raw-to-derived transformations this
build actually performs, each with an id, a version, its input and its
output:

| Id | Version | Input → output |
| --- | --- | --- |
| `structural-context` | `structural-v1` | Journal entry → structural `FACT` rows (kind, local date, word count, user title) |
| `research-export-canonicalisation` | `export-canon-v1` | Research rows → canonically sorted, content-hashed export document |

`transformationSetVersion` is a SHA-256 over the sorted `id@version`
lines — not over the whole record. `input`, `output` and `description` are
documentation, and hashing them would mean a typo fix in a description
opened a new study phase and split the series for a change with no
semantic content. Program 2's feature windows join this list; *that*
change opens a new phase, correctly.

### 7.2 Missing-data policy

`missing-data-v2`: **nothing is ever imputed, interpolated, carried
forward, or filled in; every absence *in the reported window* is
enumerated explicitly with a reason.**

The window qualifier is not a hedge, it is the honest statement of what
the report contains. The window ends at the export date and reaches back
at most `MAX_REPORT_DAYS` (ten years); a record dated more than
`MAX_FUTURE_DAYS` (thirty days) beyond the export date, or further back
than the reach, is excluded from choosing the window *and* from deciding
the reasons inside it, and still appears verbatim in the data.

The two bounds are deliberately asymmetric. A symmetric one was a real
defect: a row dated 2126 instead of 2026 — one digit — sat inside a
century-wide bound, dragged the window forward, and pushed every real
date out of the report. Nothing legitimately records the future; the only
reason to tolerate any of it is a device clock that is behind, which does
not run to years.

`MissingDataPolicy.report(...)` is a pure function producing
`MissingDataRecord(localDate, variable, reason)` for every local date from
the first record to the export date. Reasons:

`NOT_RECORDED`, `BEFORE_FIRST_RECORD`, `CONTEXT_NOT_DERIVED`,
`SENSOR_GAP`, `DEVICE_CHANGE_GAP`.

`BEFORE_FIRST_RECORD` versus `NOT_RECORDED` is the distinction that
matters most: somebody who journalled for two weeks before ever completing
a morning measure did not skip fourteen measures.

There is deliberately **no** reason separating "context extraction was
switched off" from "it ran and produced nothing". The kill switch is a
live user-toggleable flag and nothing records when it was toggled, so
stamping today's flag state onto a six-week-old absence would assert a
cause nobody knows — a fabrication, and a carry-forward of a reason rather
than a value. `CONTEXT_NOT_DERIVED` says only what is known.

`SENSOR_GAP` and `DEVICE_CHANGE_GAP` are capability without a detector,
the same discipline the ledger's `SENSOR_GAP` follows. A test exhausts the
function's reachable inputs to prove Program 1 emits neither.

The report window is bounded (`MAX_REPORT_DAYS`): a span longer than a
personal record could plausibly be is a wrong clock, and it fails loudly
rather than materialising a hundred thousand rows.

An export therefore says how many days have no morning measure and why,
rather than presenting a series that quietly looks complete.

## 8. Frozen machine-readable data dictionary

`ResearchDataDictionary` produces a `DataDictionary` — a serialisable
document describing every variable in the export:

`name`, `dataset`, `type`, `unit`, `allowedValues`, `description`,
`provenance` (`USER_AUTHORED` | `USER_REPORTED` | `DERIVED_STRUCTURAL` |
`SYSTEM_RECORDED`), `missingPolicy`, `transformationId`.

"Frozen" is enforced, not asserted:

- The dictionary's canonical JSON SHA-256 is pinned by a test.
- A checked-in golden file,
  `app/src/test/resources/research/data-dictionary-mindanchor-research-v2.json`,
  makes a change reviewable as a diff.
- A reflection test asserts **every field of every exported research DTO
  has a dictionary entry**, so adding an export field without describing it
  fails the build.

Changing the dictionary requires a new version identifier. Version
`mindanchor-research-v1` (Program 0's) stays in the supported set forever.

## 9. Versioned research export

`ResearchExport` v2 adds, to Program 0's four content lists: the ledger
events, the ledger head hash and integrity verdict, the study phases, the
full protocol registry with its catalogue hash, the transformation
registry, the missing-data report and its policy version, and the frozen
data dictionary with its hash.

- `dataDictionaryVersion` is the single version identifier for both the
  dictionary and the export shape, because §12.3 freezes them together.
  Program 0 files carry `mindanchor-research-v1`; new files carry
  `mindanchor-research-v2`. `decode` accepts both and returns a typed
  `UnsupportedVersion` for anything else.
- `contentSha256` still covers **content only** — never `exportedAt`,
  never the app version — so "did the data change" stays answerable
  independently of "was this exported again". It covers every other
  field, including the protocol registry and the transformation registry
  in full: `transformationSetVersion` hashes `id@version` only, so the
  descriptions — one of which is the file's own statement that MindAnchor
  reads no meaning from journal text — would otherwise be editable in a
  file that still verified. A reflection test fails the build if a field
  is added to the export without deciding whether it is content.
- `dataDictionarySha256` is carried separately, so a dictionary version
  bump does not masquerade as a data change. The hash itself *is* inside
  the content hash, and `verify` separately recomputes it against the
  carried dictionary — together those make a rewritten dictionary
  detectable without making a version bump look like edited data.
- A version-1 document that carries any Program 1 field is rejected as
  corrupt. Its hash covers four content lists, so anything else it
  carries sits outside its own hash; without this, a fabricated ledger
  pasted into a genuine Program 0 export would still verify.
- The export never throws. The document picker has already created the
  file by the time the builder runs, so an escaping exception would leave
  a zero-byte export and no error; a typed `BuildFailed` is what the
  caller can show. The missing-data window runs from the first record to
  the later of "now" and the newest record — a clock behind its own data
  must not produce an empty report under a statement promising a complete
  one — and is clamped to the policy's maximum span so a single corrupt
  date cannot ask for four hundred thousand rows.
- `ResearchExportCodec.verify(export)` recomputes the hash using the
  projection for that file's own version. A Program 0 export written
  months ago stays verifiable by a Program 1 build. Both projections are
  frozen by golden tests.

## 10. Continuity integration

### 10.1 Snapshot format version 2

`ContinuityPayload` gains `researchLedgerEvents` and `studyPhases`.
`ContinuityContract.SNAPSHOT_FORMAT_VERSION` becomes `2`;
`SUPPORTED_SNAPSHOT_FORMAT_VERSIONS` is `{1, 2}`.
`ContinuitySnapshotCodec.decode` accepts any supported version instead of
only the current one, so a Program 0 checkpoint still decodes.

**A version constant moves in the same commit as the shape it names.**
Raising it earlier would stamp `2` on checkpoints whose payload still had
Program 0's ten fields, and every nightly snapshot written in that window
would carry a ten-field hash under a twelve-field stamp — indistinguishable
afterwards from a real version-2 file, with the one discriminator a reader
needs already spent. The same rule governs
`RESEARCH_DICTIONARY_VERSION` and the export shape.

### 10.2 Versioned content hashing — the load-bearing part

Program 0's hash is a SHA-256 over the JSON of the whole payload with
`encodeDefaults = true`. Appending two fields changes that JSON even when
both lists are empty, so a naive addition would make **every existing
Program 0 backup fail its restore verification**.

`ContinuityContentHasher.hash(payload, formatVersion)` therefore projects
the payload onto the field set of the requested version before hashing.
Version 1 serialises exactly Program 0's ten fields; version 2 serialises
all twelve. `RestoreCoordinator` verifies against **the staged snapshot's
own format version**, which `RestoreStateStore` now persists alongside the
expected content hash so a restore interrupted after `DATASTORES_MERGED`
resumes with the right projection.

A golden test freezes the version-1 hash of a fully-populated fixture at
`0425b07482520c0e3841b45b6f576540ba57d012d4023c5c5ebfd9395aac9b7c` — the
value the Program 0 code produces today, captured before any Program 1
change was made.

### 10.3 Backup, restore, resume

- **Capture**: `ContinuitySnapshotRepository` reads both new tables and
  sorts them canonically (ledger by `sequence` then `id`, phases by
  `ordinal` then `id`).
- **Restore**: `mergeRoom` inserts ledger events and phases with
  `INSERT OR IGNORE`. Because the ledger `id` *is* the event hash, a
  second merge of the same events inserts nothing — duplicate-free by
  construction, not by a de-duplication pass.
- **Preflight**: the local-data-empty check gains the two new tables, so a
  replacement-phone restore cannot silently fork a chain.
- **Change ledger**: research writes record `RESEARCH_LEDGER_EVENT` and
  `STUDY_PHASE` rows in `continuity_changes` and request a checkpoint,
  exactly like Journal and morning-measure writes do.

### 10.4 Room v6 → v7 migration

Additive only: two `CREATE TABLE IF NOT EXISTS`, their indices, and the
four immutability triggers. No column is dropped, renamed, or retyped; no
existing row is touched. `fallbackToDestructiveMigration` is not used and
never will be. `MigrationTest` gains a v6-with-Program-0-data walk-forward
proving Journal entries, context rows and morning measures all survive.

## 11. Morning research measure: preserved and integrated

The measure is **not modified**. Same five 1–5 items, same
`morning_measures` table, same `morning-v1` instrument version, same
"a personal research measure, not a diagnosis or clinical score" framing,
no derived total and no threshold anywhere.

Integration is entirely additive:

- Its five variables are described in the frozen data dictionary, with
  their scales, endpoints, and `USER_REPORTED` provenance.
- `instrumentVersion` joins the provenance version vector, so a future
  instrument change opens a new study phase instead of silently
  reinterpreting old days.
- `MorningMeasureRepository.save` calls `ensureCurrentPhase` before
  writing, so every measure falls inside a known phase.
- Days without a measure appear in the missing-data report as
  `NOT_RECORDED`, never as an imputed value.

## 12. Failure behaviour

| Failure | Required behaviour |
| --- | --- |
| Provenance write fails during a Journal save | Fail soft and logged. The phase attempt runs first and its own transaction rolls itself back, so the entry is written regardless and the person never loses words. Same contract as structural-context extraction. |
| Ledger chain verification fails | The export carries `LedgerIntegrity.BROKEN`. Nothing is repaired, deleted, or rewritten. |
| A protocol fails validation | It is not registered, and the failure names the missing field. There is no partial registration. |
| Restoring a Program 0 (v1) checkpoint | Verified against the version-1 projection. Restores normally. |
| An unsupported snapshot or export version | Typed `UnsupportedVersion`, never a thrown exception and never a silent partial read. |
| A version vector component changes | A new study phase opens. History is never reinterpreted. |
| Local research rows exist when a restore is attempted | Preflight blocks the restore, exactly as Program 0 does for Journal data. |

## 13. Verification strategy

- **JVM**: protocol validation and every rejection reason; catalogue and
  protocol hash freezing; chain link determinism, tamper detection and gap
  detection; study-phase decisions for every vector component; missing-data
  policy; transformation registry; dictionary freeze plus the
  every-field-is-described reflection test; export v1 and v2 hash
  projections; snapshot v1 hash freeze; DAO append-only reflection test.
- **Instrumented**: v6→v7 migration with real Program 0 data; the
  `UPDATE`/`DELETE` triggers; ledger repository append and phase opening;
  duplicate-free double merge; full capture → encrypt → decrypt → restore
  → verify round trip carrying ledger and phases; the research-log UI.
- **Gates**: `:app:testDebugUnitTest`, `:app:lintDebug`, `detekt`,
  `:app:connectedDebugAndroidTest`, `:app:koverHtmlReportDebug`, and
  `tools/verify-reproducible-release.sh`.

## 14. Recorded decisions

Conservative in-scope calls made without interrupting the owner:

1. **One seeded protocol, not several.** §4.4's seeding policy plus the
   repository's own citation audit permit exactly one. Recorded with the
   full list of rejected candidates and reasons (§4.4).
2. **`SENSOR_GAP` capability without a detector.** Program 1 owns no
   sensors; a fabricated detector would be Program 2 work and dishonest
   data. The kind exists end-to-end so Program 2 needs no schema change.
3. **`ADVERSE_OR_UNINTENDED_EFFECT` is included.** §12.3 lists an
   adverse-event and unintended-effect log under scientific
   reproducibility. A research substrate that cannot record harm is the
   wrong thing to omit; it costs one ledger kind and one chip.
4. **Confounders are `kind` + optional verbatim note + timestamp.** No
   invented quantitative scales (caffeine in mg, exercise in minutes).
   Presence-per-day is what an N-of-1 covariate needs, and it is what the
   person can honestly supply in seconds.
5. **No `endedAt` on study phases.** Closing a phase would mutate a
   historical row. The next phase's start is the previous phase's end.
6. **Phases open lazily, not at startup.** Required for replacement-phone
   restore correctness (§6.3).
7. **Versioned hashing rather than a snapshot format bump alone.** The only
   way to add rows to the payload without breaking every existing Program 0
   backup (§10.2).
8. **`rule-set-none-v1` / `model-set-none-v1`.** Honest "this build ships
   none" identifiers, not placeholders; they make Program 2's first rule a
   phase boundary automatically.
9. **New user-facing strings will need clinical review before merge.** The
   `clinical-review` workflow gate is a PR gate, and this branch is not
   pushed. Flagged in the final report rather than worked around.
10. **The export consent dialog enumerates categories, not fields.** The
    v2 export carries far more than Program 0's did — the whole research
    log including verbatim notes about illness and medication changes,
    morning-measure ratings, device identifiers, and a day-by-day record
    of what was and was not logged. The dialog names each category, and
    `ResearchExportDisclosureTest` fails the build when a field is added
    to the export without a decision about what the person is told. A
    plaintext file handed to a clinician or an insurer is not
    recoverable, so consent obtained for a smaller dataset must not be
    reused for a larger one.

11. **The missing-data window is chosen by excluding implausible records,
    not by clamping around them.** The first implementation took the
    window from the outermost recorded dates and clamped the result. That
    is not equivalent: one row stamped a thousand years in the future
    dragged the window with it, and the report listed thirty-six thousand
    absences in the thirtieth century while dropping every date the person
    had actually lived — in a document whose own policy statement promises
    that every absence is listed. Excluding a record that is implausibly
    far from the export date, in either direction, keeps the report about
    the person. The excluded row is still exported verbatim in the data
    itself; only the derived report ignores it. This changed the policy's
    meaning, so it is `missing-data-v2` rather than an edit to v1, and the
    provenance vector carries it — a device that recorded under the old
    rule opens a new study phase rather than having its history
    reinterpreted.

## 15. Sources used

Only citations already verified in this repository, plus the reporting
standards named in the parent design's §14:

- Balban MY et al. (2023). *Cell Reports Medicine* 4(1):100895.
  <https://doi.org/10.1016/j.xcrm.2022.100895>
- Bernardi L et al. (2001). *J. Hypertens.* 19(12):2221-2229.
  <https://doi.org/10.1097/00004872-200112000-00016>
- Vohra S et al. (2015). CONSORT extension for reporting N-of-1 trials
  (CENT) 2015 Statement. *BMJ*. <https://doi.org/10.1136/bmj.h1738>
- Tate RL et al. (2016). The Single-Case Reporting Guideline In
  BEhavioural Interventions (SCRIBE) 2016 Statement. *Archives of
  Scientific Psychology*. <https://doi.org/10.1037/arc0000026>

CENT and SCRIBE inform *which fields the ledger keeps* — dates, phases,
versions, confounders, adverse effects, and an explicit missing-data
policy. Program 1 makes no claim of compliance with either standard; §13.8
of the parent design places the report itself in Program 7.
