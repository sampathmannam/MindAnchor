package org.mindanchor.support

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
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
        rule.waitUntil(timeoutMillis = 10_000) { persistedWarningSigns() == null }
    }

    @After
    fun tearDown() {
        dropTestDatabaseObjects()
        runBlocking { database.clearAllTables() }
    }

    @Test
    fun doneWritesOnceToRealRoomAndReopeningReadsIt() {
        installWriteCounter()
        enterWarningSigns("cannot sleep")

        assertNull(persistedWarningSigns())
        assertEquals(0, writeCount())

        tapDone()
        rule.waitUntil(timeoutMillis = 10_000) {
            persistedWarningSigns() == "cannot sleep" && writeCount() == 1
        }

        rule.activityRule.scenario.recreate()
        rule.waitUntil(timeoutMillis = 10_000) {
            persistedWarningSigns() == "cannot sleep"
        }
        rule.onNodeWithText("cannot sleep").performScrollTo().assertIsDisplayed()
        assertEquals(1, writeCount())
    }

    @Test
    fun immediateBackDuringSaveClosesOnlyAfterTheRealRoomCommit() {
        val (started, release) = blockTransactions()
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            enterWarningSigns("call Maya")
            tapDone()

            rule.onNodeWithText("Saving…").performScrollTo().assertIsDisplayed()
            pressSystemBack()
            assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
            assertNull(persistedWarningSigns())

            release.countDown()
            rule.waitUntil(timeoutMillis = 10_000) {
                persistedWarningSigns() == "call Maya"
            }
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.activityRule.scenario.state == Lifecycle.State.DESTROYED
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun recreationWhileEditingRestoresTheUnsavedDraft() {
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
    fun backWhileEditingCancelsWithoutPersisting() {
        enterWarningSigns("do not save this")

        pressSystemBack()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }

        assertNull(persistedWarningSigns())
    }

    @Test
    fun failedVerificationKeepsQueuedBackOpenWithTheDraftAndAnAccessibleError() {
        installIgnoredWrite()
        val (started, release) = blockTransactions()
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            enterWarningSigns("stay with Priya")
            tapDone()
            rule.onNodeWithText("Saving…").performScrollTo().assertIsDisplayed()

            pressSystemBack()
            release.countDown()

            val error = "That didn't save. Your plan is still here — try again."
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(error)
                .performScrollTo()
                .assertIsDisplayed()
            assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
            rule.onNodeWithText("When things are turning")
                .performScrollTo()
                .assertTextContains("stay with Priya", substring = true)
            assertNull(persistedWarningSigns())
        } finally {
            release.countDown()
        }
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

    private fun pressSystemBack() {
        rule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun blockTransactions(): Pair<CountDownLatch, CountDownLatch> {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        database.transactionExecutor.execute {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
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
}
