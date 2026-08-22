# Branch protection

This document describes the GitHub branch-protection rules
for the `sampathmannam/MindAnchor` repository. The rules
exist to make a regression loud in CI before it can
silently land on `main` or on a `work/*` branch, and to
preserve the linear history that the
`docs/superpowers/plans/*.md` SOTA plans depend on.

## `main` — protected

- **Require a pull request before merging**: yes. Direct
  pushes to `main` are rejected by the branch-protection
  rule. Every change ships via a PR.
- **Required approvals**: 1. The single-approver rule is
  deliberate — the project is single-developer today, and
  a self-merge is the only path the developer has; the
  rule pins the developer-self-merge to "must be a PR,
  not a push." A two-approver rule would block the
  developer from merging their own work, which is not
  the intent of the rule today.
- **Require status checks to pass before merging**: yes.
  The required check is `CI / build` (the
  `.github/workflows/ci.yml` job that runs
  `:app:testDebugUnitTest :app:detekt :app:assembleDebug`
  plus the clinical-review gate and the
  `tools/clinician-pack.py` fresh-check). A green build
  is the only path to merge.
- **Require linear history**: yes. Squash-merge only;
  rebase-merge disabled; merge-commit disabled. A future
  `git rebase origin/main` is the only path to keep the
  history clean.
- **Allow force-pushes**: no. Force-push to `main` is
  rejected. The linear-history rule and the
  no-force-push rule together pin the conclusion that
  `main` is append-only and reviewable.
- **Allow deletion**: no. The branch cannot be deleted
  through the API; a deliberate re-creation is the only
  path.

## `work/*` — protected

- **Require a pull request before merging**: no. `work/*`
  branches are the developer's working branches; direct
  pushes to them are allowed.
- **Required approvals**: 0. The developer's working
  branch does not need a second pair of eyes.
- **Require status checks to pass before merging**: yes.
  The required check is `CI / build`, same as `main`. A
  red build on a `work/*` branch is loud; a developer
  who needs the branch for a feature-in-progress builds
  the feature on a separate `feature/*` branch and
  rebases onto `work/*` only when CI is green.
- **Require linear history**: no. A `work/*` branch is
  allowed to merge non-linearly; the linear-history
  constraint is enforced on the merge to `main`.
- **Allow force-pushes**: yes (developer-only). The
  developer can force-push their own `work/*` branch to
  rewrite history before merging to `main`. This is the
  standard "wip branch" pattern.
- **Allow deletion**: yes. The developer can delete a
  `work/*` branch once the feature has shipped to
  `main`.

## `feature/*` — unprotected (developer-only)

- All branch-protection rules OFF. A `feature/*` branch
  is a developer's sandbox; nothing is enforced.

## Workflow files

The CI workflow that drives the required check is
`.github/workflows/ci.yml`. The clinical-review gate is
`.github/workflows/clinical-review.yml` and the
detekt-only check is `.github/workflows/detekt.yml`. The
required check is the `build` job in `ci.yml`; the
clinical-review and detekt jobs are part of the same
workflow and run as part of the same `build` job (the
`./gradlew build --stacktrace` step).

## Why these rules

- **Why PR-only on `main`**: a direct push to `main`
  bypasses the SOTA-v2 plan's review layer
  (`docs/superpowers/plans/*`) and the
  `tools/clinician-pack.py` drift check. The plan's
  wording is "every wording change must be added to the
  clinical-review log before merge"; a direct push
  defeats the gate.
- **Why one approval**: the project is single-developer
  today. The one-approval rule pins the developer-self-
  merge to "must be a PR, not a push." A future team
  (a second clinician, a second developer) can raise
  the count to 2.
- **Why linear history**: the `RELEASE_NOTES_v0.X.Y.md`
  convention is to read the merge commit message as
  the release's title. A non-linear history would break
  that convention.
- **Why no force-push on `main`**: a force-push would
  rewrite the commit hashes that
  `docs/superpowers/plans/*.md` reference. The plans
  pin to a specific commit (e.g. `d30bada` for the
  v0.25.10 fix). A force-push would invalidate the
  plans.

## How to set these rules

The rules are configured under
`Settings → Branches → Branch protection rules` on
GitHub. The rule for `main` is the source of truth; the
`work/*` and `feature/*` patterns are wildcards.
