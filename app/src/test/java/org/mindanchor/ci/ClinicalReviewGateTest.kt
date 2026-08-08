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
}
