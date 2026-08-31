package org.mindanchor.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mindanchor.data.db.SafetyPlan

class SafetyPlanDraftStateTest {

    @Test
    fun doneShowsTheDraftBeforePersistencePublishesIt() {
        val persisted = SafetyPlan()
        val draft = persisted.copy(warningSigns = "cannot sleep")
        val editing = SafetyPlanDraftState()
            .startEditing(persisted)
            .updateDraft(draft)

        val committed = editing.finishEditing()

        assertFalse(committed.state.isEditing)
        assertEquals(draft, committed.planToSave)
        assertEquals(draft, committed.state.visiblePlan(persisted))
    }

    @Test
    fun publishedPlanReleasesTheOptimisticDraft() {
        val draft = SafetyPlan(warningSigns = "cannot sleep")
        val committed = SafetyPlanDraftState()
            .startEditing(SafetyPlan())
            .updateDraft(draft)
            .finishEditing()
        val published = draft.copy(updatedAt = 123L)

        val caughtUp = committed.state.persistedPlanObserved(published)

        assertEquals(published, caughtUp.visiblePlan(published))
    }
}
