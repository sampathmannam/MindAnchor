# Program 1: Scientific Foundation — completion report

Implementation branch: `program-1-scientific-foundation`

Program 0 base: `4d15f5a`

Verified implementation head: `44003fa`

Verification date: 2026-08-30

Program 1's automated scope is complete. Nothing was pushed, merged,
tagged, published, or released. Three physical-device acceptance checks
remain explicitly pending and are listed below.

## Delivered foundation

- A complete evidence-protocol contract and registry that rejects
  incomplete or disallowed sources.
- One deliberately seeded protocol, `cyclic-sighing` v1, whose citation
  metadata is checked against the repository research index.
- An append-only, hash-chained research ledger and append-only study
  phases, enforced by SQLite triggers as well as repository rules.
- A ten-component provenance vector that opens a new phase when the
  research environment changes.
- An additive Room v6-to-v7 migration; Program 0 data remains readable
  and no destructive-migration fallback exists.
- A frozen machine-readable data dictionary, provenance classifications,
  missing-data policy, golden file, and pinned hashes.
- Research rows carried through canonical continuity snapshots,
  encrypted backup/restore, duplicate-free resume, and a versioned
  self-describing research export. Program 0 v1 snapshots/exports remain
  readable and verifiable.
- A research-log UI that records only person-selected confounders,
  preserves note text verbatim, exposes no edit/delete path, and gives no
  medication advice.
- Explicit export disclosure, release notes, and a physical QA runbook.

Program 1 does **not** add wearable sensing, state estimation,
diagnosis, clinical prediction, autonomous intervention, crisis
prediction, medication guidance, clinical scoring, or semantic
interpretation of journal text. Those boundaries remain enforced.

## Final clean gates

All automated results below were produced from a separate clean clone at
exact implementation commit `44003fa`, not from the dirty development
worktree.

| Gate | Final result |
| --- | --- |
| `:app:testDebugUnitTest` | **PASS — 1,712 tests, 0 failures/errors/skips** |
| `:app:lintDebug` | **PASS — 0 errors, 26 warnings** |
| `detekt` | **PASS — 0 unsuppressed findings** |
| `:app:koverHtmlReportDebug` / `:app:koverXmlReportDebug` | **PASS — reports generated** |
| `:app:connectedDebugAndroidTest` | **PASS — 180/180 tests** on `MindAnchorTest`, 0 failures/errors/skips |
| `tools/verify-reproducible-release.sh` | **PASS — two clean unsigned APKs were byte-identical** |

The combined JVM/lint/detekt/coverage invocation completed successfully
in 9m30s. The final connected suite completed from a cold-booted AVD in
7m08s. An earlier connected attempt was invalidated when the seven-hour-old
AVD's System UI `FinalizerWatchdogDaemon` timed out and Android killed
`system_server`; after a cold boot, the complete 180-test suite passed.
The attached physical phone `ZD2232FCR5` was never selected.

Release reproducibility used fixed
`SOURCE_DATE_EPOCH=1788074012`. Both clean unsigned release builds
produced:

`64502d315b9874a43d53be5ad4a1ec4896bd7021f2724d66bce978af71925243`

Unsigned reproducibility proves the APK content, resources, DEX, native
libraries, and manifest are deterministic. The signed path requires the
real release keystore and remains a CI/release-environment check.

### JVM line coverage

| Package | Covered lines |
| --- | --- |
| `org.mindanchor.research` | **80.9% (895/1,106)** |
| `org.mindanchor.continuity` | **45.0% (662/1,470)** |
| `org.mindanchor.continuity.crypto` | **83.8% (98/117)** |

These are JVM-only figures. Room, migration, restore, export-builder,
and Compose coverage is exercised separately by the 180 instrumented
tests.

### Detekt baseline repair

The old repository baseline did not describe the Program 0 boundary.
It was regenerated from exact base `4d15f5a`, where detekt reported 122
weighted issues, then applied unchanged to Program 1. The base-derived
file has 551 IDs and SHA-256:

`77e72e764d9ddc90479cf8f22e4cc113d9d1c6104687dd6b4236b85c94878e56`

The final branch has zero unsuppressed detekt findings; Program 1 did not
hide its own findings in a current-head baseline.

## Task-by-task RED/GREEN evidence

Every task was developed test-first. The table names the test that was
observed red before its production implementation and the command/class
used to establish green. JVM tests used
`./gradlew :app:testDebugUnitTest --tests <class>`; Android tests used
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=<class>`.

| Task | RED evidence before implementation | GREEN evidence |
| --- | --- | --- |
| 1. Freeze Program 0 hash/Program 1 contract | `ContinuitySnapshotCodecTest` failed until format projection and frozen v1 hash existed | `ContinuitySnapshotCodecTest` passed |
| 2. Evidence protocol contract | `EvidenceProtocolRegistryTest` rejected missing contract validation | `EvidenceProtocolRegistryTest` passed |
| 3. Verified seed catalogue | `EvidenceProtocolCatalogTest` failed until repository-indexed citation metadata was used | `EvidenceProtocolCatalogTest` passed |
| 4. Ledger model/hash chain | `LedgerChainTest` failed before canonical linking, tamper detection, and stable IDs | `LedgerChainTest` passed |
| 5. Provenance versions/missing data | `ProvenanceVersionsTest` and `MissingDataPolicyTest` failed before version vectors and honest windows | both classes passed, including adversarial future/empty-window cases |
| 6. Study phases/coordinator | `StudyPhaseTest` and `ResearchProvenanceCoordinatorTest` exposed non-atomic phase creation and clock rollback | both classes passed with rollback coverage |
| 7. Room v7/append-only tables | `MigrationTest` and `ResearchImmutabilityTest` failed before migration and triggers | both classes passed on emulator |
| 8. Research ledger repository | `ResearchLedgerRepositoryTest` failed before transactional append, exact-note preservation, high-water refresh, and clock clamping | full class passed on emulator |
| 9. Frozen dictionary | `ResearchDataDictionaryTest` failed before every exported field had provenance and a golden/hash pin | full class passed; dictionary hash `1cbfa2cf7552b675500583959511b8df069bf2aa932beeade1196ca6302393a9` |
| 10. Continuity snapshot integration | `ContinuitySnapshotRepositoryTest` failed before v2 rows, selective acknowledgement, and one-point-in-time reads | full class passed; competing writer barrier is deterministic |
| 11. Versioned research export | `ResearchExportCodecTest`, `ResearchExportBuilderTest`, and `ResearchExportDisclosureTest` failed before v1 compatibility, v2 verification, disclosure, and missing-data windows | all passed; frozen current content hash `860581818e1f08d165adfad6b53bb3c2836de5ce59b9f4ef82dad054d9f6e559` |
| 12. Research-log surface | `ResearchLogCardTest` failed before chips, append-only UI, medication notice, and exact verbatim-length validation | full class passed; the 500-character-plus-space regression is pinned |
| 13. Documentation/full gates | stale report placeholders and failing detekt gate were red | this report, release/runbook docs, 1,712 JVM tests, 180 Android tests, lint, detekt, coverage, and reproducibility all passed |

## Independent review closure

Independent whole-branch review found one Critical consistency defect and
multiple Important data-integrity defects. All were repaired rather than
waived:

- Snapshot and research-export Room reads now occur inside single
  transactions, with deterministic competing-writer tests.
- Verified snapshots acknowledge only the exact continuity-change IDs
  they contain.
- Journal context commits request a replacement checkpoint.
- Journal, morning-measure, ledger, and phase timestamps cannot be
  attributed before a clamped phase start after clock rollback.
- A null export output stream fails closed.
- Dictionary provenance distinguishes structural, self-reported, system,
  and mixed-origin fields; golden files and hashes were regenerated.
- Protocol registry values are deeply defensive-copied.
- Research notes preserve whitespace exactly, and UI/repository length
  validation now agrees on the exact verbatim string.
- Shared preference singleton state is reset between combined Android
  test classes.
- The pre-existing AnchorCore DataStore/Compose race now waits for the
  persisted state transition and passed five focused repetitions plus
  both full suites.

The final review repair is commit `44003fa`; its 31 affected Android
tests and detekt passed before the complete clean gates above.

## Physical checks still required

These cannot be honestly replaced by emulator/unit coverage. Steps are
documented in `docs/qa/program-1-research-runbook.md`.

1. Export through the real Android system document picker into Downloads
   and open the file in an ordinary text viewer.
2. Restore through real Drive onto a second physical phone and confirm a
   `DEVICE_CHANGE` study phase.
3. Inspect the export consent dialog on a small physical screen at the
   largest system font scale.

These checks gate production release acceptance, not Program 1's
automated implementation closure.

## Preserved unrelated work and branch scope

- The pre-existing untracked `AGENTS.md` was not added or deleted.
- The uncommitted `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt`
  belongs to another session. It was never edited, staged, reverted, or
  included in verification; clean-clone gates exclude it.
- Commit `8bbf56d` (certificate pinning) came from a concurrent session
  and is not a Program 1 deliverable. It was left intact.
- Before this report commit, the branch contained 34 commits after base
  `4d15f5a`, including that concurrent commit and the final review repairs.

## Closure verdict

Automated Program 1 implementation: **PASS**.

Critical/Important review findings: **resolved**.

Production release acceptance: **pending the three physical checks**.

Repository integration: **not performed**; no merge, push, tag, publish,
or release was requested.
