package org.mindanchor.support

import org.mindanchor.data.db.SafetyPlan

internal data class SafetyPlanDraftState(
    private val draft: SafetyPlan? = null,
    private val committedPlan: SafetyPlan? = null,
) {
    val isEditing: Boolean
        get() = draft != null

    fun visiblePlan(persistedPlan: SafetyPlan): SafetyPlan =
        draft ?: committedPlan ?: persistedPlan

    fun startEditing(persistedPlan: SafetyPlan): SafetyPlanDraftState =
        copy(draft = visiblePlan(persistedPlan))

    fun updateDraft(plan: SafetyPlan): SafetyPlanDraftState {
        check(isEditing)
        return copy(draft = plan)
    }

    fun finishEditing(): SafetyPlanDraftCommit {
        val planToSave = checkNotNull(draft)
        return SafetyPlanDraftCommit(
            state = copy(draft = null, committedPlan = planToSave),
            planToSave = planToSave,
        )
    }

    fun persistedPlanObserved(persistedPlan: SafetyPlan): SafetyPlanDraftState =
        if (committedPlan?.sameContentAs(persistedPlan) == true) {
            copy(committedPlan = null)
        } else {
            this
        }
}

internal data class SafetyPlanDraftCommit(
    val state: SafetyPlanDraftState,
    val planToSave: SafetyPlan,
)

private fun SafetyPlan.sameContentAs(other: SafetyPlan): Boolean =
    copy(updatedAt = other.updatedAt) == other
