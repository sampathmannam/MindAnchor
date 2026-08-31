package org.mindanchor.support

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.db.SafetyPlan

class SafetyPlanSaveStateTest {

    @Test
    fun onlyIdleAndFailedAdmitANewSave() {
        assertTrue(SafetyPlanSaveState.Idle.canStartSave)
        assertTrue(SafetyPlanSaveState.Failed.canStartSave)
        assertFalse(SafetyPlanSaveState.Saving.canStartSave)
        assertFalse(SafetyPlanSaveState.Saved.canStartSave)
    }

    @Test
    fun saveVerificationReturnsFailureAtItsDeadline() = runBlocking {
        val startedAt = System.nanoTime()

        val verified = saveAndVerifySafetyPlan(
            plan = SafetyPlan(warningSigns = "expected"),
            timeoutMillis = 50,
            save = { delay(5_000) },
            readback = { error("readback must not run after timeout") },
        )

        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        assertFalse(verified)
        assertTrue("timeout took $elapsedMillis ms", elapsedMillis < 1_000)
    }
}
