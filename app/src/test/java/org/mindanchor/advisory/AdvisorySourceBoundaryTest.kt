package org.mindanchor.advisory

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 3 Task 5 — the advisory package reads no wearable or Health
 * Connect record directly, no Journal or Note content, no LLM, and
 * takes over nothing: no notification, vibration, scheduled work,
 * Accessibility, overlay, lock task, Do Not Disturb, network, or
 * foreground-service API. The one permitted exception is the existing
 * `JournalDao.insertChange` continuity hook, which every append already
 * uses to mark a pending checkpoint — it writes one row, it does not
 * read Journal content.
 *
 * This is a source-level boundary rather than a runtime one, checked
 * once for the whole package rather than trusted per file, because a
 * single new import anywhere under `org/mindanchor/advisory` would
 * otherwise be the only evidence a privacy or safety boundary had moved.
 */
class AdvisorySourceBoundaryTest {

    /** The gradle test working directory is the `app` module. */
    private val advisoryRoot = File("src/main/java/org/mindanchor/advisory")

    private val forbidden = listOf(
        "HealthConnect", "androidx.health.connect",
        "COROS", "Wearable", "wearable",
        "JournalEntry", "JournalContext", "JournalRepository",
        "NoteActivity", "NotesPrefs",
        "org.mindanchor.narrate", "ModelStore", "ModelSlot",
        "NotificationManager", "NotificationCompat", "notify(",
        "Vibrator", "vibrate(",
        "WorkManager", "WorkRequest", "androidx.work",
        "AccessibilityService", "accessibilityservice",
        "WindowManager.addView", "TYPE_APPLICATION_OVERLAY",
        "startLockTask", "stopLockTask",
        "setInterruptionFilter", "NotificationManager.INTERRUPTION_FILTER",
        "HttpURLConnection", "OkHttpClient", "Retrofit", "java.net.Socket",
        "startForegroundService", "ForegroundServiceType",
    )

    /** insertChange is a plain DAO write, never a read of Journal content. */
    private val permittedJournalUsage = Regex("""database\.journal\(\)\.insertChange\(""")

    private fun kotlinFiles(): List<File> {
        assertTrue(
            "the advisory source root must be readable from the test working directory",
            advisoryRoot.isDirectory,
        )
        return advisoryRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun codeLines(file: File): List<String> = file.readLines(Charsets.UTF_8)
        .map { it.substringBefore("//").trim() }
        .filterNot { it.isEmpty() || it.startsWith("*") || it.startsWith("/*") }

    @Test
    fun `no advisory source references a forbidden sensing notification or network API`() {
        val files = kotlinFiles()
        assertTrue("the advisory package must contain source files", files.isNotEmpty())
        val offenders = mutableListOf<String>()
        files.forEach { file ->
            val text = codeLines(file).joinToString("\n")
            forbidden.forEach { symbol ->
                if (text.contains(symbol)) {
                    offenders += "${file.name} references forbidden symbol '$symbol'"
                }
            }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `journal usage is limited to the existing insertChange continuity hook`() {
        val files = kotlinFiles()
        val journalMentions = mutableListOf<String>()
        files.forEach { file ->
            codeLines(file).forEach { line ->
                if (line.contains("journal()") && !permittedJournalUsage.containsMatchIn(line)) {
                    journalMentions += "${file.name}: $line"
                }
            }
        }
        assertTrue(
            "only database.journal().insertChange(...) is permitted:\n${journalMentions.joinToString("\n")}",
            journalMentions.isEmpty(),
        )
    }

    @Test
    fun `the evidence screen has no checkbox radio questionnaire or text field`() {
        val screen = File(advisoryRoot, "AdvisoryScreen.kt")
        assertTrue("AdvisoryScreen.kt must exist", screen.isFile)
        val text = codeLines(screen).joinToString("\n")
        listOf("Checkbox", "RadioButton", "TriStateCheckbox", "TextField").forEach { control ->
            assertFalse("AdvisoryScreen.kt must not use $control", text.contains(control))
        }
    }

    @Test
    fun `the evidence screen wires exactly one clickable Start action`() {
        // A parameter named onStart may be threaded through more than
        // one composable on its way to the button; what must be exactly
        // one is the actual clickable element the person can tap.
        val screen = File(advisoryRoot, "AdvisoryScreen.kt")
        val startClicks = Regex("""onClick\s*=\s*onStart\b""")
        val matches = startClicks.findAll(screen.readText(Charsets.UTF_8)).count()
        assertTrue("AdvisoryScreen.kt must wire exactly one Start button, found $matches", matches == 1)
    }
}
