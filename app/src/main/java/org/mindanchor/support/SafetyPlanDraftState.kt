package org.mindanchor.support

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import org.mindanchor.data.db.SafetyPlan

internal data class SafetyPlanDraftState(
    val isEditing: Boolean = false,
    private val draft: SafetyPlan = SafetyPlan(),
) {
    fun visiblePlan(persistedPlan: SafetyPlan): SafetyPlan =
        if (isEditing) draft else persistedPlan

    fun startEditing(persistedPlan: SafetyPlan): SafetyPlanDraftState =
        copy(isEditing = true, draft = persistedPlan)

    fun updateDraft(plan: SafetyPlan): SafetyPlanDraftState {
        check(isEditing)
        return copy(draft = plan)
    }

    fun saveSucceeded(): SafetyPlanDraftState = copy(isEditing = false)

    companion object {
        val Saver: Saver<SafetyPlanDraftState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.isEditing,
                    state.draft.warningSigns,
                    state.draft.copingSteps,
                    state.draft.distractions,
                    state.draft.reasonsForLiving,
                    state.draft.environmentSafety,
                )
            },
            restore = { saved ->
                SafetyPlanDraftState(
                    isEditing = saved[0] as Boolean,
                    draft = SafetyPlan(
                        warningSigns = saved[1] as String,
                        copingSteps = saved[2] as String,
                        distractions = saved[3] as String,
                        reasonsForLiving = saved[4] as String,
                        environmentSafety = saved[5] as String,
                    ),
                )
            },
        )
    }
}
