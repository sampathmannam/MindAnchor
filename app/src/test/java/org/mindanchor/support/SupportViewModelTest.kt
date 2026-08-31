package org.mindanchor.support

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyDao
import org.mindanchor.data.db.SafetyPlan
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun editAndDraftChangedDoNotWrite() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.start()
        fixture.vm.onEvent(SupportEvent.Edit)
        fixture.vm.onEvent(SupportEvent.DraftChanged(SafetyPlan(warningSigns = "cannot sleep")))
        assertEquals(0, fixture.store.saveCalls)
        assertEquals(
            "cannot sleep",
            (fixture.vm.uiState.value as SafetyPlanUiState.Editing).draft.warningSigns,
        )
    }

    @Test
    fun doneMovesToSavingSynchronouslyAndRejectsEveryDuplicate() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.startEditing("call Maya")
        fixture.vm.onEvent(SupportEvent.Done)
        val saving = fixture.vm.uiState.value as SafetyPlanUiState.Saving
        fixture.vm.onEvent(SupportEvent.Done)
        fixture.vm.onEvent(SupportEvent.Done)
        runCurrent()
        assertEquals(1, fixture.store.saveCalls)
        assertEquals(1L, saving.command.operationId)
    }

    @Test
    fun threeSecondsOnlyMarksTheSameOperationSlow() = runTest(dispatcher) {
        val fixture = fixture(slowThresholdMillis = 3_000L)
        fixture.startEditing("walk")
        fixture.vm.onEvent(SupportEvent.Done)
        advanceTimeBy(2_999L)
        assertFalse((fixture.vm.uiState.value as SafetyPlanUiState.Saving).isSlow)
        advanceTimeBy(1L)
        runCurrent()
        assertTrue((fixture.vm.uiState.value as SafetyPlanUiState.Saving).isSlow)
        assertEquals(1, fixture.store.saveCalls)
    }

    @Test
    fun matchingCommitReturnsDirectlyToViewing() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.startEditing("walk")
        fixture.vm.onEvent(SupportEvent.Done)
        runCurrent()
        fixture.store.completeCommitted(updatedAt = 10L)
        advanceUntilIdle()
        assertTrue(fixture.vm.uiState.value is SafetyPlanUiState.Viewing)
        assertEquals("walk", fixture.vm.uiState.value.persisted.warningSigns)
    }

    @Test
    fun matchingFailureRetainsDraftCancelsQueuedCloseAndAllowsOneRetry() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.startEditing("stay with Priya")
        fixture.vm.onEvent(SupportEvent.Done)
        fixture.vm.onEvent(SupportEvent.Back)
        runCurrent()
        fixture.store.completeFailed()
        advanceUntilIdle()
        val editing = fixture.vm.uiState.value as SafetyPlanUiState.Editing
        assertEquals("stay with Priya", editing.draft.warningSigns)
        assertEquals(SafetyPlanUiError.SaveFailed, editing.error)
        assertNull(fixture.effects.tryReceive().getOrNull())
        fixture.vm.onEvent(SupportEvent.Done)
        runCurrent()
        assertEquals(2, fixture.store.saveCalls)
    }

    @Test
    fun repeatedBackWhileSavingEmitsExactlyOneCloseAfterCommit() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.startEditing("call Maya")
        fixture.vm.onEvent(SupportEvent.Done)
        fixture.vm.onEvent(SupportEvent.Back)
        fixture.vm.onEvent(SupportEvent.Back)
        runCurrent()
        fixture.store.completeCommitted(updatedAt = 20L)
        advanceUntilIdle()
        assertEquals(SupportEffect.Close, fixture.effects.receive())
        assertNull(fixture.effects.tryReceive().getOrNull())
    }

    @Test
    fun newerRoomPublicationBeatsAnOlderMatchingResult() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.startEditing("writer A")
        fixture.vm.onEvent(SupportEvent.Done)
        runCurrent()
        fixture.store.publish(SafetyPlan(warningSigns = "writer B", updatedAt = 12L))
        runCurrent()
        fixture.store.completeCommitted(updatedAt = 11L)
        advanceUntilIdle()
        assertEquals("writer B", fixture.vm.uiState.value.persisted.warningSigns)
    }

    @Test
    fun staleResultCannotCompleteTheCurrentOperation() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.startEditing("first")
        fixture.vm.onEvent(SupportEvent.Done)
        runCurrent()
        fixture.store.completeFailed()
        advanceUntilIdle()
        fixture.vm.onEvent(SupportEvent.Done)
        val current = fixture.vm.uiState.value as SafetyPlanUiState.Saving
        fixture.vm.acceptSaveResult(
            SafetyPlanSaveResult.Committed(
                1L,
                SafetyPlan(warningSigns = "stale", updatedAt = 99L),
            ),
        )
        assertEquals(current, fixture.vm.uiState.value)
    }

    @Test
    fun productionSlowThresholdIsExactlyThreeSeconds() {
        assertEquals(3_000L, SupportViewModel.SLOW_THRESHOLD_MILLIS)
    }

    private fun TestScope.fixture(slowThresholdMillis: Long = 3_000L): Fixture {
        val store = FakeSafetyPlanStore()
        val vm = SupportViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            store = store,
            dao = FakeSafetyDao(),
            slowThresholdMillis = slowThresholdMillis,
        )
        return Fixture(vm, store, vm.effects.produceIn(this), this)
    }

    private data class Fixture(
        val vm: SupportViewModel,
        val store: FakeSafetyPlanStore,
        val effects: ReceiveChannel<SupportEffect>,
        val scope: TestScope,
    ) {
        fun start() {
            scope.runCurrent()
        }

        fun startEditing(warningSigns: String) {
            start()
            vm.onEvent(SupportEvent.Edit)
            vm.onEvent(SupportEvent.DraftChanged(SafetyPlan(warningSigns = warningSigns)))
        }
    }

    private class FakeSafetyPlanStore : SafetyPlanStore {
        private val published = MutableStateFlow(SafetyPlan())
        private val pending = ArrayDeque<Pair<SaveSafetyPlan, CompletableDeferred<SafetyPlanSaveResult>>>()
        override val plans: Flow<SafetyPlan> = published
        var saveCalls = 0
            private set

        override suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult {
            saveCalls += 1
            val result = CompletableDeferred<SafetyPlanSaveResult>()
            pending.addLast(command to result)
            return result.await()
        }

        fun publish(plan: SafetyPlan) {
            published.value = plan
        }

        fun completeCommitted(updatedAt: Long) {
            val (command, result) = pending.removeFirst()
            result.complete(
                SafetyPlanSaveResult.Committed(
                    command.operationId,
                    command.draft.copy(updatedAt = updatedAt),
                ),
            )
        }

        fun completeFailed() {
            val (command, result) = pending.removeFirst()
            result.complete(
                SafetyPlanSaveResult.Failed(command.operationId, IOException("write failed")),
            )
        }
    }

    private class FakeSafetyDao : SafetyDao() {
        private val contactRows = MutableStateFlow<List<CrisisContact>>(emptyList())
        override fun plan(): Flow<SafetyPlan?> = flowOf(null)
        override suspend fun planNow(): SafetyPlan? = null
        override suspend fun savePlan(plan: SafetyPlan) = error("plan writes must use FakeSafetyPlanStore")
        override fun contacts(): Flow<List<CrisisContact>> = contactRows
        override suspend fun contactsNow(): List<CrisisContact> = contactRows.value
        override suspend fun addContact(contact: CrisisContact) {
            contactRows.value = contactRows.value + contact
        }

        override suspend fun removeContact(contact: CrisisContact) {
            contactRows.value = contactRows.value - contact
        }
    }
}
