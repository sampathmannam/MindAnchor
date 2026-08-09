package org.mindanchor.ci

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the structure of the clinical-review gate
 * workflow. The substantive logic (label presence,
 * wording-heavy detection) is run in CI; this test
 * pins the file's structure so a careless edit cannot
 * silently disable the gate.
 *
 * The project's review log is in `docs/CLINICAL_REVIEW.md`;
 * this gate enforces that the review happened.
 */
class ClinicalReviewGateTest {

    private val base_sha = "\$base_sha"
    private val head_sha = "\$head_sha"

    private val workflowFile: File
        get() {
            val candidates = listOf(
                ".github/workflows/clinical-review.yml",
                "../.github/workflows/clinical-review.yml",
                "../../.github/workflows/clinical-review.yml",
            )
            return candidates.map(::File).firstOrNull { it.isFile }
                ?: error(
                    "clinical-review.yml not found from working directory " +
                        "${File(".").absolutePath}. This gate must not be silently absent.",
                )
        }

    @Test
    fun `the workflow file exists and is non-empty`() {
        assertNotNull(workflowFile)
        assertTrue(
            "clinical-review.yml exists but is empty",
            workflowFile.length() > 0,
        )
    }

    @Test
    fun `the workflow triggers on pull_request with the required event types`() {
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must trigger on pull_request",
            content.contains("pull_request:"),
        )
        for (eventType in listOf("opened", "labeled", "unlabeled", "synchronize")) {
            assertTrue(
                "Workflow must listen for pull_request type '$eventType' " +
                    "so a label applied after the initial push still gates.",
                content.contains("- $eventType"),
            )
        }
    }

    @Test
    fun `the workflow requires the clinical-review-approved label`() {
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must reference the clinical-review-approved label",
            content.contains("clinical-review-approved"),
        )
    }

    @Test
    fun `the workflow fails closed (exits non-zero) on missing label`() {
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must exit 1 when the label is missing (fail-closed).",
            content.contains("exit 1"),
        )
    }

    @Test
    fun `the workflow detects strings-xml changes`() {
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must detect a strings.xml change as wording-heavy.",
            content.contains("strings.xml") || content.contains("strings\\.xml"),
        )
    }

    @Test
    fun `the workflow detects the at-wording-reviewed tag`() {
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must check for @wording-reviewed KDoc tag.",
            content.contains("@wording-reviewed"),
        )
    }

    // v0.20.1 hardening (CodeRabbit audit 2026-08-08):

    @Test
    fun `the workflow sets persist-credentials false on actions checkout`() {
        // zizmor [artipacked] warning. The default
        // actions/checkout leaves GITHUB_TOKEN in
        // .git/config; a documented credential-
        // persistence risk.
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must set persist-credentials: false on actions/checkout " +
                "to prevent GITHUB_TOKEN from leaking into .git/config.",
            content.contains("persist-credentials: false"),
        )
    }

    @Test
    fun `the workflow passes event-derived values through env not template expansion`() {
        // zizmor [template-injection] error. Direct
        // ${{ toJSON(...) }} in a `run:` block allows
        // shell injection through the value.
        val content = workflowFile.readText()
        // BASE_SHA, HEAD_SHA, EVENT_LABELS should be
        // declared in `env:` entries, not used
        // directly in shell.
        assertTrue(
            "Workflow must declare BASE_SHA in env:",
            content.contains("BASE_SHA:"),
        )
        assertTrue(
            "Workflow must declare HEAD_SHA in env:",
            content.contains("HEAD_SHA:"),
        )
        assertTrue(
            "Workflow must declare EVENT_LABELS in env:",
            content.contains("EVENT_LABELS:"),
        )
    }

    @Test
    fun `the workflow uses null-delimited paths via git diff -z and read -d empty`() {
        // Path handling: a path with whitespace (e.g.
        // a future file under app/src/main/ with a
        // space) would split across word boundaries in
        // the v0.20.0 loop. The v0.20.1 fix uses `-z`
        // and `read -d ''`.
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must call git diff with -z for null-delimited paths.",
            content.contains("git diff --name-only -z"),
        )
        assertTrue(
            "Workflow must use `read -d ''` for null-delimited path reading.",
            content.contains("read -r -d ''"),
        )
    }

    @Test
    fun `the workflow checks both base_sha and head_sha for the tag`() {
        // CodeRabbit #2: a PR can remove the tag and
        // change wording in the same diff; the v0.20.0
        // detector only checked HEAD.
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must check $base_sha in the @wording-reviewed detector",
            content.contains("\"$base_sha:$f\"") || content.contains("\$base_sha:\$f"),
        )
        assertTrue(
            "Workflow must check $head_sha in the @wording-reviewed detector",
            content.contains("\"$head_sha:$f\"") || content.contains("\$head_sha:\$f"),
        )
    }

    @Test
    fun `the workflow uses exact label match (not substring)`() {
        // CodeRabbit #3: a label like
        // "not-clinical-review-approved" would have
        // matched the v0.20.0 `grep -q` substring
        // check. v0.20.1 iterates the label list and
        // tests for exact equality.
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must use exact label match (iterate labels and compare).",
            // v0.20.1: 'if [ "$label" = "clinical-review-approved" ]'
            content.contains("[ \"\$label\" = \"clinical-review-approved\" ]") ||
                content.contains("[ \"$label\" = \"clinical-review-approved\" ]"),
        )
    }

    @Test
    fun `the workflow treats deleted files as having had the tag`() {
        // v0.20.1: a PR that *deletes* a
        // @wording-reviewed file is a wording change.
        // The v0.20.0 detector checked `git show HEAD:$f`
        // which is non-zero for deleted files, silently
        // passing the deletion through.
        val content = workflowFile.readText()
        // The detector should check $base_sha:$f even
        // when the file no longer exists at HEAD.
        assertTrue(
            "Workflow must check pre-change revision for the tag (handles deletions).",
            content.contains("base_sha:\$f") || content.contains("BASE_SHA:\$f"),
        )
    }

    @Test
    fun `the workflow runs the bash interpreter in strict mode`() {
        // set -euo pipefail is the standard hardening
        // for GitHub Actions shell scripts. Without
        // it, an unbound variable (`set -u`) silently
        // expands to "" and a failed command (`set -e`)
        // is ignored. Both are documented foot-guns in
        // the GitHub Actions security guide.
        val content = workflowFile.readText()
        assertTrue(
            "Workflow must use 'set -euo pipefail' in run: blocks.",
            content.contains("set -euo pipefail"),
        )
    }
}
