package org.mindanchor.ci

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the structure of the detekt config and baseline.
 * detekt 1.23.8 is pinned in gradle/libs.versions.toml;
 * the gate runs in CI; this test pins the file structure
 * so a careless edit cannot silently disable the gate.
 */
class DetektConfigTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    @Test
    fun `the detekt config exists and is non-empty`() {
        val f = fileAt("config/detekt/detekt.yml")
        assertNotNull(f)
        assertTrue("detekt.yml exists but is empty", f.length() > 0)
    }

    @Test
    fun `the detekt config does not turn on all rules at once`() {
        // The point of the config is *which* rules, not
        // *every* rule. Enabling all of them on day one
        // would surface a thousand style findings and bury
        // the real ones (see Android Meda, "Detekt Done
        // Right," 2025).
        val content = fileAt("config/detekt/detekt.yml").readText()
        assertTrue(
            "allRules: true is the wrong default. See config/detekt/detekt.yml.",
            !content.contains("allRules: true"),
        )
    }

    @Test
    fun `the build script does not turn on all rules at once`() {
        // CodeRabbit audit (PR #20, item #15, 2026-08-08):
        // the YAML check above can pass while the build
        // script overrides allRules = true. The detekt
        // plugin reads the build script before the YAML
        // config. Both must keep allRules = false.
        val content = fileAt("build.gradle.kts").readText()
        assertTrue(
            "build.gradle.kts must keep allRules = false. The detekt " +
                "plugin reads the build script before the YAML config, " +
                "so this is the source of truth.",
            !content.contains(Regex("""allRules\s*=\s*true""")),
        )
    }

    @Test
    fun `the detekt config excludes vendored code`() {
        // v0.20.1 (CodeRabbit #18): the vendored-source
        // exclusion is now applied at the Gradle level
        // (root build.gradle.kts), not the detekt config. The
        // detekt config's `build:` block is not a valid
        // top-level key in detekt 1.23.8 and was emitting
        // a config-validation warning.
        //
        // The detekt configuration lives in the *root*
        // build.gradle.kts (subprojects { apply(plugin)
        // "io.gitlab.arturbosch.detekt" }), not in the
        // app-module build.gradle.kts. The v0.20.1 PR
        // moved the exclusion list to the root so the
        // same `detektExcludes` array is shared between
        // every subproject's Detekt and
        // DetektCreateBaselineTask.
        val gradle = fileAt("../build.gradle.kts").readText()
        assertTrue(
            "Vendored code (e.g. llama) must be excluded from detekt in " +
                "build.gradle.kts. The detekt.yml 'build:' key is not a " +
                "valid top-level key in detekt 1.23.8.",
            gradle.contains("llama"),
        )
        // The exclusion list must apply to BOTH the
        // standard Detekt task and the
        // DetektCreateBaselineTask. The v0.20.0
        // configuration only applied to Detekt, so
        // regenerating the baseline would scan the
        // vendored engine.
        assertTrue(
            "build.gradle.kts must apply the exclusion list to the " +
                "DetektCreateBaselineTask too, so baseline generation " +
                "matches normal detekt runs.",
            gradle.contains("DetektCreateBaselineTask"),
        )
        for (ex in listOf("**/build/**", "**/generated/**", "**/llama/**")) {
            assertTrue(
                "build.gradle.kts must declare the exclusion $ex " +
                    "as a centralized list (detektExcludes).",
                gradle.contains("\"$ex\""),
            )
        }
    }

    @Test
    fun `the detekt config does not use the unsupported build excludes key`() {
        // v0.20.1 (CodeRabbit #18): the detekt config
        // had a `build.excludes` block which is not a
        // valid top-level key in detekt 1.23.8. The
        // block was emitting a config-validation warning.
        val content = fileAt("config/detekt/detekt.yml").readText()
        assertTrue(
            "config/detekt/detekt.yml must not declare `build:` — it is not " +
                "a valid top-level key in detekt 1.23.8. The vendored-source " +
                "exclusions now live in build.gradle.kts.",
            !content.contains("^build:".toRegex(RegexOption.MULTILINE)),
        )
    }

    @Test
    fun `the detekt config turns on MagicNumber detection with a tuned ignore list`() {
        val content = fileAt("config/detekt/detekt.yml").readText()
        assertTrue(
            "MagicNumber rule must be on; the v1.2 bandit and cadence have evidence-cited literals.",
            content.contains("MagicNumber:"),
        )
        // The 1.5x and 0.7 in FrictionBandit, and the 7/10/14 in
        // PulseCadence, are Lally 2010 / Mintz 2020 / HeartSteps V3
        // evidence. They must not be flagged.
        for (lit in listOf("'7'", "'10'", "'14'")) {
            assertTrue(
                "MagicNumber ignore list must include $lit (Lally 2010 cadence).",
                content.contains(lit),
            )
        }
    }

    @Test
    fun `the detekt baseline exists and is a valid XML structure`() {
        val f = fileAt("config/detekt/baseline.xml")
        assertNotNull(f)
        val content = f.readText()
        assertTrue(
            "Baseline must be an XML document with the SmellBaseline root.",
            content.contains("<SmellBaseline>") && content.contains("</SmellBaseline>"),
        )
        // The baseline is initially empty by design; this is
        // the contract — any new finding fails the gate.
        assertTrue(
            "Baseline must not be silently deleted. If you mean to start " +
                "fresh, leave the empty <CurrentIssues> tag in place.",
            content.contains("<CurrentIssues>"),
        )
    }

    @Test
    fun `the libs versions toml pins a detekt version compatible with kotlin 2_0_21`() {
        val content = fileAt("gradle/libs.versions.toml").readText()
        assertTrue(
            "libs.versions.toml must pin a detekt version.",
            content.contains("detekt = \""),
        )
        // detekt 1.23.x is the last line built against Kotlin
        // 2.0.21 (per the detekt compatibility table). 2.x
        // requires Kotlin 2.4 and is not on the roadmap.
        assertTrue(
            "Pinned detekt version must be in the 1.23.x line (last " +
                "release built against the project's Kotlin 2.0.21).",
            content.contains("detekt = \"1.23."),
        )
    }
}
