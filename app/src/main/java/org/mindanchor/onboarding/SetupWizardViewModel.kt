/*
 * v0.35.1 — setup wizard ViewModel.
 *
 * Owns the current step and the per-step decision logic. The
 * Activity is a thin host: it collects `currentStep` and renders
 * the matching step Composable. The Activity does not decide
 * navigation — the ViewModel does, because the navigation rules
 * touch DataStore (per-step skipped flags) and we want them in one
 * place that is unit-testable.
 *
 * The step chain is a hand-rolled linked list, not a sealed class
 * hierarchy. The wizard has exactly 5 user steps + welcome + done;
 * a sealed hierarchy would be one Composable per subtype, and the
 * rendering surface is small enough that a `when` over the enum is
 * the right shape.
 */
package org.mindanchor.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetupWizardViewModel(
    private val prefs: SetupPrefs,
) : ViewModel() {

    /** The user's current position in the wizard. */
    private val _currentStep = MutableStateFlow(SetupStep.WELCOME)
    val currentStep: StateFlow<SetupStep> = _currentStep.asStateFlow()

    /**
     * The skip state of every skippable step. Recomposes the
     * step Composables when the user changes it.
     */
    val progress: StateFlow<SetupProgress> = prefs.progress.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SetupProgress(),
    )

    /** Mark the wizard as completed (suspend — caller awaits the write). */
    suspend fun complete() {
        prefs.markCompleted()
    }

    /** Mark the wizard as dismissed (suspend — caller awaits the write). */
    suspend fun dismiss() {
        prefs.markDismissed()
    }

    /**
     * Advance to the next non-skipped step after the current one.
     * If the current step is the last non-skipped one, advance to
     * DONE. Calling advance() while at DONE is a no-op (it would be
     * a UI bug, but the wizard is forgiving).
     */
    fun advance() {
        val next = progress.value.firstPendingAfter(_currentStep.value)
        _currentStep.value = next
    }

    /**
     * Go to the previous step. From WELCOME this is a no-op; the
     * Activity handles step 1's back-press as `dismissAndFinish`
     * instead. From any other step, this is the previous enum value
     * (the wizard never skips backwards over a step the user has
     * marked skipped — the user can come back to it from Settings).
     */
    fun back() {
        val cur = _currentStep.value
        val prev = when (cur) {
            SetupStep.WELCOME -> return
            SetupStep.HEALTH_CONNECT -> SetupStep.WELCOME
            SetupStep.PAIR_WATCH -> SetupStep.HEALTH_CONNECT
            SetupStep.POLAR -> SetupStep.PAIR_WATCH
            SetupStep.PPG -> SetupStep.POLAR
            SetupStep.DONE -> {
                // From Done, back goes to the last non-skipped
                // step before DONE, so the user can re-touch it.
                progress.value.firstPendingAfter(SetupStep.WELCOME).let {
                    if (it == SetupStep.DONE) SetupStep.PPG else it
                }
            }
        }
        _currentStep.value = prev
    }

    /**
     * Mark [step] as skipped, then advance to the next non-skipped
     * step. The user can re-enter a skipped step by re-running the
     * wizard from Settings.
     */
    fun skip(step: SetupStep) {
        viewModelScope.launch {
            prefs.setSkipped(step, true)
            advance()
        }
    }

    /**
     * Reset and re-launch. Used by the "Run setup wizard again"
     * button in Settings.
     */
    fun reset() {
        viewModelScope.launch {
            prefs.reset()
            _currentStep.value = SetupStep.WELCOME
        }
    }

    /** Factory that supplies the [SetupPrefs] from a [Context]. */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SetupWizardViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            return SetupWizardViewModel(SetupPrefs(context.applicationContext)) as T
        }
    }
}
