package org.mindanchor.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.db.SafetyPlan

class SafetyPlanDraftStateTest {

    @Test
    fun draftRemainsEditableUntilTheSaveIsVerified() {
        val persisted = SafetyPlan()
        val draft = persisted.copy(warningSigns = "cannot sleep")
        val editing = SafetyPlanDraftState()
            .startEditing(persisted)
            .updateDraft(draft)

        assertTrue(editing.isEditing)
        assertEquals(draft, editing.visiblePlan(persisted))
    }

    @Test
    fun verifiedSaveReturnsToThePersistedReader() {
        val draft = SafetyPlan(warningSigns = "cannot sleep")
        val editing = SafetyPlanDraftState()
            .startEditing(SafetyPlan())
            .updateDraft(draft)
        val published = draft.copy(updatedAt = 123L)

        val saved = editing.saveSucceeded()

        assertFalse(saved.isEditing)
        assertEquals(published, saved.visiblePlan(published))
    }
}
