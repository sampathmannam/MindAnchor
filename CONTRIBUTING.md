# Contributing to MindAnchor

MindAnchor is a privacy-first Android launcher for the
mental-health population. It is small, single-developer
today, and built to *stay* small. The most useful thing a
new contributor can do is read this file first.

## The single most important rule

**The clinical-review log at `docs/CLINICAL_REVIEW.md` is
the project's substantive review record.** Every wording
change — every string in `strings.xml`, every
`@wording-reviewed` Kotlin file, every manifest change —
must be added to that log before merge. The
clinical-review gate (`.github/workflows/clinical-review.yml`)
blocks the merge; the rule is enforced by automation, not
by people remembering.

The reason this rule exists: the population the app serves
is at a higher base rate of harm than the general
population. A wording mistake is a clinical mistake.
A "we'll fix it in the next release" attitude about a
clinical surface is a different kind of error than the
same attitude about a UI bug. Read the project's
`docs/CLINICAL_REVIEW.md` R1–R4 entries for the
precedent.

## How the project is organized

```
app/src/main/java/org/mindanchor/
├── friction/       # The friction-gate feature (the core intervention)
├── pulse/          # Daily check-in prompts, HRV via camera
├── sleep/          # Bedtime to-do list, sunset hours
├── settings/       # Settings screen
├── launcher/       # The home screen and LauncherViewModel
├── goinglight/     # Going Light v1.1 (VpnService)
├── goinglight/.../PacketForwarder.kt  # the pure-function decision
├── data/           # DataStore codecs, repositories
├── ui/             # Cross-feature Compose primitives
├── diagnostics/    # On-device log + share
├── support/        # Crisis support screen
└── ...
```

Package-by-concern, not package-by-type. A new feature
goes in its own `feature/` directory with a `feature/`
KDoc, a `feature/...Test` directory, and a `feature/`
manifest block (if it adds a service or receiver).

## The design record

Three documents that *must* be read before any change:

1. `docs/PLAN.md` — what the project is and what it
   isn't.
2. `docs/CONCEPT.md` — why each feature exists.
3. `docs/CLINICAL_REVIEW.md` — the substantive review
   log. R1 is the canonical example of a feature that
   was reverted because the clinical review happened
   *after* the merge.

Plus the per-feature briefs in `docs/research/01–25`.
Every existing feature cites a primary source. New
features must do the same.

## Before opening a PR

1. Read `docs/PLAN.md` and the relevant brief in
   `docs/research/`.
2. If your change is wording-heavy (strings.xml,
   manifest, `@wording-reviewed` files), add an entry
   to `docs/CLINICAL_REVIEW.md` *in the same PR*. The
   clinical-review gate will block the merge; the
   review happens via the label application.
3. Run the local build:
   ```bash
   ./gradlew test
   ```
   CI is the ground truth; the local build is a
   pre-flight.
4. Add new tests. The project maintains 100% test
   pass rate; the new feature must be tested.
5. Add a brief in `docs/research/NN-feature-name.md`
   with primary sources. The brief is the evidence
   trail. Code without a brief lands as "evidence or
   it doesn't ship" (the project's own rule).

## The test pyramid

The project has four kinds of tests, in priority order:

1. **Pure-function unit tests** (`app/src/test/.../*Test.kt`).
   These are the most valuable because they are the
   most testable. The friction feature, the
   FrictionBandit, the GoingLightSchedule, the
   BedtimeList — all are pure functions, all are
   tested.
2. **Structural tests** (`StringResourcesTest`,
   `Apostrophe`, `Brace`, `Lint`, `ClinicalReviewGateTest`).
   These are the right tests for things the
   compiler cannot check: aapt's unescaped-
   apostrophe trap, brace/paren balance, lint rule
   violations, the clinical-review gate's structure.
3. **Composable tests** (`app/src/androidTest/...`).
   The instrumented tests run on a real device or
   emulator. Slow and flaky; the project uses them
   only when the Composable's runtime behavior is
   not testable as a pure function.
4. **CI workflows** (`.github/workflows/*.yml`).
   The clinical-review gate, the detekt gate, the
   build-and-test gate. These are the *meta*-tests:
   they ensure the other three kinds of tests are
   actually run.

The Python-mirror pattern (verifying pure functions
in Python before writing the Kotlin version) is the
project's day-zero sanity check. It catches drift
between the brief and the implementation.

## Code conventions

- **KDoc on the why, not the what.** Every public
  function has a KDoc that explains the design
  choice, the citation, or the trade-off. Comments
  in the body explain decisions that are not obvious
  from the type signature.
- **Pure-function split.** Data-side logic (the
  decision of *what* to do) is in a pure function.
  Language-side logic (the *wording* of the decision)
  is in a string resource. The two are reviewed by
  different people.
- **Hand-rolled DI.** The project does not use Hilt
  or any DI framework. ViewModels take
  `Application` in the constructor and read the
  data layer directly. The dependency graph is
  small enough to track by hand.
- **No `INTERNET` permission except via the Going
  Light v1.1 VpnService.** The manifest's permission
  set is minimal by design. The `NetworkCallsForbiddenTest`
  enforces "no outbound calls anywhere."
- **Apache 2.0.** The license is the project's promise
  to its users. A change that adds a copyleft dependency
  (GPL, LGPL, AGPL, or any license with a share-alike
  clause) needs a written exception from the project
  owner. Permissive licenses (MIT, BSD-2-clause,
  BSD-3-clause, ISC, Apache 2.0 itself) are fine.
- **No comments in commit messages with the literal
  `'` character.** Apostrophes in commit-message
  text break git's argument parser on some shells.
  The project uses curly quotes (`'`, `'`, `'`) in
  commit text.

## The merge gate

PRs are merged when:

1. `./gradlew test` passes (CI confirms).
2. `./gradlew detekt` passes (the static-analysis
   gate from item B+K).
3. The clinical-review gate is green (the
   `clinical-review-approved` label has been applied
   by a human reviewer; the gate does not auto-apply
   labels).
4. The PR has been reviewed by the project owner.
   The project is single-developer today, so the
   reviewer and the author are usually different
   *days*; the 24-hour review window is the
   project's review-period default.
5. The PR is squash-merged with a single
   `commit_message` that summarizes the SOTA
   evidence trail. The full report is the PR body.

## When you find a bug

Open a GitHub issue. Use the issue templates
(`bug_report.md` is the only template today). The
issue should include:

- The MindAnchor version (`vX.Y.Z` from settings).
- The Android version and device model.
- The exact reproduction steps.
- The expected vs actual behavior.
- A debug log (Settings → Share diagnostic logs).

## When you want to add a feature

Open a GitHub issue with the *research brief* before
the code. The brief is a markdown file in
`docs/research/NN-feature-name.md` with the primary
sources, the design trade, and the evidence the
project's review culture expects. A feature without
a brief will not be merged.

The brief is the conversation, not the code. The
code is the implementation of the conversation.

## See also

- `docs/PLAN.md`
- `docs/CONCEPT.md`
- `docs/CLINICAL_REVIEW.md`
- `docs/research/` — the per-feature briefs
- `SOTA-IMPROVEMENT-REPORT.md` — the most recent
  evidence-backed SOTA work, with primary citations
- `.devcontainer/README.md` — the devcontainer
  for local builds
- `.github/workflows/clinical-review.yml` — the
  clinical-review gate (item B+K of the SOTA plan)
- `.github/workflows/detekt.yml` — the static
  analysis gate (item B+K of the SOTA plan)
- `docs/research/20-coros-bridge.md` — the
  COROS Training Hub side-channel; opt-in only, the
  launcher's default promise of "zero outbound calls"
  is preserved everywhere else

## Adding a new file under `vitals/coros/`

The COROS Training Hub side-channel is the *only* place in
the app that makes outbound network calls. The
`NetworkCallsForbiddenTest` enforces this with a pinned
allowlist of 5 file paths under
`app/src/main/java/org/mindanchor/vitals/coros/`. A new
file in that package must be:

1. Added to the `corosBridgeFiles` set in
   `NetworkCallsForbiddenTest.kt` (the test will fail
   otherwise).
2. Documented in `docs/research/20-coros-bridge.md`.
3. Reviewed per the clinical-review gate — the test's
   KDoc text is wording-reviewed, and any change to
   `corosBridgeFiles` is a wording-heavy change that the
   gate catches on the file's own KDoc.

A network reference in any file outside this package is a
test failure by design. The clinical-review gate and the
`NetworkCallsForbiddenTest` are the two layers of the
"no other outbound calls" promise; both must be updated
together for the new file to build.
