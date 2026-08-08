package org.mindanchor.ci

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pin that the new CI workflow files are valid YAML. The
 * project doesn't pull in a YAML library, so this test
 * uses the structural shape of a GitHub Actions workflow
 * (the `name:` and `on:` keys are required at the top
 * level) plus a small handful of invariants that would
 * catch a typo in a way the file's own content review
 * would miss.
 *
 * If a richer YAML validator is ever added, this test
 * can grow into it.
 */
class WorkflowSyntaxTest {

    private fun workflowsDir(): File {
        val candidates = listOf(
            ".github/workflows",
            "../.github/workflows",
            "../../.github/workflows",
        )
        return candidates.map(::File).first { it.isDirectory }
    }

    @Test
    fun `clinical-review yml has a name and an on-trigger`() {
        val f = File(workflowsDir(), "clinical-review.yml")
        assertTrue("clinical-review.yml must exist", f.isFile)
        val lines = f.readLines().filter { it.isNotBlank() }
        assertTrue(
            "clinical-review.yml must have a 'name:' key at the top level",
            lines.any { it.startsWith("name:") },
        )
        assertTrue(
            "clinical-review.yml must have an 'on:' key at the top level",
            lines.any { it.startsWith("on:") },
        )
    }

    @Test
    fun `detekt yml has a name and an on-trigger`() {
        val f = File(workflowsDir(), "detekt.yml")
        assertTrue("detekt.yml must exist", f.isFile)
        val lines = f.readLines().filter { it.isNotBlank() }
        assertTrue(
            "detekt.yml must have a 'name:' key at the top level",
            lines.any { it.startsWith("name:") },
        )
        assertTrue(
            "detekt.yml must have an 'on:' key at the top level",
            lines.any { it.startsWith("on:") },
        )
    }

    @Test
    fun `clinical-review yml runs on pull_request`() {
        val content = File(workflowsDir(), "clinical-review.yml").readText()
        assertTrue(
            "clinical-review.yml must trigger on pull_request.",
            content.contains("pull_request:"),
        )
    }

    @Test
    fun `detekt yml runs on pull_request and push to main`() {
        val content = File(workflowsDir(), "detekt.yml").readText()
        assertTrue(
            "detekt.yml must trigger on pull_request.",
            content.contains("pull_request:"),
        )
        assertTrue(
            "detekt.yml must trigger on push to main so the gate " +
                "runs after a merge to main too.",
            content.contains("branches: [main]") ||
                content.contains("branches:\n        - main"),
        )
    }
}
