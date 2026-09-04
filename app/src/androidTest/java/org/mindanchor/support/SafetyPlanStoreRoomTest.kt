package org.mindanchor.support

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.SafetyPlan

@RunWith(AndroidJUnit4::class)
class SafetyPlanStoreRoomTest {
    private lateinit var room: SafetyPlanRoomHarness

    @Before
    fun setUp() {
        room = SafetyPlanRoomHarness(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = room.close()

    @Test
    fun saveReturnsTheExactCommittedRowWithAMonotonicStamp() = runBlocking {
        room.dao.savePlan(SafetyPlan(warningSigns = "old", updatedAt = 250L))
        val result = RoomSafetyPlanStore(room.dao) { 100L }.save(
            SaveSafetyPlan(7L, SafetyPlan(warningSigns = "new")),
        )
        val committed = result as SafetyPlanSaveResult.Committed
        assertEquals(7L, committed.operationId)
        assertEquals(251L, committed.stored.updatedAt)
        assertEquals("new", committed.stored.warningSigns)
        assertEquals(committed.stored, room.dao.planNow())
    }

    @Test
    fun absentRowUsesTheClockMillisStamp() = runBlocking {
        val result = RoomSafetyPlanStore(room.dao) { 500L }.save(
            SaveSafetyPlan(1L, SafetyPlan(copingSteps = "walk")),
        ) as SafetyPlanSaveResult.Committed
        assertEquals(500L, result.stored.updatedAt)
    }

    @Test
    fun ignoredInsertProducesFailedAndLeavesThePriorRow() = runBlocking {
        val prior = SafetyPlan(warningSigns = "prior", updatedAt = 10L)
        room.dao.savePlan(prior)
        room.installIgnoreInsertTrigger()
        val result = RoomSafetyPlanStore(room.dao) { 20L }.save(
            SaveSafetyPlan(2L, SafetyPlan(warningSigns = "ignored")),
        )
        assertTrue(result is SafetyPlanSaveResult.Failed)
        assertEquals(prior, room.dao.planNow())
    }

    @Test
    fun abortTriggerProducesFailedAndNoLateWrite() = runBlocking {
        room.installWriteCounter()
        room.installAbortInsertTrigger()
        val result = RoomSafetyPlanStore(room.dao) { 20L }.save(
            SaveSafetyPlan(3L, SafetyPlan(warningSigns = "abort")),
        )
        assertTrue(result is SafetyPlanSaveResult.Failed)
        room.drainTransactions()
        assertEquals(0, room.writeCount())
        assertNull(room.dao.planNow())
    }

    @Test
    fun timestampOverflowFailsBeforeWriting() = runBlocking {
        room.dao.savePlan(SafetyPlan(warningSigns = "max", updatedAt = Long.MAX_VALUE))
        room.installWriteCounter()
        val result = RoomSafetyPlanStore(room.dao) { Long.MAX_VALUE }.save(
            SaveSafetyPlan(4L, SafetyPlan(warningSigns = "must not replace")),
        )
        assertTrue(result is SafetyPlanSaveResult.Failed)
        assertEquals(0, room.writeCount())
        assertEquals("max", room.dao.planNow()?.warningSigns)
    }
}
