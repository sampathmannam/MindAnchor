package org.mindanchor.support

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyPlan

internal sealed interface SafetyPlanSaveState {
    data object Idle : SafetyPlanSaveState
    data object Saving : SafetyPlanSaveState
    data object Saved : SafetyPlanSaveState
    data object Failed : SafetyPlanSaveState
}

internal val SafetyPlanSaveState.canStartSave: Boolean
    get() = this == SafetyPlanSaveState.Idle || this == SafetyPlanSaveState.Failed

internal suspend fun saveAndVerifySafetyPlan(
    plan: SafetyPlan,
    timeoutMillis: Long,
    save: suspend (SafetyPlan) -> Unit,
    readback: suspend () -> SafetyPlan?,
): Boolean = runCatching {
    withContext(NonCancellable) {
        withTimeout(timeoutMillis) {
            save(plan)
            readback() == plan
        }
    }
}.getOrDefault(false)

class SupportViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AnchorDatabase.get(application).safety()

    val plan = dao.plan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saveState = MutableStateFlow<SafetyPlanSaveState>(SafetyPlanSaveState.Idle)
    internal val saveState = _saveState.asStateFlow()

    val contacts = dao.contacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal val saveBlocksNavigation: Boolean
        get() = !_saveState.value.canStartSave

    fun savePlan(plan: SafetyPlan): Boolean {
        val current = _saveState.value
        if (!current.canStartSave || !_saveState.compareAndSet(current, SafetyPlanSaveState.Saving)) {
            return false
        }
        val planToSave = plan.copy(updatedAt = System.currentTimeMillis())
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val verified = saveAndVerifySafetyPlan(
                plan = planToSave,
                timeoutMillis = SAVE_TIMEOUT_MILLIS,
                save = dao::savePlan,
                readback = dao::planNow,
            )
            _saveState.value = if (verified) SafetyPlanSaveState.Saved else SafetyPlanSaveState.Failed
        }
        return true
    }

    internal fun consumeSaveSuccess() {
        _saveState.compareAndSet(SafetyPlanSaveState.Saved, SafetyPlanSaveState.Idle)
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

    private companion object {
        const val SAVE_TIMEOUT_MILLIS = 3_000L
    }
}
