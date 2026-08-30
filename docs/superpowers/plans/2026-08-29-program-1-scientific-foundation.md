# MindAnchor Program 1: Scientific Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Add the research substrate a later N-of-1 study needs — a validated evidence protocol registry, an immutable hash-chained research ledger, append-only study phases carrying a complete provenance version vector, an explicit missing-data policy, a frozen machine-readable data dictionary, and a versioned self-describing research export — with every new durable row flowing through Program 0's canonical hashing, encrypted checkpoint, replacement-phone restore, and duplicate-free resume, on a non-destructive Room v6 → v7 migration.

**Architecture:** Keep Program 0's continuity spine exactly as it is and extend it additively. New research logic lives flat in `org.mindanchor.research`; two new append-only Room entities and their insert-and-read-only DAO live in `org.mindanchor.data.db`. Pure decision logic (protocol validation, chain linking, study-phase decisions, missing-data reporting, dictionary construction) is plain Kotlin with no Android dependency, so it is JVM-testable; Room/DataStore wiring is injected as narrow suspend lambdas, the same seam `RestoreCoordinator` and `ContinuityBackupCoordinator` already use. The continuity content hash becomes version-aware so a Program 0 checkpoint still verifies on a Program 1 build.

**Tech Stack:** Kotlin 2.0.21, Android/Compose Material 3, Room 2.6.1, Preferences DataStore 1.1.1, WorkManager 2.9.1, kotlinx.serialization 1.7.3, JUnit 4, Robolectric, AndroidX instrumentation, detekt, Kover, GitHub Actions.

**Global Constraints:**

- Program 1 adds no sensing, no baselines, no state estimation, no wearable inference, and no intervention behaviour. Those are Programs 2–5.
- Program 1 makes no diagnosis, clinical prediction, efficacy claim, autonomous control decision, medication recommendation, crisis prediction, clinical score, or semantic interpretation of journal text.
- `JournalEntry.body`, `StructuralContextExtractor`, `journal_entries` and `journal_context` are **not modified**. Journal context stays structural only.
- `MorningMeasure`, `MorningMeasureEntity`, `morning_measures`, the five items and `morning-v1` are **not modified**. Integration is additive only.
- Only citations already verified in `docs/research/23-citation-audit.md`, or primary/authoritative sources. Never fabricate evidence. If a source cannot be verified, implement the registry capability and do not seed that protocol.
- A version change creates a new immutable record and a new study phase. No historical ledger event, study phase, or protocol registration is ever rewritten, updated, or deleted.
- Program 0 continuity behaviour is preserved: a snapshot-format-version-1 checkpoint must still decode, restore, and verify. Phone, SMS, WhatsApp and always-open app availability is untouched.
- Room migrations stay forward-only. Never add `fallbackToDestructiveMigration`.
- Do not add, modify, or delete the pre-existing untracked `AGENTS.md`.
- Do not push, tag, merge, publish, or release.
- No task is complete because it compiles. Every task states its RED command and expected failure, its GREEN command and expected pass, and commits only after GREEN.

## Program 1 release boundary

The releasable vertical loop is:

1. Open Journal from the launcher and write an entry — the first research write opens study phase 0 and chains a `STUDY_PHASE_STARTED` event plus one `PROTOCOL_VERSION_REGISTERED` event per catalogued protocol.
2. Complete the morning measure — unchanged, now attributed to that phase.
3. Record a research-log event (shift, exercise, illness, caffeine, medication change, life event, or adverse effect) with an optional note in the person's own words.
4. Have every row backed up inside the existing encrypted Drive checkpoint and nightly snapshot.
5. Restore on a replacement phone and get the same canonical content hash, with the ledger chain intact and the first local write recording a `DEVICE_CHANGE` phase.
6. Export a `mindanchor-research-v2` file that carries its own frozen data dictionary, the full protocol registry, every study phase, the whole chained ledger with its integrity verdict, the transformation registry, and an explicit missing-data report.

Program 1 intentionally does not include signal ingestion, baselines, state estimation, protocol delivery, preregistration, or analysis execution.

---

### Task 1: Freeze the Program 0 content hash and pin the Program 1 contract

**Files:**

- Create: `app/src/test/java/org/mindanchor/continuity/ProgramZeroPayloadFixture.kt`
- Create: `app/src/test/java/org/mindanchor/continuity/ContinuityHashVersionTest.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt`
- Test: `app/src/test/java/org/mindanchor/continuity/ContinuitySnapshotCodecTest.kt` (must stay green unchanged)

**Step 1: Confirm the baseline**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: PASS, 1529 tests, 0 failures. If it fails, record the pre-existing failure and stop.

**Step 2: Write the failing version-aware hash test**

`ProgramZeroPayloadFixture.kt` builds one fully-populated Program 0 payload (one journal entry, one context row, one morning measure, one note, one letter, one read date, one frictioned app, one always-open app, one continuity change, and a real `BackupCodec.encode(...)` legacy blob with `savedAt = 1_234L`), passed through `ContinuityContentHasher.sorted`.

`ContinuityHashVersionTest.kt`:

```kotlin
class ContinuityHashVersionTest {

    @Test
    fun `the program zero hash is frozen`() {
        assertEquals(
            "0425b07482520c0e3841b45b6f576540ba57d012d4023c5c5ebfd9395aac9b7c",
            ContinuityContentHasher.hash(ProgramZeroPayloadFixture.payload(), formatVersion = 1),
        )
    }

    @Test
    fun `the default hash is the current format version`() {
        val payload = ProgramZeroPayloadFixture.payload()
        assertEquals(
            ContinuityContentHasher.hash(payload, ContinuityContract.SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(payload),
        )
    }

    @Test
    fun `an unsupported format version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuityContentHasher.hash(ProgramZeroPayloadFixture.payload(), formatVersion = 99)
        }
    }
}
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ContinuityHashVersionTest' --console=plain
```

Expected: FAIL to compile — `hash` has no `formatVersion` parameter.

**Step 3: Make it pass**

In `ContinuityContract.kt`:

```kotlin
object ContinuityContract {
    // Stays 1 here. A version constant moves in the SAME commit as the
    // shape it names — Task 10 raises this to 2 when ContinuityPayload
    // actually gains its fields. Stamping 2 now would mean every nightly
    // checkpoint written before Task 10 carried a ten-field hash under a
    // twelve-field stamp, and no later reader could tell those files apart
    // from real version-2 files.
    const val SNAPSHOT_FORMAT_VERSION = 1
    const val PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1
    val SUPPORTED_SNAPSHOT_FORMAT_VERSIONS = setOf(PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION)

    const val ENVELOPE_FORMAT_VERSION = 1
    const val LATEST_FILE_NAME = "MindAnchor-Continuity-Latest.mab"

    // Likewise: Task 11 raises this to "mindanchor-research-v2" in the
    // commit that changes the export document.
    const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"
}
```

In `ContinuityContentHasher.kt`, add a private `@Serializable data class V1Payload` declaring Program 0's ten fields in Program 0's declaration order, a `projectV1(payload)` mapper, and:

```kotlin
fun hash(payload: ContinuityPayload, formatVersion: Int = ContinuityContract.SNAPSHOT_FORMAT_VERSION): String {
    require(formatVersion in ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS) {
        "unsupported snapshot format version: $formatVersion"
    }
    val canonical = canonicalize(payload)
    val bytes = when (formatVersion) {
        ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION -> json.encodeToString(projectV1(canonical))
        else -> json.encodeToString(canonical)
    }.encodeToByteArray()
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
```

`canonicalize` is the existing sort + `acknowledgedSnapshotId = null` + `normalizeLegacyBackup` block, extracted so both projections share it. The `when` is exhaustive over versions that have a projection and `error()`s otherwise, so a version added to the supported set without a projection fails loudly instead of being hashed against whatever the current shape happens to be.

`V1Payload` is extracted as an `internal` top-level `ContinuityPayloadV1` so the test can read its serialised element names directly rather than inferring the shape from a digest the same code produced.

`normalizeLegacyBackup` gains a KDoc warning: it re-encodes through today's `BackupCodec.Backup`, so appending a field to *that* class changes the content hash of every continuity snapshot ever written, whatever the snapshot format version says.

In `ContinuitySnapshotCodec.decode`, replace the equality check with membership against `SUPPORTED_SNAPSHOT_FORMAT_VERSIONS`. It is behaviour-neutral today (the set holds only 1) and becomes load-bearing in Task 10.

Update `ContinuityContractTest` to assert `SNAPSHOT_FORMAT_VERSION == 1`, `PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION == 1`, `ENVELOPE_FORMAT_VERSION == 1`, `LATEST_FILE_NAME`, `RESEARCH_DICTIONARY_VERSION == "mindanchor-research-v1"`, and that Program 0 is in the supported set.

Before pinning the golden, prove it is genuinely a Program 0 value: write a throwaway test that reimplements Program 0's algorithm from scratch (sort, null the acknowledged id, normalise the legacy backup, serialise `ContinuityPayload` itself, SHA-256) and assert it equals `hash(payload, 1)`. Record the result, then delete the throwaway.

Run:

```bash
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: PASS. Because `ContinuityPayload` has not changed yet, versions 1 and 2 currently hash identically — Step 2's freeze test is what keeps version 1 pinned when Task 10 adds the new fields.

**Step 4: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt app/src/test/java/org/mindanchor/continuity/
git commit -m "test: freeze the Program 0 content hash before Program 1 extends it"
```

---

### Task 2: The evidence protocol contract and its validation

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/EvidenceProtocol.kt`
- Create: `app/src/main/java/org/mindanchor/research/EvidenceProtocolRegistry.kt`
- Create: `app/src/test/java/org/mindanchor/research/EvidenceProtocolRegistryTest.kt`

**Step 1: Write the failing tests**

`EvidenceProtocolRegistryTest` builds a known-valid protocol from a private `validProtocol()` helper and asserts, one test each:

1. `validProtocol()` validates.
2. A blank `targetState`, `intendedPopulation`, `mechanism`, `expectedOutcome`, `successInterpretation`, or `userFacingExplanation` fails with `Invalid` naming that field.
3. An empty `exclusions`, `eligibilityRules`, `contraindicationRules`, `steps`, `permittedModalities`, `stopRules`, or `evidenceSources` fails naming that field.
4. `version < 1`, `maxDurationSeconds <= 0`, `outcomeWindowSeconds <= 0`, `cooldownSeconds < 0` each fail naming that field.
5. A blank `id`, or an `id` that is not lowercase-kebab, fails.
6. An evidence source whose `sourceType` is `BLOG`, `INFLUENCER`, `MARKETING`, or `AI_GENERATED` fails with `Invalid` naming `evidenceSources`, one test per excluded type.
7. An evidence source with a blank `citation` or blank `reference` fails.
8. `definitionSha256` is stable across two calls and changes when any single field changes.
9. `EvidenceProtocolRegistry.of(listOf(valid, valid))` fails for a duplicate `(id, version)`.
10. `EvidenceProtocolRegistry.of(listOf(invalid))` throws `IllegalArgumentException` — there is no partial registration.
11. `catalogSha256` is stable and order-independent.
12. `EvidenceStrength.entries` is in the §4.4 order, strongest first.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*EvidenceProtocolRegistryTest' --console=plain
```

Expected: FAIL to compile — neither file exists.

**Step 2: Make it pass**

`EvidenceProtocol.kt` declares, all `@Serializable`:

```kotlin
enum class EvidenceStrength {
    CLINICAL_GUIDELINE_OR_SYSTEMATIC_REVIEW,
    RANDOMIZED_OR_CONTROLLED_TRIAL,
    VALIDATED_TREATMENT_MANUAL,
    MECHANISTIC_STUDY,
    EXPERT_BOOK_CONSISTENT_WITH_STRONGER_EVIDENCE,
}

enum class EvidenceSourceType {
    PEER_REVIEWED_ARTICLE, SYSTEMATIC_REVIEW, CLINICAL_GUIDELINE, TREATMENT_MANUAL, ACADEMIC_BOOK,
    BLOG, INFLUENCER, MARKETING, AI_GENERATED;

    val isPermitted: Boolean get() = this !in EXCLUDED

    companion object { val EXCLUDED = setOf(BLOG, INFLUENCER, MARKETING, AI_GENERATED) }
}

enum class ClinicalReviewStatus { NOT_REVIEWED, REVIEW_REQUESTED, REVIEWED_WITH_CHANGES, REVIEWED_AND_ACCEPTED }
enum class Modality { VISUAL, AUDIO, HAPTIC, TEXT }
enum class StopRule { USER_STOPPED, MAX_DURATION_REACHED, DISCOMFORT_REPORTED, INTERRUPTED_BY_PROTECTED_APP }

data class EvidenceSource(val citation: String, val reference: String, val strength: EvidenceStrength, val sourceType: EvidenceSourceType)
data class ProtocolStep(val ordinal: Int, val instruction: String, val durationSeconds: Int)
data class EvidenceProtocol(/* the 19 fields in §4.1 of the design */)
```

`EvidenceProtocolRegistry.kt`:

```kotlin
sealed class ProtocolValidation {
    data object Valid : ProtocolValidation()
    data class Invalid(val field: String, val reason: String) : ProtocolValidation()
}

class EvidenceProtocolRegistry private constructor(val protocols: List<EvidenceProtocol>) {
    fun find(id: String, version: Int): EvidenceProtocol?
    fun latest(id: String): EvidenceProtocol?
    val catalogSha256: String
    companion object {
        fun validate(protocol: EvidenceProtocol): ProtocolValidation
        fun definitionSha256(protocol: EvidenceProtocol): String
        fun of(protocols: List<EvidenceProtocol>): EvidenceProtocolRegistry
    }
}
```

`validate` checks fields in a fixed order and returns the first failure. `definitionSha256` is the SHA-256 of `Json { encodeDefaults = true }` over the protocol with its steps sorted by `ordinal`, modalities and stop rules sorted by `name`, and evidence sources sorted by `reference`. `catalogSha256` is the SHA-256 over the sorted list of `"$id@$version:$definitionSha256"` lines. `of` throws `IllegalArgumentException` naming the offending protocol on the first invalid entry or duplicate key.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*EvidenceProtocolRegistryTest' --console=plain
```

Expected: PASS.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/EvidenceProtocol.kt app/src/main/java/org/mindanchor/research/EvidenceProtocolRegistry.kt app/src/test/java/org/mindanchor/research/EvidenceProtocolRegistryTest.kt
git commit -m "feat: refuse to register a protocol without a complete evidence contract"
```

---

### Task 3: Seed the catalogue from verified repository citations only

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/EvidenceProtocolCatalog.kt`
- Create: `app/src/test/java/org/mindanchor/research/EvidenceProtocolCatalogTest.kt`
- Modify: `docs/research/22-research-index.md` (add the registry cross-reference row)

**Step 1: Write the failing tests**

`EvidenceProtocolCatalogTest`:

1. `EvidenceProtocolCatalog.registry.protocols` has exactly one entry, `cyclic-sighing` version 1.
2. Every catalogued protocol passes `EvidenceProtocolRegistry.validate`.
3. Every evidence source's `sourceType.isPermitted` is true.
4. Every evidence source's `reference` starts with `https://doi.org/`.
5. The cyclic-sighing protocol cites exactly `https://doi.org/10.1016/j.xcrm.2022.100895` at `RANDOMIZED_OR_CONTROLLED_TRIAL` and `https://doi.org/10.1097/00004872-200112000-00016` at `MECHANISTIC_STUDY`.
6. Its step durations match `BreathingProtocol.INHALE_MILLIS`, `SIP_MILLIS` and `EXHALE_MILLIS` converted to seconds, and its `maxDurationSeconds` is `300`.
7. Its `clinicalReviewStatus` is `NOT_REVIEWED`.
8. `userFacingExplanation` contains no efficacy verb — asserted against the list `listOf("will reduce", "will improve", "cures", "treats", "guarantees", "proven to")`.
9. The catalogue hash is frozen: `assertEquals("<value>", EvidenceProtocolCatalog.registry.catalogSha256)`.
10. No protocol id in the catalogue appears in `EvidenceProtocolCatalog.DELIBERATELY_NOT_SEEDED`, and that set contains the four documented ids.

For test 9, write the assertion against `"PENDING"` first, run, and paste the reported actual value in. That is the freeze value; a later edit to any protocol field without a version bump then fails this test.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*EvidenceProtocolCatalogTest' --console=plain
```

Expected: FAIL to compile — `EvidenceProtocolCatalog` does not exist.

**Step 2: Make it pass**

`EvidenceProtocolCatalog.kt` declares `CYCLIC_SIGHING_V1` with:

- `id = "cyclic-sighing"`, `version = 1`
- `targetState = "Elevated self-reported tension or arousal that the person has noticed themselves."`
- `intendedPopulation = "One adult using MindAnchor for personal, non-clinical self-experimentation."`
- `exclusions = listOf("Not established for anyone under 18.", "Not established during pregnancy.", "Not established for any respiratory or cardiovascular condition.", "Not for anyone whose clinician has advised against breathing exercises.")`
- `evidenceSources` — Balban 2023 (`RANDOMIZED_OR_CONTROLLED_TRIAL`, `PEER_REVIEWED_ARTICLE`) and Bernardi 2001 (`MECHANISTIC_STUDY`, `PEER_REVIEWED_ARTICLE`), with the citation strings copied verbatim from `friction/BreathingProtocol.kt`'s KDoc.
- `mechanism` — the long exhale's baroreflex/parasympathetic drive, per Bernardi 2001.
- `expectedOutcome` — "Higher same-day self-reported mood and lower same-day self-reported tension, relative to this person's own recent days."
- `eligibilityRules = listOf("The person chose to start it.", "No exclusion in this protocol applies to them.")`
- `contraindicationRules = listOf("Stop if breathing becomes uncomfortable.", "Do not run while driving or operating machinery.", "Do not run during physical exertion.")`
- `steps` — 2 s nasal inhale, 1 s sip inhale, 6 s slow mouth exhale, wording taken from `BreathingProtocol`'s existing constants and KDoc.
- `permittedModalities = setOf(Modality.VISUAL, Modality.AUDIO, Modality.HAPTIC, Modality.TEXT)`
- `maxDurationSeconds = 300`, `cooldownSeconds = 72_000` (20 hours — the once-daily dose the trial used), `outcomeWindowSeconds = 86_400`
- `stopRules = setOf(USER_STOPPED, MAX_DURATION_REACHED, DISCOMFORT_REPORTED, INTERRUPTED_BY_PROTECTED_APP)`
- `successInterpretation = "Compared only against this person's own recent days. No clinical threshold, cut-off, or score is applied, and no result is evidence of treatment effect."`
- `clinicalReviewStatus = ClinicalReviewStatus.NOT_REVIEWED`
- `userFacingExplanation = "A five-minute breathing practice studied in a randomised trial with healthy adults. MindAnchor records it as a research protocol. It is not treatment, and it makes no promise about how you will feel."`

Also declare, with a KDoc citing the design's §4.4 table:

```kotlin
val DELIBERATELY_NOT_SEEDED = setOf(
    "symmetric-slow-paced-breathing", "self-compassion-moment",
    "behavioural-activation", "friction-gate-breath-trigger",
)
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*EvidenceProtocolCatalogTest' --console=plain
```

Expected: PASS.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/EvidenceProtocolCatalog.kt app/src/test/java/org/mindanchor/research/EvidenceProtocolCatalogTest.kt docs/research/22-research-index.md
git commit -m "feat: seed the protocol registry from verified citations only"
```

---

### Task 4: The ledger event model and its hash chain

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/ResearchLedgerEvent.kt`
- Create: `app/src/main/java/org/mindanchor/research/LedgerChain.kt`
- Create: `app/src/test/java/org/mindanchor/research/LedgerChainTest.kt`

**Step 1: Write the failing tests**

`LedgerChainTest`:

0. The event hash is frozen twice over, the way Task 1 freezes the continuity hash: a pinned digest for a fixture event, and an assertion that `LedgerCanonicalEvent`'s serialised element names match a literal list. The kind names are pinned by test 13 for the same reason — `kind.name` is hash input.
1. `LedgerChain.link` is deterministic — the same unlinked event and the same previous hash produce the same `eventHash` twice.
2. Changing any single field of the unlinked event changes the hash (parameterised over `kind`, `occurredAt`, `recordedAt`, `localDate`, `studyPhaseId`, `sourceDeviceId`, `note`, `payloadJson`, `sequence`).
3. Changing `previousEventHash` changes the hash.
4. `verify(emptyList())` is `LedgerIntegrity.VERIFIED` — an empty chain is vacuously intact.
5. A correctly-built three-event chain verifies.
6. Editing event 2's `note` without relinking gives `BROKEN`.
7. Deleting event 2 from a three-event chain gives `BROKEN` (sequence gap).
7b. Deleting the *newest* event gives `VERIFIED` without an anchor and `BROKEN` with one. Tail truncation leaves a shorter but perfectly self-consistent chain, so it is the one tampering direction the chain alone cannot see — and, for a self-experiment where the subject is also the custodian, the likeliest one. `LedgerAnchor(headHash, eventCount)` is the part that cannot live inside the chain; `ContinuityPrefs` holds the local anchor and the export carries one. The limit is asserted explicitly so it stays documented rather than discovered.
7c. `link` refuses a sequence below 1 and a note longer than `MAX_LEDGER_NOTE_LENGTH`. An append-only table has no repair path, so both are checked before the row can exist.
7d. Two *different* events at the same sequence give `BROKEN` — the fork case, not the same-object-twice case.
8. A chain whose first event has a non-empty `previousEventHash` gives `BROKEN`.
9. A chain not starting at sequence 1 gives `BROKEN`.
10. Two events sharing a sequence number give `BROKEN`.
11. `verify` sorts by sequence first, so an out-of-order input list still verifies.
12. `headHash(events)` is the last event's `eventHash`, and `""` for an empty chain.
13. `LedgerEventKind.entries` contains all 18 kinds named in the design §5.2.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*LedgerChainTest' --console=plain
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`ResearchLedgerEvent.kt`:

```kotlin
enum class LedgerEventKind(val isSelfReported: Boolean) {
    SHIFT_SCHEDULE(true), EXERCISE(true), ILLNESS(true), CAFFEINE(true),
    MEDICATION_CHANGE(true), LIFE_EVENT(true), ADVERSE_OR_UNINTENDED_EFFECT(true),
    STUDY_PHASE_STARTED(false), PROTOCOL_VERSION_REGISTERED(false),
    APP_VERSION_CHANGE(false), RULE_VERSION_CHANGE(false), MODEL_VERSION_CHANGE(false),
    TRANSFORMATION_VERSION_CHANGE(false), MISSING_DATA_POLICY_CHANGE(false),
    INSTRUMENT_VERSION_CHANGE(false), DICTIONARY_VERSION_CHANGE(false),
    DEVICE_CHANGE(false), SENSOR_GAP(false);
}

const val MAX_LEDGER_NOTE_LENGTH = 500

data class UnlinkedLedgerEvent(
    val sequence: Long, val kind: LedgerEventKind, val occurredAt: Long, val recordedAt: Long,
    val localDate: String, val studyPhaseId: String, val sourceDeviceId: String,
    val note: String, val payloadJson: String,
)

data class ResearchLedgerEvent(/* the above plus previousEventHash, eventHash, and id */)
```

`LedgerChain.kt`:

```kotlin
enum class LedgerIntegrity { VERIFIED, BROKEN, NOT_APPLICABLE }

data class LedgerAnchor(val headHash: String, val eventCount: Int)

object LedgerChain {
    const val GENESIS_PREVIOUS_HASH = ""
    fun link(event: UnlinkedLedgerEvent, previousEventHash: String): ResearchLedgerEvent
    fun verify(events: List<ResearchLedgerEvent>, expected: LedgerAnchor? = null): LedgerIntegrity
    fun headHash(events: List<ResearchLedgerEvent>): String
    fun anchorOf(events: List<ResearchLedgerEvent>): LedgerAnchor
    fun nextSequence(events: List<ResearchLedgerEvent>): Long
}
```

`LedgerChain`'s KDoc scopes the guarantee honestly in three parts: interior edits and corruption are detected by the chain; tail truncation needs an anchor; a custodian who re-links the whole file is not detectable at all without a head hash published at handover. It also states that `headHash` → `link` → insert is a read-modify-write the caller must serialise, which `ResearchLedgerRepository` does inside one Room transaction.

`link` serialises a private `@Serializable` canonical record (the nine unlinked fields plus `previousEventHash`, in that declaration order) with `Json { encodeDefaults = true }` and takes its SHA-256 as both `eventHash` and `id`. `verify` sorts by sequence, then checks contiguity from 1, no duplicate sequence, `previousEventHash` linkage, and that recomputing `link` reproduces each stored `eventHash`.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*LedgerChainTest' --console=plain
```

Expected: PASS.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/ResearchLedgerEvent.kt app/src/main/java/org/mindanchor/research/LedgerChain.kt app/src/test/java/org/mindanchor/research/LedgerChainTest.kt
git commit -m "feat: chain research ledger events so tampering is detectable"
```

---

### Task 5: Provenance versions, transformations, and the missing-data policy

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/TransformationRegistry.kt`
- Create: `app/src/main/java/org/mindanchor/research/MissingDataPolicy.kt`
- Create: `app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt`
- Create: `app/src/test/java/org/mindanchor/research/TransformationRegistryTest.kt`
- Create: `app/src/test/java/org/mindanchor/research/MissingDataPolicyTest.kt`
- Create: `app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt`

**Step 1: Write the failing tests**

`TransformationRegistryTest`:

1. The registry has exactly two transformations, ids `structural-context` and `research-export-canonicalisation`.
2. `structural-context`'s version equals `StructuralContextExtractor.EXTRACTOR_VERSION` — so a change there forces a phase boundary.
3. `setVersion` is stable across two calls and is frozen to a pinned value.
4. Adding a transformation changes `setVersion` (constructed locally, not by mutating the real registry).

`MissingDataPolicyTest`:

1. `MissingDataPolicy.VERSION == "missing-data-v2"` (v1 during Task 7;
   raised to v2 by the review fix that reworked window selection).
2. `report` over a date range with no measures returns one `NOT_RECORDED` record per date for the `morning_measure` variable.
3. A date that has a measure produces no record for it.
4. Dates before the first record produce `BEFORE_FIRST_RECORD`, not `NOT_RECORDED`.
5. A journal entry with no context rows and extraction disabled produces `EXTRACTION_DISABLED`; with extraction enabled it produces `EXTRACTION_FAILED`.
6. The report is sorted by `(localDate, variable)` and contains no duplicates.
7. No output record ever carries a value — the type has no value field, asserted by reflection over `MissingDataRecord::class.java.declaredFields` names being exactly `localDate`, `variable`, `reason`.
8. An empty input produces an empty report — nothing is invented.

`ProvenanceVersionsTest`:

1. `RULE_SET_VERSION == "rule-set-none-v1"` and `MODEL_SET_VERSION == "model-set-none-v1"`.
2. `ProvenanceVersions.vector(appVersionCode, appVersionName, sourceDeviceId)` returns a vector whose `protocolCatalogSha256` is `EvidenceProtocolCatalog.registry.catalogSha256`, whose `instrumentVersion` is `MorningMeasure.INSTRUMENT_VERSION`, whose `dictionaryVersion` is `ContinuityContract.RESEARCH_DICTIONARY_VERSION`, whose `missingDataPolicyVersion` is `MissingDataPolicy.VERSION`, and whose `transformationSetVersion` is `TransformationRegistry.setVersion`.
3. `vector` is a pure function of its three arguments — two calls with the same arguments are equal.
4. Two vectors differing in exactly one component are not equal, one test per component.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*TransformationRegistryTest' --tests '*MissingDataPolicyTest' --tests '*ProvenanceVersionsTest' --console=plain
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`TransformationRegistry.kt` declares `@Serializable data class Transformation(id, version, input, output, description)`, a `transformations` list of the two entries, and `setVersion` as the SHA-256 of the canonical JSON of that list sorted by id.

`MissingDataPolicy.kt`:

```kotlin
enum class MissingDataReason { NOT_RECORDED, EXTRACTION_DISABLED, EXTRACTION_FAILED, SENSOR_GAP, DEVICE_CHANGE_GAP, BEFORE_FIRST_RECORD }

@Serializable data class MissingDataRecord(val localDate: String, val variable: String, val reason: MissingDataReason)

object MissingDataPolicy {
    const val VERSION = "missing-data-v2"
    // Final wording (see Task 13 review fixes): names the window, because
    // a report that silently covers less than it promises is the failure
    // this policy exists to prevent.
    const val STATEMENT = "Nothing is imputed, interpolated, carried forward, or filled in. Every absence in the reported window is listed with a reason. The window is stated in this file, in the exporting device\u2019s local dates: it ends on the device\u2019s export date — never after it, because a day that has not happened cannot be missing — and reaches back about ten years. When no record could be an observation, the window is absent and no absence is reported, which is not the same as having missed nothing. Records dated outside the window are excluded from it and from the reasons given inside it, and still appear in the data."
    fun report(
        firstRecordDate: LocalDate?, throughDate: LocalDate,
        measureDates: Set<String>, entryDatesWithoutContext: Set<String>, contextExtractionEnabled: Boolean,
    ): List<MissingDataRecord>
}
```

`ProvenanceVersions.kt` declares `@Serializable data class ProvenanceVector(...)` with the nine components from design §6.1, the two `*-none-v1` constants with KDoc explaining they are honest "this build ships none" identifiers, and `vector(...)`.

Run the same three test filters. Expected: PASS. Then run the whole suite.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/TransformationRegistry.kt app/src/main/java/org/mindanchor/research/MissingDataPolicy.kt app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt app/src/test/java/org/mindanchor/research/
git commit -m "feat: version every transformation and state the missing-data policy"
```

---

### Task 6: Study phases and the provenance coordinator

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/StudyPhase.kt`
- Create: `app/src/main/java/org/mindanchor/research/ResearchProvenanceCoordinator.kt`
- Create: `app/src/test/java/org/mindanchor/research/StudyPhaseTest.kt`
- Create: `app/src/test/java/org/mindanchor/research/ResearchProvenanceCoordinatorTest.kt`

**Step 1: Write the failing tests**

`StudyPhaseTest`:

1. `StudyPhase` has no `endedAt` — asserted by reflection over declared field names.
2. `StudyPhaseDecision.next(current = null, vector, now)` returns a phase with `ordinal = 0` and `reason = INITIAL`.
3. `next(current = phaseWithSameVector, vector, now)` returns `null` — no phase churn.
4. For each of the nine vector components, changing only that component returns a new phase with `ordinal = current.ordinal + 1` and the matching `reason`, one test per component.
5. When two components change at once, `reason` is the first differing component in the declared order — deterministic, not arbitrary.
6. `phaseAt(phases, instant)` returns the last phase with `startedAt <= instant`, `null` before the first phase, and is unaffected by input list order.
7. A phase's `id` is deterministic from `(ordinal, startedAt, vector)`.

`ResearchProvenanceCoordinatorTest` fakes every seam as in-memory lambdas (no Room, no Context):

1. On an empty store, `ensureCurrentPhase` inserts phase 0 and appends, in order: one `STUDY_PHASE_STARTED` event, then one `PROTOCOL_VERSION_REGISTERED` event per catalogued protocol.
2. The appended events form a valid chain (`LedgerChain.verify` is `VERIFIED`) starting at sequence 1 with an empty genesis previous-hash.
3. Calling it twice writes nothing the second time and returns the same phase.
4. Changing the app version code inserts phase 1 with `reason = APP_VERSION_CHANGE` and appends `STUDY_PHASE_STARTED` plus `APP_VERSION_CHANGE`.
5. Changing the device id inserts a phase with `reason = DEVICE_CHANGE` and appends a `DEVICE_CHANGE` event.
6. Registering a protocol version already present in the ledger does not append a duplicate `PROTOCOL_VERSION_REGISTERED`.
7. Every appended event carries the new phase's id, so nothing is attributed to a phase that had not started.
8. The chain continues from a pre-existing non-empty ledger — sequence starts at `head.sequence + 1` and the first new event's `previousEventHash` is the pre-existing head hash. This is the replacement-phone case.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*StudyPhaseTest' --tests '*ResearchProvenanceCoordinatorTest' --console=plain
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`StudyPhase.kt`:

```kotlin
enum class StudyPhaseReason {
    INITIAL, APP_VERSION_CHANGE, PROTOCOL_CATALOG_CHANGE, RULE_VERSION_CHANGE, MODEL_VERSION_CHANGE,
    TRANSFORMATION_VERSION_CHANGE, MISSING_DATA_POLICY_CHANGE, INSTRUMENT_VERSION_CHANGE,
    DICTIONARY_VERSION_CHANGE, DEVICE_CHANGE,
}

data class StudyPhase(val id: String, val ordinal: Int, val startedAt: Long, val reason: StudyPhaseReason, val vector: ProvenanceVector)

object StudyPhaseDecision {
    fun next(current: StudyPhase?, vector: ProvenanceVector, now: Long): StudyPhase?
    fun phaseAt(phases: List<StudyPhase>, instant: Long): StudyPhase?
}
```

`ResearchProvenanceCoordinator.kt` takes narrow suspend seams — `latestPhase`, `insertPhase`, `ledgerHead`, `registeredProtocolKeys`, `appendEvents`, `currentVector` — and exposes `suspend fun ensureCurrentPhase(now: Long): StudyPhase`. `Companion.build(context)` wires the real Room DAO, `DeviceIdentityStore`, and `PackageInfo`, the same shape `RestoreCoordinator.build` uses.

Run the two filters. Expected: PASS.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/StudyPhase.kt app/src/main/java/org/mindanchor/research/ResearchProvenanceCoordinator.kt app/src/test/java/org/mindanchor/research/StudyPhaseTest.kt app/src/test/java/org/mindanchor/research/ResearchProvenanceCoordinatorTest.kt
git commit -m "feat: open a new study phase whenever the provenance vector changes"
```

Note: `ResearchProvenanceCoordinator.build` references the DAO that Task 7 creates. Write `build` in Task 7 and keep Task 6 to the pure coordinator plus its seams, so this task compiles and commits on its own.

---

### Task 7: Room v7 — append-only ledger and study-phase tables

**Files:**

- Create: `app/src/main/java/org/mindanchor/data/db/ResearchEntities.kt`
- Create: `app/src/main/java/org/mindanchor/data/db/ResearchDao.kt`
- Modify: `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt`
- Create: `app/schemas/org.mindanchor.data.db.AnchorDatabase/7.json` (generated by KSP)
- Create: `app/src/test/java/org/mindanchor/data/db/ResearchDaoAppendOnlyTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/data/db/ResearchImmutabilityTest.kt`

**Step 1: Write the failing tests**

`ResearchDaoAppendOnlyTest` (JVM, reflection only):

1. `ResearchDao` declares no method annotated `@Update` or `@Delete`.
2. No `@Query` value on `ResearchDao` matches `Regex("(?i)\\b(update|delete)\\b")`.
3. Every `@Insert` on `ResearchDao` uses `OnConflictStrategy.IGNORE`.

`ResearchImmutabilityTest` (instrumented, in-memory Room):

1. Inserting the same ledger event twice leaves one row — content-addressed ids plus `INSERT OR IGNORE`.
2. `db.query("UPDATE research_ledger_events SET note = 'x'")` throws.
3. `db.query("DELETE FROM research_ledger_events")` throws.
4. The same two, for `study_phases`.
5. `ledgerEventsNow()` returns rows ordered by `sequence`.

`MigrationTest` gains `aVersion6DatabaseWithProgramZeroDataGainsTheResearchTables`, which creates a v6 schema by hand (the Program 0 table set), inserts a journal entry, a context row, a morning measure and a continuity change, opens the current database, and asserts all four rows survive and both new tables are readable and writable.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ResearchDaoAppendOnlyTest' --console=plain
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`ResearchEntities.kt`:

```kotlin
@Entity(tableName = "research_ledger_events", indices = [Index(value = ["sequence"], unique = true), Index("recordedAt"), Index("kind"), Index("studyPhaseId"), Index("localDate")])
data class ResearchLedgerEventEntity(
    @PrimaryKey val id: String, val sequence: Long, val kind: String,
    val occurredAt: Long, val recordedAt: Long, val localDate: String,
    val studyPhaseId: String, val sourceDeviceId: String, val note: String,
    val payloadJson: String, val previousEventHash: String, val eventHash: String,
)

@Entity(tableName = "study_phases", indices = [Index(value = ["ordinal"], unique = true), Index("startedAt")])
data class StudyPhaseEntity(
    @PrimaryKey val id: String, val ordinal: Int, val startedAt: Long, val reason: String,
    val appVersionCode: Int, val appVersionName: String, val protocolCatalogSha256: String,
    val ruleSetVersion: String, val modelSetVersion: String, val transformationSetVersion: String,
    val missingDataPolicyVersion: String, val instrumentVersion: String,
    val dictionaryVersion: String, val sourceDeviceId: String,
)
```

`ResearchDao.kt` exposes only `insertLedgerEvents`, `insertStudyPhase` (both `@Insert(onConflict = OnConflictStrategy.IGNORE)`), `ledgerEvents(): Flow<List<...>>`, `ledgerEventsNow()`, `ledgerHead()`, `ledgerEventCount()`, `studyPhases(): Flow<...>`, `studyPhasesNow()`, `latestStudyPhase()`, and `studyPhaseCount()`.

`AnchorDatabase`: add both entities, add `abstract fun research(): ResearchDao`, bump `version = 7`, and add:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // CREATE TABLE IF NOT EXISTS research_ledger_events (...)
        // CREATE TABLE IF NOT EXISTS study_phases (...)
        // the six indices
        // four triggers:
        //   research_ledger_events_no_update / _no_delete
        //   study_phases_no_update / _no_delete
        // each: CREATE TRIGGER IF NOT EXISTS <name> BEFORE <op> ON <table>
        //       BEGIN SELECT RAISE(ABORT, '<table> is append-only'); END
    }
}
```

Append it to `migrations()`. The KDoc must state, in the same voice as `MIGRATION_4_5`, that this migration is purely additive, that no Program 0 column or row is touched, and that the triggers are what make "immutable" a property rather than a claim.

Run:

```bash
./gradlew :app:testDebugUnitTest --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.data.db.MigrationTest,org.mindanchor.data.db.ResearchImmutabilityTest
```

Expected: both PASS, and `app/schemas/.../7.json` appears.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/data/db/ app/schemas/ app/src/test/java/org/mindanchor/data/db/ app/src/androidTest/java/org/mindanchor/data/db/
git commit -m "feat: add the append-only research ledger and study phase tables"
```

---

### Task 8: The research ledger repository

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/ResearchLedgerRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/research/ResearchProvenanceCoordinator.kt` (add `build`)
- Modify: `app/src/main/java/org/mindanchor/research/MorningMeasureRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/journal/JournalRepository.kt`
- Create: `app/src/androidTest/java/org/mindanchor/research/ResearchLedgerRepositoryTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/research/MorningMeasureRepositoryTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/journal/JournalRepositoryTest.kt`

**Step 1: Write the failing tests**

`ResearchLedgerRepositoryTest` (instrumented, in-memory Room):

1. `record(EXERCISE, occurredAt, note = "morning run")` opens phase 0 first, then appends the `EXERCISE` event; the whole ledger verifies.
2. The stored `note` is exactly what was passed — verbatim, no trimming beyond `trim()`, no interpretation.
3. A note longer than `MAX_LEDGER_NOTE_LENGTH` throws `IllegalArgumentException` and writes nothing.
4. A blank note is stored as the empty string, not null.
5. `record` writes a `continuity_changes` row with `entityType = "RESEARCH_LEDGER_EVENT"`, and phase creation writes one with `entityType = "STUDY_PHASE"`.
6. Two `record` calls produce sequences 1..n with no gaps after the phase-opening events.
7. `events()` emits in sequence order.
8. `MEDICATION_CHANGE` is recorded with no derived field of any kind — the row's `payloadJson` is `"{}"`.

`MorningMeasureRepositoryTest` gains: saving a measure opens phase 0, and the measure's `createdAt` falls at or after that phase's `startedAt`. The existing assertions must still pass unchanged — the measure's own columns and upsert-by-date behaviour are untouched.

`JournalRepositoryTest` gains: creating an entry opens phase 0; and a provenance failure does not roll back the entry (inject a coordinator whose `ensureCurrentPhase` throws, assert the entry is still readable).

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.research.ResearchLedgerRepositoryTest
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`ResearchLedgerRepository.kt` mirrors `MorningMeasureRepository`'s shape: `record(kind, occurredAt, note, now)` calls `coordinator.ensureCurrentPhase(now)`, then inside one `database.withTransaction` reads `ledgerHead()`, links via `LedgerChain.link`, inserts, and writes the `continuity_changes` row; then calls `ContinuityWorkScheduler.requestCheckpoint(context)` outside the transaction. `events()` maps the DAO flow to domain.

`ResearchProvenanceCoordinator.build(context)` wires the DAO seams and writes its own `continuity_changes` row for each new phase.

`MorningMeasureRepository` gains a constructor parameter `provenance: ResearchProvenanceCoordinator` and calls `ensureCurrentPhase(now)` as the first line of `save`.

`JournalRepository` gains the same parameter and calls it inside a `runCatching` block **after** the entry transaction has committed, next to the existing `deriveContext` call, with a KDoc pointing at the existing fail-soft contract.

Run the instrumented filter, then the whole JVM suite.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/ app/src/main/java/org/mindanchor/journal/JournalRepository.kt app/src/androidTest/java/org/mindanchor/
git commit -m "feat: append confounders and provenance to the research ledger"
```

---

### Task 9: The frozen machine-readable data dictionary

**Files:**

- Create: `app/src/main/java/org/mindanchor/research/ResearchDataDictionary.kt`
- Create: `app/src/test/resources/research/data-dictionary-mindanchor-research-v2.json`
- Create: `app/src/test/java/org/mindanchor/research/ResearchDataDictionaryTest.kt`

**Step 1: Write the failing tests**

`ResearchDataDictionaryTest`:

1. `dictionary.version == ContinuityContract.RESEARCH_DICTIONARY_VERSION`.
2. `sha256` is stable across two calls and frozen to a pinned value.
3. The canonical JSON equals the checked-in golden resource byte-for-byte.
4. Every variable has a non-blank `name`, `dataset`, `type`, `description`, and a `missingPolicy` equal to `MissingDataPolicy.VERSION`.
5. Variable names are unique within a dataset.
6. Every dataset named by a variable is in the closed `DictionaryDataset` enum.
7. The five morning-measure variables are present, each with `allowedValues = listOf("1","2","3","4","5")`, provenance `USER_REPORTED`, and an `instrumentVersion` of `morning-v1`.
8. `journal_entries.body` has provenance `USER_AUTHORED` and no `transformationId`.
9. Every `journal_context` variable has provenance `DERIVED_STRUCTURAL` and `transformationId == "structural-context"`.
10. **Coverage**: for each of `JournalEntryDto`, `JournalContextDto`, `MorningMeasureDto`, `ResearchLedgerEventDto`, `StudyPhaseDto`, and `MissingDataRecord`, every declared field name has a dictionary entry in the matching dataset. Reflection over `declaredFields`, excluding synthetic fields.
11. No variable description contains a clinical-interpretation word — asserted against `listOf("diagnos", "disorder", "severity", "clinically", "symptom score")`.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ResearchDataDictionaryTest' --console=plain
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`ResearchDataDictionary.kt` declares `DictionaryDataset`, `VariableProvenance`, `@Serializable data class DictionaryVariable(...)`, `@Serializable data class DataDictionary(version, statement, missingDataPolicyVersion, missingDataStatement, variables)`, and `object ResearchDataDictionary { val dictionary: DataDictionary; val sha256: String; fun canonicalJson(): String }`.

Write the golden resource by running the test once with the comparison against an empty file, then pasting the produced canonical JSON into the resource. Pin `sha256` the same way.

Run the filter. Expected: PASS.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/research/ResearchDataDictionary.kt app/src/test/resources/research/ app/src/test/java/org/mindanchor/research/ResearchDataDictionaryTest.kt
git commit -m "feat: freeze the machine-readable research data dictionary"
```

---

### Task 10: Carry the new rows through the continuity snapshot

**Files:**

- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/RestoreStateStore.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ContinuitySnapshotRepositoryTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/RestoreResumeTest.kt`
- Modify: `docs/backup/program-0-data-inventory.md`

**Step 1: Write the failing tests**

In `ContinuityHashVersionTest` add:

1. A payload carrying ledger events and phases hashes differently under version 2 than the same payload with those lists emptied.
2. That same payload hashes **identically under version 1** whether or not the new lists are populated — the version-1 projection genuinely ignores them.
3. The Task 1 freeze value still holds.

In `RestoreCoordinatorTest` add:

4. A staged snapshot with `formatVersion = 1` verifies against a recapture that also contains ledger events and phases, because verification uses the snapshot's own version. This is the "restore a Program 0 backup onto a Program 1 build" case.
5. Resuming from `DATASTORES_MERGED` uses the persisted format version — a fake whose persisted version is 1 verifies where version 2 would not.
6. Merging the same payload twice leaves the ledger and phase row counts unchanged and the chain `VERIFIED`.

In `ContinuityRoundTripTest` (instrumented) add:

7. Capture with ledger events and phases present → encrypt → decrypt → restore into a clean database → the recaptured content hash equals the snapshot's, the ledger verifies, and the head hash matches.

In `ContinuitySnapshotRepositoryTest` add:

8. `capture` includes every ledger event and study phase, canonically sorted.

Run:

```bash
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: FAIL.

**Step 2: Make it pass**

- `ContinuityContract.kt`: **now** raise `SNAPSHOT_FORMAT_VERSION` to 2 and widen `SUPPORTED_SNAPSHOT_FORMAT_VERSIONS` to `{1, 2}` — in this commit, atomically with the field append below, never before it.
- `ContinuityContentHasher.hash`: add the `SNAPSHOT_FORMAT_VERSION -> canonical` branch alongside the existing version-1 projection.
- `ContinuitySnapshotCodec`: add a test that takes an encoded snapshot, rewrites `"formatVersion":2` to `"formatVersion":1`, and asserts `DecodeResult.Success` — the change that makes a Program 0 checkpoint readable at all, which until this commit had no version other than the current one to exercise it.
- `ContinuitySnapshot.kt`: add `@Serializable data class ResearchLedgerEventDto(...)` and `StudyPhaseDto(...)` mirroring the entities field-for-field, append `researchLedgerEvents` and `studyPhases` (both defaulting to `emptyList()`) to `ContinuityPayload`, and add the `toDto()` / `toEntity()` mappers. Appending — never inserting — keeps `ContinuityHashVersionTest`'s "the live payload still begins with Program 0's fields" assertion true.
- `ContinuityContentHasher.sorted`: sort ledger events by `(sequence, id)` and phases by `(ordinal, id)`.
- `ContinuitySnapshotRepository.capture`: read both new tables.
- `RestoreStateStore`: add `expectedFormatVersion: Int?` to `RestoreStageInfo`, an `intPreferencesKey("restore_expected_format_version")`, and the parameter on `markDownloaded`/`markDecrypted`.
- `RestoreCoordinator`: widen `persistDownloaded`/`persistDecrypted` seams by one `formatVersion` parameter, carry `expectedFormatVersion` through `resume`, and use it in the final `ContinuityContentHasher.hash(recaptured.payload, expectedFormatVersion)`. Default to `PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION` when the persisted value is absent: because Task 1 kept the constant at 1, every snapshot staged before this commit genuinely is a version-1 snapshot, so the fallback is sound rather than merely convenient. Extend `mergeRoom` with `dao.insertLedgerEvents(...)` and per-phase `insertStudyPhase(...)`, and extend `preflightIsLocalDataEmpty` with `research().ledgerEventCount() == 0 && research().studyPhaseCount() == 0`.
- Update `docs/backup/program-0-data-inventory.md` with two new "Protected" rows.

Run the JVM suite, then:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.package=org.mindanchor.continuity
```

Expected: both PASS.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity/ app/src/test/java/org/mindanchor/continuity/ app/src/androidTest/java/org/mindanchor/continuity/ docs/backup/program-0-data-inventory.md
git commit -m "feat: back up and restore the research ledger without breaking Program 0 checkpoints"
```

---

### Task 11: The versioned research export

**Files:**

- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExport.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExportCodec.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExportBuilder.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/continuity/ResearchExportBuilderTest.kt`

**Step 1: Write the failing tests**

`ResearchExportCodecTest` gains:

1. A v2 export round-trips every new field.
2. `decode` of a Program 0 v1 JSON document (a literal string fixture written into the test) succeeds, reports `dataDictionaryVersion == "mindanchor-research-v1"`, and leaves every Program 1 list empty.
3. `decode` of a document with `"dataDictionaryVersion": "mindanchor-research-v99"` returns `UnsupportedVersion`.
4. `verify` returns true for a freshly built v2 export and false after a single character of a journal body is changed.
5. `verify` returns true for the v1 fixture from test 2 — the version-1 hash projection is preserved.
6. The v1 content hash of a fixed fixture is frozen to a pinned value.
7. `contentSha256` ignores `exportedAt`, `appVersionCode`, and `appVersionName`.
8. `contentSha256` changes when a ledger event, a study phase, or a missing-data record changes.
9. `dataDictionarySha256` equals `ResearchDataDictionary.sha256` and is not part of `contentSha256`.
10. `ledgerIntegrity` is `VERIFIED` for an intact chain, `BROKEN` for a tampered one, and `NOT_APPLICABLE` when decoded from the v1 fixture.
11. The export's `protocolRegistry` equals the catalogue and its `protocolCatalogSha256` matches.

`ResearchExportBuilderTest` (instrumented): building from a database with entries, measures, ledger events and phases writes a file that decodes, verifies, and reports the same content hash the builder returned.

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ResearchExportCodecTest' --console=plain
```

Expected: FAIL.

**Step 2: Make it pass**

`ContinuityContract.RESEARCH_DICTIONARY_VERSION` is raised to `"mindanchor-research-v2"` **in this commit**, atomically with the export shape below, and `SUPPORTED_RESEARCH_DICTIONARY_VERSIONS` is introduced here (not earlier) so it lands with the code that reads it.

`ResearchExport.kt` gains, all defaulted so v1 files still decode: `ledgerEvents`, `ledgerHeadHash`, `ledgerEventCount`, `ledgerHighWaterCount`, `ledgerIntegrity` (default `NOT_APPLICABLE`), `studyPhases`, `protocolRegistry`, `protocolCatalogSha256`, `transformations`, `transformationSetVersion`, `missingData`, `missingDataWindowStart`, `missingDataWindowThrough`, `missingDataPolicyVersion`, `missingDataStatement`, `dataDictionary` (nullable, default null), `dataDictionarySha256`. `ledgerHeadHash` + `ledgerEventCount` are a reproducible summary of the carried list; `ledgerHighWaterCount`, stored separately before export, is what can reveal truncation that happened earlier.

`ResearchExportCodec` gains a private `V1Content` projection (the four Program 0 lists), a `V2Content` projection (those four plus ledger events, head hash, phases, protocol registry, catalogue hash, transformations, missing data), `hashContent(content, dictionaryVersion)`, `fun verify(export: ResearchExport): Boolean`, a `DecodeResult.UnsupportedVersion(version)` case, and a version check in `decode`.

`ResearchExportBuilder.export` reads the two new tables, computes the missing-data report from the measure dates and the `contextExtractionEnabled` flag, and passes the catalogue, transformation registry and dictionary through.

Run the filter, then the whole JVM suite, then the instrumented filter.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/continuity/Research* app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt app/src/androidTest/java/org/mindanchor/continuity/ResearchExportBuilderTest.kt
git commit -m "feat: export a self-describing, verifiable research file"
```

---

### Task 12: The research log entry surface

**Files:**

- Create: `app/src/main/java/org/mindanchor/journal/ResearchLogCard.kt`
- Modify: `app/src/main/java/org/mindanchor/journal/JournalToday.kt`
- Modify: `app/src/main/java/org/mindanchor/journal/JournalViewModel.kt`
- Modify: `app/src/main/java/org/mindanchor/journal/JournalActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/androidTest/java/org/mindanchor/journal/ResearchLogCardTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/journal/JournalScreenTest.kt`

**Step 1: Write the failing tests**

`ResearchLogCardTest` (Compose instrumented):

1. Every self-reported kind renders a chip with test tag `research_log_chip_<KIND>`; the eleven system kinds render no chip.
2. Tapping a chip opens the note dialog; Save invokes `onRecord(kind, note)` exactly once with the typed text.
3. Cancel invokes nothing.
4. The medication-change dialog shows the "MindAnchor does not give medication advice" line, test tag `research_log_medication_notice`.
5. Today's already-recorded events render read-only, newest first, with no edit or delete affordance anywhere — asserted by the absence of tags `research_log_edit` and `research_log_delete`.
6. A note longer than `MAX_LEDGER_NOTE_LENGTH` disables Save.
7. Every chip and the dialog's fields carry a non-empty `contentDescription` or text for a screen reader.

`JournalScreenTest` gains: the Today tab renders `research_log_card` below `MorningMeasureCard`.

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.journal.ResearchLogCardTest
```

Expected: FAIL to compile.

**Step 2: Make it pass**

`ResearchLogCard.kt` is a stateless Composable taking `todaysEvents`, `onRecord`, and `modifier`. Strings go in `strings.xml` with the `research_log_` prefix, following the existing `continuity_` naming. Copy rules: no clinical language, no interpretation, no advice. Specifically the card's subtitle is "Things that might explain a day. Recorded for research only — nothing here is advice, and nothing reads your notes." and the medication notice is "MindAnchor records that something changed. It does not give medication advice."

`JournalViewModel` gains `todaysLedgerEvents: Flow<List<ResearchLedgerEvent>>` and `recordResearchEvent(kind, note)`; `JournalActivity` builds the `ResearchLedgerRepository`.

Run:

```bash
./gradlew :app:testDebugUnitTest --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.package=org.mindanchor.journal
```

Expected: both PASS. `ClinicalReviewWordlistTest` must stay green — if a new string trips it, change the wording rather than the allowlist.

**Step 3: Commit**

```bash
git add app/src/main/java/org/mindanchor/journal/ app/src/main/res/values/strings.xml app/src/androidTest/java/org/mindanchor/journal/
git commit -m "feat: let a person log what might explain a day"
```

---

### Task 13: Documentation, release notes, and the full gate run

**Files:**

- Create: `docs/qa/program-1-research-runbook.md`
- Modify: `docs/superpowers/specs/2026-08-29-program-1-scientific-foundation-design.md` (status line only)
- Modify: `RELEASE_NOTES_v0.71.0.md` or create `RELEASE_NOTES_v0.72.0.md`
- Modify: `app/build.gradle.kts` (versionCode/versionName bump)
- Create: `sdd/claude-final-report.md`

**Step 1: Write the runbook**

`docs/qa/program-1-research-runbook.md` follows `docs/qa/program-0-continuity-runbook.md`'s table form and covers, on real hardware: open Journal and write an entry; confirm phase 0 opened by exporting and reading `studyPhases`; record one of each self-reported kind; complete the morning measure; force-stop mid-note and confirm nothing partial was written; export and verify the file with `ResearchExportCodec.verify`; upgrade an existing v6 install and confirm no data loss; restore a Program 0 v1 checkpoint and confirm it still verifies; restore a Program 1 v2 checkpoint on a second phone and confirm the ledger head hash matches and the next write records `DEVICE_CHANGE`.

**Step 2: Run every gate**

```bash
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:lintDebug --console=plain
./gradlew detekt --console=plain
./gradlew :app:connectedDebugAndroidTest --console=plain
./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug --console=plain
bash tools/verify-reproducible-release.sh
```

Record each command's exact result. A failure is fixed, not narrated around.

**Step 3: Whole-branch review and the final report**

Re-read the full branch diff against `main`. Confirm, item by item: no Program 2+ capability leaked in; no diagnosis, prediction, efficacy claim, scoring, or semantic journal reading anywhere; `JournalEntry`, `StructuralContextExtractor`, and `MorningMeasure` unchanged; no `fallbackToDestructiveMigration`; `AGENTS.md` untouched and still untracked; every protected store in `docs/backup/program-0-data-inventory.md` still has a destination.

Write `sdd/claude-final-report.md` with the exact evidence: per-task RED and GREEN commands and outcomes, per-task review findings and their fixes, final gate output, test counts, coverage figures, and an explicit list of anything not completed and why.

**Step 4: Commit**

```bash
git add docs/qa/program-1-research-runbook.md docs/superpowers/specs/2026-08-29-program-1-scientific-foundation-design.md RELEASE_NOTES_v0.72.0.md app/build.gradle.kts sdd/claude-final-report.md
git commit -m "docs: close Program 1 scientific foundation"
```

---

## Review discipline

After every task: run a fresh independent review of that task's diff with no memory of writing it. Classify findings Critical / Important / Minor. Fix every Critical and Important **before** starting the next task, and record the finding and the fix in the final report. Minor findings are recorded and either fixed or explicitly deferred with a reason.

## Definition of done

Program 1 is complete when:

- Every task above is committed with its RED and GREEN evidence recorded.
- `:app:testDebugUnitTest`, `:app:lintDebug`, `detekt`, `:app:connectedDebugAndroidTest`, and the Kover report all pass on a clean run.
- `tools/verify-reproducible-release.sh` reports two identical APK hashes.
- A Program 0 snapshot-format-version-1 checkpoint still decodes, restores, and verifies.
- The research export decodes and self-verifies, and carries its own frozen dictionary.
- The whole-branch review is clean and `sdd/claude-final-report.md` records exact evidence.
