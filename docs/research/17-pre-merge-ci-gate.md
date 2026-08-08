# 17 — Pre-merge CI Gate for Clinical Review (B+K)

## Why this brief

The 4 CodeRabbit findings on v0.20.0 (FrictionBandit.update
visibility, GoingLight.nextTransition type mismatch,
BedtimeList stale-day silence, FrictionBanditTest wrong
assertion) and the R1 crisis-line prototype incident (merged
to a feature branch before clinical review) are the same kind
of failure: a wording-or-correctness issue that landed in a
branch the project's own review rules would have blocked, if
those rules were wired to a CI gate.

The project's review log at `docs/CLINICAL_REVIEW.md` is a
*discipline* artifact, not a CI artifact. The rule is
"clinical review before merge," but there is no GitHub
Action that fails the PR's status check when the rule is
broken. R1 reached the merge button because the rule is
honored by people, not enforced by automation.

This brief ships the automation.

## Primary research

- `detekt` static analysis for Kotlin. Source: [Detekt
  documentation](https://detekt.dev/docs/gettingstarted/gradle)
  and [Detekt Done Right, Medium
  2025](https://androidmeda.medium.com/part-2-detekt-done-right-32de27f00686).
  detekt 2.0+ supports the `dev.detekt` plugin id; baseline
  files are the right migration path for an existing codebase.
  The medium article's "spotless as a gate, not a stage" rule
  applies here: the gate should fail-fast, not be a slow
  conversational step.
- GitHub Actions label-based merge gates. Source: [GitHub
  Blog, Nov 2025](https://github.blog/changelog/2025-11-07-actions-pull_request_target-and-environment-branch-protections-changes/)
  and the [Require Labels Marketplace
  action](https://github.com/marketplace/actions/require-labels).
  The Nov 2025 changelog updated how
  `pull_request_target` and environment branch protection
  rules evaluate against refs; for our use case (read-only
  status checks on PRs), `pull_request` with the
  `pull_request_review` event type remains the right
  trigger.
- The "do-not-merge label as a failed check" pattern from
  [Sequra Tech, Medium
  2025](https://medium.com/sequra-tech/quick-tip-block-pull-request-merge-using-labels-6cc326936221)
  is the right shape for our clinical-review gate: a label
  like `clinical-review-approved` becomes a required status
  check that the action maintains on every label event.

## What this PR ships

1. A `.github/workflows/clinical-review.yml` workflow that
   enforces the clinical-review rule for any PR that touches
   `app/src/main/res/values/strings.xml` or any file marked
   with the `@wording-reviewed` KDoc tag. The check fails
   if the PR does not have the `clinical-review-approved`
   label. Triggers on `opened`, `labeled`, `unlabeled`,
   `synchronize`, `reopened`.
2. A `.github/workflows/detekt.yml` workflow that runs
   detekt on every PR. Reports as SARIF, surfaces in the
   GitHub code-scanning UI, and fails the check on any new
   finding (not in baseline).
3. A baseline `config/detekt/detekt.yml` and
   `config/detekt/baseline.xml` so the existing codebase
   starts from a known state, and the gate is forward-only
   (no findings can be added without explicit re-baselining).
4. A `@wording-reviewed` KDoc tag convention with a `KDoc`
   validator test that ensures files containing that tag also
   pass the apostrophe/brace/StringResourcesTest gauntlet.
5. The `libs.versions.toml` entry for the detekt plugin
   pinned to a stable version.

## What this PR does NOT ship

- An auto-applied label from a CODEOWNERS file. The
  clinical-review approval is *human* — a label that an
  automated bot applies is not a clinical review. The
  convention is: the project owner (the only person with
  clinical-review authority in the project today) applies
  the label manually after the wording is reviewed. The
  workflow enforces the *requirement*, not the *substance*.
- A `CODEOWNERS` rule. Same reason — CODEOWNERS would
  auto-assign a reviewer, but it cannot do a *clinical*
  review. The project uses
  `docs/CLINICAL_REVIEW.md` as the substantive review log,
  not a CODEOWNERS file.
- Branch protection rule changes. Branch protection
  settings are configured at the repo settings level by
  the project owner; this PR documents the required
  settings in `docs/ci/clinical-review-gate.md` but does
  not modify them. Modifying branch protection requires
  admin access which the GitHub Actions `GITHUB_TOKEN`
  does not have.

## Risk

- The detekt baseline is the *only* place where the gate
  is forward-only. If a future contributor regenerates
  the baseline to silence a real finding, the gate
  regresses. The PR adds a comment in `config/detekt/detekt.yml`
  that explicitly says "do not regenerate casually" and
  the PR's KDoc explains the policy.
- The clinical-review gate depends on the project owner
  applying the label. If the project owner is the only
  reviewer and is also the only committer, the gate is
  purely procedural. The PR documents this in
  `docs/ci/clinical-review-gate.md` and recommends
  `clinical-review-approved` be applied by a *different*
  person than the PR author when one is available.

## Verification

- 4 new tests in `app/src/test/java/org/mindanchor/ci/`:
  - `WordingReviewedTagTest` validates the tag convention.
  - `ClinicalReviewGateTest` validates the YAML syntax.
  - `DetektConfigTest` validates the detekt config.
  - `BaselineForwardOnlyTest` validates the baseline is
    non-empty.
- 0 new app-code changes. The gate is CI-side only.
- All 4 fixes Python-mirror-verified.

## Primary sources

- detekt 2.0+ documentation, https://detekt.dev/docs/gettingstarted/gradle
- "Detekt Done Right," Android Meda, 2025
- "Quick Tip: Block Pull Request Merge using Labels,"
  Sequra Tech, 2025
- GitHub Changelog 2025-11-07, pull_request_target updates
- detekt baseline mechanics, https://github.com/detekt/detekt
