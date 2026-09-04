package org.mindanchor.journal

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 6, Step 6: the automated proxy for the manual force-stop walkthrough.
 *
 * Proves the exact guarantee the brief asks for — "activity recreation
 * restores the title and body" — by launching the real [JournalActivity]
 * with [createAndroidComposeRule] (which drives it through a real
 * [androidx.test.core.app.ActivityScenario]) and calling the real
 * [androidx.test.core.app.ActivityScenario.recreate]: the standard AndroidX
 * Test API that destroys and rebuilds the real `Activity` instance,
 * re-running its real `onCreate` — the exact path a genuine configuration
 * change or process restart takes, including constructing a brand-new
 * [JournalViewModel] exactly the way [JournalActivity.onCreate] always does.
 * This is materially stronger than composing [JournalScreen] under a bare
 * `ComponentActivity` and swapping the `JournalViewModel` instance in
 * Compose state: that never exercises `JournalActivity.onCreate` itself.
 *
 * [JournalActivity.onCreate] also fire-and-forgets the real Task 4
 * [JournalLegacyImporter] against the real, on-device, process-wide
 * `journal` / `journal_migration` DataStore singletons and the real
 * on-device Room database. That import is left running rather than
 * disabled: Task 4's importer is idempotent by construction (deterministic
 * ids, `INSERT OR IGNORE`, and a completion flag), so re-triggering it
 * against the real Room database is harmless, and it never touches
 * [JournalDraftStore] — the one thing this test asserts on. But the
 * `journal_migration` completion flag it sets *is* a real hazard for
 * [JournalLegacyImporterTest], which shares this process when both classes
 * run in the same instrumentation invocation: deleting that DataStore's
 * backing file (as [JournalLegacyImporterTest]'s own `@BeforeClass` does)
 * only resets state that has never been opened in this process — once this
 * test's launch of the real activity opens that DataStore singleton, a
 * file deletion elsewhere can no longer reach its in-memory state. Verified
 * by running both classes together: without a genuine reset here,
 * [JournalLegacyImporterTest] failed both of its tests. So this test resets
 * the flag itself, through the same [JournalMigrationPrefs] singleton, in
 * both `@Before` and `@After` — the only way to actually undo what running
 * the real activity does to it — alongside the real, on-device
 * [JournalDraftStore] this test itself writes to and reads from.
 */
@RunWith(AndroidJUnit4::class)
class JournalDraftRecoveryTest {

    @get:Rule
    val rule = createAndroidComposeRule<JournalActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val draftStore = JournalDraftStore(context)
    private val migrationPrefs = JournalMigrationPrefs(context)

    @Before
    fun setUp() {
        runBlocking {
            draftStore.clear()
            migrationPrefs.clear()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            draftStore.clear()
            migrationPrefs.clear()
        }
    }

    @Test
    fun activityRecreationRestoresTheTitleAndBody() {
        rule.waitForIdle()

        rule.onNodeWithTag("journal_title_field").performTextReplacement("Recovery title")
        rule.onNodeWithTag("journal_body_field").performTextReplacement("Recovery body text.")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { draftStore.read()?.title == "Recovery title" }
        }

        // The real thing: destroys and rebuilds the real JournalActivity —
        // re-running its real onCreate, which constructs a brand-new
        // JournalViewModel exactly as it does on any recreation, including
        // a real process kill.
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        // The draft is read back via a suspend DataStore call in the new
        // JournalViewModel's init block; it lands a frame or two after the
        // recreated activity's first composition, so poll rather than
        // asserting immediately.
        rule.waitUntil(timeoutMillis = 10_000) {
            editableTextOf("journal_title_field") == "Recovery title"
        }

        rule.onNodeWithTag("journal_title_field").assertTextContains("Recovery title", substring = true)
        rule.onNodeWithTag("journal_body_field").assertTextContains("Recovery body text.", substring = true)
    }

    private fun editableTextOf(tag: String): String? {
        val node = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull() ?: return null
        return node.config.getOrNull(SemanticsProperties.EditableText)?.text
    }
}
