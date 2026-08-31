package org.mindanchor.support

import android.content.Context
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
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.SafetyPlan

@RunWith(AndroidJUnit4::class)
class SupportSafetyPlanPersistenceTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val harness = SafetyPlanRoomHarness(appContext)
    private val resultGate = AfterCommitResultGate(RoomSafetyPlanStore(harness.dao) { 100L })
    private val closeCount = AtomicInteger(0)
    private val latestViewModel = AtomicReference<SupportViewModel>()

    init {
        SupportHarnessActivity.closeCounter = closeCount
        SupportHarnessActivity.factoryProvider = { application ->
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(modelClass == SupportViewModel::class.java)
                    return SupportViewModel(application, resultGate, harness.dao, 50L)
                        .also(latestViewModel::set) as T
                }
            }
        }
    }

    @get:Rule
    val rule = createAndroidComposeRule<SupportHarnessActivity>()

    private val scenario: ActivityScenario<SupportHarnessActivity>
        get() = rule.activityRule.scenario

    @After
    fun tearDown() {
        resultGate.releaseResult()
        if (scenario.state != Lifecycle.State.DESTROYED) {
            scenario.onActivity { it.finish() }
        }
        rule.waitUntil(TEST_TIMEOUT_MILLIS) { scenario.state == Lifecycle.State.DESTROYED }
        SupportHarnessActivity.factoryProvider = null
        SupportHarnessActivity.closeCounter = null
        harness.close()
    }

    @Test
    fun doneWritesOnceToRealRoomAndNeverOnKeystrokes() {
        awaitInitialPlan()
        harness.installWriteCounter()
        enterWarningSigns("cannot sleep")

        harness.drainTransactions()
        assertNull(persistedPlan())
        assertEquals(0, harness.writeCount())

        tapDone()
        waitUntil { persistedWarningSigns() == "cannot sleep" && harness.writeCount() == 1 }
        harness.drainTransactions()
        assertEquals(1, harness.writeCount())
    }

    @Test
    fun slowRealRoomSaveQueuedBackAndLateCommitClosesOnce() {
        awaitInitialPlan()
        harness.installWriteCounter()
        val transactionGate = harness.gateTransactionExecutor()
        try {
            enterWarningSigns("call Maya")

            invokeDoneAndBackInTheSameMainLoop()
            waitForText("Still saving…")
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertEquals(0, harness.writeCount())
            assertNoText("That didn't save")

            transactionGate.release.countDown()
            waitUntil { persistedWarningSigns() == "call Maya" && harness.writeCount() == 1 }
            waitForOriginalActivityToClose()
            harness.drainTransactions()
            assertEquals(1, closeCount.get())
            assertEquals(1, harness.writeCount())
        } finally {
            transactionGate.release.countDown()
        }
    }

    @Test
    fun realAbortWithQueuedBackRetainsDraftAndHasNoLateCommit() {
        awaitInitialPlan()
        harness.installWriteCounter()
        harness.installAbortInsertTrigger()
        val transactionGate = harness.gateTransactionExecutor()
        try {
            enterWarningSigns("stay with Priya")
            invokeDoneAndBackInTheSameMainLoop()
            transactionGate.release.countDown()

            waitForPoliteFailure()
            harness.drainTransactions()
            val editing = currentViewModel().uiState.value as SafetyPlanUiState.Editing
            assertEquals("stay with Priya", editing.draft.warningSigns)
            assertNull(persistedPlan())
            assertEquals(0, harness.writeCount())
            assertEquals(0, closeCount.get())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            transactionGate.release.countDown()
        }
    }

    @Test
    fun capturedDoneInvokedAgainAfterCommitBeforeResultDeliveryWritesOnce() = runBlocking {
        awaitInitialPlan()
        harness.installWriteCounter()
        resultGate.arm()
        try {
            enterWarningSigns("call Maya")
            val capturedDone = captureDoneAction()
            invokeOnMain(capturedDone)
            resultGate.awaitCommitted()

            assertTrue(currentViewModel().uiState.value is SafetyPlanUiState.Saving)
            invokeOnMain(capturedDone)
            assertEquals(1, resultGate.calls.get())
            assertEquals(1, harness.writeCount())

            resultGate.releaseResult()
            waitUntil { currentViewModel().uiState.value is SafetyPlanUiState.Viewing }
            harness.drainTransactions()
            assertEquals(1, resultGate.calls.get())
            assertEquals(1, harness.writeCount())
            assertEquals("call Maya", persistedWarningSigns())
        } finally {
            resultGate.releaseResult()
        }
    }

    @Test
    fun newerWriterStaysVisibleWhenOlderCommittedResultIsReleased() = runBlocking {
        awaitInitialPlan()
        harness.installWriteCounter()
        resultGate.arm()
        try {
            enterWarningSigns("writer A")
            val capturedDone = captureDoneAction()
            invokeOnMain(capturedDone)
            resultGate.awaitCommitted()

            val writerB = RoomSafetyPlanStore(harness.dao) { 200L }.save(
                SaveSafetyPlan(99L, SafetyPlan(warningSigns = "writer B")),
            ) as SafetyPlanSaveResult.Committed
            val savingViewModel = currentViewModel()
            waitForText("writer B")
            resultGate.releaseResult()
            waitUntil {
                (savingViewModel.uiState.value as? SafetyPlanUiState.Viewing)?.persisted ==
                    writerB.stored
            }
            waitForText("writer B")
            assertEquals(writerB.stored, harness.dao.planNow())
            assertEquals("writer B", savingViewModel.uiState.value.visiblePlan.warningSigns)
        } finally {
            resultGate.releaseResult()
        }
    }

    @Test
    fun newerWriterPrecedenceAlsoEmitsOneQueuedClose() = runBlocking {
        awaitInitialPlan()
        harness.installWriteCounter()
        resultGate.arm()
        try {
            enterWarningSigns("writer A")
            invokeOnMain(captureDoneAction())
            resultGate.awaitCommitted()
            pressSystemBack()

            val writerB = RoomSafetyPlanStore(harness.dao) { 200L }.save(
                SaveSafetyPlan(99L, SafetyPlan(warningSigns = "writer B")),
            ) as SafetyPlanSaveResult.Committed
            val savingViewModel = currentViewModel()
            waitForText("writer B")
            resultGate.releaseResult()
            waitUntil {
                (savingViewModel.uiState.value as? SafetyPlanUiState.Viewing)?.persisted ==
                    writerB.stored
            }
            waitForOriginalActivityToClose()
            harness.drainTransactions()
            assertEquals(writerB.stored, harness.dao.planNow())
            assertEquals(1, closeCount.get())
            assertEquals(2, harness.writeCount())
        } finally {
            resultGate.releaseResult()
        }
    }

    @Test
    fun ignoredInsertReadbackMismatchRetainsDraftAsPoliteFailure() {
        awaitInitialPlan()
        harness.installWriteCounter()
        harness.installIgnoreInsertTrigger()
        enterWarningSigns("stay with Priya")

        tapDone()
        waitForPoliteFailure()
        harness.drainTransactions()

        val editing = currentViewModel().uiState.value as SafetyPlanUiState.Editing
        assertEquals("stay with Priya", editing.draft.warningSigns)
        assertNull(persistedPlan())
        assertEquals(0, harness.writeCount())
        assertEquals(0, closeCount.get())
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    @Test
    fun configurationRecreationRetainsDraftAndSlowSaveInTheSameViewModel() {
        awaitInitialPlan()
        harness.installWriteCounter()
        enterWarningSigns("pace and stop replying")
        val originalViewModel = currentViewModel()

        scenario.recreate()
        rule.waitForIdle()
        assertSame(originalViewModel, currentViewModel())
        assertWarningSignsFieldContains("pace and stop replying")

        val transactionGate = harness.gateTransactionExecutor()
        try {
            tapDone()
            waitForText("Still saving…")
            scenario.recreate()
            rule.waitForIdle()
            assertSame(originalViewModel, currentViewModel())
            waitForText("Still saving…")
            assertTrue(currentViewModel().uiState.value is SafetyPlanUiState.Saving)

            transactionGate.release.countDown()
            waitUntil { persistedWarningSigns() == "pace and stop replying" }
            waitUntil { currentViewModel().uiState.value is SafetyPlanUiState.Viewing }
            harness.drainTransactions()
            assertEquals(1, harness.writeCount())
        } finally {
            transactionGate.release.countDown()
        }
    }

    @Test
    fun destroyAndFreshLaunchShowsRoomOnlyAndDoesNotRestoreDraft_notProcessDeathSimulation(): Unit =
        runBlocking {
            awaitInitialPlan()
            harness.installWriteCounter()
            val roomPlan = (RoomSafetyPlanStore(harness.dao) { 100L }.save(
                SaveSafetyPlan(1L, SafetyPlan(warningSigns = "Room-only plan")),
            ) as SafetyPlanSaveResult.Committed).stored
            waitForText("Room-only plan")

            replaceWarningSigns("unsaved draft")
            assertWarningSignsFieldContains("unsaved draft")
            val originalViewModel = currentViewModel()
            finishOriginalActivity()
            harness.drainTransactions()
            assertEquals(roomPlan, harness.dao.planNow())
            assertEquals(1, harness.writeCount())

            // This is a destroy-and-fresh-launch contract. It intentionally does not
            // claim to simulate an operating-system process death.
            ActivityScenario.launch(SupportHarnessActivity::class.java).use { freshScenario ->
                waitUntil { latestViewModel.get() !== originalViewModel }
                awaitInitialPlan()
                waitForText("Room-only plan")
                assertNoText("unsaved draft")
                assertNotSame(originalViewModel, currentViewModel())
                harness.drainTransactions()
                assertEquals(roomPlan, harness.dao.planNow())
                assertEquals(1, harness.writeCount())
                freshScenario.onActivity { it.finish() }
            }
        }

    @Test
    fun backWhileEditingWritesNothingAndContactsStillUseTheirExistingDaoPath() {
        awaitInitialPlan()
        harness.installWriteCounter()
        enterWarningSigns("do not save this")
        val originalViewModel = currentViewModel()

        pressSystemBack()
        waitForOriginalActivityToClose()
        harness.drainTransactions()
        assertNull(persistedPlan())
        assertEquals(0, harness.writeCount())

        ActivityScenario.launch(SupportHarnessActivity::class.java).use { freshScenario ->
            waitUntil { latestViewModel.get() !== originalViewModel }
            awaitInitialPlan()
            addContact("Priya", "5551234567")
            waitUntil { runBlocking { harness.dao.contactsNow() }.singleOrNull()?.phone == "5551234567" }

            rule.onNodeWithText("remove").performScrollTo().performClick()
            waitUntil { runBlocking { harness.dao.contactsNow() }.isEmpty() }
            harness.drainTransactions()
            assertEquals(0, harness.writeCount())
            freshScenario.onActivity { it.finish() }
        }
    }

    private fun awaitInitialPlan() {
        waitUntil {
            latestViewModel.get()?.uiState?.value?.persisted?.updatedAt != null &&
                latestViewModel.get().uiState.value.persisted.updatedAt != Long.MIN_VALUE
        }
    }

    private fun currentViewModel(): SupportViewModel = checkNotNull(latestViewModel.get())

    private fun enterWarningSigns(text: String) {
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .performTextInput(text)
        rule.waitForIdle()
    }

    private fun replaceWarningSigns(text: String) {
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun assertWarningSignsFieldContains(text: String) {
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .assertTextContains(text, substring = true)
    }

    private fun addContact(name: String, phone: String) {
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.onNodeWithText("Name").performScrollTo().performTextInput(name)
        rule.onNodeWithText("Phone").performScrollTo().performTextInput(phone)
        rule.onNodeWithText("Add person").performScrollTo().performClick()
    }

    private fun tapDone() {
        rule.onNodeWithText("done").performScrollTo().performClick()
    }

    private fun captureDoneAction(): () -> Boolean {
        val node = rule.onNodeWithText("done").fetchSemanticsNode()
        return checkNotNull(node.config[SemanticsActions.OnClick].action)
    }

    private fun invokeOnMain(action: () -> Boolean, times: Int = 1) {
        scenario.onActivity {
            repeat(times) { assertTrue(action()) }
        }
    }

    private fun invokeDoneAndBackInTheSameMainLoop() {
        val done = captureDoneAction()
        scenario.onActivity { activity ->
            assertTrue(done())
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun pressSystemBack() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun finishOriginalActivity() {
        scenario.onActivity { it.finish() }
        waitForOriginalActivityToClose()
    }

    private fun waitForOriginalActivityToClose() {
        waitUntil { scenario.state == Lifecycle.State.DESTROYED }
    }

    private fun waitForText(text: String) {
        waitUntil { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun assertNoText(text: String) {
        assertTrue(rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    private fun waitForPoliteFailure() {
        val error = "That didn't save. Your plan is still here — try again."
        waitForText(error)
        rule.onNode(
            hasText(error) and SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        ).assertIsDisplayed()
    }

    private fun persistedPlan(): SafetyPlan? =
        harness.database.openHelper.readableDatabase
            .query("SELECT * FROM safety_plan WHERE id = ${SafetyPlan.SINGLETON_ID}")
            .use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                SafetyPlan(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    warningSigns = cursor.getString(cursor.getColumnIndexOrThrow("warningSigns")),
                    copingSteps = cursor.getString(cursor.getColumnIndexOrThrow("copingSteps")),
                    distractions = cursor.getString(cursor.getColumnIndexOrThrow("distractions")),
                    reasonsForLiving = cursor.getString(cursor.getColumnIndexOrThrow("reasonsForLiving")),
                    environmentSafety = cursor.getString(cursor.getColumnIndexOrThrow("environmentSafety")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                )
            }

    private fun persistedWarningSigns(): String? = persistedPlan()?.warningSigns

    private fun waitUntil(condition: () -> Boolean) {
        rule.waitUntil(TEST_TIMEOUT_MILLIS, condition)
    }

    companion object {
        private const val TEST_TIMEOUT_MILLIS = 10_000L
    }
}
