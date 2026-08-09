# Clinical review gate

The project has a rule, recorded in
`docs/CLINICAL_REVIEW.md`, that any wording-heavy change
must be reviewed by the project's clinical reviewer before
merge. The rule is honored by people, not enforced by
automation.

This workflow (`/.github/workflows/clinical-review.yml`)
makes the rule enforceable.

## What counts as a wording-heavy change

A change to:
1. `app/src/main/res/values/strings.xml` — any string change.
2. `app/src/main/AndroidManifest.xml` — manifest
   changes declare what the app is allowed to do on the
   user's phone. Additions like `INTERNET` or a new
   `VpnService` are user-visible consent decisions; the
   gate is the clinical-review surface for these.
3. Any Kotlin source file under `app/src/main/` whose
   KDoc carries the `@wording-reviewed` tag. A file
   owner adds this tag to flag that *changes to the
   file's wording are clinical-review-required*, even
   though the source itself is code. The detector
   checks both the pre-change and post-change
   revisions; a PR that drops the tag and changes
   wording in the same diff still fails the gate.

## How to use

1. Open a PR that touches a wording-heavy surface.
2. The PR's status check `clinical-review` starts in a
   failed state.
3. The clinical reviewer reviews the change. The reviewer's
   name and date go in `docs/CLINICAL_REVIEW.md` per the
   project's standing rule.
4. The reviewer (or the project owner) applies the
   `clinical-review-approved` label.
5. The status check turns green; the PR is unblocked.
6. The reviewer removes the label if a follow-up commit
   changes the wording again. The gate re-fails closed;
   the reviewer re-reviews and re-applies.

## Branch protection

To make this gate enforceable, the project's repo
admin must add the workflow's job name
(`Clinical review gate / Clinical review required for wording changes`)
to the required status checks on the main branch:

- Settings → Branches → Branch protection rules → main
- ☑ Require status checks to pass before merging
- ☑ Require branches to be up to date before merging
- Search for the workflow: `Clinical review gate`
- Select `Clinical review required for wording changes`

## Why the gate does not do the review

The gate cannot substitute for the substantive review. A
bot that reads the change and pronounces it safe is not a
clinical review. The project's design culture (R1 / R3 / R4
in `docs/CLINICAL_REVIEW.md`) is that the clinical reviewer
is a *person* who holds responsibility for what the
product says.

The gate enforces that the review *happened* (label
applied), not that the review was *correct* (a property
only the reviewer can attest to).

## v0.20.1 hardening

The CodeRabbit audit (2026-08-08) found a number of
issues in the v0.20.0 workflow. The v0.20.1 workflow
addresses each:

- **`persist-credentials: false`** on `actions/checkout`.
  The default behavior of `actions/checkout` is to leave
  the GITHUB_TOKEN in `.git/config`, a documented
  credential-persistence risk. zizmor `[artipacked]`
  warning.
- **Event-derived values through `env:` entries**, not
  direct template expansion in `run:`. The v0.20.0
  workflow used `${{ toJSON(...) }}` directly in a
  shell command, a template-injection risk
  (zizmor `[template-injection]` error).
- **`-z` and `read -d ''`** for path handling. The
  v0.20.0 workflow used a word-splitting loop, which
  silently breaks on paths with whitespace.
- **Both base and head** for the `@wording-reviewed`
  check. A PR can remove the tag and change wording in
  the same diff; the v0.20.0 detector only checked
  HEAD and would let the re-tag slip through.
- **Deleted files** are treated as "had the tag." A PR
  that *deletes* a `@wording-reviewed` file is a wording
  change (the wording the file carried is gone); the
  v0.20.1 detector fails closed on the deletion.
- **Exact label match**, not substring. A label like
  `not-clinical-review-approved` or
  `clinical-review-approved-and-stale` would have
  matched the v0.20.0 `grep -q 'clinical-review-approved'`
  check; v0.20.1 iterates the label list and tests
  for exact equality.

## When to remove the gate

The gate is here to stay. The day the project ships a
wording change that wasn't reviewed, the gate has failed
its job. If you find yourself wanting to bypass it, the
right move is to update `docs/CLINICAL_REVIEW.md` to record
the bypass and its rationale, not to disable the gate.

## Primary sources

- GitHub Changelog 2025-11-07, pull_request_target and
  environment branch protections changes
- "Quick Tip: Block Pull Request Merge using Labels,"
  Sequra Tech, 2025
- detekt 1.23.8 documentation, https://detekt.dev/docs/gettingstarted/gradle
- zizmor documentation,
  https://woodruffw.github.io/zizmor/
- GitHub Actions security hardening,
  https://docs.github.com/en/actions/concepts/security/script-injections
- CodeRabbit audit on PR #21, 2026-08-08
