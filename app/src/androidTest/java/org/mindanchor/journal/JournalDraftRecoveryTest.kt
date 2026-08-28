package org.mindanchor.journal

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.research.MorningMeasureRepository
import org.mindanchor.ui.MindAnchorTheme

/**
 * Task 6, Step 6: the automated proxy for the manual force-stop walkthrough.
 *
 * Proves the guarantee [JournalDraftStore] exists for: a freshly
 * constructed [JournalViewModel] — the same thing [JournalActivity.onCreate]
 * builds on every recreation, including after a real process kill — reads
 * back exactly what an earlier instance last persisted. Simulates the
 * "activity recreated" moment by composing a second, independent
 * `JournalViewModel` against the same on-device [JournalDraftStore] rather
 * than by launching the real [JournalActivity]: launching the real activity
 * would also run the real Task 4 legacy importer against the real, on-device
 * `journal`/`journal_migration` DataStores, and — because those DataStores
 * are process-wide singletons — that state leaks into
 * [JournalLegacyImporterTest] when both classes run in the same
 * instrumentation process. Using the real [JournalDraftStore] here (the one
 * thing actually under test) while keeping the database and the legacy
 * importer out of it avoids that cross-test contamination.
 */
@RunWith(AndroidJUnit4::class)
class JournalDraftRecoveryTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var draftStore: JournalDraftStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java).build()
        draftStore = JournalDraftStore(context)
        runBlocking { draftStore.clear() }
    }

    @After
    fun tearDown() {
        db.close()
        runBlocking { draftStore.clear() }
    }

    private fun newViewModel(): JournalViewModel {
        val deviceIdentity = DeviceIdentityStore(context)
        return JournalViewModel(
            journalRepository = JournalRepository(db, deviceIdentity, StructuralContextExtractor()),
            morningMeasureRepository = MorningMeasureRepository(db, deviceIdentity),
            draftStore = draftStore,
            database = db,
        )
    }

    @Test
    fun aFreshViewModelInstanceRestoresTitleAndBodyFromTheDraftStore() {
        // A single composition root whose ViewModel instance can be swapped
        // from the test — ComposeContentTestRule only allows one
        // setContent() call per test, so "recreation" is simulated by
        // replacing the value this state holds with a brand-new
        // JournalViewModel, the same object JournalActivity.onCreate would
        // construct on a real recreation.
        var currentViewModel by mutableStateOf(newViewModel())
        rule.setContent {
            val vm = currentViewModel
            MindAnchorTheme {
                JournalScreen(viewModel = vm, onBack = {})
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("journal_title_field").performTextReplacement("Recovery title")
        rule.onNodeWithTag("journal_body_field").performTextReplacement("Recovery body text.")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { draftStore.read()?.title == "Recovery title" }
        }

        // The "recreation" moment: a brand-new JournalViewModel, exactly
        // what JournalActivity.onCreate constructs every time (including
        // after a real process kill) — its init block is what has to pull
        // the draft back.
        rule.runOnUiThread { currentViewModel = newViewModel() }
        rule.waitForIdle()
        // The draft is read back via a suspend DataStore call in the new
        // ViewModel's init block; it lands a frame or two after first
        // composition, so poll rather than asserting immediately.
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
