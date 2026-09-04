# Program 3 MacBook Handoff — 2026-09-02

This is the authoritative handoff for continuing MindAnchor development on a MacBook after the Windows-to-Mac branch recovery.

## Authoritative Git State

- Repository: `sampathmannam/MindAnchor`
- Continue from branch: `program-3-adaptive-protocol-delivery`
- Branch base before this handoff document: `1b1b386052292da3ec5dc269f63682d9cd7e03f1`
- Parent development branch: `program-2-passive-intelligence`
- Next plan: [`2026-08-31-program-3-adaptive-protocol-delivery.md`](../superpowers/plans/2026-08-31-program-3-adaptive-protocol-delivery.md)

Do not continue from `main`. On 2026-09-01, a MacBook task started from the default branch and pushed seven repair commits ending at `db76cce`. Those commits remain recoverable on `main`, but they were made against the older v0.70 baseline. Do not merge or cherry-pick them wholesale into Program 3.

## Recovery Result

The authoritative Program 2 line was cloned fresh from GitHub and placed on the dedicated Program 3 branch. The seven MacBook commits were audited rather than copied. The newer branch already contained the applicable Android build configuration, Compose test import, Room 3→4 migration, native-build workaround, and related fixes.

No Program 3 production implementation has started. The next action is Program 3 Task 1: freeze the disabled-by-default authorization contracts and composite rule provenance using the plan's RED-GREEN sequence.

## Verification Evidence

All commands below ran on 2026-09-02 from a clean GitHub clone of `program-2-passive-intelligence` at `1b1b386`, subsequently named `program-3-adaptive-protocol-delivery`.

### JVM and build

| Gate | Result |
|---|---|
| `LlmSettingsTest` clean-clone isolation check | 9/9 passed |
| Complete `:app:testDebugUnitTest` | 1,920/1,920 passed |
| `:app:assembleDebug` | Passed for ARM64 and x86_64 |
| `SafetyPlanArchitectureTest` | Passed |

### Android 14 / API 34

Device: `MindAnchorCodexApi34`, Android 14/API 34.

| Gate | Result |
|---|---|
| Focused backup, restore, continuity, and support classes | 23/23 passed; 0 skipped |
| `SupportSafetyPlanPersistenceTest`, 20 consecutive runs | 200/200 executions passed; 0 skipped |
| Complete connected suite, run 1 | 213/213 passed; 0 skipped |
| Complete connected suite, run 2 | 213/213 passed; 0 skipped |
| Complete connected suite, run 3 | 213/213 passed; 0 skipped |

Total Android test executions recorded during this handoff: 862, with zero test failures and zero skips. An initial direct-instrumentation command failed before executing tests because Gradle had removed the test APK after a connected run. Both APKs were then installed explicitly and the 20-run evidence count restarted from zero.

## Local-Only Windows Files

The earlier Windows worktree still contains two protected local-only paths:

- `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt`
- root `AGENTS.md`

They are not part of this branch and were not copied, staged, committed, or pushed. The local `LlmPrefs.kt` experiment uses a per-instance `StateFlow` and fails the cross-instance API-key round-trip test; the clean GitHub version passes. Do not copy that local experiment to the MacBook.

## MacBook Checkout

From an existing MacBook clone:

```bash
git status --short
git stash push --include-untracked -m "MacBook state before Program 3 handoff"
git fetch origin
git switch --track origin/program-3-adaptive-protocol-delivery
```

If the branch already exists locally:

```bash
git switch program-3-adaptive-protocol-delivery
git pull --ff-only
```

Then verify:

```bash
git branch --show-current
git log -1 --oneline
git status --short
./gradlew :app:testDebugUnitTest --no-parallel --max-workers=1
```

The branch must be `program-3-adaptive-protocol-delivery`, the worktree must be clean before new edits, and the unit suite must pass before Task 1 begins.

## Continuation Rules

1. Read the complete Program 3 plan before editing.
2. Execute Task 1 through Task 8 in order.
3. Start every behavior change with its specified failing test and confirm the expected RED.
4. Make only the minimum production change required for GREEN.
5. Use the exact focused test commands and task-sized commits in the plan.
6. Never edit `LlmPrefs.kt`, root `AGENTS.md`, `HomeActivity.kt`, or `AndroidManifest.xml` as part of Program 3.
7. Never commit directly to `main`.
8. Push each verified task commit to `program-3-adaptive-protocol-delivery`.

## Prompt for the MacBook Agent

```text
Continue MindAnchor only from branch program-3-adaptive-protocol-delivery.
Read docs/qa/2026-09-02-program-3-macbook-handoff.md and the complete
docs/superpowers/plans/2026-08-31-program-3-adaptive-protocol-delivery.md.

Do not work on main and do not merge or cherry-pick the seven main repair commits.
Begin with Program 3 Task 1. Follow the plan's test-first RED-GREEN sequence,
run every focused verification, make the exact narrow task commit, and push it
to program-3-adaptive-protocol-delivery. Preserve protected files and stop on
any unexplained baseline failure rather than bypassing it.
```

