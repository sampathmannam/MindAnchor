package org.mindanchor.support

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyPlan

internal sealed interface SafetyPlanSaveState {
    data object Idle : SafetyPlanSaveState
    data object Saving : SafetyPlanSaveState
    data object Saved : SafetyPlanSaveState
    data object Failed : SafetyPlanSaveState
}

class SupportViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AnchorDatabase.get(application).safety()

    private val _plan = MutableStateFlow<SafetyPlan?>(null)
    val plan = _plan.asStateFlow()

    private val _saveState = MutableStateFlow<SafetyPlanSaveState>(SafetyPlanSaveState.Idle)
    internal val saveState = _saveState.asStateFlow()

    val contacts = dao.contacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            dao.plan().collect { _plan.value = it }
        }
    }

    fun savePlan(plan: SafetyPlan) {
        if (_saveState.value == SafetyPlanSaveState.Saving) return
        _saveState.value = SafetyPlanSaveState.Saving
        val planToSave = plan.copy(updatedAt = System.currentTimeMillis())
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val verified = runCatching {
                withContext(NonCancellable) {
                    dao.savePlan(planToSave)
                    dao.planNow().takeIf { it == planToSave }
                        ?: error("Safety plan write did not match its readback")
                }
            }
            verified.fold(
                onSuccess = {
                    _plan.value = it
                    _saveState.value = SafetyPlanSaveState.Saved
                },
                onFailure = { _saveState.value = SafetyPlanSaveState.Failed },
            )
        }
    }

    internal fun consumeSaveSuccess() {
        if (_saveState.value == SafetyPlanSaveState.Saved) {
            _saveState.value = SafetyPlanSaveState.Idle
        }
    }

    /**
     * A crisis contact exists to be called, so the number is what makes it
     * one. The old guard only rejected a contact that was blank in both
     * fields, which let a name with no number through — it then sat at the
     * top of the crisis card looking like a way to reach someone and did
     * nothing when tapped.
     */
    fun addContact(name: String, phone: String, isProfessional: Boolean) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            dao.addContact(
                CrisisContact(
                    name = name.trim(),
                    phone = phone.trim(),
                    isProfessional = isProfessional,
                ),
            )
        }
    }

    fun removeContact(contact: CrisisContact) {
        viewModelScope.launch { dao.removeContact(contact) }
    }
}
