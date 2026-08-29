package org.mindanchor.journal

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.research.testLedgerRepository
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.research.MorningMeasureRepository
import org.mindanchor.research.ResearchLedgerRepository
import org.mindanchor.ui.MindAnchorTheme

/**
 * Task 6: Journal Today / Entries / Patterns, driven on a real device
 * against an in-memory Room database (so the tests never touch the shared
 * on-device DB) and the real, on-device [JournalDraftStore] (which is the
 * one thing Task 6 actually needs to prove works end to end).
 */
@RunWith(AndroidJUnit4::class)
class JournalScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var deviceIdentity: DeviceIdentityStore
    private lateinit var journalRepository: JournalRepository
    private lateinit var ledgerRepository: ResearchLedgerRepository
    private lateinit var draftStore: JournalDraftStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        deviceIdentity = DeviceIdentityStore(context)
        ledgerRepository = testLedgerRepository(context, db)
        journalRepository = JournalRepository(
            context,
            db,
            deviceIdentity,
            StructuralContextExtractor(),
            ledgerRepository.provenance,
        )
        draftStore = JournalDraftStore(context)
        runBlocking { draftStore.clear() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newViewModel(): JournalViewModel = JournalViewModel(
        journalRepository = journalRepository,
        morningMeasureRepository = MorningMeasureRepository(
                context,
                db,
                deviceIdentity,
                ledgerRepository.provenance,
            ),
            ledgerRepository = ledgerRepository,
        draftStore = draftStore,
        database = db,
    )

    private fun launch(viewModel: JournalViewModel = newViewModel()): JournalViewModel {
        rule.setContent {
            MindAnchorTheme {
                JournalScreen(viewModel = viewModel, onBack = {})
            }
        }
        rule.waitForIdle()
        return viewModel
    }

    @Test
    fun threeDestinationsAreVisibleAndSwitchContent() {
        launch()

        rule.onNodeWithTag("journal_tab_today").assertIsDisplayed()
        rule.onNodeWithTag("journal_tab_entries").assertIsDisplayed()
        rule.onNodeWithTag("journal_tab_patterns").assertIsDisplayed()

        // Today is the default destination.
        rule.onNodeWithTag("journal_title_field").assertIsDisplayed()

        rule.onNodeWithTag("journal_tab_entries").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("journal_search_field").assertIsDisplayed()
        rule.onNodeWithTag("journal_title_field").assertDoesNotExist()

        rule.onNodeWithTag("journal_tab_patterns").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("From your writing").assertIsDisplayed()
        rule.onNodeWithTag("journal_search_field").assertDoesNotExist()

        rule.onNodeWithTag("journal_tab_today").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("journal_title_field").assertIsDisplayed()
    }

    @Test
    fun todayShowsDateTitleBodyMorningCardAndSaveButton() {
        launch()

        val expectedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
        rule.onNodeWithTag("journal_date_header").assertTextEquals(expectedDate)
        rule.onNodeWithTag("journal_title_field").assertIsDisplayed()
        rule.onNodeWithTag("journal_body_field").assertIsDisplayed()
        rule.onNodeWithText("Morning check-in").assertIsDisplayed()
        rule.onNodeWithTag("journal_save_button").assertIsDisplayed()
    }

    @Test
    fun savingClearsDraftOnlyAfterSuccessfulRepositoryCreate() {
        launch()

        rule.onNodeWithTag("journal_title_field").performTextInput("A good title")
        rule.onNodeWithTag("journal_body_field").performTextInput("A durable body of text.")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { draftStore.read()?.body == "A durable body of text." }
        }

        rule.onNodeWithTag("journal_save_button").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { draftStore.read() == null }
        }

        assertNull("draft must be cleared only after a successful create()", runBlocking { draftStore.read() })

        val stored = runBlocking { db.journal().entriesNow() }
        assertTrue(
            "the entry must actually be durably saved by JournalRepository.create()",
            stored.any { it.title == "A good title" && it.body == "A durable body of text." },
        )
    }

    @Test
    fun failedRepositorySaveLeavesTheDraftVisible() {
        launch()

        // JournalEntry.create() rejects a body over MAX_BODY_LENGTH — a
        // real JournalRepository.create() failure, not a fake, and one
        // that never touches the database (so nothing else on screen
        // breaks). This is the seam the brief allows: "inject/force a
        // failure". Built from short space-separated words (not one huge
        // unbroken run of characters) so the TextField's text layout stays
        // fast to measure.
        val overLongBody = "OVERLONGMARKER " + (1..5_000).joinToString(" ") { "pad" }
        rule.onNodeWithTag("journal_title_field").performTextInput("Kept title")
        rule.onNodeWithTag("journal_body_field").performTextReplacement(overLongBody)
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { draftStore.read()?.title == "Kept title" }
        }

        // The 20,000+ character body pushes Save off the bottom of the
        // scrollable column — scroll it into view before clicking, same
        // as AnchorCoreUiTest does for a row below the fold.
        rule.onNodeWithTag("journal_save_button").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("journal_save_error").fetchSemanticsNodes().isNotEmpty()
        }

        // assertTextContains, not assertTextEquals: the field also carries
        // its "Title" label in the merged Text semantics.
        rule.onNodeWithTag("journal_title_field").assertTextContains("Kept title", substring = true)
        rule.onNodeWithTag("journal_body_field").assertTextContains("OVERLONGMARKER", substring = true)

        val draft = runBlocking { draftStore.read() }
        assertTrue("a failed save must never clear the draft", draft != null)
        assertEquals("Kept title", draft?.title)
    }

    @Test
    fun entriesListsSavedOriginalsChronologicallyAndSearchesCaseInsensitively() {
        runBlocking {
            journalRepository.create("First", "Morning walk in the park.", 1_000L, LocalDate.of(2026, 8, 26))
            journalRepository.create("Second", "A rough afternoon at work.", 2_000L, LocalDate.of(2026, 8, 27))
            journalRepository.create("Third", "UNIQUEMARKER quiet evening.", 3_000L, LocalDate.of(2026, 8, 28))
        }

        launch()
        rule.onNodeWithTag("journal_tab_entries").performClick()
        rule.waitForIdle()
        // The entries Flow's first emission arrives asynchronously off the
        // Room invalidation tracker; waitForIdle() alone doesn't wait for
        // it, so poll instead of asserting immediately.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true).fetchSemanticsNodes().size == 3
        }

        val titles = rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, titles.size)

        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true)[0].assertTextEquals("Third")
        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true)[1].assertTextEquals("Second")
        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true)[2].assertTextEquals("First")

        // Case-insensitive search over the body.
        rule.onNodeWithTag("journal_search_field").performTextInput("uniquemarker")
        rule.waitForIdle()

        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true)[0].assertTextEquals("Third")

        // Case-insensitive search over the title.
        rule.onNodeWithTag("journal_search_field").performTextReplacement("second")
        rule.waitForIdle()
        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("journal_entry_title", useUnmergedTree = true)[0].assertTextEquals("Second")
    }

    @Test
    fun patternsNeverShowsRawBodyTextOrDiagnosticLabelsAndShowsExactCopy() {
        runBlocking {
            journalRepository.create(
                "Private title",
                "PRIVATE_BODY_TEXT that must never appear in Patterns.",
                1_000L,
                LocalDate.of(2026, 8, 28),
            )
        }

        launch()
        rule.onNodeWithTag("journal_tab_patterns").performClick()
        rule.waitForIdle()

        // The exact required headings and inference copy.
        rule.onNodeWithText("From your writing").assertIsDisplayed()
        rule.onNodeWithText("Inferences").assertIsDisplayed()
        rule.onNodeWithText("No inferences are created in Program 0.").assertIsDisplayed()

        // Never the raw body or title text.
        rule.onNodeWithText("PRIVATE_BODY_TEXT that must never appear in Patterns.").assertDoesNotExist()
        rule.onNodeWithText("Private title").assertDoesNotExist()

        // Never a diagnostic/clinical-sounding label.
        listOf("diagnosis", "disorder", "clinical", "score").forEach { forbidden ->
            rule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }
}
