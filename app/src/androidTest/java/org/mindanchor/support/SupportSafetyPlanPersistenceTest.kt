package org.mindanchor.support

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.SafetyPlan

@RunWith(AndroidJUnit4::class)
class SupportSafetyPlanPersistenceTest {

    @get:Rule
    val rule = createAndroidComposeRule<SupportActivity>()

    private val database: AnchorDatabase by lazy {
        AnchorDatabase.get(ApplicationProvider.getApplicationContext())
    }

    @Before
    fun setUp() {
        dropTestDatabaseObjects()
        runBlocking { database.clearAllTables() }
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) { persistedWarningSigns() == null }
    }

    @After
    fun tearDown() {
        dropTestDatabaseObjects()
        runBlocking { database.clearAllTables() }
    }

    @Test
    fun doneWritesOnceToRealRoomAndNeverOnKeystrokes() {
        installWriteCounter()
        enterWarningSigns("cannot sleep")

        assertNull(persistedWarningSigns())
        assertEquals(0, writeCount())

        tapDone()
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            persistedWarningSigns() == "cannot sleep" && writeCount() == 1
        }
    }

    @Test
    fun rapidDoubleDoneProducesExactlyOneRoomWrite() {
        installWriteCounter()
        val (started, release) = blockTransactions()
        try {
            assertTrue(started.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            enterWarningSigns("call Maya")

            invokeDoneWithoutWaiting(times = 2)
            assertEquals(0, writeCount())

            release.countDown()
            rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
                persistedWarningSigns() == "call Maya" && writeCount() == 1
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun zeroDelayBackAfterDoneWaitsForVerifiedPersistence() {
        val (started, release) = blockTransactions()
        try {
            assertTrue(started.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            enterWarningSigns("call Maya")

            invokeDoneAndBackInTheSameMainLoop()

            assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
            assertNull(persistedWarningSigns())

            release.countDown()
            rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
                persistedWarningSigns() == "call Maya"
            }
            waitForOriginalActivityToClose()
        } finally {
            release.countDown()
        }
    }

    @Test
    fun competingRoomWriterUltimatelyWinsTheDisplayedPersistedState() {
        enterWarningSigns("my draft")
        tapDone()
        waitForDisplayedText("my draft")

        runBlocking {
            database.safety().savePlan(
                SafetyPlan(
                    warningSigns = "newer Room value",
                    updatedAt = System.currentTimeMillis() + 1,
                ),
            )
        }

        waitForDisplayedText("newer Room value")
    }

    @Test
    fun closingAndLaunchingAFreshActivityReadsTheSavedRoomValue() {
        enterWarningSigns("fresh reopen value")
        tapDone()
        waitForDisplayedText("fresh reopen value")

        pressSystemBack()
        waitForOriginalActivityToClose()

        ActivityScenario.launch(SupportActivity::class.java).use {
            waitForDisplayedText("fresh reopen value")
        }
    }

    @Test
    fun configurationRecreationPreservesTheUnsavedDraft() {
        enterWarningSigns("pace and stop replying")
        assertNull(persistedWarningSigns())

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithText("done").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .assertTextContains("pace and stop replying", substring = true)
        assertNull(persistedWarningSigns())
    }

    @Test
    fun backWhileEditingWritesNothing() {
        installWriteCounter()
        enterWarningSigns("do not save this")

        pressSystemBack()
        waitForOriginalActivityToClose()

        assertNull(persistedWarningSigns())
        assertEquals(0, writeCount())
    }

    @Test
    fun contactsStillAddAndRemoveThroughRealRoom() {
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.onNodeWithText("Name").performScrollTo().performTextInput("Priya")
        rule.onNodeWithText("Phone").performScrollTo().performTextInput("5551234567")
        rule.onNodeWithText("Add person").performScrollTo().performClick()

        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            runBlocking { database.safety().contactsNow() }.singleOrNull()?.phone == "5551234567"
        }

        rule.onNodeWithText("remove").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            runBlocking { database.safety().contactsNow() }.isEmpty()
        }
    }

    @Test
    fun nonmatchingReadbackKeepsDraftAndExposesAPoliteLiveRegionError() {
        installIgnoredWrite()
        enterWarningSigns("stay with Priya")
        tapDone()

        val error = "That didn't save. Your plan is still here — try again."
        waitForDisplayedText(error)
        rule.onNode(
            hasText(error) and SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        ).assertIsDisplayed()
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .assertTextContains("stay with Priya", substring = true)
        assertNull(persistedWarningSigns())
        assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
    }

    private fun enterWarningSigns(text: String) {
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .performTextInput(text)
        rule.waitForIdle()
    }

    private fun tapDone() {
        rule.onNodeWithText("done").performScrollTo().performClick()
    }

    private fun invokeDoneWithoutWaiting(times: Int) {
        val done = rule.onNodeWithText("done").fetchSemanticsNode()
        val onClick = checkNotNull(done.config[SemanticsActions.OnClick].action)
        rule.activityRule.scenario.onActivity {
            repeat(times) { assertTrue(onClick()) }
        }
    }

    private fun invokeDoneAndBackInTheSameMainLoop() {
        val done = rule.onNodeWithText("done").fetchSemanticsNode()
        val onClick = checkNotNull(done.config[SemanticsActions.OnClick].action)
        rule.activityRule.scenario.onActivity { activity ->
            assertTrue(onClick())
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun pressSystemBack() {
        rule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun waitForOriginalActivityToClose() {
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            rule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
    }

    private fun waitForDisplayedText(text: String) {
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun blockTransactions(): Pair<CountDownLatch, CountDownLatch> {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        database.transactionExecutor.execute {
            started.countDown()
            release.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return started to release
    }

    private fun installWriteCounter() {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("CREATE TABLE support_test_safety_writes (count INTEGER NOT NULL)")
        sqlite.execSQL("INSERT INTO support_test_safety_writes VALUES (0)")
        sqlite.execSQL(
            "CREATE TRIGGER support_test_count_safety_plan " +
                "AFTER INSERT ON safety_plan BEGIN " +
                "UPDATE support_test_safety_writes SET count = count + 1; END",
        )
    }

    private fun installIgnoredWrite() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER support_test_ignore_safety_plan " +
                "BEFORE INSERT ON safety_plan BEGIN SELECT RAISE(IGNORE); END",
        )
    }

    private fun persistedWarningSigns(): String? =
        database.openHelper.readableDatabase
            .query("SELECT warningSigns FROM safety_plan WHERE id = 1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun writeCount(): Int =
        database.openHelper.readableDatabase
            .query("SELECT count FROM support_test_safety_writes")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun dropTestDatabaseObjects() {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("DROP TRIGGER IF EXISTS support_test_count_safety_plan")
        sqlite.execSQL("DROP TRIGGER IF EXISTS support_test_ignore_safety_plan")
        sqlite.execSQL("DROP TABLE IF EXISTS support_test_safety_writes")
    }

    companion object {
        private const val TEST_TIMEOUT_MILLIS = 10_000L
        private const val BLOCK_TIMEOUT_SECONDS = 15L
    }
}
