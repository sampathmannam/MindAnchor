# Program 3 Adaptive Protocol Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a disabled-by-default, owner-research-only adaptive protocol path that may present one ordinary, dismissible historical advisory from a finalized Program 2 sustained-deviation decision, allows one deliberate manual Start action for the registry's exact `cyclic-sighing@1` protocol, and records the opportunity and episode as immutable research evidence without making diagnostic, current-state, efficacy, or outcome claims.

**Architecture:** Keep Program 3 as a narrow adapter over frozen Program 1 and Program 2 contracts. A pure policy evaluates the latest immutable `PassiveObservationDecisionEntity`; a repository materializes one content-addressed advisory opportunity and an append-only, per-episode event chain; a dedicated ViewModel renders a normal back-navigable foreground-only Compose flow from registry content. Independent build, operational-evidence, master-opt-in, delivery-kill-switch, protocol-allowlist, source-finality, clinical-review, prospective-phase, active-episode, and cooldown gates all fail closed. Program 0 continuity snapshot/restore and Program 1 research export advance additively to v4 while literal v1-v3 projections and hashes remain frozen.

**Tech Stack:** Kotlin 2.x/JVM 17, Android API 33+, Jetpack Compose Material 3, Lifecycle ViewModel/StateFlow, Room 2.6.1, Preferences DataStore, kotlinx.serialization JSON, SHA-256 canonical hashing, JUnit 4, Robolectric, AndroidX Room/instrumentation/Compose tests, PowerShell and Gradle wrapper commands.

## Global Constraints

- Program 3 is advisory only. It never blocks an app, delays an app launch, replaces the launcher, takes over the screen, opens itself, vibrates, posts a notification, starts a foreground service, draws an overlay, enters lock-task mode, changes Do Not Disturb, or uses Accessibility.
- Eligibility consumes the one latest immutable Program 2 decision only. It must have `dataStatus == AVAILABLE_FINAL`, `observationState == SUSTAINED_DEVIATION`, a successful `PassivePipelineCodec.decisionToDomain` decode, and complete source provenance. Never search backward for an older eligible decision when the latest decision is ineligible or corrupt.
- Every advisory is explicitly historical. The card and evidence screen show the source local date and source finalization/as-of time. They make no claim about the person's present or current state.
- No Program 3 type, rule, event, UI string, export field, or test may label a person with anxiety, panic, depression, borderline personality disorder, anger, crisis, illness, diagnosis, or any other clinical/current-state category. Do not claim that a sensor caused an observation or that a protocol treats, prevents, improves, succeeds, or fails.
- Program 3 reads no wearable or Health Connect record directly. It reads no Journal, Note, free-text, LLM, sentiment, inferred emotion, or inferred intent. `LlmPrefs.kt` is outside scope and must remain untouched.
- The catalog contains exactly one protocol: `cyclic-sighing@1`, definition SHA-256 `1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0`, in catalog SHA-256 `9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee`. Its clinical status is `NOT_REVIEWED`.
- Ordinary/public builds have an explicit empty protocol allowlist and therefore expose zero deliverable protocols. They must not special-case or downgrade `NOT_REVIEWED`.
- A personal research build requires an explicit build property and a separate explicit operational-evidence property. Both default to false; release builds force both false. Those build gates remain independent from the master advisory opt-in, local delivery/kill switch, exact protocol allowlist, source eligibility, and cooldown.
- The personal research build may allow exactly the frozen tuple `cyclic-sighing@1` plus its definition hash. It remains hidden and unable to start until build authorization, operational evidence, master opt-in, and delivery are all enabled deliberately.
- There are no eligibility questions, checkbox attestations, remembered attestations, or multi-step checklist gates. The evidence screen displays target, exclusions, contraindications, review status, and stop rules as registry facts.
- One manual Start button is the episode-local attestation that the person currently self-notices tension/arousal, chooses this protocol, has read the exclusions and contraindications and none applies, and is not driving, operating machinery, or physically exerting. The action records all four facts once; no wearable, Journal, Note, LLM, prior answer, or default toggle may supply them.
- Delivery is an ordinary foreground-only, text/visual, back-navigable screen. It follows the registry's exact 2-second inhale, 1-second second inhale, and 6-second exhale cycle and ends at the registry's five-minute maximum. Do not reuse the one-cycle `FrictionGate` breathing animation.
- Backgrounding, process recovery, Back, user Stop, discomfort, or kill-switch activation never count as completion. Only reaching the exact registered maximum appends `COMPLETED_MAX_DURATION`.
- `advisory_opportunities` and `intervention_episode_events` are append-only Room tables. All writes use `INSERT OR IGNORE`; database triggers abort every `UPDATE` and `DELETE`; identifiers and hashes are canonical and deterministic; episode history is a hash-linked event stream, never a mutable episode row.
- Store no per-second samples, breath timestamps, raw wearable values, Journal text, Note text, or free text. A terminal event may store only aggregate `deliveredForegroundMillis` and `completedCycles`.
- Dismissal is an immutable `DISMISSED` event. Starting appends `ELIGIBILITY_ATTESTED` and `STARTED` atomically. Exactly one terminal event is allowed per started episode.
- Cooldown is measured from `STARTED`, never from presentation, dismissal, or completion, and uses the exact registry cooldown.
- There is no compatible registered outcome instrument in Program 3. A completed episode opens the registry outcome window. Once due, reconciliation appends `OUTCOME_WINDOW_CLOSED_MISSING` exactly once with reason `NO_REGISTERED_COMPATIBLE_INSTRUMENT`. It never infers success, failure, effect, symptom change, or treatment response.
- Reconciliation runs before ordinary backup/export capture, but restore verification must not synthesize terminal or outcome events. A replacement restore verifies opportunity hashes and every episode chain before `INSERT OR IGNORE` merge.
- Snapshot format v4 appends opportunities and events. Snapshot v1, v2, and v3 decode, project, hash, restore, and verify exactly as before. Canonical v4 ordering is opportunities by `(presentedAt, id)` and events by `(occurredAt, episodeId, sequence, id)`.
- Research export/data dictionary v4 appends the same two datasets with complete gate, source, protocol, hash-chain, payload-schema, provenance, and missing-outcome disclosure. Export v1, v2, and v3 projections, fixtures, and hashes remain literal and unchanged.
- The Program 1 provenance vector continues to carry the passive rule version and additionally carries advisory rule `advisory-opportunity-v1` through a deterministic versioned encoding. Legacy phase values remain readable as passive-only values; never rename or overwrite the passive rule.
- Restored runtime preferences always leave master advisory opt-in false, delivery false, and the recovery episode key empty. Build authorization is compiled locally and is never serialized, backed up, or restored.
- Any new person-facing wording and every file that renders it is a clinical-review surface. Add `@wording-reviewed`; the existing `clinical-review` CI label gate remains authoritative. Registry copy is rendered mechanically and is not rewritten in Program 3.
- Do not add an Activity, receiver, service, provider, intent filter, permission, network client, Health Connect dependency, wearable dependency, Journal-content/domain dependency, Notes-content dependency, or LLM dependency for Program 3. The existing `JournalDao.insertChange` continuity hook is allowed only for pending checkpoint records.
- Do not edit `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt` or root `AGENTS.md`. Every commit stages only the exact paths named by its task; never use `git add -A` or `git add .`.
- Room migrations are forward-only and non-destructive. Never add `fallbackToDestructiveMigration`.

## Required Read-First Sources

Before implementation, read these files completely in this order; the first two are worktree-specific git metadata and must be resolved with `git rev-parse --git-path`:

```powershell
Get-Content -LiteralPath (git rev-parse --git-path sdd/program3-architecture-brief.md)
Get-Content -LiteralPath (git rev-parse --git-path sdd/program3-design-decisions.md)
Get-Content -LiteralPath docs/superpowers/specs/2026-08-28-mindanchor-mental-health-os-design.md
Get-Content -LiteralPath docs/superpowers/plans/2026-08-30-program-2b-operational-pipeline.md
```

The Program 2 plan is a formatting and compatibility example only. If it conflicts with the Program 3 architecture brief or design decisions, the Program 3 documents win; the design-decisions file wins where it deliberately narrows the brief's earlier attestation shape.

## Pre-Implementation and Activation Blockers

Program 3 implementation must not begin while Program 2 serial verification is running. Run the following source/evidence checks only after that shared verification has finished; do not launch parallel Gradle daemons.

```powershell
$program2Findings = git rev-parse --git-path sdd/program2b-whole-review-findings.md
rg -n "^Verdict: approved\." $program2Findings
```

Expected before Task 1: exactly one approval line. The current file says `Verdict: not approved. Zero Critical, three Important, four Minor.` If the approval line is absent, stop before implementation. Do not reinterpret local fixes or a passing test as whole-branch approval.

The following evidence gates may remain open while the disabled code is tested, but they block every human-facing personal-research delivery build:

```powershell
rg -n "^- \[ \] Pending" docs/research/28-program-2b-device-validation.md
rg -n "template|pending physical execution|Zero are recorded" docs/qa/program-0-continuity-log.md docs/qa/program-0-battery-log.md
rg -n "clinicalReviewStatus = ClinicalReviewStatus.NOT_REVIEWED" app/src/main/java/org/mindanchor/research/EvidenceProtocolCatalog.kt
```

Current expected output is eight pending Program 2 checks, pending Program 0 replacement/battery evidence, and `NOT_REVIEWED`. Therefore:

- ordinary/public builds expose zero protocols;
- the owner personal-research delivery build remains off;
- no test or local property setting is evidence that these gates are complete;
- the build flags must not be enabled for a person until the named evidence artifacts contain observed results and the project owner records an explicit activation decision.

## Frozen Backward-Compatibility Baseline

Do not edit existing v1/v2/v3 golden resources to make new code pass. Freeze these current contracts before adding v4:

```kotlin
// Continuity snapshot versions
const val PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1
const val PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION = 2
const val PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION = 3
const val SNAPSHOT_FORMAT_VERSION = 4

// Research export/data-dictionary versions
const val PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"
const val PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v2"
const val PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v3"
const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v4"
```

The frozen snapshot-v3 projection is the current `ContinuityPayload` through these 20 fields, in this exact order: `journalEntries`, `contextRows`, `morningMeasures`, `notes`, `letters`, `readLetterDates`, `frictionedApps`, `alwaysOpenApps`, `continuityChanges`, `legacyBackupJson`, `researchLedgerEvents`, `studyPhases`, `passiveRawProvenance`, `passiveSourceReads`, `passiveSourceLags`, `passiveBaselineSegments`, `passivePipelineRuns`, `passiveWindowRevisions`, `passiveDailyRevisions`, `passiveObservationDecisions`. Program 3 fields append after them.

## Interfaces Consumed

Program 3 consumes these existing interfaces without weakening them:

```kotlin
// Program 1 protocol registry
class EvidenceProtocolRegistry private constructor(
    val protocols: List<EvidenceProtocol>,
) {
    fun find(id: String, version: Int): EvidenceProtocol?
    fun latest(id: String): EvidenceProtocol?
    val catalogSha256: String

    companion object {
        fun definitionSha256(protocol: EvidenceProtocol): String
    }
}

// Program 1 phase lookup and current provenance
fun StudyPhaseDecision.phaseAt(phases: List<StudyPhase>, instant: Long): StudyPhase?
val ResearchLedgerRepository.provenance: ResearchProvenanceCoordinator
suspend fun ResearchProvenanceCoordinator.ensureCurrentPhase(now: Long): StudyPhase
suspend fun ResearchProvenanceCoordinator.refreshAfterCommit()

// Program 2 immutable decision input
@Query("SELECT * FROM passive_observation_decisions ORDER BY localDate DESC, asOfTime DESC, rowid DESC LIMIT 1")
suspend fun PassiveDao.latestObservationDecisionNow(): PassiveObservationDecisionEntity?

fun PassivePipelineCodec.decisionToDomain(
    entity: PassiveObservationDecisionEntity,
): PassiveObservation

// Program 0 durability hooks
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun JournalDao.insertChange(change: ContinuityChangeEntity)

fun ContinuityWorkScheduler.requestCheckpoint(context: Context)
```

`latestObservationDecisionNow()` is the only new Program 2 query. It preserves `PassiveObservationDecisionEntity`, `PassivePipelineCodec`, all Program 2 hashes, and all v3 continuity/export fields verbatim.

## Interfaces Produced

Use these exact public domain interfaces across tasks so policy, repository, UI, continuity, and export code do not invent parallel representations:

```kotlin
data class ProtocolKey(
    val protocolId: String,
    val protocolVersion: Int,
    val definitionSha256: String,
)

data class AdvisorySettings(
    val masterAdvisoryEnabled: Boolean = false,
    val deliveryAllowed: Boolean = false,
    val currentEpisodeId: String? = null,
)

enum class AdvisoryBuildMode { ORDINARY, PERSONAL_RESEARCH }

data class AdvisoryBuildAuthorization(
    val buildMode: AdvisoryBuildMode,
    val operationalEvidenceApproved: Boolean,
    val protocolAllowlist: Set<ProtocolKey>,
)

data class AdvisorySource(
    val decisionId: String,
    val decisionContentHash: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: PassiveDataStatus,
    val observationState: PassiveObservationState,
    val explanation: String,
    val baselineSegment: String,
    val passiveRuleVersion: String,
    val passiveModelVersion: String,
    val sourceStudyPhaseId: String,
    val sourceDeviceId: String,
)

enum class AdvisoryAction { PRESENT, START }

sealed interface AdvisoryPolicyResult {
    data class Eligible(
        val source: AdvisorySource,
        val protocol: EvidenceProtocol,
        val protocolKey: ProtocolKey,
        val advisoryRuleVersion: String,
        val buildMode: AdvisoryBuildMode,
    ) : AdvisoryPolicyResult

    data class Ineligible(val reason: AdvisoryIneligibleReason) : AdvisoryPolicyResult
}

object AdvisoryPolicy {
    const val RULE_VERSION = "advisory-opportunity-v1"
    fun evaluate(input: AdvisoryPolicyInput, action: AdvisoryAction): AdvisoryPolicyResult
}

interface AdvisoryRepository {
    fun observe(): Flow<AdvisoryReadModel>
    suspend fun refreshOpportunity(now: Long, zoneId: ZoneId): AdvisoryRefreshResult
    suspend fun dismiss(opportunityId: String, now: Long, zoneId: ZoneId): AdvisoryMutationResult
    suspend fun start(opportunityId: String, now: Long, zoneId: ZoneId): AdvisoryStartResult
    suspend fun stop(episodeId: String, kind: EpisodeEventType, now: Long, deliveredForegroundMillis: Long): AdvisoryMutationResult
    suspend fun completeMaximumDuration(episodeId: String, now: Long, deliveredForegroundMillis: Long, completedCycles: Int): AdvisoryMutationResult
}

interface AdvisoryOutcomeReconciler {
    suspend fun reconcile(now: Long, zoneId: ZoneId, requestCheckpoint: Boolean = true): Int
}
```

The only valid `stop` kinds are `STOPPED_BY_USER`, `STOPPED_DISCOMFORT_REPORTED`, `INTERRUPTED_APP_BACKGROUND`, `INTERRUPTED_PROCESS_RECOVERY`, and `STOPPED_KILL_SWITCH`. `completeMaximumDuration` alone may append `COMPLETED_MAX_DURATION` and `OUTCOME_WINDOW_OPENED`.

---

### Task 1: Freeze Program 3 contracts, build authorization, and composite rule provenance

**Files:**

- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryBuildAuthorization.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryPolicy.kt`
- Create: `app/src/test/java/org/mindanchor/advisory/AdvisoryBuildAuthorizationTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt`
- Modify: `app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt`
- Modify: `app/src/test/java/org/mindanchor/research/EvidenceProtocolCatalogTest.kt`

- [ ] **Step 1: Write the failing build-authorization and provenance tests**

Create `AdvisoryBuildAuthorizationTest.kt` with these cases:

```kotlin
class AdvisoryBuildAuthorizationTest {
    private val cyclic = ProtocolKey(
        protocolId = "cyclic-sighing",
        protocolVersion = 1,
        definitionSha256 = "1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
    )

    @Test fun `ordinary builds expose zero deliverable protocols`() {
        val auth = AdvisoryBuildAuthorization.forFlags(
            personalResearchBuild = false,
            operationalEvidenceApproved = false,
        )
        assertEquals(AdvisoryBuildMode.ORDINARY, auth.buildMode)
        assertFalse(auth.operationalEvidenceApproved)
        assertTrue(auth.protocolAllowlist.isEmpty())
    }

    @Test fun `personal build remains closed without operational evidence`() {
        val auth = AdvisoryBuildAuthorization.forFlags(true, false)
        assertEquals(AdvisoryBuildMode.PERSONAL_RESEARCH, auth.buildMode)
        assertFalse(auth.operationalEvidenceApproved)
        assertEquals(setOf(cyclic), auth.protocolAllowlist)
    }

    @Test fun `only the exact frozen cyclic sighing tuple is allowlisted`() {
        val auth = AdvisoryBuildAuthorization.forFlags(true, true)
        assertEquals(setOf(cyclic), auth.protocolAllowlist)
        assertEquals(
            "9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee",
            AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256,
        )
        assertFalse(auth.protocolAllowlist.any { it.protocolId != "cyclic-sighing" || it.protocolVersion != 1 })
    }
}
```

Extend `ProvenanceVersionsTest.kt` to assert exact deterministic encoding and legacy parsing:

```kotlin
@Test fun `rule vector keeps passive and advisory versions separately`() {
    val encoded = RuleSetVersionVector.encode(
        passive = PassiveEstimator.RULE_VERSION,
        advisory = AdvisoryPolicy.RULE_VERSION,
    )
    assertEquals("rule-version-vector-v1|passive=${PassiveEstimator.RULE_VERSION}|advisory=advisory-opportunity-v1", encoded)
    assertEquals(PassiveEstimator.RULE_VERSION, RuleSetVersionVector.passive(encoded))
    assertEquals("advisory-opportunity-v1", RuleSetVersionVector.advisory(encoded))
}

@Test fun `legacy phase rule value remains a passive-only value`() {
    assertEquals("passive-observation-v4", RuleSetVersionVector.passive("passive-observation-v4"))
    assertNull(RuleSetVersionVector.advisory("passive-observation-v4"))
}
```

Pin the one-entry catalog and both supplied hashes in `EvidenceProtocolCatalogTest.kt`; also assert `ClinicalReviewStatus.NOT_REVIEWED`.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run only after the pre-implementation approval check passes and no other Gradle verification owns the worktree:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryBuildAuthorizationTest" --tests "org.mindanchor.research.ProvenanceVersionsTest" --tests "org.mindanchor.research.EvidenceProtocolCatalogTest"
```

Expected: FAIL because `AdvisoryBuildAuthorization`, `ProtocolKey`, `RuleSetVersionVector`, and `AdvisoryPolicy.RULE_VERSION` do not exist.

- [ ] **Step 3: Add the frozen domain enums and event vocabulary**

In `AdvisoryContracts.kt`, add `ProtocolKey`, `AdvisorySettings`, `AdvisoryBuildMode`, and these exact enums. The source/policy/read-model/result types are added when their implementations arrive in Task 3; the repository mutation and outcome-reconciler methods are added in Task 4, so Task 1 never references not-yet-created Room entities.

```kotlin
enum class AdvisoryIneligibleReason {
    BUILD_NOT_AUTHORIZED,
    OPERATIONAL_EVIDENCE_NOT_APPROVED,
    MASTER_ADVISORY_DISABLED,
    DELIVERY_DISABLED,
    SOURCE_MISSING,
    SOURCE_NOT_FINAL,
    SOURCE_NOT_SUSTAINED_DEVIATION,
    SOURCE_DECODE_FAILED,
    SOURCE_PROVENANCE_INCOMPLETE,
    SOURCE_PREDATES_STUDY_PHASE,
    PROTOCOL_MISSING,
    PROTOCOL_HASH_MISMATCH,
    PROTOCOL_NOT_ALLOWLISTED,
    PROTOCOL_NOT_CLINICALLY_REVIEWED,
    OPPORTUNITY_ALREADY_RECORDED,
    OPPORTUNITY_NOT_FOUND,
    OPPORTUNITY_ALREADY_HANDLED,
    ACTIVE_EPISODE_EXISTS,
    COOLDOWN_ACTIVE,
}

enum class EpisodeEventType {
    DISMISSED,
    ELIGIBILITY_ATTESTED,
    STARTED,
    COMPLETED_MAX_DURATION,
    STOPPED_BY_USER,
    STOPPED_DISCOMFORT_REPORTED,
    INTERRUPTED_APP_BACKGROUND,
    INTERRUPTED_PROCESS_RECOVERY,
    STOPPED_KILL_SWITCH,
    OUTCOME_WINDOW_OPENED,
    OUTCOME_WINDOW_CLOSED_MISSING,
}

enum class MissingOutcomeReason {
    NO_REGISTERED_COMPATIBLE_INSTRUMENT,
}
```

Create the initial `AdvisoryPolicy.kt` with the provenance constant only; Task 3 adds the pure evaluator to this same object:

```kotlin
object AdvisoryPolicy {
    const val RULE_VERSION = "advisory-opportunity-v1"
}
```

Do not add success, failure, effective, ineffective, diagnosis, symptom, current-state, notification, or blocking enums.

- [ ] **Step 4: Add fail-closed build fields and authorization**

In `app/build.gradle.kts`, parse only the exact lower-case literal `true`, reject operational approval without the personal build property, and force release false:

```kotlin
val program3PersonalResearch =
    providers.gradleProperty("mindanchor.program3.personalResearch").orNull == "true"
val program3OperationalEvidence =
    providers.gradleProperty("mindanchor.program3.operationalEvidenceApproved").orNull == "true"
require(!program3OperationalEvidence || program3PersonalResearch) {
    "Program 3 operational evidence cannot authorize an ordinary build"
}

android {
    buildTypes {
        debug {
            buildConfigField("boolean", "PROGRAM3_PERSONAL_RESEARCH", program3PersonalResearch.toString())
            buildConfigField("boolean", "PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED", program3OperationalEvidence.toString())
        }
        release {
            buildConfigField("boolean", "PROGRAM3_PERSONAL_RESEARCH", "false")
            buildConfigField("boolean", "PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

Do not duplicate the existing `android`, `buildTypes`, or `buildFeatures` blocks; insert these lines into them.

Implement `AdvisoryBuildAuthorization` with two explicit allowlists:

```kotlin
data class AdvisoryBuildAuthorization(
    val buildMode: AdvisoryBuildMode,
    val operationalEvidenceApproved: Boolean,
    val protocolAllowlist: Set<ProtocolKey>,
) {
    companion object {
        const val PROGRAM_THREE_CATALOG_SHA256 =
            "9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee"

        private val personalAllowlist = setOf(
            ProtocolKey(
                protocolId = "cyclic-sighing",
                protocolVersion = 1,
                definitionSha256 = "1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
            ),
        )
        private val ordinaryAllowlist = emptySet<ProtocolKey>()

        fun forFlags(
            personalResearchBuild: Boolean,
            operationalEvidenceApproved: Boolean,
        ): AdvisoryBuildAuthorization = if (personalResearchBuild) {
            AdvisoryBuildAuthorization(
                buildMode = AdvisoryBuildMode.PERSONAL_RESEARCH,
                operationalEvidenceApproved = operationalEvidenceApproved,
                protocolAllowlist = personalAllowlist,
            )
        } else {
            AdvisoryBuildAuthorization(
                buildMode = AdvisoryBuildMode.ORDINARY,
                operationalEvidenceApproved = false,
                protocolAllowlist = ordinaryAllowlist,
            )
        }

        fun current(): AdvisoryBuildAuthorization = forFlags(
            personalResearchBuild = BuildConfig.PROGRAM3_PERSONAL_RESEARCH,
            operationalEvidenceApproved = BuildConfig.PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED,
        )
    }
}
```

- [ ] **Step 5: Encode both rule versions without rewriting legacy phases**

Add this parser/encoder beside `ProvenanceVersions` and change only new vectors to use its output:

```kotlin
object RuleSetVersionVector {
    private const val PREFIX = "rule-version-vector-v1|"

    fun encode(passive: String, advisory: String): String =
        "${PREFIX}passive=$passive|advisory=$advisory"

    fun passive(value: String): String =
        if (value.startsWith(PREFIX)) value.substringAfter("passive=").substringBefore("|advisory=") else value

    fun advisory(value: String): String? =
        if (value.startsWith(PREFIX)) value.substringAfter("|advisory=") else null
}

object ProvenanceVersions {
    const val PASSIVE_RULE_SET_VERSION = PassiveEstimator.RULE_VERSION
    const val ADVISORY_RULE_SET_VERSION = AdvisoryPolicy.RULE_VERSION
    val RULE_SET_VERSION: String = RuleSetVersionVector.encode(
        passive = PASSIVE_RULE_SET_VERSION,
        advisory = ADVISORY_RULE_SET_VERSION,
    )

    fun vector(appVersionCode: Int, appVersionName: String, sourceDeviceId: String): ProvenanceVector =
        ProvenanceVector(
            appVersionCode = appVersionCode,
            appVersionName = appVersionName,
            protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            ruleSetVersion = RULE_SET_VERSION,
            modelSetVersion = MODEL_SET_VERSION,
            transformationSetVersion = TransformationRegistry.setVersion,
            missingDataPolicyVersion = MissingDataPolicy.VERSION,
            instrumentVersion = MorningMeasure.INSTRUMENT_VERSION,
            dictionaryVersion = ContinuityContract.RESEARCH_DICTIONARY_VERSION,
            sourceDeviceId = sourceDeviceId,
        )
}
```

Existing rows remain byte-for-byte unchanged. A newly opened phase gets the composite value, causing a legitimate provenance phase change once.

- [ ] **Step 6: Run the focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryBuildAuthorizationTest" --tests "org.mindanchor.research.ProvenanceVersionsTest" --tests "org.mindanchor.research.EvidenceProtocolCatalogTest"
```

Expected: PASS. Confirm the default debug build reports both Program 3 BuildConfig fields false and the ordinary allowlist empty.

- [ ] **Step 7: Commit only Task 1 files**

```powershell
git add app/build.gradle.kts app/src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt app/src/main/java/org/mindanchor/advisory/AdvisoryBuildAuthorization.kt app/src/main/java/org/mindanchor/advisory/AdvisoryPolicy.kt app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt app/src/test/java/org/mindanchor/advisory/AdvisoryBuildAuthorizationTest.kt app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt app/src/test/java/org/mindanchor/research/EvidenceProtocolCatalogTest.kt
git commit -m "feat: freeze disabled Program 3 authorization contracts"
```

### Task 2: Add Room v9 append-only opportunity and episode-event storage

**Files:**

- Create: `app/src/main/java/org/mindanchor/data/db/AdvisoryEntities.kt`
- Create: `app/src/main/java/org/mindanchor/data/db/AdvisoryDao.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryCodec.kt`
- Create: `app/src/test/java/org/mindanchor/advisory/AdvisoryCodecTest.kt`
- Create: `app/src/test/java/org/mindanchor/data/db/AdvisoryDaoAppendOnlyTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/data/db/AdvisoryDaoTest.kt`
- Modify: `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/data/db/ResearchImmutabilityTest.kt`
- Create: `app/schemas/org.mindanchor.data.db.AnchorDatabase/9.json`

- [ ] **Step 1: Write failing canonical-hash, DAO, migration, and immutability tests**

Create `AdvisoryCodecTest.kt` to pin these properties:

```kotlin
@Test fun `opportunity identity changes for corrected source content`() {
    val first = AdvisoryCodec.opportunityId("decision-1", "hash-a", CYCLIC_KEY, AdvisoryPolicy.RULE_VERSION)
    val corrected = AdvisoryCodec.opportunityId("decision-1", "hash-b", CYCLIC_KEY, AdvisoryPolicy.RULE_VERSION)
    assertNotEquals(first, corrected)
    assertEquals(first, AdvisoryCodec.opportunityId("decision-1", "hash-a", CYCLIC_KEY, AdvisoryPolicy.RULE_VERSION))
}

@Test fun `event chain detects mutation and sequence gaps`() {
    val rows = listOf(attestedEvent(), startedEvent(previous = attestedEvent().eventHash))
    assertEquals(EventChainVerdict.VALID, AdvisoryCodec.verifyEpisodeChain(rows))
    assertEquals(EventChainVerdict.BROKEN, AdvisoryCodec.verifyEpisodeChain(rows.mapIndexed { i, row -> if (i == 1) row.copy(sequence = 3) else row }))
    assertEquals(EventChainVerdict.BROKEN, AdvisoryCodec.verifyEpisodeChain(rows.mapIndexed { i, row -> if (i == 1) row.copy(payloadJson = "{}") else row }))
}
```

`AdvisoryDaoTest.kt` must prove `INSERT OR IGNORE`, stable ordering, one `(episodeId, sequence)`, and that exact rescans do not duplicate either table. Extend `MigrationTest.kt` with a v8 fixture migrated to v9 and assert every old row survives plus both new tables/indexes/triggers exist. Extend `ResearchImmutabilityTest.kt` to execute direct SQL `UPDATE` and `DELETE` against both tables and assert `SQLiteConstraintException`. `AdvisoryDaoAppendOnlyTest.kt` source-scans the DAO and rejects `@Update`, `@Delete`, and SQL `UPDATE`/`DELETE` methods.

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryCodecTest" --tests "org.mindanchor.data.db.AdvisoryDaoAppendOnlyTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.data.db.AdvisoryDaoTest,org.mindanchor.data.db.MigrationTest,org.mindanchor.data.db.ResearchImmutabilityTest
```

Expected: the JVM command fails on missing Program 3 storage/codec symbols; the instrumentation command fails because Room schema 9, tables, DAO, and triggers do not exist.

- [ ] **Step 3: Add the two immutable entities with complete provenance**

Create `AdvisoryEntities.kt` with these exact persisted fields and no mutable episode summary:

```kotlin
@Entity(tableName = "advisory_opportunities", indices = [Index("presentedAt"), Index("sourceDecisionId")])
data class AdvisoryOpportunityEntity(
    @PrimaryKey val id: String,
    val presentedAt: Long,
    val localDate: String,
    val zoneId: String,
    val sourceDecisionId: String,
    val sourceDecisionContentHash: String,
    val sourceLocalDate: String,
    val sourceAsOfTime: Long,
    val sourceDataStatus: String,
    val sourceObservationState: String,
    val sourceExplanation: String,
    val sourceBaselineSegment: String,
    val sourcePassiveRuleVersion: String,
    val sourcePassiveModelVersion: String,
    val sourceStudyPhaseId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val protocolDefinitionSha256: String,
    val protocolCatalogSha256: String,
    val protocolClinicalReviewStatus: String,
    val advisoryRuleVersion: String,
    val buildMode: String,
    val operationalEvidenceApproved: Boolean,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowedAtPresentation: Boolean,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val contentHash: String,
)

@Entity(
    tableName = "intervention_episode_events",
    indices = [
        Index(value = ["episodeId", "sequence"], unique = true),
        Index("opportunityId"),
        Index("occurredAt"),
    ],
)
data class InterventionEpisodeEventEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val opportunityId: String,
    val sequence: Long,
    val eventType: String,
    val occurredAt: Long,
    val localDate: String,
    val zoneId: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val protocolDefinitionSha256: String,
    val protocolCatalogSha256: String,
    val advisoryRuleVersion: String,
    val buildMode: String,
    val operationalEvidenceApproved: Boolean,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowed: Boolean,
    val payloadSchemaVersion: Int,
    val payloadJson: String,
    val previousEventHash: String,
    val eventHash: String,
)
```

All enum-like columns are serialized with `.name`. Decode with `enumValueOf` and reject unknown values; do not silently map them.

- [ ] **Step 4: Add a read/insert-only DAO**

```kotlin
@Dao
interface AdvisoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOpportunity(row: AdvisoryOpportunityEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(rows: List<InterventionEpisodeEventEntity>): List<Long>

    @Query("SELECT * FROM advisory_opportunities ORDER BY presentedAt, id")
    suspend fun opportunitiesNow(): List<AdvisoryOpportunityEntity>

    @Query("SELECT * FROM advisory_opportunities WHERE id = :id LIMIT 1")
    suspend fun opportunity(id: String): AdvisoryOpportunityEntity?

    @Query("SELECT * FROM intervention_episode_events ORDER BY occurredAt, episodeId, sequence, id")
    suspend fun eventsNow(): List<InterventionEpisodeEventEntity>

    @Query("SELECT * FROM intervention_episode_events WHERE episodeId = :episodeId ORDER BY sequence, id")
    suspend fun eventsForEpisode(episodeId: String): List<InterventionEpisodeEventEntity>

    @Query("SELECT * FROM intervention_episode_events WHERE opportunityId = :opportunityId ORDER BY occurredAt, episodeId, sequence, id")
    suspend fun eventsForOpportunity(opportunityId: String): List<InterventionEpisodeEventEntity>

    @Query("SELECT * FROM advisory_opportunities ORDER BY presentedAt, id")
    fun observeOpportunities(): Flow<List<AdvisoryOpportunityEntity>>

    @Query("SELECT * FROM intervention_episode_events ORDER BY occurredAt, episodeId, sequence, id")
    fun observeEvents(): Flow<List<InterventionEpisodeEventEntity>>
}
```

- [ ] **Step 5: Implement canonical IDs, opportunity hashes, event payloads, and chain verification**

Use UTF-8, lower-case SHA-256, and length-prefixed named fields so delimiters in values cannot collide:

```kotlin
object AdvisoryCodec {
    const val EVENT_PAYLOAD_SCHEMA_VERSION = 1
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    private fun canonical(vararg fields: Pair<String, String?>): ByteArray = buildString {
        fields.forEach { (name, value) ->
            append(name.length).append(':').append(name).append('=')
            if (value == null) append("-1:") else append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
            append('\n')
        }
    }.toByteArray(Charsets.UTF_8)

    fun opportunityId(sourceDecisionId: String, sourceDecisionHash: String, key: ProtocolKey, advisoryRule: String): String =
        sha256(canonical(
            "sourceDecisionId" to sourceDecisionId,
            "sourceDecisionHash" to sourceDecisionHash,
            "protocolId" to key.protocolId,
            "protocolVersion" to key.protocolVersion.toString(),
            "definitionSha256" to key.definitionSha256,
            "advisoryRuleVersion" to advisoryRule,
        ))

    fun dismissalStreamId(opportunityId: String): String =
        sha256(canonical("kind" to "dismissal", "opportunityId" to opportunityId))

    fun episodeId(opportunityId: String, startedAt: Long, sourceDeviceId: String): String =
        sha256(canonical("kind" to "started", "opportunityId" to opportunityId, "startedAt" to startedAt.toString(), "sourceDeviceId" to sourceDeviceId))

    fun verifyEpisodeChain(rows: List<InterventionEpisodeEventEntity>): EventChainVerdict {
        if (rows.isEmpty()) return EventChainVerdict.EMPTY
        var previous = ""
        rows.sortedBy { it.sequence }.forEachIndexed { index, row ->
            if (row.sequence != index + 1L || row.previousEventHash != previous) return EventChainVerdict.BROKEN
            if (eventHash(row.copy(id = "", eventHash = "")) != row.eventHash || row.id != row.eventHash) return EventChainVerdict.BROKEN
            previous = row.eventHash
        }
        return EventChainVerdict.VALID
    }
}
```

Add serializable payload DTOs with exact fields:

```kotlin
@Serializable data class EligibilityAttestedPayloadV1(
    val currentlySelfNoticesTensionOrArousal: Boolean,
    val choosesProtocol: Boolean,
    val exclusionsAndContraindicationsClear: Boolean,
    val notDrivingOperatingMachineryOrExerting: Boolean,
)
@Serializable data class TerminalPayloadV1(val deliveredForegroundMillis: Long, val completedCycles: Int)
@Serializable data class OutcomeWindowOpenedPayloadV1(val opensAt: Long, val closesAt: Long)
@Serializable data class MissingOutcomePayloadV1(val reason: MissingOutcomeReason)
```

`DISMISSED` and `STARTED` use `{}`. Do not add arbitrary maps or free-text payload fields.

`opportunityContentHash` length-prefixes every persisted opportunity field in entity declaration order except `id` and `contentHash`. `eventHash` length-prefixes, in entity declaration order, `episodeId`, `opportunityId`, `sequence`, `eventType`, `occurredAt`, `localDate`, `zoneId`, `studyPhaseId`, `sourceDeviceId`, `protocolId`, `protocolVersion`, `protocolDefinitionSha256`, `protocolCatalogSha256`, `advisoryRuleVersion`, `buildMode`, `operationalEvidenceApproved`, `masterAdvisoryEnabled`, `deliveryAllowed`, `payloadSchemaVersion`, `payloadJson`, and `previousEventHash`; set both `id` and `eventHash` to that digest. Canonical verification may sort a supplied episode by sequence before checking links because serialized list order is not evidence; a missing/duplicate sequence, changed field, changed payload, changed previous hash, or changed event hash must return `BROKEN`.

- [ ] **Step 6: Migrate Room 8 to 9 and install triggers on migration and fresh creation**

Add both entities to `@Database`, set `version = 9`, expose `abstract fun advisory(): AdvisoryDao`, and add `MIGRATION_8_9`. Use SQL types matching Room's generated schema, create the three declared indexes, then call the existing immutability installer extended with:

```kotlin
private val APPEND_ONLY_TABLES = listOf(
    "research_ledger_events",
    "study_phases",
    "passive_raw_provenance",
    "passive_raw_samples",
    "passive_source_reads",
    "passive_source_lags",
    "passive_baseline_segments",
    "passive_pipeline_runs",
    "passive_window_revisions",
    "passive_daily_revisions",
    "passive_observation_decisions",
    "advisory_opportunities",
    "intervention_episode_events",
)
```

For each table, install `BEFORE UPDATE` and `BEFORE DELETE` triggers using `RAISE(ABORT, '<table> is append-only')`. Do not add uniqueness that could merge scientifically distinct corrected Program 2 rows.

- [ ] **Step 7: Run focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryCodecTest" --tests "org.mindanchor.data.db.AdvisoryDaoAppendOnlyTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.data.db.AdvisoryDaoTest,org.mindanchor.data.db.MigrationTest,org.mindanchor.data.db.ResearchImmutabilityTest
```

Expected: PASS; migration retains every v8 row, new inserts are idempotent, direct mutation aborts, and chain mutation/sequence gaps fail verification.

- [ ] **Step 8: Commit only Task 2 files**

```powershell
git add app/src/main/java/org/mindanchor/data/db/AdvisoryEntities.kt app/src/main/java/org/mindanchor/data/db/AdvisoryDao.kt app/src/main/java/org/mindanchor/advisory/AdvisoryCodec.kt app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt app/src/test/java/org/mindanchor/advisory/AdvisoryCodecTest.kt app/src/test/java/org/mindanchor/data/db/AdvisoryDaoAppendOnlyTest.kt app/src/androidTest/java/org/mindanchor/data/db/AdvisoryDaoTest.kt app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt app/src/androidTest/java/org/mindanchor/data/db/ResearchImmutabilityTest.kt app/schemas/org.mindanchor.data.db.AnchorDatabase/9.json
git commit -m "feat: add append-only Program 3 evidence tables"
```

### Task 3: Implement the pure eligibility policy and idempotent opportunity materialization

**Files:**

- Modify: `app/src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt`
- Modify: `app/src/main/java/org/mindanchor/advisory/AdvisoryPolicy.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryRepository.kt`
- Create: `app/src/test/java/org/mindanchor/advisory/AdvisoryPolicyTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/advisory/AdvisoryOpportunityRepositoryTest.kt`
- Modify: `app/src/main/java/org/mindanchor/data/db/PassiveDao.kt`

- [ ] **Step 1: Write the failing policy matrix and repository tests**

Create a table-driven `AdvisoryPolicyTest.kt` whose eligible fixture is changed one field at a time. Pin the rejection order and assert these exact results:

```kotlin
@Test fun `presentation requires final sustained decoded prospective source and every independent gate`() {
    val eligible = eligibleInput()
    assertTrue(AdvisoryPolicy.evaluate(eligible, AdvisoryAction.PRESENT) is AdvisoryPolicyResult.Eligible)

    val cases = listOf(
        eligible.copy(authorization = ordinaryAuthorization()) to AdvisoryIneligibleReason.BUILD_NOT_AUTHORIZED,
        eligible.copy(authorization = eligible.authorization.copy(operationalEvidenceApproved = false)) to AdvisoryIneligibleReason.OPERATIONAL_EVIDENCE_NOT_APPROVED,
        eligible.copy(masterAdvisoryEnabled = false) to AdvisoryIneligibleReason.MASTER_ADVISORY_DISABLED,
        eligible.copy(source = null) to AdvisoryIneligibleReason.SOURCE_MISSING,
        eligible.copy(source = eligible.source!!.copy(dataStatus = PassiveDataStatus.AVAILABLE_PROVISIONAL)) to AdvisoryIneligibleReason.SOURCE_NOT_FINAL,
        eligible.copy(source = eligible.source!!.copy(observationState = PassiveObservationState.WITHIN_PERSON_RANGE)) to AdvisoryIneligibleReason.SOURCE_NOT_SUSTAINED_DEVIATION,
        eligible.copy(sourceDecodeSucceeded = false) to AdvisoryIneligibleReason.SOURCE_DECODE_FAILED,
        eligible.copy(sourceProvenanceComplete = false) to AdvisoryIneligibleReason.SOURCE_PROVENANCE_INCOMPLETE,
        eligible.copy(sourceProducedAfterStudyStart = false) to AdvisoryIneligibleReason.SOURCE_PREDATES_STUDY_PHASE,
        eligible.copy(protocol = null) to AdvisoryIneligibleReason.PROTOCOL_MISSING,
        eligible.copy(protocolDefinitionSha256 = "wrong") to AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH,
        eligible.copy(protocolCatalogSha256 = "wrong") to AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH,
        eligible.copy(opportunityAlreadyRecorded = true) to AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_RECORDED,
    )
    cases.forEach { (input, reason) ->
        assertEquals(AdvisoryPolicyResult.Ineligible(reason), AdvisoryPolicy.evaluate(input, AdvisoryAction.PRESENT))
    }
}

@Test fun `start adds delivery active episode and started cooldown checks`() {
    val input = eligibleInput()
    assertEquals(
        AdvisoryIneligibleReason.DELIVERY_DISABLED,
        (AdvisoryPolicy.evaluate(input.copy(deliveryAllowed = false), AdvisoryAction.START) as AdvisoryPolicyResult.Ineligible).reason,
    )
    assertEquals(
        AdvisoryIneligibleReason.ACTIVE_EPISODE_EXISTS,
        (AdvisoryPolicy.evaluate(input.copy(activeEpisodeExists = true), AdvisoryAction.START) as AdvisoryPolicyResult.Ineligible).reason,
    )
    assertEquals(
        AdvisoryIneligibleReason.COOLDOWN_ACTIVE,
        (AdvisoryPolicy.evaluate(input.copy(lastStartedAt = input.now - input.protocol!!.cooldownSeconds * 1_000L + 1), AdvisoryAction.START) as AdvisoryPolicyResult.Ineligible).reason,
    )
}
```

Add negative vocabulary assertions over `AdvisoryPolicyResult` and `AdvisoryIneligibleReason`; there must be no diagnostic/current-state/success/failure categories.

`AdvisoryOpportunityRepositoryTest.kt` uses an in-memory Room database and fake prefs/build authorization/clock. It must prove:

1. the latest overall decision is used, not the latest eligible historical decision;
2. malformed `decisionJson`, provisional data, no deviation, and incomplete phase provenance append nothing;
3. a final sustained decision appends one opportunity and one `ContinuityChangeEntity` in one transaction;
4. an exact refresh appends neither a duplicate opportunity nor a duplicate continuity change;
5. a corrected decision hash appends a distinct opportunity;
6. the persisted source local date/as-of/passive rule/model/phase/device and protocol definition/catalog/review/gate/advisory-rule fields match their owners exactly;
7. no raw passive sample or wearable API is read.

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryPolicyTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.advisory.AdvisoryOpportunityRepositoryTest
```

Expected: FAIL because `AdvisoryPolicy`, its input model, the overall-latest DAO query, and repository do not exist.

- [ ] **Step 3: Add the exact policy input and one fail-closed function**

```kotlin
data class AdvisoryPolicyInput(
    val authorization: AdvisoryBuildAuthorization,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowed: Boolean,
    val source: AdvisorySource?,
    val sourceDecodeSucceeded: Boolean,
    val sourceProvenanceComplete: Boolean,
    val sourceProducedAfterStudyStart: Boolean,
    val protocol: EvidenceProtocol?,
    val protocolDefinitionSha256: String?,
    val protocolCatalogSha256: String,
    val opportunityAlreadyRecorded: Boolean,
    val opportunityAlreadyHandled: Boolean,
    val activeEpisodeExists: Boolean,
    val lastStartedAt: Long?,
    val now: Long,
)
```

Implement one `AdvisoryPolicy.evaluate` function. Its check order is:

1. personal-research build mode;
2. operational evidence true;
3. master opt-in true;
4. source exists;
5. source final;
6. source sustained deviation;
7. source decoded;
8. source provenance complete;
9. source was produced at/after both current study-phase `startedAt` and its local start date;
10. exact protocol exists, registry definition hash matches, and `protocolCatalogSha256 == AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256`;
11. exact `ProtocolKey` is in the explicit build allowlist;
12. ordinary mode would additionally require `REVIEWED_AND_ACCEPTED` (ordinary mode is already closed and its current allowlist is empty; keep this check explicit);
13. presentation has not already been recorded;
14. for `START` only: opportunity exists/unhandled, delivery true, no active episode, and `now >= lastStartedAt + protocol.cooldownSeconds * 1_000`.

Return only `Eligible` or one explicit `Ineligible` reason. Do not throw for external data and do not return explanatory prose.

Add the plan-wide `AdvisorySource`, `AdvisoryPolicyResult`, `AdvisoryPolicyInput`, `AdvisoryReadModel`, and these exact result types to `AdvisoryContracts.kt` in this task:

```kotlin
sealed interface AdvisoryRefreshResult {
    data class Created(val opportunityId: String) : AdvisoryRefreshResult
    data class AlreadyRecorded(val opportunityId: String) : AdvisoryRefreshResult
    data class Ineligible(val reason: AdvisoryIneligibleReason) : AdvisoryRefreshResult
}

sealed interface AdvisoryMutationResult {
    data class Appended(val eventIds: List<String>) : AdvisoryMutationResult
    data class Ignored(val reason: AdvisoryIneligibleReason) : AdvisoryMutationResult
    data class IntegrityFailure(val episodeId: String) : AdvisoryMutationResult
}

sealed interface AdvisoryStartResult {
    data class Started(val episodeId: String) : AdvisoryStartResult
    data class NotStarted(val reason: AdvisoryIneligibleReason) : AdvisoryStartResult
    data class IntegrityFailure(val opportunityId: String) : AdvisoryStartResult
}
```

For Task 3, `AdvisoryRepository` contains `observe()` and `refreshOpportunity()` only. Task 4 extends it with `dismiss`, `start`, `stop`, and `completeMaximumDuration`, matching the final interface in “Interfaces Produced.”

- [ ] **Step 4: Add the latest-overall immutable decision query**

Append this read-only method to `PassiveDao.kt` without changing any existing query:

```kotlin
@Query("SELECT * FROM passive_observation_decisions ORDER BY localDate DESC, asOfTime DESC, rowid DESC LIMIT 1")
suspend fun latestObservationDecisionNow(): PassiveObservationDecisionEntity?
```

The repository must stop if that row is ineligible; it must not call `priorDecisions` or search `observationDecisionsNow()` for another candidate.

- [ ] **Step 5: Materialize an opportunity transactionally and idempotently**

Implement `RoomAdvisoryRepository.refreshOpportunity` in this order:

```kotlin
override suspend fun refreshOpportunity(now: Long, zoneId: ZoneId): AdvisoryRefreshResult {
    val settings = settingsProvider()
    val authorization = buildAuthorization()
    val inserted = database.withTransaction {
        val existingRows = database.advisory().opportunitiesNow()
        val latest = database.passive().latestObservationDecisionNow()
            ?: return@withTransaction AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_MISSING)
        val decoded = runCatching { PassivePipelineCodec.decisionToDomain(latest) }
        val phases = database.research().studyPhasesNow().map(StudyPhaseEntity::toAdvisoryStudyPhase)
        val sourcePhase = StudyPhaseDecision.phaseAt(phases, latest.asOfTime)
        val currentPhase = researchLedger.provenance.ensureCurrentPhase(now)
        val protocol = EvidenceProtocolCatalog.registry.find("cyclic-sighing", 1)
        val key = protocol?.let {
            ProtocolKey(it.id, it.version, EvidenceProtocolRegistry.definitionSha256(it))
        }
        val opportunityId = if (key == null) "" else AdvisoryCodec.opportunityId(
            latest.id, latest.contentHash, key, AdvisoryPolicy.RULE_VERSION,
        )
        val result = AdvisoryPolicy.evaluate(
            buildPolicyInput(latest, decoded, sourcePhase, currentPhase, protocol, key, settings, authorization, existingRows, zoneId, now),
            AdvisoryAction.PRESENT,
        )
        if (result !is AdvisoryPolicyResult.Eligible) return@withTransaction AdvisoryRefreshResult.Ineligible(result.reason)

        val row = result.toOpportunityEntity(
            id = opportunityId,
            presentedAt = now,
            zoneId = zoneId.id,
            currentPhase = currentPhase,
            settings = settings,
            catalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
        )
        if (database.advisory().insertOpportunity(row) == -1L) {
            return@withTransaction AdvisoryRefreshResult.AlreadyRecorded(opportunityId)
        }
        database.journal().insertChange(
            ContinuityChangeEntity(
                id = UUID.randomUUID().toString(),
                entityType = "ADVISORY_OPPORTUNITY",
                entityId = row.id,
                operation = ChangeOperation.CREATE.name,
                occurredAt = now,
                acknowledgedSnapshotId = null,
            ),
        )
        AdvisoryRefreshResult.Created(row.id)
    }
    if (inserted is AdvisoryRefreshResult.Created) {
        researchLedger.provenance.refreshAfterCommit()
        ContinuityWorkScheduler.requestCheckpoint(context)
    }
    return inserted
}
```

The opportunity `contentHash` covers every persisted field except `id` and `contentHash`, and is verified immediately before insert. Persist `sourceExplanation` mechanically from the decoded final Program 2 decision; do not generate new interpretive text.

Add these private, fully typed adapters to `AdvisoryRepository.kt`:

```kotlin
private fun StudyPhaseEntity.toAdvisoryStudyPhase(): StudyPhase = StudyPhase(
    id = id,
    ordinal = ordinal,
    startedAt = startedAt,
    reason = StudyPhaseReason.valueOf(reason),
    vector = ProvenanceVector(
        appVersionCode = appVersionCode,
        appVersionName = appVersionName,
        protocolCatalogSha256 = protocolCatalogSha256,
        ruleSetVersion = ruleSetVersion,
        modelSetVersion = modelSetVersion,
        transformationSetVersion = transformationSetVersion,
        missingDataPolicyVersion = missingDataPolicyVersion,
        instrumentVersion = instrumentVersion,
        dictionaryVersion = dictionaryVersion,
        sourceDeviceId = sourceDeviceId,
    ),
)

private fun buildPolicyInput(
    latest: PassiveObservationDecisionEntity,
    decoded: Result<PassiveObservation>,
    sourcePhase: StudyPhase?,
    currentPhase: StudyPhase,
    protocol: EvidenceProtocol?,
    key: ProtocolKey?,
    settings: AdvisorySettings,
    authorization: AdvisoryBuildAuthorization,
    existingRows: List<AdvisoryOpportunityEntity>,
    zoneId: ZoneId,
    now: Long,
): AdvisoryPolicyInput

private fun AdvisoryPolicyResult.Eligible.toOpportunityEntity(
    id: String,
    presentedAt: Long,
    zoneId: String,
    currentPhase: StudyPhase,
    settings: AdvisorySettings,
    catalogSha256: String,
): AdvisoryOpportunityEntity
```

`buildPolicyInput` maps each named argument directly to the identically named policy field. The source phase is the last `StudyPhase.startedAt <= source.asOfTime`. Parse its `ruleSetVersion` through `RuleSetVersionVector.passive`; copy its `modelSetVersion`, `id`, and `sourceDeviceId`. If no such phase exists, set `sourceProvenanceComplete = false`. Set `sourceProducedAfterStudyStart` only when both `latest.asOfTime >= currentPhase.startedAt` and `LocalDate.parse(latest.localDate) >= Instant.ofEpochMilli(currentPhase.startedAt).atZone(zoneId).toLocalDate()`.

- [ ] **Step 6: Derive one visible read model without mutating evidence**

Combine `observeOpportunities()` and `observeEvents()` and select at most one unhandled opportunity, newest first. An opportunity is handled after `DISMISSED` or any `STARTED` event. Never delete or update it. The read model is:

```kotlin
sealed interface AdvisoryReadModel {
    data object Hidden : AdvisoryReadModel
    data class Opportunity(
        val row: AdvisoryOpportunityEntity,
        val protocol: EvidenceProtocol,
        val startAvailable: Boolean,
        val startBlockedReason: AdvisoryIneligibleReason?,
    ) : AdvisoryReadModel
    data class ActiveEpisode(
        val opportunity: AdvisoryOpportunityEntity,
        val events: List<InterventionEpisodeEventEntity>,
        val protocol: EvidenceProtocol,
    ) : AdvisoryReadModel
}
```

If protocol lookup/hash verification later fails, emit `Hidden`; never render stale or changed registry instructions.

- [ ] **Step 7: Run focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryPolicyTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.advisory.AdvisoryOpportunityRepositoryTest
```

Expected: PASS; one eligible finalized source creates one opportunity/change, every gate fails closed, and exact refresh is duplicate-free.

- [ ] **Step 8: Commit only Task 3 files**

```powershell
git add app/src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt app/src/main/java/org/mindanchor/advisory/AdvisoryPolicy.kt app/src/main/java/org/mindanchor/advisory/AdvisoryRepository.kt app/src/main/java/org/mindanchor/data/db/PassiveDao.kt app/src/test/java/org/mindanchor/advisory/AdvisoryPolicyTest.kt app/src/androidTest/java/org/mindanchor/advisory/AdvisoryOpportunityRepositoryTest.kt
git commit -m "feat: materialize finalized historical advisory opportunities"
```

### Task 4: Implement the manual-start event stream, foreground delivery state, recovery, and missing outcome closure

**Files:**

- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryPrefs.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryPlayerStateMachine.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryOutcomeReconciler.kt`
- Create: `app/src/test/java/org/mindanchor/advisory/AdvisoryPlayerStateMachineTest.kt`
- Create: `app/src/test/java/org/mindanchor/advisory/AdvisoryOutcomeReconcilerTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/advisory/AdvisoryEpisodeRepositoryTest.kt`
- Modify: `app/src/main/java/org/mindanchor/advisory/AdvisoryRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt`
- Modify: `app/src/test/java/org/mindanchor/advisory/AdvisoryCodecTest.kt`

- [ ] **Step 1: Write failing event-transition, persistence, recovery, and outcome tests**

Pin the pure player transition first:

```kotlin
@Test fun `only exact maximum duration completes`() {
    val running = AdvisoryPlayerStateMachine.start(startedElapsedRealtime = 10_000L, maximumMillis = 300_000L)
    assertNull(AdvisoryPlayerStateMachine.maximumEvent(running, nowElapsedRealtime = 309_999L))
    assertEquals(EpisodeEventType.COMPLETED_MAX_DURATION, AdvisoryPlayerStateMachine.maximumEvent(running, 310_000L))
}

@Test fun `background back discomfort process recovery and kill switch are never completion`() {
    listOf(
        EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
        EpisodeEventType.STOPPED_BY_USER,
        EpisodeEventType.STOPPED_DISCOMFORT_REPORTED,
        EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
        EpisodeEventType.STOPPED_KILL_SWITCH,
    ).forEach { assertFalse(AdvisoryPlayerStateMachine.isCompletion(it)) }
}
```

`AdvisoryEpisodeRepositoryTest.kt` must prove:

- a single repository `start` call re-evaluates all current gates and appends `ELIGIBILITY_ATTESTED` sequence 1 plus `STARTED` sequence 2 atomically;
- the attestation payload contains four `true` facts and the API accepts no caller-supplied inferred facts;
- a disabled delivery switch, stale/changed source decision, changed registry hash, active episode, handled opportunity, or cooldown appends zero rows;
- dismissal uses a deterministic dismissal stream and appends exactly one `DISMISSED` event with no free text;
- terminal calls append exactly one allowed terminal event and a second terminal call is ignored;
- completion appends `COMPLETED_MAX_DURATION` and `OUTCOME_WINDOW_OPENED` in one transaction;
- background before the maximum appends `INTERRUPTED_APP_BACKGROUND`; at/after the maximum it appends completion instead;
- process recovery appends `INTERRUPTED_PROCESS_RECOVERY`, unless the in-process registry still owns the episode after an Activity recreation;
- disabling delivery while active appends `STOPPED_KILL_SWITCH`;
- every inserted event is accompanied by a pending `ContinuityChangeEntity`, while ignored duplicates add no change;
- no event payload contains per-breath timestamps, raw sensor values, Journal/Note text, or free text.

`AdvisoryOutcomeReconcilerTest.kt` must prove an opened, due window closes once with the exact missing reason and that stopped/interrupted/dismissed episodes never open or close an outcome window.

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryPlayerStateMachineTest" --tests "org.mindanchor.advisory.AdvisoryOutcomeReconcilerTest" --tests "org.mindanchor.advisory.AdvisoryCodecTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.advisory.AdvisoryEpisodeRepositoryTest
```

Expected: FAIL because the preferences, player state machine, repository event transitions, and outcome reconciler do not exist.

- [ ] **Step 3: Add disabled-by-default preferences and a recovery key only**

```kotlin
class AdvisoryPrefs(private val context: Context) {
    val settings: Flow<AdvisorySettings>
    suspend fun setMasterAdvisoryEnabled(enabled: Boolean)
    suspend fun setDeliveryAllowed(enabled: Boolean)
    suspend fun setCurrentEpisodeId(episodeId: String?)
    suspend fun disableAfterRestore()
}
```

Use a dedicated Preferences DataStore named `program_three_advisory`. Missing keys decode to false/false/null. `disableAfterRestore()` atomically writes false/false and removes the episode key. Do not store attestation facts, answers, source state, protocol progress, or outcome state in DataStore.

- [ ] **Step 4: Make manual Start construct the attestation internally**

The UI/repository boundary takes only `opportunityId`, wall-clock time, and zone. Construct this value inside `start`; there is no public Boolean-parameter overload:

```kotlin
data class ManualStartAttestation private constructor(
    val currentlySelfNoticesTensionOrArousal: Boolean,
    val choosesProtocol: Boolean,
    val exclusionsAndContraindicationsClear: Boolean,
    val notDrivingOperatingMachineryOrExerting: Boolean,
) {
    companion object {
        fun fromSingleManualStartAction() = ManualStartAttestation(
            currentlySelfNoticesTensionOrArousal = true,
            choosesProtocol = true,
            exclusionsAndContraindicationsClear = true,
            notDrivingOperatingMachineryOrExerting = true,
        )
    }
}
```

In one Room transaction, reload the opportunity, latest source decision, phases, registry hashes, prefs snapshot, all events, and last `STARTED`; construct the complete `AdvisoryPolicyInput` shown in Task 3 and call `AdvisoryPolicy.evaluate(input, AdvisoryAction.START)`. If eligible, append the two linked events and their two continuity changes. Set `currentEpisodeId` after commit, register it in `AdvisoryProcessSessionRegistry`, refresh provenance, and request one checkpoint. If the process dies between Room commit and the preference write, database recovery remains authoritative.

- [ ] **Step 5: Enforce legal event transitions and one terminal event**

Implement a pure transition validator:

```kotlin
object EpisodeTransitions {
    val terminal = setOf(
        EpisodeEventType.COMPLETED_MAX_DURATION,
        EpisodeEventType.STOPPED_BY_USER,
        EpisodeEventType.STOPPED_DISCOMFORT_REPORTED,
        EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
        EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
        EpisodeEventType.STOPPED_KILL_SWITCH,
    )

    fun mayAppend(existing: List<EpisodeEventType>, next: EpisodeEventType): Boolean = when (next) {
        EpisodeEventType.DISMISSED -> existing.isEmpty()
        EpisodeEventType.ELIGIBILITY_ATTESTED -> existing.isEmpty()
        EpisodeEventType.STARTED -> existing == listOf(EpisodeEventType.ELIGIBILITY_ATTESTED)
        in terminal -> existing.lastOrNull() == EpisodeEventType.STARTED && existing.none { it in terminal }
        EpisodeEventType.OUTCOME_WINDOW_OPENED -> existing.lastOrNull() == EpisodeEventType.COMPLETED_MAX_DURATION
        EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING ->
            existing.contains(EpisodeEventType.OUTCOME_WINDOW_OPENED) &&
                existing.none { it == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING }
    }
}
```

For completion, append completion then window-open in one transaction. For every terminal event, clear the preference and process registry after commit. Calculate `completedCycles = deliveredForegroundMillis / 9_000L` and cap aggregates at the protocol maximum. Store no tick or breath event.

- [ ] **Step 6: Add a monotonic foreground-only state machine and process registry**

```kotlin
data class RunningAdvisoryEpisode(
    val episodeId: String,
    val startedElapsedRealtime: Long,
    val maximumMillis: Long,
)

object AdvisoryPlayerStateMachine {
    fun elapsed(state: RunningAdvisoryEpisode, nowElapsedRealtime: Long): Long =
        (nowElapsedRealtime - state.startedElapsedRealtime).coerceIn(0L, state.maximumMillis)

    fun maximumEvent(state: RunningAdvisoryEpisode, nowElapsedRealtime: Long): EpisodeEventType? =
        if (elapsed(state, nowElapsedRealtime) == state.maximumMillis) EpisodeEventType.COMPLETED_MAX_DURATION else null

    fun isCompletion(type: EpisodeEventType): Boolean = type == EpisodeEventType.COMPLETED_MAX_DURATION
}

object AdvisoryProcessSessionRegistry {
    private val activeEpisodeIds = ConcurrentHashMap.newKeySet<String>()
    fun register(episodeId: String) { activeEpisodeIds += episodeId }
    fun unregister(episodeId: String) { activeEpisodeIds -= episodeId }
    fun contains(episodeId: String): Boolean = episodeId in activeEpisodeIds
}
```

Use `SystemClock.elapsedRealtime()` only while foregrounded. On lifecycle background, immediately choose completion if elapsed equals the maximum; otherwise append `INTERRUPTED_APP_BACKGROUND`. On Back or ordinary Stop use `STOPPED_BY_USER`. On the dedicated discomfort action use `STOPPED_DISCOMFORT_REPORTED`. No WorkManager, alarm, notification, service, vibration, or lock is involved.

- [ ] **Step 7: Reconcile process death, kill switch, and missing outcome windows**

On foreground resume, scan event streams with a `STARTED` and no terminal. If the process registry lacks the episode, append `INTERRUPTED_PROCESS_RECOVERY`. If delivery is false, prefer `STOPPED_KILL_SWITCH`. Never infer elapsed completion after process death.

Define the compatible-outcome registry as deliberately empty:

```kotlin
object CompatibleOutcomeInstrumentRegistry {
    fun compatibleWith(protocol: ProtocolKey): Nothing? = null
}
```

`RoomAdvisoryOutcomeReconciler.reconcile` scans valid chains. For each `OUTCOME_WINDOW_OPENED` whose `closesAt <= now` and which has no close event, append exactly one:

```kotlin
MissingOutcomePayloadV1(
    reason = MissingOutcomeReason.NO_REGISTERED_COMPATIBLE_INSTRUMENT,
)
```

Insert the close event and continuity change atomically and refresh provenance. Request one checkpoint after commit only when at least one event was inserted and `requestCheckpoint` is true. Chain corruption returns a typed integrity error and appends nothing. Snapshot capture passes false because its current worker is already capturing the new row; this prevents reconciliation from replacing/cancelling its own checkpoint work. Foreground resume and research export use the default true.

- [ ] **Step 8: Run focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisoryPlayerStateMachineTest" --tests "org.mindanchor.advisory.AdvisoryOutcomeReconcilerTest" --tests "org.mindanchor.advisory.AdvisoryCodecTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.advisory.AdvisoryEpisodeRepositoryTest
```

Expected: PASS; all evidence is append-only, Start is the sole attestation source, interruptions never complete, and due windows close missing exactly once without outcome interpretation.

- [ ] **Step 9: Commit only Task 4 files**

```powershell
git add app/src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt app/src/main/java/org/mindanchor/advisory/AdvisoryPrefs.kt app/src/main/java/org/mindanchor/advisory/AdvisoryPlayerStateMachine.kt app/src/main/java/org/mindanchor/advisory/AdvisoryOutcomeReconciler.kt app/src/main/java/org/mindanchor/advisory/AdvisoryRepository.kt app/src/test/java/org/mindanchor/advisory/AdvisoryPlayerStateMachineTest.kt app/src/test/java/org/mindanchor/advisory/AdvisoryOutcomeReconcilerTest.kt app/src/test/java/org/mindanchor/advisory/AdvisoryCodecTest.kt app/src/androidTest/java/org/mindanchor/advisory/AdvisoryEpisodeRepositoryTest.kt
git commit -m "feat: record deliberate foreground protocol episodes"
```

### Task 5: Add the single-card, evidence, and foreground player UI with explicit settings

**Files:**

- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryViewModel.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryHomeCard.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisoryScreen.kt`
- Create: `app/src/main/java/org/mindanchor/advisory/AdvisorySettingsSection.kt`
- Create: `app/src/test/java/org/mindanchor/advisory/AdvisorySourceBoundaryTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/advisory/AdvisoryScreenTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/advisory/AdvisoryPrefsTest.kt`
- Modify: `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/org/mindanchor/launcher/LauncherUiTest.kt`

- [ ] **Step 1: Write failing Compose, preference, and static boundary tests**

`AdvisoryScreenTest.kt` uses an injected fake state and callbacks. Cover these exact cases:

```kotlin
@Test fun ordinaryBuildAndDisabledMasterRenderNoAdvisoryCard()
@Test fun eligibleStateRendersExactlyOneOrdinaryDismissibleCard()
@Test fun cardShowsHistoricalSourceDateAndFinalizedAsOfTime()
@Test fun openShowsRegistryTargetExclusionsContraindicationsReviewStatusAndStopRules()
@Test fun evidenceScreenHasNoCheckboxRadioQuestionnaireOrChecklist()
@Test fun evidenceScreenHasExactlyOneStartActionWithFullAttestationCopy()
@Test fun playerRendersTheRegistryStepAndAllowsStopDiscomfortAndBack()
@Test fun backgroundInterruptsAndDoesNotComplete()
```

For the one-action contract, assert the exact content description/text:

```kotlin
private const val START_ATTESTATION =
    "Start — I currently notice tension/arousal, choose this practice, have read the exclusions " +
        "and contraindications and none applies, and I am not driving, operating machinery, or physically exerting."

composeRule.onAllNodesWithText(START_ATTESTATION).assertCountEquals(1)
composeRule.onAllNodes(hasClickAction() and hasText("Start", substring = true)).assertCountEquals(1)
composeRule.onAllNodes(isToggleable()).assertCountEquals(0)
```

`AdvisoryPrefsTest.kt` proves a new store is false/false/null, setters are independent, and `disableAfterRestore()` returns to false/false/null.

`AdvisorySourceBoundaryTest.kt` reads all Kotlin files under `org/mindanchor/advisory` and fails on imports/references to Health Connect, wearable/COROS, `JournalEntry`, `JournalContext`, `JournalRepository`, `NoteActivity`, `NotesPrefs`, LLM, notification, vibration, WorkManager, Accessibility, overlay, lock task, DND, network, or foreground-service APIs. It permits only the existing `JournalDao.insertChange` continuity hook. It separately fails if `AdvisoryScreen.kt` contains `Checkbox`, `RadioButton`, `TriStateCheckbox`, `TextField`, or a second Start callback.

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisorySourceBoundaryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.advisory.AdvisoryScreenTest,org.mindanchor.advisory.AdvisoryPrefsTest
```

Expected: FAIL because the dedicated ViewModel/screens/settings section and test tags do not exist.

- [ ] **Step 3: Add one dedicated UI state owner**

`AdvisoryViewModel` owns no broad launcher state and accepts clock/repository dependencies for tests:

```kotlin
sealed interface AdvisoryUiState {
    data object Hidden : AdvisoryUiState
    data class Card(val opportunity: AdvisoryOpportunityEntity) : AdvisoryUiState
    data class Evidence(val opportunity: AdvisoryOpportunityEntity, val protocol: EvidenceProtocol, val startEnabled: Boolean) : AdvisoryUiState
    data class Player(val opportunity: AdvisoryOpportunityEntity, val protocol: EvidenceProtocol, val episodeId: String, val elapsedMillis: Long) : AdvisoryUiState
}

class AdvisoryViewModel internal constructor(
    private val repository: AdvisoryRepository,
    private val reconciler: AdvisoryOutcomeReconciler,
    private val wallClock: () -> Long,
    private val elapsedClock: () -> Long,
    private val zoneId: () -> ZoneId,
) : ViewModel() {
    val uiState: StateFlow<AdvisoryUiState>
    fun onResume()
    fun openEvidence()
    fun dismiss()
    fun start()
    fun stop()
    fun reportDiscomfort()
    fun onBack(): Boolean
    fun onBackground()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AdvisoryViewModel(
                    repository = RoomAdvisoryRepository.build(appContext),
                    reconciler = RoomAdvisoryOutcomeReconciler.build(appContext),
                    wallClock = System::currentTimeMillis,
                    elapsedClock = SystemClock::elapsedRealtime,
                    zoneId = ZoneId::systemDefault,
                )
            }
        }
    }
}
```

`onResume` runs process/kill/outcome reconciliation, refreshes opportunity eligibility, then collects the repository read model. A coroutine may update the visual countdown from `elapsedRealtime`; it persists nothing until a terminal transition. `onBackground` is synchronous at the ViewModel boundary and dispatches exactly one terminal write. `onBack` returns true when it consumes Back by stopping an active episode or returning Evidence to Card.

- [ ] **Step 4: Render one ordinary Home card and a normal evidence screen**

`AdvisoryHomeCard.kt` is an ordinary Material 3 `Card`, never a dialog/overlay/full-screen takeover:

```kotlin
@Composable
fun AdvisoryHomeCard(
    opportunity: AdvisoryOpportunityEntity,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card {
        Text(stringResource(R.string.advisory_historical_title))
        Text(opportunity.sourceExplanation)
        Text(stringResource(R.string.advisory_recorded_date, opportunity.sourceLocalDate))
        Text(stringResource(R.string.advisory_finalized_as_of, formatInstant(opportunity.sourceAsOfTime)))
        Row {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.advisory_open)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.advisory_dismiss)) }
        }
    }
}
```

`AdvisoryScreen.kt` resolves every target, instruction step, exclusion, contraindication, maximum, cooldown, outcome window, stop rule, and clinical-review status from the exact registry object. It does not paraphrase registry copy. Render list text, not controls. Its KDoc contains `@wording-reviewed`.

The only Start callback is:

```kotlin
Button(onClick = onStart, enabled = startEnabled) {
    Text(stringResource(R.string.advisory_start_attestation))
}
```

Do not save or restore an attestation answer. If Start becomes ineligible, disable the button and show only a mechanical local-control reason such as “Delivery is off” or “Cooldown is active”; never reinterpret the source.

- [ ] **Step 5: Render the exact foreground protocol player**

Use the registry's ordered `steps` and durations. For `cyclic-sighing@1`, the visible state repeats 2,000 ms inhale, 1,000 ms second inhale, and 6,000 ms exhale. Derive cycle position from aggregate foreground elapsed time:

```kotlin
private fun List<ProtocolStep>.stepAt(withinCycleMillis: Long): ProtocolStep {
    var upperBound = 0L
    return first { step ->
        upperBound += step.durationSeconds * 1_000L
        withinCycleMillis < upperBound
    }
}

val cycleMillis = protocol.steps.sumOf { it.durationSeconds.toLong() } * 1_000L
val withinCycle = elapsedMillis % cycleMillis
val activeStep = protocol.steps.stepAt(withinCycle)
```

Show remaining total time, current registry instruction, Stop, and “Stop — discomfort” actions. Back invokes `onBack`. Do not call `FrictionGate`, schedule work, hold a wake lock, vibrate, play audio, or continue after `ON_STOP`.

- [ ] **Step 6: Wire Home without adding an Activity or manifest entry**

In `HomeScreen.kt`, append `Advisory` to the existing private `LauncherSurface` enum. Obtain one `AdvisoryViewModel` in `LauncherRoot` with `viewModel(factory = AdvisoryViewModel.factory(LocalContext.current.applicationContext))`, collect its state, and pass only `AdvisoryUiState.Card` to `HomeSurface`. Insert at most one `AdvisoryHomeCard` in the existing scroll content. Route Open to `LauncherSurface.Advisory`; route Back through `advisoryViewModel.onBack()` before returning Home.

Use the existing launcher lifecycle observer pattern to call `onResume` at `ON_RESUME` and `onBackground` at `ON_STOP`. Do not modify `HomeActivity.kt` or `AndroidManifest.xml`.

- [ ] **Step 7: Add independent master and delivery settings**

`AdvisorySettingsSection` shows two independent switches in Settings:

- “Historical protocol advisories” maps to `masterAdvisoryEnabled`;
- “Allow protocol delivery” is the reachable kill switch and maps to `deliveryAllowed`.

Both default off. Turning master off hides cards; turning delivery off prevents Start and stops an active episode through `STOPPED_KILL_SWITCH`. Settings toggles are configuration controls, not episode eligibility questions, and must never record an attestation.

Add `AdvisoryPrefs` flows/setters to `SettingsViewModel` using the same application-context pattern already present. Add the section to `SettingsScreen.kt` without altering LLM settings or `LlmPrefs.kt`.

- [ ] **Step 8: Add only mechanical/historical strings and clinical-review sentinels**

Add these strings under an `@wording-reviewed — clinical-review-required` resource comment:

```xml
<string name="advisory_historical_title">Historical recorded-data advisory</string>
<string name="advisory_recorded_date">Recorded date: %1$s</string>
<string name="advisory_finalized_as_of">Finalized as of: %1$s</string>
<string name="advisory_open">Open</string>
<string name="advisory_dismiss">Dismiss</string>
<string name="advisory_start_attestation">Start — I currently notice tension/arousal, choose this practice, have read the exclusions and contraindications and none applies, and I am not driving, operating machinery, or physically exerting.</string>
<string name="advisory_stop">Stop</string>
<string name="advisory_stop_discomfort">Stop — discomfort</string>
<string name="advisory_review_status">Clinical review status: %1$s</string>
<string name="advisory_master_setting">Historical protocol advisories</string>
<string name="advisory_delivery_setting">Allow protocol delivery</string>
```

No string may say “you are,” “you seem,” “right now,” “detected,” “treatment,” “works,” “improved,” “success,” or “failure.” The protocol's existing catalog copy remains its single source of truth.

- [ ] **Step 9: Run focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.AdvisorySourceBoundaryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.advisory.AdvisoryScreenTest,org.mindanchor.advisory.AdvisoryPrefsTest,org.mindanchor.launcher.LauncherUiTest
```

Expected: PASS. Default/ordinary builds show no card; an injected eligible owner-research state shows one historical card; the evidence screen contains no questionnaire/checklist and exactly one Start action; all interruption paths remain non-completion.

- [ ] **Step 10: Commit only Task 5 files**

```powershell
git add app/src/main/java/org/mindanchor/advisory/AdvisoryViewModel.kt app/src/main/java/org/mindanchor/advisory/AdvisoryHomeCard.kt app/src/main/java/org/mindanchor/advisory/AdvisoryScreen.kt app/src/main/java/org/mindanchor/advisory/AdvisorySettingsSection.kt app/src/main/java/org/mindanchor/launcher/HomeScreen.kt app/src/main/java/org/mindanchor/settings/SettingsScreen.kt app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt app/src/main/res/values/strings.xml app/src/test/java/org/mindanchor/advisory/AdvisorySourceBoundaryTest.kt app/src/androidTest/java/org/mindanchor/advisory/AdvisoryScreenTest.kt app/src/androidTest/java/org/mindanchor/advisory/AdvisoryPrefsTest.kt app/src/androidTest/java/org/mindanchor/launcher/LauncherUiTest.kt
git commit -m "feat: add deliberate historical advisory delivery UI"
```

### Task 6: Extend continuity snapshot and replacement restore to v4 without moving v1-v3 hashes

**Files:**

- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuityHashVersionTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuitySnapshotCodecTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ContinuitySnapshotRepositoryTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt`

- [ ] **Step 1: Write failing version, projection, canonical-order, restore, and no-synthesis tests**

Extend `ContinuityContractTest.kt`:

```kotlin
@Test fun `snapshot versions one through four remain supported`() {
    assertEquals(3, ContinuityContract.PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION)
    assertEquals(4, ContinuityContract.SNAPSHOT_FORMAT_VERSION)
    assertEquals(setOf(1, 2, 3, 4), ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS)
}
```

Extend `ContinuityHashVersionTest.kt` with literal v1, v2, and v3 projections and their existing expected hashes. Do not derive an old projection by copying the new payload and clearing fields. Add v4 tests proving:

- opportunity order `(presentedAt, id)` is canonical;
- event order `(occurredAt, episodeId, sequence, id)` is canonical;
- either new list changes a v4 content hash;
- the same lists do not change v1/v2/v3 hashes.

Extend codec tests to reject Program 3 fields smuggled into format 1, 2, or 3 JSON. Extend restore tests to reject a bad opportunity content hash or broken event chain before any Room/DataStore mutation, merge exact rows with `INSERT OR IGNORE`, restore twice without duplicates, and keep prefs false/false/null. Add a source test proving restore recapture calls `capture(now, reconcileDueOutcomes = false)`.

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.continuity.ContinuityContractTest" --tests "org.mindanchor.continuity.ContinuityHashVersionTest" --tests "org.mindanchor.continuity.ContinuitySnapshotCodecTest" --tests "org.mindanchor.continuity.RestoreCoordinatorTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ContinuitySnapshotRepositoryTest,org.mindanchor.continuity.ContinuityRoundTripTest
```

Expected: FAIL because snapshot v4, Program 3 DTOs/lists, chain preflight, and restore preference reset do not exist.

- [ ] **Step 3: Append field-for-field DTOs and mappers**

Add `AdvisoryOpportunityDto` and `InterventionEpisodeEventDto` with exactly the same names/types/order as their entities. Add complete `toDto()`/`toEntity()` mappers. Append only these fields to `ContinuityPayload`:

```kotlin
// --- Program 3. Appended so frozen v1/v2/v3 projections do not move. ---
val advisoryOpportunities: List<AdvisoryOpportunityDto> = emptyList(),
val interventionEpisodeEvents: List<InterventionEpisodeEventDto> = emptyList(),
```

No advisory preference or build authorization field belongs in `ContinuityPayload`.

- [ ] **Step 4: Freeze a literal v3 projection, then add v4 sorting and hashing**

In `ContinuityContentHasher.kt`, keep existing v1/v2 projection classes literal. Rename the current Program 2 projection to `ContinuityPayloadV3` and list the exact 20 baseline fields from “Frozen Backward-Compatibility Baseline.” Add:

```kotlin
@Serializable
private data class ContinuityPayloadV4(
    val journalEntries: List<JournalEntryDto>,
    val contextRows: List<JournalContextDto>,
    val morningMeasures: List<MorningMeasureDto>,
    val notes: List<NoteDto>,
    val letters: List<LetterDto>,
    val readLetterDates: List<String>,
    val frictionedApps: List<String>,
    val alwaysOpenApps: List<String>,
    val continuityChanges: List<ContinuityChangeDto>,
    val legacyBackupJson: String,
    val researchLedgerEvents: List<ResearchLedgerEventDto>,
    val studyPhases: List<StudyPhaseDto>,
    val passiveRawProvenance: List<PassiveRawProvenanceDto>,
    val passiveSourceReads: List<PassiveSourceReadDto>,
    val passiveSourceLags: List<PassiveSourceLagDto>,
    val passiveBaselineSegments: List<PassiveBaselineSegmentDto>,
    val passivePipelineRuns: List<PassivePipelineRunDto>,
    val passiveWindowRevisions: List<PassiveWindowRevisionDto>,
    val passiveDailyRevisions: List<PassiveDailyRevisionDto>,
    val passiveObservationDecisions: List<PassiveObservationDecisionDto>,
    val advisoryOpportunities: List<AdvisoryOpportunityDto>,
    val interventionEpisodeEvents: List<InterventionEpisodeEventDto>,
)
```

The implementation must spell out the omitted frozen fields from the baseline section; the ellipsis above explains ordering and must not appear in source. Select projection strictly from the snapshot's own `formatVersion`. Before v4 encoding/hash, sort:

```kotlin
advisoryOpportunities.sortedWith(compareBy(AdvisoryOpportunityDto::presentedAt, AdvisoryOpportunityDto::id))
interventionEpisodeEvents.sortedWith(
    compareBy(
        InterventionEpisodeEventDto::occurredAt,
        InterventionEpisodeEventDto::episodeId,
        InterventionEpisodeEventDto::sequence,
        InterventionEpisodeEventDto::id,
    ),
)
```

Do not edit old expected hash constants/resources.

- [ ] **Step 5: Make the codec version gate additive and strict**

Add the two exact top-level names to a Program 3 field set. Reject them for formats 1-3 and allow them for 4. Formats 1-3 decode with empty Program 3 lists. Unknown top-level fields remain rejected under the existing strict gate.

Update `ContinuityContract` exactly:

```kotlin
const val PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1
const val PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION = 2
const val PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION = 3
const val SNAPSHOT_FORMAT_VERSION = 4
val SUPPORTED_SNAPSHOT_FORMAT_VERSIONS = setOf(1, 2, 3, 4)
```

- [ ] **Step 6: Capture a reconciled v4 snapshot without changing restore recapture**

Add Program 3 lists to `RoomRows` and capture them inside the existing Room transaction. Extend the constructor with a nullable/local reconciler and the method signature:

```kotlin
suspend fun capture(
    now: Long,
    reconcileDueOutcomes: Boolean = true,
): ContinuitySnapshot {
    if (reconcileDueOutcomes) {
        advisoryOutcomeReconciler?.reconcile(
            now = now,
            zoneId = ZoneId.systemDefault(),
            requestCheckpoint = false,
        )
    }
    // Existing transaction/canonical build follows.
}
```

Production constructors pass `RoomAdvisoryOutcomeReconciler`; existing tests may pass null except tests that verify reconciliation. Restore verification recapture must explicitly pass `false` so restoring an old due window cannot create a new event and then fail its own content-hash comparison.

- [ ] **Step 7: Preflight hashes/chains, merge append-only rows, and reset runtime gates**

Before any restore mutation:

1. verify every opportunity by recomputing `contentHash` and its deterministic `id`;
2. group events by `episodeId` and require `AdvisoryCodec.verifyEpisodeChain == VALID` for every non-empty group;
3. validate enum names, payload schema version 1, event payload shape, registry tuple/hash/catalog hash, and allowed transition sequence;
4. reject all Program 3 data atomically if one row fails.

Add both tables to fresh-profile preflight. In the Room merge transaction, call `insertOpportunity` and `insertEvents` with `INSERT OR IGNORE`; do not append interruption, completion, outcome, or continuity-change rows during restore. In the DataStore merge stage call:

```kotlin
advisoryPrefs.disableAfterRestore()
```

Never restore BuildConfig authorization. Recapture with `reconcileDueOutcomes = false`; compare using the restored snapshot's own format version.

- [ ] **Step 8: Run focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.continuity.ContinuityContractTest" --tests "org.mindanchor.continuity.ContinuityHashVersionTest" --tests "org.mindanchor.continuity.ContinuitySnapshotCodecTest" --tests "org.mindanchor.continuity.RestoreCoordinatorTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ContinuitySnapshotRepositoryTest,org.mindanchor.continuity.ContinuityRoundTripTest
```

Expected: PASS; v1-v3 hashes are unchanged, v4 is canonical, corrupt Program 3 evidence mutates nothing, duplicate restore is idempotent, no events are synthesized, and runtime gates restore off.

- [ ] **Step 9: Commit only Task 6 files**

```powershell
git add app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt app/src/test/java/org/mindanchor/continuity/ContinuityHashVersionTest.kt app/src/test/java/org/mindanchor/continuity/ContinuitySnapshotCodecTest.kt app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt app/src/androidTest/java/org/mindanchor/continuity/ContinuitySnapshotRepositoryTest.kt app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt
git commit -m "feat: preserve Program 3 evidence in continuity v4"
```

### Task 7: Extend research export and the frozen data dictionary to v4

**Files:**

- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExport.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExportCodec.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExportBuilder.kt`
- Modify: `app/src/main/java/org/mindanchor/research/ResearchDataDictionary.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ResearchExportDisclosureTest.kt`
- Modify: `app/src/test/java/org/mindanchor/research/ResearchDataDictionaryTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ResearchExportBuilderTest.kt`
- Create: `app/src/test/resources/research/data-dictionary-mindanchor-research-v4.json`
- Create: `app/src/test/resources/research/research-export-mindanchor-research-v4.json`

- [ ] **Step 1: Write failing v4 compatibility, coverage, disclosure, and builder tests**

Extend `ResearchExportCodecTest.kt` to assert:

```kotlin
@Test fun `research versions one through four remain supported`() {
    assertEquals("mindanchor-research-v3", ContinuityContract.PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION)
    assertEquals("mindanchor-research-v4", ContinuityContract.RESEARCH_DICTIONARY_VERSION)
    assertEquals(
        setOf("mindanchor-research-v1", "mindanchor-research-v2", "mindanchor-research-v3", "mindanchor-research-v4"),
        ContinuityContract.SUPPORTED_RESEARCH_DICTIONARY_VERSIONS,
    )
}
```

Keep all current v1-v3 hash assertions unchanged. Add tests that v4 list order is canonical, either new list changes v4 content hash, v1-v3 reject smuggled Program 3 fields, and malformed opportunity/event hashes fail verification.

Extend `ResearchDataDictionaryTest.kt` reflection coverage so every DTO field in both new lists has exactly one variable with the correct dataset. Assert the four attestation Booleans have `VariableProvenance.SELF_REPORTED`; identifiers, hashes, versions, gates, timestamps, state-machine events, and aggregate delivery fields are `SYSTEM_RECORDED`. Assert dictionary descriptions contain no diagnostic/current-state/efficacy interpretation.

Extend `ResearchExportBuilderTest.kt` to insert one complete opportunity/episode chain, run due-outcome reconciliation, and assert the export includes all rows in canonical order with a valid chain and missing-outcome closure. A second test exports stopped-only history and asserts no outcome-window rows.

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.continuity.ResearchExportCodecTest" --tests "org.mindanchor.continuity.ResearchExportDisclosureTest" --tests "org.mindanchor.research.ResearchDataDictionaryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ResearchExportBuilderTest
```

Expected: FAIL because research v4, the two export lists, `SELF_REPORTED`, dictionary variables, and builder reconciliation do not exist.

- [ ] **Step 3: Append the two v4 datasets and freeze the v3 projection literally**

Append to `ResearchExport` after all Program 2 fields:

```kotlin
// --- Program 3. Appended so frozen v1/v2/v3 projections do not move. ---
val advisoryOpportunities: List<AdvisoryOpportunityDto> = emptyList(),
val interventionEpisodeEvents: List<InterventionEpisodeEventDto> = emptyList(),
```

In `ResearchExportCodec`, retain literal `ResearchContentV1` and `ResearchContentV2` projections. Rename the current full projection to literal `ResearchContentV3` without changing its field names/order. Add this complete projection and select it only for v4; never clear new fields on the live object and hash that as an old version:

```kotlin
@Serializable
private data class ResearchContentV4(
    val journalEntries: List<JournalEntryDto>,
    val contextFacts: List<JournalContextDto>,
    val contextInferences: List<JournalContextDto>,
    val morningMeasures: List<MorningMeasureDto>,
    val ledgerEvents: List<ResearchLedgerEventDto>,
    val ledgerHeadHash: String,
    val ledgerEventCount: Int,
    val ledgerIntegrity: LedgerIntegrity,
    val ledgerHighWaterCount: Int,
    val studyPhases: List<StudyPhaseDto>,
    val protocolRegistry: List<EvidenceProtocol>,
    val protocolCatalogSha256: String,
    val transformations: List<Transformation>,
    val transformationSetVersion: String,
    val missingData: List<MissingDataRecord>,
    val missingDataWindowStart: String?,
    val missingDataWindowThrough: String?,
    val missingDataPolicyVersion: String,
    val missingDataStatement: String,
    val dataDictionarySha256: String,
    val passiveRawProvenance: List<PassiveRawProvenanceDto>,
    val passiveSourceReads: List<PassiveSourceReadDto>,
    val passiveSourceLags: List<PassiveSourceLagDto>,
    val passiveBaselineSegments: List<PassiveBaselineSegmentDto>,
    val passivePipelineRuns: List<PassivePipelineRunDto>,
    val passiveWindowRevisions: List<PassiveWindowRevisionDto>,
    val passiveDailyRevisions: List<PassiveDailyRevisionDto>,
    val passiveObservationDecisions: List<PassiveObservationDecisionDto>,
    val advisoryOpportunities: List<AdvisoryOpportunityDto>,
    val interventionEpisodeEvents: List<InterventionEpisodeEventDto>,
)
```

Use the same canonical ordering as continuity. Strict raw-field gates reject `advisoryOpportunities` and `interventionEpisodeEvents` in v1-v3 and permit them only in v4.

- [ ] **Step 4: Advance only the current research version**

```kotlin
const val PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"
const val PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v2"
const val PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v3"
const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v4"
val SUPPORTED_RESEARCH_DICTIONARY_VERSIONS = setOf(
    PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION,
    PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION,
    PROGRAM_TWO_RESEARCH_DICTIONARY_VERSION,
    RESEARCH_DICTIONARY_VERSION,
)
```

The snapshot version remains 4 from Task 6; the encrypted envelope stays version 1.

- [ ] **Step 5: Build v4 from a reconciled, transactionally consistent Room read**

At the beginning of ordinary export build, call `advisoryOutcomeReconciler.reconcile(exportedAt, ZoneId.systemDefault())`. Then read both tables inside the builder's existing Room transaction and map/sort them. Verify opportunity hashes and episode chains before emitting. On integrity failure, fail export with a typed verification result; never omit the bad rows and never rewrite them.

The export carries persisted historical gate values and build mode. It does not carry current BuildConfig authorization or current prefs as if they were historical evidence.

- [ ] **Step 6: Add complete dictionary datasets and provenance**

Extend the closed sets:

```kotlin
enum class DictionaryDataset {
    JOURNAL_ENTRIES,
    JOURNAL_CONTEXT,
    MORNING_MEASURES,
    RESEARCH_LEDGER_EVENTS,
    STUDY_PHASES,
    MISSING_DATA,
    PASSIVE_RAW_PROVENANCE,
    PASSIVE_SOURCE_READS,
    PASSIVE_SOURCE_LAGS,
    PASSIVE_BASELINE_SEGMENTS,
    PASSIVE_PIPELINE_RUNS,
    PASSIVE_WINDOW_REVISIONS,
    PASSIVE_DAILY_REVISIONS,
    PASSIVE_OBSERVATION_DECISIONS,
    ADVISORY_OPPORTUNITIES,
    INTERVENTION_EPISODE_EVENTS,
}

enum class VariableProvenance {
    USER_AUTHORED,
    USER_REPORTED,
    SELF_REPORTED,
    DERIVED_STRUCTURAL,
    SYSTEM_RECORDED,
    MIXED,
}
```

Append `advisoryOpportunities()` and `interventionEpisodeEvents()` to `ResearchDataDictionary.dictionary.variables`. Define one `DictionaryVariable` per DTO property. Use these exact allowed-value sets:

```kotlin
val buildModes = AdvisoryBuildMode.entries.map { it.name }
val reviewStatuses = ClinicalReviewStatus.entries.map { it.name }
val eventTypes = EpisodeEventType.entries.map { it.name }
val sourceStatuses = listOf(PassiveDataStatus.AVAILABLE_FINAL.name)
val sourceStates = listOf(PassiveObservationState.SUSTAINED_DEVIATION.name)
val missingOutcomeReasons = listOf(MissingOutcomeReason.NO_REGISTERED_COMPATIBLE_INSTRUMENT.name)
```

Describe `sourceExplanation` as “The frozen mechanical explanation stored by the finalized Program 2 decision; not a diagnosis or current-state claim.” Describe aggregate duration/cycles as operational delivery measurements only. Describe the attestation payload as facts self-reported by the one manual Start action. Describe missing closure as absence of a compatible registered instrument, never a negative outcome.

- [ ] **Step 7: Freeze v4 golden resources without altering older resources**

Run the new byte-for-byte golden assertions once to obtain their actual canonical JSON in the assertion diff, add that exact complete JSON to the two v4 resource files with `apply_patch`, and rerun. Review every field before accepting the files. Do not copy v3 and hand-edit hashes. The tests compare exact bytes/canonical JSON and assert the computed dictionary SHA-256 and export content SHA-256 each match `[0-9a-f]{64}` and remain identical on a second encode.

Verify old resources are untouched:

```powershell
git diff --exit-code -- app/src/test/resources/research/data-dictionary-mindanchor-research-v2.json app/src/test/resources/research/data-dictionary-mindanchor-research-v3.json app/src/test/resources/research/research-export-mindanchor-research-v2.json
```

Expected: no output and exit code 0.

- [ ] **Step 8: Add explicit research disclosure tests**

Require the v4 carried dictionary/export statement to disclose:

- eligibility was based only on a finalized historical recorded-data decision;
- the source local date and finalization time are carried;
- the Start event is a self-report from one deliberate action, not sensor/Journal/LLM inference;
- no diagnostic or current-state conclusion is made;
- no success/failure or treatment effect is inferred;
- no compatible outcome instrument was registered, so due windows close missing;
- opportunities/events are append-only and hash-verifiable;
- ordinary/public delivery had an empty allowlist at this version.

The test rejects the words/phrases `diagnosed`, `you are anxious`, `panic detected`, `treatment worked`, `successful intervention`, and `failed intervention` case-insensitively.

- [ ] **Step 9: Run focused tests and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.continuity.ResearchExportCodecTest" --tests "org.mindanchor.continuity.ResearchExportDisclosureTest" --tests "org.mindanchor.research.ResearchDataDictionaryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ResearchExportBuilderTest
```

Expected: PASS; old export hashes/resources remain fixed, v4 is complete and canonical, chains verify, attestation provenance is self-reported, and no outcome claim is present.

- [ ] **Step 10: Commit only Task 7 files**

```powershell
git add app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt app/src/main/java/org/mindanchor/continuity/ResearchExport.kt app/src/main/java/org/mindanchor/continuity/ResearchExportCodec.kt app/src/main/java/org/mindanchor/continuity/ResearchExportBuilder.kt app/src/main/java/org/mindanchor/research/ResearchDataDictionary.kt app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt app/src/test/java/org/mindanchor/continuity/ResearchExportDisclosureTest.kt app/src/test/java/org/mindanchor/research/ResearchDataDictionaryTest.kt app/src/androidTest/java/org/mindanchor/continuity/ResearchExportBuilderTest.kt app/src/test/resources/research/data-dictionary-mindanchor-research-v4.json app/src/test/resources/research/research-export-mindanchor-research-v4.json
git commit -m "feat: export Program 3 evidence with dictionary v4"
```

### Task 8: Lock static safety boundaries and write the non-activating operational evidence runbook

**Files:**

- Create: `app/src/test/java/org/mindanchor/advisory/ProgramThreeBoundaryTest.kt`
- Create: `docs/qa/program-3-adaptive-delivery-runbook.md`
- Create: `docs/qa/program-3-adaptive-delivery-evidence.md`
- Modify: `docs/RELEASING.md`
- Modify: `app/src/test/java/org/mindanchor/launcher/ClinicalReviewWordlistTest.kt`

- [ ] **Step 1: Write the failing whole-boundary and evidence-contract tests**

`ProgramThreeBoundaryTest.kt` scans source/build/docs and asserts:

```kotlin
@Test fun `Program 3 adds no component permission or invasive API`() {
    assertSourceAbsent("app/src/main/AndroidManifest.xml", listOf("Advisory", "program3"))
    assertAdvisorySourcesAbsent(
        listOf(
            "NotificationManager", "NotificationCompat", "Vibrator", "VibrationEffect",
            "startForeground", "ForegroundService", "WorkManager", "HealthConnectClient",
            "Coros", "Wearable", "JournalEntry", "JournalContext", "JournalRepository",
            "NoteActivity", "NotesPrefs", "LlmPrefs", "Narrator",
            "AccessibilityService", "SYSTEM_ALERT_WINDOW", "startLockTask", "NotificationManager.Policy",
            "OkHttp", "Retrofit",
        ),
    )
}

@Test fun `ordinary build and source defaults are closed`() {
    assertTrue(appBuildFile().contains("PROGRAM3_PERSONAL_RESEARCH\", \"false\""))
    assertTrue(appBuildFile().contains("PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED\", \"false\""))
    assertTrue(advisoryPrefsSource().contains("masterAdvisoryEnabled: Boolean = false"))
    assertTrue(advisoryPrefsSource().contains("deliveryAllowed: Boolean = false"))
}

@Test fun `runbook cannot claim activation while inherited evidence is pending`() {
    val evidence = file("docs/qa/program-3-adaptive-delivery-evidence.md").readText()
    assertTrue(evidence.contains("Activation decision: NOT_APPROVED"))
    assertTrue(evidence.contains("Program 2 physical-device evidence: NOT_COMPLETE"))
    assertTrue(evidence.contains("Program 0 replacement/battery evidence: NOT_COMPLETE"))
    assertTrue(evidence.contains("cyclic-sighing@1 clinical review: NOT_REVIEWED"))
}
```

Also assert:

- only `cyclic-sighing@1` and the two supplied SHA-256 values appear in deliverable allowlists;
- source eligibility compares exactly `AVAILABLE_FINAL` and `SUSTAINED_DEVIATION`;
- no advisory source calls `priorDecisions`;
- the UI has no Q&A/checklist controls and exactly one Start callback;
- all event inserts use `OnConflictStrategy.IGNORE` and both no-update/no-delete trigger names exist;
- no success/failure field or event exists;
- `AdvisoryScreen.kt`, `AdvisoryHomeCard.kt`, `AdvisorySettingsSection.kt`, and the relevant strings block carry `@wording-reviewed`;
- `docs/RELEASING.md` says public release remains zero-delivery until exact protocol and copy review, and personal delivery remains blocked until evidence plus explicit owner activation.

- [ ] **Step 2: Run the boundary tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.advisory.ProgramThreeBoundaryTest" --tests "org.mindanchor.launcher.ClinicalReviewWordlistTest"
```

Expected: FAIL because the runbook/evidence contract and Program 3 release text do not exist yet, and any missing static sentinel is reported by exact path.

- [ ] **Step 3: Write the operational runbook with observable pass criteria**

`program-3-adaptive-delivery-runbook.md` must contain these serial, physical-device procedures and artifact requirements:

1. record app commit, APK SHA-256, signing certificate, device/model/API, time zone, tester, and test start/end;
2. attach the approved Program 2 whole-review finding and completed eight-row Program 2 device artifact;
3. attach the completed Program 0 three-restore log and 24-hour battery/background log;
4. build ordinary debug/release and prove zero card/Start path even with eligible database fixtures;
5. build personal research with only the personal property and prove operational evidence remains closed;
6. after external approval only, build with both explicit properties, then deliberately enable master and delivery settings;
7. insert/produce a finalized historical `AVAILABLE_FINAL + SUSTAINED_DEVIATION` decision and record that the one card shows the source local date and finalization time without current-state wording;
8. prove provisional/no-deviation/corrupt/missing-provenance/latest-ineligible decisions expose nothing and never fall back to older decisions;
9. dismiss once and verify one immutable `DISMISSED` event and no reappearance for the same opportunity;
10. open evidence and verify registry target/exclusions/contraindications/review/stop text, no Q&A/checklist, and one Start attestation action;
11. Start and verify attested/started events, exact 2/1/6-second visual sequence, foreground-only behavior, and five-minute cap;
12. separately exercise Stop, discomfort, Back, background, process death, and kill switch; verify each exact terminal event and zero completions;
13. complete one exact maximum-duration run; verify completion plus outcome-window-open, then advance through the due window and verify one close-missing event with `NO_REGISTERED_COMPATIBLE_INSTRUMENT`;
14. prove cooldown starts at `STARTED`, not presentation/dismissal/completion;
15. capture/export, restore on a replacement/test phone, compare v4 content hashes/chains, restore twice, and verify prefs return off with no synthesized terminal/outcome events;
16. inspect notification shade, vibration history, running services, overlays, DND, lock task, network, Health Connect accesses, and battery diagnostics; expected Program 3 activity is none;
17. record deviations as facts without converting them into efficacy, safety, or clinical claims.

Every row has `Observed result`, `Artifact path`, `Timestamp`, `Tester`, and `Pass/Fail` columns. The runbook says the tests are not complete until populated with observed evidence; screenshots/logs contain no Journal/Note text.

- [ ] **Step 4: Create an honest non-approval evidence record**

Create `program-3-adaptive-delivery-evidence.md` with exact current state:

```markdown
# Program 3 adaptive delivery evidence

- Activation decision: NOT_APPROVED
- Program 2 whole-review approval: NOT_RECORDED
- Program 2 physical-device evidence: NOT_COMPLETE
- Program 0 replacement/battery evidence: NOT_COMPLETE
- cyclic-sighing@1 clinical review: NOT_REVIEWED
- Program 3 physical-device runbook: NOT_EXECUTED
- Ordinary/public deliverable protocol count: 0
- Personal research build authorization default: false
- Operational-evidence build gate default: false
- Master advisory opt-in default: false
- Delivery/kill switch default: false

No human-facing Program 3 delivery build is authorized by this document.
```

Do not add invented device results, dates, screenshots, approvals, or clinical sign-off.

- [ ] **Step 5: Add release rules without activating a gate**

In `docs/RELEASING.md`, add a Program 3 section stating:

- public release requires the exact protocol definition and every new copy surface to be `REVIEWED_AND_ACCEPTED`, a non-empty explicit ordinary allowlist in a separately reviewed change, and the clinical-review CI label;
- current public protocol count is zero because `cyclic-sighing@1` is `NOT_REVIEWED` and ordinary allowlist is empty;
- personal-research delivery requires completed Program 0/2/3 evidence, explicit owner activation, both build properties, master opt-in, delivery true, and exact personal allowlist membership;
- no evidence document or property may bypass source finality, provenance, active-episode, cooldown, or runtime kill switch;
- changing `NOT_REVIEWED` or either allowlist is a separate clinical/release decision, not part of Program 3 implementation.

- [ ] **Step 6: Run all verification serially and confirm GREEN**

Wait until the shared Program 2 verification has completely finished. Run one Gradle invocation at a time:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:koverXmlReportDebug
```

Expected: each command exits 0. Do not overlap these commands, do not run `--stop` against another worker's daemon, and do not represent the emulator suite as the pending physical-device evidence.

Run source-only boundary checks:

```powershell
rg -n "NotificationManager|NotificationCompat|Vibrator|VibrationEffect|startForeground|WorkManager|HealthConnectClient|AccessibilityService|SYSTEM_ALERT_WINDOW|startLockTask|OkHttp|Retrofit" app/src/main/java/org/mindanchor/advisory
rg -n "Checkbox|RadioButton|TriStateCheckbox|TextField" app/src/main/java/org/mindanchor/advisory/AdvisoryScreen.kt
rg -n "AVAILABLE_FINAL|SUSTAINED_DEVIATION|advisory-opportunity-v1|NO_REGISTERED_COMPATIBLE_INSTRUMENT" app/src/main/java/org/mindanchor/advisory app/src/main/java/org/mindanchor/continuity app/src/main/java/org/mindanchor/research
```

Expected: the first two commands have no matches; the third shows the exact policy/provenance/missing-outcome contracts. Inspect `git diff --name-only` and confirm neither `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt` nor root `AGENTS.md` was staged or changed by a Program 3 commit.

- [ ] **Step 7: Confirm gates remain closed after all automated tests**

```powershell
rg -n "Activation decision: NOT_APPROVED|NOT_COMPLETE|NOT_REVIEWED|Ordinary/public deliverable protocol count: 0" docs/qa/program-3-adaptive-delivery-evidence.md
rg -n "PROGRAM3_PERSONAL_RESEARCH\", \"false\"|PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED\", \"false\"" app/build.gradle.kts
```

Expected: all non-approval/default-off lines are present. Automated GREEN means the disabled implementation is internally consistent; it does not authorize owner or public delivery.

- [ ] **Step 8: Commit only Task 8 files**

```powershell
git add app/src/test/java/org/mindanchor/advisory/ProgramThreeBoundaryTest.kt app/src/test/java/org/mindanchor/launcher/ClinicalReviewWordlistTest.kt docs/qa/program-3-adaptive-delivery-runbook.md docs/qa/program-3-adaptive-delivery-evidence.md docs/RELEASING.md
git commit -m "test: lock Program 3 safety and activation boundaries"
```

## Coverage Matrix

| Required decision | Implementation proof |
|---|---|
| Finalized historical advisory only | Task 3 latest-overall query/policy tests; Task 5 source date/as-of UI; Task 8 device cases |
| `AVAILABLE_FINAL + SUSTAINED_DEVIATION` | Task 3 exact enum checks; Task 8 source scan |
| No diagnosis/current-state claim | Global constraints; Tasks 3, 5, 7, 8 vocabulary/disclosure tests |
| No blocking/notification/takeover | Task 5 normal card/screen; Tasks 5 and 8 forbidden-API scans |
| Only `cyclic-sighing@1`, currently `NOT_REVIEWED` | Tasks 1 and 8 exact tuple/hash/status tests |
| Ordinary/public zero delivery | Task 1 empty ordinary allowlist; Tasks 5 and 8 default UI/release tests |
| Personal research gated/off by default | Tasks 1, 4, 5, and 8 independent build/prefs/evidence gates |
| No Q&A/checklists; one Start attestation | Tasks 4 and 5 API/Compose/static tests |
| No wearable/Journal/LLM inference | Tasks 3, 5, and 8 source boundaries |
| Append-only opportunities/events | Tasks 2-4 Room triggers, `INSERT OR IGNORE`, hashes, chains, transitions |
| No inferred success/failure without compatible outcome | Tasks 4 and 7 empty registry, exact missing reason, disclosure tests |
| v1-v3 continuity/export compatibility | Tasks 6 and 7 literal projections, strict smuggling tests, unchanged resources/hashes |
| Restore gates off and no synthesized events | Task 6 restore tests and explicit non-reconciling recapture |
| Disabled activation pending Program 0/2/clinical evidence | Pre-implementation blockers and Task 8 evidence/release rules |

## Plan Self-Review

- **Spec coverage:** Every architecture-brief and design-decision invariant is mapped above, including the design-decision override from attestation widgets to one manual Start action, the latest-only finalized source, exact protocol/hash/status, all prohibited behaviors, cooldown semantics, process/background semantics, missing outcome closure, and non-activation blockers.
- **Backward compatibility:** Snapshot/export v1-v3 are literal projections selected by the file's own version; Program 3 fields append only; old golden resources/hashes are immutable; Room 8→9 is additive and append-only.
- **Type consistency:** One `ProtocolKey`, `AdvisorySource`, policy result, opportunity DTO/entity, event DTO/entity, event vocabulary, payload schema v1, and missing-outcome reason flow through policy, Room, UI, restore, export, and dictionary. Epoch times are `Long`, event sequence is `Long`, protocol version/cycles are `Int`, dates/zones are strings at persistence boundaries, and registry domain types remain authoritative in memory.
- **Feasibility against current files:** All modified files and existing test classes named in the tasks exist in the current worktree; all new paths sit beside the current Program 1/2/continuity/launcher/settings packages. The plan adds only `PassiveDao.latestObservationDecisionNow()` to the consumed Program 2 surface and does not alter Program 2 entities/codecs/hashes.
- **Implementation specificity:** Each task begins with an explicit failing test, names exact commands and expected RED/GREEN results, gives concrete interfaces/data fields/transitions, and ends with an exact task-sized commit. There are no deferred implementation decisions or source-code placeholders.
- **Protected scope:** No task edits `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt`, root `AGENTS.md`, `HomeActivity.kt`, or `AndroidManifest.xml`; no task uses broad staging.
- **Planning verification:** This plan was prepared by complete source/spec/build-file inspection only. No Gradle task, unit test, instrumentation test, lint task, coverage task, or device procedure was executed while writing it.
