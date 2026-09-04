package org.mindanchor.advisory

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 3 Task 8 — the whole-boundary lock. Every assertion here scans
 * real source, build, and doc text rather than exercising behaviour
 * already covered elsewhere, so it fails the moment a future change
 * widens what an ordinary build can do, reintroduces an API this feature
 * deliberately never touches, or lets a document claim an activation
 * that has not happened.
 *
 * This file adds no feature behaviour. Passing it says the disabled
 * implementation is internally consistent — it is not, and must never
 * be read as, authorization to deliver anything to anyone.
 */
class ProgramThreeBoundaryTest {

    /** Working directory for `:app:test*` is this module's root, matching every other source-scanning test here. */
    private fun file(path: String): File = File(path).also {
        assertTrue("expected to find $path", it.isFile)
    }

    private fun sourceText(path: String): String = file(path).readText()

    private val advisoryRoot = File("src/main/java/org/mindanchor/advisory")

    private fun advisoryKotlinFiles(): List<File> {
        assertTrue("advisory source root must exist", advisoryRoot.isDirectory)
        return advisoryRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Comment-stripped, non-blank lines — keeps a `//` mention of a forbidden name from being a false failure. */
    private fun codeLines(file: File): String = file.readLines()
        .map { it.substringBefore("//").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("/*") }
        .joinToString("\n")

    private fun assertSourceAbsent(path: String, forbidden: List<String>) {
        val text = sourceText(path)
        forbidden.forEach { token ->
            assertFalse("$path must not reference '$token'", text.contains(token))
        }
    }

    private fun assertAdvisorySourcesAbsent(forbidden: List<String>) {
        advisoryKotlinFiles().forEach { source ->
            val text = codeLines(source)
            forbidden.forEach { token ->
                assertFalse("${source.name} must not reference forbidden symbol '$token'", text.contains(token))
            }
        }
    }

    private fun appBuildFile(): String = sourceText("build.gradle.kts")

    /**
     * [AdvisorySettings]'s defaults, not [AdvisoryPrefs]'s DataStore
     * wiring — the literal `= false` defaults live on the data class in
     * `AdvisoryContracts.kt`; `AdvisoryPrefs.kt` only maps a missing key
     * to `false` at read time, a different literal shape.
     */
    private fun advisoryPrefsSource(): String = sourceText("src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt")

    @Test
    fun `Program 3 adds no component permission or invasive API`() {
        assertSourceAbsent("src/main/AndroidManifest.xml", listOf("Advisory", "program3"))
        assertAdvisorySourcesAbsent(
            listOf(
                "NotificationManager", "NotificationCompat", "Vibrator", "VibrationEffect",
                "startForeground", "ForegroundService", "WorkManager", "HealthConnectClient",
                "Coros", "Wearable", "JournalEntry", "JournalContext", "JournalRepository",
                "NoteActivity", "NotesPrefs", "LlmPrefs", "Narrator",
                "AccessibilityService", "SYSTEM_ALERT_WINDOW", "startLockTask", "NotificationManager.Policy",
                "OkHttp", "Retrofit",
            ),
        )
    }

    @Test
    fun `ordinary build and source defaults are closed`() {
        assertTrue(appBuildFile().contains("PROGRAM3_PERSONAL_RESEARCH\", \"false\""))
        assertTrue(appBuildFile().contains("PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED\", \"false\""))
        assertTrue(advisoryPrefsSource().contains("masterAdvisoryEnabled: Boolean = false"))
        assertTrue(advisoryPrefsSource().contains("deliveryAllowed: Boolean = false"))
    }

    @Test
    fun `runbook cannot claim activation while inherited evidence is pending`() {
        val evidence = file("../docs/qa/program-3-adaptive-delivery-evidence.md").readText()
        assertTrue(evidence.contains("Activation decision: NOT_APPROVED"))
        assertTrue(evidence.contains("Program 2 physical-device evidence: NOT_COMPLETE"))
        assertTrue(evidence.contains("Program 0 replacement/battery evidence: NOT_COMPLETE"))
        assertTrue(evidence.contains("cyclic-sighing@1 clinical review: NOT_REVIEWED"))
    }

    @Test
    fun `only the one reviewed protocol and its two approved hashes appear in the deliverable allowlists`() {
        val source = sourceText("src/main/java/org/mindanchor/advisory/AdvisoryBuildAuthorization.kt")
        val hashes = Regex("\"([0-9a-f]{64})\"").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee",
                "1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
            ),
            hashes,
        )
        val protocolIds = Regex("protocolId\\s*=\\s*\"([^\"]+)\"").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("cyclic-sighing"), protocolIds)
        assertTrue(source.contains("private val ordinaryAllowlist = emptySet<ProtocolKey>()"))
    }

    @Test
    fun `source eligibility compares exactly the finalized sustained-deviation states`() {
        val policy = sourceText("src/main/java/org/mindanchor/advisory/AdvisoryPolicy.kt")
        assertTrue(policy.contains("PassiveDataStatus.AVAILABLE_FINAL"))
        assertTrue(policy.contains("PassiveObservationState.SUSTAINED_DEVIATION"))
    }

    @Test
    fun `no advisory source reaches back into prior decisions`() {
        assertAdvisorySourcesAbsent(listOf("priorDecisions"))
    }

    @Test
    fun `every advisory event insert is conflict-ignore and both append-only triggers are installed`() {
        val dao = sourceText("src/main/java/org/mindanchor/data/db/AdvisoryDao.kt")
        assertEquals(2, Regex("OnConflictStrategy\\.IGNORE").findAll(dao).count())

        val database = sourceText("src/main/java/org/mindanchor/data/db/AnchorDatabase.kt")
        assertTrue(database.contains("\"advisory_opportunities\""))
        assertTrue(database.contains("\"intervention_episode_events\""))
    }

    @Test
    fun `no success or failure vocabulary exists in the episode event or opportunity shape`() {
        val contracts = sourceText("src/main/java/org/mindanchor/advisory/AdvisoryContracts.kt")
        val eventTypeBody = contracts.substringAfter("enum class EpisodeEventType {").substringBefore("\n}")
        listOf("SUCCESS", "FAILURE").forEach { token ->
            assertFalse("EpisodeEventType must not name a $token member", eventTypeBody.contains(token))
        }

        val snapshot = sourceText("src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt")
        val opportunityBody = snapshot.substringAfter("data class AdvisoryOpportunityDto(").substringBefore("\n)")
        val eventBody = snapshot.substringAfter("data class InterventionEpisodeEventDto(").substringBefore("\n)")
        listOf("success", "failure", "Success", "Failure").forEach { token ->
            assertFalse("AdvisoryOpportunityDto must not carry a $token field", opportunityBody.contains(token))
            assertFalse("InterventionEpisodeEventDto must not carry a $token field", eventBody.contains(token))
        }
    }

    @Test
    fun `the evidence screen has no QA-checklist control and exactly one Start callback`() {
        val screen = sourceText("src/main/java/org/mindanchor/advisory/AdvisoryScreen.kt")
        listOf("Checkbox", "RadioButton", "TriStateCheckbox", "TextField").forEach { control ->
            assertFalse("AdvisoryScreen.kt must not render a $control", screen.contains(control))
        }
        assertEquals(1, Regex("onClick\\s*=\\s*onStart\\b").findAll(screen).count())
    }

    @Test
    fun `the release rules describe zero-delivery public and evidence-gated personal release`() {
        val releasing = file("../docs/RELEASING.md").readText()
        assertTrue(releasing.contains("Program 3"))
        assertTrue(releasing.contains("Current public protocol count is zero"))
        assertTrue(releasing.contains("NOT_REVIEWED"))
        assertTrue(releasing.contains("explicit owner activation"))
        assertTrue(releasing.contains("PROGRAM3_PERSONAL_RESEARCH=true"))
        assertTrue(releasing.contains("PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED=true"))
    }
}
